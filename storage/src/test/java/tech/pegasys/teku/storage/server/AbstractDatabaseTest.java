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

package tech.pegasys.teku.storage.server;

import static java.util.Map.entry;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;

import com.google.common.collect.Streams;
import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSKeyGenerator;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.core.ChainBuilder;
import tech.pegasys.teku.core.ChainBuilder.BlockOptions;
import tech.pegasys.teku.core.StateTransitionException;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.operations.DepositWithIndex;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.datastructures.util.MerkleTree;
import tech.pegasys.teku.datastructures.util.OptimizedMerkleTree;
import tech.pegasys.teku.storage.Store;
import tech.pegasys.teku.storage.Store.Transaction;
import tech.pegasys.teku.storage.api.TrackingStorageUpdateChannel;
import tech.pegasys.teku.storage.events.StorageUpdateResult;
import tech.pegasys.teku.util.config.Constants;
import tech.pegasys.teku.util.config.StateStorageMode;

public abstract class AbstractDatabaseTest {

  protected static final List<BLSKeyPair> VALIDATOR_KEYS = BLSKeyGenerator.generateKeyPairs(3);

  protected final ChainBuilder chainBuilder = ChainBuilder.create(VALIDATOR_KEYS);
  protected Store store;

  protected SignedBlockAndState genesisBlockAndState;
  protected SignedBlockAndState checkpoint1BlockAndState;
  protected SignedBlockAndState checkpoint2BlockAndState;
  protected SignedBlockAndState checkpoint3BlockAndState;

  protected Checkpoint genesisCheckpoint;
  protected Checkpoint checkpoint1;
  protected Checkpoint checkpoint2;
  protected Checkpoint checkpoint3;

  protected Database database;
  protected TrackingStorageUpdateChannel storageUpdateChannel;

  protected List<Database> databases = new ArrayList<>();

  @BeforeEach
  public void setup() throws StateTransitionException {
    Constants.SLOTS_PER_EPOCH = 3;
    setupDatabase(StateStorageMode.ARCHIVE);

    genesisBlockAndState = chainBuilder.generateGenesis();
    genesisCheckpoint = getCheckpointForBlock(genesisBlockAndState.getBlock());
    while (chainBuilder.getLatestEpoch().longValue() < 3) {
      chainBuilder.generateNextBlock();
    }

    checkpoint1BlockAndState = chainBuilder.getLatestBlockAndStateAtEpochBoundary(1);
    checkpoint1 = chainBuilder.getCurrentCheckpointForEpoch(1);
    checkpoint2BlockAndState = chainBuilder.getLatestBlockAndStateAtEpochBoundary(2);
    checkpoint2 = chainBuilder.getCurrentCheckpointForEpoch(2);
    checkpoint3BlockAndState = chainBuilder.getLatestBlockAndStateAtEpochBoundary(3);
    checkpoint3 = chainBuilder.getCurrentCheckpointForEpoch(3);

    store = Store.getForkChoiceStore(genesisBlockAndState.getState());
    database.storeGenesis(store);
  }

  @AfterEach
  public void tearDown() throws Exception {
    Constants.setConstants("minimal");
    for (Database db : databases) {
      db.close();
    }
  }

  protected abstract Database createDatabase(final StateStorageMode storageMode);

  protected Database setupDatabase(final StateStorageMode storageMode) {
    database = createDatabase(storageMode);
    databases.add(database);
    storageUpdateChannel = new TrackingStorageUpdateChannel(database);
    return database;
  }

  @Test
  public void createMemoryStoreFromEmptyDatabase() {
    Database database = setupDatabase(StateStorageMode.ARCHIVE);
    assertThat(database.createMemoryStore()).isEmpty();
  }

  @Test
  public void shouldRecreateOriginalGenesisStore() {
    final Store memoryStore = database.createMemoryStore().orElseThrow();
    assertThat(memoryStore).isEqualToIgnoringGivenFields(store, "time", "lock", "readLock");
  }

  @Test
  public void shouldStoreBlockWithLargeSlot() throws StateTransitionException {
    final UnsignedLong slot = UnsignedLong.MAX_VALUE;
    final DataStructureUtil dataStructureUtil = new DataStructureUtil();
    final SignedBeaconBlock newBlock = dataStructureUtil.randomSignedBeaconBlock(slot);
    final Bytes32 root = newBlock.getMessage().hash_tree_root();

    final Transaction transaction = store.startTransaction(storageUpdateChannel);
    transaction.putBlock(root, newBlock);
    final UnsignedLong epoch = compute_epoch_at_slot(slot);
    transaction.setFinalizedCheckpoint(new Checkpoint(epoch, root));
    transaction.commit().reportExceptions();

    assertThat(database.getSignedBlock(root)).hasValue(newBlock);
    assertThat(database.getFinalizedRootAtSlot(slot)).hasValue(root);
  }

  @Test
  public void shouldGetHotBlockByRoot() {
    final Transaction transaction = store.startTransaction(storageUpdateChannel);
    final SignedBeaconBlock block1 = chainBuilder.getBlockAtSlot(1);
    final SignedBeaconBlock block2 = chainBuilder.getBlockAtSlot(2);

    transaction.putBlock(block1.getRoot(), block1);
    transaction.putBlock(block2.getRoot(), block2);

    commit(transaction);

    assertThat(database.getSignedBlock(block1.getRoot())).contains(block1);
    assertThat(database.getSignedBlock(block2.getRoot())).contains(block2);
  }

  protected void commit(final Transaction transaction) {
    assertThat(transaction.commit()).isCompleted();
  }

  @Test
  public void shouldPruneHotBlocksAddedOverMultipleSessions() throws Exception {
    final ChainBuilder forkA = chainBuilder.fork();
    final ChainBuilder forkB = chainBuilder.fork();

    // Set target slot at which to create duplicate blocks
    // and generate block options to make each block unique
    final UnsignedLong targetSlot = UnsignedLong.valueOf(10);
    final List<BlockOptions> blockOptions =
        chainBuilder
            .streamValidAttestationsForBlockAtSlot(targetSlot)
            .map(attestation -> BlockOptions.create().addAttestation(attestation))
            .limit(2)
            .collect(toList());

    // Create several different blocks at the same slot
    final SignedBlockAndState blockA = forkA.generateBlockAtSlot(targetSlot, blockOptions.get(0));
    final SignedBlockAndState blockB = forkB.generateBlockAtSlot(targetSlot, blockOptions.get(1));
    final SignedBlockAndState blockC = chainBuilder.generateBlockAtSlot(10);
    final Set<Bytes32> block10Roots = Set.of(blockA.getRoot(), blockB.getRoot(), blockC.getRoot());
    // Sanity check
    assertThat(block10Roots.size()).isEqualTo(3);

    // Add blocks at same height sequentially
    add(List.of(blockA));
    add(List.of(blockB));
    add(List.of(blockC));

    // Verify all blocks are available
    assertThat(store.getBlock(blockA.getRoot())).isEqualTo(blockA.getBlock().getMessage());
    assertThat(store.getBlock(blockB.getRoot())).isEqualTo(blockB.getBlock().getMessage());
    assertThat(store.getBlock(blockC.getRoot())).isEqualTo(blockC.getBlock().getMessage());

    // Finalize subsequent block to prune blocks a, b, and c
    final SignedBlockAndState finalBlock = chainBuilder.generateNextBlock();
    add(List.of(finalBlock));
    final UnsignedLong finalEpoch = chainBuilder.getLatestEpoch().plus(UnsignedLong.ONE);
    final Checkpoint finalizedCheckpoint = chainBuilder.getCurrentCheckpointForEpoch(finalEpoch);
    finalizeCheckpoint(finalizedCheckpoint);

    // Check pruning result
    final Set<Bytes32> rootsToPrune = new HashSet<>(block10Roots);
    rootsToPrune.add(genesisBlockAndState.getRoot());
    final StorageUpdateResult updateResult = getLatestUpdateResult();
    assertThat(updateResult.getPrunedBlockRoots())
        .containsExactlyInAnyOrderElementsOf(rootsToPrune);
    // Check that all blocks at slot 10 were pruned
    assertStoreWasPruned(store, rootsToPrune, Set.of(genesisCheckpoint));
  }

  @Test
  public void shouldGetHotStateByRoot() throws StateTransitionException {
    final SignedBlockAndState block1 = chainBuilder.getBlockAndStateAtSlot(1);
    final SignedBlockAndState block2 = chainBuilder.getBlockAndStateAtSlot(2);

    final Transaction transaction = store.startTransaction(storageUpdateChannel);
    transaction.putBlockState(block1.getRoot(), block1.getState());
    transaction.putBlockState(block2.getRoot(), block2.getState());
    commit(transaction);

    assertThat(database.getState(block1.getRoot())).contains(block1.getState());
    assertThat(database.getState(block2.getRoot())).contains(block2.getState());
  }

  @Test
  public void shouldStoreSingleValueFields() {
    addBlocks(
        checkpoint1BlockAndState.getBlock(),
        checkpoint2BlockAndState.getBlock(),
        checkpoint3BlockAndState.getBlock());

    final Transaction transaction = store.startTransaction(storageUpdateChannel);
    transaction.setGenesis_time(UnsignedLong.valueOf(3));
    transaction.setFinalizedCheckpoint(checkpoint1);
    transaction.setJustifiedCheckpoint(checkpoint2);
    transaction.setBestJustifiedCheckpoint(checkpoint3);

    commit(transaction);

    final Store result = database.createMemoryStore().orElseThrow();

    assertThat(result.getGenesisTime()).isEqualTo(transaction.getGenesisTime());
    assertThat(result.getFinalizedCheckpoint()).isEqualTo(transaction.getFinalizedCheckpoint());
    assertThat(result.getJustifiedCheckpoint()).isEqualTo(transaction.getJustifiedCheckpoint());
    assertThat(result.getBestJustifiedCheckpoint())
        .isEqualTo(transaction.getBestJustifiedCheckpoint());
  }

  @Test
  public void shouldStoreSingleValue_genesisTime() {
    final UnsignedLong newGenesisTime = UnsignedLong.valueOf(3);
    // Sanity check
    assertThat(store.getGenesisTime()).isNotEqualTo(newGenesisTime);

    final Transaction transaction = store.startTransaction(storageUpdateChannel);
    transaction.setGenesis_time(newGenesisTime);
    commit(transaction);

    final Store result = database.createMemoryStore().orElseThrow();
    assertThat(result.getGenesisTime()).isEqualTo(transaction.getGenesisTime());
  }

  @Test
  public void shouldStoreSingleValue_justifiedCheckpoint() {
    final Checkpoint newValue = checkpoint3;
    // Sanity check
    assertThat(store.getJustifiedCheckpoint()).isNotEqualTo(checkpoint3);

    final Transaction transaction = store.startTransaction(storageUpdateChannel);
    transaction.setJustifiedCheckpoint(newValue);
    commit(transaction);

    final Store result = database.createMemoryStore().orElseThrow();
    assertThat(result.getJustifiedCheckpoint()).isEqualTo(newValue);
  }

  @Test
  public void shouldStoreSingleValue_finalizedCheckpoint() {
    final Checkpoint newValue = checkpoint3;
    // Sanity check
    assertThat(store.getFinalizedCheckpoint()).isNotEqualTo(checkpoint3);

    final Transaction transaction = store.startTransaction(storageUpdateChannel);
    transaction.setFinalizedCheckpoint(newValue);
    commit(transaction);

    final Store result = database.createMemoryStore().orElseThrow();
    assertThat(result.getFinalizedCheckpoint()).isEqualTo(newValue);
  }

  @Test
  public void shouldStoreSingleValue_bestJustifiedCheckpoint() {
    final Checkpoint newValue = checkpoint3;
    // Sanity check
    assertThat(store.getBestJustifiedCheckpoint()).isNotEqualTo(checkpoint3);

    final Transaction transaction = store.startTransaction(storageUpdateChannel);
    transaction.setBestJustifiedCheckpoint(newValue);
    commit(transaction);

    final Store result = database.createMemoryStore().orElseThrow();
    assertThat(result.getBestJustifiedCheckpoint()).isEqualTo(newValue);
  }

  @Test
  public void shouldStoreSingleValue_singleBlock() {
    final SignedBeaconBlock newBlock = checkpoint3BlockAndState.getBlock();
    final Bytes32 newBlockRoot = newBlock.getMessage().hash_tree_root();
    // Sanity check
    assertThat(store.getBlock(newBlockRoot)).isNull();

    final Transaction transaction = store.startTransaction(storageUpdateChannel);
    transaction.putBlock(newBlockRoot, newBlock);
    commit(transaction);

    final Store result = database.createMemoryStore().orElseThrow();
    assertThat(result.getBlock(newBlockRoot)).isEqualTo(newBlock.getMessage());
  }

  @Test
  public void shouldStoreSingleValue_singleBlockState() {
    final BeaconState newState = checkpoint3BlockAndState.getState();
    final Bytes32 blockRoot = checkpoint3BlockAndState.getBlock().getMessage().hash_tree_root();
    // Sanity check
    assertThat(store.getBlockState(blockRoot)).isNull();

    final Transaction transaction = store.startTransaction(storageUpdateChannel);
    transaction.putBlockState(blockRoot, newState);
    commit(transaction);

    final Store result = database.createMemoryStore().orElseThrow();
    assertThat(result.getBlockState(blockRoot)).isEqualTo(newState);
  }

  @Test
  public void shouldStoreSingleValue_singleCheckpointState() {
    final Checkpoint checkpoint = checkpoint3;
    final BeaconState newState = checkpoint3BlockAndState.getState();
    // Sanity check
    assertThat(store.getCheckpointState(checkpoint)).isNull();

    final Transaction transaction = store.startTransaction(storageUpdateChannel);
    transaction.putCheckpointState(checkpoint, newState);
    commit(transaction);

    final Store result = database.createMemoryStore().orElseThrow();
    assertThat(result.getCheckpointState(checkpoint)).isEqualTo(newState);
  }

  @Test
  public void shouldStoreCheckpointStates() {
    final Transaction transaction = store.startTransaction(storageUpdateChannel);

    addBlocks(
        checkpoint1BlockAndState.getBlock(),
        checkpoint2BlockAndState.getBlock(),
        checkpoint3BlockAndState.getBlock());

    transaction.putCheckpointState(checkpoint1, checkpoint1BlockAndState.getState());
    transaction.putCheckpointState(checkpoint2, checkpoint2BlockAndState.getState());
    transaction.putCheckpointState(checkpoint3, checkpoint3BlockAndState.getState());

    commit(transaction);

    final Store result = database.createMemoryStore().orElseThrow();
    assertThat(result.getCheckpointState(checkpoint1))
        .isEqualTo(checkpoint1BlockAndState.getState());
    assertThat(result.getCheckpointState(checkpoint2))
        .isEqualTo(checkpoint2BlockAndState.getState());
    assertThat(result.getCheckpointState(checkpoint3))
        .isEqualTo(checkpoint3BlockAndState.getState());
  }

  @Test
  public void shouldRemoveCheckpointStatesPriorToFinalizedCheckpoint() {
    // First store the initial checkpoints.
    final Transaction transaction1 = store.startTransaction(storageUpdateChannel);
    // Add blocks
    transaction1.putBlock(checkpoint1BlockAndState.getRoot(), checkpoint1BlockAndState.getBlock());
    transaction1.putBlock(checkpoint2BlockAndState.getRoot(), checkpoint2BlockAndState.getBlock());
    transaction1.putBlock(checkpoint3BlockAndState.getRoot(), checkpoint3BlockAndState.getBlock());
    // Add checkpoints
    transaction1.putCheckpointState(checkpoint1, checkpoint1BlockAndState.getState());
    transaction1.putCheckpointState(checkpoint2, checkpoint2BlockAndState.getState());
    transaction1.putCheckpointState(checkpoint3, checkpoint3BlockAndState.getState());
    commit(transaction1);
    assertLatestUpdateResultPrunedCollectionsAreEmpty();

    // Now update the finalized checkpoint
    final Set<SignedBeaconBlock> blocksToPrune =
        Set.of(genesisBlockAndState.getBlock(), checkpoint1BlockAndState.getBlock());
    final Transaction transaction2 = store.startTransaction(storageUpdateChannel);
    transaction2.setFinalizedCheckpoint(checkpoint2);
    commit(transaction2);

    final Set<Bytes32> expectedPrunedBlocks =
        blocksToPrune.stream()
            .map(SignedBeaconBlock::getMessage)
            .map(BeaconBlock::hash_tree_root)
            .collect(Collectors.toSet());
    final Set<Checkpoint> expectedPrunedCheckpoints = Set.of(genesisCheckpoint, checkpoint1);
    assertLatestUpdateResultContains(expectedPrunedBlocks, expectedPrunedCheckpoints);

    // Check pruned data has been removed from store
    assertStoreWasPruned(store, expectedPrunedBlocks, expectedPrunedCheckpoints);

    final Store result = database.createMemoryStore().orElseThrow();
    assertThat(result.getCheckpointState(checkpoint1)).isNull();
    assertThat(result.getCheckpointState(checkpoint2))
        .isEqualTo(transaction1.getCheckpointState(checkpoint2));
    assertThat(result.getCheckpointState(checkpoint3))
        .isEqualTo(transaction1.getCheckpointState(checkpoint3));
    assertStoreWasPruned(result, expectedPrunedBlocks, expectedPrunedCheckpoints);
  }

  @Test
  public void shouldLoadHotBlocksAndStatesIntoMemoryStore() {
    final Bytes32 genesisRoot = genesisBlockAndState.getRoot();
    final Transaction transaction = store.startTransaction(storageUpdateChannel);

    final SignedBlockAndState blockAndState1 = chainBuilder.getBlockAndStateAtSlot(1);
    final SignedBlockAndState blockAndState2 = chainBuilder.getBlockAndStateAtSlot(2);

    transaction.putBlock(blockAndState1.getRoot(), blockAndState1.getBlock());
    transaction.putBlock(blockAndState2.getRoot(), blockAndState2.getBlock());
    transaction.putBlockState(blockAndState1.getRoot(), blockAndState1.getState());
    transaction.putBlockState(blockAndState2.getRoot(), blockAndState2.getState());

    commit(transaction);

    final Store result = database.createMemoryStore().orElseThrow();
    assertThat(result.getSignedBlock(genesisRoot)).isEqualTo(genesisBlockAndState.getBlock());
    assertThat(result.getSignedBlock(blockAndState1.getRoot()))
        .isEqualTo(blockAndState1.getBlock());
    assertThat(result.getSignedBlock(blockAndState2.getRoot()))
        .isEqualTo(blockAndState2.getBlock());
    assertThat(result.getBlockState(blockAndState1.getRoot())).isEqualTo(blockAndState1.getState());
    assertThat(result.getBlockState(blockAndState2.getRoot())).isEqualTo(blockAndState2.getState());
  }

  @Test
  public void shouldRemoveHotBlocksAndStatesOnceEpochIsFinalized() {
    final Transaction transaction = store.startTransaction(storageUpdateChannel);
    final SignedBlockAndState block1 = chainBuilder.getBlockAndStateAtSlot(1);
    final SignedBlockAndState block2 = checkpoint1BlockAndState;
    final SignedBlockAndState unfinalizedBlock =
        chainBuilder.getLatestBlockAndStateAtEpochBoundary(2);

    transaction.putBlock(block1.getRoot(), block1.getBlock());
    transaction.putBlock(block2.getRoot(), block2.getBlock());
    transaction.putBlock(unfinalizedBlock.getRoot(), unfinalizedBlock.getBlock());
    transaction.putBlockState(block1.getRoot(), block1.getState());
    transaction.putBlockState(block2.getRoot(), block2.getState());
    transaction.putBlockState(unfinalizedBlock.getRoot(), unfinalizedBlock.getState());

    commit(transaction);

    final Transaction transaction2 = store.startTransaction(storageUpdateChannel);
    transaction2.setFinalizedCheckpoint(checkpoint1);
    commit(transaction2);

    final Store result = database.createMemoryStore().orElseThrow();
    assertThat(result.getSignedBlock(block1.getRoot())).isNull();
    assertThat(result.getSignedBlock(block2.getRoot())).isEqualTo(block2.getBlock());
    assertThat(result.getSignedBlock(unfinalizedBlock.getRoot()))
        .isEqualTo(unfinalizedBlock.getBlock());
    assertThat(result.getBlockState(block1.getRoot())).isNull();
    assertThat(result.getBlockState(block2.getRoot())).isEqualTo(block2.getState());
    assertThat(result.getBlockState(unfinalizedBlock.getRoot()))
        .isEqualTo(unfinalizedBlock.getState());
    assertThat(result.getBlockRoots()).containsOnly(block2.getRoot(), unfinalizedBlock.getRoot());
  }

  @Test
  public void shouldRecordFinalizedBlocksAndStates_pruneMode() throws StateTransitionException {
    testShouldRecordFinalizedBlocksAndStates(StateStorageMode.PRUNE, false);
  }

  @Test
  public void shouldRecordFinalizedBlocksAndStates_archiveMode() throws StateTransitionException {
    testShouldRecordFinalizedBlocksAndStates(StateStorageMode.ARCHIVE, false);
  }

  @Test
  public void testShouldRecordFinalizedBlocksAndStatesInBatchUpdate()
      throws StateTransitionException {
    testShouldRecordFinalizedBlocksAndStates(StateStorageMode.ARCHIVE, true);
  }

  @Test
  public void shouldRecreateMerkleTreeFromDatabase() {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil();
    DepositWithIndex firstDeposit = dataStructureUtil.randomDepositWithIndex(1L);
    DepositWithIndex secondDeposit = dataStructureUtil.randomDepositWithIndex(2L);
    database.addEth1Deposit(firstDeposit);
    database.addEth1Deposit(secondDeposit);

    MerkleTree expectedTree = new OptimizedMerkleTree(Constants.DEPOSIT_CONTRACT_TREE_DEPTH);
    expectedTree.add(firstDeposit.getData().hash_tree_root());
    expectedTree.add(secondDeposit.getData().hash_tree_root());
    assertThat(database.getMerkleTree().getRoot()).isEqualTo(expectedTree.getRoot());
  }

  @Test
  public void shouldRecreateDepositNavigableMap() {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil();
    DepositWithIndex firstDeposit = dataStructureUtil.randomDepositWithIndex(1L);
    DepositWithIndex secondDeposit = dataStructureUtil.randomDepositWithIndex(2L);
    database.addEth1Deposit(firstDeposit);
    database.addEth1Deposit(secondDeposit);

    NavigableMap<UnsignedLong, DepositWithIndex> data = database.getDepositNavigableMap();
    assertThat(data)
        .containsExactly(
            // Proofs are not stored in the database
            entry(
                UnsignedLong.valueOf(1),
                new DepositWithIndex(firstDeposit.getData(), firstDeposit.getIndex())),
            entry(
                UnsignedLong.valueOf(2),
                new DepositWithIndex(secondDeposit.getData(), secondDeposit.getIndex())));
  }

  @Test
  public void shouldPruneDepositNavigableMap() {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil();
    database.addEth1Deposit(dataStructureUtil.randomDepositWithIndex(1L));
    database.addEth1Deposit(dataStructureUtil.randomDepositWithIndex(2L));
    database.addEth1Deposit(dataStructureUtil.randomDepositWithIndex(3L));
    database.pruneEth1Deposits(UnsignedLong.valueOf(2L));

    NavigableMap<UnsignedLong, DepositWithIndex> data = database.getDepositNavigableMap();
    assertThat(data).containsOnlyKeys(UnsignedLong.valueOf(3L));
  }

  public void testShouldRecordFinalizedBlocksAndStates(
      final StateStorageMode storageMode, final boolean batchUpdate)
      throws StateTransitionException {
    // Setup chains
    // Both chains share block up to slot 3
    final ChainBuilder primaryChain = ChainBuilder.create(VALIDATOR_KEYS);
    final SignedBlockAndState genesis = primaryChain.generateGenesis();
    primaryChain.generateBlocksUpToSlot(3);
    final ChainBuilder forkChain = primaryChain.fork();
    // Fork chain's next block is at 6
    forkChain.generateBlockAtSlot(6);
    forkChain.generateBlocksUpToSlot(9);
    // Primary chain's next block is at 7
    primaryChain.generateBlockAtSlot(7);
    primaryChain.generateBlocksUpToSlot(9);

    // Setup database
    database = setupDatabase(storageMode);
    final Checkpoint genesisCheckpoint = getCheckpointForBlock(genesis.getBlock());
    store = Store.getForkChoiceStore(genesis.getState());
    database.storeGenesis(store);

    final Set<SignedBlockAndState> allBlocksAndStates =
        Streams.concat(primaryChain.streamBlocksAndStates(), forkChain.streamBlocksAndStates())
            .collect(Collectors.toSet());

    final Map<Bytes32, BeaconState> allStatesByRoot =
        allBlocksAndStates.stream()
            .collect(Collectors.toMap(SignedBlockAndState::getRoot, SignedBlockAndState::getState));

    final Checkpoint finalizedCheckpoint = getCheckpointForBlock(primaryChain.getBlockAtSlot(7));
    if (batchUpdate) {
      final Transaction transaction = store.startTransaction(storageUpdateChannel);
      add(transaction, allBlocksAndStates);
      transaction.setFinalizedCheckpoint(finalizedCheckpoint);
      transaction.commit().reportExceptions();
    } else {
      add(allBlocksAndStates);
      finalizeCheckpoint(finalizedCheckpoint);
    }

    // Upon finalization, we should prune data
    final Set<Bytes32> blocksToPrune =
        Streams.concat(
                primaryChain.streamBlocksAndStates(0, 6), forkChain.streamBlocksAndStates(0, 6))
            .map(SignedBlockAndState::getRoot)
            .collect(Collectors.toSet());
    final Set<Checkpoint> checkpointsToPrune = Set.of(genesisCheckpoint);
    assertLatestUpdateResultContains(blocksToPrune, checkpointsToPrune);

    // Check data was pruned from store
    assertStoreWasPruned(store, blocksToPrune, checkpointsToPrune);

    // Check hot data
    final List<SignedBlockAndState> expectedHotBlocksAndStates =
        Streams.concat(
                primaryChain.streamBlocksAndStates(7, 9), forkChain.streamBlocksAndStates(7, 9))
            .collect(toList());
    assertHotBlocksAndStates(expectedHotBlocksAndStates);

    // Check finalized data
    final List<SignedBeaconBlock> expectedFinalizedBlocks =
        primaryChain
            .streamBlocksAndStates(0, 7)
            .map(SignedBlockAndState::getBlock)
            .collect(toList());
    assertBlocksFinalized(expectedFinalizedBlocks);
    assertGetLatestFinalizedRootAtSlotReturnsFinalizedBlocks(expectedFinalizedBlocks);
    assertBlocksAvailableByRoot(expectedFinalizedBlocks);

    switch (storageMode) {
      case ARCHIVE:
        final Map<Bytes32, BeaconState> expectedStates = new HashMap<>(allStatesByRoot);
        // We should've pruned non-canonical states prior to latest finalized slot
        expectedStates.remove(forkChain.getBlockAtSlot(6).getRoot());
        assertStatesAvailable(expectedStates);
        break;
      case PRUNE:
        // Check pruned states
        final List<Bytes32> prunedRoots =
            Streams.concat(
                    primaryChain.streamBlocksAndStatesUpTo(6),
                    forkChain.streamBlocksAndStatesUpTo(6))
                .map(SignedBlockAndState::getRoot)
                .collect(toList());
        assertStatesUnavailable(prunedRoots);
        // Check hot states
        final Map<Bytes32, BeaconState> expectedHotStates =
            Streams.concat(
                    primaryChain.streamBlocksAndStates(7, 9), forkChain.streamBlocksAndStates(7, 9))
                .collect(
                    Collectors.toMap(SignedBlockAndState::getRoot, SignedBlockAndState::getState));
        assertStatesAvailable(expectedHotStates);
        break;
    }
  }

  protected void assertBlocksFinalized(final List<SignedBeaconBlock> blocks) {
    for (SignedBeaconBlock block : blocks) {
      assertThat(database.getFinalizedRootAtSlot(block.getSlot()))
          .describedAs("Block root at slot %s", block.getSlot())
          .contains(block.getMessage().hash_tree_root());
    }
  }

  protected void assertBlocksAvailableByRoot(final List<SignedBeaconBlock> blocks) {
    for (SignedBeaconBlock block : blocks) {
      assertThat(database.getSignedBlock(block.getRoot()))
          .describedAs("Block root at slot %s", block.getSlot())
          .contains(block);
    }
  }

  protected void assertGetLatestFinalizedRootAtSlotReturnsFinalizedBlocks(
      final List<SignedBeaconBlock> blocks) {
    final UnsignedLong genesisSlot = UnsignedLong.valueOf(Constants.GENESIS_SLOT);
    final Bytes32 genesisRoot = database.getFinalizedRootAtSlot(genesisSlot).get();
    final SignedBeaconBlock genesisBlock = database.getSignedBlock(genesisRoot).get();

    final List<SignedBeaconBlock> finalizedBlocks = new ArrayList<>();
    finalizedBlocks.add(genesisBlock);
    finalizedBlocks.addAll(blocks);
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

  protected void assertHotBlocksAndStates(final Collection<SignedBlockAndState> blocksAndStates) {
    final Store memoryStore = database.createMemoryStore().orElseThrow();
    assertThat(memoryStore.getBlockRoots())
        .hasSameElementsAs(
            blocksAndStates.stream().map(SignedBlockAndState::getRoot).collect(toList()));

    final List<BeaconState> hotStates =
        memoryStore.getBlockRoots().stream()
            .map(memoryStore::getBlockState)
            .filter(Objects::nonNull)
            .collect(toList());

    assertThat(hotStates)
        .hasSameElementsAs(
            blocksAndStates.stream().map(SignedBlockAndState::getState).collect(toList()));
  }

  protected void assertHotBlocksAndStatesInclude(
      final Collection<SignedBlockAndState> blocksAndStates) {
    final Store memoryStore = database.createMemoryStore().orElseThrow();
    assertThat(memoryStore.getBlockRoots())
        .containsAll(blocksAndStates.stream().map(SignedBlockAndState::getRoot).collect(toList()));

    final List<BeaconState> hotStates =
        memoryStore.getBlockRoots().stream()
            .map(memoryStore::getBlockState)
            .filter(Objects::nonNull)
            .collect(toList());

    assertThat(hotStates)
        .containsAll(blocksAndStates.stream().map(SignedBlockAndState::getState).collect(toList()));
  }

  protected void assertStatesAvailable(final Map<Bytes32, BeaconState> states) {
    for (Bytes32 root : states.keySet()) {
      assertThat(database.getState(root)).contains(states.get(root));
    }
  }

  protected void assertStatesUnavailable(final Collection<Bytes32> roots) {
    for (Bytes32 root : roots) {
      Optional<BeaconState> bs = database.getState(root);
      assertThat(bs).isEmpty();
    }
  }

  protected void assertBlocksUnavailable(final Collection<Bytes32> roots) {
    for (Bytes32 root : roots) {
      Optional<SignedBeaconBlock> bb = database.getSignedBlock(root);
      assertThat(bb).isEmpty();
    }
  }

  protected void assertLatestUpdateResultContains(
      final Set<Bytes32> blockRoots, final Set<Checkpoint> checkpoints) {
    final StorageUpdateResult latestResult = getLatestUpdateResult();
    assertThat(latestResult.getPrunedBlockRoots()).containsExactlyInAnyOrderElementsOf(blockRoots);
    assertThat(latestResult.getPrunedCheckpoints())
        .containsExactlyInAnyOrderElementsOf(checkpoints);
  }

  protected void assertLatestUpdateResultPrunedCollectionsAreEmpty() {
    final StorageUpdateResult latestResult = getLatestUpdateResult();
    assertThat(latestResult.getPrunedBlockRoots()).isEmpty();
    assertThat(latestResult.getPrunedCheckpoints()).isEmpty();
  }

  protected void assertStoreWasPruned(
      final Store store, final Set<Bytes32> prunedBlocks, final Set<Checkpoint> prunedCheckpoints) {
    // Check pruned data has been removed from store
    for (Bytes32 prunedBlock : prunedBlocks) {
      assertThat(store.getBlock(prunedBlock)).isNull();
      assertThat(store.getBlockState(prunedBlock)).isNull();
    }
    for (Checkpoint prunedCheckpoint : prunedCheckpoints) {
      assertThat(store.getCheckpointState(prunedCheckpoint)).isNull();
    }
  }

  protected void addBlocks(final SignedBeaconBlock... blocks) {
    final Transaction transaction = store.startTransaction(storageUpdateChannel);
    for (SignedBeaconBlock block : blocks) {
      transaction.putBlock(block.getMessage().hash_tree_root(), block);
    }
    commit(transaction);
  }

  protected void add(final Collection<SignedBlockAndState> blocks) {
    final Transaction transaction = store.startTransaction(storageUpdateChannel);
    add(transaction, blocks);
    commit(transaction);
  }

  protected void add(
      final Transaction transaction, final Collection<SignedBlockAndState> blocksAndStates) {
    for (SignedBlockAndState blockAndState : blocksAndStates) {
      transaction.putBlock(blockAndState.getRoot(), blockAndState.getBlock());
      transaction.putBlockState(blockAndState.getRoot(), blockAndState.getState());
    }
  }

  protected void finalizeEpoch(final UnsignedLong epoch, final Bytes32 root) {
    final Transaction transaction = store.startTransaction(storageUpdateChannel);
    transaction.setFinalizedCheckpoint(new Checkpoint(epoch, root));
    commit(transaction);
  }

  protected void finalizeCheckpoint(final Checkpoint checkpoint) {
    final Transaction transaction = store.startTransaction(storageUpdateChannel);
    transaction.setFinalizedCheckpoint(checkpoint);
    commit(transaction);
  }

  protected StorageUpdateResult getLatestUpdateResult() {
    final List<StorageUpdateResult> updateResults = storageUpdateChannel.getStorageUpdates();
    return updateResults.get(updateResults.size() - 1);
  }

  protected Checkpoint getCheckpointForBlock(final SignedBeaconBlock block) {
    final UnsignedLong blockEpoch = compute_epoch_at_slot(block.getSlot());
    final UnsignedLong blockEpochBoundary = compute_start_slot_at_epoch(blockEpoch);
    final UnsignedLong checkpointEpoch =
        equivalentLongs(block.getSlot(), blockEpochBoundary)
            ? blockEpoch
            : blockEpoch.plus(UnsignedLong.ONE);
    return new Checkpoint(checkpointEpoch, block.getMessage().hash_tree_root());
  }

  private boolean equivalentLongs(final UnsignedLong valA, final UnsignedLong valB) {
    return valA.compareTo(valB) == 0;
  }
}
