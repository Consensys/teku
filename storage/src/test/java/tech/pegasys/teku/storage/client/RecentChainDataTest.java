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
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.spec.config.SpecConfig.GENESIS_SLOT;
import static tech.pegasys.teku.storage.store.MockStoreHelper.mockChainData;
import static tech.pegasys.teku.storage.store.MockStoreHelper.mockGenesis;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tech.pegasys.teku.bls.BLSKeyGenerator;
import tech.pegasys.teku.core.ChainBuilder;
import tech.pegasys.teku.core.ChainBuilder.BlockOptions;
import tech.pegasys.teku.core.ChainProperties;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.api.TrackingChainHeadChannel.HeadEvent;
import tech.pegasys.teku.storage.api.TrackingChainHeadChannel.ReorgEvent;
import tech.pegasys.teku.storage.server.StateStorageMode;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;
import tech.pegasys.teku.storage.store.StoreConfig;
import tech.pegasys.teku.storage.store.UpdatableStore;
import tech.pegasys.teku.storage.store.UpdatableStore.StoreTransaction;

class RecentChainDataTest {
  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final SpecConfig genesisSpecConfig = spec.getGenesisSpecConfig();
  private StorageSystem storageSystem;

  private ChainBuilder chainBuilder;
  private SignedBlockAndState genesis;
  private BeaconState genesisState;
  private BeaconBlock genesisBlock;

  private RecentChainData recentChainData;

  private void initPreGenesis() {
    initPreGenesis(true);
  }

  private void initPreGenesis(final boolean updateHeadForEmptySlots) {
    storageSystem =
        InMemoryStorageSystemBuilder.create()
            .storageMode(StateStorageMode.PRUNE)
            .storeConfig(
                StoreConfig.builder().updateHeadForEmptySlots(updateHeadForEmptySlots).build())
            .build();
    chainBuilder = storageSystem.chainBuilder();
    recentChainData = storageSystem.recentChainData();

    genesis = chainBuilder.generateGenesis();
    genesisState = genesis.getState();
    genesisBlock = genesis.getBlock().getMessage();
  }

  private void initPostGenesis() {
    initPostGenesis(true);
  }

  private void initPostGenesis(final boolean updateHeadForEmptySlots) {
    initPreGenesis(updateHeadForEmptySlots);
    recentChainData.initializeFromGenesis(genesisState, UInt64.ZERO);
  }

  @Test
  public void initialize_setupInitialState() {
    initPreGenesis();
    recentChainData.initializeFromGenesis(genesisState, UInt64.ZERO);

    assertThat(recentChainData.getGenesisTime()).isEqualTo(genesisState.getGenesis_time());
    assertThat(recentChainData.getHeadSlot()).isEqualTo(GENESIS_SLOT);
    assertThat(recentChainData.getBestState()).hasValue(genesisState);
    assertThat(recentChainData.getStore()).isNotNull();
  }

  @Test
  public void initializeFromGenesis_withTimeLessThanGenesisTime() {
    initPreGenesis();
    final ChainBuilder chainBuilder = ChainBuilder.create(spec);
    final UInt64 genesisTime = UInt64.valueOf(5000);
    final SignedBlockAndState genesis = chainBuilder.generateGenesis(genesisTime, false);
    recentChainData.initializeFromGenesis(genesis.getState(), UInt64.valueOf(100));
    assertThat(recentChainData.getStore().getTime()).isEqualTo(genesisTime);
  }

  @Test
  public void initializeFromGenesis_withTimeGreaterThanGenesisTime() {
    initPreGenesis();
    final ChainBuilder chainBuilder = ChainBuilder.create(spec);
    final UInt64 genesisTime = UInt64.valueOf(5000);
    final UInt64 time = genesisTime.plus(100);
    final SignedBlockAndState genesis = chainBuilder.generateGenesis(genesisTime, false);
    recentChainData.initializeFromGenesis(genesis.getState(), time);
    assertThat(recentChainData.getStore().getTime()).isEqualTo(time);
  }

  @Test
  public void initializeFromAnchorPoint_withTimeLessThanGenesisTime() {
    initPreGenesis();
    final ChainBuilder chainBuilder = ChainBuilder.create(spec);
    final UInt64 genesisTime = UInt64.valueOf(5000);
    chainBuilder.generateGenesis(genesisTime, true);
    // Build a small chain
    chainBuilder.generateBlocksUpToSlot(10);
    final SignedBlockAndState anchor = chainBuilder.generateNextBlock();

    final AnchorPoint anchorPoint = AnchorPoint.fromInitialState(spec, anchor.getState());
    final UInt64 anchorBlockTime =
        anchorPoint.getBlockSlot().times(genesisSpecConfig.getSecondsPerSlot()).plus(genesisTime);
    recentChainData.initializeFromAnchorPoint(anchorPoint, UInt64.valueOf(100));
    assertThat(recentChainData.getStore().getTime()).isEqualTo(anchorBlockTime);
  }

  @Test
  public void initializeFromAnchorPoint_withTimeLessThanAnchorBlockTime() {
    initPreGenesis();
    final ChainBuilder chainBuilder = ChainBuilder.create(spec);
    final UInt64 genesisTime = UInt64.valueOf(5000);
    chainBuilder.generateGenesis(genesisTime, true);
    // Build a small chain
    chainBuilder.generateBlocksUpToSlot(10);
    final SignedBlockAndState anchor = chainBuilder.generateNextBlock();

    final AnchorPoint anchorPoint = AnchorPoint.fromInitialState(spec, anchor.getState());
    final UInt64 anchorBlockTime =
        anchorPoint.getBlockSlot().times(genesisSpecConfig.getSecondsPerSlot()).plus(genesisTime);
    final UInt64 time = genesisTime.plus(1);
    assertThat(time).isLessThan(anchorBlockTime);
    recentChainData.initializeFromAnchorPoint(anchorPoint, time);
    assertThat(recentChainData.getStore().getTime()).isEqualTo(anchorBlockTime);
  }

  @Test
  public void initializeFromAnchorPoint_withTimeGreaterThanAnchorBlockTime() {
    initPreGenesis();
    final ChainBuilder chainBuilder = ChainBuilder.create(spec);
    final UInt64 genesisTime = UInt64.valueOf(5000);
    chainBuilder.generateGenesis(genesisTime, true);
    // Build a small chain
    chainBuilder.generateBlocksUpToSlot(10);
    final SignedBlockAndState anchor = chainBuilder.generateNextBlock();

    final AnchorPoint anchorPoint = AnchorPoint.fromInitialState(spec, anchor.getState());
    final UInt64 anchorBlockTime =
        anchorPoint.getBlockSlot().times(genesisSpecConfig.getSecondsPerSlot()).plus(genesisTime);
    final UInt64 time = anchorBlockTime.plus(100);
    recentChainData.initializeFromAnchorPoint(anchorPoint, time);
    assertThat(recentChainData.getStore().getTime()).isEqualTo(time);
  }

  @Test
  void getGenesisData_shouldBeEmptyPreGenesis() {
    initPreGenesis();
    assertThat(recentChainData.getGenesisData()).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void updateHead_validUpdate(final boolean updateHeadForEmptySlots) {
    initPostGenesis(updateHeadForEmptySlots);

    // Ensure the current and previous target root blocks are different
    chainBuilder.generateBlockAtSlot(genesisSpecConfig.getSlotsPerEpoch() - 1);
    chainBuilder.generateBlockAtSlot(genesisSpecConfig.getSlotsPerEpoch() * 2 - 1);
    final SignedBlockAndState bestBlock = chainBuilder.generateNextBlock();
    saveBlock(recentChainData, bestBlock);

    recentChainData.updateHead(bestBlock.getRoot(), bestBlock.getSlot());
    assertThat(recentChainData.getChainHead().map(StateAndBlockSummary::getRoot))
        .contains(bestBlock.getRoot());
    assertThat(this.storageSystem.reorgEventChannel().getHeadEvents())
        .contains(
            new HeadEvent(
                bestBlock.getSlot(),
                bestBlock.getStateRoot(),
                bestBlock.getRoot(),
                true,
                spec.getBeaconStateUtil(bestBlock.getSlot())
                    .getPreviousDutyDependentRoot(bestBlock.getState()),
                spec.getBeaconStateUtil(bestBlock.getSlot())
                    .getCurrentDutyDependentRoot(bestBlock.getState())));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void updateHead_blockAndStateAreMissing(final boolean updateHeadForEmptySlots) {
    initPostGenesis(updateHeadForEmptySlots);
    final SignedBlockAndState bestBlock = chainBuilder.generateNextBlock();

    recentChainData.updateHead(bestBlock.getRoot(), bestBlock.getSlot());
    assertThat(recentChainData.getChainHead().map(StateAndBlockSummary::getRoot))
        .contains(genesis.getRoot());
  }

  @Test
  void retrieveStateInEffectAtSlot_returnEmptyWhenStoreNotSet() {
    initPreGenesis();
    final SafeFuture<Optional<BeaconState>> result =
        recentChainData.retrieveStateInEffectAtSlot(UInt64.ZERO);
    assertThatSafeFuture(result).isCompletedWithEmptyOptional();
  }

  @Test
  public void retrieveStateInEffectAtSlot_returnGenesisStateWhenItIsTheBestState() {
    initPostGenesis();
    assertThat(recentChainData.retrieveStateInEffectAtSlot(genesis.getSlot()))
        .isCompletedWithValue(Optional.of(genesisState));
  }

  @Test
  public void retrieveStateInEffectAtSlot_returnStateFromLastBlockWhenSlotsAreEmpty() {
    initPostGenesis();
    // Request block for an empty slot immediately after genesis
    final UInt64 requestedSlot = genesisBlock.getSlot().plus(ONE);
    final UInt64 bestSlot = requestedSlot.plus(ONE);

    final SignedBlockAndState bestBlock = chainBuilder.generateBlockAtSlot(bestSlot);
    updateHead(recentChainData, bestBlock);

    assertThat(recentChainData.retrieveStateInEffectAtSlot(requestedSlot))
        .isCompletedWithValue(Optional.of(genesisState));
  }

  @Test
  public void retrieveStateInEffectAtSlot_returnStateFromLastBlockWhenHeadSlotIsEmpty() {
    initPostGenesis();
    assertThat(recentChainData.retrieveStateInEffectAtSlot(ONE))
        .isCompletedWithValue(Optional.of(genesisState));
  }

  @Test
  public void retrieveStateInEffectAtSlot_returnHeadState() {
    initPostGenesis();
    final SignedBlockAndState bestBlock = addNewBestBlock(recentChainData);
    assertThat(recentChainData.retrieveStateInEffectAtSlot(bestBlock.getSlot()))
        .isCompletedWithValue(Optional.of(bestBlock.getState()));
  }

  @Test
  public void startStoreTransaction_mutateFinalizedCheckpoint() {
    initPostGenesis();
    final Checkpoint originalCheckpoint = recentChainData.getStore().getFinalizedCheckpoint();

    // Add a new finalized checkpoint
    final SignedBlockAndState newBlock = advanceChain(recentChainData);
    final UInt64 finalizedEpoch = originalCheckpoint.getEpoch().plus(ONE);
    final Checkpoint newCheckpoint = new Checkpoint(finalizedEpoch, newBlock.getRoot());
    assertThat(originalCheckpoint).isNotEqualTo(newCheckpoint); // Sanity check

    final StoreTransaction tx = recentChainData.startStoreTransaction();
    tx.setFinalizedCheckpoint(newCheckpoint);

    tx.commit().reportExceptions();

    // Check that store was updated
    final Checkpoint currentCheckpoint = recentChainData.getStore().getFinalizedCheckpoint();
    assertThat(currentCheckpoint).isEqualTo(newCheckpoint);
  }

  @Test
  public void startStoreTransaction_doNotMutateFinalizedCheckpoint() {
    initPostGenesis();
    final Checkpoint originalCheckpoint = recentChainData.getStore().getFinalizedCheckpoint();

    final StoreTransaction tx = recentChainData.startStoreTransaction();
    tx.setTime(UInt64.valueOf(11L));
    tx.commit().reportExceptions();

    final Checkpoint currentCheckpoint = recentChainData.getStore().getFinalizedCheckpoint();
    assertThat(currentCheckpoint).isEqualTo(originalCheckpoint);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void updateHead_noReorgEventWhenBestBlockFirstSet(final boolean updateHeadForEmptySlots) {
    initPreGenesis(updateHeadForEmptySlots);
    recentChainData.initializeFromGenesis(genesisState, UInt64.ZERO);

    assertThat(storageSystem.reorgEventChannel().getReorgEvents()).isEmpty();
    assertThat(getReorgCountMetric(storageSystem)).isZero();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void updateHead_noReorgEventWhenChainAdvances(final boolean updateHeadForEmptySlots) {
    initPostGenesis(updateHeadForEmptySlots);
    chainBuilder.generateBlocksUpToSlot(2);
    importBlocksAndStates(recentChainData, chainBuilder);

    final SignedBlockAndState latestBlockAndState = chainBuilder.getLatestBlockAndState();
    recentChainData.updateHead(latestBlockAndState.getRoot(), latestBlockAndState.getSlot());
    assertThat(storageSystem.reorgEventChannel().getReorgEvents()).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void updateHead_forkChoiceHeadNeverGoesDown(final boolean updateHeadForEmptySlots) {
    initPostGenesis(updateHeadForEmptySlots);
    chainBuilder.generateBlocksUpToSlot(3);
    importBlocksAndStates(recentChainData, chainBuilder);

    final SignedBlockAndState block1 = chainBuilder.getBlockAndStateAtSlot(1);
    final SignedBlockAndState block2 = chainBuilder.getBlockAndStateAtSlot(2);
    final SignedBlockAndState block3 = chainBuilder.getBlockAndStateAtSlot(3);
    // Head updated to block1 followed by empty slots up to slot 11
    recentChainData.updateHead(block1.getRoot(), UInt64.valueOf(11));
    assertThat(storageSystem.reorgEventChannel().getReorgEvents()).isEmpty();

    // Head updated to block2 but should preserve empty slots up to slot 11
    recentChainData.updateHead(block2.getRoot(), block2.getSlot());
    if (updateHeadForEmptySlots) {
      assertThat(storageSystem.reorgEventChannel().getReorgEvents()).hasSize(1);
    } else {
      assertThat(storageSystem.reorgEventChannel().getReorgEvents()).isEmpty();
    }

    recentChainData.updateHead(block3.getRoot(), UInt64.valueOf(12));
    if (updateHeadForEmptySlots) {
      // Block 3 is imported and should still trigger a reorg because its filling an empty slot
      assertThat(storageSystem.reorgEventChannel().getReorgEvents()).hasSize(2);
    } else {
      // We ignore the empty slots so no reorgs
      assertThat(storageSystem.reorgEventChannel().getReorgEvents()).isEmpty();
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void updateHead_reorgEventWhenBlockFillsEmptyHeadSlot(
      final boolean updateHeadForEmptySlots) {
    initPostGenesis(updateHeadForEmptySlots);
    final SignedBlockAndState slot1Block = chainBuilder.generateBlockAtSlot(1);
    importBlocksAndStates(recentChainData, chainBuilder);
    recentChainData.updateHead(slot1Block.getRoot(), UInt64.valueOf(2));
    assertThat(storageSystem.reorgEventChannel().getReorgEvents()).isEmpty();
    assertThat(getReorgCountMetric(storageSystem)).isZero();

    final SignedBlockAndState slot2Block = chainBuilder.generateBlockAtSlot(2);
    importBlocksAndStates(recentChainData, chainBuilder);
    recentChainData.updateHead(slot2Block.getRoot(), slot2Block.getSlot());
    final List<ReorgEvent> reorgEvents = storageSystem.reorgEventChannel().getReorgEvents();
    if (updateHeadForEmptySlots) {
      assertThat(reorgEvents)
          .containsExactly(
              new ReorgEvent(
                  slot2Block.getRoot(),
                  slot2Block.getSlot(),
                  slot2Block.getStateRoot(),
                  slot1Block.getRoot(),
                  slot1Block.getStateRoot(),
                  slot1Block.getSlot()));
      assertThat(getReorgCountMetric(storageSystem)).isEqualTo(1);
    } else {
      assertThat(reorgEvents).isEmpty();
      assertThat(getReorgCountMetric(storageSystem)).isEqualTo(0);
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void updateHead_headUpdatesWhenUpdatingWithEmptySlot(
      final boolean updateHeadForEmptySlots) {
    initPostGenesis(updateHeadForEmptySlots);
    final SignedBlockAndState slot1Block = chainBuilder.generateBlockAtSlot(1);
    importBlocksAndStates(recentChainData, chainBuilder);
    recentChainData.updateHead(slot1Block.getRoot(), slot1Block.getSlot());
    // Clear head updates from setup
    storageSystem.reorgEventChannel().getHeadEvents().clear();

    recentChainData.updateHead(slot1Block.getRoot(), slot1Block.getSlot().plus(1));
    if (updateHeadForEmptySlots) {
      assertThat(storageSystem.reorgEventChannel().getHeadEvents()).hasSize(1);
    } else {
      assertThat(storageSystem.reorgEventChannel().getHeadEvents()).isEmpty();
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void updateHead_reorgEventWhenChainSwitchesToNewBlockAtSameSlot(
      final boolean updateHeadForEmptySlots) {
    initPreGenesis(updateHeadForEmptySlots);
    final ChainBuilder chainBuilder = ChainBuilder.create(BLSKeyGenerator.generateKeyPairs(16));
    final SignedBlockAndState genesis = chainBuilder.generateGenesis();
    recentChainData.initializeFromGenesis(genesis.getState(), UInt64.ZERO);
    assertThat(storageSystem.reorgEventChannel().getReorgEvents()).isEmpty();

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
    importBlocksAndStates(recentChainData, chainBuilder, forkBuilder);

    // Update to head block of original chain.
    recentChainData.updateHead(latestBlockAndState.getRoot(), latestBlockAndState.getSlot());
    assertThat(storageSystem.reorgEventChannel().getReorgEvents()).isEmpty();

    // Switch to fork.
    recentChainData.updateHead(
        latestForkBlockAndState.getRoot(), latestForkBlockAndState.getSlot());
    // Check reorg event
    assertThat(storageSystem.reorgEventChannel().getReorgEvents())
        .containsExactly(
            new ReorgEvent(
                latestForkBlockAndState.getRoot(),
                latestForkBlockAndState.getSlot(),
                latestForkBlockAndState.getStateRoot(),
                latestBlockAndState.getRoot(),
                latestBlockAndState.getStateRoot(),
                ONE));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void updateHead_reorgEventWhenChainSwitchesToNewBlockAtLaterSlot(
      final boolean updateHeadForEmptySlots) {
    initPreGenesis(updateHeadForEmptySlots);
    final ChainBuilder chainBuilder = ChainBuilder.create(BLSKeyGenerator.generateKeyPairs(16));
    final SignedBlockAndState genesis = chainBuilder.generateGenesis();
    recentChainData.initializeFromGenesis(genesis.getState(), UInt64.ZERO);
    assertThat(storageSystem.reorgEventChannel().getReorgEvents()).isEmpty();

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
    importBlocksAndStates(recentChainData, chainBuilder, forkBuilder);

    // Update to head block of original chain.
    recentChainData.updateHead(latestBlockAndState.getRoot(), latestBlockAndState.getSlot());
    assertThat(storageSystem.reorgEventChannel().getReorgEvents()).isEmpty();

    // Switch to fork.
    recentChainData.updateHead(
        latestForkBlockAndState.getRoot(), latestForkBlockAndState.getSlot());
    // Check reorg event
    assertThat(storageSystem.reorgEventChannel().getReorgEvents())
        .containsExactly(
            new ReorgEvent(
                latestForkBlockAndState.getRoot(),
                latestForkBlockAndState.getSlot(),
                latestForkBlockAndState.getStateRoot(),
                latestBlockAndState.getRoot(),
                latestBlockAndState.getStateRoot(),
                ONE));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void updateHead_ignoreStaleUpdate(final boolean updateHeadForEmptySlots) {
    initPreGenesis(updateHeadForEmptySlots);
    final UpdatableStore store = mock(UpdatableStore.class);

    // Set up mock store with genesis data and a small chain
    List<SignedBlockAndState> chain = chainBuilder.generateBlocksUpToSlot(3);
    mockGenesis(spec, store, genesis);
    mockChainData(store, chain);

    // Set store and update best block to genesis
    assertThat(recentChainData.getChainHead()).isEmpty();
    recentChainData.setStore(store);
    recentChainData.updateHead(genesis.getRoot(), genesis.getSlot());
    assertThat(recentChainData.getBestBlockRoot()).contains(genesis.getRoot());

    // Update best block, but delay the resolution of the future
    final SignedBlockAndState chainHeadA = chain.get(0);
    final SafeFuture<Optional<StateAndBlockSummary>> chainHeadAFuture = new SafeFuture<>();
    when(store.retrieveStateAndBlockSummary(chainHeadA.getRoot())).thenReturn(chainHeadAFuture);
    recentChainData.updateHead(chainHeadA.getRoot(), chainHeadA.getSlot());
    // We should still be at genesis while we wait on the future to resolve
    assertThat(recentChainData.getBestBlockRoot()).contains(genesis.getRoot());

    // Now start another update
    final SignedBlockAndState chainHeadB = chain.get(1);
    recentChainData.updateHead(chainHeadB.getRoot(), chainHeadB.getSlot());
    assertThat(recentChainData.getBestBlockRoot()).contains(chainHeadB.getRoot());

    // Resolve the earlier update - which should be ignored since we've already moved on
    chainHeadAFuture.complete(Optional.of(chainHeadA));
    assertThat(recentChainData.getBestBlockRoot()).contains(chainHeadB.getRoot());
  }

  @Test
  public void getLatestFinalizedBlockSlot_genesis() {
    initPostGenesis();
    assertThat(recentChainData.getStore().getLatestFinalizedBlockSlot())
        .isEqualTo(genesis.getSlot());
  }

  @Test
  public void getLatestFinalizedBlockSlot_postGenesisFinalizedBlockOutsideOfEpochBoundary() {
    initPostGenesis();
    final UInt64 epoch = ONE;
    final UInt64 epochBoundarySlot = spec.computeStartSlotAtEpoch(epoch);
    final UInt64 finalizedBlockSlot = epochBoundarySlot.minus(ONE);
    final SignedBlockAndState finalizedBlock = chainBuilder.generateBlockAtSlot(finalizedBlockSlot);
    saveBlock(recentChainData, finalizedBlock);

    // Start tx to update finalized checkpoint
    final StoreTransaction tx = recentChainData.startStoreTransaction();
    // Initially finalized slot should match store
    assertThat(tx.getLatestFinalizedBlockSlot()).isEqualTo(genesis.getSlot());
    // Update checkpoint and check finalized slot accessors
    tx.setFinalizedCheckpoint(new Checkpoint(epoch, finalizedBlock.getRoot()));
    assertThat(tx.getLatestFinalizedBlockSlot()).isEqualTo(finalizedBlockSlot);
    assertThat(recentChainData.getStore().getLatestFinalizedBlockSlot())
        .isEqualTo(genesis.getSlot());
    // Commit tx
    tx.commit().reportExceptions();

    assertThat(recentChainData.getStore().getLatestFinalizedBlockSlot())
        .isEqualTo(finalizedBlockSlot);
  }

  @Test
  public void retrieveBlockAndState_withBlockAndStateAvailable() {
    initPostGenesis();
    final SignedBlockAndState block = advanceChain(recentChainData);
    assertThat(recentChainData.getStore().retrieveBlockAndState(block.getRoot()))
        .isCompletedWithValue(Optional.of(block));
  }

  @Test
  public void retrieveBlockAndState_withinTxFromUnderlyingStore() {
    initPostGenesis();
    final SignedBlockAndState block = advanceChain(recentChainData);
    final StoreTransaction tx = recentChainData.startStoreTransaction();
    assertThat(tx.retrieveBlockAndState(block.getRoot())).isCompletedWithValue(Optional.of(block));
  }

  @Test
  public void retrieveBlockAndState_withinTxFromUpdates() {
    initPostGenesis();
    final SignedBlockAndState block = chainBuilder.generateNextBlock();

    final StoreTransaction tx = recentChainData.startStoreTransaction();
    tx.putBlockAndState(block);

    assertThat(tx.retrieveBlockAndState(block.getRoot())).isCompletedWithValue(Optional.of(block));
  }

  @Test
  public void getBlockRootBySlot_forOutOfRangeSlot() {
    initPostGenesis();
    disableForkChoicePruneThreshold();
    final UInt64 historicalRoots = UInt64.valueOf(genesisSpecConfig.getSlotsPerHistoricalRoot());
    final UInt64 targetSlot = UInt64.valueOf(10);
    final UInt64 finalizedBlockSlot = targetSlot.plus(historicalRoots).plus(ONE);
    final UInt64 finalizedEpoch = spec.computeEpochAtSlot(finalizedBlockSlot).plus(ONE);

    // Add a block within the finalized range
    final SignedBlockAndState historicalBlock = chainBuilder.generateBlockAtSlot(targetSlot);
    final SignedBlockAndState finalizedBlock = chainBuilder.generateBlockAtSlot(finalizedBlockSlot);
    saveBlock(recentChainData, historicalBlock);
    finalizeBlock(recentChainData, finalizedEpoch, finalizedBlock);
    advanceBestBlock(recentChainData);

    assertThat(recentChainData.getBlockRootBySlot(targetSlot)).isEmpty();
  }

  @Test
  public void getBlockRootBySlot_forHistoricalSlotInRange() {
    initPostGenesis();
    final UInt64 historicalRoots = UInt64.valueOf(genesisSpecConfig.getSlotsPerHistoricalRoot());
    final UInt64 targetSlot = UInt64.valueOf(10);
    final UInt64 finalizedBlockSlot = targetSlot.plus(historicalRoots);
    final UInt64 finalizedEpoch = spec.computeEpochAtSlot(finalizedBlockSlot).plus(ONE);

    // Add a block within the finalized range
    final SignedBlockAndState historicalBlock = chainBuilder.generateBlockAtSlot(targetSlot);
    final SignedBlockAndState finalizedBlock = chainBuilder.generateBlockAtSlot(finalizedBlockSlot);
    saveBlock(recentChainData, historicalBlock);
    finalizeBlock(recentChainData, finalizedEpoch, finalizedBlock);
    advanceBestBlock(recentChainData);

    assertThat(recentChainData.getBlockRootBySlot(targetSlot)).contains(historicalBlock.getRoot());
  }

  @Test
  public void getBlockRootBySlot_forBestBlock() {
    initPostGenesis();
    final SignedBlockAndState bestBlock = advanceBestBlock(recentChainData);

    assertThat(recentChainData.getBlockRootBySlot(bestBlock.getSlot()))
        .contains(bestBlock.getRoot());
  }

  @Test
  public void getBlockRootBySlot_forBlockPriorToBestBlock() {
    initPostGenesis();
    final SignedBlockAndState targetBlock = advanceBestBlock(recentChainData);
    advanceBestBlock(recentChainData);

    assertThat(recentChainData.getBlockRootBySlot(targetBlock.getSlot()))
        .contains(targetBlock.getRoot());
  }

  @Test
  public void getBlockRootBySlot_forSlotAfterBestBlock() {
    initPostGenesis();
    final SignedBlockAndState bestBlock = advanceBestBlock(recentChainData);

    final UInt64 targetSlot = bestBlock.getSlot().plus(ONE);
    assertThat(recentChainData.getBlockRootBySlot(targetSlot)).contains(bestBlock.getRoot());
  }

  @Test
  public void getBlockRootBySlot_queryEntireChain() {
    initPostGenesis();
    disableForkChoicePruneThreshold();
    final UInt64 historicalRoots = UInt64.valueOf(genesisSpecConfig.getSlotsPerHistoricalRoot());

    // Build a chain that spans multiple increments of SLOTS_PER_HISTORICAL_ROOT
    final int skipBlocks = 3;
    final UInt64 skipBlocksLong = UInt64.valueOf(skipBlocks);
    final UInt64 finalizedBlockSlot = UInt64.valueOf(10).plus(historicalRoots);
    final UInt64 finalizedEpoch =
        ChainProperties.computeBestEpochFinalizableAtSlot(finalizedBlockSlot);
    final UInt64 recentSlot = spec.computeStartSlotAtEpoch(finalizedEpoch).plus(ONE);
    final UInt64 chainHeight = historicalRoots.times(2).plus(recentSlot).plus(5);
    // Build historical blocks
    final SignedBlockAndState finalizedBlock;
    while (true) {
      if (chainBuilder.getLatestSlot().plus(skipBlocksLong).compareTo(finalizedBlockSlot) >= 0) {
        // Add our target finalized block
        finalizedBlock = chainBuilder.generateBlockAtSlot(finalizedBlockSlot);
        saveBlock(recentChainData, finalizedBlock);
        break;
      }
      final SignedBlockAndState nextBlock = chainBuilder.generateNextBlock(skipBlocks);
      saveBlock(recentChainData, nextBlock);
    }
    // Build recent blocks
    SignedBlockAndState bestBlock = null;
    UInt64 nextSlot = recentSlot;
    while (chainBuilder.getLatestSlot().compareTo(chainHeight) < 0) {
      bestBlock = chainBuilder.generateBlockAtSlot(nextSlot);
      saveBlock(recentChainData, bestBlock);
      nextSlot = nextSlot.plus(skipBlocks);
    }
    // Update best block and finalized state
    updateHead(recentChainData, bestBlock);
    finalizeBlock(recentChainData, finalizedEpoch, finalizedBlock);

    // Check slots that should be unavailable
    for (int i = 0; i < finalizedBlockSlot.intValue(); i++) {
      final UInt64 targetSlot = UInt64.valueOf(i);
      assertThat(recentChainData.getBlockRootBySlot(targetSlot)).isEmpty();
    }
    // Check slots that should be available
    for (int i = finalizedBlockSlot.intValue(); i <= bestBlock.getSlot().intValue(); i++) {
      final UInt64 targetSlot = UInt64.valueOf(i);
      final SignedBlockAndState expectedResult =
          chainBuilder.getLatestBlockAndStateAtSlot(targetSlot);
      final Optional<Bytes32> result = recentChainData.getBlockRootBySlot(targetSlot);
      assertThat(result)
          .withFailMessage(
              "Expected root at slot %s to be %s (%s) but was %s",
              targetSlot, expectedResult.getRoot(), expectedResult.getSlot(), result)
          .contains(expectedResult.getRoot());
    }
  }

  @Test
  public void getBlockRootBySlotWithHeadRoot_forSlotAfterHeadRoot() {
    initPostGenesis();
    final SignedBlockAndState targetBlock = advanceBestBlock(recentChainData);
    final SignedBlockAndState bestBlock = advanceBestBlock(recentChainData);

    assertThat(recentChainData.getBlockRootBySlot(bestBlock.getSlot(), targetBlock.getRoot()))
        .contains(targetBlock.getRoot());
  }

  @Test
  public void getBlockRootBySlotWithHeadRoot_forUnknownHeadRoot() {
    initPostGenesis();
    final Bytes32 headRoot = dataStructureUtil.randomBytes32();
    final SignedBlockAndState bestBlock = advanceBestBlock(recentChainData);

    assertThat(recentChainData.getBlockRootBySlot(bestBlock.getSlot(), headRoot)).isEmpty();
  }

  @Test
  public void getBlockRootBySlotWithHeadRoot_withForkRoot() {
    initPostGenesis();
    // Build small chain
    for (int i = 0; i < 5; i++) {
      advanceChain(recentChainData);
    }

    // Split the chain
    final ChainBuilder fork = chainBuilder.fork();
    final UInt64 chainSplitSlot = chainBuilder.getLatestSlot();
    for (int i = 0; i < 5; i++) {
      final UInt64 canonicalBlockSlot = chainSplitSlot.plus(i * 2 + 2);
      final UInt64 forkSlot = chainSplitSlot.plus(i * 2 + 1);
      updateHead(recentChainData, chainBuilder.generateBlockAtSlot(canonicalBlockSlot));
      saveBlock(recentChainData, fork.generateBlockAtSlot(forkSlot));
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
      assertThat(recentChainData.getBlockRootBySlot(targetSlot, headRoot))
          .contains(expectedBlock.getRoot());
    }
  }

  @Test
  void getSlotForBlockRoot_shouldReturnEmptyForUnknownBlock() {
    initPostGenesis();

    assertThat(recentChainData.getSlotForBlockRoot(dataStructureUtil.randomBytes32())).isEmpty();
  }

  @Test
  void getSlotForBlockRoot_shouldReturnSlotForKnownBlock() {
    initPostGenesis();

    final SignedBeaconBlock block = storageSystem.chainUpdater().advanceChain().getBlock();
    assertThat(recentChainData.getSlotForBlockRoot(block.getRoot())).contains(block.getSlot());
  }

  @Test
  public void commit_pruneParallelNewBlocks() {
    initPostGenesis();
    testCommitPruningOfParallelBlocks(true);
  }

  @Test
  public void commit_pruneParallelExistingBlocks() {
    initPostGenesis();
    testCommitPruningOfParallelBlocks(false);
  }

  @Test
  public void getAncestorsOnFork() {
    initPreGenesis();
    final ChainBuilder chainBuilder = ChainBuilder.create(BLSKeyGenerator.generateKeyPairs(16));
    final SignedBlockAndState genesis = chainBuilder.generateGenesis();
    recentChainData.initializeFromGenesis(genesis.getState(), UInt64.ZERO);

    chainBuilder.generateBlockAtSlot(1);

    final List<BlockOptions> blockOptions =
        chainBuilder
            .streamValidAttestationsForBlockAtSlot(ONE)
            .map(attestation -> BlockOptions.create().addAttestation(attestation))
            .limit(2)
            .collect(toList());
    final ChainBuilder forkBuilder = chainBuilder.fork();

    final SignedBlockAndState firstBlockAndState =
        chainBuilder.generateBlockAtSlot(UInt64.valueOf(2), blockOptions.get(0));
    final SignedBlockAndState latestBlockAndState =
        chainBuilder.generateBlockAtSlot(UInt64.valueOf(3));

    final SignedBlockAndState forkFirstBlockAndState =
        forkBuilder.generateBlockAtSlot(UInt64.valueOf(2), blockOptions.get(1));
    final SignedBlockAndState latestForkBlockAndState =
        forkBuilder.generateBlockAtSlot(UInt64.valueOf(3), blockOptions.get(1));
    importBlocksAndStates(recentChainData, chainBuilder, forkBuilder);

    assertThat(recentChainData.getAncestorsOnFork(UInt64.valueOf(1), latestBlockAndState.getRoot()))
        .containsOnly(
            Map.entry(UInt64.valueOf(2), firstBlockAndState.getRoot()),
            Map.entry(UInt64.valueOf(3), latestBlockAndState.getRoot()));

    assertThat(
            recentChainData.getAncestorsOnFork(
                UInt64.valueOf(1), latestForkBlockAndState.getRoot()))
        .containsOnly(
            Map.entry(UInt64.valueOf(2), forkFirstBlockAndState.getRoot()),
            Map.entry(UInt64.valueOf(3), latestForkBlockAndState.getRoot()));
  }

  @Test
  public void getAncestorsOnFork_unknownRoot() {
    initPreGenesis();
    final ChainBuilder chainBuilder = ChainBuilder.create(BLSKeyGenerator.generateKeyPairs(16));
    final SignedBlockAndState genesis = chainBuilder.generateGenesis();
    recentChainData.initializeFromGenesis(genesis.getState(), UInt64.ZERO);
    assertThat(recentChainData.getAncestorsOnFork(UInt64.valueOf(1), Bytes32.ZERO)).isEmpty();
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
    final UInt64 epoch2Slot = spec.computeStartSlotAtEpoch(UInt64.valueOf(2));

    // Create a fork by skipping the next slot on the fork chain
    ChainBuilder fork = chainBuilder.fork();
    // Generate the next 2 blocks on the primary chain
    final SignedBlockAndState firstCanonicalBlock = chainBuilder.generateNextBlock();
    saveBlock(recentChainData, firstCanonicalBlock);
    saveBlock(recentChainData, chainBuilder.generateNextBlock());
    // Skip a block and then generate the next block on the fork chain
    final SignedBlockAndState firstForkBlock = fork.generateNextBlock(1);
    saveBlock(recentChainData, firstForkBlock);

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
          .forEach(b -> saveBlock(recentChainData, b));
      fork.streamBlocksAndStates(firstForkBlock.getSlot()).forEach(newBlocks::add);
    } else {
      // Save fork blocks now, put canonical blocks in the transaction
      chainBuilder.streamBlocksAndStates(firstCanonicalBlock.getSlot()).forEach(newBlocks::add);
      fork.streamBlocksAndStates(firstForkBlock.getSlot())
          .forEach(b -> saveBlock(recentChainData, b));
    }

    // Add blocks and finalize epoch 1, so that blocks will be pruned
    final StoreTransaction tx = recentChainData.startStoreTransaction();
    final Checkpoint finalizedCheckpoint = chainBuilder.getCurrentCheckpointForEpoch(1);
    tx.setFinalizedCheckpoint(finalizedCheckpoint);
    newBlocks.forEach(tx::putBlockAndState);
    tx.commit().reportExceptions();

    // Check that only recent, canonical blocks at or after the latest finalized block are left in
    // the store
    final List<SignedBlockAndState> expectedBlocks =
        chainBuilder
            .streamBlocksAndStates(finalizedCheckpoint.getEpochStartSlot(spec))
            .collect(Collectors.toList());
    final Set<Bytes32> blockRoots =
        expectedBlocks.stream().map(SignedBlockAndState::getRoot).collect(Collectors.toSet());
    // Collect blocks that should be pruned
    final Set<Bytes32> prunedBlocks =
        fork.streamBlocksAndStates(firstForkBlock.getSlot())
            .map(SignedBlockAndState::getRoot)
            .collect(Collectors.toSet());

    // Check expected blocks
    assertThat(recentChainData.getStore().getOrderedBlockRoots())
        .containsExactlyInAnyOrderElementsOf(blockRoots);
    for (SignedBlockAndState expectedBlock : expectedBlocks) {
      assertThat(recentChainData.retrieveSignedBlockByRoot(expectedBlock.getRoot()))
          .isCompletedWithValue(Optional.of(expectedBlock.getBlock()));
      assertThat(recentChainData.retrieveBlockState(expectedBlock.getRoot()))
          .isCompletedWithValue(Optional.of(expectedBlock.getState()));
    }
    // Check pruned blocks
    for (Bytes32 prunedBlock : prunedBlocks) {
      assertThatSafeFuture(recentChainData.retrieveSignedBlockByRoot(prunedBlock))
          .isCompletedWithEmptyOptional();
      assertThat(recentChainData.retrieveBlockState(prunedBlock))
          .isCompletedWithValue(Optional.empty());
    }
  }

  private void importBlocksAndStates(
      final RecentChainData client, final ChainBuilder... chainBuilders) {
    final StoreTransaction transaction = client.startStoreTransaction();
    Stream.of(chainBuilders)
        .flatMap(ChainBuilder::streamBlocksAndStates)
        .forEach(transaction::putBlockAndState);
    transaction.commit().join();
  }

  private SignedBlockAndState addNewBestBlock(RecentChainData recentChainData) {
    final SignedBlockAndState nextBlock = chainBuilder.generateNextBlock();
    updateHead(recentChainData, nextBlock);

    return nextBlock;
  }

  private void updateHead(RecentChainData recentChainData, final SignedBlockAndState bestBlock) {
    saveBlock(recentChainData, bestBlock);

    this.recentChainData.updateHead(bestBlock.getRoot(), bestBlock.getSlot());
  }

  private SignedBlockAndState advanceBestBlock(final RecentChainData recentChainData) {
    final SignedBlockAndState nextBlock = advanceChain(recentChainData);
    updateHead(recentChainData, nextBlock);
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
  }

  private void disableForkChoicePruneThreshold() {
    recentChainData.getForkChoiceStrategy().orElseThrow().setPruneThreshold(0);
  }

  private long getReorgCountMetric(final StorageSystem storageSystem) {
    return storageSystem
        .getMetricsSystem()
        .getCounter(TekuMetricCategory.BEACON, "reorgs_total")
        .getValue();
  }
}
