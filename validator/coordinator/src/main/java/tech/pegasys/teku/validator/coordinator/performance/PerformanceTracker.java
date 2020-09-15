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

package tech.pegasys.teku.validator.coordinator.performance;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_block_root;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_block_root_at_slot;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
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

  private final NavigableMap<UInt64, List<SignedBeaconBlock>> sentBlocksByEpoch = new TreeMap<>();
  private final NavigableMap<UInt64, List<Attestation>> sentAttestationsByEpoch = new TreeMap<>();

  private static final UInt64 BLOCK_PERFORMANCE_EVALUATION_INTERVAL = UInt64.valueOf(100); // epochs
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

    // Output attestation performance information for current epoch - 2 since attestations can be
    // included in both the epoch they were produced in or in the one following.
    LOG.info(
        getAttestationPerformanceForEpoch(currentEpoch, currentEpoch.minus(UInt64.valueOf(2))));

    // Output block performance information for the past BLOCK_PERFORMANCE_INTERVAL epochs
    if (currentEpoch.mod(BLOCK_PERFORMANCE_EVALUATION_INTERVAL).equals(UInt64.ZERO)) {
      LOG.info(
          getBlockPerformanceForEpochs(
              currentEpoch.minus(BLOCK_PERFORMANCE_EVALUATION_INTERVAL), currentEpoch));
    }
  }

  private BlockPerformance getBlockPerformanceForEpochs(
      UInt64 startEpochInclusive, UInt64 endEpochExclusive) {
    List<BeaconBlock> blockInEpoch = getBlocksInEpochs(startEpochInclusive, endEpochExclusive);
    List<SignedBeaconBlock> sentBlocks =
        sentBlocksByEpoch.subMap(startEpochInclusive, true, endEpochExclusive, false).values()
            .stream()
            .flatMap(Collection::stream)
            .collect(Collectors.toList());
    long numberOfIncludedBlocks =
        sentBlocks.stream()
            .map(SignedBeaconBlock::getMessage)
            .filter(blockInEpoch::contains)
            .count();

    return new BlockPerformance((int) numberOfIncludedBlocks, sentBlocks.size());
  }

  private AttestationPerformance getAttestationPerformanceForEpoch(
      UInt64 currentEpoch, UInt64 analyzedEpoch) {
    checkArgument(
        analyzedEpoch.isLessThanOrEqualTo(currentEpoch.minus(UInt64.valueOf(2))),
        "Epoch to analyze attestation performance must be at least 2 epochs less than the current epoch");
    // Attestations can be included in either the epoch they were produced in or in
    // the following epoch. Thus, the most recent epoch for which we can evaluate attestation
    // performance
    // is current epoch - 2.
    UInt64 epochFollowingAnalyzedEpoch = analyzedEpoch.increment();

    // Get included attestations for the given epochs in a map from slot to attestations
    // included in block.
    Map<UInt64, List<Attestation>> attestations =
        getAttestationsIncludedInEpochs(analyzedEpoch, epochFollowingAnalyzedEpoch);

    // Get sent attestations in range
    List<Attestation> sentAttestations =
        sentAttestationsByEpoch.subMap(analyzedEpoch, true, epochFollowingAnalyzedEpoch, false)
            .values().stream()
            .flatMap(Collection::stream)
            .collect(Collectors.toList());
    UInt64 analyzedEpochStartSlot = compute_start_slot_at_epoch(analyzedEpoch);
    UInt64 rangeEndSlot = compute_start_slot_at_epoch(epochFollowingAnalyzedEpoch.plus(UInt64.ONE));
    BeaconState state = recentChainData.getBestState().orElseThrow();

    int correctTargetCount = 0;
    int correctHeadBlockCount = 0;
    List<Integer> inclusionDistances = new ArrayList<>();

    for (Attestation sentAttestation : sentAttestations) {
      // Check if the sent attestation is included in any block in the appropriate range.
      // Appropriate range being: [ attestation_production_epoch, attestation_production_epoch + 1 ]
      UInt64 attestationSlot = sentAttestation.getData().getSlot();
      for (UInt64 currSlot = analyzedEpochStartSlot;
          currSlot.isLessThan(rangeEndSlot);
          currSlot = currSlot.increment()) {
        if (attestations.containsKey(currSlot)) {
          if (checkIfAttestationIsIncludedInList(sentAttestation, attestations.get(currSlot))) {
            inclusionDistances.add(currSlot.minus(attestationSlot).intValue());
          }
        }
      }

      // Check if the attestation had correct target
      Bytes32 attestationTargetRoot = sentAttestation.getData().getTarget().getRoot();
      if (attestationTargetRoot.equals(get_block_root(state, analyzedEpoch))) {
        correctTargetCount++;

        // Check if the attestation had correct head block root
        Bytes32 attestationHeadBlockRoot = sentAttestation.getData().getBeacon_block_root();
        if (attestationHeadBlockRoot.equals(get_block_root_at_slot(state, attestationSlot))) {
          correctHeadBlockCount++;
        }
      }
    }

    IntSummaryStatistics inclusionDistanceStatistics =
        inclusionDistances.stream().collect(Collectors.summarizingInt(Integer::intValue));

    return new AttestationPerformance(
        sentAttestations.size(),
        (int) inclusionDistanceStatistics.getCount(),
        inclusionDistanceStatistics.getMax(),
        inclusionDistanceStatistics.getMin(),
        inclusionDistanceStatistics.getAverage(),
        correctTargetCount,
        correctHeadBlockCount);
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

  static long getPercentage(final long numerator, final long denominator) {
    return (long) (numerator * 100.0 / denominator + 0.5);
  }
}
