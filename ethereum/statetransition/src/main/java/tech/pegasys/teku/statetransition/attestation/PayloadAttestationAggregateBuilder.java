/*
 * Copyright Consensys Software Inc., 2024
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

import static com.google.common.base.Preconditions.checkState;

import java.util.HashSet;
import java.util.Set;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.operations.PayloadAttestation;
import tech.pegasys.teku.spec.datastructures.operations.PayloadAttestationData;
import tech.pegasys.teku.spec.datastructures.operations.PayloadAttestationSchema;
import tech.pegasys.teku.statetransition.attestation.utils.PayloadAttestationBitsAggregator;

public class PayloadAttestationAggregateBuilder {

  private final Spec spec;
  private final Set<PayloadAttestation> includedPayloadAttestations = new HashSet<>();
  private final PayloadAttestationData payloadAttestationData;
  private PayloadAttestationBitsAggregator payloadAttestationBitsAggregator;

  PayloadAttestationAggregateBuilder(
      final Spec spec, final PayloadAttestationData payloadAttestationData) {
    this.spec = spec;
    this.payloadAttestationData = payloadAttestationData;
  }

  public boolean aggregate(final PayloadAttestation payloadAttestation) {

    if (payloadAttestationBitsAggregator == null) {
      includedPayloadAttestations.add(payloadAttestation);
      payloadAttestationBitsAggregator = PayloadAttestationBitsAggregator.of(payloadAttestation);
      return true;
    }
    if (payloadAttestationBitsAggregator.aggregateWith(payloadAttestation)) {
      includedPayloadAttestations.add(payloadAttestation);
      return true;
    }
    return false;
  }

  public PayloadAttestation buildAggregate() {
    checkState(
        payloadAttestationBitsAggregator != null,
        "Must aggregate at least one payload attestation");
    final PayloadAttestationSchema payloadAttestationSchema =
        spec.atSlot(payloadAttestationData.getSlot())
            .getSchemaDefinitions()
            .toVersionEip7732()
            .orElseThrow()
            .getPayloadAttestationSchema();
    return payloadAttestationSchema.create(
        payloadAttestationBitsAggregator.getAggregationBits(),
        payloadAttestationData,
        BLS.aggregate(
            includedPayloadAttestations.stream().map(PayloadAttestation::getSignature).toList()));
  }
}
