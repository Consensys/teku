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

package tech.pegasys.teku.ethereum.executionlayer.client.schema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.ethereum.executionlayer.client.serialization.BLSSignatureDeserializer;
import tech.pegasys.teku.ethereum.executionlayer.client.serialization.BLSSignatureSerializer;
import tech.pegasys.teku.ethereum.executionlayer.client.serialization.BytesDeserializer;
import tech.pegasys.teku.ethereum.executionlayer.client.serialization.BytesSerializer;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.operations.Attestation.AttestationSchema;

public class AttestationV1 {
  @JsonSerialize(using = BytesSerializer.class)
  @JsonDeserialize(using = BytesDeserializer.class)
  public final Bytes aggregationBits;

  public final AttestationDataV1 data;

  @JsonSerialize(using = BLSSignatureSerializer.class)
  @JsonDeserialize(using = BLSSignatureDeserializer.class)
  public final BLSSignature signature;

  public AttestationV1(tech.pegasys.teku.spec.datastructures.operations.Attestation attestation) {
    this.aggregationBits = attestation.getAggregationBits().sszSerialize();
    this.data = new AttestationDataV1(attestation.getData());
    this.signature = new BLSSignature(attestation.getAggregateSignature());
  }

  @JsonCreator
  public AttestationV1(
      @JsonProperty("aggregationBits") final Bytes aggregationBits,
      @JsonProperty("data") final AttestationDataV1 data,
      @JsonProperty("signature") final BLSSignature signature) {
    this.aggregationBits = aggregationBits;
    this.data = data;
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
        attestationSchema.getAggregationBitsSchema().sszDeserialize(aggregationBits),
        data.asInternalAttestationData(),
        signature.asInternalBLSSignature());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof AttestationV1)) {
      return false;
    }
    AttestationV1 that = (AttestationV1) o;
    return Objects.equals(aggregationBits, that.aggregationBits)
        && Objects.equals(data, that.data)
        && Objects.equals(signature, that.signature);
  }

  @Override
  public int hashCode() {
    return Objects.hash(aggregationBits, data, signature);
  }
}
