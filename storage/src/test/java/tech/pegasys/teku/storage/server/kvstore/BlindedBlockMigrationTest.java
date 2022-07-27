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

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.bls.BLSKeyGenerator;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.server.BlockStorage;
import tech.pegasys.teku.storage.server.Database;
import tech.pegasys.teku.storage.server.DatabaseContext;
import tech.pegasys.teku.storage.server.DatabaseVersion;
import tech.pegasys.teku.storage.server.StateStorageMode;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;
import tech.pegasys.teku.storage.store.StoreConfig;
import tech.pegasys.teku.storage.store.UpdatableStore;

public class BlindedBlockMigrationTest {
  private final Spec spec = TestSpecFactory.createMinimalBellatrix();
  private static final List<BLSKeyPair> VALIDATOR_KEYS = BLSKeyGenerator.generateKeyPairs(3);

  private final ChainBuilder chainBuilder = ChainBuilder.create(spec, VALIDATOR_KEYS);

  private RecentChainData recentChainData;
  private final DatabaseContext context =
      new DatabaseContext(DatabaseVersion.DEFAULT_VERSION, BlockStorage.BLINDED_BLOCK, false);
  private final DatabaseContext fullBlockContext =
      new DatabaseContext(DatabaseVersion.DEFAULT_VERSION, BlockStorage.FULL_BLOCK, false);

  @Test
  void shouldMigrateHotBlocks(@TempDir final Path tempDir) throws Exception {
    setupStorageWithHotBlocks(tempDir, 10);
    try (final StorageSystem storage =
        context.createFileBasedStorage(
            spec, tempDir, StateStorageMode.PRUNE, StoreConfig.createDefault(), true)) {
      final Database database = storage.database();
      assertHotBlockCounts(database.getColumnCounts(), 11, 11, 0, 0);
      database.migrate();
      // payloads do get separated out, but there's only 1 default payload, so only 1 entry
      assertHotBlockCounts(database.getColumnCounts(), 11, 0, 11, 1);
    }
  }

  private void assertHotBlockCounts(
      final Map<String, Long> counts,
      final int expectedCheckpoints,
      final int expectedHotBlocks,
      final int expectedBlindedBlocks,
      final int payloads) {
    assertThat(counts.get("HOT_BLOCKS_BY_ROOT")).isEqualTo(expectedHotBlocks);
    assertThat(counts.get("HOT_BLOCK_CHECKPOINT_EPOCHS_BY_ROOT")).isEqualTo(expectedCheckpoints);
    assertThat(counts.get("BLINDED_BLOCKS_BY_ROOT")).isEqualTo(expectedBlindedBlocks);
    assertThat(counts.get("EXECUTION_PAYLOAD_BY_PAYLOAD_HASH")).isEqualTo(payloads);
  }

  private void setupStorageWithHotBlocks(final Path tempDir, final int blockCount)
      throws Exception {

    try (final StorageSystem storage =
        fullBlockContext.createFileBasedStorage(
            spec, tempDir, StateStorageMode.PRUNE, StoreConfig.createDefault(), true)) {
      recentChainData = storage.recentChainData();
      SignedBlockAndState genesisBlockAndState = chainBuilder.generateGenesis();
      recentChainData.initializeFromGenesis(genesisBlockAndState.getState(), UInt64.ZERO);
      final UpdatableStore.StoreTransaction transaction = recentChainData.startStoreTransaction();
      final List<SignedBlockAndState> blocks = chainBuilder.generateBlocksUpToSlot(blockCount);
      add(blocks);
      commit(transaction);
    }
  }

  private void add(final Collection<SignedBlockAndState> blocks) {
    final UpdatableStore.StoreTransaction transaction = recentChainData.startStoreTransaction();
    add(transaction, blocks);
    commit(transaction);
  }

  private void add(
      final UpdatableStore.StoreTransaction transaction,
      final Collection<SignedBlockAndState> blocksAndStates) {
    blocksAndStates.stream()
        .sorted(Comparator.comparing(SignedBlockAndState::getSlot))
        .forEach(
            blockAndState ->
                transaction.putBlockAndState(
                    blockAndState, spec.calculateBlockCheckpoints(blockAndState.getState())));
  }

  private void commit(final UpdatableStore.StoreTransaction transaction) {
    assertThat(transaction.commit()).isCompleted();
  }
}
