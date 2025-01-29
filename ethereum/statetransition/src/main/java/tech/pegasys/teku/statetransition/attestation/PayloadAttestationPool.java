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
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.metrics.SettableGauge;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.config.SpecConfigEip7732;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.eip7732.BeaconBlockBodySchemaEip7732;
import tech.pegasys.teku.spec.datastructures.operations.PayloadAttestation;
import tech.pegasys.teku.spec.datastructures.operations.PayloadAttestationData;
import tech.pegasys.teku.spec.datastructures.operations.PayloadAttestationMessage;
import tech.pegasys.teku.spec.datastructures.operations.PayloadAttestationSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsEip7732;

public class PayloadAttestationPool implements SlotEventsChannel {
  private static final Logger LOG = LogManager.getLogger();

  /** The payload attestation are valid for 2 slots only */
  static final long PAYLOAD_ATTESTATION_RETENTION_SLOTS = 32;

  private final Map<Bytes, MatchingDataPayloadAttestationGroup> payloadAttestationGroupByDataHash =
      new HashMap<>();
  private final NavigableMap<UInt64, Set<Bytes>> payloadAttestationDataHashBySlot = new TreeMap<>();
  private final Spec spec;
  private final SettableGauge sizeGauge;
  private final AtomicInteger size = new AtomicInteger(0);
  private final int maximumPayloadAttestationCount;

  public PayloadAttestationPool(
      final Spec spec,
      final MetricsSystem metricsSystem,
      final int maximumPayloadAttestationCount) {
    this.spec = spec;
    this.sizeGauge =
        SettableGauge.create(
            metricsSystem,
            TekuMetricCategory.BEACON,
            "payload_attestation_pool_size",
            "The number of payload attestations available to be included in proposed blocks");
    this.maximumPayloadAttestationCount = maximumPayloadAttestationCount;
  }

  public synchronized void add(final PayloadAttestationMessage payloadAttestationMessage) {
    final SpecVersion specVersion = spec.atSlot(payloadAttestationMessage.getData().getSlot());
    final PayloadAttestationSchema payloadAttestationSchema =
        SchemaDefinitionsEip7732.required(specVersion.getSchemaDefinitions())
            .getPayloadAttestationSchema();
    final int ptcSize = SpecConfigEip7732.required(specVersion.getConfig()).getPtcSize();
    getOrCreatePayloadAttestationGroup(payloadAttestationMessage)
        .ifPresent(
            attestationGroup -> {
              final boolean added =
                  attestationGroup.add(
                      payloadAttestationSchema.create(
                          payloadAttestationSchema
                              .getAggregationBitsSchema()
                              .ofBits(
                                  ptcSize,
                                  payloadAttestationMessage.getValidatorIndex().intValue()),
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

    return payloadAttestationDataHashBySlot
        .headMap(stateAtBlockSlot.getSlot(), false)
        .descendingMap()
        .values()
        .stream()
        .flatMap(Collection::stream)
        .map(payloadAttestationGroupByDataHash::get)
        .filter(Objects::nonNull)
        .filter(group -> isValid(stateAtBlockSlot, group.getPayloadAttestationData()))
        .flatMap(MatchingDataPayloadAttestationGroup::stream)
        .limit(payloadAttestationsSchema.getMaxLength())
        .collect(payloadAttestationsSchema.collector());
  }

  /** TODO EIP7732 Implement validatePayloadAttestationData in OperationValidatorEip7732 */
  @SuppressWarnings("unused")
  private boolean isValid(
      final BeaconState stateAtBlockSlot, final PayloadAttestationData payloadAttestationData) {
    return true;
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
    dataHashesToRemove.stream()
        .flatMap(Set::stream)
        .forEach(
            key -> {
              final int removed = payloadAttestationGroupByDataHash.get(key).size();
              payloadAttestationGroupByDataHash.remove(key);
              updateSize(-removed);
            });
    if (!dataHashesToRemove.isEmpty()) {
      LOG.trace(
          "firstValidPayloadAttestationSlot: {}, removing: {}",
          firstValidPayloadAttestationSlot.longValue(),
          dataHashesToRemove.size());
    }
    dataHashesToRemove.clear();
  }

  /** TODO EIP7732 */
  public void onPayloadAttestationsIncludedInBlock(
      final SszList<PayloadAttestation> payloadAttestations) {}
}
