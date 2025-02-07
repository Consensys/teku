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

import it.unimi.dsi.fastutil.ints.IntList;
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
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.eip7732.BeaconBlockBodySchemaEip7732;
import tech.pegasys.teku.spec.datastructures.operations.PayloadAttestation;
import tech.pegasys.teku.spec.datastructures.operations.PayloadAttestationData;
import tech.pegasys.teku.spec.datastructures.operations.PayloadAttestationMessage;
import tech.pegasys.teku.spec.datastructures.operations.PayloadAttestationSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsEip7732;
import tech.pegasys.teku.storage.client.RecentChainData;

public class PayloadAttestationPool implements SlotEventsChannel {
  private static final Logger LOG = LogManager.getLogger();

  /** The payload attestation are valid for 2 slots only */
  static final long PAYLOAD_ATTESTATION_RETENTION_SLOTS = 32;

  private final Map<Bytes, MatchingDataPayloadAttestationGroup> payloadAttestationGroupByDataHash =
      new HashMap<>();
  private final NavigableMap<UInt64, Set<Bytes>> payloadAttestationDataHashBySlot = new TreeMap<>();

  private final Spec spec;
  private final SettableGauge sizeGauge;
  private final RecentChainData recentChainData;
  private final int maximumPayloadAttestationCount;

  private final AtomicInteger size = new AtomicInteger(0);

  public PayloadAttestationPool(
      final Spec spec,
      final MetricsSystem metricsSystem,
      final RecentChainData recentChainData,
      final int maximumPayloadAttestationCount) {
    this.spec = spec;
    this.sizeGauge =
        SettableGauge.create(
            metricsSystem,
            TekuMetricCategory.BEACON,
            "payload_attestation_pool_size",
            "The number of payload attestations available to be included in proposed blocks");
    this.recentChainData = recentChainData;
    this.maximumPayloadAttestationCount = maximumPayloadAttestationCount;
  }

  public synchronized void add(final PayloadAttestationMessage payloadAttestationMessage) {
    final UInt64 slot = payloadAttestationMessage.getData().getSlot();
    final SpecVersion specVersion = spec.atSlot(slot);
    final PayloadAttestationSchema payloadAttestationSchema =
        SchemaDefinitionsEip7732.required(specVersion.getSchemaDefinitions())
            .getPayloadAttestationSchema();
    // EIP-7732 TODO: avoid this join
    final Optional<BeaconState> state = recentChainData.retrieveStateInEffectAtSlot(slot).join();
    if (state.isEmpty()) {
      return;
    }
    getOrCreatePayloadAttestationGroup(payloadAttestationMessage)
        .ifPresent(
            attestationGroup -> {
              final IntList ptc = spec.getPtc(state.get(), slot);
              final int ptcRelativePosition =
                  ptc.indexOf(payloadAttestationMessage.getValidatorIndex().intValue());
              if (ptcRelativePosition == -1) {
                return;
              }
              final boolean added =
                  attestationGroup.add(
                      payloadAttestationSchema.create(
                          payloadAttestationSchema
                              .getAggregationBitsSchema()
                              .ofBits(ptcRelativePosition),
                          payloadAttestationMessage.getData(),
                          payloadAttestationMessage.getSignature()));
              if (added) {
                updateSize(1);
              }
            });
    // Always keep the latest slot payload attestations, so we don't discard everything
    int currentSize = getSize();
    while (payloadAttestationDataHashBySlot.size() > 1
        && currentSize > maximumPayloadAttestationCount) {
      LOG.trace(
          "Attestation cache at {} exceeds {}, ", currentSize, maximumPayloadAttestationCount);
      final UInt64 firstSlotToKeep = payloadAttestationDataHashBySlot.firstKey().plus(1);
      removePayloadAttestationsPriorToSlot(firstSlotToKeep);
      currentSize = getSize();
    }
  }

  private void removeGroup(final Bytes dataHash) {
    final int removed = payloadAttestationGroupByDataHash.remove(dataHash).size();
    updateSize(-removed);
  }

  private Optional<MatchingDataPayloadAttestationGroup> getOrCreatePayloadAttestationGroup(
      final PayloadAttestationMessage payloadAttestationMessage) {
    final PayloadAttestationData payloadAttestationData = payloadAttestationMessage.getData();
    payloadAttestationDataHashBySlot
        .computeIfAbsent(payloadAttestationData.getSlot(), slot -> new HashSet<>())
        .add(payloadAttestationData.hashTreeRoot());
    final MatchingDataPayloadAttestationGroup payloadAttestationGroup =
        payloadAttestationGroupByDataHash.computeIfAbsent(
            payloadAttestationData.hashTreeRoot(),
            key -> new MatchingDataPayloadAttestationGroup(spec, payloadAttestationData));
    return Optional.of(payloadAttestationGroup);
  }

  private void updateSize(final int delta) {
    final int currentSize = size.addAndGet(delta);
    sizeGauge.set(currentSize);
  }

  public synchronized int getSize() {
    return size.get();
  }

  public synchronized SszList<PayloadAttestation> getPayloadAttestationsForBlock(
      final BeaconState stateAtBlockSlot) {
    final SchemaDefinitions schemaDefinitions =
        spec.atSlot(stateAtBlockSlot.getSlot()).getSchemaDefinitions();
    final SszListSchema<PayloadAttestation, ?> payloadAttestationsSchema =
        BeaconBlockBodySchemaEip7732.required(schemaDefinitions.getBeaconBlockBodySchema())
            .getPayloadAttestationsSchema();

    final Set<PayloadAttestationData> invalidAttestationsToRemove = new HashSet<>();

    final SszList<PayloadAttestation> payloadAttestations =
        payloadAttestationDataHashBySlot
            .headMap(stateAtBlockSlot.getSlot(), false)
            .descendingMap()
            .values()
            .stream()
            .flatMap(Collection::stream)
            .map(payloadAttestationGroupByDataHash::get)
            .filter(Objects::nonNull)
            .filter(
                group -> {
                  if (!isValid(stateAtBlockSlot, group.getPayloadAttestationData())) {
                    invalidAttestationsToRemove.add(group.getPayloadAttestationData());
                    return false;
                  }
                  return true;
                })
            .flatMap(MatchingDataPayloadAttestationGroup::stream)
            .limit(payloadAttestationsSchema.getMaxLength())
            .collect(payloadAttestationsSchema.collector());

    // clean-up invalid attestations
    invalidAttestationsToRemove.forEach(
        invalidAttestation -> {
          final Bytes32 dataHash = invalidAttestation.hashTreeRoot();
          final UInt64 slot = invalidAttestation.getSlot();
          removeGroup(dataHash);
          final Set<Bytes> dataHashes = payloadAttestationDataHashBySlot.get(slot);
          dataHashes.remove(dataHash);
          if (dataHashes.isEmpty()) {
            payloadAttestationDataHashBySlot.remove(slot);
          }
        });

    return payloadAttestations;
  }

  private boolean isValid(
      final BeaconState stateAtBlockSlot, final PayloadAttestationData payloadAttestationData) {
    return spec.validatePayloadAttestation(stateAtBlockSlot, payloadAttestationData)
        .map(
            invalidReason -> {
              final UInt64 slot = payloadAttestationData.getSlot();
              LOG.debug(
                  "Aggregated payload attestation for slot {} and block root {} is invalid: {}",
                  slot,
                  payloadAttestationData.getBeaconBlockRoot(),
                  invalidReason.describe());
              return false;
            })
        .orElse(true);
  }

  @Override
  public void onSlot(final UInt64 slot) {
    if (slot.compareTo(PAYLOAD_ATTESTATION_RETENTION_SLOTS) <= 0) {
      return;
    }
    final UInt64 firstValidPayloadAttestationSlot = slot.minus(PAYLOAD_ATTESTATION_RETENTION_SLOTS);
    removePayloadAttestationsPriorToSlot(firstValidPayloadAttestationSlot);
  }

  private void removePayloadAttestationsPriorToSlot(final UInt64 firstValidPayloadAttestationSlot) {
    final Collection<Set<Bytes>> dataHashesToRemove =
        payloadAttestationDataHashBySlot.headMap(firstValidPayloadAttestationSlot, false).values();
    dataHashesToRemove.stream().flatMap(Set::stream).forEach(this::removeGroup);
    if (!dataHashesToRemove.isEmpty()) {
      LOG.trace(
          "firstValidPayloadAttestationSlot: {}, removing: {}",
          firstValidPayloadAttestationSlot.longValue(),
          dataHashesToRemove.size());
    }
    dataHashesToRemove.clear();
  }

  @SuppressWarnings("unused")
  // EIP-7732 TODO: implement
  public void onPayloadAttestationsIncludedInBlock(
      final UInt64 slot, final SszList<PayloadAttestation> payloadAttestations) {}
}
