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

package tech.pegasys.teku.ethereum.executionclient.schema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import tech.pegasys.teku.ethereum.executionclient.serialization.BLSSignatureDeserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.BLSSignatureSerializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.UInt64AsHexDeserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.UInt64AsHexSerializer;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestation.IndexedAttestationSchema;

@SuppressWarnings("JavaCase")
public class IndexedAttestationV1 {
  @JsonSerialize(contentUsing = UInt64AsHexSerializer.class)
  @JsonDeserialize(contentUsing = UInt64AsHexDeserializer.class)
  public final List<UInt64> attestingIndices;

  public final AttestationDataV1 data;

  @JsonSerialize(using = BLSSignatureSerializer.class)
  @JsonDeserialize(using = BLSSignatureDeserializer.class)
  public final BLSSignature signature;

  public IndexedAttestationV1(
      tech.pegasys.teku.spec.datastructures.operations.IndexedAttestation indexedAttestation) {
    this.attestingIndices =
        indexedAttestation.getAttestingIndices().streamUnboxed().collect(Collectors.toList());
    this.data = new AttestationDataV1(indexedAttestation.getData());
    this.signature = new BLSSignature(indexedAttestation.getSignature());
  }

  @JsonCreator
  public IndexedAttestationV1(
      @JsonProperty("attestingIndices") final List<UInt64> attestingIndices,
      @JsonProperty("data") final AttestationDataV1 data,
      @JsonProperty("signature") final BLSSignature signature) {
    this.attestingIndices = attestingIndices;
    this.data = data;
    this.signature = signature;
  }

  public tech.pegasys.teku.spec.datastructures.operations.IndexedAttestation
      asInternalIndexedAttestation(final Spec spec) {
    return asInternalIndexedAttestation(spec.atSlot(data.slot));
  }

  public tech.pegasys.teku.spec.datastructures.operations.IndexedAttestation
      asInternalIndexedAttestation(final SpecVersion spec) {
    final IndexedAttestationSchema indexedAttestationSchema =
        spec.getSchemaDefinitions().getIndexedAttestationSchema();
    return indexedAttestationSchema.create(
        indexedAttestationSchema.getAttestingIndicesSchema().of(attestingIndices),
        data.asInternalAttestationData(),
        signature.asInternalBLSSignature());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof IndexedAttestationV1)) {
      return false;
    }
    IndexedAttestationV1 that = (IndexedAttestationV1) o;
    return Objects.equals(attestingIndices, that.attestingIndices)
        && Objects.equals(data, that.data)
        && Objects.equals(signature, that.signature);
  }

  @Override
  public int hashCode() {
    return Objects.hash(attestingIndices, data, signature);
  }
}
