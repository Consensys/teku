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
import java.util.Comparator;
import java.util.Iterator;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
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
 *
 * <p>This V2 implementation uses concurrent collections and a ReadWriteLock for thread-safety.
 */
public class MatchingDataAttestationGroupV2 implements MatchingDataAttestationGroup {

  // Use Concurrent collections and lock for thread safety
  private final ConcurrentNavigableMap<Integer, Set<ValidatableAttestation>>
      attestationsByValidatorCount =
          new ConcurrentSkipListMap<>(
              Comparator.reverseOrder()); // Most validators first, thread-safe map

  private final Spec spec;
  // Make the field volatile for safe publication and reads without lock after first write
  private volatile Optional<Bytes32> committeeShufflingSeed = Optional.empty();
  private final AttestationData attestationData;
  private final Optional<Int2IntMap> committeesSize;

  /**
   * Tracks which validators were included in attestations at a given slot on the canonical chain.
   * Uses ConcurrentSkipListMap for concurrent access. AttestationBitsAggregator instances managed
   * within this map must be handled carefully under lock if mutable.
   */
  private final ConcurrentNavigableMap<UInt64, AttestationBitsAggregator> includedValidatorsBySlot =
      new ConcurrentSkipListMap<>();

  /**
   * Precalculated combined list of included validators across all blocks. Must be accessed via
   * lock. AttestationBitsAggregator is mutable, so needs protection.
   */
  private AttestationBitsAggregator includedValidators;

  // Lock for protecting mutable state (committeeShufflingSeed, includedValidators, and mutations
  // to AttestationBitsAggregator instances within includedValidatorsBySlot)
  private final ReadWriteLock lock = new ReentrantReadWriteLock();
  private final Lock readLock = lock.readLock();
  private final Lock writeLock = lock.writeLock();

  public MatchingDataAttestationGroupV2(
      final Spec spec,
      final AttestationData attestationData,
      final Optional<Int2IntMap> committeesSize) {
    this.spec = spec;
    this.attestationData = attestationData;
    this.committeesSize = committeesSize;
    // Initialization happens before concurrent access is possible
    this.includedValidators = createEmptyAttestationBits();
  }

  private AttestationBitsAggregator createEmptyAttestationBits() {
    // Assumes schema lookup is safe here
    return AttestationBitsAggregator.fromEmptyFromAttestationSchema(
        spec.atSlot(attestationData.getSlot()).getSchemaDefinitions().getAttestationSchema(),
        committeesSize);
  }

  @Override
  public AttestationData getAttestationData() {
    // AttestationData is immutable, safe to return without lock
    return attestationData;
  }

  /**
   * Adds an attestation to this group. When possible, the attestation will be aggregated with
   * others during iteration. Ignores attestations with no new, unseen aggregation bits. Optimized
   * to set the committeeShufflingSeed only once using volatile + double-checked locking.
   *
   * @param attestation the attestation to add
   * @return True if the attestation was added (contributed new information), false otherwise
   */
  @Override
  public boolean add(final ValidatableAttestation attestation) {
    // --- Read Lock Section (Only for includedValidators check) ---
    readLock.lock();
    try {
      if (includedValidators.isSuperSetOf(attestation.getAttestation())) {
        return false; // Fast exit if redundant
      }
    } finally {
      readLock.unlock();
    }
    // --- End Read Lock Section ---

    // --- Set Seed (Set-Once Optimization using volatile + double-checked lock) ---
    // Perform cheap volatile read first. If seed is already set, skip locking.
    if (committeeShufflingSeed.isEmpty()) {
      // Only try to set the seed if the attestation actually has one
      Optional<Bytes32> attestationSeedMaybe = attestation.getCommitteeShufflingSeed();
      if (attestationSeedMaybe.isPresent()) {
        // Acquire write lock only if seed is potentially unset AND attestation has a seed
        writeLock.lock();
        try {
          // Double-check under write lock to ensure only one thread sets it
          if (committeeShufflingSeed.isEmpty()) {
            committeeShufflingSeed = attestationSeedMaybe; // Set the seed
          }
          // If another thread set it between volatile read and lock acquisition,
          // this check prevents overwriting. If attestationSeedMaybe was empty,
          // we wouldn't acquire the lock anyway.
        } finally {
          writeLock.unlock();
        }
      }
      // If committeeShufflingSeed is still empty here, it means either:
      // 1. This attestation (and potentially others before it) didn't have a seed.
      // 2. Another thread set it while we were waiting for the lock.
      // Both outcomes are correct.
    }
    // --- End Set Seed ---

    // --- Add to Concurrent Collection (Outside Lock) ---
    // Add to the concurrent map - computeIfAbsent handles concurrency here
    final Set<ValidatableAttestation> attestations =
        attestationsByValidatorCount.computeIfAbsent(
            attestation.getAttestation().getAggregationBits().getBitCount(),
            __ -> ConcurrentHashMap.newKeySet());

    // .add() on the ConcurrentHashMap.KeySetView is thread-safe
    final boolean addedToSet = attestations.add(attestation);
    // --- End Add to Concurrent Collection ---

    // Return true if it wasn't redundant based on includedValidators check AND
    // was successfully added to the underlying set.
    return addedToSet;
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
  public Iterator<ValidatableAttestation> iterator() {
    // Create iterator with necessary state captured under lock
    return createAggregatingIterator(Optional.empty());
  }

  @Override
  public Iterator<ValidatableAttestation> iterator(final Optional<UInt64> committeeIndex) {
    // Create iterator with necessary state captured under lock
    return createAggregatingIterator(committeeIndex);
  }

  private Iterator<ValidatableAttestation> createAggregatingIterator(
      final Optional<UInt64> committeeIndex) {
    readLock.lock();
    try {
      // Capture a copy of includedValidators under lock for the iterator's isolated use
      AttestationBitsAggregator includedValidatorsCopy = this.includedValidators.copy();

      return new AggregatingIteratorV2(committeeIndex, includedValidatorsCopy);
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public Stream<ValidatableAttestation> stream() {
    return StreamSupport.stream(spliterator(Optional.empty()), false);
  }

  @Override
  public Stream<ValidatableAttestation> stream(final Optional<UInt64> committeeIndex) {
    return StreamSupport.stream(spliterator(committeeIndex), false);
  }

  @Override
  public Stream<ValidatableAttestation> stream(
      final Optional<UInt64> committeeIndex, final boolean requiresCommitteeBits) {
    // Read lock needed for accessing includedValidators properties
    boolean noMatch;
    readLock.lock();
    try {
      noMatch = noMatchingAttestations(committeeIndex, requiresCommitteeBits);
    } finally {
      readLock.unlock();
    }
    if (noMatch) {
      return Stream.empty();
    }
    return StreamSupport.stream(spliterator(committeeIndex), false);
  }

  @Override
  public Spliterator<ValidatableAttestation> spliterator(final Optional<UInt64> committeeIndex) {
    // Delegate to iterator creation which handles locking
    return Spliterators.spliteratorUnknownSize(iterator(committeeIndex), 0);
  }

  /**
   * Returns true if there are no attestations in this group.
   *
   * @return true if this group is empty.
   */
  @Override
  public boolean isEmpty() {
    // isEmpty() on ConcurrentSkipListMap is thread-safe and fast
    return attestationsByValidatorCount.isEmpty();
  }

  @Override
  public int size() {
    // Iterate over the values (concurrent sets) and sum their sizes.
    // This gives an approximate size at a moment in time, acceptable for metrics.
    // No lock needed as iterating values and set.size() are safe.
    return attestationsByValidatorCount.values().stream().mapToInt(Set::size).sum();
  }

  /**
   * Updates {@code seenAggregationBits} and removes any attestation from this group whose
   * aggregation bits have all been seen. Needs write lock as it modifies state.
   *
   * @param attestation the attestation to logically remove from the pool.
   */
  @Override
  public int onAttestationIncludedInBlock(final UInt64 slot, final Attestation attestation) {
    int numRemoved;
    writeLock.lock();
    try {
      // Record validators in attestation as seen in this slot
      includedValidatorsBySlot.compute(
          slot,
          (__, existingAggregator) -> {
            if (existingAggregator == null) {
              return AttestationBitsAggregator.of(attestation, committeesSize);
            }
            // Mutate existing aggregator under write lock
            existingAggregator.or(attestation);
            return existingAggregator;
          });

      // Check if already seen before modifying the main includedValidators
      if (includedValidators.isSuperSetOf(attestation)) {
        // We've already seen and filtered out all of these bits, nothing to do
        return 0;
      }
      // Mutate main includedValidators under write lock
      includedValidators.or(attestation);

      // Calculate size *before* removal for accurate delta.
      int sizeBefore = attestationsByValidatorCount.values().stream().mapToInt(Set::size).sum();

      // Remove fully covered attestations using removeIf on the concurrent sets
      attestationsByValidatorCount
          .values()
          .forEach(
              candidates ->
                  candidates.removeIf(
                      candidate -> includedValidators.isSuperSetOf(candidate.getAttestation())));

      // Also remove empty sets from the outer map for cleanup
      attestationsByValidatorCount.entrySet().removeIf(entry -> entry.getValue().isEmpty());

      int sizeAfter = attestationsByValidatorCount.values().stream().mapToInt(Set::size).sum();
      numRemoved = sizeBefore - sizeAfter;

    } finally {
      writeLock.unlock();
    }
    return numRemoved; // Return the count accurately calculated under lock
  }

  @Override
  public void onReorg(final UInt64 commonAncestorSlot) {
    writeLock.lock();
    try {
      // tailMap is a view, need to collect keys/entries before clearing
      final NavigableMap<UInt64, AttestationBitsAggregator> removedSlotsView =
          includedValidatorsBySlot.tailMap(commonAncestorSlot, false);
      if (removedSlotsView.isEmpty()) {
        // No relevant attestations in affected slots, so nothing to do.
        return;
      }
      // Clear the view, which removes entries from the underlying concurrent map
      removedSlotsView.clear();

      // Recalculate totalSeenAggregationBits as validators may have been seen in multiple blocks
      includedValidators = createEmptyAttestationBits(); // Re-initialize
      // Iterate safely over remaining values and aggregate under lock
      includedValidatorsBySlot.values().forEach(aggregator -> includedValidators.or(aggregator));
    } finally {
      writeLock.unlock();
    }
  }


  @Override
  public boolean isValid(final BeaconState stateAtBlockSlot, final Spec spec) {
    return spec.validateAttestation(stateAtBlockSlot, attestationData).isEmpty();
  }

  @Override
  public boolean matchesCommitteeShufflingSeed(final Set<Bytes32> validSeeds) {
    // Volatile read is safe without needing a lock here
    return committeeShufflingSeed.map(validSeeds::contains).orElse(false);
  }

  private boolean noMatchingAttestations(
      final Optional<UInt64> committeeIndex, final boolean requiresCommitteeBits) {
    // Assumes called under read lock already by stream(...)
    return requiresCommitteeBits != includedValidators.requiresCommitteeBits()
        || noMatchingPreElectraAttestations(committeeIndex);
  }

  private boolean noMatchingPreElectraAttestations(final Optional<UInt64> committeeIndex) {
    // Assumes called under read lock already by noMatchingAttestations
    return committeeIndex.isPresent()
        && !includedValidators.requiresCommitteeBits() // Read under caller's lock
        && !attestationData.getIndex().equals(committeeIndex.get()); // immutable data access
  }

  private class AggregatingIteratorV2 implements Iterator<ValidatableAttestation> {

    private final Optional<UInt64> maybeCommitteeIndex;
    // Holds a *copy* of the state from the time of creation, safe for local mutation.
    private final AttestationBitsAggregator iteratorSpecificIncludedValidators;

    private Iterator<ValidatableAttestation> remainingAttestations;

    // Constructor receives the copy made under lock
    private AggregatingIteratorV2(
        final Optional<UInt64> committeeIndex,
        final AttestationBitsAggregator includedValidatorsCopy) {
      this.maybeCommitteeIndex = committeeIndex;
      this.iteratorSpecificIncludedValidators = includedValidatorsCopy; // Use the provided copy
      this.remainingAttestations = getRemainingAttestations();
    }

    @Override
    public boolean hasNext() {
      // If current iterator exhausted, try fetching remaining ones again
      if (!remainingAttestations.hasNext()) {
        remainingAttestations = getRemainingAttestations();
      }
      return remainingAttestations.hasNext();
    }

    @Override
    public ValidatableAttestation next() {
      final AggregateAttestationBuilder builder =
          new AggregateAttestationBuilder(spec, attestationData);

      // No lock needed here as we operate on iterator-local state and read concurrent map
      remainingAttestations.forEachRemaining(
          candidate -> {
            if (builder.aggregate(candidate)) {
              iteratorSpecificIncludedValidators.or(candidate.getAttestation());
            }
          });
      return builder.buildAggregate();
    }

    // Fetches attestations not yet covered *by this iterator*
    private Iterator<ValidatableAttestation> getRemainingAttestations() {
      // Stream over the concurrent map's values (concurrent sets)
      // Filter based on the iterator's local includedValidators copy
      // No lock needed for this read operation
      // Use outer class field attestationsByValidatorCount
      return MatchingDataAttestationGroupV2.this
          .attestationsByValidatorCount
          .values()
          .stream()
          .flatMap(Set::stream) // streams the concurrent set safely
          .filter(this::isAttestationRelevant)
          // Check against the iterator's local copy of included validators
          .filter(
              candidate ->
                  !iteratorSpecificIncludedValidators.isSuperSetOf(candidate.getAttestation()))
          .iterator();
    }

    // Checks relevance based on committee index, reads attestation data (immutable)
    private boolean isAttestationRelevant(final ValidatableAttestation candidate) {
      // No lock needed, reads immutable parts of candidate
      final Optional<SszBitvector> maybeCommitteeBits =
          candidate.getAttestation().getCommitteeBits();
      if (maybeCommitteeBits.isEmpty()) {
        // Pre-Electra attestation, always relevant initially
        return true;
      }

      if (maybeCommitteeIndex.isEmpty()) {
        // Block proposal scenario (no committee filter) - consider all Electra attestations
        return !candidate.getUnconvertedAttestation().isSingleAttestation();
      }

      // Committee aggregation scenario
      final SszBitvector committeeBits = maybeCommitteeBits.get();
      if (!committeeBits.isSet(maybeCommitteeIndex.get().intValue())) {
        // Must match the specific committee index
        return false;
      }

      // Aggregate only single-committee attestations in this scenario
      return committeeBits.getBitCount() == 1;
    }
  }
}
