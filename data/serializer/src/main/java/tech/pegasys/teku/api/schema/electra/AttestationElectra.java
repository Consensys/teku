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

package tech.pegasys.teku.api.schema.electra;

import static tech.pegasys.teku.api.schema.SchemaConstants.DESCRIPTION_BYTES96;
import static tech.pegasys.teku.api.schema.SchemaConstants.DESCRIPTION_BYTES_SSZ;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.api.schema.AttestationData;
import tech.pegasys.teku.api.schema.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.operations.AttestationContainer;
import tech.pegasys.teku.spec.datastructures.operations.versions.electra.AttestationElectraSchema;

@SuppressWarnings("JavaCase")
public class AttestationElectra {
  @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES_SSZ)
  public final Bytes aggregation_bits;

  public final AttestationData data;

  @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES_SSZ)
  public final SszBitvector committee_bits;

  @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES96)
  public final BLSSignature signature;

  public AttestationElectra(final AttestationContainer attestationContainer) {
    this.aggregation_bits = attestationContainer.getAggregationBits().sszSerialize();
    this.data = new AttestationData(attestationContainer.getData());
    this.committee_bits = attestationContainer.getCommitteeBitsRequired();
    this.signature = new BLSSignature(attestationContainer.getAggregateSignature());
  }

  public AttestationElectra(
      final tech.pegasys.teku.spec.datastructures.operations.versions.electra.AttestationElectra
          attestation) {
    this.aggregation_bits = attestation.getAggregationBits().sszSerialize();
    this.data = new AttestationData(attestation.getData());
    this.committee_bits = attestation.getCommitteeBitsRequired();
    this.signature = new BLSSignature(attestation.getAggregateSignature());
  }

  @JsonCreator
  public AttestationElectra(
      @JsonProperty("aggregation_bits") final Bytes aggregation_bits,
      @JsonProperty("data") final AttestationData data,
      @JsonProperty("committee_bits") final SszBitvector committee_bits,
      @JsonProperty("signature") final BLSSignature signature) {
    this.aggregation_bits = aggregation_bits;
    this.data = data;
    this.committee_bits = committee_bits;
    this.signature = signature;
  }

  public tech.pegasys.teku.spec.datastructures.operations.versions.electra.AttestationElectra
      asInternalAttestation(final Spec spec) {
    return asInternalAttestation(spec.atSlot(data.slot));
  }

  public tech.pegasys.teku.spec.datastructures.operations.versions.electra.AttestationElectra
      asInternalAttestation(final SpecVersion specVersion) {
    final AttestationElectraSchema attestationSchema =
        specVersion
            .getSchemaDefinitions()
            .toVersionElectra()
            .orElseThrow()
            .getAttestationElectraSchema();
    return attestationSchema.create(
        attestationSchema.getAggregationBitsSchema().sszDeserialize(aggregation_bits),
        data.asInternalAttestationData(),
        committee_bits,
        signature.asInternalBLSSignature());
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof AttestationElectra that)) {
      return false;
    }
    return Objects.equals(aggregation_bits, that.aggregation_bits)
        && Objects.equals(data, that.data)
        && Objects.equals(committee_bits, that.committee_bits)
        && Objects.equals(signature, that.signature);
  }

  @Override
  public int hashCode() {
    return Objects.hash(aggregation_bits, data, committee_bits, signature);
  }
}
