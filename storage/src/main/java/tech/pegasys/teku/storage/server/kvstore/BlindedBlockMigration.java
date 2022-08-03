/*
 * Copyright ConsenSys Software Inc., 2022
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.storage.server.kvstore;

import com.google.common.base.Throwables;
import com.google.common.primitives.Longs;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.beacon.pow.exception.RejectedRequestException;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.storage.server.ShuttingDownException;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.KvStoreCombinedDaoBlinded;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.KvStoreCombinedDaoUnblinded;

public class BlindedBlockMigration<
    T extends KvStoreCombinedDaoBlinded & KvStoreCombinedDaoUnblinded> {
  private static final Logger LOG = LogManager.getLogger();

  private final int blockBatchSize;

  private static final int INDEX_BATCH_SIZE = 10_000;
  private final int blockMigrationBatchDelay;
  private static final int LOGGING_FREQUENCY = 50_000;

  private static final int STREAM_OPEN_LENGTH = 1_000;

  private final AtomicBoolean preBellatrix = new AtomicBoolean(true);

  private final Spec spec;

  private final T dao;

  private final Optional<AsyncRunner> asyncRunner;

  BlindedBlockMigration(
      final Spec spec,
      final T dao,
      final int blockMigrationBatchSize,
      final int blockMigrationBatchDelay,
      final Optional<AsyncRunner> asyncRunner) {
    this.spec = spec;
    this.dao = dao;
    this.asyncRunner = asyncRunner;
    this.blockBatchSize = blockMigrationBatchSize;
    this.blockMigrationBatchDelay = blockMigrationBatchDelay;
  }

  void migrateBlocks() {
    if (asyncRunner.isEmpty()) {
      throw new IllegalStateException("Not able to migrate blocks without an async runner");
    }
    moveHotBlocksToBlindedStorage();
    asyncRunner
        .get()
        .runAsync(this::migrateRemainingBlocks)
        .finish(
            error -> {
              final Throwable rootCause = Throwables.getRootCause(error);
              if (rootCause instanceof ShuttingDownException
                  || rootCause instanceof InterruptedException
                  || rootCause instanceof RejectedRequestException) {
                LOG.debug("Shutting down");
              } else {
                LOG.error("Failed to complete block migration", error);
              }
            });
  }

  private void moveHotBlocksToBlindedStorage() {
    final long countBlocks = dao.countUnblindedHotBlocks();
    if (countBlocks == 0) {
      return;
    }
    LOG.info("Migrating blocks to blinded storage, {} hot blocks to migrate", countBlocks);

    long counter = 0;
    try (final Stream<SignedBeaconBlock> blocks = dao.streamHotBlocks()) {
      for (Iterator<SignedBeaconBlock> it = blocks.iterator(); it.hasNext(); ) {
        try (KvStoreCombinedDaoBlinded.FinalizedUpdaterBlinded blindedUpdater =
                dao.finalizedUpdaterBlinded();
            KvStoreCombinedDaoUnblinded.HotUpdaterUnblinded unblindedUpdater =
                dao.hotUpdaterUnblinded()) {
          for (int i = 0; i < blockBatchSize && it.hasNext(); i++) {
            final SignedBeaconBlock block = it.next();
            blindedUpdater.addBlindedBlock(block, block.getRoot(), spec);
            unblindedUpdater.deleteUnblindedHotBlockOnly(block.getRoot());
            counter++;
            if (counter % 32 == 0 || counter == countBlocks) {
              double percentCompleted = counter * 100.0 / countBlocks;
              LOG.info(
                  "{} hot blocks moved ({} %)", counter, String.format("%.2f", percentCompleted));
            }
          }
          blindedUpdater.commit();
          unblindedUpdater.commit();
        }
      }
    }
    moveFirstFinalizedBlockToBlindedStorage();
  }

  private void moveFirstFinalizedBlockToBlindedStorage() {
    Optional<SignedBeaconBlock> maybeBlock = dao.getEarliestFinalizedBlock();
    maybeBlock.ifPresent(
        block -> {
          final Bytes32 root = block.getRoot();
          LOG.info("Setting lowest finalized block at {}({})", root, block.getSlot());
          try (KvStoreCombinedDaoBlinded.FinalizedUpdaterBlinded blindedUpdater =
                  dao.finalizedUpdaterBlinded();
              KvStoreCombinedDaoUnblinded.FinalizedUpdaterUnblinded unblindedUpdater =
                  dao.finalizedUpdaterUnblinded()) {

            blindedUpdater.addBlindedFinalizedBlock(block, root, spec);
            unblindedUpdater.deleteUnblindedFinalizedBlock(block.getSlot(), root);
            blindedUpdater.commit();
            unblindedUpdater.commit();
          }
        });
  }

  private void migrateRemainingBlocks() {
    long finalizedblockTotal = 0;

    LOG.info("Migrating finalized blocks to blinded storage");
    copyFinalizedBlockIndexToBlindedStorage();
    long counter;
    do {
      counter = migrateFinalizedBlocksPreBellatrix();
      finalizedblockTotal += counter;
      if (finalizedblockTotal % LOGGING_FREQUENCY == 0) {
        LOG.info("{} blocks moved", finalizedblockTotal);
      }
    } while (counter > 0 && preBellatrix.get());

    do {
      counter = migrateRemainingFinalizedBlocks();
      finalizedblockTotal += counter;
      if (finalizedblockTotal % LOGGING_FREQUENCY == 0) {
        LOG.info("{} blocks moved", finalizedblockTotal);
      }
    } while (counter > 0);

    if (finalizedblockTotal > 0) {
      LOG.info("DONE - {} blocks moved", finalizedblockTotal);
    }

    migrateNonCanonicalBlocks();
  }

  private long copyFinalizedBlockIndexToBlindedStorage() {
    long counter = 0;
    try (final Stream<Map.Entry<Bytes32, UInt64>> blockRoots =
        dao.streamUnblindedFinalizedBlockRoots()) {
      for (Iterator<Map.Entry<Bytes32, UInt64>> it = blockRoots.iterator(); it.hasNext(); ) {
        if (counter == 0) {
          LOG.info("Copying finalized block indices to blinded storage");
        }
        try (KvStoreCombinedDaoBlinded.FinalizedUpdaterBlinded finalizedUpdaterBlinded =
            dao.finalizedUpdaterBlinded()) {
          for (int i = 0; i < INDEX_BATCH_SIZE && it.hasNext(); i++) {
            final Map.Entry<Bytes32, UInt64> entry = it.next();
            final Bytes32 root = entry.getKey();
            final UInt64 slot = entry.getValue();
            finalizedUpdaterBlinded.addFinalizedBlockRootBySlot(slot, root);
            counter++;
          }
          finalizedUpdaterBlinded.commit();
        }

        pause();
      }
    }
    if (counter > 0) {
      LOG.info("{} finalized block indices copied", counter);
    }
    return counter;
  }

  private long migrateFinalizedBlocksPreBellatrix() {
    long counter = 0;
    try (final Stream<Map.Entry<Bytes, Bytes>> stream = dao.streamUnblindedFinalizedBlocksRaw()) {
      for (Iterator<Map.Entry<Bytes, Bytes>> it = stream.iterator();
          it.hasNext() && preBellatrix.get() && counter < STREAM_OPEN_LENGTH; ) {
        try (KvStoreCombinedDaoBlinded.FinalizedUpdaterBlinded finalizedUpdaterBlinded =
                dao.finalizedUpdaterBlinded();
            KvStoreCombinedDaoUnblinded.FinalizedUpdaterUnblinded finalizedUpdaterUnblinded =
                dao.finalizedUpdaterUnblinded()) {
          for (int i = 0;
              i < blockBatchSize
                  && it.hasNext()
                  && preBellatrix.get()
                  && counter < STREAM_OPEN_LENGTH;
              i++) {
            final Map.Entry<Bytes, Bytes> entry = it.next();
            final UInt64 slot =
                UInt64.fromLongBits(Longs.fromByteArray(entry.getKey().toArrayUnsafe()));
            if (spec.atSlot(slot).getMilestone().isGreaterThanOrEqualTo(SpecMilestone.BELLATRIX)) {
              preBellatrix.set(false);
              if (counter > 0) {
                LOG.info("At slot {}, detected bellatrix block, stopping direct copy", slot);
              }
              continue;
            }
            final Optional<Bytes32> maybeRoot = dao.getFinalizedBlockRootAtSlot(slot);
            if (maybeRoot.isEmpty()) {
              LOG.error("Could not find block root for slot {}", slot);
              continue;
            }
            finalizedUpdaterBlinded.addBlindedFinalizedBlockRaw(
                entry.getValue(), maybeRoot.get(), slot);
            finalizedUpdaterUnblinded.deleteUnblindedFinalizedBlock(slot, maybeRoot.get());
            counter++;
          }
          finalizedUpdaterBlinded.commit();
          finalizedUpdaterUnblinded.commit();
        }
        pause();
      }
    }
    return counter;
  }

  private long migrateRemainingFinalizedBlocks() {
    long counter = 0;
    try (final Stream<SignedBeaconBlock> stream =
        dao.streamUnblindedFinalizedBlocks(UInt64.ZERO, UInt64.MAX_VALUE)) {
      for (Iterator<SignedBeaconBlock> it = stream.iterator();
          it.hasNext() && counter < STREAM_OPEN_LENGTH; ) {
        try (KvStoreCombinedDaoBlinded.FinalizedUpdaterBlinded finalizedUpdaterBlinded =
                dao.finalizedUpdaterBlinded();
            KvStoreCombinedDaoUnblinded.FinalizedUpdaterUnblinded finalizedUpdaterUnblinded =
                dao.finalizedUpdaterUnblinded()) {
          for (int i = 0; i < blockBatchSize && it.hasNext() && counter < STREAM_OPEN_LENGTH; i++) {
            final SignedBeaconBlock block = it.next();
            finalizedUpdaterBlinded.addBlindedFinalizedBlock(block, block.getRoot(), spec);
            finalizedUpdaterUnblinded.deleteUnblindedFinalizedBlock(
                block.getSlot(), block.getRoot());
            counter++;
          }
          finalizedUpdaterBlinded.commit();
          finalizedUpdaterUnblinded.commit();
        }
        pause();
      }
    }
    return counter;
  }

  private void pause() {
    try {
      Thread.sleep(blockMigrationBatchDelay);
    } catch (InterruptedException e) {
      LOG.debug("Interrupted while processing blocks", e);
      throw new ShuttingDownException();
    }
  }

  private void migrateNonCanonicalBlocks() {
    long counter = 0;
    try (final Stream<Map.Entry<Bytes32, SignedBeaconBlock>> entries =
        dao.streamUnblindedNonCanonicalBlocks()) {
      for (Iterator<Map.Entry<Bytes32, SignedBeaconBlock>> it = entries.iterator();
          it.hasNext(); ) {
        if (counter == 0) {
          LOG.info("Migrating non-canonical blocks to blinded storage");
        }
        try (KvStoreCombinedDaoBlinded.FinalizedUpdaterBlinded finalizedUpdaterBlinded =
                dao.finalizedUpdaterBlinded();
            KvStoreCombinedDaoUnblinded.FinalizedUpdaterUnblinded finalizedUpdaterUnblinded =
                dao.finalizedUpdaterUnblinded()) {
          for (int i = 0; i < blockBatchSize && it.hasNext(); i++) {
            final Map.Entry<Bytes32, SignedBeaconBlock> entry = it.next();
            final Bytes32 root = entry.getKey();
            final SignedBeaconBlock block = entry.getValue();
            finalizedUpdaterBlinded.addBlindedBlock(block, root, spec);
            finalizedUpdaterUnblinded.deleteUnblindedNonCanonicalBlockOnly(root);
            counter++;
            if (counter % LOGGING_FREQUENCY == 0) {
              LOG.info("{} non-canonical blocks moved", counter);
            }
          }
          finalizedUpdaterBlinded.commit();
          finalizedUpdaterUnblinded.commit();
        }
        pause();
      }
    }
    if (counter > 0) {
      LOG.info("Done - {} non-canonical blocks moved", counter);
    }
  }
}
