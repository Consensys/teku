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

import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.logging.StatusLogger;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeMessage;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.validator.api.ValidatorPerformanceTrackingMode;
import tech.pegasys.teku.validator.coordinator.ActiveValidatorTracker;

public class DefaultPerformanceTracker implements PerformanceTracker {

  @VisibleForTesting
  final NavigableMap<UInt64, Set<SlotAndBlockRoot>> producedBlocksByEpoch =
      new ConcurrentSkipListMap<>();

  final NavigableMap<UInt64, Set<Attestation>> producedAttestationsByEpoch =
      new ConcurrentSkipListMap<>();

  final NavigableMap<UInt64, AtomicInteger> blockProductionAttemptsByEpoch =
      new ConcurrentSkipListMap<>();

  public static final UInt64 ATTESTATION_INCLUSION_RANGE = UInt64.valueOf(2);

  private final CombinedChainDataClient combinedChainDataClient;
  private final StatusLogger statusLogger;
  private final ValidatorPerformanceMetrics validatorPerformanceMetrics;
  private final ValidatorPerformanceTrackingMode mode;
  private final ActiveValidatorTracker validatorTracker;
  private final SyncCommitteePerformanceTracker syncCommitteePerformanceTracker;
  private final Spec spec;

  private volatile Optional<UInt64> nodeStartEpoch = Optional.empty();
  private final AtomicReference<UInt64> latestAnalyzedEpoch = new AtomicReference<>(UInt64.ZERO);

  public DefaultPerformanceTracker(
      CombinedChainDataClient combinedChainDataClient,
      StatusLogger statusLogger,
      ValidatorPerformanceMetrics validatorPerformanceMetrics,
      ValidatorPerformanceTrackingMode mode,
      ActiveValidatorTracker validatorTracker,
      SyncCommitteePerformanceTracker syncCommitteePerformanceTracker,
      final Spec spec) {
    this.combinedChainDataClient = combinedChainDataClient;
    this.statusLogger = statusLogger;
    this.validatorPerformanceMetrics = validatorPerformanceMetrics;
    this.mode = mode;
    this.validatorTracker = validatorTracker;
    this.syncCommitteePerformanceTracker = syncCommitteePerformanceTracker;
    this.spec = spec;
  }

  @Override
  public void start(UInt64 nodeStartSlot) {
    this.nodeStartEpoch = Optional.of(spec.computeEpochAtSlot(nodeStartSlot));
  }

  @Override
  public void onSlot(UInt64 slot) {
    // Ensure a consistent view as the field is volatile.
    final Optional<UInt64> nodeStartEpoch = this.nodeStartEpoch;
    if (nodeStartEpoch.isEmpty() || combinedChainDataClient.getChainHead().isEmpty()) {
      return;
    }

    if (slot.mod(spec.getSlotsPerEpoch(slot)).isGreaterThan(UInt64.ZERO)) {
      return;
    }

    UInt64 currentEpoch = spec.computeEpochAtSlot(slot);
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

      // suppress performance metric output when not relevant
      if (mode.isLoggingEnabled() && attestationPerformance.numberOfExpectedAttestations > 0) {
        statusLogger.performance(attestationPerformance.toString());
      }

      if (mode.isMetricsEnabled()) {
        validatorPerformanceMetrics.updateAttestationPerformanceMetrics(attestationPerformance);
      }
      producedAttestationsByEpoch.headMap(analyzedEpoch, true).clear();
    }

    // Nothing to report until epoch 0 is complete
    if (!currentEpoch.isZero()) {
      final UInt64 blockProductionEpoch = currentEpoch.minus(1);
      BlockPerformance blockPerformance = getBlockPerformanceForEpoch(blockProductionEpoch);
      if (blockPerformance.numberOfExpectedBlocks > 0) {
        if (mode.isLoggingEnabled()) {
          statusLogger.performance(blockPerformance.toString());
        }

        if (mode.isMetricsEnabled()) {
          validatorPerformanceMetrics.updateBlockPerformanceMetrics(blockPerformance);
        }
      }
      producedBlocksByEpoch.headMap(blockProductionEpoch, true).clear();
      blockProductionAttemptsByEpoch.headMap(blockProductionEpoch, true).clear();
    }

    // Nothing to report until epoch 0 is complete
    if (!currentEpoch.isZero()) {
      final SyncCommitteePerformance syncCommitteePerformance =
          syncCommitteePerformanceTracker.calculatePerformance(currentEpoch.minus(1)).join();
      if (syncCommitteePerformance.getNumberOfExpectedMessages() > 0) {
        if (mode.isLoggingEnabled()) {
          statusLogger.performance(syncCommitteePerformance.toString());
        }

        if (mode.isMetricsEnabled()) {
          validatorPerformanceMetrics.updateSyncCommitteePerformance(syncCommitteePerformance);
        }
      }
    }
  }

  private BlockPerformance getBlockPerformanceForEpoch(UInt64 currentEpoch) {
    int numberOfBlockProductionAttempts = 0;
    AtomicInteger blocksAtEpoch = blockProductionAttemptsByEpoch.get(currentEpoch);
    if (blocksAtEpoch != null) {
      numberOfBlockProductionAttempts = blocksAtEpoch.get();
    }

    Collection<SlotAndBlockRoot> producedBlocks = Collections.emptyList();
    if (producedBlocksByEpoch.get(currentEpoch) != null) {
      producedBlocks = producedBlocksByEpoch.get(currentEpoch);
    }
    final StateAndBlockSummary chainHead = combinedChainDataClient.getChainHead().orElseThrow();
    final BeaconState state = chainHead.getState();
    final long numberOfIncludedBlocks =
        producedBlocks.stream()
            .filter(
                producedBlock ->
                    // Chain head root itself isn't available in state history
                    producedBlock.getBlockRoot().equals(chainHead.getRoot())
                        || isInHistoricBlockRoots(state, producedBlock))
            .count();

    int numberOfProducedBlocks = producedBlocks.size();
    return new BlockPerformance(
        currentEpoch,
        numberOfBlockProductionAttempts,
        (int) numberOfIncludedBlocks,
        numberOfProducedBlocks);
  }

  private boolean isInHistoricBlockRoots(
      final BeaconState state, final SlotAndBlockRoot producedBlock) {
    return producedBlock.getSlot().isLessThan(state.getSlot())
        && spec.getBlockRootAtSlot(state, producedBlock.getSlot())
            .equals(producedBlock.getBlockRoot());
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
        producedAttestationsByEpoch.getOrDefault(analyzedEpoch, Collections.emptySet());
    BeaconState state = combinedChainDataClient.getBestState().orElseThrow();

    int correctTargetCount = 0;
    int correctHeadBlockCount = 0;
    List<Integer> inclusionDistances = new ArrayList<>();

    // Pre-process attestations included on chain to group them by
    // data hash to inclusion slot to aggregation bitlist
    Map<Bytes32, NavigableMap<UInt64, SszBitlist>> slotAndBitlistsByAttestationDataHash =
        new HashMap<>();
    for (UInt64 slot : attestationsIncludedOnChain.keySet()) {
      for (Attestation attestation : attestationsIncludedOnChain.get(slot)) {
        Bytes32 attestationDataHash = attestation.getData().hashTreeRoot();
        NavigableMap<UInt64, SszBitlist> slotToBitlists =
            slotAndBitlistsByAttestationDataHash.computeIfAbsent(
                attestationDataHash, __ -> new TreeMap<>());
        slotToBitlists.merge(slot, attestation.getAggregationBits(), SszBitlist::nullableOr);
      }
    }

    for (Attestation sentAttestation : producedAttestations) {
      Bytes32 sentAttestationDataHash = sentAttestation.getData().hashTreeRoot();
      UInt64 sentAttestationSlot = sentAttestation.getData().getSlot();
      if (!slotAndBitlistsByAttestationDataHash.containsKey(sentAttestationDataHash)) {
        continue;
      }
      NavigableMap<UInt64, SszBitlist> slotAndBitlists =
          slotAndBitlistsByAttestationDataHash.get(sentAttestationDataHash);
      for (UInt64 slot : slotAndBitlists.keySet()) {
        if (slotAndBitlists.get(slot).isSuperSetOf(sentAttestation.getAggregationBits())) {
          inclusionDistances.add(slot.minus(sentAttestationSlot).intValue());
          break;
        }
      }

      // Check if the attestation had correct target
      Bytes32 attestationTargetRoot = sentAttestation.getData().getTarget().getRoot();
      if (attestationTargetRoot.equals(spec.getBlockRoot(state, analyzedEpoch))) {
        correctTargetCount++;

        // Check if the attestation had correct head block root
        Bytes32 attestationHeadBlockRoot = sentAttestation.getData().getBeacon_block_root();
        if (attestationHeadBlockRoot.equals(spec.getBlockRootAtSlot(state, sentAttestationSlot))) {
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
            analyzedEpoch,
            validatorTracker.getNumberOfValidatorsForEpoch(analyzedEpoch),
            numberOfProducedAttestations,
            (int) inclusionDistanceStatistics.getCount(),
            inclusionDistanceStatistics.getMax(),
            inclusionDistanceStatistics.getMin(),
            inclusionDistanceStatistics.getAverage(),
            correctTargetCount,
            correctHeadBlockCount)
        : AttestationPerformance.empty(
            analyzedEpoch, validatorTracker.getNumberOfValidatorsForEpoch(analyzedEpoch));
  }

  private Set<BeaconBlock> getBlocksInEpochs(UInt64 startEpochInclusive, UInt64 endEpochExclusive) {
    UInt64 epochStartSlot = spec.computeStartSlotAtEpoch(startEpochInclusive);
    UInt64 inclusiveEndEpochEndSlot = spec.computeStartSlotAtEpoch(endEpochExclusive).decrement();

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
    UInt64 epoch = spec.computeEpochAtSlot(attestation.getData().getSlot());
    Set<Attestation> attestationsInEpoch =
        producedAttestationsByEpoch.computeIfAbsent(epoch, __ -> concurrentSet());
    attestationsInEpoch.add(attestation);
  }

  @Override
  public void saveProducedBlock(SignedBeaconBlock block) {
    UInt64 epoch = spec.computeEpochAtSlot(block.getSlot());
    Set<SlotAndBlockRoot> blocksInEpoch =
        producedBlocksByEpoch.computeIfAbsent(epoch, __ -> concurrentSet());
    blocksInEpoch.add(new SlotAndBlockRoot(block.getSlot(), block.getRoot()));
  }

  @Override
  public void reportBlockProductionAttempt(UInt64 epoch) {
    AtomicInteger numberOfBlockProductionAttempts =
        blockProductionAttemptsByEpoch.computeIfAbsent(epoch, __ -> new AtomicInteger(0));
    numberOfBlockProductionAttempts.incrementAndGet();
  }

  @Override
  public void saveExpectedSyncCommitteeParticipant(
      final int validatorIndex,
      final Set<Integer> syncCommitteeIndices,
      final UInt64 subscribeUntilEpoch) {
    syncCommitteePerformanceTracker.saveExpectedSyncCommitteeParticipant(
        validatorIndex, syncCommitteeIndices, subscribeUntilEpoch);
  }

  @Override
  public void saveProducedSyncCommitteeMessage(final SyncCommitteeMessage message) {
    syncCommitteePerformanceTracker.saveProducedSyncCommitteeMessage(message);
  }

  static long getPercentage(final long numerator, final long denominator) {
    return (long) (numerator * 100.0 / denominator + 0.5);
  }

  private <T> Set<T> concurrentSet() {
    return Collections.newSetFromMap(new ConcurrentHashMap<>());
  }
}
