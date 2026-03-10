/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.statetransition.payloadattestation;

import com.google.common.annotations.VisibleForTesting;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.SettableGauge;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.gloas.BeaconBlockBodySchemaGloas;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestation;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationData;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationMessage;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.statetransition.OperationAddedSubscriber;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

public class AggregatingPayloadAttestationPool
    implements PayloadAttestationPool, SlotEventsChannel {

  /** The payload attestations are valid for 2 slots only */
  @VisibleForTesting static final long PAYLOAD_ATTESTATION_RETENTION_SLOTS = 2;

  private final Subscribers<OperationAddedSubscriber<PayloadAttestationMessage>> subscribers =
      Subscribers.create(true);

  private final ConcurrentMap<Bytes, MatchingDataPayloadAttestationGroup>
      payloadAttestationGroupByDataHash = new ConcurrentHashMap<>();

  private final ConcurrentNavigableMap<UInt64, Set<Bytes>> dataHashBySlot =
      new ConcurrentSkipListMap<>();

  private final Spec spec;
  private final PayloadAttestationMessageGossipValidator validator;
  private final SettableGauge sizeGauge;

  private final AtomicInteger size = new AtomicInteger(0);

  public AggregatingPayloadAttestationPool(
      final Spec spec,
      final PayloadAttestationMessageGossipValidator validator,
      final MetricsSystem metricsSystem) {
    this.spec = spec;
    this.validator = validator;
    sizeGauge =
        SettableGauge.create(
            metricsSystem,
            TekuMetricCategory.BEACON,
            "payload_attestation_pool_size",
            "The number of payload attestations available to be included in proposed blocks");
  }

  @Override
  public void onSlot(final UInt64 slot) {
    final UInt64 firstPayloadAttestationSlotToKeep =
        slot.minusMinZero(PAYLOAD_ATTESTATION_RETENTION_SLOTS);
    removePayloadAttestationsPriorToSlot(firstPayloadAttestationSlotToKeep);
  }

  private void removePayloadAttestationsPriorToSlot(final UInt64 slot) {
    final Collection<Set<Bytes>> dataHashesToRemove = dataHashBySlot.headMap(slot, false).values();
    dataHashesToRemove.stream()
        .flatMap(Set::stream)
        .forEach(
            dataHash -> {
              final MatchingDataPayloadAttestationGroup removed =
                  payloadAttestationGroupByDataHash.remove(dataHash);
              if (removed != null) {
                updateSize(-removed.size());
              }
            });
    dataHashesToRemove.clear();
  }

  private void updateSize(final int delta) {
    final int currentSize = size.addAndGet(delta);
    sizeGauge.set(currentSize);
  }

  @Override
  public void subscribeOperationAdded(
      final OperationAddedSubscriber<PayloadAttestationMessage> subscriber) {
    subscribers.subscribe(subscriber);
  }

  @Override
  public SafeFuture<InternalValidationResult> addLocal(
      final PayloadAttestationMessage payloadAttestationMessage) {
    return add(payloadAttestationMessage, false);
  }

  @Override
  public SafeFuture<InternalValidationResult> addRemote(
      final PayloadAttestationMessage payloadAttestationMessage,
      final Optional<UInt64> arrivalTimestamp) {
    return add(payloadAttestationMessage, true);
  }

  private SafeFuture<InternalValidationResult> add(
      final PayloadAttestationMessage payloadAttestationMessage, final boolean fromNetwork) {
    return validator
        .validate(payloadAttestationMessage)
        .thenPeek(
            result -> {
              if (result.isAccept()) {
                subscribers.forEach(
                    subscriber ->
                        subscriber.onOperationAdded(
                            payloadAttestationMessage, result, fromNetwork));
                if (doAdd(payloadAttestationMessage)) {
                  updateSize(1);
                }
              }
            });
  }

  private boolean doAdd(final PayloadAttestationMessage payloadAttestationMessage) {
    final PayloadAttestationData data = payloadAttestationMessage.getData();
    final Bytes32 dataHash = data.hashTreeRoot();
    dataHashBySlot
        .computeIfAbsent(data.getSlot(), __ -> ConcurrentHashMap.newKeySet())
        .add(dataHash);
    return payloadAttestationGroupByDataHash
        .computeIfAbsent(dataHash, __ -> new MatchingDataPayloadAttestationGroup(spec, data))
        .add(payloadAttestationMessage);
  }

  @Override
  public SszList<PayloadAttestation> getPayloadAttestationsForBlock(
      final BeaconState blockSlotState, final Bytes32 parentRoot) {
    final SpecVersion specVersion = spec.atSlot(blockSlotState.getSlot());
    final SszListSchema<PayloadAttestation, ?> payloadAttestationsSchema =
        BeaconBlockBodySchemaGloas.required(
                specVersion.getSchemaDefinitions().getBeaconBlockBodySchema())
            .getPayloadAttestationsSchema();
    // The slot of the parent block is exactly one slot before the proposing slot.
    final UInt64 slot = blockSlotState.getSlot().minusMinZero(1);
    final Set<Bytes> dataHashes = dataHashBySlot.get(slot);
    if (dataHashes == null || dataHashes.isEmpty()) {
      return payloadAttestationsSchema.of();
    }
    final IntList ptc = spec.getPtc(blockSlotState, slot);
    return dataHashes.stream()
        .map(payloadAttestationGroupByDataHash::get)
        .filter(Objects::nonNull)
        // Only match payload attestations for the given parent root
        .filter(
            payloadAttestationGroup ->
                payloadAttestationGroup.getData().getBeaconBlockRoot().equals(parentRoot))
        // Most validators first
        .sorted(Comparator.comparingInt(MatchingDataPayloadAttestationGroup::size).reversed())
        .limit(payloadAttestationsSchema.getMaxLength())
        .map(
            payloadAttestationGroup ->
                payloadAttestationGroup.createAggregatedPayloadAttestation(ptc))
        .collect(payloadAttestationsSchema.collector());
  }
}
