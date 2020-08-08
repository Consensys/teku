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

package tech.pegasys.teku.storage.client;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.storage.store.MockStoreHelper.mockChainData;
import static tech.pegasys.teku.storage.store.MockStoreHelper.mockGenesis;

import com.google.common.eventbus.EventBus;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSKeyGenerator;
import tech.pegasys.teku.core.ChainBuilder;
import tech.pegasys.teku.core.ChainBuilder.BlockOptions;
import tech.pegasys.teku.core.ChainProperties;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.metrics.TekuMetricCategory;
import tech.pegasys.teku.protoarray.ProtoArrayForkChoiceStrategy;
import tech.pegasys.teku.storage.api.TrackingReorgEventChannel.ReorgEvent;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystem;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;
import tech.pegasys.teku.storage.store.UpdatableStore;
import tech.pegasys.teku.storage.store.UpdatableStore.StoreTransaction;
import tech.pegasys.teku.util.EventSink;
import tech.pegasys.teku.util.config.Constants;
import tech.pegasys.teku.util.config.StateStorageMode;

class RecentChainDataTest {
  private final StorageSystem storageSystem =
      InMemoryStorageSystem.createEmptyLatestStorageSystem(StateStorageMode.PRUNE);
  private final StorageSystem preGenesisStorageSystem =
      InMemoryStorageSystem.createEmptyLatestStorageSystem(StateStorageMode.PRUNE);

  private final ChainBuilder chainBuilder = storageSystem.chainBuilder();
  private final SignedBlockAndState genesis = chainBuilder.generateGenesis();
  private final BeaconState genesisState = genesis.getState();
  private final BeaconBlock genesisBlock = genesis.getBlock().getMessage();

  private final RecentChainData storageClient = storageSystem.recentChainData();
  private final RecentChainData preGenesisStorageClient = preGenesisStorageSystem.recentChainData();

  @BeforeEach
  public void setup() {
    final SafeFuture<Void> initialized = storageClient.initializeFromGenesis(genesisState);
    assertThat(initialized).isCompleted();
  }

  @Test
  public void initialize_setupInitialState() {
    final SafeFuture<Void> initialized =
        preGenesisStorageClient.initializeFromGenesis(genesisState);
    assertThat(initialized).isCompleted();

    assertThat(preGenesisStorageClient.getGenesisTime()).isEqualTo(genesisState.getGenesis_time());
    assertThat(preGenesisStorageClient.getBestSlot())
        .isEqualTo(UInt64.valueOf(Constants.GENESIS_SLOT));
    assertThat(preGenesisStorageClient.getBestState()).hasValue(genesisState);
    assertThat(preGenesisStorageClient.getStore()).isNotNull();
  }

  @Test
  public void updateBestBlock_validUpdate() throws Exception {
    final SignedBlockAndState bestBlock = chainBuilder.generateNextBlock();
    saveBlock(storageClient, bestBlock);

    storageClient.updateBestBlock(bestBlock.getRoot(), bestBlock.getSlot());
    assertThat(storageClient.getBestBlockAndState()).contains(bestBlock.toUnsigned());
  }

  @Test
  public void updateBestBlock_blockAndStateAreMissing() throws Exception {
    final SignedBlockAndState bestBlock = chainBuilder.generateNextBlock();

    storageClient.updateBestBlock(bestBlock.getRoot(), bestBlock.getSlot());
    assertThat(storageClient.getBestBlockAndState()).contains(genesis.toUnsigned());
  }

  @Test
  void retrieveStateInEffectAtSlot_returnEmptyWhenStoreNotSet() {
    final SafeFuture<Optional<BeaconState>> result =
        preGenesisStorageClient.retrieveStateInEffectAtSlot(UInt64.ZERO);
    assertThatSafeFuture(result).isCompletedWithEmptyOptional();
  }

  @Test
  public void retrieveStateInEffectAtSlot_returnGenesisStateWhenItIsTheBestState() {
    assertThat(storageClient.retrieveStateInEffectAtSlot(genesis.getSlot()))
        .isCompletedWithValue(Optional.of(genesisState));
  }

  @Test
  public void retrieveStateInEffectAtSlot_returnStateFromLastBlockWhenSlotsAreEmpty()
      throws Exception {
    // Request block for an empty slot immediately after genesis
    final UInt64 requestedSlot = genesisBlock.getSlot().plus(ONE);
    final UInt64 bestSlot = requestedSlot.plus(ONE);

    final SignedBlockAndState bestBlock = chainBuilder.generateBlockAtSlot(bestSlot);
    updateBestBlock(storageClient, bestBlock);

    assertThat(storageClient.retrieveStateInEffectAtSlot(requestedSlot))
        .isCompletedWithValue(Optional.of(genesisState));
  }

  @Test
  public void retrieveStateInEffectAtSlot_returnStateFromLastBlockWhenHeadSlotIsEmpty() {
    assertThat(storageClient.retrieveStateInEffectAtSlot(ONE))
        .isCompletedWithValue(Optional.of(genesisState));
  }

  @Test
  public void retrieveStateInEffectAtSlot_returnHeadState() throws Exception {
    final SignedBlockAndState bestBlock = addNewBestBlock(storageClient);
    assertThat(storageClient.retrieveStateInEffectAtSlot(bestBlock.getSlot()))
        .isCompletedWithValue(Optional.of(bestBlock.getState()));
  }

  @Test
  public void startStoreTransaction_mutateFinalizedCheckpoint() {
    final Checkpoint originalCheckpoint = storageClient.getStore().getFinalizedCheckpoint();

    // Add a new finalized checkpoint
    final SignedBlockAndState newBlock = advanceChain(storageClient);
    final UInt64 finalizedEpoch = originalCheckpoint.getEpoch().plus(ONE);
    final Checkpoint newCheckpoint = new Checkpoint(finalizedEpoch, newBlock.getRoot());
    assertThat(originalCheckpoint).isNotEqualTo(newCheckpoint); // Sanity check

    final StoreTransaction tx = storageClient.startStoreTransaction();
    tx.setFinalizedCheckpoint(newCheckpoint);

    tx.commit().reportExceptions();

    // Check that store was updated
    final Checkpoint currentCheckpoint = storageClient.getStore().getFinalizedCheckpoint();
    assertThat(currentCheckpoint).isEqualTo(newCheckpoint);
  }

  @Test
  public void startStoreTransaction_doNotMutateFinalizedCheckpoint() {
    final EventBus eventBus = storageSystem.eventBus();
    final List<Checkpoint> checkpointEvents = EventSink.capture(eventBus, Checkpoint.class);
    final Checkpoint originalCheckpoint = storageClient.getStore().getFinalizedCheckpoint();

    final StoreTransaction tx = storageClient.startStoreTransaction();
    tx.setTime(UInt64.valueOf(11L));
    tx.commit().reportExceptions();
    assertThat(checkpointEvents).isEmpty();

    final Checkpoint currentCheckpoint = storageClient.getStore().getFinalizedCheckpoint();
    assertThat(currentCheckpoint).isEqualTo(originalCheckpoint);
  }

  @Test
  public void updateBestBlock_noReorgEventWhenBestBlockFirstSet() {
    final SafeFuture<Void> initialized =
        preGenesisStorageClient.initializeFromGenesis(genesisState);
    assertThat(initialized).isCompleted();

    assertThat(preGenesisStorageSystem.reorgEventChannel().getReorgEvents()).isEmpty();
    assertThat(getReorgCountMetric(preGenesisStorageSystem)).isZero();
  }

  @Test
  public void updateBestBlock_noReorgEventWhenChainAdvances() throws Exception {
    chainBuilder.generateBlocksUpToSlot(2);
    importBlocksAndStates(storageClient, chainBuilder);

    final SignedBlockAndState latestBlockAndState = chainBuilder.getLatestBlockAndState();
    storageClient.updateBestBlock(latestBlockAndState.getRoot(), latestBlockAndState.getSlot());
    assertThat(storageSystem.reorgEventChannel().getReorgEvents()).isEmpty();
  }

  @Test
  public void updateBestBlock_reorgEventWhenBlockFillsEmptyHeadSlot() throws Exception {
    final SignedBlockAndState slot1Block = chainBuilder.generateBlockAtSlot(1);
    importBlocksAndStates(storageClient, chainBuilder);
    storageClient.updateBestBlock(slot1Block.getRoot(), UInt64.valueOf(2));
    assertThat(storageSystem.reorgEventChannel().getReorgEvents()).isEmpty();
    assertThat(getReorgCountMetric(storageSystem)).isZero();

    final SignedBlockAndState slot2Block = chainBuilder.generateBlockAtSlot(2);
    importBlocksAndStates(storageClient, chainBuilder);
    storageClient.updateBestBlock(slot2Block.getRoot(), slot2Block.getSlot());
    final List<ReorgEvent> reorgEvents = storageSystem.reorgEventChannel().getReorgEvents();
    assertThat(reorgEvents).hasSize(1);
    assertThat(reorgEvents.get(0).getBestBlockRoot()).isEqualTo(slot2Block.getRoot());
    assertThat(reorgEvents.get(0).getBestSlot()).isEqualTo(slot2Block.getSlot());
    assertThat(getReorgCountMetric(storageSystem)).isEqualTo(1);
  }

  @Test
  public void updateBestBlock_reorgEventWhenChainSwitchesToNewBlockAtSameSlot() throws Exception {
    final ChainBuilder chainBuilder = ChainBuilder.create(BLSKeyGenerator.generateKeyPairs(16));
    final SignedBlockAndState genesis = chainBuilder.generateGenesis();
    final SafeFuture<Void> initialized =
        preGenesisStorageClient.initializeFromGenesis(genesis.getState());
    assertThat(initialized).isCompleted();
    assertThat(preGenesisStorageSystem.reorgEventChannel().getReorgEvents()).isEmpty();

    chainBuilder.generateBlockAtSlot(1);

    // Set target slot at which to create duplicate blocks
    // and generate block options to make each block unique
    final List<BlockOptions> blockOptions =
        chainBuilder
            .streamValidAttestationsForBlockAtSlot(ONE)
            .map(attestation -> BlockOptions.create().addAttestation(attestation))
            .limit(2)
            .collect(toList());
    final ChainBuilder forkBuilder = chainBuilder.fork();
    final SignedBlockAndState latestBlockAndState =
        chainBuilder.generateBlockAtSlot(UInt64.valueOf(2), blockOptions.get(0));
    final SignedBlockAndState latestForkBlockAndState =
        forkBuilder.generateBlockAtSlot(UInt64.valueOf(2), blockOptions.get(1));
    importBlocksAndStates(preGenesisStorageClient, chainBuilder, forkBuilder);

    // Update to head block of original chain.
    preGenesisStorageClient.updateBestBlock(
        latestBlockAndState.getRoot(), latestBlockAndState.getSlot());
    assertThat(preGenesisStorageSystem.reorgEventChannel().getReorgEvents()).isEmpty();

    // Switch to fork.
    preGenesisStorageClient.updateBestBlock(
        latestForkBlockAndState.getRoot(), latestForkBlockAndState.getSlot());
    // Check reorg event
    assertThat(preGenesisStorageSystem.reorgEventChannel().getReorgEvents().size()).isEqualTo(1);
    final ReorgEvent reorgEvent =
        preGenesisStorageSystem.reorgEventChannel().getReorgEvents().get(0);
    assertThat(reorgEvent.getBestBlockRoot()).isEqualTo(latestForkBlockAndState.getRoot());
    assertThat(reorgEvent.getBestSlot()).isEqualTo(latestForkBlockAndState.getSlot());
  }

  @Test
  public void updateBestBlock_reorgEventWhenChainSwitchesToNewBlockAtLaterSlot() throws Exception {
    final ChainBuilder chainBuilder = ChainBuilder.create(BLSKeyGenerator.generateKeyPairs(16));
    final SignedBlockAndState genesis = chainBuilder.generateGenesis();
    final SafeFuture<Void> initialized =
        preGenesisStorageClient.initializeFromGenesis(genesis.getState());
    assertThat(initialized).isCompleted();
    assertThat(preGenesisStorageSystem.reorgEventChannel().getReorgEvents()).isEmpty();

    chainBuilder.generateBlockAtSlot(1);

    // Set target slot at which to create duplicate blocks
    // and generate block options to make each block unique
    final List<BlockOptions> blockOptions =
        chainBuilder
            .streamValidAttestationsForBlockAtSlot(ONE)
            .map(attestation -> BlockOptions.create().addAttestation(attestation))
            .limit(2)
            .collect(toList());
    final ChainBuilder forkBuilder = chainBuilder.fork();
    final SignedBlockAndState latestBlockAndState =
        chainBuilder.generateBlockAtSlot(UInt64.valueOf(2), blockOptions.get(0));

    forkBuilder.generateBlockAtSlot(UInt64.valueOf(2), blockOptions.get(1));

    // Fork extends a slot further
    final SignedBlockAndState latestForkBlockAndState = forkBuilder.generateBlockAtSlot(3);
    importBlocksAndStates(preGenesisStorageClient, chainBuilder, forkBuilder);

    // Update to head block of original chain.
    preGenesisStorageClient.updateBestBlock(
        latestBlockAndState.getRoot(), latestBlockAndState.getSlot());
    assertThat(preGenesisStorageSystem.reorgEventChannel().getReorgEvents()).isEmpty();

    // Switch to fork.
    preGenesisStorageClient.updateBestBlock(
        latestForkBlockAndState.getRoot(), latestForkBlockAndState.getSlot());
    // Check reorg event
    final ReorgEvent reorgEvent =
        preGenesisStorageSystem.reorgEventChannel().getReorgEvents().get(0);
    assertThat(reorgEvent.getBestBlockRoot()).isEqualTo(latestForkBlockAndState.getRoot());
    assertThat(reorgEvent.getBestSlot()).isEqualTo(latestForkBlockAndState.getSlot());
  }

  @Test
  public void updateBestBlock_ignoreStaleUpdate() throws Exception {
    final UpdatableStore store = mock(UpdatableStore.class);

    // Set up mock store with genesis data and a small chain
    List<SignedBlockAndState> chain = chainBuilder.generateBlocksUpToSlot(3);
    mockGenesis(store, genesis);
    mockChainData(store, chain);

    // Set store and update best block to genesis
    assertThat(preGenesisStorageClient.getBestBlockAndState()).isEmpty();
    preGenesisStorageClient.setStore(store);
    preGenesisStorageClient.updateBestBlock(genesis.getRoot(), genesis.getSlot());
    assertThat(preGenesisStorageClient.getBestBlockAndState()).contains(genesis.toUnsigned());

    // Update best block, but delay the resolution of the future
    final SignedBlockAndState chainHeadA = chain.get(0);
    final SafeFuture<Optional<SignedBlockAndState>> chainHeadAFuture = new SafeFuture<>();
    when(store.retrieveBlockAndState(chainHeadA.getRoot())).thenReturn(chainHeadAFuture);
    preGenesisStorageClient.updateBestBlock(chainHeadA.getRoot(), chainHeadA.getSlot());
    // We should still be at genesis while we wait on the future to resolve
    assertThat(preGenesisStorageClient.getBestBlockAndState()).contains(genesis.toUnsigned());

    // Now start another update
    final SignedBlockAndState chainHeadB = chain.get(1);
    preGenesisStorageClient.updateBestBlock(chainHeadB.getRoot(), chainHeadB.getSlot());
    assertThat(preGenesisStorageClient.getBestBlockAndState()).contains(chainHeadB.toUnsigned());

    // Resolve the earlier update - which should be ignored since we've already moved on
    chainHeadAFuture.complete(Optional.of(chainHeadA));
    assertThat(preGenesisStorageClient.getBestBlockAndState()).contains(chainHeadB.toUnsigned());
  }

  @Test
  public void getLatestFinalizedBlockSlot_genesis() {
    assertThat(storageClient.getStore().getLatestFinalizedBlockSlot()).isEqualTo(genesis.getSlot());
  }

  @Test
  public void getLatestFinalizedBlockSlot_postGenesisFinalizedBlockOutsideOfEpochBoundary()
      throws Exception {
    final UInt64 epoch = ONE;
    final UInt64 epochBoundarySlot = compute_start_slot_at_epoch(epoch);
    final UInt64 finalizedBlockSlot = epochBoundarySlot.minus(ONE);
    final SignedBlockAndState finalizedBlock = chainBuilder.generateBlockAtSlot(finalizedBlockSlot);
    saveBlock(storageClient, finalizedBlock);

    // Start tx to update finalized checkpoint
    final StoreTransaction tx = storageClient.startStoreTransaction();
    // Initially finalized slot should match store
    assertThat(tx.getLatestFinalizedBlockSlot()).isEqualTo(genesis.getSlot());
    // Update checkpoint and check finalized slot accessors
    tx.setFinalizedCheckpoint(new Checkpoint(epoch, finalizedBlock.getRoot()));
    assertThat(tx.getLatestFinalizedBlockSlot()).isEqualTo(finalizedBlockSlot);
    assertThat(storageClient.getStore().getLatestFinalizedBlockSlot()).isEqualTo(genesis.getSlot());
    // Commit tx
    tx.commit().reportExceptions();

    assertThat(storageClient.getStore().getLatestFinalizedBlockSlot())
        .isEqualTo(finalizedBlockSlot);
  }

  @Test
  public void retrieveBlockAndState_withBlockAndStateAvailable() throws Exception {
    final SignedBlockAndState block = advanceChain(storageClient);
    assertThat(storageClient.getStore().retrieveBlockAndState(block.getRoot()))
        .isCompletedWithValue(Optional.of(block));
  }

  @Test
  public void retrieveBlockAndState_withinTxFromUnderlyingStore() throws Exception {
    final SignedBlockAndState block = advanceChain(storageClient);
    final StoreTransaction tx = storageClient.startStoreTransaction();
    assertThat(tx.retrieveBlockAndState(block.getRoot())).isCompletedWithValue(Optional.of(block));
  }

  @Test
  public void retrieveBlockAndState_withinTxFromUpdates() throws Exception {
    final SignedBlockAndState block = chainBuilder.generateNextBlock();

    final StoreTransaction tx = storageClient.startStoreTransaction();
    tx.putBlockAndState(block);

    assertThat(tx.retrieveBlockAndState(block.getRoot())).isCompletedWithValue(Optional.of(block));
  }

  @Test
  public void getBlockRootBySlot_forOutOfRangeSlot() throws Exception {
    disableForkChoicePruneThreshold();
    final UInt64 historicalRoots = UInt64.valueOf(Constants.SLOTS_PER_HISTORICAL_ROOT);
    final UInt64 targetSlot = UInt64.valueOf(10);
    final UInt64 finalizedBlockSlot = targetSlot.plus(historicalRoots).plus(ONE);
    final UInt64 finalizedEpoch = compute_epoch_at_slot(finalizedBlockSlot).plus(ONE);

    // Add a block within the finalized range
    final SignedBlockAndState historicalBlock = chainBuilder.generateBlockAtSlot(targetSlot);
    final SignedBlockAndState finalizedBlock = chainBuilder.generateBlockAtSlot(finalizedBlockSlot);
    saveBlock(storageClient, historicalBlock);
    finalizeBlock(storageClient, finalizedEpoch, finalizedBlock);
    advanceBestBlock(storageClient);

    assertThat(storageClient.getBlockRootBySlot(targetSlot)).isEmpty();
  }

  @Test
  public void getBlockRootBySlot_forHistoricalSlotInRange() throws Exception {
    final UInt64 historicalRoots = UInt64.valueOf(Constants.SLOTS_PER_HISTORICAL_ROOT);
    final UInt64 targetSlot = UInt64.valueOf(10);
    final UInt64 finalizedBlockSlot = targetSlot.plus(historicalRoots);
    final UInt64 finalizedEpoch = compute_epoch_at_slot(finalizedBlockSlot).plus(ONE);

    // Add a block within the finalized range
    final SignedBlockAndState historicalBlock = chainBuilder.generateBlockAtSlot(targetSlot);
    final SignedBlockAndState finalizedBlock = chainBuilder.generateBlockAtSlot(finalizedBlockSlot);
    saveBlock(storageClient, historicalBlock);
    finalizeBlock(storageClient, finalizedEpoch, finalizedBlock);
    advanceBestBlock(storageClient);

    assertThat(storageClient.getBlockRootBySlot(targetSlot)).contains(historicalBlock.getRoot());
  }

  @Test
  public void getBlockRootBySlot_forBestBlock() throws Exception {
    final SignedBlockAndState bestBlock = advanceBestBlock(storageClient);

    assertThat(storageClient.getBlockRootBySlot(bestBlock.getSlot())).contains(bestBlock.getRoot());
  }

  @Test
  public void getBlockRootBySlot_forBlockPriorToBestBlock() throws Exception {
    final SignedBlockAndState targetBlock = advanceBestBlock(storageClient);
    advanceBestBlock(storageClient);

    assertThat(storageClient.getBlockRootBySlot(targetBlock.getSlot()))
        .contains(targetBlock.getRoot());
  }

  @Test
  public void getBlockRootBySlot_forSlotAfterBestBlock() throws Exception {
    final SignedBlockAndState bestBlock = advanceBestBlock(storageClient);

    final UInt64 targetSlot = bestBlock.getSlot().plus(ONE);
    assertThat(storageClient.getBlockRootBySlot(targetSlot)).contains(bestBlock.getRoot());
  }

  @Test
  public void getBlockRootBySlot_queryEntireChain() throws Exception {
    disableForkChoicePruneThreshold();
    final UInt64 historicalRoots = UInt64.valueOf(Constants.SLOTS_PER_HISTORICAL_ROOT);

    // Build a chain that spans multiple increments of SLOTS_PER_HISTORICAL_ROOT
    final int skipBlocks = 3;
    final UInt64 skipBlocksLong = UInt64.valueOf(skipBlocks);
    final UInt64 finalizedBlockSlot = UInt64.valueOf(10).plus(historicalRoots);
    final UInt64 finalizedEpoch =
        ChainProperties.computeBestEpochFinalizableAtSlot(finalizedBlockSlot);
    final UInt64 recentSlot = compute_start_slot_at_epoch(finalizedEpoch).plus(ONE);
    final UInt64 chainHeight =
        historicalRoots.times(UInt64.valueOf(2)).plus(recentSlot).plus(UInt64.valueOf(5));
    // Build historical blocks
    final SignedBlockAndState finalizedBlock;
    while (true) {
      if (chainBuilder.getLatestSlot().plus(skipBlocksLong).compareTo(finalizedBlockSlot) >= 0) {
        // Add our target finalized block
        finalizedBlock = chainBuilder.generateBlockAtSlot(finalizedBlockSlot);
        saveBlock(storageClient, finalizedBlock);
        break;
      }
      final SignedBlockAndState nextBlock = chainBuilder.generateNextBlock(skipBlocks);
      saveBlock(storageClient, nextBlock);
    }
    // Build recent blocks
    SignedBlockAndState bestBlock = null;
    UInt64 nextSlot = recentSlot;
    while (chainBuilder.getLatestSlot().compareTo(chainHeight) < 0) {
      bestBlock = chainBuilder.generateBlockAtSlot(nextSlot);
      saveBlock(storageClient, bestBlock);
      nextSlot = nextSlot.plus(UInt64.valueOf(skipBlocks));
    }
    // Update best block and finalized state
    updateBestBlock(storageClient, bestBlock);
    finalizeBlock(storageClient, finalizedEpoch, finalizedBlock);

    // Check slots that should be unavailable
    for (int i = 0; i < finalizedBlockSlot.intValue(); i++) {
      final UInt64 targetSlot = UInt64.valueOf(i);
      assertThat(storageClient.getBlockRootBySlot(targetSlot)).isEmpty();
    }
    // Check slots that should be available
    for (int i = finalizedBlockSlot.intValue(); i <= bestBlock.getSlot().intValue(); i++) {
      final UInt64 targetSlot = UInt64.valueOf(i);
      final SignedBlockAndState expectedResult =
          chainBuilder.getLatestBlockAndStateAtSlot(targetSlot);
      final Optional<Bytes32> result = storageClient.getBlockRootBySlot(targetSlot);
      assertThat(result)
          .withFailMessage(
              "Expected root at slot %s to be %s (%s) but was %s",
              targetSlot, expectedResult.getRoot(), expectedResult.getSlot(), result)
          .contains(expectedResult.getRoot());
    }
  }

  @Test
  public void getBlockRootBySlotWithHeadRoot_forSlotAfterHeadRoot() throws Exception {
    final SignedBlockAndState targetBlock = advanceBestBlock(storageClient);
    final SignedBlockAndState bestBlock = advanceBestBlock(storageClient);

    assertThat(storageClient.getBlockRootBySlot(bestBlock.getSlot(), targetBlock.getRoot()))
        .contains(targetBlock.getRoot());
  }

  @Test
  public void getBlockRootBySlotWithHeadRoot_forUnknownHeadRoot() throws Exception {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil();
    final Bytes32 headRoot = dataStructureUtil.randomBytes32();
    final SignedBlockAndState bestBlock = advanceBestBlock(storageClient);

    assertThat(storageClient.getBlockRootBySlot(bestBlock.getSlot(), headRoot)).isEmpty();
  }

  @Test
  public void getBlockRootBySlotWithHeadRoot_withForkRoot() throws Exception {
    // Build small chain
    for (int i = 0; i < 5; i++) {
      advanceChain(storageClient);
    }

    // Split the chain
    final ChainBuilder fork = chainBuilder.fork();
    final UInt64 chainSplitSlot = chainBuilder.getLatestSlot();
    for (int i = 0; i < 5; i++) {
      final UInt64 canonicalBlockSlot = chainSplitSlot.plus(UInt64.valueOf(i * 2 + 2));
      final UInt64 forkSlot = chainSplitSlot.plus(UInt64.valueOf(i * 2 + 1));
      updateBestBlock(storageClient, chainBuilder.generateBlockAtSlot(canonicalBlockSlot));
      saveBlock(storageClient, fork.generateBlockAtSlot(forkSlot));
    }

    final Bytes32 headRoot = fork.getLatestBlockAndState().getRoot();
    for (int i = 0; i < fork.getLatestSlot().intValue(); i++) {
      final UInt64 targetSlot = UInt64.valueOf(i);
      final SignedBlockAndState expectedBlock = fork.getLatestBlockAndStateAtSlot(targetSlot);
      if (targetSlot.compareTo(chainSplitSlot) > 0) {
        // Sanity check that fork differs from main chain
        assertThat(expectedBlock)
            .isNotEqualTo(chainBuilder.getLatestBlockAndStateAtSlot(targetSlot));
      }
      assertThat(storageClient.getBlockRootBySlot(targetSlot, headRoot))
          .contains(expectedBlock.getRoot());
    }
  }

  @Test
  public void commit_pruneParallelNewBlocks() throws Exception {
    testCommitPruningOfParallelBlocks(true);
  }

  @Test
  public void commit_pruneParallelExistingBlocks() throws Exception {
    testCommitPruningOfParallelBlocks(false);
  }

  /**
   * Builds 2 parallel chains, one of which will get pruned when a block in the middle of the other
   * chain is finalized. Keep one chain in the finalizing transaction, the other chain is already
   * saved to the store.
   *
   * @param pruneNewBlocks Whether to keep the blocks to be pruned in the finalizing transaction, or
   *     keep the blocks to be kept in the finalizing transaction @
   */
  private void testCommitPruningOfParallelBlocks(final boolean pruneNewBlocks) {
    final UInt64 epoch2Slot = compute_start_slot_at_epoch(UInt64.valueOf(2));

    // Create a fork by skipping the next slot on the fork chain
    ChainBuilder fork = chainBuilder.fork();
    // Generate the next 2 blocks on the primary chain
    final SignedBlockAndState firstCanonicalBlock = chainBuilder.generateNextBlock();
    saveBlock(storageClient, firstCanonicalBlock);
    saveBlock(storageClient, chainBuilder.generateNextBlock());
    // Skip a block and then generate the next block on the fork chain
    final SignedBlockAndState firstForkBlock = fork.generateNextBlock(1);
    saveBlock(storageClient, firstForkBlock);

    // Build both the primary and fork chain past epoch1
    // Make sure both chains are at the same slot
    assertThat(chainBuilder.getLatestSlot()).isEqualTo(fork.getLatestSlot());
    while (chainBuilder.getLatestSlot().compareTo(epoch2Slot) < 0) {
      chainBuilder.generateNextBlock();
      fork.generateNextBlock();
    }

    // Save one chain to the store, setup the other chain to be saved in the finalizing transaction
    final List<SignedBlockAndState> newBlocks = new ArrayList<>();
    if (pruneNewBlocks) {
      // Save canonical blocks now, put fork blocks in the transaction
      chainBuilder
          .streamBlocksAndStates(firstCanonicalBlock.getSlot())
          .forEach(b -> saveBlock(storageClient, b));
      fork.streamBlocksAndStates(firstForkBlock.getSlot()).forEach(newBlocks::add);
    } else {
      // Save fork blocks now, put canonical blocks in the transaction
      chainBuilder.streamBlocksAndStates(firstCanonicalBlock.getSlot()).forEach(newBlocks::add);
      fork.streamBlocksAndStates(firstForkBlock.getSlot())
          .forEach(b -> saveBlock(storageClient, b));
    }

    // Add blocks and finalize epoch 1, so that blocks will be pruned
    final StoreTransaction tx = storageClient.startStoreTransaction();
    final Checkpoint finalizedCheckpoint = chainBuilder.getCurrentCheckpointForEpoch(1);
    tx.setFinalizedCheckpoint(finalizedCheckpoint);
    newBlocks.forEach(tx::putBlockAndState);
    tx.commit().reportExceptions();

    // Check that only recent, canonical blocks at or after the latest finalized block are left in
    // the store
    final List<SignedBlockAndState> expectedBlocks =
        chainBuilder
            .streamBlocksAndStates(finalizedCheckpoint.getEpochStartSlot())
            .collect(Collectors.toList());
    final Set<Bytes32> blockRoots =
        expectedBlocks.stream().map(SignedBlockAndState::getRoot).collect(Collectors.toSet());
    // Collect blocks that should be pruned
    final Set<Bytes32> prunedBlocks =
        fork.streamBlocksAndStates(firstForkBlock.getSlot())
            .map(SignedBlockAndState::getRoot)
            .collect(Collectors.toSet());

    // Check expected blocks
    assertThat(storageClient.getStore().getBlockRoots())
        .containsExactlyInAnyOrderElementsOf(blockRoots);
    for (SignedBlockAndState expectedBlock : expectedBlocks) {
      assertThat(storageClient.retrieveSignedBlockByRoot(expectedBlock.getRoot()))
          .isCompletedWithValue(Optional.of(expectedBlock.getBlock()));
      assertThat(storageClient.retrieveBlockState(expectedBlock.getRoot()))
          .isCompletedWithValue(Optional.of(expectedBlock.getState()));
    }
    // Check pruned blocks
    for (Bytes32 prunedBlock : prunedBlocks) {
      assertThatSafeFuture(storageClient.retrieveSignedBlockByRoot(prunedBlock))
          .isCompletedWithEmptyOptional();
      assertThat(storageClient.retrieveBlockState(prunedBlock))
          .isCompletedWithValue(Optional.empty());
    }
  }

  private void importBlocksAndStates(
      final RecentChainData client, final ChainBuilder... chainBuilders) {
    final StoreTransaction transaction = client.startStoreTransaction();
    Stream.of(chainBuilders)
        .flatMap(ChainBuilder::streamBlocksAndStates)
        .forEach(
            blockAndState -> {
              transaction.putBlockAndState(blockAndState);
              client
                  .getForkChoiceStrategy()
                  .orElseThrow()
                  .onBlock(blockAndState.getBlock().getMessage(), blockAndState.getState());
            });
    transaction.commit().join();
  }

  private SignedBlockAndState addNewBestBlock(RecentChainData recentChainData) {
    final SignedBlockAndState nextBlock = chainBuilder.generateNextBlock();
    updateBestBlock(recentChainData, nextBlock);

    return nextBlock;
  }

  private void updateBestBlock(
      RecentChainData recentChainData, final SignedBlockAndState bestBlock) {
    saveBlock(recentChainData, bestBlock);

    storageClient.updateBestBlock(bestBlock.getRoot(), bestBlock.getSlot());
  }

  private SignedBlockAndState advanceBestBlock(final RecentChainData recentChainData) {
    final SignedBlockAndState nextBlock = advanceChain(recentChainData);
    updateBestBlock(recentChainData, nextBlock);
    return nextBlock;
  }

  private void finalizeBlock(
      RecentChainData recentChainData,
      final UInt64 epoch,
      final SignedBlockAndState finalizedBlock) {
    saveBlock(recentChainData, finalizedBlock);

    final StoreTransaction tx = recentChainData.startStoreTransaction();
    tx.setFinalizedCheckpoint(new Checkpoint(epoch, finalizedBlock.getRoot()));
    assertThat(tx.commit()).isCompleted();
  }

  private SignedBlockAndState advanceChain(final RecentChainData recentChainData) {
    final SignedBlockAndState nextBlock = chainBuilder.generateNextBlock();
    saveBlock(recentChainData, nextBlock);
    return nextBlock;
  }

  private void saveBlock(final RecentChainData recentChainData, final SignedBlockAndState block) {
    final StoreTransaction tx = recentChainData.startStoreTransaction();
    tx.putBlockAndState(block);
    tx.commit().reportExceptions();
    recentChainData
        .getForkChoiceStrategy()
        .orElseThrow()
        .onBlock(block.getBlock().getMessage(), block.getState());
  }

  private void disableForkChoicePruneThreshold() {
    ((ProtoArrayForkChoiceStrategy) storageClient.getForkChoiceStrategy().orElseThrow())
        .setPruneThreshold(0);
  }

  private long getReorgCountMetric(final StorageSystem storageSystem) {
    return storageSystem
        .getMetricsSystem()
        .getCounter(TekuMetricCategory.BEACON, "reorgs_total")
        .getValue();
  }
}
