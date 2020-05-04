/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.api.schema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import tech.pegasys.teku.ssz.SSZTypes.Bitlist;

public class Attestation {
  public final Bitlist aggregation_bits;
  public final AttestationData data;
  public final BLSSignature signature;

  public Attestation(tech.pegasys.teku.datastructures.operations.Attestation attestation) {
    this.aggregation_bits = attestation.getAggregation_bits();
    this.data = new AttestationData(attestation.getData());
    this.signature = new BLSSignature(attestation.getAggregate_signature());
  }

  @JsonCreator
  public Attestation(
      @JsonProperty("aggregation_bits") final Bitlist aggregation_bits,
      @JsonProperty("data") final AttestationData data,
      @JsonProperty("signature") final BLSSignature signature) {
    this.aggregation_bits = aggregation_bits;
    this.data = data;
    this.signature = signature;
  }

  public tech.pegasys.teku.datastructures.operations.Attestation asInternalAttestation() {
    return new tech.pegasys.teku.datastructures.operations.Attestation(
        aggregation_bits, data.asInternalAttestationData(), signature.asInternalBLSSignature());
  }
}
