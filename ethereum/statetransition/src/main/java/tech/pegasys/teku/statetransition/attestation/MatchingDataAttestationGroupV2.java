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

import static com.google.common.base.Preconditions.checkArgument;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import java.util.Comparator;
import java.util.Iterator;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.statetransition.attestation.utils.AttestationBits;
import tech.pegasys.teku.statetransition.attestation.utils.TimeLimitingIterator;

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
 *
 * <p>This V2 implementation uses concurrent collections and a ReadWriteLock for thread-safety.
 */
public class MatchingDataAttestationGroupV2 {
  private static final Logger LOG = LogManager.getLogger();

  // Use Concurrent collections and lock for thread safety
  private final ConcurrentNavigableMap<Integer, Set<PooledAttestation>>
      attestationsByValidatorCount =
          new ConcurrentSkipListMap<>(
              Comparator.reverseOrder()); // Most validators first, thread-safe map

  private final ConcurrentMap<Integer, Set<PooledAttestation>> singleAttestationsByCommitteeIndex =
      new ConcurrentHashMap<>();

  private final Spec spec;
  private final AtomicReference<Optional<Bytes32>> committeeShufflingSeedRef =
      new AtomicReference<>(Optional.empty());
  private final AttestationData attestationData;
  private final Optional<Int2IntMap> committeesSize;
  private final LongSupplier nanosSupplier;

  /**
   * Tracks which validators were included in attestations at a given slot on the canonical chain.
   * Uses ConcurrentSkipListMap for concurrent access. AttestationBits instances managed within this
   * map must be handled carefully under lock if mutable.
   *
   * <p>When a reorg occurs we can accurately compute the set of included validators at the common
   * ancestor by removing blocks in slots after the ancestor then recalculating {@link
   * #includedValidators}. Otherwise, we might remove a validator from the included list because it
   * was in a block moved off the canonical chain even though that validator was also included in an
   * earlier block which is still on the canonical chain.
   *
   * <p>Pruning isn't required for this map because the entire attestation group is dropped by
   * {@link AggregatingAttestationPool} once it is too old to be included in blocks.
   */
  private final ConcurrentNavigableMap<UInt64, AttestationBits> includedValidatorsBySlot =
      new ConcurrentSkipListMap<>();

  /**
   * Precalculated combined list of included validators across all blocks. Must be accessed via
   * lock. AttestationBits is mutable, so needs protection.
   */
  private AttestationBits includedValidators;

  private final ReadWriteLock lock = new ReentrantReadWriteLock();
  private final Lock readLock = lock.readLock();
  private final Lock writeLock = lock.writeLock();

  private final boolean earlyDropSingleAttestations;

  public MatchingDataAttestationGroupV2(
      final Spec spec,
      final LongSupplier nanosSupplier,
      final AttestationData attestationData,
      final Optional<Int2IntMap> committeesSize,
      final boolean earlyDropSingleAttestations) {
    this.spec = spec;
    this.attestationData = attestationData;
    this.committeesSize = committeesSize;
    this.includedValidators = createEmptyAttestationBits();
    this.earlyDropSingleAttestations = earlyDropSingleAttestations;
    this.nanosSupplier = nanosSupplier;
  }

  private AttestationBits createEmptyAttestationBits() {
    return AttestationBits.fromEmptyFromAttestationSchema(
        spec.atSlot(attestationData.getSlot()).getSchemaDefinitions().getAttestationSchema(),
        committeesSize);
  }

  public AttestationData getAttestationData() {
    return attestationData;
  }

  public PooledAttestationWithData fillUpAggregation(
      final PooledAttestationWithData attestation, final long timeLimitNanos) {

    final AggregateAttestationBuilder builder = new AggregateAttestationBuilder(true);

    builder.aggregate(attestation.pooledAttestation());

    final Iterator<PooledAttestation> singleAttestationTimeLimitedIterator =
        new TimeLimitingIterator<>(
            nanosSupplier,
            timeLimitNanos,
            singleAttestationsByCommitteeIndex.values().stream().flatMap(Set::stream).iterator(),
            __ -> LOG.info("Time limit reached, while fillingUp single attestation"));

    while (singleAttestationTimeLimitedIterator.hasNext()) {
      builder.aggregate(singleAttestationTimeLimitedIterator.next());
    }

    return new PooledAttestationWithData(attestationData, builder.buildAggregate());
  }

  /**
   * Adds an attestation to this group. When possible, the attestation will be aggregated with
   * others during iteration. Ignores attestations with no new, unseen aggregation bits. Optimized
   * to set the committeeShufflingSeed only once using volatile + double-checked locking.
   *
   * @param attestation the attestation to add
   * @return True if the attestation was added, false otherwise
   */
  public boolean add(
      final PooledAttestation attestation, final Optional<Bytes32> committeeShufflingSeed) {
    readLock.lock();
    try {
      if (includedValidators.isSuperSetOf(attestation.bits())) {
        return false;
      }
    } finally {
      readLock.unlock();
    }

    if (committeeShufflingSeedRef.get().isEmpty()) {
      if (committeeShufflingSeed.isPresent()) {
        // Attempt to atomically set the value *only if* it's currently Optional.empty()
        // This guarantees only one thread succeeds in setting the value.
        committeeShufflingSeedRef.compareAndSet(Optional.empty(), committeeShufflingSeed);
        // We don't need to check the return value of compareAndSet;
        // if it fails, it means another thread already set it, which is fine.
      }
    }

    if (attestation.isSingleAttestation()) {
      final Set<PooledAttestation> singleAttestations =
          singleAttestationsByCommitteeIndex.computeIfAbsent(
              attestation.bits().getFirstCommitteeIndex(), __ -> ConcurrentHashMap.newKeySet());
      return singleAttestations.add(attestation);
    }

    final Set<PooledAttestation> attestations =
        attestationsByValidatorCount.computeIfAbsent(
            attestation.bits().getBitCount(), __ -> ConcurrentHashMap.newKeySet());

    // .add() on the ConcurrentHashMap.KeySetView is thread-safe
    final boolean added = attestations.add(attestation);

    if (earlyDropSingleAttestations && added && attestation.bits().requiresCommitteeBits()) {

      attestation
          .bits()
          .streamCommitteeIndices()
          .forEach(
              committeeIndex -> {
                final Set<PooledAttestation> singleAttestations =
                    singleAttestationsByCommitteeIndex.get(committeeIndex);
                if (singleAttestations != null) {
                  singleAttestations.removeIf(sa -> attestation.bits().isSuperSetOf(sa.bits()));
                }
              });
    }

    return added;
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
  private Iterator<PooledAttestation> createAggregatingIterator(
      final long timeLimitNanos,
      final Supplier<Stream<PooledAttestation>> candidatesStreamSupplier) {
    final AttestationBits includedValidatorsCopy;
    readLock.lock();
    try {
      // Capture a copy of includedValidators under lock for the iterator's isolated use
      includedValidatorsCopy = this.includedValidators.copy();
    } finally {
      readLock.unlock();
    }
    final AggregatingIterator iterator =
        new AggregatingIterator(includedValidatorsCopy, candidatesStreamSupplier);
    if (timeLimitNanos == Long.MAX_VALUE) {
      return iterator;
    }

    return new TimeLimitingIterator<>(
        nanosSupplier,
        timeLimitNanos,
        iterator,
        __ -> LOG.info("Time limit reached, skipping aggregation"));
  }

  public Stream<PooledAttestationWithData> streamForBlockProduction(final long timeLimitNanos) {
    return StreamSupport.stream(
            spliterator(timeLimitNanos, blockProductionCandidatesStreamSupplier()), false)
        .map(
            pooledAttestation -> new PooledAttestationWithData(attestationData, pooledAttestation));
  }

  public Stream<PooledAttestationWithData> streamForAggregationProduction(
      final Optional<UInt64> committeeIndex, final long timeLimitNanos) {
    checkArgument(
        committeeIndex.isPresent() || !includedValidators.requiresCommitteeBits(),
        "Committee index must be present if committee bits are required");

    // we don't care about committeeIndex in pre-Electra, since attestationData has been already
    // determined by
    // attestation_data_root parameter
    final Optional<UInt64> actualCommitteeIndex =
        includedValidators.requiresCommitteeBits() ? committeeIndex : Optional.empty();

    return StreamSupport.stream(
            spliterator(
                timeLimitNanos,
                aggregationProductionCandidatesStreamSupplier(actualCommitteeIndex)),
            false)
        .map(
            pooledAttestation -> new PooledAttestationWithData(attestationData, pooledAttestation));
  }

  public Stream<PooledAttestationWithData> streamForApiRequest(
      final Optional<UInt64> committeeIndex, final boolean requiresCommitteeBits) {
    if (noMatchingAttestations(committeeIndex, requiresCommitteeBits)) {
      return Stream.empty();
    }
    return StreamSupport.stream(
            spliterator(
                Long.MAX_VALUE,
                apiRequestCandidatesStreamSupplier(committeeIndex, requiresCommitteeBits)),
            false)
        .map(
            pooledAttestationBitsAndSignature ->
                new PooledAttestationWithData(attestationData, pooledAttestationBitsAndSignature));
  }

  private Spliterator<PooledAttestation> spliterator(
      final long timeLimitNanos,
      final Supplier<Stream<PooledAttestation>> candidatesStreamSupplier) {
    return Spliterators.spliteratorUnknownSize(
        createAggregatingIterator(timeLimitNanos, candidatesStreamSupplier), 0);
  }

  /**
   * Returns true if there are no attestations in this group.
   *
   * @return true if this group is empty.
   */
  public boolean isEmpty() {
    return attestationsByValidatorCount.isEmpty() && singleAttestationsByCommitteeIndex.isEmpty();
  }

  public int size() {
    return attestationsByValidatorCount.values().stream().mapToInt(Set::size).sum()
        + singleAttestationsByCommitteeIndex.values().stream().mapToInt(Set::size).sum();
  }

  /**
   * Updates includedValidators bits and removes any attestation from this group whose aggregation
   * bits have all been seen. Needs a write lock as it modifies state.
   *
   * @param attestation the attestation to logically remove from the pool.
   */
  public int onAttestationIncludedInBlock(final UInt64 slot, final Attestation attestation) {
    writeLock.lock();
    try {
      // Record validators in attestation as seen in this slot
      includedValidatorsBySlot.compute(
          slot,
          (__, includedValidators) -> {
            if (includedValidators == null) {
              return AttestationBits.of(attestation, committeesSize);
            }
            // Mutate includedValidators aggregator under write lock
            includedValidators.or(attestation);
            return includedValidators;
          });

      // Check if already seen before modifying the main includedValidators
      if (includedValidators.isSuperSetOf(attestation)) {
        // We've already seen and filtered out all of these bits, nothing to do
        return 0;
      }
      // Mutate main includedValidators under write lock
      includedValidators.or(attestation);

      // Calculate size *before* removal for accurate delta.
      final int sizeBefore = size();

      attestationsByValidatorCount
          .entrySet()
          .removeIf(entry -> pruneSupersededPooledAttestations(entry.getValue()));

      singleAttestationsByCommitteeIndex
          .entrySet()
          .removeIf(entry -> pruneSupersededPooledAttestations(entry.getValue()));

      return sizeBefore - size();
    } finally {
      writeLock.unlock();
    }
  }

  private boolean pruneSupersededPooledAttestations(final Set<PooledAttestation> candidates) {
    candidates.removeIf(candidate -> includedValidators.isSuperSetOf(candidate.bits()));
    return candidates.isEmpty();
  }

  public void onReorg(final UInt64 commonAncestorSlot) {
    writeLock.lock();
    try {
      final NavigableMap<UInt64, AttestationBits> removedSlotsView =
          includedValidatorsBySlot.tailMap(commonAncestorSlot, false);
      if (removedSlotsView.isEmpty()) {
        // No relevant attestations in affected slots, so nothing to do.
        return;
      }

      removedSlotsView.clear();

      // Recalculate includedValidators as validators may have been seen in multiple blocks
      includedValidators = createEmptyAttestationBits();
      includedValidatorsBySlot.values().forEach(includedValidators::or);
    } finally {
      writeLock.unlock();
    }
  }

  public boolean isValid(final BeaconState stateAtBlockSlot, final Spec spec) {
    return spec.validateAttestation(stateAtBlockSlot, attestationData).isEmpty();
  }

  public boolean matchesCommitteeShufflingSeed(final Set<Bytes32> validSeeds) {
    return committeeShufflingSeedRef.get().map(validSeeds::contains).orElse(false);
  }

  private boolean noMatchingAttestations(
      final Optional<UInt64> committeeIndex, final boolean requiresCommitteeBits) {
    readLock.lock();
    try {
      return requiresCommitteeBits != includedValidators.requiresCommitteeBits()
          || noMatchingPreElectraAttestations(committeeIndex);
    } finally {
      readLock.unlock();
    }
  }

  private boolean noMatchingPreElectraAttestations(final Optional<UInt64> committeeIndex) {
    // Assumes called under read lock
    return committeeIndex.isPresent()
        && !includedValidators.requiresCommitteeBits()
        && !attestationData.getIndex().equals(committeeIndex.get());
  }

  private Supplier<Stream<PooledAttestation>> blockProductionCandidatesStreamSupplier() {
    return () -> attestationsByValidatorCount.values().stream().flatMap(Set::stream);
  }

  private Supplier<Stream<PooledAttestation>> aggregationProductionCandidatesStreamSupplier(
      final Optional<UInt64> maybeCommitteeIndex) {
    if (maybeCommitteeIndex.isPresent()) {
      // We are in aggregation mode post-Electra.
      // Only consider single attestations from the given committee index
      return () ->
          singleAttestationsByCommitteeIndex
              .getOrDefault(maybeCommitteeIndex.get().intValue(), Set.of())
              .stream();
    }
    return () -> attestationsByValidatorCount.values().stream().flatMap(Set::stream);
  }

  private Supplier<Stream<PooledAttestation>> apiRequestCandidatesStreamSupplier(
      final Optional<UInt64> maybeCommitteeIndex, final boolean requiresCommitteeBits) {
    if (!requiresCommitteeBits) {
      // in pre-electra mode this group has been already checked against committee index
      // so we can just stream everything (single attestations don't exist)
      return () -> attestationsByValidatorCount.values().stream().flatMap(Set::stream);
    }

    // post electra we need a committee matcher if the committee index is specified
    final Predicate<PooledAttestation> committeeMatcher =
        maybeCommitteeIndex
            .<Predicate<PooledAttestation>>map(
                committeeIndex ->
                    (PooledAttestation pooledAttestation) ->
                        pooledAttestation.bits().isFromCommittee(committeeIndex.intValue()))
            .orElse(pooledAttestation -> true);

    return () -> {
      final Stream<PooledAttestation> singleAttestationsStream =
          maybeCommitteeIndex
              .map(
                  committeeIndex ->
                      // Stream single attestations by committee index
                      singleAttestationsByCommitteeIndex
                          .getOrDefault(committeeIndex.intValue(), Set.of())
                          .stream())
              .orElse(
                  // No committee index filter, stream all single attestations
                  singleAttestationsByCommitteeIndex.values().stream().flatMap(Set::stream));
      // stream aggregates first and then single attestations
      return Stream.concat(
          attestationsByValidatorCount.values().stream()
              .flatMap(Set::stream)
              .filter(committeeMatcher),
          singleAttestationsStream);
    };
  }

  private static class AggregatingIterator implements Iterator<PooledAttestation> {
    private final Supplier<Stream<PooledAttestation>> candidatesStreamSupplier;
    private final AttestationBits includedValidators;

    private Iterator<PooledAttestation> remainingAttestations;

    private AggregatingIterator(
        final AttestationBits includedValidatorsCopy,
        final Supplier<Stream<PooledAttestation>> candidatesStreamSupplier) {
      this.candidatesStreamSupplier = candidatesStreamSupplier;
      this.includedValidators = includedValidatorsCopy;
      this.remainingAttestations = getRemainingAttestations();
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
      final AggregateAttestationBuilder builder = new AggregateAttestationBuilder(true);

      remainingAttestations.forEachRemaining(
          candidate -> {
            if (builder.aggregate(candidate)) {
              includedValidators.or(candidate.bits());
            }
          });
      return builder.buildAggregate();
    }

    private Iterator<PooledAttestation> getRemainingAttestations() {

      return candidatesStreamSupplier
          .get()
          .filter(candidate -> !includedValidators.isSuperSetOf(candidate.bits()))
          .iterator();
    }
  }
}
