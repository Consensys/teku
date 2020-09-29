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

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.storage.store.StoreAssertions.assertStoresMatch;

import com.google.common.collect.Streams;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSKeyGenerator;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.core.ChainBuilder;
import tech.pegasys.teku.core.ChainBuilder.BlockOptions;
import tech.pegasys.teku.core.ChainProperties;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.pow.event.MinGenesisTimeBlockEvent;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.events.AnchorPoint;
import tech.pegasys.teku.storage.events.WeakSubjectivityUpdate;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;
import tech.pegasys.teku.storage.store.StoreConfig;
import tech.pegasys.teku.storage.store.UpdatableStore;
import tech.pegasys.teku.storage.store.UpdatableStore.StoreTransaction;
import tech.pegasys.teku.util.config.Constants;
import tech.pegasys.teku.util.config.StateStorageMode;

public abstract class AbstractDatabaseTest {

  protected static final List<BLSKeyPair> VALIDATOR_KEYS = BLSKeyGenerator.generateKeyPairs(3);

  protected final ChainBuilder chainBuilder = ChainBuilder.create(VALIDATOR_KEYS);

  protected UInt64 genesisTime = UInt64.valueOf(100);
  protected AnchorPoint genesisAnchor;
  protected SignedBlockAndState genesisBlockAndState;
  protected SignedBlockAndState checkpoint1BlockAndState;
  protected SignedBlockAndState checkpoint2BlockAndState;
  protected SignedBlockAndState checkpoint3BlockAndState;

  protected Checkpoint genesisCheckpoint;
  protected Checkpoint checkpoint1;
  protected Checkpoint checkpoint2;
  protected Checkpoint checkpoint3;

  protected StateStorageMode storageMode;
  protected StorageSystem storageSystem;
  protected Database database;
  protected RecentChainData recentChainData;
  protected UpdatableStore store;

  protected List<tech.pegasys.teku.storage.storageSystem.StorageSystem> storageSystems =
      new ArrayList<>();

  @BeforeEach
  public void setup() {
    Constants.SLOTS_PER_EPOCH = 3;
    createStorage(StateStorageMode.ARCHIVE);

    genesisBlockAndState = chainBuilder.generateGenesis(genesisTime, true);
    genesisCheckpoint = getCheckpointForBlock(genesisBlockAndState.getBlock());
    genesisAnchor = AnchorPoint.fromGenesisState(genesisBlockAndState.getState());

    // Initialize genesis store
    initGenesis();
  }

  @AfterEach
  public void tearDown() throws Exception {
    Constants.setConstants("minimal");
    for (StorageSystem storageSystem : storageSystems) {
      storageSystem.close();
    }
  }

  // This method shouldn't be called outside of createStorage
  protected abstract StorageSystem createStorageSystemInternal(
      final StateStorageMode storageMode, final StoreConfig storeConfig);

  protected void restartStorage() {
    final StorageSystem storage = storageSystem.restarted(storageMode);
    setDefaultStorage(storage);
  }

  protected StorageSystem createStorage(final StateStorageMode storageMode) {
    return createStorage(storageMode, StoreConfig.createDefault());
  }

  protected StorageSystem createStorage(
      final StateStorageMode storageMode, final StoreConfig storeConfig) {
    this.storageMode = storageMode;
    storageSystem = createStorageSystemInternal(storageMode, storeConfig);
    setDefaultStorage(storageSystem);

    return storageSystem;
  }

  protected void setDefaultStorage(final StorageSystem storageSystem) {
    this.storageSystem = storageSystem;
    database = storageSystem.getDatabase();
    recentChainData = storageSystem.recentChainData();
    storageSystems.add(storageSystem);
  }

  @Test
  public void createMemoryStoreFromEmptyDatabase() {
    createStorage(StateStorageMode.ARCHIVE);
    assertThat(database.createMemoryStore()).isEmpty();
  }

  @Test
  public void shouldRecreateOriginalGenesisStore() {
    final UpdatableStore memoryStore = recreateStore();
    assertStoresMatch(memoryStore, store);
  }

  @Test
  public void updateWeakSubjectivityState_setValue() {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil();
    final Checkpoint checkpoint = dataStructureUtil.randomCheckpoint();
    assertThat(database.getWeakSubjectivityState().getCheckpoint()).isEmpty();

    final WeakSubjectivityUpdate update =
        WeakSubjectivityUpdate.setWeakSubjectivityCheckpoint(checkpoint);
    database.updateWeakSubjectivityState(update);

    assertThat(database.getWeakSubjectivityState().getCheckpoint()).contains(checkpoint);
  }

  @Test
  public void updateWeakSubjectivityState_clearValue() {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil();
    final Checkpoint checkpoint = dataStructureUtil.randomCheckpoint();

    // Set an initial value
    final WeakSubjectivityUpdate initUpdate =
        WeakSubjectivityUpdate.setWeakSubjectivityCheckpoint(checkpoint);
    database.updateWeakSubjectivityState(initUpdate);
    assertThat(database.getWeakSubjectivityState().getCheckpoint()).contains(checkpoint);

    // Clear the checkpoint
    final WeakSubjectivityUpdate update = WeakSubjectivityUpdate.clearWeakSubjectivityCheckpoint();
    database.updateWeakSubjectivityState(update);

    assertThat(database.getWeakSubjectivityState().getCheckpoint()).isEmpty();
  }

  @Test
  public void shouldGetHotBlockByRoot() {
    final StoreTransaction transaction = recentChainData.startStoreTransaction();
    final SignedBlockAndState block1 = chainBuilder.generateBlockAtSlot(1);
    final SignedBlockAndState block2 = chainBuilder.generateBlockAtSlot(2);

    transaction.putBlockAndState(block1);
    transaction.putBlockAndState(block2);

    commit(transaction);

    assertThat(database.getSignedBlock(block1.getRoot())).contains(block1.getBlock());
    assertThat(database.getSignedBlock(block2.getRoot())).contains(block2.getBlock());
  }

  protected void commit(final StoreTransaction transaction) {
    assertThat(transaction.commit()).isCompleted();
  }

  @Test
  public void shouldPruneHotBlocksAddedOverMultipleSessions() throws Exception {
    final UInt64 targetSlot = UInt64.valueOf(10);

    chainBuilder.generateBlocksUpToSlot(targetSlot.minus(UInt64.ONE));
    final ChainBuilder forkA = chainBuilder.fork();
    final ChainBuilder forkB = chainBuilder.fork();

    // Add base blocks
    addBlocks(chainBuilder.streamBlocksAndStates().collect(toList()));

    // Set target slot at which to create duplicate blocks
    // and generate block options to make each block unique
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
    assertThat(store.retrieveBlock(blockA.getRoot()))
        .isCompletedWithValue(Optional.of(blockA.getBlock().getMessage()));
    assertThat(store.retrieveBlock(blockB.getRoot()))
        .isCompletedWithValue(Optional.of(blockB.getBlock().getMessage()));
    assertThat(store.retrieveBlock(blockC.getRoot()))
        .isCompletedWithValue(Optional.of(blockC.getBlock().getMessage()));

    // Finalize subsequent block to prune blocks a, b, and c
    final SignedBlockAndState finalBlock = chainBuilder.generateNextBlock();
    add(List.of(finalBlock));
    final UInt64 finalEpoch = chainBuilder.getLatestEpoch().plus(ONE);
    final SignedBlockAndState finalizedBlock =
        chainBuilder.getLatestBlockAndStateAtEpochBoundary(finalEpoch);
    justifyAndFinalizeEpoch(finalEpoch, finalizedBlock);

    // Check pruning result
    final Set<Bytes32> rootsToPrune = new HashSet<>(block10Roots);
    rootsToPrune.add(genesisBlockAndState.getRoot());
    // Check that all blocks at slot 10 were pruned
    assertRecentDataWasPruned(store, rootsToPrune, Set.of(genesisCheckpoint));
  }

  @Test
  public void getFinalizedState() {
    generateCheckpoints();
    final Checkpoint finalizedCheckpoint = chainBuilder.getCurrentCheckpointForEpoch(UInt64.ONE);
    final SignedBlockAndState block2 =
        chainBuilder.getLatestBlockAndStateAtEpochBoundary(UInt64.ONE);
    final SignedBlockAndState block1 =
        chainBuilder.getBlockAndStateAtSlot(block2.getSlot().minus(UInt64.ONE));

    final List<SignedBlockAndState> allBlocks =
        chainBuilder.streamBlocksAndStates(0, block2.getSlot().longValue()).collect(toList());
    addBlocks(allBlocks);

    // Finalize block2
    final StoreTransaction transaction = recentChainData.startStoreTransaction();
    transaction.setFinalizedCheckpoint(finalizedCheckpoint);
    commit(transaction);

    assertThat(database.getLatestAvailableFinalizedState(block2.getSlot()))
        .contains(block2.getState());
    assertThat(database.getLatestAvailableFinalizedState(block1.getSlot()))
        .contains(block1.getState());
  }

  @Test
  public void shouldStoreSingleValueFields() {
    generateCheckpoints();

    final List<SignedBlockAndState> allBlocks =
        chainBuilder
            .streamBlocksAndStates(0, checkpoint3BlockAndState.getSlot().longValue())
            .collect(toList());
    addBlocks(allBlocks);

    final StoreTransaction transaction = recentChainData.startStoreTransaction();
    transaction.setGenesis_time(UInt64.valueOf(3));
    transaction.setFinalizedCheckpoint(checkpoint1);
    transaction.setJustifiedCheckpoint(checkpoint2);
    transaction.setBestJustifiedCheckpoint(checkpoint3);

    commit(transaction);

    final UpdatableStore result = recreateStore();

    assertThat(result.getGenesisTime()).isEqualTo(transaction.getGenesisTime());
    assertThat(result.getFinalizedCheckpoint()).isEqualTo(transaction.getFinalizedCheckpoint());
    assertThat(result.getJustifiedCheckpoint()).isEqualTo(transaction.getJustifiedCheckpoint());
    assertThat(result.getBestJustifiedCheckpoint())
        .isEqualTo(transaction.getBestJustifiedCheckpoint());
  }

  @Test
  public void shouldStoreSingleValue_genesisTime() {
    final UInt64 newGenesisTime = UInt64.valueOf(3);
    // Sanity check
    assertThat(store.getGenesisTime()).isNotEqualTo(newGenesisTime);

    final StoreTransaction transaction = recentChainData.startStoreTransaction();
    transaction.setGenesis_time(newGenesisTime);
    commit(transaction);

    final UpdatableStore result = recreateStore();
    assertThat(result.getGenesisTime()).isEqualTo(transaction.getGenesisTime());
  }

  @Test
  public void shouldStoreSingleValue_justifiedCheckpoint() {
    generateCheckpoints();
    final Checkpoint newValue = checkpoint3;
    // Sanity check
    assertThat(store.getJustifiedCheckpoint()).isNotEqualTo(checkpoint3);

    final StoreTransaction transaction = recentChainData.startStoreTransaction();
    transaction.setJustifiedCheckpoint(newValue);
    commit(transaction);

    final UpdatableStore result = recreateStore();
    assertThat(result.getJustifiedCheckpoint()).isEqualTo(newValue);
  }

  @Test
  public void shouldStoreSingleValue_finalizedCheckpoint() {
    generateCheckpoints();
    final List<SignedBlockAndState> allBlocks =
        chainBuilder
            .streamBlocksAndStates(0, checkpoint3BlockAndState.getSlot().longValue())
            .collect(toList());
    addBlocks(allBlocks);

    final Checkpoint newValue = checkpoint3;
    // Sanity check
    assertThat(store.getFinalizedCheckpoint()).isNotEqualTo(checkpoint3);

    justifyAndFinalizeEpoch(newValue.getEpoch(), checkpoint3BlockAndState);

    final UpdatableStore result = recreateStore();
    assertThat(result.getFinalizedCheckpoint()).isEqualTo(newValue);
  }

  @Test
  public void shouldStoreSingleValue_bestJustifiedCheckpoint() {
    generateCheckpoints();
    final Checkpoint newValue = checkpoint3;
    // Sanity check
    assertThat(store.getBestJustifiedCheckpoint()).isNotEqualTo(checkpoint3);

    final StoreTransaction transaction = recentChainData.startStoreTransaction();
    transaction.setBestJustifiedCheckpoint(newValue);
    commit(transaction);

    final UpdatableStore result = recreateStore();
    assertThat(result.getBestJustifiedCheckpoint()).isEqualTo(newValue);
  }

  @Test
  public void shouldStoreSingleValue_singleBlockAndState() {
    final SignedBlockAndState newBlock = chainBuilder.generateNextBlock();
    // Sanity check
    assertThatSafeFuture(store.retrieveBlock(newBlock.getRoot())).isCompletedWithEmptyOptional();

    final StoreTransaction transaction = recentChainData.startStoreTransaction();
    transaction.putBlockAndState(newBlock);
    commit(transaction);

    final UpdatableStore result = recreateStore();
    assertThat(result.retrieveSignedBlock(newBlock.getRoot()))
        .isCompletedWithValue(Optional.of(newBlock.getBlock()));
    assertThat(result.retrieveBlockState(newBlock.getRoot()))
        .isCompletedWithValue(Optional.of(newBlock.getState()));
  }

  @Test
  public void shouldLoadHotBlocksAndStatesIntoMemoryStore() {
    final Bytes32 genesisRoot = genesisBlockAndState.getRoot();
    final StoreTransaction transaction = recentChainData.startStoreTransaction();

    final SignedBlockAndState blockAndState1 = chainBuilder.generateBlockAtSlot(1);
    final SignedBlockAndState blockAndState2 = chainBuilder.generateBlockAtSlot(2);

    transaction.putBlockAndState(blockAndState1);
    transaction.putBlockAndState(blockAndState2);

    commit(transaction);

    final UpdatableStore result = recreateStore();
    assertThat(result.retrieveSignedBlock(genesisRoot))
        .isCompletedWithValue(Optional.of(genesisBlockAndState.getBlock()));
    assertThat(result.retrieveSignedBlock(blockAndState1.getRoot()))
        .isCompletedWithValue(Optional.of(blockAndState1.getBlock()));
    assertThat(result.retrieveSignedBlock(blockAndState2.getRoot()))
        .isCompletedWithValue(Optional.of(blockAndState2.getBlock()));
    assertThat(result.retrieveBlockState(blockAndState1.getRoot()))
        .isCompletedWithValue(Optional.of(blockAndState1.getState()));
    assertThat(result.retrieveBlockState(blockAndState2.getRoot()))
        .isCompletedWithValue(Optional.of(blockAndState2.getState()));
  }

  @Test
  public void shouldRemoveHotBlocksAndStatesOnceEpochIsFinalized() {
    generateCheckpoints();
    final List<SignedBlockAndState> allBlocks =
        chainBuilder
            .streamBlocksAndStates(0, checkpoint2BlockAndState.getSlot().longValue())
            .collect(toList());
    addBlocks(allBlocks);

    // Finalize block
    justifyAndFinalizeEpoch(checkpoint1.getEpoch(), checkpoint1BlockAndState);

    final List<SignedBlockAndState> historicalBlocks =
        chainBuilder
            .streamBlocksAndStates(0, checkpoint1BlockAndState.getSlot().longValue())
            .collect(toList());
    historicalBlocks.remove(checkpoint1BlockAndState);
    final List<SignedBlockAndState> hotBlocks =
        chainBuilder
            .streamBlocksAndStates(
                checkpoint1BlockAndState.getSlot(), checkpoint2BlockAndState.getSlot())
            .collect(toList());

    final UpdatableStore result = recreateStore();
    // Historical blocks should not be in the new store
    for (SignedBlockAndState historicalBlock : historicalBlocks) {
      assertThatSafeFuture(result.retrieveSignedBlock(historicalBlock.getRoot()))
          .isCompletedWithEmptyOptional();
      assertThatSafeFuture(result.retrieveBlockState(historicalBlock.getRoot()))
          .isCompletedWithEmptyOptional();
    }

    // Hot blocks should be available in the new store
    for (SignedBlockAndState hotBlock : hotBlocks) {
      assertThat(result.retrieveSignedBlock(hotBlock.getRoot()))
          .isCompletedWithValue(Optional.of(hotBlock.getBlock()));
      assertThat(result.retrieveBlockState(hotBlock.getRoot()))
          .isCompletedWithValue(Optional.of(hotBlock.getState()));
    }

    final Set<Bytes32> hotBlockRoots =
        hotBlocks.stream().map(SignedBlockAndState::getRoot).collect(Collectors.toSet());
    assertThat(result.getBlockRoots()).containsExactlyInAnyOrderElementsOf(hotBlockRoots);
  }

  @Test
  public void shouldRecordAndRetrieveGenesisInformation() {
    final DataStructureUtil util = new DataStructureUtil();
    final MinGenesisTimeBlockEvent event =
        new MinGenesisTimeBlockEvent(
            util.randomUInt64(), util.randomUInt64(), util.randomBytes32());
    database.addMinGenesisTimeBlock(event);

    final Optional<MinGenesisTimeBlockEvent> fetch = database.getMinGenesisTimeBlock();
    assertThat(fetch.isPresent()).isTrue();
    assertThat(fetch.get()).isEqualToComparingFieldByField(event);
  }

  @Test
  public void handleFinalizationWhenCacheLimitsExceeded() {
    createStorage(StateStorageMode.ARCHIVE);
    initGenesis();

    final int startSlot = genesisBlockAndState.getSlot().intValue();
    final int minFinalSlot = startSlot + StoreConfig.DEFAULT_STATE_CACHE_SIZE + 10;
    final UInt64 finalizedEpoch = ChainProperties.computeBestEpochFinalizableAtSlot(minFinalSlot);
    final UInt64 finalizedSlot = compute_start_slot_at_epoch(finalizedEpoch);

    chainBuilder.generateBlocksUpToSlot(finalizedSlot);
    final Checkpoint finalizedCheckpoint =
        chainBuilder.getCurrentCheckpointForEpoch(finalizedEpoch);

    // Save all blocks and states in a single transaction
    final List<SignedBlockAndState> newBlocks =
        chainBuilder.streamBlocksAndStates(startSlot).collect(toList());
    add(newBlocks);
    // Then finalize
    final StoreTransaction tx = recentChainData.startStoreTransaction();
    tx.setFinalizedCheckpoint(finalizedCheckpoint);
    assertThat(tx.commit()).isCompleted();

    // All finalized blocks and states should be available
    assertFinalizedBlocksAndStatesAvailable(newBlocks);
  }

  @Test
  public void shouldRecordFinalizedBlocksAndStates_pruneMode() {
    testShouldRecordFinalizedBlocksAndStates(StateStorageMode.PRUNE, false);
  }

  @Test
  public void shouldRecordFinalizedBlocksAndStates_archiveMode() {
    testShouldRecordFinalizedBlocksAndStates(StateStorageMode.ARCHIVE, false);
  }

  @Test
  public void testShouldRecordFinalizedBlocksAndStatesInBatchUpdate() {
    testShouldRecordFinalizedBlocksAndStates(StateStorageMode.ARCHIVE, true);
  }

  @Test
  public void slotAndBlock_shouldStoreAndRetrieve() {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil();
    final Bytes32 stateRoot = dataStructureUtil.randomBytes32();
    final SlotAndBlockRoot slotAndBlockRoot =
        new SlotAndBlockRoot(dataStructureUtil.randomUInt64(), dataStructureUtil.randomBytes32());

    database.addHotStateRoots(Map.of(stateRoot, slotAndBlockRoot));

    final Optional<SlotAndBlockRoot> fromStorage =
        database.getSlotAndBlockRootFromStateRoot(stateRoot);

    assertThat(fromStorage.isPresent()).isTrue();
    assertThat(fromStorage.get()).isEqualTo(slotAndBlockRoot);
  }

  @Test
  public void slotAndBlock_shouldGetStateRootsBeforeSlot() {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil();
    final Bytes32 zeroStateRoot = insertRandomSlotAndBlock(0L, dataStructureUtil);
    final Bytes32 oneStateRoot = insertRandomSlotAndBlock(1L, dataStructureUtil);
    insertRandomSlotAndBlock(2L, dataStructureUtil);
    insertRandomSlotAndBlock(3L, dataStructureUtil);

    assertThat(database.getStateRootsBeforeSlot(UInt64.valueOf(2L)))
        .containsExactlyInAnyOrder(zeroStateRoot, oneStateRoot);
  }

  @Test
  public void slotAndBlock_shouldPurgeToSlot() {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil();
    insertRandomSlotAndBlock(0L, dataStructureUtil);
    insertRandomSlotAndBlock(1L, dataStructureUtil);
    final Bytes32 twoStateRoot = insertRandomSlotAndBlock(2L, dataStructureUtil);
    final Bytes32 threeStateRoot = insertRandomSlotAndBlock(3L, dataStructureUtil);

    database.pruneHotStateRoots(database.getStateRootsBeforeSlot(UInt64.valueOf(2L)));
    assertThat(database.getStateRootsBeforeSlot(UInt64.valueOf(10L)))
        .containsExactlyInAnyOrder(twoStateRoot, threeStateRoot);
  }

  protected Bytes32 insertRandomSlotAndBlock(
      final long slot, final DataStructureUtil dataStructureUtil) {
    final Bytes32 stateRoot = dataStructureUtil.randomBytes32();
    final SlotAndBlockRoot slotAndBlockRoot =
        new SlotAndBlockRoot(UInt64.valueOf(slot), dataStructureUtil.randomBytes32());
    database.addHotStateRoots(Map.of(stateRoot, slotAndBlockRoot));
    return stateRoot;
  }

  public void testShouldRecordFinalizedBlocksAndStates(
      final StateStorageMode storageMode, final boolean batchUpdate) {
    testShouldRecordFinalizedBlocksAndStates(storageMode, batchUpdate, this::createStorage);
  }

  protected void testShouldRecordFinalizedBlocksAndStates(
      final StateStorageMode storageMode,
      final boolean batchUpdate,
      Consumer<StateStorageMode> initializeDatabase) {
    // Setup chains
    // Both chains share block up to slot 3
    final ChainBuilder primaryChain = ChainBuilder.create(VALIDATOR_KEYS);
    primaryChain.generateGenesis(genesisTime, true);
    primaryChain.generateBlocksUpToSlot(3);
    final ChainBuilder forkChain = primaryChain.fork();
    // Fork chain's next block is at 6
    forkChain.generateBlockAtSlot(6);
    forkChain.generateBlocksUpToSlot(7);
    // Primary chain's next block is at 7
    final SignedBlockAndState finalizedBlock = primaryChain.generateBlockAtSlot(7);
    final Checkpoint finalizedCheckpoint = getCheckpointForBlock(finalizedBlock.getBlock());
    final UInt64 pruneToSlot = finalizedCheckpoint.getEpochStartSlot();
    // Add some blocks in the next epoch
    final UInt64 hotSlot = pruneToSlot.plus(UInt64.ONE);
    primaryChain.generateBlockAtSlot(hotSlot);
    forkChain.generateBlockAtSlot(hotSlot);

    // Setup database
    initializeDatabase.accept(storageMode);
    initGenesis();

    final Set<SignedBlockAndState> allBlocksAndStates =
        Streams.concat(primaryChain.streamBlocksAndStates(), forkChain.streamBlocksAndStates())
            .collect(Collectors.toSet());

    if (batchUpdate) {
      final StoreTransaction transaction = recentChainData.startStoreTransaction();
      add(transaction, allBlocksAndStates);
      justifyAndFinalizeEpoch(finalizedCheckpoint.getEpoch(), finalizedBlock, transaction);
      assertThat(transaction.commit()).isCompleted();
    } else {
      add(allBlocksAndStates);
      justifyAndFinalizeEpoch(finalizedCheckpoint.getEpoch(), finalizedBlock);
    }

    // Upon finalization, we should prune data
    final Set<Bytes32> blocksToPrune =
        Streams.concat(
                primaryChain.streamBlocksAndStates(0, pruneToSlot.longValue()),
                forkChain.streamBlocksAndStates(0, pruneToSlot.longValue()))
            .map(SignedBlockAndState::getRoot)
            .collect(Collectors.toSet());
    blocksToPrune.remove(finalizedBlock.getRoot());
    final Set<Checkpoint> checkpointsToPrune = Set.of(genesisCheckpoint);

    // Check data was pruned from store
    assertRecentDataWasPruned(store, blocksToPrune, checkpointsToPrune);

    restartStorage();

    // Check hot data
    final List<SignedBlockAndState> expectedHotBlocksAndStates =
        List.of(finalizedBlock, primaryChain.getBlockAndStateAtSlot(hotSlot));
    assertHotBlocksAndStates(store, expectedHotBlocksAndStates);
    final SignedBlockAndState prunedForkBlock = forkChain.getBlockAndStateAtSlot(hotSlot);
    assertThat(store.containsBlock(prunedForkBlock.getRoot())).isFalse();

    // Check finalized data
    final List<SignedBeaconBlock> expectedFinalizedBlocks =
        primaryChain
            .streamBlocksAndStates(0, 7)
            .map(SignedBlockAndState::getBlock)
            .collect(toList());
    assertBlocksFinalized(expectedFinalizedBlocks);
    assertGetLatestFinalizedRootAtSlotReturnsFinalizedBlocks(expectedFinalizedBlocks);
    assertBlocksAvailableByRoot(expectedFinalizedBlocks);
    assertFinalizedBlocksAvailableViaStream(
        1,
        3,
        primaryChain.getBlockAtSlot(1),
        primaryChain.getBlockAtSlot(2),
        primaryChain.getBlockAtSlot(3));

    switch (storageMode) {
      case ARCHIVE:
        // Finalized states should be available
        final Map<Bytes32, BeaconState> expectedStates =
            primaryChain
                .streamBlocksAndStates(0, 7)
                .collect(toMap(SignedBlockAndState::getRoot, SignedBlockAndState::getState));
        assertFinalizedStatesAvailable(expectedStates);
        break;
      case PRUNE:
        // Check pruned states
        final List<UInt64> unavailableSlots =
            allBlocksAndStates.stream().map(SignedBlockAndState::getSlot).collect(toList());
        assertStatesUnavailable(unavailableSlots);
        break;
    }
  }

  protected void assertFinalizedBlocksAvailableViaStream(
      final int fromSlot, final int toSlot, final SignedBeaconBlock... expectedBlocks) {
    try (final Stream<SignedBeaconBlock> stream =
        database.streamFinalizedBlocks(UInt64.valueOf(fromSlot), UInt64.valueOf(toSlot))) {
      assertThat(stream).containsExactly(expectedBlocks);
    }
  }

  protected void assertFinalizedBlocksAndStatesAvailable(
      final List<SignedBlockAndState> blocksAndStates) {
    final List<SignedBeaconBlock> blocks =
        blocksAndStates.stream().map(SignedBlockAndState::getBlock).collect(toList());
    final Map<Bytes32, BeaconState> states =
        blocksAndStates.stream()
            .collect(Collectors.toMap(SignedBlockAndState::getRoot, SignedBlockAndState::getState));
    assertBlocksFinalized(blocks);
    assertBlocksAvailable(blocks);
    assertFinalizedStatesAvailable(states);
  }

  protected void assertBlocksFinalized(final List<SignedBeaconBlock> blocks) {
    for (SignedBeaconBlock block : blocks) {
      assertThat(database.getFinalizedBlockAtSlot(block.getSlot()))
          .describedAs("Block at slot %s", block.getSlot())
          .contains(block);
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
    final UInt64 genesisSlot = UInt64.valueOf(Constants.GENESIS_SLOT);
    final SignedBeaconBlock genesisBlock =
        database.getFinalizedBlockAtSlot(genesisSlot).orElseThrow();

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
        assertThat(database.getLatestFinalizedBlockAtSlot(UInt64.valueOf(slot)))
            .describedAs("Latest finalized block at slot %s", slot)
            .contains(currentBlock);
      }
    }

    // Check that last block
    final SignedBeaconBlock lastFinalizedBlock = finalizedBlocks.get(finalizedBlocks.size() - 1);
    for (int i = 0; i < 10; i++) {
      final UInt64 slot = lastFinalizedBlock.getSlot().plus(i);
      assertThat(database.getLatestFinalizedBlockAtSlot(slot))
          .describedAs("Latest finalized block at slot %s", slot)
          .contains(lastFinalizedBlock);
    }
  }

  protected void assertHotBlocksAndStates(
      final UpdatableStore store, final Collection<SignedBlockAndState> blocksAndStates) {
    final List<UpdatableStore> storesToCheck = List.of(store, recreateStore());
    for (UpdatableStore currentStore : storesToCheck) {
      assertThat(currentStore.getBlockRoots())
          .hasSameElementsAs(
              blocksAndStates.stream().map(SignedBlockAndState::getRoot).collect(toList()));

      final List<BeaconState> hotStates =
          currentStore.getBlockRoots().stream()
              .map(currentStore::retrieveBlockState)
              .map(
                  f -> {
                    assertThat(f).isCompleted();
                    return f.join();
                  })
              .flatMap(Optional::stream)
              .collect(toList());

      assertThat(hotStates)
          .hasSameElementsAs(
              blocksAndStates.stream().map(SignedBlockAndState::getState).collect(toList()));
    }
  }

  protected void assertHotBlocksAndStatesInclude(
      final Collection<SignedBlockAndState> blocksAndStates) {
    final UpdatableStore memoryStore = recreateStore();
    assertThat(memoryStore.getBlockRoots())
        .containsAll(blocksAndStates.stream().map(SignedBlockAndState::getRoot).collect(toList()));

    final List<BeaconState> hotStates =
        memoryStore.getBlockRoots().stream()
            .map(memoryStore::retrieveBlockState)
            .map(
                f -> {
                  assertThat(f).isCompleted();
                  return f.join();
                })
            .flatMap(Optional::stream)
            .collect(toList());

    assertThat(hotStates)
        .containsAll(blocksAndStates.stream().map(SignedBlockAndState::getState).collect(toList()));
  }

  protected void assertFinalizedStatesAvailable(final Map<Bytes32, BeaconState> states) {
    for (BeaconState state : states.values()) {
      assertThat(database.getLatestAvailableFinalizedState(state.getSlot())).contains(state);
    }
  }

  protected void assertStatesUnavailable(final Collection<UInt64> slots) {
    for (UInt64 slot : slots) {
      Optional<BeaconState> bs =
          database
              .getLatestAvailableFinalizedState(slot)
              .filter(state -> state.getSlot().equals(slot));
      assertThat(bs).isEmpty();
    }
  }

  protected void assertBlocksUnavailable(final Collection<Bytes32> roots) {
    for (Bytes32 root : roots) {
      Optional<SignedBeaconBlock> bb = database.getSignedBlock(root);
      assertThat(bb).isEmpty();
    }
  }

  protected void assertBlocksAvailable(final Collection<SignedBeaconBlock> blocks) {
    for (SignedBeaconBlock expectedBlock : blocks) {
      Optional<SignedBeaconBlock> actualBlock = database.getSignedBlock(expectedBlock.getRoot());
      assertThat(actualBlock).contains(expectedBlock);
    }
  }

  protected void assertRecentDataWasPruned(
      final UpdatableStore store,
      final Set<Bytes32> prunedBlocks,
      final Set<Checkpoint> prunedCheckpoints) {
    for (Bytes32 prunedBlock : prunedBlocks) {
      // Check pruned data has been removed from store
      assertThat(store.containsBlock(prunedBlock)).isFalse();
      assertThatSafeFuture(store.retrieveBlock(prunedBlock)).isCompletedWithEmptyOptional();
      assertThatSafeFuture(store.retrieveBlockState(prunedBlock)).isCompletedWithEmptyOptional();

      // Check hot data was pruned from db
      assertThat(database.getHotBlocks(Set.of(prunedBlock))).isEmpty();
      assertThat(database.getHotState(prunedBlock)).isEmpty();
    }
  }

  protected void addBlocks(final SignedBlockAndState... blocks) {
    addBlocks(Arrays.asList(blocks));
  }

  protected void addBlocks(final List<SignedBlockAndState> blocks) {
    final StoreTransaction transaction = recentChainData.startStoreTransaction();
    for (SignedBlockAndState block : blocks) {
      transaction.putBlockAndState(block);
      recentChainData
          .getForkChoiceStrategy()
          .orElseThrow()
          .onBlock(block.getBlock().getMessage(), block.getState());
    }
    commit(transaction);
  }

  protected void add(final Collection<SignedBlockAndState> blocks) {
    final StoreTransaction transaction = recentChainData.startStoreTransaction();
    add(transaction, blocks);
    commit(transaction);
  }

  protected void add(
      final StoreTransaction transaction, final Collection<SignedBlockAndState> blocksAndStates) {
    for (SignedBlockAndState blockAndState : blocksAndStates) {
      transaction.putBlockAndState(blockAndState);
      recentChainData
          .getForkChoiceStrategy()
          .orElseThrow()
          .onBlock(blockAndState.getBlock().getMessage(), blockAndState.getState());
    }
  }

  protected void justifyAndFinalizeEpoch(final UInt64 epoch, final SignedBlockAndState block) {
    StoreTransaction tx = recentChainData.startStoreTransaction();
    justifyAndFinalizeEpoch(epoch, block, tx);
    tx.commit().reportExceptions();
  }

  protected void justifyAndFinalizeEpoch(
      final UInt64 epoch, final SignedBlockAndState block, final StoreTransaction tx) {
    justifyEpoch(epoch, block, tx);
    finalizeEpoch(epoch, block, tx);
  }

  protected void finalizeEpoch(
      final UInt64 epoch, final SignedBlockAndState block, final StoreTransaction transaction) {
    final Checkpoint checkpoint = new Checkpoint(epoch, block.getRoot());
    transaction.setFinalizedCheckpoint(checkpoint);
  }

  protected void justifyEpoch(
      final UInt64 epoch, final SignedBlockAndState block, final StoreTransaction transaction) {
    final Checkpoint checkpoint = new Checkpoint(epoch, block.getRoot());
    transaction.setJustifiedCheckpoint(checkpoint);
  }

  protected Checkpoint getCheckpointForBlock(final SignedBeaconBlock block) {
    final UInt64 blockEpoch = compute_epoch_at_slot(block.getSlot());
    final UInt64 blockEpochBoundary = compute_start_slot_at_epoch(blockEpoch);
    final UInt64 checkpointEpoch =
        equivalentLongs(block.getSlot(), blockEpochBoundary) ? blockEpoch : blockEpoch.plus(ONE);
    return new Checkpoint(checkpointEpoch, block.getMessage().hash_tree_root());
  }

  private boolean equivalentLongs(final UInt64 valA, final UInt64 valB) {
    return valA.compareTo(valB) == 0;
  }

  protected void initGenesis() {
    recentChainData.initializeFromGenesis(genesisBlockAndState.getState()).join();
    store = recentChainData.getStore();
  }

  protected void generateCheckpoints() {
    while (chainBuilder.getLatestEpoch().longValue() < 3) {
      chainBuilder.generateNextBlock();
    }

    checkpoint1BlockAndState = chainBuilder.getLatestBlockAndStateAtEpochBoundary(1);
    checkpoint1 = chainBuilder.getCurrentCheckpointForEpoch(1);
    checkpoint2BlockAndState = chainBuilder.getLatestBlockAndStateAtEpochBoundary(2);
    checkpoint2 = chainBuilder.getCurrentCheckpointForEpoch(2);
    checkpoint3BlockAndState = chainBuilder.getLatestBlockAndStateAtEpochBoundary(3);
    checkpoint3 = chainBuilder.getCurrentCheckpointForEpoch(3);
  }

  protected UpdatableStore recreateStore() {
    restartStorage();
    return storageSystem.recentChainData().getStore();
  }
}
