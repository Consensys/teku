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

import static com.google.common.primitives.UnsignedLong.ONE;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;
import static tech.pegasys.teku.storage.store.StoreAssertions.assertStoresMatch;

import com.google.common.collect.Streams;
import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import tech.pegasys.teku.core.StateTransitionException;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.pow.event.DepositsFromBlockEvent;
import tech.pegasys.teku.pow.event.MinGenesisTimeBlockEvent;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;
import tech.pegasys.teku.storage.store.StorePruningOptions;
import tech.pegasys.teku.storage.store.UpdatableStore;
import tech.pegasys.teku.storage.store.UpdatableStore.StoreTransaction;
import tech.pegasys.teku.util.config.Constants;
import tech.pegasys.teku.util.config.StateStorageMode;

public abstract class AbstractDatabaseTest {

  protected static final List<BLSKeyPair> VALIDATOR_KEYS = BLSKeyGenerator.generateKeyPairs(3);

  protected final ChainBuilder chainBuilder = ChainBuilder.create(VALIDATOR_KEYS);

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

    genesisBlockAndState = chainBuilder.generateGenesis();
    genesisCheckpoint = getCheckpointForBlock(genesisBlockAndState.getBlock());

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
  protected abstract StorageSystem createStorageSystemInternal(final StateStorageMode storageMode);

  protected void restartStorage() {
    final StorageSystem storage = storageSystem.restarted(storageMode);
    setDefaultStorage(storage);
  }

  protected StorageSystem createStorage(final StateStorageMode storageMode) {
    this.storageMode = storageMode;
    storageSystem = createStorageSystemInternal(storageMode);
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
  public void shouldGetHotBlockByRoot() throws StateTransitionException {
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
    final UnsignedLong targetSlot = UnsignedLong.valueOf(10);

    chainBuilder.generateBlocksUpToSlot(targetSlot.minus(UnsignedLong.ONE));
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
    assertThat(store.getBlock(blockA.getRoot())).isEqualTo(blockA.getBlock().getMessage());
    assertThat(store.getBlock(blockB.getRoot())).isEqualTo(blockB.getBlock().getMessage());
    assertThat(store.getBlock(blockC.getRoot())).isEqualTo(blockC.getBlock().getMessage());

    // Finalize subsequent block to prune blocks a, b, and c
    final SignedBlockAndState finalBlock = chainBuilder.generateNextBlock();
    add(List.of(finalBlock));
    final UnsignedLong finalEpoch = chainBuilder.getLatestEpoch().plus(ONE);
    final SignedBlockAndState finalizedBlock =
        chainBuilder.getLatestBlockAndStateAtEpochBoundary(finalEpoch);
    justifyAndFinalizeEpoch(finalEpoch, finalizedBlock);

    // Check pruning result
    final Set<Bytes32> rootsToPrune = new HashSet<>(block10Roots);
    rootsToPrune.add(genesisBlockAndState.getRoot());
    // Check that all blocks at slot 10 were pruned
    assertStoreWasPruned(store, rootsToPrune, Set.of(genesisCheckpoint));
  }

  @Test
  public void getFinalizedState() throws StateTransitionException {
    generateCheckpoints();
    final Checkpoint finalizedCheckpoint =
        chainBuilder.getCurrentCheckpointForEpoch(UnsignedLong.ONE);
    final SignedBlockAndState block2 =
        chainBuilder.getLatestBlockAndStateAtEpochBoundary(UnsignedLong.ONE);
    final SignedBlockAndState block1 =
        chainBuilder.getBlockAndStateAtSlot(block2.getSlot().minus(UnsignedLong.ONE));

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
  public void shouldStoreSingleValueFields() throws StateTransitionException {
    generateCheckpoints();

    final List<SignedBlockAndState> allBlocks =
        chainBuilder
            .streamBlocksAndStates(0, checkpoint3BlockAndState.getSlot().longValue())
            .collect(toList());
    addBlocks(allBlocks);

    final StoreTransaction transaction = recentChainData.startStoreTransaction();
    transaction.setGenesis_time(UnsignedLong.valueOf(3));
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
    final UnsignedLong newGenesisTime = UnsignedLong.valueOf(3);
    // Sanity check
    assertThat(store.getGenesisTime()).isNotEqualTo(newGenesisTime);

    final StoreTransaction transaction = recentChainData.startStoreTransaction();
    transaction.setGenesis_time(newGenesisTime);
    commit(transaction);

    final UpdatableStore result = recreateStore();
    assertThat(result.getGenesisTime()).isEqualTo(transaction.getGenesisTime());
  }

  @Test
  public void shouldStoreSingleValue_justifiedCheckpoint() throws StateTransitionException {
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
  public void shouldStoreSingleValue_finalizedCheckpoint() throws StateTransitionException {
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
  public void shouldStoreSingleValue_bestJustifiedCheckpoint() throws StateTransitionException {
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
  public void shouldStoreSingleValue_singleBlockAndState() throws StateTransitionException {
    final SignedBlockAndState newBlock = chainBuilder.generateNextBlock();
    // Sanity check
    assertThat(store.getBlock(newBlock.getRoot())).isNull();

    final StoreTransaction transaction = recentChainData.startStoreTransaction();
    transaction.putBlockAndState(newBlock);
    commit(transaction);

    final UpdatableStore result = recreateStore();
    assertThat(result.getSignedBlock(newBlock.getRoot())).isEqualTo(newBlock.getBlock());
    assertThat(result.getBlockState(newBlock.getRoot())).isEqualTo(newBlock.getState());
  }

  @Test
  public void shouldLoadHotBlocksAndStatesIntoMemoryStore() throws StateTransitionException {
    final Bytes32 genesisRoot = genesisBlockAndState.getRoot();
    final StoreTransaction transaction = recentChainData.startStoreTransaction();

    final SignedBlockAndState blockAndState1 = chainBuilder.generateBlockAtSlot(1);
    final SignedBlockAndState blockAndState2 = chainBuilder.generateBlockAtSlot(2);

    transaction.putBlockAndState(blockAndState1);
    transaction.putBlockAndState(blockAndState2);

    commit(transaction);

    final UpdatableStore result = recreateStore();
    assertThat(result.getSignedBlock(genesisRoot)).isEqualTo(genesisBlockAndState.getBlock());
    assertThat(result.getSignedBlock(blockAndState1.getRoot()))
        .isEqualTo(blockAndState1.getBlock());
    assertThat(result.getSignedBlock(blockAndState2.getRoot()))
        .isEqualTo(blockAndState2.getBlock());
    assertThat(result.getBlockState(blockAndState1.getRoot())).isEqualTo(blockAndState1.getState());
    assertThat(result.getBlockState(blockAndState2.getRoot())).isEqualTo(blockAndState2.getState());
  }

  @Test
  public void shouldRemoveHotBlocksAndStatesOnceEpochIsFinalized() throws StateTransitionException {
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
      assertThat(result.getSignedBlock(historicalBlock.getRoot())).isNull();
      assertThat(result.getBlockState(historicalBlock.getRoot())).isNull();
    }

    // Hot blocks should be available in the new store
    for (SignedBlockAndState hotBlock : hotBlocks) {
      assertThat(result.getSignedBlock(hotBlock.getRoot())).isEqualTo(hotBlock.getBlock());
      assertThat(result.getBlockState(hotBlock.getRoot())).isEqualTo(hotBlock.getState());
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
            util.randomUnsignedLong(), util.randomUnsignedLong(), util.randomBytes32());
    database.addMinGenesisTimeBlock(event);

    final Optional<MinGenesisTimeBlockEvent> fetch = database.getMinGenesisTimeBlock();
    assertThat(fetch.isPresent()).isTrue();
    assertThat(fetch.get()).isEqualToComparingFieldByField(event);
  }

  @Test
  public void shouldRecordAndRetrieveDepositEvents() {
    final DataStructureUtil util = new DataStructureUtil();
    final UnsignedLong firstBlock = util.randomUnsignedLong();
    final DepositsFromBlockEvent event1 = util.randomDepositsFromBlockEvent(firstBlock, 10L);
    final DepositsFromBlockEvent event2 =
        util.randomDepositsFromBlockEvent(firstBlock.plus(ONE), 1L);

    database.addDepositsFromBlockEvent(event1);
    database.addDepositsFromBlockEvent(event2);
    try (Stream<DepositsFromBlockEvent> events = database.streamDepositsFromBlocks()) {
      assertThat(events.collect(toList())).containsExactly(event1, event2);
    }
  }

  @Test
  public void handleFinalizationWhenCacheLimitsExceeded() throws StateTransitionException {
    createStorage(StateStorageMode.ARCHIVE);
    initGenesis();

    final int startSlot = genesisBlockAndState.getSlot().intValue();
    final int minFinalSlot = startSlot + StorePruningOptions.DEFAULT_STATE_CACHE_SIZE + 10;
    final UnsignedLong finalizedEpoch =
        ChainProperties.computeBestEpochFinalizableAtSlot(minFinalSlot);
    final UnsignedLong finalizedSlot = compute_start_slot_at_epoch(finalizedEpoch);

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

  public void testShouldRecordFinalizedBlocksAndStates(
      final StateStorageMode storageMode, final boolean batchUpdate)
      throws StateTransitionException {
    testShouldRecordFinalizedBlocksAndStates(storageMode, batchUpdate, this::createStorage);
  }

  protected void testShouldRecordFinalizedBlocksAndStates(
      final StateStorageMode storageMode,
      final boolean batchUpdate,
      Consumer<StateStorageMode> initializeDatabase)
      throws StateTransitionException {
    // Setup chains
    // Both chains share block up to slot 3
    final ChainBuilder primaryChain = ChainBuilder.create(VALIDATOR_KEYS);
    primaryChain.generateGenesis();
    primaryChain.generateBlocksUpToSlot(3);
    final ChainBuilder forkChain = primaryChain.fork();
    // Fork chain's next block is at 6
    forkChain.generateBlockAtSlot(6);
    forkChain.generateBlocksUpToSlot(7);
    // Primary chain's next block is at 7
    final SignedBlockAndState finalizedBlock = primaryChain.generateBlockAtSlot(7);
    final Checkpoint finalizedCheckpoint = getCheckpointForBlock(finalizedBlock.getBlock());
    final UnsignedLong pruneToSlot = finalizedCheckpoint.getEpochStartSlot();
    // Add some blocks in the next epoch
    final UnsignedLong hotSlot = pruneToSlot.plus(UnsignedLong.ONE);
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
    assertStoreWasPruned(store, blocksToPrune, checkpointsToPrune);

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
        final List<UnsignedLong> unavailableSlots =
            allBlocksAndStates.stream().map(SignedBlockAndState::getSlot).collect(toList());
        assertStatesUnavailable(unavailableSlots);
        break;
    }
  }

  protected void assertFinalizedBlocksAvailableViaStream(
      final int fromSlot, final int toSlot, final SignedBeaconBlock... expectedBlocks) {
    try (final Stream<SignedBeaconBlock> stream =
        database.streamFinalizedBlocks(
            UnsignedLong.valueOf(fromSlot), UnsignedLong.valueOf(toSlot))) {
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
    final UnsignedLong genesisSlot = UnsignedLong.valueOf(Constants.GENESIS_SLOT);
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
        assertThat(database.getLatestFinalizedBlockAtSlot(UnsignedLong.valueOf(slot)))
            .describedAs("Latest finalized block at slot %s", slot)
            .contains(currentBlock);
      }
    }

    // Check that last block
    final SignedBeaconBlock lastFinalizedBlock = finalizedBlocks.get(finalizedBlocks.size() - 1);
    for (int i = 0; i < 10; i++) {
      final UnsignedLong slot = lastFinalizedBlock.getSlot().plus(UnsignedLong.valueOf(i));
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
              .map(currentStore::getBlockState)
              .filter(Objects::nonNull)
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
            .map(memoryStore::getBlockState)
            .filter(Objects::nonNull)
            .collect(toList());

    assertThat(hotStates)
        .containsAll(blocksAndStates.stream().map(SignedBlockAndState::getState).collect(toList()));
  }

  protected void assertFinalizedStatesAvailable(final Map<Bytes32, BeaconState> states) {
    for (BeaconState state : states.values()) {
      assertThat(database.getLatestAvailableFinalizedState(state.getSlot())).contains(state);
    }
  }

  protected void assertStatesUnavailable(final Collection<UnsignedLong> slots) {
    for (UnsignedLong slot : slots) {
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

  protected void assertStoreWasPruned(
      final UpdatableStore store,
      final Set<Bytes32> prunedBlocks,
      final Set<Checkpoint> prunedCheckpoints) {
    // Check pruned data has been removed from store
    for (Bytes32 prunedBlock : prunedBlocks) {
      assertThat(store.getBlock(prunedBlock)).isNull();
      assertThat(store.getBlockState(prunedBlock)).isNull();
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

  protected void justifyAndFinalizeEpoch(
      final UnsignedLong epoch, final SignedBlockAndState block) {
    StoreTransaction tx = recentChainData.startStoreTransaction();
    justifyAndFinalizeEpoch(epoch, block, tx);
    tx.commit().reportExceptions();
  }

  protected void justifyAndFinalizeEpoch(
      final UnsignedLong epoch, final SignedBlockAndState block, final StoreTransaction tx) {
    justifyEpoch(epoch, block, tx);
    finalizeEpoch(epoch, block, tx);
  }

  protected void finalizeEpoch(
      final UnsignedLong epoch,
      final SignedBlockAndState block,
      final StoreTransaction transaction) {
    final Checkpoint checkpoint = new Checkpoint(epoch, block.getRoot());
    transaction.setFinalizedCheckpoint(checkpoint);
  }

  protected void justifyEpoch(
      final UnsignedLong epoch,
      final SignedBlockAndState block,
      final StoreTransaction transaction) {
    final Checkpoint checkpoint = new Checkpoint(epoch, block.getRoot());
    transaction.setJustifiedCheckpoint(checkpoint);
  }

  protected Checkpoint getCheckpointForBlock(final SignedBeaconBlock block) {
    final UnsignedLong blockEpoch = compute_epoch_at_slot(block.getSlot());
    final UnsignedLong blockEpochBoundary = compute_start_slot_at_epoch(blockEpoch);
    final UnsignedLong checkpointEpoch =
        equivalentLongs(block.getSlot(), blockEpochBoundary) ? blockEpoch : blockEpoch.plus(ONE);
    return new Checkpoint(checkpointEpoch, block.getMessage().hash_tree_root());
  }

  private boolean equivalentLongs(final UnsignedLong valA, final UnsignedLong valB) {
    return valA.compareTo(valB) == 0;
  }

  protected void initGenesis() {
    recentChainData.initializeFromGenesis(genesisBlockAndState.getState());
    store = recentChainData.getStore();
  }

  protected void generateCheckpoints() throws StateTransitionException {
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
