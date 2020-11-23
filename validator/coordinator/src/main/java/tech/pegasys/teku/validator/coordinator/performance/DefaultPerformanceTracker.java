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

import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.infrastructure.logging.StatusLogger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.Bitlist;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.util.config.Constants;
import tech.pegasys.teku.util.config.ValidatorPerformanceTrackingMode;
import tech.pegasys.teku.validator.coordinator.ActiveValidatorTracker;

public class DefaultPerformanceTracker implements PerformanceTracker {

  @VisibleForTesting
  final NavigableMap<UInt64, Set<SignedBeaconBlock>> producedBlocksByEpoch = new TreeMap<>();

  final NavigableMap<UInt64, Set<Attestation>> producedAttestationsByEpoch = new TreeMap<>();

  final NavigableMap<UInt64, AtomicInteger> blockProductionAttemptsByEpoch = new TreeMap<>();

  @VisibleForTesting
  static final UInt64 BLOCK_PERFORMANCE_EVALUATION_INTERVAL = UInt64.valueOf(2); // epochs

  public static final UInt64 ATTESTATION_INCLUSION_RANGE = UInt64.valueOf(2);

  private final CombinedChainDataClient combinedChainDataClient;
  private final StatusLogger statusLogger;
  private final ValidatorPerformanceMetrics validatorPerformanceMetrics;
  private final ValidatorPerformanceTrackingMode mode;
  private final ActiveValidatorTracker validatorTracker;

  private Optional<UInt64> nodeStartEpoch = Optional.empty();
  private AtomicReference<UInt64> latestAnalyzedEpoch = new AtomicReference<>(UInt64.ZERO);

  public DefaultPerformanceTracker(
      CombinedChainDataClient combinedChainDataClient,
      StatusLogger statusLogger,
      ValidatorPerformanceMetrics validatorPerformanceMetrics,
      ValidatorPerformanceTrackingMode mode,
      ActiveValidatorTracker validatorTracker) {
    this.combinedChainDataClient = combinedChainDataClient;
    this.statusLogger = statusLogger;
    this.validatorPerformanceMetrics = validatorPerformanceMetrics;
    this.mode = mode;
    this.validatorTracker = validatorTracker;
  }

  @Override
  public void start(UInt64 nodeStartSlot) {
    this.nodeStartEpoch = Optional.of(compute_epoch_at_slot(nodeStartSlot));
  }

  @Override
  public void onSlot(UInt64 slot) {
    if (nodeStartEpoch.isEmpty()) {
      return;
    }

    if (slot.mod(Constants.SLOTS_PER_EPOCH).isGreaterThan(UInt64.ZERO)) {
      return;
    }

    UInt64 currentEpoch = compute_epoch_at_slot(slot);
    if (currentEpoch.isLessThanOrEqualTo(
        latestAnalyzedEpoch.getAndUpdate(
            val -> val.isLessThan(currentEpoch) ? currentEpoch : val))) {
      return;
    }

    // Output attestation performance information for current epoch - 2 since attestations can be
    // included in both the epoch they were produced in or in the one following.
    if (currentEpoch.isGreaterThanOrEqualTo(
        nodeStartEpoch.get().plus(ATTESTATION_INCLUSION_RANGE))) {
      UInt64 analyzedEpoch = currentEpoch.minus(ATTESTATION_INCLUSION_RANGE);
      AttestationPerformance attestationPerformance =
          getAttestationPerformanceForEpoch(currentEpoch, analyzedEpoch);

      if (mode.isLoggingEnabled()) {
        statusLogger.performance(attestationPerformance.toString());
      }

      if (mode.isMetricsEnabled()) {
        validatorPerformanceMetrics.updateAttestationPerformanceMetrics(attestationPerformance);
      }

      producedAttestationsByEpoch.headMap(analyzedEpoch, true).clear();
    }

    // Output block performance information for the past BLOCK_PERFORMANCE_INTERVAL epochs
    if (currentEpoch.isGreaterThanOrEqualTo(BLOCK_PERFORMANCE_EVALUATION_INTERVAL)) {
      if (currentEpoch.mod(BLOCK_PERFORMANCE_EVALUATION_INTERVAL).equals(UInt64.ZERO)) {
        UInt64 oldestAnalyzedEpoch = currentEpoch.minus(BLOCK_PERFORMANCE_EVALUATION_INTERVAL);
        BlockPerformance blockPerformance =
            getBlockPerformanceForEpochs(oldestAnalyzedEpoch, currentEpoch);
        if (blockPerformance.numberOfExpectedBlocks > 0) {

          if (mode.isLoggingEnabled()) {
            statusLogger.performance(blockPerformance.toString());
          }

          if (mode.isMetricsEnabled()) {
            validatorPerformanceMetrics.updateBlockPerformanceMetrics(blockPerformance);
          }

          producedBlocksByEpoch.headMap(oldestAnalyzedEpoch, true).clear();
          blockProductionAttemptsByEpoch.headMap(oldestAnalyzedEpoch, true).clear();
        }
      }
    }
  }

  private BlockPerformance getBlockPerformanceForEpochs(
      UInt64 startEpochInclusive, UInt64 endEpochExclusive) {
    int numberOfBlockProductionAttempts =
        blockProductionAttemptsByEpoch.subMap(startEpochInclusive, true, endEpochExclusive, false)
            .values().stream()
            .mapToInt(AtomicInteger::get)
            .sum();
    List<SignedBeaconBlock> producedBlocks =
        producedBlocksByEpoch.subMap(startEpochInclusive, true, endEpochExclusive, false).values()
            .stream()
            .flatMap(Collection::stream)
            .collect(Collectors.toList());

    long numberOfIncludedBlocks =
        producedBlocks.stream()
            .filter(
                sentBlock ->
                    combinedChainDataClient
                        .getBlockAtSlotExact(sentBlock.getSlot())
                        .join()
                        .map(block -> block.equals(sentBlock))
                        .orElse(false))
            .count();

    int numberOfProducedBlocks = producedBlocks.size();
    return new BlockPerformance(
        numberOfBlockProductionAttempts, (int) numberOfIncludedBlocks, numberOfProducedBlocks);
  }

  private AttestationPerformance getAttestationPerformanceForEpoch(
      UInt64 currentEpoch, UInt64 analyzedEpoch) {
    checkArgument(
        analyzedEpoch.isLessThanOrEqualTo(currentEpoch.minus(ATTESTATION_INCLUSION_RANGE)),
        "Epoch to analyze attestation performance must be at least 2 epochs less than the current epoch");

    // Attestations can be included in either the epoch they were produced in or in
    // the following epoch. Thus, the most recent epoch for which we can evaluate attestation
    // performance is current epoch - 2.
    UInt64 analysisRangeEndEpoch = analyzedEpoch.plus(ATTESTATION_INCLUSION_RANGE);

    // Get included attestations for the given epochs in a map from slot to attestations
    // included in block.
    Map<UInt64, List<Attestation>> attestationsIncludedOnChain =
        getAttestationsIncludedInEpochs(analyzedEpoch, analysisRangeEndEpoch);

    // Get sent attestations in range
    Set<Attestation> producedAttestations =
        producedAttestationsByEpoch.getOrDefault(analyzedEpoch, new HashSet<>());
    BeaconState state = combinedChainDataClient.getBestState().orElseThrow();

    int correctTargetCount = 0;
    int correctHeadBlockCount = 0;
    List<Integer> inclusionDistances = new ArrayList<>();

    // Pre-process attestations included on chain to group them by
    // data hash to inclusion slot to aggregation bitlist
    Map<Bytes32, NavigableMap<UInt64, Bitlist>> slotAndBitlistsByAttestationDataHash =
        new HashMap<>();
    for (UInt64 slot : attestationsIncludedOnChain.keySet()) {
      for (Attestation attestation : attestationsIncludedOnChain.get(slot)) {
        Bytes32 attestationDataHash = attestation.getData().hash_tree_root();
        NavigableMap<UInt64, Bitlist> slotToBitlists =
            slotAndBitlistsByAttestationDataHash.computeIfAbsent(
                attestationDataHash, __ -> new TreeMap<>());
        Bitlist bitlistToInsert =
            slotToBitlists.computeIfAbsent(slot, __ -> attestation.getAggregation_bits().copy());
        bitlistToInsert.setAllBits(attestation.getAggregation_bits());
      }
    }

    for (Attestation sentAttestation : producedAttestations) {
      Bytes32 sentAttestationDataHash = sentAttestation.getData().hash_tree_root();
      UInt64 sentAttestationSlot = sentAttestation.getData().getSlot();
      if (!slotAndBitlistsByAttestationDataHash.containsKey(sentAttestationDataHash)) {
        continue;
      }
      NavigableMap<UInt64, Bitlist> slotAndBitlists =
          slotAndBitlistsByAttestationDataHash.get(sentAttestationDataHash);
      for (UInt64 slot : slotAndBitlists.keySet()) {
        if (slotAndBitlists.get(slot).isSuperSetOf(sentAttestation.getAggregation_bits())) {
          inclusionDistances.add(slot.minus(sentAttestationSlot).intValue());
          break;
        }
      }

      // Check if the attestation had correct target
      Bytes32 attestationTargetRoot = sentAttestation.getData().getTarget().getRoot();
      if (attestationTargetRoot.equals(get_block_root(state, analyzedEpoch))) {
        correctTargetCount++;

        // Check if the attestation had correct head block root
        Bytes32 attestationHeadBlockRoot = sentAttestation.getData().getBeacon_block_root();
        if (attestationHeadBlockRoot.equals(get_block_root_at_slot(state, sentAttestationSlot))) {
          correctHeadBlockCount++;
        }
      }
    }

    IntSummaryStatistics inclusionDistanceStatistics =
        inclusionDistances.stream().collect(Collectors.summarizingInt(Integer::intValue));

    // IntSummaryStatistics returns Integer.MIN and MAX when the summarized integer list is empty.
    int numberOfProducedAttestations = producedAttestations.size();
    return producedAttestations.size() > 0
        ? new AttestationPerformance(
            validatorTracker.getNumberOfValidatorsForEpoch(analyzedEpoch),
            numberOfProducedAttestations,
            (int) inclusionDistanceStatistics.getCount(),
            inclusionDistanceStatistics.getMax(),
            inclusionDistanceStatistics.getMin(),
            inclusionDistanceStatistics.getAverage(),
            correctTargetCount,
            correctHeadBlockCount)
        : AttestationPerformance.empty(
            validatorTracker.getNumberOfValidatorsForEpoch(analyzedEpoch));
  }

  private Set<BeaconBlock> getBlocksInEpochs(UInt64 startEpochInclusive, UInt64 endEpochExclusive) {
    UInt64 epochStartSlot = compute_start_slot_at_epoch(startEpochInclusive);
    UInt64 inclusiveEndEpochEndSlot = compute_start_slot_at_epoch(endEpochExclusive).decrement();

    Set<BeaconBlock> blocksInEpoch = new HashSet<>();
    UInt64 currSlot = inclusiveEndEpochEndSlot;
    while (currSlot.isGreaterThanOrEqualTo(epochStartSlot)) {
      Optional<BeaconBlock> block =
          combinedChainDataClient
              .getBlockInEffectAtSlot(currSlot)
              .join()
              .map(SignedBeaconBlock::getMessage);
      block.ifPresent(blocksInEpoch::add);

      if (block.isEmpty() || block.get().getSlot().equals(UInt64.ZERO)) {
        break;
      }
      currSlot = block.get().getSlot().decrement();
    }
    return blocksInEpoch;
  }

  private Map<UInt64, List<Attestation>> getAttestationsIncludedInEpochs(
      UInt64 startEpochInclusive, UInt64 endEpochExclusive) {
    return getBlocksInEpochs(startEpochInclusive, endEpochExclusive).stream()
        .collect(
            Collectors.toMap(
                BeaconBlock::getSlot, block -> block.getBody().getAttestations().asList()));
  }

  @Override
  public void saveProducedAttestation(Attestation attestation) {
    UInt64 epoch = compute_epoch_at_slot(attestation.getData().getSlot());
    Set<Attestation> attestationsInEpoch =
        producedAttestationsByEpoch.computeIfAbsent(epoch, __ -> new HashSet<>());
    attestationsInEpoch.add(attestation);
  }

  @Override
  public void saveProducedBlock(SignedBeaconBlock block) {
    UInt64 epoch = compute_epoch_at_slot(block.getSlot());
    Set<SignedBeaconBlock> blocksInEpoch =
        producedBlocksByEpoch.computeIfAbsent(epoch, __ -> new HashSet<>());
    blocksInEpoch.add(block);
  }

  @Override
  public void reportBlockProductionAttempt(UInt64 epoch) {
    AtomicInteger numberOfBlockProductionAttempts =
        blockProductionAttemptsByEpoch.computeIfAbsent(epoch, __ -> new AtomicInteger(0));
    numberOfBlockProductionAttempts.incrementAndGet();
  }

  static long getPercentage(final long numerator, final long denominator) {
    return (long) (numerator * 100.0 / denominator + 0.5);
  }
}
