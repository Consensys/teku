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

package tech.pegasys.teku.api.schema.eip7732;

import static tech.pegasys.teku.api.schema.SchemaConstants.DESCRIPTION_BYTES96;
import static tech.pegasys.teku.api.schema.SchemaConstants.DESCRIPTION_BYTES_SSZ;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.api.schema.BLSSignature;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.operations.PayloadAttestationSchema;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsEip7732;

@SuppressWarnings("JavaCase")
public class PayloadAttestation {

  @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES_SSZ)
  public final Bytes aggregation_bits;

  public final PayloadAttestationData data;

  @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES96)
  public final BLSSignature signature;

  public PayloadAttestation(
      final tech.pegasys.teku.spec.datastructures.operations.PayloadAttestation
          payloadAttestation) {
    this.aggregation_bits = payloadAttestation.getAggregationBits().sszSerialize();
    this.data = new PayloadAttestationData(payloadAttestation.getData());
    this.signature = new BLSSignature(payloadAttestation.getSignature());
  }

  @JsonCreator
  public PayloadAttestation(
      @JsonProperty("aggregation_bits") final Bytes aggregation_bits,
      @JsonProperty("data") final PayloadAttestationData data,
      @JsonProperty("signature") final BLSSignature signature) {
    this.aggregation_bits = aggregation_bits;
    this.data = data;
    this.signature = signature;
  }

  @Override
  public boolean equals(final Object object) {
    if (this == object) {
      return true;
    }
    if (!(object instanceof final PayloadAttestation that)) {
      return false;
    }
    return Objects.equals(aggregation_bits, that.aggregation_bits)
        && Objects.equals(data, that.data)
        && Objects.equals(signature, that.signature);
  }

  public tech.pegasys.teku.spec.datastructures.operations.PayloadAttestation
      asInternalPayloadAttestation(final SpecVersion spec) {
    final PayloadAttestationSchema payloadAttestationSchema =
        SchemaDefinitionsEip7732.required(spec.getSchemaDefinitions())
            .getPayloadAttestationSchema();
    return payloadAttestationSchema.create(
        payloadAttestationSchema.getAggregationBitsSchema().sszDeserialize(aggregation_bits),
        data.asInternalPayloadAttestationData(),
        signature.asInternalBLSSignature());
  }

  @Override
  public int hashCode() {
    return Objects.hash(aggregation_bits, data, signature);
  }
}
