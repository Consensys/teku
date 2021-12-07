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

import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import com.google.common.annotations.VisibleForTesting;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.Eth2P2PNetwork;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.NodeSlot;
import tech.pegasys.teku.statetransition.EpochCachePrimer;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceNotifier;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceTrigger;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.sync.forward.ForwardSync;

public class SlotProcessor {

  private final Spec spec;
  private final RecentChainData recentChainData;
  private final ForwardSync syncService;
  private final ForkChoiceTrigger forkChoiceTrigger;
  private final ForkChoiceNotifier forkChoiceNotifier;
  private final Eth2P2PNetwork p2pNetwork;
  private final SlotEventsChannel slotEventsChannelPublisher;
  private final NodeSlot nodeSlot = new NodeSlot(ZERO);
  private final EpochCachePrimer epochCachePrimer;
  private final EventLogger eventLog;

  private volatile UInt64 onTickSlotStart;
  private volatile UInt64 onTickSlotAttestation;
  private volatile UInt64 onTickEpochPrecompute;

  @VisibleForTesting
  SlotProcessor(
      final Spec spec,
      final RecentChainData recentChainData,
      final ForwardSync syncService,
      final ForkChoiceTrigger forkChoiceTrigger,
      final ForkChoiceNotifier forkChoiceNotifier,
      final Eth2P2PNetwork p2pNetwork,
      final SlotEventsChannel slotEventsChannelPublisher,
      final EpochCachePrimer epochCachePrimer,
      final EventLogger eventLogger) {
    this.spec = spec;
    this.recentChainData = recentChainData;
    this.syncService = syncService;
    this.forkChoiceTrigger = forkChoiceTrigger;
    this.forkChoiceNotifier = forkChoiceNotifier;
    this.p2pNetwork = p2pNetwork;
    this.slotEventsChannelPublisher = slotEventsChannelPublisher;
    this.epochCachePrimer = epochCachePrimer;
    this.eventLog = eventLogger;
  }

  public SlotProcessor(
      final Spec spec,
      final RecentChainData recentChainData,
      final ForwardSync syncService,
      final ForkChoiceTrigger forkChoiceTrigger,
      final ForkChoiceNotifier forkChoiceNotifier,
      final Eth2P2PNetwork p2pNetwork,
      final SlotEventsChannel slotEventsChannelPublisher,
      final EpochCachePrimer epochCachePrimer) {
    this(
        spec,
        recentChainData,
        syncService,
        forkChoiceTrigger,
        forkChoiceNotifier,
        p2pNetwork,
        slotEventsChannelPublisher,
        epochCachePrimer,
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

    final UInt64 calculatedSlot = spec.getCurrentSlot(currentTime, genesisTime);
    // tolerate 1 slot difference, not more
    if (calculatedSlot.compareTo(nodeSlot.getValue().plus(ONE)) > 0) {
      eventLog.nodeSlotsMissed(nodeSlot.getValue(), calculatedSlot);
      nodeSlot.setValue(calculatedSlot);
    }

    final UInt64 epoch = spec.computeEpochAtSlot(nodeSlot.getValue());
    final UInt64 nodeSlotStartTime = spec.getSlotStartTime(nodeSlot.getValue(), genesisTime);
    if (isSlotStartDue(calculatedSlot)) {
      processSlotStart(epoch);
    }
    if (isSlotAttestationDue(calculatedSlot, currentTime, nodeSlotStartTime)) {
      processSlotAttestation(epoch);
      nodeSlot.inc();
    }

    if (isEpochPrecalculationDue(epoch, currentTime, genesisTime)) {
      processEpochPrecompute(epoch);
    }
  }

  private void processEpochPrecompute(final UInt64 epoch) {
    onTickEpochPrecompute = spec.computeStartSlotAtEpoch(epoch);
    epochCachePrimer.primeCacheForEpoch(epoch);
  }

  private void processSlotWhileSyncing() {
    UInt64 slot = nodeSlot.getValue();
    this.forkChoiceTrigger.onSlotStartedWhileSyncing(slot);
    eventLog.syncEvent(slot, recentChainData.getHeadSlot(), p2pNetwork.getPeerCount());
    slotEventsChannelPublisher.onSlot(slot);
  }

  boolean isNextSlotDue(final UInt64 currentTime, final UInt64 genesisTime) {
    final UInt64 slotStartTime = spec.getSlotStartTime(nodeSlot.getValue(), genesisTime);
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
    final UInt64 earliestTime = nodeSlotStartTime.plus(oneThirdSlotSeconds(calculatedSlot));
    return isProcessingDueForSlot(calculatedSlot, onTickSlotAttestation)
        && isTimeReached(currentTime, earliestTime);
  }

  // Precalculate epoch transition 2/3 of the way through the last slot of the epoch
  boolean isEpochPrecalculationDue(
      final UInt64 epoch, final UInt64 currentTime, final UInt64 genesisTime) {
    final UInt64 firstSlotOfNextEpoch = spec.computeStartSlotAtEpoch(epoch);
    if (onTickEpochPrecompute == null) {
      onTickEpochPrecompute =
          firstSlotOfNextEpoch.minusMinZero(spec.getSlotsPerEpoch(firstSlotOfNextEpoch));
      return false;
    }
    final UInt64 nextEpochStartTime = spec.getSlotStartTime(firstSlotOfNextEpoch, genesisTime);
    final UInt64 earliestTime =
        nextEpochStartTime.minusMinZero(oneThirdSlotSeconds(firstSlotOfNextEpoch));
    final boolean processingDueForSlot =
        isProcessingDueForSlot(firstSlotOfNextEpoch, onTickEpochPrecompute);
    final boolean timeReached = isTimeReached(currentTime, earliestTime);
    return processingDueForSlot && timeReached;
  }

  private int oneThirdSlotSeconds(final UInt64 slot) {
    return spec.getSecondsPerSlot(slot) / 3;
  }

  private void processSlotStart(final UInt64 nodeEpoch) {
    onTickSlotStart = nodeSlot.getValue();
    if (nodeSlot.getValue().equals(spec.computeStartSlotAtEpoch(nodeEpoch))) {
      p2pNetwork.onEpoch(nodeEpoch);
      if (!nodeEpoch.isZero()) {
        spec.getForkSchedule().reportActivatingMilestones(nodeEpoch);
      }
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
    forkChoiceTrigger.onAttestationsDueForSlot(onTickSlotAttestation);
    forkChoiceNotifier.onAttestationsDue(onTickSlotAttestation);
    recentChainData
        .getChainHead()
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
