/*
 * Copyright ConsenSys Software Inc., 2022
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

import static tech.pegasys.teku.api.schema.AttestationData.ATTESTATION_DATA_TYPE;
import static tech.pegasys.teku.api.schema.BLSSignature.BLS_SIGNATURE_TYPE;
import static tech.pegasys.teku.api.schema.SchemaConstants.DESCRIPTION_BYTES96;
import static tech.pegasys.teku.api.schema.SchemaConstants.DESCRIPTION_BYTES_SSZ;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.json.types.CoreTypes;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.operations.Attestation.AttestationSchema;

@SuppressWarnings("JavaCase")
public class Attestation {
  @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES_SSZ)
  public Bytes aggregation_bits;

  public AttestationData data;

  @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES96)
  public BLSSignature signature;

  public static final DeserializableTypeDefinition<Attestation> ATTESTATION_TYPE =
      DeserializableTypeDefinition.object(Attestation.class)
          .initializer(Attestation::new)
          .withField(
              "aggregation_bits",
              CoreTypes.BYTES_TYPE,
              Attestation::getAggregationBits,
              Attestation::setAggregationBits)
          .withField("data", ATTESTATION_DATA_TYPE, Attestation::getData, Attestation::setData)
          .withField(
              "signature", BLS_SIGNATURE_TYPE, Attestation::getSignature, Attestation::setSignature)
          .build();

  public Attestation() {}

  public Attestation(tech.pegasys.teku.spec.datastructures.operations.Attestation attestation) {
    this.aggregation_bits = attestation.getAggregationBits().sszSerialize();
    this.data = new AttestationData(attestation.getData());
    this.signature = new BLSSignature(attestation.getAggregateSignature());
  }

  @JsonCreator
  public Attestation(
      @JsonProperty("aggregation_bits") final Bytes aggregation_bits,
      @JsonProperty("data") final AttestationData data,
      @JsonProperty("signature") final BLSSignature signature) {
    this.aggregation_bits = aggregation_bits;
    this.data = data;
    this.signature = signature;
  }

  public Bytes getAggregationBits() {
    return aggregation_bits;
  }

  public void setAggregationBits(Bytes aggregation_bits) {
    this.aggregation_bits = aggregation_bits;
  }

  public AttestationData getData() {
    return data;
  }

  public void setData(AttestationData data) {
    this.data = data;
  }

  public BLSSignature getSignature() {
    return signature;
  }

  public void setSignature(BLSSignature signature) {
    this.signature = signature;
  }

  public tech.pegasys.teku.spec.datastructures.operations.Attestation asInternalAttestation(
      final Spec spec) {
    return asInternalAttestation(spec.atSlot(data.slot));
  }

  public tech.pegasys.teku.spec.datastructures.operations.Attestation asInternalAttestation(
      final SpecVersion specVersion) {
    final AttestationSchema attestationSchema =
        specVersion.getSchemaDefinitions().getAttestationSchema();
    return attestationSchema.create(
        attestationSchema.getAggregationBitsSchema().sszDeserialize(aggregation_bits),
        data.asInternalAttestationData(),
        signature.asInternalBLSSignature());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Attestation)) {
      return false;
    }
    Attestation that = (Attestation) o;
    return Objects.equals(aggregation_bits, that.aggregation_bits)
        && Objects.equals(data, that.data)
        && Objects.equals(signature, that.signature);
  }

  @Override
  public int hashCode() {
    return Objects.hash(aggregation_bits, data, signature);
  }
}
