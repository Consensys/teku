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
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;
import static tech.pegasys.teku.util.config.Constants.SECONDS_PER_SLOT;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.core.ForkChoiceUtil;
import tech.pegasys.teku.datastructures.blocks.NodeSlot;
import tech.pegasys.teku.logging.EventLogger;
import tech.pegasys.teku.networking.eth2.Eth2Network;
import tech.pegasys.teku.statetransition.events.attestation.BroadcastAggregatesEvent;
import tech.pegasys.teku.statetransition.events.attestation.BroadcastAttestationEvent;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.sync.SyncService;
import tech.pegasys.teku.util.time.channels.SlotEventsChannel;

public class SlotProcessor {
  private final RecentChainData recentChainData;
  private final SyncService syncService;
  private final ForkChoice forkChoice;
  private final Eth2Network p2pNetwork;
  private final SlotEventsChannel slotEventsChannelPublisher;
  private final EventBus eventBus;
  private final NodeSlot nodeSlot = new NodeSlot(ZERO);
  private final EventLogger eventLog;

  private volatile UnsignedLong onTickSlotStart;
  private volatile UnsignedLong onTickSlotAttestation;
  private volatile UnsignedLong onTickSlotAggregate;
  private final UnsignedLong oneThirdSlotSeconds = UnsignedLong.valueOf(SECONDS_PER_SLOT / 3);

  @VisibleForTesting
  SlotProcessor(
      final RecentChainData recentChainData,
      final SyncService syncService,
      final ForkChoice forkChoice,
      final Eth2Network p2pNetwork,
      final SlotEventsChannel slotEventsChannelPublisher,
      final EventBus eventBus,
      final EventLogger eventLogger) {
    this.recentChainData = recentChainData;
    this.syncService = syncService;
    this.forkChoice = forkChoice;
    this.p2pNetwork = p2pNetwork;
    this.slotEventsChannelPublisher = slotEventsChannelPublisher;
    this.eventBus = eventBus;
    this.eventLog = eventLogger;
  }

  public SlotProcessor(
      final RecentChainData recentChainData,
      final SyncService syncService,
      final ForkChoice forkChoice,
      final Eth2Network p2pNetwork,
      final SlotEventsChannel slotEventsChannelPublisher,
      final EventBus eventBus) {
    this(
        recentChainData,
        syncService,
        forkChoice,
        p2pNetwork,
        slotEventsChannelPublisher,
        eventBus,
        EventLogger.EVENT_LOG);
  }

  public NodeSlot getNodeSlot() {
    return nodeSlot;
  }

  public void setCurrentSlot(final UnsignedLong slot) {
    nodeSlot.setValue(slot);
  }

  public void onTick(final UnsignedLong currentTime) {
    final UnsignedLong genesisTime = recentChainData.getGenesisTime();
    if (currentTime.compareTo(genesisTime) < 0) {
      return;
    }
    if (isNextSlotDue(currentTime, genesisTime) && syncService.isSyncActive()) {
      processSlotWhileSyncing();
      nodeSlot.inc();
      return;
    }

    final UnsignedLong calculatedSlot = ForkChoiceUtil.getCurrentSlot(currentTime, genesisTime);
    // tolerate 1 slot difference, not more
    if (calculatedSlot.compareTo(nodeSlot.getValue().plus(ONE)) > 0) {
      eventLog.nodeSlotsMissed(nodeSlot.getValue(), calculatedSlot);
      nodeSlot.setValue(calculatedSlot);
    }

    final UnsignedLong epoch = compute_epoch_at_slot(nodeSlot.getValue());
    final UnsignedLong nodeSlotStartTime =
        ForkChoiceUtil.getSlotStartTime(nodeSlot.getValue(), genesisTime);
    if (isSlotStartDue(calculatedSlot)) {
      processSlotStart(epoch);
    }
    if (isSlotAttestationDue(calculatedSlot, currentTime, nodeSlotStartTime)) {
      processSlotAttestation(epoch);
    }
    if (isSlotAggregationDue(calculatedSlot, currentTime, nodeSlotStartTime)) {
      processSlotAggregate();
      nodeSlot.inc();
    }
  }

  private void processSlotWhileSyncing() {
    UnsignedLong slot = nodeSlot.getValue();
    this.forkChoice.processHead(slot);
    eventLog.syncEvent(slot, recentChainData.getBestSlot(), p2pNetwork.getPeerCount());
    slotEventsChannelPublisher.onSlot(slot);
  }

  boolean isNextSlotDue(final UnsignedLong currentTime, final UnsignedLong genesisTime) {
    final UnsignedLong slotStartTime =
        ForkChoiceUtil.getSlotStartTime(nodeSlot.getValue(), genesisTime);
    return currentTime.compareTo(slotStartTime) >= 0;
  }

  boolean isProcessingDueForSlot(
      final UnsignedLong calculatedSlot, final UnsignedLong currentPosition) {
    return currentPosition == null || calculatedSlot.compareTo(currentPosition) > 0;
  }

  boolean isTimeReached(final UnsignedLong currentTime, final UnsignedLong earliestTime) {
    return currentTime.compareTo(earliestTime) >= 0;
  }

  boolean isSlotStartDue(final UnsignedLong calculatedSlot) {
    return isProcessingDueForSlot(calculatedSlot, onTickSlotStart);
  }

  // Attestations are due 1/3 of the way through the slots time period
  boolean isSlotAttestationDue(
      final UnsignedLong calculatedSlot,
      final UnsignedLong currentTime,
      final UnsignedLong nodeSlotStartTime) {
    final UnsignedLong earliestTime = nodeSlotStartTime.plus(oneThirdSlotSeconds);
    return isProcessingDueForSlot(calculatedSlot, onTickSlotAttestation)
        && isTimeReached(currentTime, earliestTime);
  }

  // Aggregations are due 2/3 of the way through the slots time period
  boolean isSlotAggregationDue(
      final UnsignedLong calculatedSlot,
      final UnsignedLong currentTime,
      final UnsignedLong nodeSlotStartTime) {
    final UnsignedLong earliestTime =
        nodeSlotStartTime.plus(oneThirdSlotSeconds).plus(oneThirdSlotSeconds);
    return isProcessingDueForSlot(calculatedSlot, onTickSlotAggregate)
        && isTimeReached(currentTime, earliestTime);
  }

  private void processSlotStart(final UnsignedLong nodeEpoch) {
    onTickSlotStart = nodeSlot.getValue();
    if (nodeSlot.getValue().equals(compute_start_slot_at_epoch(nodeEpoch))) {
      eventLog.epochEvent(
          nodeEpoch,
          recentChainData.getStore().getJustifiedCheckpoint().getEpoch(),
          recentChainData.getStore().getFinalizedCheckpoint().getEpoch(),
          recentChainData.getFinalizedRoot());
    }
    slotEventsChannelPublisher.onSlot(nodeSlot.getValue());
  }

  private void processSlotAttestation(final UnsignedLong nodeEpoch) {
    onTickSlotAttestation = nodeSlot.getValue();
    Bytes32 headBlockRoot = this.forkChoice.processHead(onTickSlotAttestation);
    eventLog.slotEvent(
        nodeSlot.getValue(),
        recentChainData.getBestSlot(),
        headBlockRoot,
        nodeEpoch,
        recentChainData.getStore().getFinalizedCheckpoint().getEpoch(),
        recentChainData.getFinalizedRoot(),
        p2pNetwork.getPeerCount());
    this.eventBus.post(new BroadcastAttestationEvent(headBlockRoot, nodeSlot.getValue()));
  }

  private void processSlotAggregate() {
    onTickSlotAggregate = nodeSlot.getValue();
    this.eventBus.post(new BroadcastAggregatesEvent(nodeSlot.getValue()));
  }

  @VisibleForTesting
  void setOnTickSlotStart(final UnsignedLong slot) {
    this.onTickSlotStart = slot;
  }

  @VisibleForTesting
  void setOnTickSlotAttestation(final UnsignedLong slot) {
    this.onTickSlotAttestation = slot;
  }

  @VisibleForTesting
  void setOnTickSlotAggregate(final UnsignedLong slot) {
    this.onTickSlotAggregate = slot;
  }
}
