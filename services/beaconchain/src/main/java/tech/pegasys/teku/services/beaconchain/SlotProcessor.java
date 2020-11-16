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

import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.util.config.Constants.SECONDS_PER_SLOT;

import com.google.common.annotations.VisibleForTesting;
import tech.pegasys.teku.core.ForkChoiceUtil;
import tech.pegasys.teku.datastructures.blocks.NodeSlot;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.Eth2Network;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.sync.forward.ForwardSync;
import tech.pegasys.teku.util.time.channels.SlotEventsChannel;

public class SlotProcessor {
  private final RecentChainData recentChainData;
  private final ForwardSync syncService;
  private final ForkChoice forkChoice;
  private final Eth2Network p2pNetwork;
  private final SlotEventsChannel slotEventsChannelPublisher;
  private final NodeSlot nodeSlot = new NodeSlot(ZERO);
  private final EventLogger eventLog;

  private volatile UInt64 onTickSlotStart;
  private volatile UInt64 onTickSlotAttestation;
  private final UInt64 oneThirdSlotSeconds = UInt64.valueOf(SECONDS_PER_SLOT / 3);

  @VisibleForTesting
  SlotProcessor(
      final RecentChainData recentChainData,
      final ForwardSync syncService,
      final ForkChoice forkChoice,
      final Eth2Network p2pNetwork,
      final SlotEventsChannel slotEventsChannelPublisher,
      final EventLogger eventLogger) {
    this.recentChainData = recentChainData;
    this.syncService = syncService;
    this.forkChoice = forkChoice;
    this.p2pNetwork = p2pNetwork;
    this.slotEventsChannelPublisher = slotEventsChannelPublisher;
    this.eventLog = eventLogger;
  }

  public SlotProcessor(
      final RecentChainData recentChainData,
      final ForwardSync syncService,
      final ForkChoice forkChoice,
      final Eth2Network p2pNetwork,
      final SlotEventsChannel slotEventsChannelPublisher) {
    this(
        recentChainData,
        syncService,
        forkChoice,
        p2pNetwork,
        slotEventsChannelPublisher,
        EventLogger.EVENT_LOG);
  }

  public NodeSlot getNodeSlot() {
    return nodeSlot;
  }

  public void setCurrentSlot(final UInt64 slot) {
    slotEventsChannelPublisher.onSlot(slot);
    nodeSlot.setValue(slot);
  }

  public void onTick(final UInt64 currentTime) {
    final UInt64 genesisTime = recentChainData.getGenesisTime();
    if (currentTime.compareTo(genesisTime) < 0) {
      return;
    }
    if (isNextSlotDue(currentTime, genesisTime) && syncService.isSyncActive()) {
      processSlotWhileSyncing();
      nodeSlot.inc();
      return;
    }

    final UInt64 calculatedSlot = ForkChoiceUtil.getCurrentSlot(currentTime, genesisTime);
    // tolerate 1 slot difference, not more
    if (calculatedSlot.compareTo(nodeSlot.getValue().plus(ONE)) > 0) {
      eventLog.nodeSlotsMissed(nodeSlot.getValue(), calculatedSlot);
      nodeSlot.setValue(calculatedSlot);
    }

    final UInt64 epoch = compute_epoch_at_slot(nodeSlot.getValue());
    final UInt64 nodeSlotStartTime =
        ForkChoiceUtil.getSlotStartTime(nodeSlot.getValue(), genesisTime);
    if (isSlotStartDue(calculatedSlot)) {
      processSlotStart(epoch);
    }
    if (isSlotAttestationDue(calculatedSlot, currentTime, nodeSlotStartTime)) {
      processSlotAttestation(epoch);
      nodeSlot.inc();
    }
  }

  private void processSlotWhileSyncing() {
    UInt64 slot = nodeSlot.getValue();
    this.forkChoice.processHead(slot);
    eventLog.syncEvent(slot, recentChainData.getHeadSlot(), p2pNetwork.getPeerCount());
    slotEventsChannelPublisher.onSlot(slot);
  }

  boolean isNextSlotDue(final UInt64 currentTime, final UInt64 genesisTime) {
    final UInt64 slotStartTime = ForkChoiceUtil.getSlotStartTime(nodeSlot.getValue(), genesisTime);
    return currentTime.compareTo(slotStartTime) >= 0;
  }

  boolean isProcessingDueForSlot(final UInt64 calculatedSlot, final UInt64 currentPosition) {
    return currentPosition == null || calculatedSlot.compareTo(currentPosition) > 0;
  }

  boolean isTimeReached(final UInt64 currentTime, final UInt64 earliestTime) {
    return currentTime.compareTo(earliestTime) >= 0;
  }

  boolean isSlotStartDue(final UInt64 calculatedSlot) {
    return isProcessingDueForSlot(calculatedSlot, onTickSlotStart);
  }

  // Attestations are due 1/3 of the way through the slots time period
  boolean isSlotAttestationDue(
      final UInt64 calculatedSlot, final UInt64 currentTime, final UInt64 nodeSlotStartTime) {
    final UInt64 earliestTime = nodeSlotStartTime.plus(oneThirdSlotSeconds);
    return isProcessingDueForSlot(calculatedSlot, onTickSlotAttestation)
        && isTimeReached(currentTime, earliestTime);
  }

  private void processSlotStart(final UInt64 nodeEpoch) {
    onTickSlotStart = nodeSlot.getValue();
    if (nodeSlot.getValue().equals(compute_start_slot_at_epoch(nodeEpoch))) {
      recentChainData
          .getFinalizedCheckpoint()
          .ifPresent(
              finalizedCheckpoint ->
                  eventLog.epochEvent(
                      nodeEpoch,
                      recentChainData.getStore().getJustifiedCheckpoint().getEpoch(),
                      finalizedCheckpoint.getEpoch(),
                      finalizedCheckpoint.getRoot()));
    }
    slotEventsChannelPublisher.onSlot(nodeSlot.getValue());
  }

  private void processSlotAttestation(final UInt64 nodeEpoch) {
    onTickSlotAttestation = nodeSlot.getValue();
    this.forkChoice.processHead(onTickSlotAttestation);
    recentChainData
        .getHeadBlock()
        .ifPresent(
            (head) ->
                recentChainData
                    .getFinalizedCheckpoint()
                    .ifPresent(
                        finalizedCheckpoint ->
                            eventLog.slotEvent(
                                nodeSlot.getValue(),
                                head.getSlot(),
                                head.getRoot(),
                                nodeEpoch,
                                finalizedCheckpoint.getEpoch(),
                                finalizedCheckpoint.getRoot(),
                                p2pNetwork.getPeerCount())));
  }

  @VisibleForTesting
  void setOnTickSlotStart(final UInt64 slot) {
    this.onTickSlotStart = slot;
  }

  @VisibleForTesting
  void setOnTickSlotAttestation(final UInt64 slot) {
    this.onTickSlotAttestation = slot;
  }
}
