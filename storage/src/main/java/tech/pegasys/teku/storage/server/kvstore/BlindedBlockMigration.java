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
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
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
  private static final int HOT_BLOCK_BATCH_SIZE = 1_000;

  private static final int FINALIZED_BLOCK_BATCH_SIZE = 25;

  private static final int INDEX_BATCH_SIZE = 10_000;
  private static final int PAUSE_BETWEEN_BATCH_MS = 100;
  private static final int LOGGING_FREQUENCY = 100_000;
  private final Spec spec;

  private final T dao;

  private final Optional<AsyncRunner> asyncRunner;

  BlindedBlockMigration(final Spec spec, final T dao, final Optional<AsyncRunner> asyncRunner) {
    this.spec = spec;
    this.dao = dao;
    this.asyncRunner = asyncRunner;
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
              if (Throwables.getRootCause(error) instanceof ShuttingDownException) {
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
          for (int i = 0; i < HOT_BLOCK_BATCH_SIZE && it.hasNext(); i++) {
            final SignedBeaconBlock block = it.next();
            blindedUpdater.addBlindedBlock(block, block.getRoot(), spec);
            unblindedUpdater.deleteUnblindedHotBlockOnly(block.getRoot());
            counter++;
          }
          if (counter % LOGGING_FREQUENCY == 0 || counter == countBlocks) {
            double percentCompleted = counter;
            percentCompleted /= countBlocks;
            percentCompleted *= 100;
            LOG.info(
                "{} hot blocks moved ({} %)", counter, String.format("%.2f", percentCompleted));
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
    final long finalizedBlockIndexCounter = copyFinalizedBlockIndexToBlindedStorage();
    final long preBellatrixMigratedBlocks =
        migrateFinalizedBlocksPreBellatrix(finalizedBlockIndexCounter);
    migrateRemainingFinalizedBlocks(finalizedBlockIndexCounter - preBellatrixMigratedBlocks);
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

  private long migrateFinalizedBlocksPreBellatrix(final long migrationCounter) {
    long counter = 0;
    boolean preBellatrix = true;
    if (migrationCounter > 0) {
      LOG.info(
          "Migrating pre-bellatrix blocks to blinded storage, {} finalized blocks to migrate",
          migrationCounter);
    }
    try (final Stream<Map.Entry<Bytes, Bytes>> stream = dao.streamUnblindedFinalizedBlocksRaw()) {
      for (Iterator<Map.Entry<Bytes, Bytes>> it = stream.iterator();
          it.hasNext() && preBellatrix; ) {
        try (KvStoreCombinedDaoBlinded.FinalizedUpdaterBlinded finalizedUpdaterBlinded =
                dao.finalizedUpdaterBlinded();
            KvStoreCombinedDaoUnblinded.FinalizedUpdaterUnblinded finalizedUpdaterUnblinded =
                dao.finalizedUpdaterUnblinded()) {
          for (int i = 0; i < FINALIZED_BLOCK_BATCH_SIZE && it.hasNext() && preBellatrix; i++) {
            final Map.Entry<Bytes, Bytes> entry = it.next();
            final UInt64 slot =
                UInt64.fromLongBits(Longs.fromByteArray(entry.getKey().toArrayUnsafe()));
            if (spec.atSlot(slot).getMilestone().isGreaterThanOrEqualTo(SpecMilestone.BELLATRIX)) {
              preBellatrix = false;
              LOG.info("At slot {}, detected bellatrix block, stopping direct copy", slot);
              continue;
            }
            final Optional<Bytes32> maybeRoot = dao.getFinalizedBlockRootAtSlot(slot);
            if (maybeRoot.isEmpty()) {
              LOG.debug("Could not find block root for slot {}", slot);
              continue;
            }
            finalizedUpdaterBlinded.addBlindedFinalizedBlockRaw(
                entry.getValue(), maybeRoot.get(), slot);
            finalizedUpdaterUnblinded.deleteUnblindedFinalizedBlock(slot, maybeRoot.get());
            counter++;
            if (counter % LOGGING_FREQUENCY == 0) {
              LOG.info("{} pre-bellatrix blocks moved", counter);
            }
          }
          finalizedUpdaterBlinded.commit();
          finalizedUpdaterUnblinded.commit();
        }

        pause();
      }
    }
    if (counter > 0) {
      LOG.info("Done - {} pre-bellatrix blocks moved", counter);
    }
    return counter;
  }

  private void migrateRemainingFinalizedBlocks(final long migrationCounter) {
    long counter = 0;
    if (migrationCounter > 0) {
      LOG.info(
          "Migrating any remaining finalized blocks to blinded storage, {} blocks to migrate",
          migrationCounter);
    }
    try (final Stream<SignedBeaconBlock> stream =
        dao.streamUnblindedFinalizedBlocks(UInt64.ZERO, UInt64.MAX_VALUE)) {
      for (Iterator<SignedBeaconBlock> it = stream.iterator(); it.hasNext(); ) {
        try (KvStoreCombinedDaoBlinded.FinalizedUpdaterBlinded finalizedUpdaterBlinded =
                dao.finalizedUpdaterBlinded();
            KvStoreCombinedDaoUnblinded.FinalizedUpdaterUnblinded finalizedUpdaterUnblinded =
                dao.finalizedUpdaterUnblinded()) {
          for (int i = 0; i < FINALIZED_BLOCK_BATCH_SIZE && it.hasNext(); i++) {
            final SignedBeaconBlock block = it.next();
            finalizedUpdaterBlinded.addBlindedFinalizedBlock(block, block.getRoot(), spec);
            finalizedUpdaterUnblinded.deleteUnblindedFinalizedBlock(
                block.getSlot(), block.getRoot());
            counter++;
            if (counter % LOGGING_FREQUENCY == 0) {
              LOG.info("{} post-bellatrix blocks moved", counter);
            }
          }
          finalizedUpdaterBlinded.commit();
          finalizedUpdaterUnblinded.commit();
        }
        pause();
      }
    }
    if (counter > 0) {
      LOG.info("Done - {} post-bellatrix blocks moved", counter);
    }
  }

  private void pause() {
    try {
      Thread.sleep(PAUSE_BETWEEN_BATCH_MS);
    } catch (InterruptedException e) {
      LOG.debug("Interrupted while processing blocks", e);
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
          for (int i = 0; i < FINALIZED_BLOCK_BATCH_SIZE && it.hasNext(); i++) {
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
