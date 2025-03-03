/*
 * Copyright Consensys Software Inc., 2022
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
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
   * <p>With 1.2 million active validators, we'd expect around 37_500 attestations per slot; so 3
   * slots worth of attestations is almost 120_000.
   *
   * <p>128 attestations perfectly packed at a 1.2 million validator set would be 1_200_000 / 32 /
   * 64 bits, about 584 bits per aggregate. 128 of those is 74752 attestations if perfectly packed.
   * Technically if we did have to cache 2 full slots of information, that would be roughly 150k
   * cache size.
   *
   * <p>Because the real world exists, it's fair to expect that it's not all perfect, and 120k
   * should be an adequately large cache to store current attestations plus some old ones that may
   * not have been included so that we have plenty to choose if block building based on an expected
   * 1.2 million validators.
   *
   * <p>Strictly to cache all attestations for a full 2 epochs is significantly larger than this
   * cache.
   */
  public static final int DEFAULT_MAXIMUM_ATTESTATION_COUNT = 120_000;

  private final Map<Bytes, MatchingDataAttestationGroup> attestationGroupByDataHash =
      new HashMap<>();
  private final NavigableMap<UInt64, Set<Bytes>> dataHashBySlot = new TreeMap<>();

  private final Spec spec;
  private final RecentChainData recentChainData;
  private final SettableGauge sizeGauge;
  private final int maximumAttestationCount;

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

  public synchronized void add(final ValidatableAttestation attestation) {
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
    // Always keep the latest slot attestations, so we don't discard everything
    int currentSize = getSize();
    while (dataHashBySlot.size() > 1 && currentSize > maximumAttestationCount) {
      LOG.trace("Attestation cache at {} exceeds {}, ", currentSize, maximumAttestationCount);
      final UInt64 firstSlotToKeep = dataHashBySlot.firstKey().plus(1);
      removeAttestationsPriorToSlot(firstSlotToKeep);
      currentSize = getSize();
    }
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
    dataHashBySlot
        .computeIfAbsent(attestationData.getSlot(), slot -> new HashSet<>())
        .add(attestationData.hashTreeRoot());
    final MatchingDataAttestationGroup attestationGroup =
        attestationGroupByDataHash.computeIfAbsent(
            attestationData.hashTreeRoot(),
            key -> new MatchingDataAttestationGroup(spec, attestationData, committeesSize));
    return Optional.of(attestationGroup);
  }

  // We only have the committees size already available via attestations received in the gossip
  // flow and have been successfully validated, so querying the state is required for other cases
  private Optional<Int2IntMap> getCommitteesSizeUsingTheState(
      final AttestationData attestationData) {
    return recentChainData
        .getStore()
        .getBlockStateIfAvailable(attestationData.getTarget().getRoot())
        .map(state -> spec.getBeaconCommitteesSize(state, attestationData.getSlot()));
  }

  @Override
  public synchronized void onSlot(final UInt64 slot) {
    if (slot.compareTo(ATTESTATION_RETENTION_SLOTS) <= 0) {
      return;
    }
    final UInt64 firstValidAttestationSlot = slot.minus(ATTESTATION_RETENTION_SLOTS);
    removeAttestationsPriorToSlot(firstValidAttestationSlot);
  }

  private void removeAttestationsPriorToSlot(final UInt64 firstValidAttestationSlot) {
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
    if (!dataHashesToRemove.isEmpty()) {
      LOG.trace(
          "firstValidAttestationSlot: {}, removing: {}",
          () -> firstValidAttestationSlot,
          dataHashesToRemove::size);
    }
    dataHashesToRemove.clear();
  }

  public synchronized void onAttestationsIncludedInBlock(
      final UInt64 slot, final Iterable<Attestation> attestations) {
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

  public synchronized int getSize() {
    return size.get();
  }

  public synchronized SszList<Attestation> getAttestationsForBlock(
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
                    stateAtBlockSlot.getSlot(),
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

  private Stream<Attestation> streamAggregatesForDataHashesBySlot(
      final UInt64 slot,
      final Set<Bytes> dataHashSetForSlot,
      final BeaconState stateAtBlockSlot,
      final AttestationForkChecker forkChecker,
      final boolean blockRequiresAttestationsWithCommitteeBits) {

    return dataHashSetForSlot.stream()
        .map(attestationGroupByDataHash::get)
        .filter(Objects::nonNull)
        .filter(group -> isValid(stateAtBlockSlot, group.getAttestationData()))
        .filter(forkChecker::areAttestationsFromCorrectFork)
        .flatMap(group -> group.streamForBlockProduction(slot))
        .map(ValidatableAttestation::getAttestation)
        .filter(
            attestation ->
                attestation.requiresCommitteeBits() == blockRequiresAttestationsWithCommitteeBits)
        .sorted(ATTESTATION_INCLUSION_COMPARATOR);
  }

  public synchronized List<Attestation> getAttestations(
      final Optional<UInt64> maybeSlot, final Optional<UInt64> maybeCommitteeIndex) {

    final Predicate<Map.Entry<UInt64, Set<Bytes>>> filterForSlot =
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
                matchingDataAttestationGroup.streamForAPI(
                    maybeCommitteeIndex, requiresCommitteeBits))
        .map(ValidatableAttestation::getAttestation)
        .toList();
  }

  private boolean isValid(
      final BeaconState stateAtBlockSlot, final AttestationData attestationData) {
    return spec.validateAttestation(stateAtBlockSlot, attestationData).isEmpty();
  }

  public synchronized Optional<ValidatableAttestation> createAggregateFor(
      final Bytes32 attestationHashTreeRoot, final Optional<UInt64> committeeIndex) {
    return Optional.ofNullable(attestationGroupByDataHash.get(attestationHashTreeRoot))
        .flatMap(attestations -> attestations.streamForAggregation(committeeIndex).findFirst());
  }

  public synchronized void onReorg(final UInt64 commonAncestorSlot) {
    attestationGroupByDataHash.values().forEach(group -> group.onReorg(commonAncestorSlot));
  }
}
