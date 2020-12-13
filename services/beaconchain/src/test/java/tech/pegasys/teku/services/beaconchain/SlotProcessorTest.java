/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.services.beaconchain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atMostOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.core.ForkChoiceUtil.getSlotStartTime;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.util.config.Constants.SECONDS_PER_SLOT;
import static tech.pegasys.teku.util.config.Constants.SLOTS_PER_EPOCH;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.Eth2Network;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;
import tech.pegasys.teku.sync.forward.ForwardSync;
import tech.pegasys.teku.util.config.StateStorageMode;
import tech.pegasys.teku.util.time.channels.SlotEventsChannel;

public class SlotProcessorTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();

  private final BeaconState beaconState = dataStructureUtil.randomBeaconState(ZERO);
  private final EventLogger eventLogger = mock(EventLogger.class);

  private final StorageSystem storageSystem =
      InMemoryStorageSystemBuilder.buildDefault(StateStorageMode.ARCHIVE);
  private final RecentChainData recentChainData = storageSystem.recentChainData();

  private final ForwardSync syncService = mock(ForwardSync.class);
  private final ForkChoice forkChoice = mock(ForkChoice.class);
  private final Eth2Network p2pNetwork = mock(Eth2Network.class);
  private final SlotEventsChannel slotEventsChannel = mock(SlotEventsChannel.class);
  private final SlotProcessor slotProcessor =
      new SlotProcessor(
          recentChainData, syncService, forkChoice, p2pNetwork, slotEventsChannel, eventLogger);
  private final UInt64 genesisTime = beaconState.getGenesis_time();
  private final UInt64 desiredSlot = UInt64.valueOf(100L);

  @BeforeEach
  public void setup() {
    recentChainData.initializeFromGenesis(beaconState);
  }

  @Test
  public void isNextSlotDue_shouldDetectNextSlotIsNotDue() {
    slotProcessor.setCurrentSlot(desiredSlot.plus(ONE));
    final UInt64 currentTime = getSlotStartTime(desiredSlot, genesisTime);
    assertThat(slotProcessor.isNextSlotDue(currentTime, genesisTime)).isFalse();
  }

  @Test
  public void isNextSlotDue_shouldDetectNextSlotIsDue() {
    slotProcessor.setCurrentSlot(desiredSlot);
    final UInt64 currentTime = getSlotStartTime(desiredSlot.plus(ONE), genesisTime);
    assertThat(slotProcessor.isNextSlotDue(currentTime, genesisTime)).isTrue();
  }

  @Test
  public void isProcessingDueForSlot_shouldHandleNull() {
    slotProcessor.setCurrentSlot(desiredSlot);
    assertThat(slotProcessor.isProcessingDueForSlot(desiredSlot, null)).isTrue();
  }

  @Test
  public void isProcessingDueForSlot_shouldReturnFalseIfPositionMatches() {
    slotProcessor.setCurrentSlot(desiredSlot);
    assertThat(slotProcessor.isProcessingDueForSlot(desiredSlot, desiredSlot)).isFalse();
  }

  @Test
  public void isProcessingDueForSlot_shouldReturnTrueIfPositionIsBehind() {
    slotProcessor.setCurrentSlot(desiredSlot);
    assertThat(slotProcessor.isProcessingDueForSlot(desiredSlot, desiredSlot.minus(ONE))).isTrue();
  }

  @Test
  public void isProcessingDueForSlot_shouldReturnFalseIfPositionIsAhead() {
    slotProcessor.setCurrentSlot(desiredSlot);
    assertThat(slotProcessor.isProcessingDueForSlot(desiredSlot, desiredSlot.plus(ONE))).isFalse();
  }

  @Test
  public void isTimeReached_shouldReturnFalseIfTimeNotReached() {
    assertThat(slotProcessor.isTimeReached(genesisTime, genesisTime.plus(ONE))).isFalse();
  }

  @Test
  public void isTimeReached_shouldReturnTrueIfTimeMatches() {
    assertThat(slotProcessor.isTimeReached(genesisTime, genesisTime)).isTrue();
  }

  @Test
  public void isTimeReached_shouldReturnTrueIfBeyondEarliestTime() {
    assertThat(slotProcessor.isTimeReached(genesisTime, genesisTime.minus(ONE))).isTrue();
  }

  @Test
  public void onTick_shouldNotProcessPreGenesis() {
    slotProcessor.onTick(genesisTime.minus(ONE));
  }

  @Test
  public void onTick_shouldExitBeforeOtherProcessingIfSyncing() {
    ArgumentCaptor<UInt64> captor = ArgumentCaptor.forClass(UInt64.class);
    when(syncService.isSyncActive()).thenReturn(true);
    when(p2pNetwork.getPeerCount()).thenReturn(1);

    slotProcessor.onTick(beaconState.getGenesis_time());
    assertThat(slotProcessor.getNodeSlot().getValue()).isEqualTo(ONE);

    verify(slotEventsChannel).onSlot(captor.capture());
    assertThat(captor.getValue()).isEqualTo(ZERO);

    verify(syncService).isSyncActive();
    verify(eventLogger).syncEvent(ZERO, ZERO, 1);
  }

  @Test
  public void onTick_shouldRunStartSlotAtGenesis() {
    ArgumentCaptor<UInt64> captor = ArgumentCaptor.forClass(UInt64.class);
    when(syncService.isSyncActive()).thenReturn(false);
    when(p2pNetwork.getPeerCount()).thenReturn(1);

    slotProcessor.onTick(beaconState.getGenesis_time());
    verify(slotEventsChannel).onSlot(captor.capture());
    assertThat(captor.getValue()).isEqualTo(ZERO);
    final Checkpoint finalizedCheckpoint = recentChainData.getStore().getFinalizedCheckpoint();
    verify(eventLogger)
        .epochEvent(
            ZERO,
            recentChainData.getStore().getJustifiedCheckpoint().getEpoch(),
            finalizedCheckpoint.getEpoch(),
            finalizedCheckpoint.getRoot());
    assertThat(slotProcessor.getNodeSlot().getValue()).isEqualTo(ZERO);
  }

  @Test
  public void onTick_shouldSkipForward() {
    final UInt64 slot = UInt64.valueOf(SLOTS_PER_EPOCH * 100L);
    slotProcessor.setOnTickSlotAttestation(slot);
    ArgumentCaptor<UInt64> captor = ArgumentCaptor.forClass(UInt64.class);
    when(syncService.isSyncActive()).thenReturn(false);
    when(p2pNetwork.getPeerCount()).thenReturn(1);

    UInt64 slotProcessingTime = beaconState.getGenesis_time().plus(slot.times(SECONDS_PER_SLOT));
    // slot processor starts at slot 0, but fast forwards to slot 100
    slotProcessor.onTick(slotProcessingTime);
    assertThat(slotProcessor.getNodeSlot().getValue()).isEqualTo(slot);

    // slot event to notify we're at slot 100
    verify(slotEventsChannel).onSlot(captor.capture());
    assertThat(captor.getValue()).isEqualTo(slot);

    // event logger reports slot 100
    final Checkpoint finalizedCheckpoint = recentChainData.getStore().getFinalizedCheckpoint();
    verify(eventLogger)
        .epochEvent(
            compute_epoch_at_slot(slot),
            recentChainData.getStore().getJustifiedCheckpoint().getEpoch(),
            finalizedCheckpoint.getEpoch(),
            finalizedCheckpoint.getRoot());

    // node slots missed event to indicate that slots were missed to catch up
    verify(eventLogger).nodeSlotsMissed(ZERO, slot);
  }

  @Test
  public void onTick_shouldRunAttestationsDuringProcessing() {
    // skip the slot start
    final UInt64 slot = slotProcessor.getNodeSlot().getValue();
    slotProcessor.setOnTickSlotStart(slot);
    when(syncService.isSyncActive()).thenReturn(false);

    when(p2pNetwork.getPeerCount()).thenReturn(1);

    slotProcessor.onTick(beaconState.getGenesis_time().plus(SECONDS_PER_SLOT / 3));
    final Checkpoint finalizedCheckpoint = recentChainData.getStore().getFinalizedCheckpoint();
    verify(eventLogger)
        .slotEvent(
            ZERO,
            recentChainData.getHeadSlot(),
            recentChainData.getBestBlockRoot().orElseThrow(),
            ZERO,
            finalizedCheckpoint.getEpoch(),
            finalizedCheckpoint.getRoot(),
            1);
    verify(forkChoice).processHead(slot);
  }

  @Test
  void onTick_shouldExitIfUpToDate() {
    slotProcessor.setOnTickSlotStart(ZERO);
    slotProcessor.setOnTickSlotAttestation(ZERO);
    when(syncService.isSyncActive()).thenReturn(false);
    slotProcessor.onTick(beaconState.getGenesis_time());

    assertThat(slotProcessor.getNodeSlot().getValue()).isEqualTo(ZERO);
  }

  @Test
  public void nodeSlot_shouldStartAtZero() {
    assertThat(slotProcessor.getNodeSlot().getValue()).isEqualTo(UInt64.ZERO);
  }

  @Test
  public void setNodeSlot_shouldAlterNodeSlotValue() {
    slotProcessor.setCurrentSlot(desiredSlot);
    assertThat(slotProcessor.getNodeSlot().getValue()).isEqualTo(desiredSlot);
  }

  @Test
  void shouldProgressThroughMultipleSlots() {
    when(syncService.isSyncActive()).thenReturn(false);
    when(p2pNetwork.getPeerCount()).thenReturn(1);

    // Slot 0 start
    slotProcessor.onTick(beaconState.getGenesis_time());
    verify(slotEventsChannel).onSlot(ZERO);
    // Attestation due
    slotProcessor.onTick(beaconState.getGenesis_time().plus(SECONDS_PER_SLOT / 3));
    verify(forkChoice).processHead(ZERO);

    // Slot 2 start
    final UInt64 slot1Start = beaconState.getGenesis_time().plus(SECONDS_PER_SLOT);
    slotProcessor.onTick(slot1Start);
    verify(slotEventsChannel).onSlot(ONE);
    // Attestation due
    slotProcessor.onTick(slot1Start.plus(SECONDS_PER_SLOT / 3));
    verify(forkChoice).processHead(ONE);
  }

  @Test
  void shouldPrecomputeEpochTransitionJustBeforeFirstSlotOfNextEpoch() {
    final RecentChainData recentChainData = mock(RecentChainData.class);
    when(recentChainData.getGenesisTime()).thenReturn(genesisTime);
    final Optional<SignedBeaconBlock> headBlock = storageSystem.recentChainData().getHeadBlock();
    when(recentChainData.getHeadBlock()).thenReturn(headBlock);
    when(recentChainData.retrieveStateAtSlot(any())).thenReturn(new SafeFuture<>());

    final SlotProcessor slotProcessor =
        new SlotProcessor(
            recentChainData, syncService, forkChoice, p2pNetwork, slotEventsChannel, eventLogger);
    slotProcessor.setCurrentSlot(UInt64.valueOf(6));
    final UInt64 slot6StartTime = getSlotStartTime(UInt64.valueOf(6), genesisTime);
    final UInt64 slot7StartTime = getSlotStartTime(UInt64.valueOf(7), genesisTime);

    // Progress through to end of initial epoch
    slotProcessor.onTick(slot6StartTime);
    slotProcessor.onTick(slot6StartTime.plus(SECONDS_PER_SLOT / 3));
    slotProcessor.onTick(slot6StartTime.plus(SECONDS_PER_SLOT / 3 * 2));
    slotProcessor.onTick(slot7StartTime);
    slotProcessor.onTick(slot7StartTime.plus(SECONDS_PER_SLOT / 3));

    // Shouldn't have precomputed epoch transition yet.
    verify(recentChainData, never()).retrieveStateAtSlot(any());

    // But just before the last slot of the epoch ends, we should precompute the next epoch
    slotProcessor.onTick(slot7StartTime.plus(SECONDS_PER_SLOT / 3 * 2));
    verify(recentChainData)
        .retrieveStateAtSlot(
            new SlotAndBlockRoot(
                compute_start_slot_at_epoch(ONE), headBlock.orElseThrow().getRoot()));

    // Should not repeat computation
    slotProcessor.onTick(slot7StartTime.plus(SECONDS_PER_SLOT / 3 * 2 + 1));
    slotProcessor.onTick(slot7StartTime.plus(SECONDS_PER_SLOT / 3 * 2 + 2));
    verify(recentChainData, atMostOnce()).retrieveStateAtSlot(any());
  }
}
