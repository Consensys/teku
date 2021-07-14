/*
 * Copyright 2021 ConsenSys AG.
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.BeaconBlockBodyAltair;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeSignature;
import tech.pegasys.teku.ssz.collections.SszBitvector;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

/**
 * Tracks and reports validator performance metrics for production of sync committee signatures.
 *
 * <p>The tracking is done based on the rewards paid, which may be different to the number of
 * signatures actually produced, signed and published to gossip. This is done so that the percent of
 * available rewards earned can be accurately calculated from these reports.
 *
 * <p>The difference comes from the fact that validators may be appear multiple times in the same
 * sync subcommittee, including multiple times in the same subcommittee. A validator in the sync
 * committee will always produce one signature per slot. The beacon node will publish that same
 * signature to each subcommittee the validator is assigned to. The {@link SyncAggregate} actually
 * included in blocks includes that same signature multiple times - once for each time the validator
 * is in the sync committee.
 */
public class SyncCommitteePerformanceTracker {
  private static final Logger LOG = LogManager.getLogger();

  private final NavigableMap<UInt64, Map<UInt64, Set<Integer>>>
      expectedSyncCommitteeParticipantsByPeriodEndEpoch = new ConcurrentSkipListMap<>();

  // Slot to block root to set of indices of validators that produced a signature for that slot+root
  private final NavigableMap<UInt64, Map<Bytes32, Set<UInt64>>> signatureProducersBySlot =
      new ConcurrentSkipListMap<>();

  private final Spec spec;
  private final CombinedChainDataClient combinedChainDataClient;

  public SyncCommitteePerformanceTracker(
      final Spec spec, final CombinedChainDataClient combinedChainDataClient) {
    this.spec = spec;
    this.combinedChainDataClient = combinedChainDataClient;
  }

  public SafeFuture<SyncCommitteePerformance> calculatePerformance(final UInt64 epoch) {
    final Map<UInt64, Set<Integer>> expectedSyncCommitteeParticipants =
        getPeriodEndEpoch(epoch)
            .map(expectedSyncCommitteeParticipantsByPeriodEndEpoch::get)
            .orElse(Collections.emptyMap());

    final Map<UInt64, Map<Bytes32, Set<UInt64>>> producingValidatorsBySlotAndBlock =
        getProducingValidatorsForEpoch(epoch);
    return calculateSyncCommitteePerformance(
            epoch, expectedSyncCommitteeParticipants, producingValidatorsBySlotAndBlock)
        .thenPeek(__ -> clearReportedData(epoch));
  }

  private UInt64 getLastSlotOfEpoch(final UInt64 epoch) {
    return spec.computeStartSlotAtEpoch(epoch.plus(1)).minus(1);
  }

  /**
   * Gets the set of validators that produces signatures by slot, for inclusion in the specified
   * epoch.
   *
   * <p>Note that validators produce sync committee signatures from the slot before the epoch starts
   * up to and not including the last slot of the epoch. See {@link
   * tech.pegasys.teku.spec.logic.common.util.SyncCommitteeUtil#getEpochForDutiesAtSlot(UInt64)}
   *
   * @param epoch the epoch to get producing validators for.
   * @return map of slot to set of validators that produced a signature.
   */
  private NavigableMap<UInt64, Map<Bytes32, Set<UInt64>>> getProducingValidatorsForEpoch(
      final UInt64 epoch) {
    final UInt64 lastSlotOfEpoch = getLastSlotOfEpoch(epoch);
    return signatureProducersBySlot.subMap(
        spec.computeStartSlotAtEpoch(epoch).minusMinZero(1), true, lastSlotOfEpoch, false);
  }

  private void clearReportedData(final UInt64 epoch) {
    // Clear data that has been reported on.
    final UInt64 lastSlotOfEpoch = getLastSlotOfEpoch(epoch);
    signatureProducersBySlot.headMap(lastSlotOfEpoch, false).clear();
    expectedSyncCommitteeParticipantsByPeriodEndEpoch.headMap(epoch, true).clear();
  }

  private SafeFuture<SyncCommitteePerformance> calculateSyncCommitteePerformance(
      final UInt64 epoch,
      final Map<UInt64, Set<Integer>> assignedSubcommitteeIndicesByValidatorIndex,
      final Map<UInt64, Map<Bytes32, Set<UInt64>>> producingValidatorsBySlotAndBlock) {

    final int numberOfExpectedSignatures =
        assignedSubcommitteeIndicesByValidatorIndex.values().stream().mapToInt(Set::size).sum()
            * spec.atEpoch(epoch).getSlotsPerEpoch();

    int producedSignatureCount = 0;
    int correctSignatureCount = 0;
    final List<SafeFuture<Integer>> includedSignatureCountFutures = new ArrayList<>();
    for (Map.Entry<UInt64, Map<Bytes32, Set<UInt64>>> entry :
        producingValidatorsBySlotAndBlock.entrySet()) {
      final UInt64 slot = entry.getKey();
      final Map<Bytes32, Set<UInt64>> producingValidatorsByBlock = entry.getValue();

      final Optional<Bytes32> correctBlockRoot =
          combinedChainDataClient
              .getChainHead()
              .map(
                  head -> {
                    if (slot.isGreaterThanOrEqualTo(head.getSlot())) {
                      return head.getRoot();
                    } else {
                      return spec.getBlockRootAtSlot(head.getState(), slot);
                    }
                  });

      for (Entry<Bytes32, Set<UInt64>> blockEntry : producingValidatorsByBlock.entrySet()) {
        final Bytes32 blockRoot = blockEntry.getKey();
        final Set<UInt64> producingValidators = blockEntry.getValue();
        final int producedSignatureCountForBlock =
            countProducedSignatures(
                assignedSubcommitteeIndicesByValidatorIndex, slot, producingValidators);

        if (correctBlockRoot.isPresent() && correctBlockRoot.get().equals(blockRoot)) {
          correctSignatureCount += producedSignatureCountForBlock;
        }

        producedSignatureCount += producedSignatureCountForBlock;

        final UInt64 inclusionSlot = slot.plus(1);
        includedSignatureCountFutures.add(
            getSyncAggregateAtSlot(inclusionSlot)
                .thenApply(
                    maybeSyncAggregate ->
                        maybeSyncAggregate
                            .map(
                                syncAggregate ->
                                    countIncludedSignatures(
                                        assignedSubcommitteeIndicesByValidatorIndex,
                                        slot,
                                        producingValidators,
                                        syncAggregate))
                            .orElse(0)));
      }
    }

    final int numberOfProducedSignatures = producedSignatureCount;
    final int numberOfCorrectSignatures = correctSignatureCount;
    return SafeFuture.collectAll(includedSignatureCountFutures.stream())
        .thenApply(
            includedSignatureCounts -> includedSignatureCounts.stream().mapToInt(a -> a).sum())
        .thenApply(
            numberOfIncludedSignatures ->
                new SyncCommitteePerformance(
                    numberOfExpectedSignatures,
                    numberOfProducedSignatures,
                    numberOfCorrectSignatures,
                    numberOfIncludedSignatures));
  }

  private synchronized int countIncludedSignatures(
      final Map<UInt64, Set<Integer>> assignedSubcommitteeIndicesByValidatorIndex,
      final UInt64 slot,
      final Set<UInt64> producingValidators,
      final SyncAggregate syncAggregate) {

    final SszBitvector syncCommitteeBits = syncAggregate.getSyncCommitteeBits();
    int numberOfIncludedSignatures = 0;
    for (UInt64 producingValidatorIndex : producingValidators) {
      final Set<Integer> committeeIndices =
          assignedSubcommitteeIndicesByValidatorIndex.get(producingValidatorIndex);
      if (committeeIndices == null) {
        LOG.debug(
            "Validator {} produced a SyncCommitteeSignature in slot {} but wasn't expected to",
            producingValidatorIndex,
            slot);
        continue;
      }
      for (Integer committeeIndex : committeeIndices) {
        if (syncCommitteeBits.getBit(committeeIndex)) {
          numberOfIncludedSignatures++;
        }
      }
    }
    return numberOfIncludedSignatures;
  }

  private int countProducedSignatures(
      final Map<UInt64, Set<Integer>> assignedSubcommitteeIndicesByValidatorIndex,
      final UInt64 slot,
      final Set<UInt64> producingValidators) {
    int numberOfProducedSignatures = 0;
    for (UInt64 producingValidatorIndex : producingValidators) {
      final Set<Integer> committeeIndices =
          assignedSubcommitteeIndicesByValidatorIndex.get(producingValidatorIndex);
      if (committeeIndices == null) {
        LOG.debug(
            "Validator {} produced a SyncCommitteeSignature in slot {} but wasn't expected to",
            producingValidatorIndex,
            slot);
        continue;
      }
      numberOfProducedSignatures += committeeIndices.size();
    }
    return numberOfProducedSignatures;
  }

  private SafeFuture<Optional<SyncAggregate>> getSyncAggregateAtSlot(final UInt64 slot) {
    return combinedChainDataClient
        .getBlockAtSlotExact(slot)
        .thenApply(
            maybeBlock ->
                maybeBlock
                    .flatMap(block -> block.getMessage().getBody().toVersionAltair())
                    .map(BeaconBlockBodyAltair::getSyncAggregate));
  }

  public void saveExpectedSyncCommitteeParticipant(
      final int validatorIndex,
      final Set<Integer> syncCommitteeIndices,
      final UInt64 subscribeUntilEpoch) {
    getPeriodEndEpoch(subscribeUntilEpoch)
        .ifPresent(
            periodEndEpoch ->
                expectedSyncCommitteeParticipantsByPeriodEndEpoch
                    .computeIfAbsent(periodEndEpoch, __ -> new ConcurrentHashMap<>())
                    .put(UInt64.valueOf(validatorIndex), syncCommitteeIndices));
  }

  public void saveProducedSyncCommitteeSignature(final SyncCommitteeSignature signature) {
    signatureProducersBySlot
        .computeIfAbsent(signature.getSlot(), __ -> new ConcurrentHashMap<>())
        .computeIfAbsent(
            signature.getBeaconBlockRoot(),
            __ -> Collections.newSetFromMap(new ConcurrentHashMap<>()))
        .add(signature.getValidatorIndex());
  }

  private Optional<UInt64> getPeriodEndEpoch(final UInt64 epoch) {
    return spec.atEpoch(epoch)
        .getSyncCommitteeUtil()
        .map(util -> util.computeFirstEpochOfNextSyncCommitteePeriod(epoch));
  }
}
