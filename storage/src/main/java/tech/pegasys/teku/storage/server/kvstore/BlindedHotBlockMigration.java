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

import java.util.Iterator;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.KvStoreCombinedDaoBlinded;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.KvStoreCombinedDaoUnblinded;

public class BlindedHotBlockMigration<
    T extends KvStoreCombinedDaoBlinded & KvStoreCombinedDaoUnblinded> {
  private static final Logger LOG = LogManager.getLogger();
  private static final int BATCH_SIZE = 1_000;
  private final Spec spec;

  private final T dao;

  private BlindedHotBlockMigration(final Spec spec, final T dao) {
    this.spec = spec;
    this.dao = dao;
  }

  static <T extends KvStoreCombinedDaoBlinded & KvStoreCombinedDaoUnblinded> void migrateBlocks(
      final T dao, final Spec spec) {
    final BlindedHotBlockMigration<T> blindedHotBlockMigration =
        new BlindedHotBlockMigration<>(spec, dao);
    blindedHotBlockMigration.performBatchMigration();
  }

  private void performBatchMigration() {
    moveHotBlocksToBlindedStorage();
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
          for (int i = 0; i < BATCH_SIZE && it.hasNext(); i++) {
            final SignedBeaconBlock block = it.next();
            blindedUpdater.addBlindedBlock(block, block.getRoot(), spec);
            unblindedUpdater.deleteUnblindedHotBlockOnly(block.getRoot());
            counter++;
          }
          blindedUpdater.commit();
          unblindedUpdater.commit();
        }
      }
    }
    LOG.info("Hot blocks all moved ({} blocks)", counter);
    moveFirstFinalizedBlockToBlindedStorage();
  }
}
