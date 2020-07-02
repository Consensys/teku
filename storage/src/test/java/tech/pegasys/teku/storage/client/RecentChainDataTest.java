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

import static com.google.common.primitives.UnsignedLong.ONE;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
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
import tech.pegasys.teku.core.StateTransitionException;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.protoarray.ProtoArrayForkChoiceStrategy;
import tech.pegasys.teku.storage.api.TrackingReorgEventChannel.ReorgEvent;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystem;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;
import tech.pegasys.teku.storage.store.UpdatableStore.StoreTransaction;
import tech.pegasys.teku.util.EventSink;
import tech.pegasys.teku.util.config.Constants;
import tech.pegasys.teku.util.config.StateStorageMode;

class RecentChainDataTest {
  private final StorageSystem storageSystem =
      InMemoryStorageSystem.createEmptyV3StorageSystem(StateStorageMode.PRUNE);
  private final StorageSystem preGenesisStorageSystem =
      InMemoryStorageSystem.createEmptyV3StorageSystem(StateStorageMode.PRUNE);

  private final ChainBuilder chainBuilder = storageSystem.chainBuilder();
  private final SignedBlockAndState genesis = chainBuilder.generateGenesis();
  private final BeaconState genesisState = genesis.getState();
  private final BeaconBlock genesisBlock = genesis.getBlock().getMessage();

  private final RecentChainData storageClient = storageSystem.recentChainData();
  private final RecentChainData preGenesisStorageClient = preGenesisStorageSystem.recentChainData();

  @BeforeEach
  public void setup() {
    storageClient.initializeFromGenesis(genesisState);
  }

  @Test
  public void initialize_setupInitialState() {
    preGenesisStorageClient.initializeFromGenesis(genesisState);
    assertThat(preGenesisStorageClient.getGenesisTime()).isEqualTo(genesisState.getGenesis_time());
    assertThat(preGenesisStorageClient.getBestSlot())
        .isEqualTo(UnsignedLong.valueOf(Constants.GENESIS_SLOT));
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
  void getStateInEffectAtSlot_returnEmptyWhenStoreNotSet() {
    assertThat(preGenesisStorageClient.getStateInEffectAtSlot(UnsignedLong.ZERO)).isEmpty();
  }

  @Test
  public void getStateInEffectAtSlot_returnGenesisStateWhenItIsTheBestState() {
    assertThat(storageClient.getStateInEffectAtSlot(genesis.getSlot())).contains(genesisState);
  }

  @Test
  public void getStateInEffectAtSlot_returnStateFromLastBlockWhenSlotsAreEmpty() throws Exception {
    // Request block for an empty slot immediately after genesis
    final UnsignedLong requestedSlot = genesisBlock.getSlot().plus(ONE);
    final UnsignedLong bestSlot = requestedSlot.plus(ONE);

    final SignedBlockAndState bestBlock = chainBuilder.generateBlockAtSlot(bestSlot);
    updateBestBlock(storageClient, bestBlock);

    assertThat(storageClient.getStateInEffectAtSlot(requestedSlot)).contains(genesisState);
  }

  @Test
  public void getStateInEffectAtSlot_returnStateFromLastBlockWhenHeadSlotIsEmpty() {
    assertThat(storageClient.getStateInEffectAtSlot(ONE)).contains(genesisState);
  }

  @Test
  public void getStateInEffectAtSlot_returnHeadState() throws Exception {
    final SignedBlockAndState bestBlock = addNewBestBlock(storageClient);
    assertThat(storageClient.getStateInEffectAtSlot(bestBlock.getSlot()))
        .contains(bestBlock.getState());
  }

  @Test
  public void startStoreTransaction_mutateFinalizedCheckpoint() throws StateTransitionException {
    final BeaconState genesisState = chainBuilder.getStateAtSlot(Constants.GENESIS_SLOT);
    preGenesisStorageClient.initializeFromGenesis(genesisState);

    final Checkpoint originalCheckpoint =
        preGenesisStorageClient.getStore().getFinalizedCheckpoint();

    // Add a new finalized checkpoint
    final SignedBlockAndState newBlock = advanceChain(preGenesisStorageClient);
    final UnsignedLong finalizedEpoch = originalCheckpoint.getEpoch().plus(ONE);
    final Checkpoint newCheckpoint = new Checkpoint(finalizedEpoch, newBlock.getRoot());
    assertThat(originalCheckpoint).isNotEqualTo(newCheckpoint); // Sanity check

    final StoreTransaction tx = preGenesisStorageClient.startStoreTransaction();
    tx.setFinalizedCheckpoint(newCheckpoint);

    tx.commit().reportExceptions();

    // Check that store was updated
    final Checkpoint currentCheckpoint =
        preGenesisStorageClient.getStore().getFinalizedCheckpoint();
    assertThat(currentCheckpoint).isEqualTo(newCheckpoint);
  }

  @Test
  public void startStoreTransaction_doNotMutateFinalizedCheckpoint() {
    final EventBus eventBus = preGenesisStorageSystem.eventBus();
    final List<Checkpoint> checkpointEvents = EventSink.capture(eventBus, Checkpoint.class);
    preGenesisStorageSystem.chainUpdater().initializeGenesis();
    final Checkpoint originalCheckpoint =
        preGenesisStorageClient.getStore().getFinalizedCheckpoint();

    final StoreTransaction tx = preGenesisStorageClient.startStoreTransaction();
    tx.setTime(UnsignedLong.valueOf(11L));
    tx.commit().reportExceptions();
    assertThat(checkpointEvents).isEmpty();

    final Checkpoint currentCheckpoint =
        preGenesisStorageClient.getStore().getFinalizedCheckpoint();
    assertThat(currentCheckpoint).isEqualTo(originalCheckpoint);
  }

  @Test
  public void updateBestBlock_noReorgEventWhenBestBlockFirstSet() {
    preGenesisStorageClient.initializeFromGenesis(genesisState);
    assertThat(preGenesisStorageSystem.reorgEventChannel().getReorgEvents()).isEmpty();
  }

  @Test
  public void updateBestBlock_noReorgEventWhenChainAdvances() throws Exception {
    final ChainBuilder chainBuilder = ChainBuilder.create(BLSKeyGenerator.generateKeyPairs(1));
    chainBuilder.generateGenesis();
    preGenesisStorageClient.initializeFromGenesis(chainBuilder.getStateAtSlot(0));
    assertThat(preGenesisStorageSystem.reorgEventChannel().getReorgEvents()).isEmpty();

    chainBuilder.generateBlocksUpToSlot(2);
    importBlocksAndStates(chainBuilder);

    final SignedBlockAndState latestBlockAndState = chainBuilder.getLatestBlockAndState();
    preGenesisStorageClient.updateBestBlock(
        latestBlockAndState.getRoot(), latestBlockAndState.getSlot());
    assertThat(preGenesisStorageSystem.reorgEventChannel().getReorgEvents()).isEmpty();
  }

  @Test
  public void updateBestBlock_reorgEventWhenBlockFillsEmptyHeadSlot() throws Exception {
    final ChainBuilder chainBuilder = ChainBuilder.create(BLSKeyGenerator.generateKeyPairs(1));
    chainBuilder.generateGenesis();
    preGenesisStorageClient.initializeFromGenesis(chainBuilder.getStateAtSlot(0));
    assertThat(preGenesisStorageSystem.reorgEventChannel().getReorgEvents()).isEmpty();

    final SignedBlockAndState slot1Block = chainBuilder.generateBlockAtSlot(1);
    importBlocksAndStates(chainBuilder);
    preGenesisStorageClient.updateBestBlock(slot1Block.getRoot(), UnsignedLong.valueOf(2));
    assertThat(preGenesisStorageSystem.reorgEventChannel().getReorgEvents()).isEmpty();

    final SignedBlockAndState slot2Block = chainBuilder.generateBlockAtSlot(2);
    importBlocksAndStates(chainBuilder);
    preGenesisStorageClient.updateBestBlock(slot2Block.getRoot(), slot2Block.getSlot());
    final List<ReorgEvent> reorgEvents =
        preGenesisStorageSystem.reorgEventChannel().getReorgEvents();
    assertThat(reorgEvents).hasSize(1);
    assertThat(reorgEvents.get(0).getBestBlockRoot()).isEqualTo(slot2Block.getRoot());
    assertThat(reorgEvents.get(0).getBestSlot()).isEqualTo(slot2Block.getSlot());
  }

  @Test
  public void updateBestBlock_reorgEventWhenChainSwitchesToNewBlockAtSameSlot() throws Exception {
    final ChainBuilder chainBuilder = ChainBuilder.create(BLSKeyGenerator.generateKeyPairs(16));
    chainBuilder.generateGenesis();
    preGenesisStorageClient.initializeFromGenesis(chainBuilder.getStateAtSlot(0));
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
        chainBuilder.generateBlockAtSlot(UnsignedLong.valueOf(2), blockOptions.get(0));
    final SignedBlockAndState latestForkBlockAndState =
        forkBuilder.generateBlockAtSlot(UnsignedLong.valueOf(2), blockOptions.get(1));
    importBlocksAndStates(chainBuilder, forkBuilder);

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
    chainBuilder.generateGenesis();
    preGenesisStorageClient.initializeFromGenesis(chainBuilder.getStateAtSlot(0));
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
        chainBuilder.generateBlockAtSlot(UnsignedLong.valueOf(2), blockOptions.get(0));

    forkBuilder.generateBlockAtSlot(UnsignedLong.valueOf(2), blockOptions.get(1));

    // Fork extends a slot further
    final SignedBlockAndState latestForkBlockAndState = forkBuilder.generateBlockAtSlot(3);
    importBlocksAndStates(chainBuilder, forkBuilder);

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
  public void getLatestFinalizedBlockSlot_genesis() {
    assertThat(storageClient.getStore().getLatestFinalizedBlockSlot()).isEqualTo(genesis.getSlot());
  }

  @Test
  public void getLatestFinalizedBlockSlot_postGenesisFinalizedBlockOutsideOfEpochBoundary()
      throws Exception {
    final UnsignedLong epoch = ONE;
    final UnsignedLong epochBoundarySlot = compute_start_slot_at_epoch(epoch);
    final UnsignedLong finalizedBlockSlot = epochBoundarySlot.minus(ONE);
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
  public void getBlockAndState_withBlockAndStateAvailable() throws Exception {
    final SignedBlockAndState block = advanceChain(storageClient);
    assertThat(storageClient.getStore().getBlockAndState(block.getRoot())).contains(block);
  }

  @Test
  public void getBlockAndState_withinTxFromUnderlyingStore() throws Exception {
    final SignedBlockAndState block = advanceChain(storageClient);
    final StoreTransaction tx = storageClient.startStoreTransaction();
    assertThat(tx.getBlockAndState(block.getRoot())).contains(block);
  }

  @Test
  public void getBlockAndState_withinTxFromUpdates() throws Exception {
    final SignedBlockAndState block = chainBuilder.generateNextBlock();

    final StoreTransaction tx = storageClient.startStoreTransaction();
    tx.putBlockAndState(block);

    assertThat(tx.getBlockAndState(block.getRoot())).contains(block);
  }

  @Test
  public void getBlockRootBySlot_forOutOfRangeSlot() throws Exception {
    disableForkChoicePruneThreshold();
    final UnsignedLong historicalRoots = UnsignedLong.valueOf(Constants.SLOTS_PER_HISTORICAL_ROOT);
    final UnsignedLong targetSlot = UnsignedLong.valueOf(10);
    final UnsignedLong finalizedBlockSlot = targetSlot.plus(historicalRoots).plus(ONE);
    final UnsignedLong finalizedEpoch = compute_epoch_at_slot(finalizedBlockSlot).plus(ONE);

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
    final UnsignedLong historicalRoots = UnsignedLong.valueOf(Constants.SLOTS_PER_HISTORICAL_ROOT);
    final UnsignedLong targetSlot = UnsignedLong.valueOf(10);
    final UnsignedLong finalizedBlockSlot = targetSlot.plus(historicalRoots);
    final UnsignedLong finalizedEpoch = compute_epoch_at_slot(finalizedBlockSlot).plus(ONE);

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

    final UnsignedLong targetSlot = bestBlock.getSlot().plus(ONE);
    assertThat(storageClient.getBlockRootBySlot(targetSlot)).contains(bestBlock.getRoot());
  }

  @Test
  public void getBlockRootBySlot_queryEntireChain() throws Exception {
    disableForkChoicePruneThreshold();
    final UnsignedLong historicalRoots = UnsignedLong.valueOf(Constants.SLOTS_PER_HISTORICAL_ROOT);

    // Build a chain that spans multiple increments of SLOTS_PER_HISTORICAL_ROOT
    final int skipBlocks = 3;
    final UnsignedLong skipBlocksLong = UnsignedLong.valueOf(skipBlocks);
    final UnsignedLong finalizedBlockSlot = UnsignedLong.valueOf(10).plus(historicalRoots);
    final UnsignedLong finalizedEpoch =
        ChainProperties.computeBestEpochFinalizableAtSlot(finalizedBlockSlot);
    final UnsignedLong recentSlot = compute_start_slot_at_epoch(finalizedEpoch).plus(ONE);
    final UnsignedLong chainHeight =
        historicalRoots
            .times(UnsignedLong.valueOf(2))
            .plus(recentSlot)
            .plus(UnsignedLong.valueOf(5));
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
    UnsignedLong nextSlot = recentSlot;
    while (chainBuilder.getLatestSlot().compareTo(chainHeight) < 0) {
      bestBlock = chainBuilder.generateBlockAtSlot(nextSlot);
      saveBlock(storageClient, bestBlock);
      nextSlot = nextSlot.plus(UnsignedLong.valueOf(skipBlocks));
    }
    // Update best block and finalized state
    updateBestBlock(storageClient, bestBlock);
    finalizeBlock(storageClient, finalizedEpoch, finalizedBlock);

    // Check slots that should be unavailable
    for (int i = 0; i < finalizedBlockSlot.intValue(); i++) {
      final UnsignedLong targetSlot = UnsignedLong.valueOf(i);
      assertThat(storageClient.getBlockRootBySlot(targetSlot)).isEmpty();
    }
    // Check slots that should be available
    for (int i = finalizedBlockSlot.intValue(); i <= bestBlock.getSlot().intValue(); i++) {
      final UnsignedLong targetSlot = UnsignedLong.valueOf(i);
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
    final UnsignedLong chainSplitSlot = chainBuilder.getLatestSlot();
    for (int i = 0; i < 5; i++) {
      final UnsignedLong canonicalBlockSlot = chainSplitSlot.plus(UnsignedLong.valueOf(i * 2 + 2));
      final UnsignedLong forkSlot = chainSplitSlot.plus(UnsignedLong.valueOf(i * 2 + 1));
      updateBestBlock(storageClient, chainBuilder.generateBlockAtSlot(canonicalBlockSlot));
      saveBlock(storageClient, fork.generateBlockAtSlot(forkSlot));
    }

    final Bytes32 headRoot = fork.getLatestBlockAndState().getRoot();
    for (int i = 0; i < fork.getLatestSlot().intValue(); i++) {
      final UnsignedLong targetSlot = UnsignedLong.valueOf(i);
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
   *     keep the blocks to be kept in the finalizing transaction
   * @throws StateTransitionException
   */
  private void testCommitPruningOfParallelBlocks(final boolean pruneNewBlocks)
      throws StateTransitionException {
    final UnsignedLong epoch2Slot = compute_start_slot_at_epoch(UnsignedLong.valueOf(2));

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
      assertThat(storageClient.getSignedBlockByRoot(expectedBlock.getRoot()))
          .contains(expectedBlock.getBlock());
      assertThat(storageClient.getBlockState(expectedBlock.getRoot()))
          .contains(expectedBlock.getState());
    }
    // Check pruned blocks
    for (Bytes32 prunedBlock : prunedBlocks) {
      assertThat(storageClient.getSignedBlockByRoot(prunedBlock)).isEmpty();
      assertThat(storageClient.getBlockState(prunedBlock)).isEmpty();
    }
  }

  private void importBlocksAndStates(final ChainBuilder... chainBuilders) {
    final StoreTransaction transaction = preGenesisStorageClient.startStoreTransaction();
    Stream.of(chainBuilders)
        .flatMap(ChainBuilder::streamBlocksAndStates)
        .forEach(
            blockAndState -> {
              transaction.putBlockAndState(blockAndState);
              preGenesisStorageClient
                  .getForkChoiceStrategy()
                  .orElseThrow()
                  .onBlock(blockAndState.getBlock().getMessage(), blockAndState.getState());
            });
    transaction.commit().join();
  }

  private SignedBlockAndState addNewBestBlock(RecentChainData recentChainData)
      throws StateTransitionException {
    final SignedBlockAndState nextBlock = chainBuilder.generateNextBlock();
    updateBestBlock(recentChainData, nextBlock);

    return nextBlock;
  }

  private void updateBestBlock(
      RecentChainData recentChainData, final SignedBlockAndState bestBlock) {
    saveBlock(recentChainData, bestBlock);

    storageClient.updateBestBlock(bestBlock.getRoot(), bestBlock.getSlot());
  }

  private SignedBlockAndState advanceBestBlock(final RecentChainData recentChainData)
      throws StateTransitionException {
    final SignedBlockAndState nextBlock = advanceChain(recentChainData);
    updateBestBlock(recentChainData, nextBlock);
    return nextBlock;
  }

  private void finalizeBlock(
      RecentChainData recentChainData,
      final UnsignedLong epoch,
      final SignedBlockAndState finalizedBlock) {
    saveBlock(recentChainData, finalizedBlock);

    final StoreTransaction tx = recentChainData.startStoreTransaction();
    tx.setFinalizedCheckpoint(new Checkpoint(epoch, finalizedBlock.getRoot()));
    assertThat(tx.commit()).isCompleted();
  }

  private SignedBlockAndState advanceChain(final RecentChainData recentChainData)
      throws StateTransitionException {
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
}
