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

import static com.google.common.primitives.UnsignedLong.ONE;
import static com.google.common.primitives.UnsignedLong.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.teku.util.config.Constants.SECONDS_PER_SLOT;
import static tech.pegasys.teku.util.config.Constants.SLOTS_PER_EPOCH;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.core.ForkChoiceUtil;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.logging.EventLogger;
import tech.pegasys.teku.networking.eth2.Eth2Network;
import tech.pegasys.teku.statetransition.events.attestation.BroadcastAggregatesEvent;
import tech.pegasys.teku.statetransition.events.attestation.BroadcastAttestationEvent;
import tech.pegasys.teku.storage.InMemoryStorageSystem;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.sync.SyncService;
import tech.pegasys.teku.util.EventSink;
import tech.pegasys.teku.util.config.StateStorageMode;
import tech.pegasys.teku.util.time.channels.SlotEventsChannel;

public class SlotProcessorTest {
  private final EventLogger eventLogger = mock(EventLogger.class);

  private final InMemoryStorageSystem storageSystem =
      InMemoryStorageSystem.createEmptyV3StorageSystem(StateStorageMode.ARCHIVE);
  private final RecentChainData recentChainData = storageSystem.recentChainData();
  private final EventBus eventBus = storageSystem.eventBus();

  private final SyncService syncService = mock(SyncService.class);
  private final Eth2Network p2pNetwork = mock(Eth2Network.class);
  private final SlotEventsChannel slotEventsChannel = mock(SlotEventsChannel.class);
  private final SlotProcessor slotProcessor =
      new SlotProcessor(
          recentChainData, syncService, p2pNetwork, slotEventsChannel, eventBus, eventLogger);

  private BeaconState beaconState;
  UnsignedLong genesisTime;
  final UnsignedLong desiredSlot = UnsignedLong.valueOf(100L);

  @BeforeEach
  public void setup() {
    final SignedBlockAndState genesis = storageSystem.chainUpdater().initializeGenesis();
    beaconState = genesis.getState();
    genesisTime = beaconState.getGenesis_time();
  }

  @Test
  public void isNextSlotDue_shouldDetectNextSlotIsNotDue() {
    slotProcessor.setCurrentSlot(desiredSlot.plus(ONE));
    final UnsignedLong currentTime = ForkChoiceUtil.getSlotStartTime(desiredSlot, genesisTime);
    assertThat(slotProcessor.isNextSlotDue(currentTime, genesisTime)).isFalse();
  }

  @Test
  public void isNextSlotDue_shouldDetectNextSlotIsDue() {
    slotProcessor.setCurrentSlot(desiredSlot);
    final UnsignedLong currentTime =
        ForkChoiceUtil.getSlotStartTime(desiredSlot.plus(ONE), genesisTime);
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
    final UnsignedLong time = UnsignedLong.valueOf(10);
    assertThat(slotProcessor.isTimeReached(time, time.plus(ONE))).isFalse();
  }

  @Test
  public void isTimeReached_shouldReturnTrueIfTimeMatches() {
    final UnsignedLong time = UnsignedLong.valueOf(10);
    assertThat(slotProcessor.isTimeReached(time, time)).isTrue();
  }

  @Test
  public void isTimeReached_shouldReturnTrueIfBeyondEarliestTime() {
    final UnsignedLong time = UnsignedLong.valueOf(10);
    assertThat(slotProcessor.isTimeReached(time, time.minus(ONE))).isTrue();
  }

  @Test
  public void onTick_shouldNotProcessPreGenesis() {
    slotProcessor.onTick(genesisTime.minus(ONE));
  }

  @Test
  public void onTick_shouldExitBeforeOtherProcessingIfSyncing() {
    ArgumentCaptor<UnsignedLong> captor = ArgumentCaptor.forClass(UnsignedLong.class);
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
    ArgumentCaptor<UnsignedLong> captor = ArgumentCaptor.forClass(UnsignedLong.class);
    when(syncService.isSyncActive()).thenReturn(false);
    when(p2pNetwork.getPeerCount()).thenReturn(1);

    slotProcessor.onTick(beaconState.getGenesis_time());
    verify(slotEventsChannel).onSlot(captor.capture());
    assertThat(captor.getValue()).isEqualTo(ZERO);
    verify(eventLogger)
        .epochEvent(
            ZERO,
            recentChainData.getStore().getJustifiedCheckpoint().getEpoch(),
            recentChainData.getStore().getFinalizedCheckpoint().getEpoch(),
            recentChainData.getFinalizedRoot());
    assertThat(slotProcessor.getNodeSlot().getValue()).isEqualTo(ZERO);
  }

  @Test
  public void onTick_shouldSkipForward() {
    final UnsignedLong slot = UnsignedLong.valueOf(SLOTS_PER_EPOCH * 100L);
    slotProcessor.setOnTickSlotAttestation(slot);
    slotProcessor.setOnTickSlotAggregate(slot);
    ArgumentCaptor<UnsignedLong> captor = ArgumentCaptor.forClass(UnsignedLong.class);
    when(syncService.isSyncActive()).thenReturn(false);
    when(p2pNetwork.getPeerCount()).thenReturn(1);

    UnsignedLong slotProcessingTime =
        beaconState.getGenesis_time().plus(slot.times(UnsignedLong.valueOf(SECONDS_PER_SLOT)));
    // slot processor starts at slot 0, but fast forwards to slot 100
    slotProcessor.onTick(slotProcessingTime);
    assertThat(slotProcessor.getNodeSlot().getValue()).isEqualTo(slot);

    // slot event to notify we're at slot 100
    verify(slotEventsChannel).onSlot(captor.capture());
    assertThat(captor.getValue()).isEqualTo(slot);

    // event logger reports slot 100
    verify(eventLogger)
        .epochEvent(
            compute_epoch_at_slot(slot),
            recentChainData.getStore().getJustifiedCheckpoint().getEpoch(),
            recentChainData.getStore().getFinalizedCheckpoint().getEpoch(),
            recentChainData.getFinalizedRoot());

    // node slots missed event to indicate that slots were missed to catch up
    verify(eventLogger).nodeSlotsMissed(ZERO, slot);
  }

  @Test
  public void onTick_shouldRunAttestationsDuringProcessing() {
    // skip the slot start
    slotProcessor.setOnTickSlotStart(slotProcessor.getNodeSlot().getValue());
    final Bytes32 bestRoot = recentChainData.getStore().getHead();
    final List<BroadcastAttestationEvent> events =
        EventSink.capture(eventBus, BroadcastAttestationEvent.class);
    when(syncService.isSyncActive()).thenReturn(false);

    when(p2pNetwork.getPeerCount()).thenReturn(1);

    slotProcessor.onTick(
        beaconState.getGenesis_time().plus(UnsignedLong.valueOf(SECONDS_PER_SLOT / 3)));
    verify(eventLogger)
        .slotEvent(
            ZERO,
            recentChainData.getBestSlot(),
            bestRoot,
            ZERO,
            recentChainData.getStore().getFinalizedCheckpoint().getEpoch(),
            recentChainData.getFinalizedRoot(),
            1);
    assertThat(events)
        .containsExactly(
            new BroadcastAttestationEvent(bestRoot, slotProcessor.getNodeSlot().getValue()));
  }

  @Test
  public void onTick_shouldRunAggregationsDuringProcessing() {
    // skip the slot start and attestations
    final UnsignedLong slot = slotProcessor.getNodeSlot().getValue();
    slotProcessor.setOnTickSlotStart(slot);
    slotProcessor.setOnTickSlotAttestation(slot);

    final List<BroadcastAggregatesEvent> events =
        EventSink.capture(eventBus, BroadcastAggregatesEvent.class);

    when(syncService.isSyncActive()).thenReturn(false);

    when(p2pNetwork.getPeerCount()).thenReturn(1);

    slotProcessor.onTick(
        beaconState.getGenesis_time().plus(UnsignedLong.valueOf(SECONDS_PER_SLOT).minus(ONE)));
    assertThat(slotProcessor.getNodeSlot().getValue()).isEqualTo(ONE);
    assertThat(events).containsExactly(new BroadcastAggregatesEvent(slot));
  }

  @Test
  void onTick_shouldExitIfUpToDate() {
    slotProcessor.setOnTickSlotStart(ZERO);
    slotProcessor.setOnTickSlotAttestation(ZERO);
    slotProcessor.setOnTickSlotAggregate(ZERO);
    when(syncService.isSyncActive()).thenReturn(false);
    slotProcessor.onTick(beaconState.getGenesis_time());

    assertThat(slotProcessor.getNodeSlot().getValue()).isEqualTo(ZERO);
  }

  @Test
  public void nodeSlot_shouldStartAtZero() {
    assertThat(slotProcessor.getNodeSlot().getValue()).isEqualTo(UnsignedLong.ZERO);
  }

  @Test
  public void setNodeSlot_shouldAlterNodeSlotValue() {
    slotProcessor.setCurrentSlot(desiredSlot);
    assertThat(slotProcessor.getNodeSlot().getValue()).isEqualTo(desiredSlot);
  }
}
