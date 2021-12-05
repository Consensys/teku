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

package tech.pegasys.teku.statetransition.attestation;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.metrics.SettableGauge;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.statetransition.attestation.AttestationWorthinessChecker;

/**
 * Maintains a pool of attestations. Attestations can be retrieved either for inclusion in a block
 * or as an aggregate to publish as part of the naive attestation aggregation algorithm. In both
 * cases the returned attestations are aggregated to maximise the number of validators that can be
 * included.
 */
public class AggregatingAttestationPool implements SlotEventsChannel {
  static final long ATTESTATION_RETENTION_EPOCHS = 2;
  private static final SszListSchema<Attestation, ?> ATTESTATIONS_SCHEMA =
      SszListSchema.create(Attestation.SSZ_SCHEMA, 128);

  private final Map<Bytes, MatchingDataAttestationGroup> attestationGroupByDataHash =
      new HashMap<>();
  private final NavigableMap<UInt64, Set<Bytes>> dataHashBySlot = new TreeMap<>();

  private final Spec spec;
  private final AtomicInteger size = new AtomicInteger(0);
  private final SettableGauge sizeGauge;

  public AggregatingAttestationPool(final Spec spec, final MetricsSystem metricsSystem) {
    this.spec = spec;
    this.sizeGauge =
        SettableGauge.create(
            metricsSystem,
            TekuMetricCategory.BEACON,
            "attestation_pool_size",
            "The number of attestations available to be included in proposed blocks");
  }

  public synchronized void add(final ValidateableAttestation attestation) {
    final AttestationData attestationData = attestation.getAttestation().getData();
    final boolean add = getOrCreateAttestationGroup(attestationData).add(attestation);
    if (add) {
      updateSize(1);
    }
  }

  private MatchingDataAttestationGroup getOrCreateAttestationGroup(
      final AttestationData attestationData) {
    dataHashBySlot
        .computeIfAbsent(attestationData.getSlot(), slot -> new HashSet<>())
        .add(attestationData.hashTreeRoot());
    return attestationGroupByDataHash.computeIfAbsent(
        attestationData.hashTreeRoot(),
        key -> new MatchingDataAttestationGroup(spec, attestationData));
  }

  @Override
  public synchronized void onSlot(final UInt64 slot) {
    final UInt64 attestationRetentionSlots =
        UInt64.valueOf(spec.getSlotsPerEpoch(slot) * ATTESTATION_RETENTION_EPOCHS);
    if (slot.compareTo(attestationRetentionSlots) <= 0) {
      return;
    }
    final UInt64 firstValidAttestationSlot = slot.minus(attestationRetentionSlots);
    final Collection<Set<Bytes>> dataHashesToRemove =
        dataHashBySlot.headMap(firstValidAttestationSlot, false).values();
    dataHashesToRemove.stream()
        .flatMap(Set::stream)
        .forEach(
            key -> {
              final int removed = attestationGroupByDataHash.get(key).size();
              attestationGroupByDataHash.remove(key);
              updateSize(-removed);
            });
    dataHashesToRemove.clear();
  }

  public synchronized void onAttestationsIncludedInBlock(
      final UInt64 slot, final Iterable<Attestation> attestations) {
    attestations.forEach(attestation -> onAttestationIncludedInBlock(slot, attestation));
  }

  private void onAttestationIncludedInBlock(final UInt64 slot, final Attestation attestation) {
    final AttestationData attestationData = attestation.getData();
    final MatchingDataAttestationGroup attestations = getOrCreateAttestationGroup(attestationData);
    final int numRemoved = attestations.onAttestationIncludedInBlock(slot, attestation);
    updateSize(-numRemoved);
  }

  private void updateSize(final int delta) {
    final int currentSize = size.addAndGet(delta);
    sizeGauge.set(currentSize);
  }

  public synchronized int getSize() {
    return size.get();
  }

  public synchronized SszList<Attestation> getAttestationsForBlock(
      final BeaconState stateAtBlockSlot,
      final AttestationForkChecker forkChecker,
      final AttestationWorthinessChecker worthinessChecker) {
    final UInt64 currentEpoch = spec.getCurrentEpoch(stateAtBlockSlot);
    final int previousEpochLimit = spec.getPreviousEpochAttestationCapacity(stateAtBlockSlot);

    final AtomicInteger prevEpochCount = new AtomicInteger(0);
    return dataHashBySlot.descendingMap().values().stream()
        .flatMap(Collection::stream)
        .map(attestationGroupByDataHash::get)
        .filter(Objects::nonNull)
        .filter(group -> isValid(stateAtBlockSlot, group.getAttestationData()))
        .filter(forkChecker::areAttestationsFromCorrectFork)
        .filter(group -> worthinessChecker.areAttestationsWorthy(group.getAttestationData()))
        .flatMap(MatchingDataAttestationGroup::stream)
        .limit(ATTESTATIONS_SCHEMA.getMaxLength())
        .map(ValidateableAttestation::getAttestation)
        .filter(
            att -> {
              if (spec.computeEpochAtSlot(att.getData().getSlot()).isLessThan(currentEpoch)) {
                final int currentCount = prevEpochCount.getAndIncrement();
                return currentCount < previousEpochLimit;
              }
              return true;
            })
        .collect(ATTESTATIONS_SCHEMA.collector());
  }

  public synchronized Stream<Attestation> getAttestations(
      final Optional<UInt64> maybeSlot, final Optional<UInt64> maybeCommitteeIndex) {
    final Predicate<Map.Entry<UInt64, Set<Bytes>>> filterForSlot =
        (entry) -> maybeSlot.map(slot -> entry.getKey().equals(slot)).orElse(true);

    final Predicate<MatchingDataAttestationGroup> filterForCommitteeIndex =
        (group) ->
            maybeCommitteeIndex
                .map(index -> group.getAttestationData().getIndex().equals(index))
                .orElse(true);

    return dataHashBySlot.descendingMap().entrySet().stream()
        .filter(filterForSlot)
        .map(Map.Entry::getValue)
        .flatMap(Collection::stream)
        .map(attestationGroupByDataHash::get)
        .filter(Objects::nonNull)
        .filter(filterForCommitteeIndex)
        .flatMap(MatchingDataAttestationGroup::stream)
        .map(ValidateableAttestation::getAttestation);
  }

  private boolean isValid(
      final BeaconState stateAtBlockSlot, final AttestationData attestationData) {
    return spec.validateAttestation(stateAtBlockSlot, attestationData).isEmpty();
  }

  public synchronized Optional<ValidateableAttestation> createAggregateFor(
      final Bytes32 attestationHashTreeRoot) {
    return Optional.ofNullable(attestationGroupByDataHash.get(attestationHashTreeRoot))
        .flatMap(attestations -> attestations.stream().findFirst());
  }

  public synchronized void onReorg(final UInt64 commonAncestorSlot) {
    attestationGroupByDataHash.values().forEach(group -> group.onReorg(commonAncestorSlot));
  }
}
