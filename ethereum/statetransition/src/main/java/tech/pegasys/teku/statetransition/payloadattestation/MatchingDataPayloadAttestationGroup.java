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

package tech.pegasys.teku.statetransition.payloadattestation;

import static com.google.common.base.Preconditions.checkArgument;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestation;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationData;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationMessage;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationSchema;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;

/**
 * Provides a single {@link PayloadAttestation} aggregated from many instances of {@link
 * PayloadAttestationMessage} which all share the same {@link PayloadAttestationData}.
 */
class MatchingDataPayloadAttestationGroup {

  private final Set<PayloadAttestationMessage> payloadAttestationMessages =
      ConcurrentHashMap.newKeySet();

  private final Spec spec;
  private final PayloadAttestationData data;

  MatchingDataPayloadAttestationGroup(final Spec spec, final PayloadAttestationData data) {
    this.spec = spec;
    this.data = data;
  }

  PayloadAttestationData getData() {
    return data;
  }

  int size() {
    return payloadAttestationMessages.size();
  }

  /**
   * Adds a payload attestation message to this group. This payload attestation message will
   * eventually be aggregated with others.
   */
  boolean add(final PayloadAttestationMessage payloadAttestationMessage) {
    if (!payloadAttestationMessage.getData().equals(data)) {
      // ignore payload attestation messages with a different data
      return false;
    }
    return payloadAttestationMessages.add(payloadAttestationMessage);
  }

  PayloadAttestation createAggregatedPayloadAttestation(final IntList ptc) {
    checkArgument(!payloadAttestationMessages.isEmpty(), "Nothing to aggregate");
    // use a snapshot to avoid any concurrent additions
    final Set<PayloadAttestationMessage> payloadAttestationMessagesSnapshot =
        new HashSet<>(payloadAttestationMessages);
    final UInt64 slot = data.getSlot();
    final int[] setBitIndices =
        payloadAttestationMessagesSnapshot.stream()
            .flatMapToInt(
                message ->
                    // relative position(s) of the validator index with respect to the PTC
                    IntStream.range(0, ptc.size())
                        .filter(i -> ptc.getInt(i) == message.getValidatorIndex().intValue()))
            .toArray();
    final PayloadAttestationSchema payloadAttestationSchema =
        SchemaDefinitionsGloas.required(spec.atSlot(slot).getSchemaDefinitions())
            .getPayloadAttestationSchema();
    final SszBitvector aggregationBits =
        payloadAttestationSchema.getAggregationBitsSchema().ofBits(setBitIndices);
    final BLSSignature signature =
        BLS.aggregate(
            payloadAttestationMessagesSnapshot.stream()
                .map(PayloadAttestationMessage::getSignature)
                .toList());
    return payloadAttestationSchema.create(aggregationBits, data, signature);
  }
}
