/*
 * Copyright Consensys Software Inc., 2025
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

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.statetransition.attestation.utils.AttestationBitsAggregator;

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
public class MatchingDataAttestationGroup implements Iterable<PooledAttestation> {

  private final NavigableMap<Integer, Set<PooledAttestation>> attestationsByValidatorCount =
      new TreeMap<>(Comparator.reverseOrder()); // Most validators first

  private final Spec spec;
  private Optional<Bytes32> committeeShufflingSeed = Optional.empty();
  private final AttestationData attestationData;
  private final Optional<Int2IntMap> committeesSize;

  /**
   * Tracks which validators were included in attestations at a given slot on the canonical chain.
   *
   * <p>When a reorg occurs we can accurately compute the set of included validators at the common
   * ancestor by removing blocks in slots after the ancestor then recalculating {@link
   * #includedValidators}. Otherwise, we might remove a validator from the included list because it
   * was in a block moved off the canonical chain even though that validator was also included in an
   * earlier block which is still on the canonical chain.
   *
   * <p>Pruning isn't required for this map because the entire attestation group is dropped by
   * {@link AggregatingAttestationPool} once it is too old to be included in blocks (32 slots).
   */
  private final NavigableMap<UInt64, AttestationBitsAggregator> includedValidatorsBySlot =
      new TreeMap<>();

  /** Precalculated combined list of included validators across all blocks. */
  private AttestationBitsAggregator includedValidators;

  public MatchingDataAttestationGroup(
      final Spec spec,
      final AttestationData attestationData,
      final Optional<Int2IntMap> committeesSize) {
    this.spec = spec;
    this.attestationData = attestationData;
    this.committeesSize = committeesSize;
    this.includedValidators = createEmptyAttestationBits();
  }

  private AttestationBitsAggregator createEmptyAttestationBits() {
    return AttestationBitsAggregator.fromEmptyFromAttestationSchema(
        spec.atSlot(attestationData.getSlot()).getSchemaDefinitions().getAttestationSchema(),
        committeesSize);
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
  public boolean add(
      final PooledAttestation attestation, final Optional<Bytes32> committeeShufflingSeed) {
    if (includedValidators.isSuperSetOf(attestation)) {
      // All attestation bits have already been included on chain
      return false;
    }
    if (this.committeeShufflingSeed.isEmpty()) {
      this.committeeShufflingSeed = committeeShufflingSeed;
    }
    return attestationsByValidatorCount
        .computeIfAbsent(attestation.bits().getBitCount(), count -> new HashSet<>())
        .add(attestation);
  }

  /**
   * Iterates through the aggregation of attestations in this group. The iterator attempts to create
   * the minimum number of attestations that include all attestations in the group.
   *
   * <p>committeeIndex is an optional parameter that enables aggregation over a specified committee
   * (applies to Electra only)
   *
   * <p>While it is guaranteed that every validator from an attestation in this group is included in
   * an aggregate produced by this iterator, there is no guarantee that the added attestation
   * instances themselves will be included.
   *
   * @return an iterator including attestations for every validator included in this group.
   */
  @Override
  public Iterator<PooledAttestation> iterator() {
    return new AggregatingIterator(Optional.empty());
  }

  public Iterator<PooledAttestation> iterator(final Optional<UInt64> committeeIndex) {
    return new AggregatingIterator(committeeIndex);
  }

  public Stream<PooledAttestation> stream() {
    return StreamSupport.stream(spliterator(Optional.empty()), false);
  }

  public Stream<PooledAttestation> stream(final Optional<UInt64> committeeIndex) {
    return StreamSupport.stream(spliterator(committeeIndex), false);
  }

  public Stream<PooledAttestation> stream(
      final Optional<UInt64> committeeIndex, final boolean requiresCommitteeBits) {
    if (noMatchingAttestations(committeeIndex, requiresCommitteeBits)) {
      return Stream.empty();
    }
    return StreamSupport.stream(spliterator(committeeIndex), false);
  }

  public Spliterator<PooledAttestation> spliterator(final Optional<UInt64> committeeIndex) {
    return Spliterators.spliteratorUnknownSize(iterator(committeeIndex), 0);
  }

  /**
   * Returns true if there are no attestations in this group.
   *
   * @return true if this group is empty.
   */
  public boolean isEmpty() {
    return attestationsByValidatorCount.isEmpty();
  }

  public int size() {
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
  public int onAttestationIncludedInBlock(final UInt64 slot, final Attestation attestation) {
    // Record validators in attestation as seen in this slot
    // Important to do even if the attestation is redundant so we handle re-orgs correctly
    includedValidatorsBySlot.compute(
        slot,
        (__, attestationBitsCalculator) -> {
          if (attestationBitsCalculator == null) {
            return AttestationBitsAggregator.of(attestation, committeesSize);
          }
          attestationBitsCalculator.or(attestation);
          return attestationBitsCalculator;
        });

    if (includedValidators.isSuperSetOf(attestation)) {
      // We've already seen and filtered out all of these bits, nothing to do
      return 0;
    }
    includedValidators.or(attestation);

    final Collection<Set<PooledAttestation>> attestationSets =
        attestationsByValidatorCount.values();
    int numRemoved = 0;
    for (Iterator<Set<PooledAttestation>> i = attestationSets.iterator(); i.hasNext(); ) {
      final Set<PooledAttestation> candidates = i.next();
      for (Iterator<PooledAttestation> iterator = candidates.iterator(); iterator.hasNext(); ) {
        PooledAttestation candidate = iterator.next();
        if (includedValidators.isSuperSetOf(candidate)) {
          iterator.remove();
          numRemoved++;
        }
      }
      if (candidates.isEmpty()) {
        i.remove();
      }
    }
    return numRemoved;
  }

  public void onReorg(final UInt64 commonAncestorSlot) {
    final NavigableMap<UInt64, AttestationBitsAggregator> removedSlots =
        includedValidatorsBySlot.tailMap(commonAncestorSlot, false);
    if (removedSlots.isEmpty()) {
      // No relevant attestations in affected slots, so nothing to do.
      return;
    }
    removedSlots.clear();
    // Recalculate totalSeenAggregationBits as validators may have been seen in multiple blocks so
    // can't do a simple remove
    includedValidators = createEmptyAttestationBits();
    includedValidatorsBySlot.values().forEach(includedValidators::or);
  }

  public boolean matchesCommitteeShufflingSeed(final Set<Bytes32> validSeeds) {
    return committeeShufflingSeed.map(validSeeds::contains).orElse(false);
  }

  private boolean noMatchingAttestations(
      final Optional<UInt64> committeeIndex, final boolean requiresCommitteeBits) {
    return requiresCommitteeBits != includedValidators.requiresCommitteeBits()
        || noMatchingPreElectraAttestations(committeeIndex);
  }

  private boolean noMatchingPreElectraAttestations(final Optional<UInt64> committeeIndex) {
    return committeeIndex.isPresent()
        && !includedValidators.requiresCommitteeBits()
        && !attestationData.getIndex().equals(committeeIndex.get());
  }

  private class AggregatingIterator implements Iterator<PooledAttestation> {

    private final Optional<UInt64> maybeCommitteeIndex;
    private final AttestationBitsAggregator includedValidators;

    private Iterator<PooledAttestation> remainingAttestations = getRemainingAttestations();

    private AggregatingIterator(final Optional<UInt64> committeeIndex) {
      this.maybeCommitteeIndex = committeeIndex;
      includedValidators = MatchingDataAttestationGroup.this.includedValidators.copy();
    }

    @Override
    public boolean hasNext() {
      if (!remainingAttestations.hasNext()) {
        remainingAttestations = getRemainingAttestations();
      }
      return remainingAttestations.hasNext();
    }

    @Override
    public PooledAttestation next() {
      final AggregateAttestationBuilder builder = new AggregateAttestationBuilder(attestationData);
      remainingAttestations.forEachRemaining(
          candidate -> {
            if (builder.aggregate(candidate)) {
              includedValidators.or(candidate);
            }
          });
      return builder.buildAggregate();
    }

    public Iterator<PooledAttestation> getRemainingAttestations() {
      return attestationsByValidatorCount.values().stream()
          .flatMap(Set::stream)
          .filter(this::isAttestationRelevant)
          .filter(candidate -> !includedValidators.isSuperSetOf(candidate))
          .iterator();
    }

    private boolean isAttestationRelevant(final PooledAttestation candidate) {
      if (!candidate.bits().requiresCommitteeBits()) {
        // Pre-Electra attestation, we always consider all attestations
        return true;
      }

      if (maybeCommitteeIndex.isEmpty()) {
        // we are in block proposal scenario (not filtering by committeeIndex)
        // we will skip single attestations
        return !candidate.isSingleAttestation();
      }

      // we are in committee aggregation scenario
      return candidate.bits().isExclusivelyFromCommittee(maybeCommitteeIndex.get().intValue());
    }
  }
}
