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

package tech.pegasys.teku.statetransition.attestation.utils;

import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.spec.datastructures.operations.PayloadAttestation;
import tech.pegasys.teku.spec.datastructures.operations.PayloadAttestationSchema;

public class PayloadAttestationBitsAggregator {

  private SszBitvector aggregationBits;

  public PayloadAttestationBitsAggregator(final SszBitvector aggregationBits) {
    this.aggregationBits = aggregationBits;
  }

  public static PayloadAttestationBitsAggregator fromEmptyFromPayloadAttestationSchema(
      final PayloadAttestationSchema payloadAttestationSchema) {
    return new PayloadAttestationBitsAggregator(
        payloadAttestationSchema.createEmptyAggregationBits());
  }

  public SszBitvector getAggregationBits() {
    return aggregationBits;
  }

  public static PayloadAttestationBitsAggregator of(final PayloadAttestation payloadAttestation) {
    return new PayloadAttestationBitsAggregator(payloadAttestation.getAggregationBits());
  }

  public void or(final PayloadAttestation other) {
    aggregationBits = aggregationBits.or(other.getAggregationBits());
  }

  public boolean isSuperSetOf(final PayloadAttestation other) {
    return aggregationBits.isSuperSetOf(other.getAggregationBits());
  }

  public boolean aggregateWith(final PayloadAttestation other) {
    return aggregateWith(other.getAggregationBits());
  }

  private boolean aggregateWith(final SszBitvector otherAggregationBits) {
    if (aggregationBits.intersects(otherAggregationBits)) {
      return false;
    }
    aggregationBits = aggregationBits.or(otherAggregationBits);
    return true;
  }

  public PayloadAttestationBitsAggregator copy() {
    return new PayloadAttestationBitsAggregator(aggregationBits);
  }
}
