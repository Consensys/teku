/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.artemis.storage;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;

import com.google.common.collect.Streams;
import com.google.common.primitives.UnsignedLong;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.junit.TempDirectory;
import org.apache.tuweni.junit.TempDirectoryExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockBody;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.storage.Store.Transaction;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.bls.BLSSignature;
import tech.pegasys.artemis.util.config.Constants;

@ExtendWith(TempDirectoryExtension.class)
class MapDbDatabaseTest {
  private static final BeaconState GENESIS_STATE =
      DataStructureUtil.randomBeaconState(UnsignedLong.ZERO, 1);

  private SignedBeaconBlock checkpoint1Block;
  private SignedBeaconBlock checkpoint2Block;
  private SignedBeaconBlock checkpoint3Block;

  private Checkpoint checkpoint1;
  private Checkpoint checkpoint2;
  private Checkpoint checkpoint3;

  private Database database = MapDbDatabase.createInMemory();
  private final List<DatabaseUpdateResult> updateResults = new ArrayList<>();
  private final TransactionPrecommit databaseTransactionPrecommit =
      updateEvent -> {
        final DatabaseUpdateResult result = database.update(updateEvent);
        updateResults.add(result);
        return SafeFuture.completedFuture(result);
      };
  private final Store store = Store.get_genesis_store(GENESIS_STATE);
  private final BeaconBlock genesisBlock =
      store.getBlockRoots().stream()
          .map(store::getBlock)
          .filter(b -> b.getSlot().equals(UnsignedLong.valueOf(Constants.GENESIS_SLOT)))
          .findFirst()
          .get();
  private final Checkpoint genesisCheckpoint = store.getFinalizedCheckpoint();

  private int seed = 498242;

  @BeforeEach
  public void recordGenesis() {
    database.storeGenesis(store);

    checkpoint1Block = blockAtEpoch(6);
    checkpoint2Block = blockAtEpoch(7);
    checkpoint3Block = blockAtEpoch(8);

    checkpoint1 =
        new Checkpoint(UnsignedLong.valueOf(6), checkpoint1Block.getMessage().hash_tree_root());
    checkpoint2 =
        new Checkpoint(UnsignedLong.valueOf(7), checkpoint2Block.getMessage().hash_tree_root());
    checkpoint3 =
        new Checkpoint(UnsignedLong.valueOf(8), checkpoint3Block.getMessage().hash_tree_root());
  }

  @Test
  public void shouldRecreateOriginalGenesisStore() {
    final Store memoryStore = database.createMemoryStore();
    assertThat(memoryStore).isEqualToIgnoringGivenFields(store, "time", "lock", "readLock");
  }

  @Test
  public void shouldGetHotBlockByRoot() {
    final Transaction transaction = store.startTransaction(databaseTransactionPrecommit);
    final SignedBeaconBlock block1 = blockAtSlot(1);
    final SignedBeaconBlock block2 = blockAtSlot(2);
    final Bytes32 block1Root = block1.getMessage().hash_tree_root();
    final Bytes32 block2Root = block2.getMessage().hash_tree_root();
    transaction.putBlock(block1Root, block1);
    transaction.putBlock(block2Root, block2);

    commit(transaction);

    assertThat(database.getSignedBlock(block1Root)).contains(block1);
    assertThat(database.getSignedBlock(block2Root)).contains(block2);
  }

  private void commit(final Transaction transaction) {
    assertThat(transaction.commit()).isCompleted();
  }

  @Test
  public void shouldGetHotStateByRoot() {
    final Transaction transaction = store.startTransaction(databaseTransactionPrecommit);
    final BeaconState state1 = DataStructureUtil.randomBeaconState(seed++);
    final BeaconState state2 = DataStructureUtil.randomBeaconState(seed++);
    final Bytes32 block1Root = Bytes32.fromHexString("0x1234");
    final Bytes32 block2Root = Bytes32.fromHexString("0x5822");
    transaction.putBlockState(block1Root, state1);
    transaction.putBlockState(block2Root, state2);

    commit(transaction);

    assertThat(database.getState(block1Root)).contains(state1);
    assertThat(database.getState(block2Root)).contains(state2);
  }

  @Test
  public void shouldStoreSingleValueFields() {
    addBlocks(checkpoint1Block, checkpoint2Block, checkpoint3Block);

    final Transaction transaction = store.startTransaction(databaseTransactionPrecommit);
    transaction.setGenesis_time(UnsignedLong.valueOf(3));
    transaction.setFinalizedCheckpoint(checkpoint1);
    transaction.setJustifiedCheckpoint(checkpoint2);
    transaction.setBestJustifiedCheckpoint(checkpoint3);

    commit(transaction);

    final Store result = database.createMemoryStore();

    assertThat(result.getGenesisTime()).isEqualTo(transaction.getGenesisTime());
    assertThat(result.getFinalizedCheckpoint()).isEqualTo(transaction.getFinalizedCheckpoint());
    assertThat(result.getJustifiedCheckpoint()).isEqualTo(transaction.getJustifiedCheckpoint());
    assertThat(result.getBestJustifiedCheckpoint())
        .isEqualTo(transaction.getBestJustifiedCheckpoint());
  }

  @Test
  public void shouldStoreLatestMessageFromEachValidator() {
    final UnsignedLong validator1 = UnsignedLong.valueOf(1);
    final UnsignedLong validator2 = UnsignedLong.valueOf(2);
    final UnsignedLong validator3 = UnsignedLong.valueOf(3);

    addBlocks(checkpoint1Block, checkpoint2Block, checkpoint3Block);

    final Transaction transaction = store.startTransaction(databaseTransactionPrecommit);
    transaction.putLatestMessage(validator1, checkpoint1);
    transaction.putLatestMessage(validator2, checkpoint2);
    transaction.putLatestMessage(validator3, checkpoint1);
    commit(transaction);

    final Store result1 = database.createMemoryStore();
    assertThat(result1.getLatestMessage(validator1)).isEqualTo(checkpoint1);
    assertThat(result1.getLatestMessage(validator2)).isEqualTo(checkpoint2);
    assertThat(result1.getLatestMessage(validator3)).isEqualTo(checkpoint1);

    // Should overwrite when later changes are made.
    final Transaction transaction2 = store.startTransaction(databaseTransactionPrecommit);
    transaction2.putLatestMessage(validator3, checkpoint2);
    commit(transaction2);

    final Store result2 = database.createMemoryStore();
    assertThat(result2.getLatestMessage(validator1)).isEqualTo(checkpoint1);
    assertThat(result2.getLatestMessage(validator2)).isEqualTo(checkpoint2);
    assertThat(result2.getLatestMessage(validator3)).isEqualTo(checkpoint2);
  }

  @Test
  public void shouldStoreCheckpointStates() {
    final Transaction transaction = store.startTransaction(databaseTransactionPrecommit);

    addBlocks(checkpoint1Block, checkpoint2Block, checkpoint3Block);

    final Checkpoint forkCheckpoint =
        new Checkpoint(checkpoint1.getEpoch(), Bytes32.fromHexString("0x88677727"));
    transaction.putCheckpointState(checkpoint1, GENESIS_STATE);
    transaction.putCheckpointState(checkpoint2, DataStructureUtil.randomBeaconState(seed++));
    transaction.putCheckpointState(forkCheckpoint, DataStructureUtil.randomBeaconState(seed++));

    commit(transaction);

    final Store result = database.createMemoryStore();
    assertThat(result.getCheckpointState(checkpoint1))
        .isEqualTo(transaction.getCheckpointState(checkpoint1));
    assertThat(result.getCheckpointState(checkpoint2))
        .isEqualTo(transaction.getCheckpointState(checkpoint2));
    assertThat(result.getCheckpointState(forkCheckpoint))
        .isEqualTo(transaction.getCheckpointState(forkCheckpoint));
  }

  @Test
  public void shouldRemoveCheckpointStatesPriorToFinalizedCheckpoint() {
    final Checkpoint earlyCheckpoint = createCheckpoint(1);
    final Checkpoint middleCheckpoint = createCheckpoint(2);
    final Checkpoint laterCheckpoint = createCheckpoint(3);

    // First store the initial checkpoints.
    final Transaction transaction1 = store.startTransaction(databaseTransactionPrecommit);
    transaction1.putCheckpointState(earlyCheckpoint, DataStructureUtil.randomBeaconState(seed++));
    transaction1.putCheckpointState(middleCheckpoint, DataStructureUtil.randomBeaconState(seed++));
    transaction1.putCheckpointState(laterCheckpoint, DataStructureUtil.randomBeaconState(seed++));
    commit(transaction1);
    assertLatestUpdateResultPrunedCollectionsAreEmpty();

    // Now update the finalized checkpoint
    final Set<BeaconBlock> blocksToPrune =
        Set.of(genesisBlock, store.getBlock(earlyCheckpoint.getRoot()));
    final Transaction transaction2 = store.startTransaction(databaseTransactionPrecommit);
    transaction2.setFinalizedCheckpoint(middleCheckpoint);
    commit(transaction2);

    final Set<Bytes32> prunedBlocks =
        blocksToPrune.stream().map(BeaconBlock::hash_tree_root).collect(Collectors.toSet());
    final Set<Checkpoint> prunedCheckpoints = Set.of(genesisCheckpoint, earlyCheckpoint);
    assertLatestUpdateResultContains(prunedBlocks, prunedCheckpoints);

    // Check pruned data has been removed from store
    assertStoreWasPruned(store, prunedBlocks, prunedCheckpoints);

    final Store result = database.createMemoryStore();
    assertThat(result.getCheckpointState(earlyCheckpoint)).isNull();
    assertThat(result.getCheckpointState(middleCheckpoint))
        .isEqualTo(transaction1.getCheckpointState(middleCheckpoint));
    assertThat(result.getCheckpointState(laterCheckpoint))
        .isEqualTo(transaction1.getCheckpointState(laterCheckpoint));
    assertStoreWasPruned(result, prunedBlocks, prunedCheckpoints);
  }

  @Test
  public void shouldLoadHotBlocksAndStatesIntoMemoryStore() {
    final Bytes32 genesisRoot = store.getFinalizedCheckpoint().getRoot();
    final Transaction transaction = store.startTransaction(databaseTransactionPrecommit);
    final SignedBeaconBlock block1 = blockAtSlot(1);
    final SignedBeaconBlock block2 = blockAtSlot(2);
    final BeaconState state1 = DataStructureUtil.randomBeaconState(seed++);
    final BeaconState state2 = DataStructureUtil.randomBeaconState(seed++);
    final Bytes32 block1Root = block1.getMessage().hash_tree_root();
    final Bytes32 block2Root = block2.getMessage().hash_tree_root();
    transaction.putBlock(block1Root, block1);
    transaction.putBlock(block2Root, block2);
    transaction.putBlockState(block1Root, state1);
    transaction.putBlockState(block2Root, state2);

    commit(transaction);

    final Store result = database.createMemoryStore();
    assertThat(result.getSignedBlock(genesisRoot)).isEqualTo(store.getSignedBlock(genesisRoot));
    assertThat(result.getSignedBlock(block1Root)).isEqualTo(block1);
    assertThat(result.getSignedBlock(block2Root)).isEqualTo(block2);
    assertThat(result.getBlockState(block1Root)).isEqualTo(state1);
    assertThat(result.getBlockState(block2Root)).isEqualTo(state2);
    assertThat(result.getBlockRoots()).containsOnly(genesisRoot, block1Root, block2Root);
  }

  @Test
  public void shouldRemoveHotBlocksAndStatesOnceEpochIsFinalized() {
    final Transaction transaction = store.startTransaction(databaseTransactionPrecommit);
    final SignedBeaconBlock block1 = blockAtSlot(1);
    final SignedBeaconBlock block2 = blockAtSlot(2);
    final SignedBeaconBlock unfinalizedBlock =
        blockAtSlot(compute_start_slot_at_epoch(UnsignedLong.valueOf(2)).longValue());

    final BeaconState state1 = DataStructureUtil.randomBeaconState(UnsignedLong.valueOf(1), seed++);
    final BeaconState state2 = DataStructureUtil.randomBeaconState(UnsignedLong.valueOf(2), seed++);
    final BeaconState unfinalizedState =
        DataStructureUtil.randomBeaconState(
            compute_start_slot_at_epoch(UnsignedLong.valueOf(2)), seed++);

    final Bytes32 block1Root = block1.getMessage().hash_tree_root();
    final Bytes32 block2Root = block2.getMessage().hash_tree_root();
    final Bytes32 unfinalizedBlockRoot = unfinalizedBlock.getMessage().hash_tree_root();

    transaction.putBlock(block1Root, block1);
    transaction.putBlock(block2Root, block2);
    transaction.putBlock(unfinalizedBlockRoot, unfinalizedBlock);
    transaction.putBlockState(block1Root, state1);
    transaction.putBlockState(block2Root, state2);
    transaction.putBlockState(unfinalizedBlockRoot, unfinalizedState);

    commit(transaction);

    finalizeEpoch(UnsignedLong.ONE, block2Root);

    final Store result = database.createMemoryStore();
    assertThat(result.getSignedBlock(block1Root)).isNull();
    assertThat(result.getSignedBlock(block2Root)).isEqualTo(block2);
    assertThat(result.getSignedBlock(unfinalizedBlockRoot)).isEqualTo(unfinalizedBlock);
    assertThat(result.getBlockState(block1Root)).isNull();
    assertThat(result.getBlockState(block2Root)).isEqualTo(state2);
    assertThat(result.getBlockState(unfinalizedBlockRoot)).isEqualTo(unfinalizedState);
    assertThat(result.getBlockRoots()).containsOnly(block2Root, unfinalizedBlockRoot);
  }

  @Test
  public void shouldRecordFinalizedBlocksAndStates() {
    final SignedBeaconBlock block1 = blockAtSlot(1, store.getFinalizedCheckpoint().getRoot());
    final SignedBeaconBlock block2 = blockAtSlot(2, block1);
    final SignedBeaconBlock block3 = blockAtSlot(3, block2);
    // Few skipped slots
    final SignedBeaconBlock block7 = blockAtSlot(7, block3);
    final SignedBeaconBlock block8 = blockAtSlot(8, block7);
    final SignedBeaconBlock block9 = blockAtSlot(9, block8);

    // Create some blocks on a different fork
    final SignedBeaconBlock forkBlock6 = blockAtSlot(6, block1);
    final SignedBeaconBlock forkBlock7 = blockAtSlot(7, forkBlock6);
    final SignedBeaconBlock forkBlock8 = blockAtSlot(8, forkBlock7);
    final SignedBeaconBlock forkBlock9 = blockAtSlot(9, forkBlock8);

    addBlocks(
        block1,
        block2,
        block3,
        block7,
        block8,
        block9,
        forkBlock6,
        forkBlock7,
        forkBlock8,
        forkBlock9);
    assertThat(database.getSignedBlock(block7.getMessage().hash_tree_root())).contains(block7);
    assertLatestUpdateResultPrunedCollectionsAreEmpty();

    finalizeEpoch(UnsignedLong.ONE, block7.getMessage().hash_tree_root());

    // Upon finalization, we should prune data
    final Set<Bytes32> blocksToPrune =
        Set.of(block1, block2, block3, forkBlock6).stream()
            .map(b -> b.getMessage().hash_tree_root())
            .collect(Collectors.toSet());
    blocksToPrune.add(genesisBlock.hash_tree_root());
    final Set<Checkpoint> checkpointsToPrune = Set.of(genesisCheckpoint);
    assertLatestUpdateResultContains(blocksToPrune, checkpointsToPrune);

    // Check data was pruned from store
    assertStoreWasPruned(store, blocksToPrune, checkpointsToPrune);

    assertOnlyHotBlocks(block7, block8, block9, forkBlock7, forkBlock8, forkBlock9);
    assertBlocksFinalized(block1, block2, block3, block7);
    assertGetLatestFinalizedRootAtSlotReturnsFinalizedBlocks(block1, block2, block3, block7);

    // Should still be able to retrieve finalized blocks by root
    assertThat(database.getSignedBlock(block1.getMessage().hash_tree_root())).contains(block1);
  }

  @Test
  public void shouldPersistOnDisk(@TempDirectory final Path tempDir) throws Exception {
    database = MapDbDatabase.createOnDisk(tempDir.toFile(), false);
    database.storeGenesis(store);

    final SignedBeaconBlock block1 = blockAtSlot(1, store.getFinalizedCheckpoint().getRoot());
    final SignedBeaconBlock block2 = blockAtSlot(2, block1);
    final SignedBeaconBlock block3 = blockAtSlot(3, block2);
    // Few skipped slots
    final SignedBeaconBlock block7 = blockAtSlot(7, block3);
    final SignedBeaconBlock block8 = blockAtSlot(8, block7);
    final SignedBeaconBlock block9 = blockAtSlot(9, block8);

    // Create some blocks on a different fork
    final SignedBeaconBlock forkBlock6 = blockAtSlot(6, block1);
    final SignedBeaconBlock forkBlock7 = blockAtSlot(7, forkBlock6);
    final SignedBeaconBlock forkBlock8 = blockAtSlot(8, forkBlock7);
    final SignedBeaconBlock forkBlock9 = blockAtSlot(9, forkBlock8);

    addBlocks(
        block1,
        block2,
        block3,
        block7,
        block8,
        block9,
        forkBlock6,
        forkBlock7,
        forkBlock8,
        forkBlock9);
    assertThat(database.getSignedBlock(block7.getMessage().hash_tree_root())).contains(block7);
    assertLatestUpdateResultPrunedCollectionsAreEmpty();

    finalizeEpoch(UnsignedLong.ONE, block7.getMessage().hash_tree_root());

    // Upon finalization, we should prune data
    final Set<Bytes32> blocksToPrune =
        Set.of(block1, block2, block3, forkBlock6).stream()
            .map(b -> b.getMessage().hash_tree_root())
            .collect(Collectors.toSet());
    blocksToPrune.add(genesisBlock.hash_tree_root());
    final Set<Checkpoint> checkpointsToPrune = Set.of(genesisCheckpoint);
    assertLatestUpdateResultContains(blocksToPrune, checkpointsToPrune);

    // Check data was pruned from store
    assertStoreWasPruned(store, blocksToPrune, checkpointsToPrune);

    // Close and re-read from disk store.
    database.close();
    database = MapDbDatabase.createOnDisk(tempDir.toFile(), true);
    assertOnlyHotBlocks(block7, block8, block9, forkBlock7, forkBlock8, forkBlock9);
    assertBlocksFinalized(block1, block2, block3, block7);
    assertGetLatestFinalizedRootAtSlotReturnsFinalizedBlocks(block1, block2, block3, block7);

    // Should still be able to retrieve finalized blocks by root
    assertThat(database.getSignedBlock(block1.getMessage().hash_tree_root())).contains(block1);
  }

  private void assertBlocksFinalized(final SignedBeaconBlock... blocks) {
    for (SignedBeaconBlock block : blocks) {
      assertThat(database.getFinalizedRootAtSlot(block.getSlot()))
          .describedAs("Block root at slot %s", block.getSlot())
          .contains(block.getMessage().hash_tree_root());
    }
  }

  private void assertGetLatestFinalizedRootAtSlotReturnsFinalizedBlocks(
      final SignedBeaconBlock... blocks) {
    final UnsignedLong genesisSlot = UnsignedLong.valueOf(Constants.GENESIS_SLOT);
    final Bytes32 genesisRoot = database.getFinalizedRootAtSlot(genesisSlot).get();
    final SignedBeaconBlock genesisBlock = database.getSignedBlock(genesisRoot).get();

    final List<SignedBeaconBlock> finalizedBlocks =
        Streams.concat(Stream.of(genesisBlock), Arrays.stream(blocks))
            .sorted(Comparator.comparing(SignedBeaconBlock::getSlot))
            .collect(toList());

    for (int i = 1; i < finalizedBlocks.size(); i++) {
      final SignedBeaconBlock currentBlock = finalizedBlocks.get(i - 1);
      final SignedBeaconBlock nextBlock = finalizedBlocks.get(i);
      // All slots from the current block up to and excluding the next block should return the
      // current block
      for (long slot = currentBlock.getSlot().longValue();
          slot < nextBlock.getSlot().longValue();
          slot++) {
        assertThat(database.getLatestFinalizedRootAtSlot(UnsignedLong.valueOf(slot)))
            .describedAs("Latest finalized at block root at slot %s", slot)
            .contains(currentBlock.getMessage().hash_tree_root());
      }
    }

    // Check that last block
    final SignedBeaconBlock lastFinalizedBlock = finalizedBlocks.get(finalizedBlocks.size() - 1);
    for (int i = 0; i < 10; i++) {
      final UnsignedLong slot = lastFinalizedBlock.getSlot().plus(UnsignedLong.valueOf(i));
      assertThat(database.getLatestFinalizedRootAtSlot(slot))
          .describedAs("Latest finalized at block root at slot %s", slot)
          .contains(lastFinalizedBlock.getMessage().hash_tree_root());
    }
  }

  private void assertOnlyHotBlocks(final SignedBeaconBlock... blocks) {
    final Store memoryStore = database.createMemoryStore();
    assertThat(memoryStore.getBlockRoots())
        .hasSameElementsAs(
            Stream.of(blocks).map(block -> block.getMessage().hash_tree_root()).collect(toList()));
  }

  private void assertLatestUpdateResultContains(
      final Set<Bytes32> blockRoots, final Set<Checkpoint> checkpoints) {
    final DatabaseUpdateResult latestResult = getLatestUpdateResult();
    assertThat(latestResult.getPrunedBlockRoots()).containsExactlyInAnyOrderElementsOf(blockRoots);
    assertThat(latestResult.getPrunedCheckpoints())
        .containsExactlyInAnyOrderElementsOf(checkpoints);
  }

  private void assertLatestUpdateResultPrunedCollectionsAreEmpty() {
    final DatabaseUpdateResult latestResult = getLatestUpdateResult();
    assertThat(latestResult.getPrunedBlockRoots()).isEmpty();
    assertThat(latestResult.getPrunedCheckpoints()).isEmpty();
  }

  private void assertStoreWasPruned(
      final Store store, final Set<Bytes32> prunedBlocks, final Set<Checkpoint> prunedCheckpoints) {
    // Check pruned data has been removed from store
    for (Bytes32 prunedBlock : prunedBlocks) {
      assertThat(store.getBlock(prunedBlock)).isNull();
    }
    for (Checkpoint prunedCheckpoint : prunedCheckpoints) {
      assertThat(store.getCheckpointState(prunedCheckpoint)).isNull();
    }
  }

  private void addBlocks(final SignedBeaconBlock... blocks) {
    final Transaction transaction = store.startTransaction(databaseTransactionPrecommit);
    for (SignedBeaconBlock block : blocks) {
      transaction.putBlock(block.getMessage().hash_tree_root(), block);
    }
    commit(transaction);
  }

  private void finalizeEpoch(final UnsignedLong epoch, final Bytes32 root) {
    final Transaction transaction = store.startTransaction(databaseTransactionPrecommit);
    transaction.setFinalizedCheckpoint(new Checkpoint(epoch, root));
    commit(transaction);
  }

  private Checkpoint createCheckpoint(final long epoch) {
    final SignedBeaconBlock block = blockAtEpoch(epoch);
    addBlocks(block);
    return new Checkpoint(UnsignedLong.valueOf(epoch), block.getMessage().hash_tree_root());
  }

  private SignedBeaconBlock blockAtEpoch(final long epoch) {
    final UnsignedLong slot = compute_start_slot_at_epoch(UnsignedLong.valueOf(epoch));
    return blockAtSlot(slot.longValue(), DataStructureUtil.randomBytes32(epoch));
  }

  private SignedBeaconBlock blockAtSlot(final long slot) {
    return blockAtSlot(slot, store.getFinalizedCheckpoint().getRoot());
  }

  private SignedBeaconBlock blockAtSlot(final long slot, final SignedBeaconBlock parent) {
    return blockAtSlot(slot, parent.getMessage().hash_tree_root());
  }

  private SignedBeaconBlock blockAtSlot(final long slot, final Bytes32 parentRoot) {
    return new SignedBeaconBlock(
        new BeaconBlock(
            UnsignedLong.valueOf(slot), parentRoot, Bytes32.ZERO, new BeaconBlockBody()),
        BLSSignature.empty());
  }

  private DatabaseUpdateResult getLatestUpdateResult() {
    return updateResults.get(updateResults.size() - 1);
  }
}
