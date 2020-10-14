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

import static java.lang.StrictMath.toIntExact;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.operations.AttestationData;
import tech.pegasys.teku.ssz.SSZTypes.Bitlist;

/**
 * Maintains an aggregated collection of attestations which all share the same {@link
 * AttestationData}.
 *
 * <p>So that the added attestations can be aggregated into the smallest number of aggregates, even
 * as the contents of the collection change, aggregation is actually done during iteration.
 * Aggregation starts with the attestation that already includes the most validators then continues
 * adding attestations in order of the number of validators they contain.
 *
 * <p>Note that the resulting aggregate will be invalid if attestations with different
 * AttestationData are added.
 */
class MatchingDataAttestationGroup implements Iterable<ValidateableAttestation> {

  private final NavigableMap<Integer, Set<ValidateableAttestation>> attestationsByValidatorCount =
      new TreeMap<>(Comparator.reverseOrder()); // Most validators first

  private final AttestationData attestationData;
  private final Bytes32 committeeShufflingSeed;
  private final Bitlist seenAggregationBits = Attestation.createEmptyAggregationBits();

  public MatchingDataAttestationGroup(
      final AttestationData attestationData, final Bytes32 committeeShufflingSeed) {
    this.attestationData = attestationData;
    this.committeeShufflingSeed = committeeShufflingSeed;
  }

  public AttestationData getAttestationData() {
    return attestationData;
  }

  /**
   * Adds an attestation to this group. When possible, the attestation will be aggregated with
   * others during iteration. Ignores attestations with no new, unseen aggregation bits.
   *
   * @param attestation the attestation to add
   * @return True if the attestation was added, false otherwise
   */
  public boolean add(final ValidateableAttestation attestation) {
    if (seenAggregationBits.isSuperSetOf(attestation.getAttestation().getAggregation_bits())) {
      // We've already seen these aggregation bits
      return false;
    }
    final int unseenAggregationBits = countUnseenAggregationBits(attestation.getAttestation());
    return attestationsByValidatorCount
        .computeIfAbsent(unseenAggregationBits, count -> new HashSet<>())
        .add(attestation);
  }

  /**
   * Iterates through the aggregation of attestations in this group. The iterator attempts to create
   * the minimum number of attestations that include all attestations in the group.
   *
   * <p>While it is guaranteed that every validator from an attestation in this group is included in
   * an aggregate produced by this iterator, there is no guarantee that the added attestation
   * instances themselves will be included.
   *
   * @return an iterator including attestations for every validator included in this group.
   */
  @Override
  public Iterator<ValidateableAttestation> iterator() {
    return new AggregatingIterator();
  }

  public Stream<ValidateableAttestation> stream() {
    return StreamSupport.stream(spliterator(), false);
  }

  /**
   * Returns true if there are no attestations in this group.
   *
   * @return true if this group is empty.
   */
  public boolean isEmpty() {
    return attestationsByValidatorCount.isEmpty();
  }

  public long size() {
    return attestationsByValidatorCount.values().stream().map(Set::size).reduce(0, Integer::sum);
  }

  /**
   * Updates {@code seenAggregationBits} and removes any attestation from this group whose
   * aggregation bits have all been seen.
   *
   * <p>This is well suited for removing attestations that have been included in a block.
   *
   * @param attestation the attestation to logically remove from the pool.
   */
  public int remove(final Attestation attestation) {
    final Bitlist newAggregationBits = getUnseenAggregationBitsFromAttestation(attestation);
    if (newAggregationBits.getBitCount() == 0) {
      // We've already seen and filtered out all of these bits, nothing to do
      return 0;
    }
    seenAggregationBits.setAllBits(newAggregationBits);

    final Map<Integer, Set<ValidateableAttestation>> attestationsToReprioritize = new HashMap<>();
    final Set<Map.Entry<Integer, Set<ValidateableAttestation>>> attestationEntries =
        attestationsByValidatorCount.entrySet();
    int numRemoved = 0;
    for (Iterator<Map.Entry<Integer, Set<ValidateableAttestation>>> i =
            attestationEntries.iterator();
        i.hasNext(); ) {
      final Map.Entry<Integer, Set<ValidateableAttestation>> entry = i.next();
      final int currentValidatorCount = entry.getKey();
      final Set<ValidateableAttestation> candidates = entry.getValue();
      for (Iterator<ValidateableAttestation> iterator = candidates.iterator();
          iterator.hasNext(); ) {
        ValidateableAttestation candidate = iterator.next();
        if (seenAggregationBits.isSuperSetOf(candidate.getAttestation().getAggregation_bits())) {
          iterator.remove();
          numRemoved++;
        } else if (newAggregationBits.intersects(
            candidate.getAttestation().getAggregation_bits())) {
          attestationsToReprioritize
              .computeIfAbsent(currentValidatorCount, __ -> new HashSet<>())
              .add(candidate);
        }
      }
      if (candidates.isEmpty()) {
        i.remove();
      }
    }

    // Reprioritize attestations based on new validator count
    reprioritizeAttestations(attestationsToReprioritize);

    return numRemoved;
  }

  private void reprioritizeAttestations(
      final Map<Integer, Set<ValidateableAttestation>> attestationsToReprioritize) {
    attestationsToReprioritize.keySet().stream()
        .sorted(Comparator.reverseOrder())
        .forEach(
            oldValidatorCount -> {
              final Set<ValidateableAttestation> attestations =
                  attestationsToReprioritize.get(oldValidatorCount);
              final Set<ValidateableAttestation> attestationsByOldCount =
                  attestationsByValidatorCount.getOrDefault(
                      oldValidatorCount, Collections.emptySet());

              // Remove attestations and re-add so that the priority is recalculated
              attestationsByOldCount.removeAll(attestations);
              attestations.forEach(this::add);

              // We're processing in reverse order so items should only move to lower values
              // So, should we safe to remove here
              if (attestationsByOldCount.isEmpty()) {
                attestationsByValidatorCount.remove(oldValidatorCount);
              }
            });
  }

  private int countUnseenAggregationBits(final Attestation attestation) {
    final long seenBits = seenAggregationBits.countMatchingBits(attestation.getAggregation_bits());
    return toIntExact(attestation.getAggregation_bits().getBitCount() - seenBits);
  }

  private Bitlist getUnseenAggregationBitsFromAttestation(final Attestation attestation) {
    final Bitlist newBits = attestation.getAggregation_bits().copy();
    // Unset bits we've already seen
    newBits
        .getAllSetBits()
        .forEach(
            index -> {
              if (seenAggregationBits.getBit(index)) {
                newBits.setBit(index, false);
              }
            });

    return newBits;
  }

  public Bytes32 getCommitteeShufflingSeed() {
    return committeeShufflingSeed;
  }

  private class AggregatingIterator implements Iterator<ValidateableAttestation> {
    private final Set<ValidateableAttestation> includedAttestations = new HashSet<>();

    @Override
    public boolean hasNext() {
      return streamRemainingAttestations().findAny().isPresent();
    }

    @Override
    public ValidateableAttestation next() {
      final AggregateAttestationBuilder builder = new AggregateAttestationBuilder(attestationData);
      streamRemainingAttestations()
          .forEach(
              candidate -> {
                if (builder.canAggregate(candidate)) {
                  builder.aggregate(candidate);
                } else if (builder.isFullyIncluded(candidate)) {
                  includedAttestations.add(candidate);
                }
              });
      includedAttestations.addAll(builder.getIncludedAttestations());
      return builder.buildAggregate();
    }

    public Stream<ValidateableAttestation> streamRemainingAttestations() {
      return attestationsByValidatorCount.values().stream()
          .flatMap(Set::stream)
          .filter(candidate -> !includedAttestations.contains(candidate));
    }
  }
}
