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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Suppliers;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.SettableGauge;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.AttestationSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.statetransition.attestation.utils.AggregatingAttestationPoolProfiler;
import tech.pegasys.teku.statetransition.attestation.utils.RewardBasedAttestationSorter;
import tech.pegasys.teku.statetransition.attestation.utils.RewardBasedAttestationSorter.AttestationWithRewardInfo;
import tech.pegasys.teku.statetransition.attestation.utils.RewardBasedAttestationSorter.RewardBasedAttestationSorterFactory;
import tech.pegasys.teku.storage.client.RecentChainData;

/**
 * Maintains a pool of attestations. Attestations can be retrieved either for inclusion in a block
 * or as an aggregate to publish as part of the naive attestation aggregation algorithm. In both
 * cases the returned attestations are aggregated to maximise the number of validators that can be
 * included.
 *
 * <p>This V2 implementation uses concurrent collections to reduce contention.
 */
public class AggregatingAttestationPoolV2 implements AggregatingAttestationPool {
  private static final Logger LOG = LogManager.getLogger();

  private final ConcurrentMap<Bytes, MatchingDataAttestationGroupV2> attestationGroupByDataHash =
      new ConcurrentHashMap<>();

  private final ConcurrentNavigableMap<UInt64, Set<Bytes>> dataHashBySlot =
      new ConcurrentSkipListMap<>();

  private final Spec spec;
  private final RecentChainData recentChainData;
  private final SettableGauge sizeGauge;
  private final int maximumAttestationCount;
  private final AggregatingAttestationPoolProfiler aggregatingAttestationPoolProfiler;

  private final long maxBlockAggregationTimeNanos;
  private final long maxTotalBlockAggregationTimeMillis;
  private final boolean earlyDropSingleAttestations;
  private final boolean parallel;

  private final LongSupplier nanosSupplier;

  private final AtomicInteger size = new AtomicInteger(0);

  private final RewardBasedAttestationSorterFactory rewardBasedAttestationSorterFactory;

  public AggregatingAttestationPoolV2(
      final Spec spec,
      final RecentChainData recentChainData,
      final MetricsSystem metricsSystem,
      final int maximumAttestationCount,
      final AggregatingAttestationPoolProfiler aggregatingAttestationPoolProfiler,
      final int maxBlockAggregationTimeMillis,
      final int maxTotalBlockAggregationTimeMillis,
      final boolean earlyDropSingleAttestations,
      final boolean parallel) {
    this.spec = spec;
    this.recentChainData = recentChainData;
    this.sizeGauge =
        SettableGauge.create(
            metricsSystem,
            TekuMetricCategory.BEACON,
            "attestation_pool_size",
            "The number of attestations available to be included in proposed blocks");
    this.maximumAttestationCount = maximumAttestationCount;
    this.aggregatingAttestationPoolProfiler = aggregatingAttestationPoolProfiler;
    this.maxBlockAggregationTimeNanos = maxBlockAggregationTimeMillis * 1_000_000L;
    this.maxTotalBlockAggregationTimeMillis = maxTotalBlockAggregationTimeMillis * 1_000_000L;
    this.earlyDropSingleAttestations = earlyDropSingleAttestations;
    this.parallel = parallel;
    this.nanosSupplier = System::nanoTime;
    this.rewardBasedAttestationSorterFactory =
        new RewardBasedAttestationSorterFactory(spec, nanosSupplier);
  }

  @VisibleForTesting
  public AggregatingAttestationPoolV2(
      final Spec spec,
      final RecentChainData recentChainData,
      final MetricsSystem metricsSystem,
      final int maximumAttestationCount,
      final LongSupplier nanosSupplier,
      final RewardBasedAttestationSorterFactory rewardBasedAttestationSorterFactory) {
    this.spec = spec;
    this.recentChainData = recentChainData;
    this.sizeGauge =
        SettableGauge.create(
            metricsSystem,
            TekuMetricCategory.BEACON,
            "attestation_pool_size",
            "The number of attestations available to be included in proposed blocks");
    this.maximumAttestationCount = maximumAttestationCount;
    this.aggregatingAttestationPoolProfiler = AggregatingAttestationPoolProfiler.NOOP;
    this.maxBlockAggregationTimeNanos = Integer.MAX_VALUE * 1_000_000L;
    this.maxTotalBlockAggregationTimeMillis = Integer.MAX_VALUE * 1_000_000L;
    this.earlyDropSingleAttestations = false;
    this.parallel = false;
    this.nanosSupplier = nanosSupplier;
    this.rewardBasedAttestationSorterFactory = rewardBasedAttestationSorterFactory;
  }

  // No longer synchronized
  @Override
  public void add(final ValidatableAttestation attestation) {
    final Supplier<Optional<BeaconState>> cachingStateSupplier =
        Suppliers.memoize(() -> retrieveState(attestation.getData()));

    final Optional<Int2IntMap> committeesSize =
        attestation
            .getCommitteesSize()
            .or(() -> getCommitteesSize(attestation.getAttestation(), cachingStateSupplier));
    final Optional<List<UInt64>> validatorIndices =
        getValidatorIndices(attestation, cachingStateSupplier);

    if (validatorIndices.isEmpty()) {
      LOG.debug(
          "Validator indices couldn't be retrieved for attestation at slot {}, block root {} and target root {}. Will NOT add this attestation to the pool.",
          attestation.getData().getSlot(),
          attestation.getData().getBeaconBlockRoot(),
          attestation.getData().getTarget().getRoot());
      return;
    }

    getOrCreateAttestationGroup(attestation.getAttestation(), committeesSize)
        .ifPresent(
            attestationGroup ->
                attestationGroup.add(
                    PooledAttestation.fromValidatableAttestation(
                        attestation, validatorIndices.get()),
                    attestation.getCommitteeShufflingSeed()));
  }

  private Optional<Int2IntMap> getCommitteesSize(
      final Attestation attestation, final Supplier<Optional<BeaconState>> stateSupplier) {
    if (attestation.requiresCommitteeBits()) {
      return stateSupplier
          .get()
          .map(state -> spec.getBeaconCommitteesSize(state, attestation.getData().getSlot()));
    }
    return Optional.empty();
  }

  private Optional<List<UInt64>> getValidatorIndices(
      final ValidatableAttestation attestation,
      final Supplier<Optional<BeaconState>> stateSupplier) {
    return attestation
        .getIndexedAttestation()
        .map(indexedAttestation -> indexedAttestation.getAttestingIndices().asListUnboxed())
        .or(
            () ->
                stateSupplier
                    .get()
                    .map(
                        state ->
                            spec.atSlot(attestation.getData().getSlot())
                                .getAttestationUtil()
                                .getAttestingIndices(state, attestation.getAttestation())
                                .intStream()
                                .mapToObj(UInt64::valueOf)
                                .toList()));
  }

  /**
   * @param committeesSize Required for aggregating attestations as per <a
   *     href="https://eips.ethereum.org/EIPS/eip-7549">EIP-7549</a>
   */
  private Optional<MatchingDataAttestationGroupV2> getOrCreateAttestationGroup(
      final Attestation attestation, final Optional<Int2IntMap> committeesSize) {
    final AttestationData attestationData = attestation.getData();
    // if an attestation has committee bits, committees size should have been computed. If this is
    // not the case, we should ignore this attestation and not add it to the pool
    if (attestation.requiresCommitteeBits() && committeesSize.isEmpty()) {
      LOG.debug(
          "Committees size couldn't be retrieved for attestation at slot {}, block root {} and target root {}. Will NOT add this attestation to the pool.",
          attestationData.getSlot(),
          attestationData.getBeaconBlockRoot(),
          attestationData.getTarget().getRoot());
      return Optional.empty();
    }

    final Bytes dataHash = attestationData.hashTreeRoot();

    dataHashBySlot
        .computeIfAbsent(attestationData.getSlot(), __ -> ConcurrentHashMap.newKeySet())
        .add(dataHash);

    final MatchingDataAttestationGroupV2 attestationGroup =
        attestationGroupByDataHash.computeIfAbsent(
            dataHash,
            __ ->
                new MatchingDataAttestationGroupV2(
                    spec,
                    attestationData,
                    committeesSize,
                    earlyDropSingleAttestations)); // Pass spec, data, committeesSize

    return Optional.of(attestationGroup);
  }

  private Optional<BeaconState> retrieveState(final AttestationData attestationData) {
    // we can use the first state of the epoch to get committees for an attestation
    final MiscHelpers miscHelpers = spec.atSlot(attestationData.getSlot()).miscHelpers();
    final Optional<UInt64> maybeEpoch = recentChainData.getCurrentEpoch();
    // the only reason this can happen is we don't have a store yet.
    if (maybeEpoch.isEmpty()) {
      return Optional.empty();
    }
    final UInt64 currentEpoch = maybeEpoch.get();
    final UInt64 attestationEpoch = miscHelpers.computeEpochAtSlot(attestationData.getSlot());

    LOG.debug("currentEpoch {}, attestationEpoch {}", currentEpoch, attestationEpoch);
    if (attestationEpoch.equals(currentEpoch)
        || attestationEpoch.equals(currentEpoch.minusMinZero(1))) {

      try {
        return recentChainData.getBestState().map(SafeFuture::getImmediately);
      } catch (final IllegalStateException e) {
        LOG.debug("Couldn't retrieve state for attestation at slot {}", attestationData.getSlot());
        return Optional.empty();
      }
    }

    // attestation is not from the current or previous epoch
    // this is really an edge case because the current or previous epoch is at least 31 slots
    // and the attestation is only valid for 64 slots, so it may be epoch-2 but not beyond.
    final UInt64 attestationEpochStartSlot = miscHelpers.computeStartSlotAtEpoch(attestationEpoch);
    LOG.debug("State at slot {} needed", attestationEpochStartSlot);
    try {
      // Assuming retrieveStateInEffectAtSlot and getBeaconCommitteesSize are thread-safe
      return recentChainData
          .retrieveStateInEffectAtSlot(attestationEpochStartSlot)
          .getImmediately();
    } catch (final IllegalStateException e) {
      LOG.debug(
          "Couldn't retrieve state in effect at slot {} for attestation at slot {}",
          attestationEpochStartSlot,
          attestationData.getSlot());
      return Optional.empty();
    }
  }

  @Override
  public void onSlot(final UInt64 slot) {
    final int currentActualSize =
        attestationGroupByDataHash.values().stream()
            .mapToInt(MatchingDataAttestationGroupV2::size)
            .sum();

    size.set(currentActualSize);
    sizeGauge.set(currentActualSize);

    LOG.trace("Attestation pool size recalculated to {}", currentActualSize);

    if (slot.isGreaterThan(ATTESTATION_RETENTION_SLOTS)) {
      final UInt64 firstValidAttestationSlot = slot.minus(ATTESTATION_RETENTION_SLOTS);
      removeAttestationsPriorToSlot(firstValidAttestationSlot);
    }

    int sizeForPruningCheck = currentActualSize; // Use the size calculated at the start of onSlot
    while (dataHashBySlot.size() > 1 && sizeForPruningCheck > maximumAttestationCount) {
      LOG.trace(
          "V2 Attestation cache at {} (pre-prune estimate) exceeds {}. Pruning...",
          sizeForPruningCheck,
          maximumAttestationCount);
      final UInt64 oldestSlot = dataHashBySlot.firstKey();
      if (oldestSlot == null) {
        break;
      }

      // Estimate the size reduction (since removeAttestationsPriorToSlot no longer updates 'size')
      // This is tricky because group.size() is approximate.
      // We might need to actually get the groups to be removed and sum their sizes *before*
      // removal.
      int estimatedRemovalCount = 0;
      final Set<Bytes> hashesToRemove =
          dataHashBySlot.getOrDefault(oldestSlot.plus(1), Set.of()); // Check slot *after* oldest
      for (final Bytes hash : hashesToRemove) {
        MatchingDataAttestationGroupV2 group = attestationGroupByDataHash.get(hash);
        if (group != null) {
          estimatedRemovalCount += group.size();
        }
      }

      removeAttestationsPriorToSlot(oldestSlot.plus(1)); // Remove the items

      if (estimatedRemovalCount == 0) {
        // If we estimated 0 removed, or failed to find the slot, break to avoid potential infinite
        // loop
        LOG.warn(
            "Failed to prune oldest slot {} or estimated 0 removals. Skipping further pruning this cycle.",
            oldestSlot);
        break;
      }
      sizeForPruningCheck -= estimatedRemovalCount;
    }

    aggregatingAttestationPoolProfiler.execute(spec, slot, recentChainData, this);
  }

  private void removeAttestationsPriorToSlot(final UInt64 firstValidAttestationSlot) {
    final NavigableMap<UInt64, Set<Bytes>> headMap =
        dataHashBySlot.headMap(firstValidAttestationSlot, false);
    final List<UInt64> slotsToRemove = List.copyOf(headMap.keySet());

    if (slotsToRemove.isEmpty()) {
      return;
    }

    LOG.trace(
        "V2 Pruning attestations before slot {}. Slots to remove: {}",
        firstValidAttestationSlot,
        slotsToRemove.size());

    for (final UInt64 slot : slotsToRemove) {
      final Set<Bytes> dataHashes = dataHashBySlot.remove(slot);
      if (dataHashes != null) {
        dataHashes.forEach(attestationGroupByDataHash::remove);
      }
    }
  }

  @Override
  public void onAttestationsIncludedInBlock(
      final UInt64 slot, final Iterable<Attestation> attestations) {
    attestations.forEach(attestation -> onAttestationIncludedInBlock(slot, attestation));
  }

  private void onAttestationIncludedInBlock(final UInt64 slot, final Attestation attestation) {
    getOrCreateAttestationGroup(
            attestation, getCommitteesSize(attestation, () -> retrieveState(attestation.getData())))
        .ifPresent(
            attestationGroup -> {
              // MatchingDataAttestationGroupV2 must handle concurrency internally
              final int numRemoved =
                  attestationGroup.onAttestationIncludedInBlock(slot, attestation);
              if (numRemoved > 0) {
                updateSize(-numRemoved);
              }
            });
  }

  private void updateSize(final int delta) {
    if (delta != 0) {
      final int currentSize = size.addAndGet(delta);
      sizeGauge.set(currentSize);
    }
  }

  @Override
  public int getSize() {
    return size.get();
  }

  @Override
  public SszList<Attestation> getAttestationsForBlock(
      final BeaconState stateAtBlockSlot, final AttestationForkChecker forkChecker) {
    final UInt64 currentEpoch = spec.getCurrentEpoch(stateAtBlockSlot);
    final int previousEpochLimit = spec.getPreviousEpochAttestationCapacity(stateAtBlockSlot);

    final RewardBasedAttestationSorter rewardBasedAttestationSorter =
        rewardBasedAttestationSorterFactory.create(stateAtBlockSlot);
    final SchemaDefinitions schemaDefinitions =
        spec.atSlot(stateAtBlockSlot.getSlot()).getSchemaDefinitions();

    final SszListSchema<Attestation, ?> attestationsSchema =
        schemaDefinitions.getBeaconBlockBodySchema().getAttestationsSchema();

    final AttestationSchema<Attestation> attestationSchema =
        schemaDefinitions.getAttestationSchema();

    final boolean blockRequiresAttestationsWithCommitteeBits =
        attestationSchema.requiresCommitteeBits();

    final AtomicInteger prevEpochCount = new AtomicInteger(0);

    final long nowNanos = nanosSupplier.getAsLong();
    final long totalTimeLimitNanos = nowNanos + maxTotalBlockAggregationTimeMillis;
    final long aggregationTimeLimit = nowNanos + maxBlockAggregationTimeNanos;

    var dataHashes =
        dataHashBySlot
            // We can immediately skip any attestations from the block slot or later
            .headMap(stateAtBlockSlot.getSlot(), false)
            .descendingMap() // Safe view
            .values();

    var fullAggregationStart = nanosSupplier.getAsLong();
    var aggregates =
        (parallel ? dataHashes.parallelStream() : dataHashes.stream())
            .flatMap(
                dataHashSetForSlot ->
                    streamAggregatesForDataHashesBySlot(
                        dataHashSetForSlot, // dataHashSetForSlot is expected to be a Concurrent Set
                        stateAtBlockSlot,
                        forkChecker,
                        blockRequiresAttestationsWithCommitteeBits,
                        aggregationTimeLimit))
            .filter(
                attestation -> {
                  if (spec.computeEpochAtSlot(attestation.data().getSlot())
                      .isLessThan(currentEpoch)) {
                    final int currentCount = prevEpochCount.getAndIncrement();
                    return currentCount < previousEpochLimit;
                  }
                  return true;
                })
            .toList();

    LOG.info(
        "Aggregation phase took {} ms. Produced {} aggregations.",
        (nanosSupplier.getAsLong() - fullAggregationStart) / 1_000_000,
        aggregates.size());
    var sortedAggregates =
        rewardBasedAttestationSorter.sort(
            aggregates, Math.toIntExact(attestationsSchema.getMaxLength()));

    return (parallel ? sortedAggregates.parallelStream() : sortedAggregates.stream())
        .peek(
            attestation ->
                aggregatingAttestationPoolProfiler.onPreFillUp(stateAtBlockSlot, attestation))
        .map(
            validatableAttestation ->
                fillUpAttestation(
                    stateAtBlockSlot.getSlot(), validatableAttestation, totalTimeLimitNanos))
        .peek(
            attestation ->
                aggregatingAttestationPoolProfiler.onPostFillUp(stateAtBlockSlot, attestation))
        .map(
            validatableAttestationWithSortingReward ->
                validatableAttestationWithSortingReward
                    .getAttestation()
                    .toAttestation(attestationSchema))
        .collect(attestationsSchema.collector());
  }

  private AttestationWithRewardInfo fillUpAttestation(
      final UInt64 slot,
      final AttestationWithRewardInfo attestationWithRewards,
      final long timeLimitNanos) {
    if (nanosSupplier.getAsLong() > timeLimitNanos) {
      LOG.info("Time limit reached, skipping fillUpAttestation");
      return attestationWithRewards;
    }

    var attestation = attestationWithRewards.getAttestation();
    return Optional.ofNullable(attestationGroupByDataHash.get(attestation.data().hashTreeRoot()))
        .map(
            group ->
                attestationWithRewards.withAttestation(
                    group.fillUpAggregation(slot, attestation, timeLimitNanos)))
        .orElse(attestationWithRewards);
  }

  private Stream<PooledAttestationWithData> streamAggregatesForDataHashesBySlot(
      final Set<Bytes> dataHashSetForSlot, // Assumed concurrent set
      final BeaconState stateAtBlockSlot,
      final AttestationForkChecker forkChecker,
      final boolean blockRequiresAttestationsWithCommitteeBits,
      final long baseAggregationTimeLimitNanos) {

    return dataHashSetForSlot.stream()
        .map(attestationGroupByDataHash::get)
        .filter(Objects::nonNull)
        .filter(group -> group.isValid(stateAtBlockSlot, spec))
        .filter(forkChecker::areAttestationsFromCorrectForkV2)
        .flatMap(group -> group.stream(baseAggregationTimeLimitNanos))
        .filter(
            attestation ->
                attestation.pooledAttestation().bits().requiresCommitteeBits()
                    == blockRequiresAttestationsWithCommitteeBits);
  }

  @Override
  public List<Attestation> getAttestations(
      final Optional<UInt64> maybeSlot, final Optional<UInt64> maybeCommitteeIndex) {

    final Predicate<Map.Entry<UInt64, Set<Bytes>>> filterForSlot =
        (entry) -> maybeSlot.map(slot -> entry.getKey().equals(slot)).orElse(true);

    final UInt64 slot = maybeSlot.orElse(recentChainData.getCurrentSlot().orElse(UInt64.ZERO));
    final SchemaDefinitions schemaDefinitions = spec.atSlot(slot).getSchemaDefinitions();
    final AttestationSchema<Attestation> attestationSchema =
        schemaDefinitions.getAttestationSchema();
    final boolean requiresCommitteeBits = attestationSchema.requiresCommitteeBits();

    return dataHashBySlot.descendingMap().entrySet().stream()
        .filter(filterForSlot)
        .map(Map.Entry::getValue)
        .flatMap(Collection::stream)
        .map(attestationGroupByDataHash::get)
        .filter(Objects::nonNull)
        .flatMap(
            matchingDataAttestationGroup ->
                matchingDataAttestationGroup.stream(maybeCommitteeIndex, requiresCommitteeBits))
        .map(pooledAttestation -> pooledAttestation.toAttestation(attestationSchema))
        .toList();
  }

  @Override
  public Optional<Attestation> createAggregateFor(
      final Bytes32 attestationHashTreeRoot, final Optional<UInt64> committeeIndex) {

    final MatchingDataAttestationGroupV2 group =
        attestationGroupByDataHash.get(attestationHashTreeRoot);
    if (group == null) {
      return Optional.empty();
    }

    final SchemaDefinitions schemaDefinitions =
        spec.atSlot(group.getAttestationData().getSlot()).getSchemaDefinitions();
    final AttestationSchema<Attestation> attestationSchema =
        schemaDefinitions.getAttestationSchema();

    return group.stream(committeeIndex, Long.MAX_VALUE)
        .findFirst()
        .map(pooledAttestation -> pooledAttestation.toAttestation(attestationSchema));
  }

  @Override
  public void onReorg(final UInt64 commonAncestorSlot) {
    attestationGroupByDataHash.values().forEach(group -> group.onReorg(commonAncestorSlot));
  }
}
