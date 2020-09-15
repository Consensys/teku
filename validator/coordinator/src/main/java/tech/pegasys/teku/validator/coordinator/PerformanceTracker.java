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

package tech.pegasys.teku.validator.coordinator;

import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_block_root;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_block_root_at_slot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.util.time.channels.SlotEventsChannel;

public class PerformanceTracker implements SlotEventsChannel {
  private static final Logger LOG = LogManager.getLogger();

  private final Map<UInt64, List<SignedBeaconBlock>> sentBlocksByEpoch = new HashMap<>();
  private final Map<UInt64, List<Attestation>> sentAttestationsByEpoch = new HashMap<>();

  private final RecentChainData recentChainData;

  public PerformanceTracker(RecentChainData recentChainData) {
    this.recentChainData = recentChainData;
  }

  @Override
  public void onSlot(UInt64 slot) {
    UInt64 currentEpoch = compute_epoch_at_slot(slot);
    if (!compute_start_slot_at_epoch(currentEpoch).equals(slot)) {
      return;
    }

    // Output performance information for the past epoch
    outputPerformanceInformation(currentEpoch.decrement());
  }

  private void outputPerformanceInformation(final UInt64 epoch) {
    outputBlockPerformanceInfo(epoch);
    outputAttestationPerformanceInfo(epoch);
  }

  private void outputBlockPerformanceInfo(UInt64 epoch) {
    List<BeaconBlock> blockInEpoch = getBlocksInEpochs(epoch, epoch.increment());
    List<SignedBeaconBlock> sentBlocks = sentBlocksByEpoch.get(epoch);
    long numberOfSentBlocks = sentBlocks.size();
    long numberOfIncludedSentBlocks =
        sentBlocks.stream()
            .map(SignedBeaconBlock::getMessage)
            .filter(blockInEpoch::contains)
            .count();

    LOG.info(
        "Number of sent blocks: {} | Number of sent blocks included on chain: {}.",
        numberOfSentBlocks,
        numberOfIncludedSentBlocks);
    LOG.info(
        "Block inclusion at: {}%", getPercentage(numberOfIncludedSentBlocks, numberOfSentBlocks));
  }

  private void outputAttestationPerformanceInfo(final UInt64 epoch) {
    // Attestations can be included in either the epoch they were produced in or in the following
    // epoch.
    // Thus, the most recent attestation performance we can evaluate is not the last epoch, but the
    // epoch before.
    UInt64 previousEpoch = epoch.decrement();
    UInt64 nextEpoch = epoch.increment();

    // Get included attestations for the given epochs in a map from slot to included attestations in
    // block list
    Map<UInt64, List<Attestation>> attestations =
        getAttestationsIncludedInEpochs(previousEpoch, nextEpoch);

    UInt64 previousEpochStartSlot = compute_start_slot_at_epoch(previousEpoch);
    UInt64 nextEpochStartSlot = compute_start_slot_at_epoch(nextEpoch);
    BeaconState state = recentChainData.getBestState().orElseThrow();

    int correctTargetCount = 0;
    int correctHeadBlockCount = 0;

    // Get the sent attestations
    List<Attestation> sentAttestations = sentAttestationsByEpoch.get(previousEpoch);

    List<Integer> inclusionDistances = new ArrayList<>();
    for (Attestation sentAttestation : sentAttestations) {
      // Check if the sent attestation is included in any of the block attestation lists in the last
      // 2 epochs.
      UInt64 attestationSlot = sentAttestation.getData().getSlot();
      for (UInt64 currSlot = previousEpochStartSlot;
          currSlot.isLessThan(nextEpochStartSlot);
          currSlot = currSlot.increment()) {
        if (attestations.containsKey(currSlot)) {
          if (checkIfAttestationIsIncludedInList(sentAttestation, attestations.get(currSlot))) {
            inclusionDistances.add(currSlot.minus(attestationSlot).intValue());
          }
        }
      }

      // Check if the attestation had correct target
      Bytes32 attestationTargetRoot = sentAttestation.getData().getTarget().getRoot();
      if (attestationTargetRoot.equals(get_block_root(state, previousEpoch))) {
        correctTargetCount++;

        // Check if the attestation had correct head block root
        Bytes32 attestationHeadBlockRoot = sentAttestation.getData().getBeacon_block_root();
        if (attestationHeadBlockRoot.equals(get_block_root_at_slot(state, attestationSlot))) {
          correctHeadBlockCount++;
        }
      }
    }

    IntSummaryStatistics inclusionDistancesStatistics =
        inclusionDistances.stream().collect(Collectors.summarizingInt(Integer::intValue));
    long numberOfSentAttestations = sentAttestations.size();
    long numberOfIncludedAttestations = inclusionDistancesStatistics.getCount();

    LOG.info(
        " ===== Attestation Performance Information ===== \n"
            + " - Number of sent attestations: {}\n"
            + " - Number of sent attestations included on chain: {}\n"
            + " - Inclusion at: {}%\n"
            + " - Inclusion distances: average: {}, min: {}, max: {}\n"
            + " - %age with correct target: {}\n"
            + " - %age with correct head block root: {}\n",
        numberOfSentAttestations,
        numberOfIncludedAttestations,
        getPercentage(numberOfIncludedAttestations, numberOfSentAttestations),
        inclusionDistancesStatistics.getAverage(),
        inclusionDistancesStatistics.getMin(),
        inclusionDistancesStatistics.getMax(),
        getPercentage(correctTargetCount, numberOfSentAttestations),
        getPercentage(correctHeadBlockCount, numberOfSentAttestations));
  }

  private boolean checkIfAttestationIsIncludedInList(
      Attestation sentAttestation, List<Attestation> aggregateAttestations) {
    for (Attestation aggregateAttestation : aggregateAttestations) {
      if (checkIfAttestationIsIncludedIn(sentAttestation, aggregateAttestation)) {
        return true;
      }
    }
    return false;
  }

  private boolean checkIfAttestationIsIncludedIn(
      Attestation sentAttestation, Attestation aggregateAttestation) {
    return sentAttestation.getData().equals(aggregateAttestation.getData())
        && aggregateAttestation
            .getAggregation_bits()
            .isSuperSetOf(sentAttestation.getAggregation_bits());
  }

  private List<BeaconBlock> getBlocksInEpochs(
      UInt64 startEpochInclusive, UInt64 endEpochExclusive) {
    UInt64 epochStartSlot = compute_start_slot_at_epoch(startEpochInclusive);
    UInt64 endEpochStartSlot = compute_start_slot_at_epoch(endEpochExclusive);

    List<Bytes32> blockRootsInEpoch = new ArrayList<>();
    for (UInt64 currSlot = epochStartSlot;
        currSlot.isLessThan(endEpochStartSlot);
        currSlot = currSlot.increment()) {
      recentChainData.getBlockRootBySlot(currSlot).ifPresent(blockRootsInEpoch::add);
    }

    return blockRootsInEpoch.stream()
        .map(recentChainData::retrieveBlockByRoot)
        .map(SafeFuture::join)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(Collectors.toList());
  }

  private Map<UInt64, List<Attestation>> getAttestationsIncludedInEpochs(
      UInt64 startEpochInclusive, UInt64 endEpochExclusive) {
    return getBlocksInEpochs(startEpochInclusive, endEpochExclusive).stream()
        .collect(
            Collectors.toMap(
                BeaconBlock::getSlot, block -> block.getBody().getAttestations().asList()));
  }

  public void saveSentAttestation(Attestation attestation) {
    UInt64 epoch = compute_epoch_at_slot(attestation.getData().getSlot());
    List<Attestation> attestationsInEpoch =
        sentAttestationsByEpoch.computeIfAbsent(epoch, __ -> new ArrayList<>());
    attestationsInEpoch.add(attestation);
  }

  public void saveSentBlock(SignedBeaconBlock block) {
    UInt64 epoch = compute_epoch_at_slot(block.getSlot());
    List<SignedBeaconBlock> blocksInEpoch =
        sentBlocksByEpoch.computeIfAbsent(epoch, __ -> new ArrayList<>());
    blocksInEpoch.add(block);
  }

  private static long getPercentage(final long numerator, final long denominator) {
    return (long) (numerator * 100.0 / denominator + 0.5);
  }
}
