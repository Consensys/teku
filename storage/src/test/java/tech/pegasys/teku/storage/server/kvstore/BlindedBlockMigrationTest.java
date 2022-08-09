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
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.bls.BLSKeyGenerator;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
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

  private final StubAsyncRunner stubAsyncRunner = new StubAsyncRunner();

  @Test
  void shouldMigrateHotBlocks(@TempDir final Path tempDir) throws Exception {
    final int blockCount = 10;
    setupStorage(tempDir, blockCount, 0);
    try (final StorageSystem storage =
        context.createFileBasedStorage(
            spec, tempDir, StateStorageMode.PRUNE, StoreConfig.createDefault(), true)) {
      final Database database = storage.database();
      assertUnblindedStorageCounts(database.getColumnCounts(), 1, blockCount, blockCount);
      // checkpoint epochs doesn't change, so it is always the expected size for blinded storage and
      // unblinded
      assertBlindedStorageCounts(database.getColumnCounts(), 0, blockCount, 0, 0);
      database.migrate();
      assertUnblindedStorageCounts(database.getColumnCounts(), 0, 0, blockCount);
      // payloads do get separated out, but there's only 1 default payload, so only 1 entry
      assertBlindedStorageCounts(database.getColumnCounts(), blockCount, blockCount, 1, blockCount);
    }
  }

  @Test
  void shouldMigrateFinalizedBlocks(@TempDir final Path tempDir) throws Exception {
    final int totalBlocks = 39;
    // 39 blocks total, 25 finalized + 14 hot
    final int finalizedBlocks = 25;
    // hot storage has all hot blocks plus current finalized block
    final int unblindedHotBlockCount = 14 + 1;
    setupStorage(tempDir, totalBlocks, 3);
    try (final Database database =
        context.createFileBasedStorageDatabaseOnly(
            spec,
            tempDir,
            StateStorageMode.PRUNE,
            StoreConfig.createDefault(),
            stubAsyncRunner,
            true)) {
      // PRE migrate, all storage is in unblinded storage
      assertUnblindedStorageCounts(
          database.getColumnCounts(),
          finalizedBlocks,
          unblindedHotBlockCount,
          unblindedHotBlockCount);
      // checkpoint epochs doesn't change, so it is always the expected size for blinded storage and
      // unblinded
      assertBlindedStorageCounts(database.getColumnCounts(), 0, unblindedHotBlockCount, 0, 0);

      database.migrate();
      // migrated hot blocks only, plus 1 finalized block initially
      assertUnblindedStorageCounts(
          database.getColumnCounts(), finalizedBlocks - 1, 0, unblindedHotBlockCount);
      assertBlindedStorageCounts(
          database.getColumnCounts(),
          unblindedHotBlockCount + 1,
          unblindedHotBlockCount,
          1,
          unblindedHotBlockCount + 1);

      assertThat(stubAsyncRunner.countDelayedActions()).isEqualTo(1);
      // run async action, then will have all blinded blocks
      stubAsyncRunner.executeQueuedActions();
      assertThat(stubAsyncRunner.hasDelayedActions()).isFalse();
      assertUnblindedStorageCounts(database.getColumnCounts(), 0, 0, unblindedHotBlockCount);
      assertBlindedStorageCounts(
          database.getColumnCounts(),
          totalBlocks,
          unblindedHotBlockCount,
          finalizedBlocks,
          totalBlocks);
    }
  }

  private void assertUnblindedStorageCounts(
      final Map<String, Long> counts,
      final long expectedFinalizedBlocks,
      final long expectedHotBlocks,
      final long expectedCheckpoints) {
    final Map<String, Long> expected =
        Map.of(
            "FINALIZED_BLOCKS_BY_SLOT", expectedFinalizedBlocks,
            "SLOTS_BY_FINALIZED_ROOT", expectedFinalizedBlocks,
            "HOT_BLOCKS_BY_ROOT", expectedHotBlocks,
            "HOT_BLOCK_CHECKPOINT_EPOCHS_BY_ROOT", expectedCheckpoints);
    assertThat(counts).containsAllEntriesOf(expected);
  }

  private void assertBlindedStorageCounts(
      final Map<String, Long> counts,
      final long expectedBlindedBlocks,
      final long expectedCheckpoints,
      final long expectedFinalizedBlockIndices,
      final long expectedPayloads) {
    final Map<String, Long> expected =
        Map.of(
            "HOT_BLOCK_CHECKPOINT_EPOCHS_BY_ROOT", expectedCheckpoints,
            "BLINDED_BLOCKS_BY_ROOT", expectedBlindedBlocks,
            "FINALIZED_BLOCK_ROOT_BY_SLOT", expectedFinalizedBlockIndices,
            "EXECUTION_PAYLOAD_BY_BLOCK_ROOT", expectedPayloads);
    assertThat(counts).containsAllEntriesOf(expected);
  }

  private void setupStorage(final Path tempDir, final int blockCount, final int finalizedEpoch)
      throws Exception {

    try (final StorageSystem storage =
        fullBlockContext.createFileBasedStorage(
            spec, tempDir, StateStorageMode.PRUNE, StoreConfig.createDefault(), true)) {
      recentChainData = storage.recentChainData();
      SignedBlockAndState genesisBlockAndState = chainBuilder.generateGenesis();
      recentChainData.initializeFromGenesis(genesisBlockAndState.getState(), UInt64.ZERO);
      UpdatableStore.StoreTransaction transaction = recentChainData.startStoreTransaction();
      final List<SignedBlockAndState> blocks = chainBuilder.generateBlocksUpToSlot(blockCount - 1);
      add(blocks);

      assertThat(transaction.commit()).isCompleted();
      if (finalizedEpoch > 0) {
        final int slotsPerEpoch = spec.getGenesisSpec().getSlotsPerEpoch();
        final int finalizedBlock = (finalizedEpoch * slotsPerEpoch) - 1;
        final int justifiedBlock = finalizedBlock + slotsPerEpoch;
        transaction = recentChainData.startStoreTransaction();
        transaction.setJustifiedCheckpoint(
            getCheckpointForBlock(blocks.get(justifiedBlock).getBlock()));
        transaction.setFinalizedCheckpoint(
            getCheckpointForBlock(blocks.get(finalizedBlock).getBlock()), false);
        assertThat(transaction.commit()).isCompleted();
      }
    }
  }

  private Checkpoint getCheckpointForBlock(final SignedBeaconBlock block) {
    final UInt64 blockEpoch = spec.computeEpochAtSlot(block.getSlot());
    final UInt64 blockEpochBoundary = spec.computeStartSlotAtEpoch(blockEpoch);
    final UInt64 checkpointEpoch =
        equivalentLongs(block.getSlot(), blockEpochBoundary) ? blockEpoch : blockEpoch.plus(ONE);
    return new Checkpoint(checkpointEpoch, block.getMessage().hashTreeRoot());
  }

  private boolean equivalentLongs(final UInt64 valA, final UInt64 valB) {
    return valA.compareTo(valB) == 0;
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
