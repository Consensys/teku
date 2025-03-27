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

import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.metrics.SettableGauge;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.storage.client.RecentChainData;

/**
 * Maintains a pool of attestations. Attestations can be retrieved either for inclusion in a block
 * or as an aggregate to publish as part of the naive attestation aggregation algorithm. In both
 * cases the returned attestations are aggregated to maximise the number of validators that can be
 * included.
 */
public class AggregatingAttestationPool implements SlotEventsChannel {
  private static final Logger LOG = LogManager.getLogger();

  /** The valid attestation retention period is 64 slots in deneb */
  static final long ATTESTATION_RETENTION_SLOTS = 64;

  static final Comparator<Attestation> ATTESTATION_INCLUSION_COMPARATOR =
      Comparator.<Attestation>comparingInt(
              attestation -> attestation.getAggregationBits().getBitCount())
          .reversed();

  /**
   * Default maximum number of attestations to store in the pool.
   *
   * <p>With 2 million active validators, we'd expect around 62_500 attestations per slot; so 3
   * slots worth of attestations is almost 187_500.
   *
   * <p>Strictly to cache all attestations for a full 2 epochs is significantly larger than this
   * cache.
   */
  public static final int DEFAULT_MAXIMUM_ATTESTATION_COUNT = 187_500;

  private final Map<Bytes32, MatchingDataAttestationGroup> attestationGroupByDataHash =
      new ConcurrentSkipListMap<>();
  private final NavigableMap<UInt64, Set<Bytes32>> dataHashBySlot = new ConcurrentSkipListMap<>();

  private final Spec spec;
  private final RecentChainData recentChainData;
  private final SettableGauge sizeGauge;
  private final int maximumAttestationCount;
  final AtomicBoolean isCleanupRunning = new AtomicBoolean(false);

  private final AtomicInteger size = new AtomicInteger(0);

  public AggregatingAttestationPool(
      final Spec spec,
      final RecentChainData recentChainData,
      final MetricsSystem metricsSystem,
      final int maximumAttestationCount) {
    this.spec = spec;
    this.recentChainData = recentChainData;
    this.sizeGauge =
        SettableGauge.create(
            metricsSystem,
            TekuMetricCategory.BEACON,
            "attestation_pool_size",
            "The number of attestations available to be included in proposed blocks");
    this.maximumAttestationCount = maximumAttestationCount;
  }

  public void add(final ValidatableAttestation attestation) {
    final Optional<Int2IntMap> committeesSize =
        attestation.getCommitteesSize().or(() -> getCommitteesSize(attestation.getAttestation()));

    getOrCreateAttestationGroup(attestation.getAttestation(), committeesSize)
        .ifPresent(
            attestationGroup -> {
              final boolean added = attestationGroup.add(attestation);
              if (added) {
                updateSize(1);
              }
            });

    cleanupCache(Optional.empty());
  }

  private Optional<Int2IntMap> getCommitteesSize(final Attestation attestation) {
    if (attestation.requiresCommitteeBits()) {
      return getCommitteesSizeUsingTheState(attestation.getData());
    }
    return Optional.empty();
  }

  /**
   * @param committeesSize Required for aggregating attestations as per <a
   *     href="https://eips.ethereum.org/EIPS/eip-7549">EIP-7549</a>
   */
  private Optional<MatchingDataAttestationGroup> getOrCreateAttestationGroup(
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
    return maybeCreateAttestationGroup(attestationData, committeesSize);
  }

  private synchronized Optional<MatchingDataAttestationGroup> maybeCreateAttestationGroup(
      final AttestationData attestationData, final Optional<Int2IntMap> committeesSize) {
    dataHashBySlot
        .computeIfAbsent(attestationData.getSlot(), slot -> Sets.newConcurrentHashSet())
        .add(attestationData.hashTreeRoot());
    final MatchingDataAttestationGroup attestationGroup =
        attestationGroupByDataHash.computeIfAbsent(
            attestationData.hashTreeRoot(),
            key -> new MatchingDataAttestationGroup(spec, attestationData, committeesSize));
    return Optional.of(attestationGroup);
  }

  private Optional<Int2IntMap> getCommitteesSizeUsingTheState(
      final AttestationData attestationData) {
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

      return recentChainData
          .getBestState()
          .flatMap(
              state -> {
                try {
                  return Optional.of(
                      spec.getBeaconCommitteesSize(
                          state.getImmediately(), attestationData.getSlot()));
                } catch (IllegalStateException e) {
                  LOG.debug(
                      "Couldn't retrieve state for committee calculation of slot {}",
                      attestationData.getSlot());
                  return Optional.empty();
                }
              });
    }

    // attestation is not from the current or previous epoch
    // this is really an edge case because the current or previous epoch is at least 31 slots
    // and the attestation is only valid for 64 slots, so it may be epoch-2 but not beyond.
    final UInt64 attestationEpochStartSlot = miscHelpers.computeStartSlotAtEpoch(attestationEpoch);
    LOG.debug("State at slot {} needed", attestationEpochStartSlot);
    try {
      return recentChainData
          .retrieveStateInEffectAtSlot(attestationEpochStartSlot)
          .getImmediately()
          .map(state -> spec.getBeaconCommitteesSize(state, attestationData.getSlot()));
    } catch (final IllegalStateException e) {
      LOG.debug(
          "Couldn't retrieve state in effect at slot {} for committee calculation of slot {}",
          attestationEpochStartSlot,
          attestationData.getSlot());
      return Optional.empty();
    }
  }

  @Override
  public void onSlot(final UInt64 slot) {
    if (slot.isLessThan(ATTESTATION_RETENTION_SLOTS)) {
      return;
    }
    cleanupCache(Optional.of(slot.minus(ATTESTATION_RETENTION_SLOTS)));
  }

  public void onAttestationsIncludedInBlock(
      final UInt64 slot, final Iterable<Attestation> attestations) {
    final Optional<UInt64> maybeCurrentSlot = recentChainData.getCurrentSlot();
    if (maybeCurrentSlot.isEmpty()
        || maybeCurrentSlot.get().minusMinZero(slot).isGreaterThan(ATTESTATION_RETENTION_SLOTS)) {
      LOG.trace(
          "Attestations included in block at slot {}, head slot {} - skipping.",
          slot,
          maybeCurrentSlot);
      return;
    }
    attestations.forEach(attestation -> onAttestationIncludedInBlock(slot, attestation));
  }

  private void onAttestationIncludedInBlock(final UInt64 slot, final Attestation attestation) {
    getOrCreateAttestationGroup(attestation, getCommitteesSize(attestation))
        .ifPresent(
            attestationGroup -> {
              final int numRemoved =
                  attestationGroup.onAttestationIncludedInBlock(slot, attestation);
              updateSize(-numRemoved);
            });
  }

  private void updateSize(final int delta) {
    final int currentSize = size.addAndGet(delta);
    sizeGauge.set(currentSize);
  }

  public int getSize() {
    return size.get();
  }

  public SszList<Attestation> getAttestationsForBlock(
      final BeaconState stateAtBlockSlot, final AttestationForkChecker forkChecker) {
    final UInt64 currentEpoch = spec.getCurrentEpoch(stateAtBlockSlot);
    final int previousEpochLimit = spec.getPreviousEpochAttestationCapacity(stateAtBlockSlot);

    final SchemaDefinitions schemaDefinitions =
        spec.atSlot(stateAtBlockSlot.getSlot()).getSchemaDefinitions();

    final SszListSchema<Attestation, ?> attestationsSchema =
        schemaDefinitions.getBeaconBlockBodySchema().getAttestationsSchema();

    final boolean blockRequiresAttestationsWithCommitteeBits =
        schemaDefinitions.getAttestationSchema().requiresCommitteeBits();

    final AtomicInteger prevEpochCount = new AtomicInteger(0);

    return dataHashBySlot
        // We can immediately skip any attestations from the block slot or later
        .headMap(stateAtBlockSlot.getSlot(), false)
        .descendingMap()
        .values()
        .stream()
        .flatMap(
            dataHashSetForSlot ->
                streamAggregatesForDataHashesBySlot(
                    dataHashSetForSlot,
                    stateAtBlockSlot,
                    forkChecker,
                    blockRequiresAttestationsWithCommitteeBits))
        .limit(attestationsSchema.getMaxLength())
        .filter(
            attestation -> {
              if (spec.computeEpochAtSlot(attestation.getData().getSlot())
                  .isLessThan(currentEpoch)) {
                final int currentCount = prevEpochCount.getAndIncrement();
                return currentCount < previousEpochLimit;
              }
              return true;
            })
        .collect(attestationsSchema.collector());
  }

  void cleanupCache(final Optional<UInt64> maybeSlot) {
    // one cleanup at a time can run
    if (!isCleanupRunning.compareAndSet(false, true)) {
      return;
    }

    try {
      if (maybeSlot.isEmpty()) {
        while (dataHashBySlot.size() > 1 && size.get() > maximumAttestationCount) {
          LOG.trace("Attestation cache at {} exceeds {}, ", size.get(), maximumAttestationCount);
          removeAttestationsPriorToSlot(dataHashBySlot.firstKey().plus(1));
        }
      } else {
        removeAttestationsPriorToSlot(maybeSlot.get());
      }
    } finally {
      isCleanupRunning.set(false);
    }
  }

  private void removeAttestationsPriorToSlot(final UInt64 firstValidAttestationSlot) {
    final AtomicInteger count = new AtomicInteger(0);
    dataHashBySlot.headMap(firstValidAttestationSlot, false).values().stream()
        .flatMap(Set::stream)
        .forEach(
            key -> {
              final MatchingDataAttestationGroup matchingDataAttestationGroup =
                  attestationGroupByDataHash.remove(key);
              if (matchingDataAttestationGroup != null) {
                updateSize(-matchingDataAttestationGroup.size());
                count.incrementAndGet();
              }
            });
    if (count.get() > 0) {
      LOG.trace(
          "firstValidAttestationSlot: {}, removed {} keys",
          () -> firstValidAttestationSlot,
          count::get);
    }
  }

  private Stream<Attestation> streamAggregatesForDataHashesBySlot(
      final Set<Bytes32> dataHashSetForSlot,
      final BeaconState stateAtBlockSlot,
      final AttestationForkChecker forkChecker,
      final boolean blockRequiresAttestationsWithCommitteeBits) {

    return dataHashSetForSlot.stream()
        .map(attestationGroupByDataHash::get)
        .filter(Objects::nonNull)
        .filter(group -> isValid(stateAtBlockSlot, group.getAttestationData()))
        .filter(forkChecker::areAttestationsFromCorrectFork)
        .flatMap(MatchingDataAttestationGroup::stream)
        .map(ValidatableAttestation::getAttestation)
        .filter(
            attestation ->
                attestation.requiresCommitteeBits() == blockRequiresAttestationsWithCommitteeBits)
        .sorted(ATTESTATION_INCLUSION_COMPARATOR);
  }

  public List<Attestation> getAttestations(
      final Optional<UInt64> maybeSlot, final Optional<UInt64> maybeCommitteeIndex) {
    final Predicate<Map.Entry<UInt64, Set<Bytes32>>> filterForSlot =
        (entry) -> maybeSlot.map(slot -> entry.getKey().equals(slot)).orElse(true);
    final UInt64 slot = maybeSlot.orElse(recentChainData.getCurrentSlot().orElse(UInt64.ZERO));

    final SchemaDefinitions schemaDefinitions = spec.atSlot(slot).getSchemaDefinitions();
    final boolean requiresCommitteeBits =
        schemaDefinitions.getAttestationSchema().requiresCommitteeBits();

    return dataHashBySlot.descendingMap().entrySet().stream()
        .filter(filterForSlot)
        .map(Map.Entry::getValue)
        .flatMap(Collection::stream)
        .map(attestationGroupByDataHash::get)
        .filter(Objects::nonNull)
        .flatMap(
            matchingDataAttestationGroup ->
                matchingDataAttestationGroup.stream(maybeCommitteeIndex, requiresCommitteeBits))
        .map(ValidatableAttestation::getAttestation)
        .toList();
  }

  private boolean isValid(
      final BeaconState stateAtBlockSlot, final AttestationData attestationData) {
    return spec.validateAttestation(stateAtBlockSlot, attestationData).isEmpty();
  }

  public Optional<ValidatableAttestation> createAggregateFor(
      final Bytes32 attestationHashTreeRoot, final Optional<UInt64> committeeIndex) {
    return Optional.ofNullable(attestationGroupByDataHash.get(attestationHashTreeRoot))
        .flatMap(attestations -> attestations.stream(committeeIndex).findFirst());
  }

  public void onReorg(final UInt64 commonAncestorSlot) {
    attestationGroupByDataHash.values().forEach(group -> group.onReorg(commonAncestorSlot));
  }
}
