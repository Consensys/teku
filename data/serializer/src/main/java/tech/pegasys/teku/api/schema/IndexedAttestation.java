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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import tech.pegasys.teku.infrastructure.json.types.CoreTypes;
import tech.pegasys.teku.infrastructure.json.types.DeserializableListTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestation.IndexedAttestationSchema;

@SuppressWarnings("JavaCase")
public class IndexedAttestation {
  @ArraySchema(schema = @Schema(type = "string", format = "uint64"))
  public List<UInt64> attesting_indices;

  public AttestationData data;

  @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES96)
  public BLSSignature signature;

  public static final DeserializableTypeDefinition<IndexedAttestation> INDEXED_ATTESTATION_TYPE =
      DeserializableTypeDefinition.object(IndexedAttestation.class)
          .initializer(IndexedAttestation::new)
          .withField(
              "attesting_indices",
              new DeserializableListTypeDefinition<>(CoreTypes.UINT64_TYPE),
              IndexedAttestation::getAttestingIndices,
              IndexedAttestation::setAttestingIndices)
          .withField(
              "data",
              ATTESTATION_DATA_TYPE,
              IndexedAttestation::getData,
              IndexedAttestation::setData)
          .withField(
              "signature",
              BLS_SIGNATURE_TYPE,
              IndexedAttestation::getSignature,
              IndexedAttestation::setSignature)
          .build();

  public IndexedAttestation() {}

  public IndexedAttestation(
      tech.pegasys.teku.spec.datastructures.operations.IndexedAttestation indexedAttestation) {
    this.attesting_indices =
        indexedAttestation.getAttestingIndices().streamUnboxed().collect(Collectors.toList());
    this.data = new AttestationData(indexedAttestation.getData());
    this.signature = new BLSSignature(indexedAttestation.getSignature());
  }

  @JsonCreator
  public IndexedAttestation(
      @JsonProperty("attesting_indices") final List<UInt64> attesting_indices,
      @JsonProperty("data") final AttestationData data,
      @JsonProperty("signature") final BLSSignature signature) {
    this.attesting_indices = attesting_indices;
    this.data = data;
    this.signature = signature;
  }

  public List<UInt64> getAttestingIndices() {
    return attesting_indices;
  }

  public void setAttestingIndices(List<UInt64> attesting_indices) {
    this.attesting_indices = attesting_indices;
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

  public tech.pegasys.teku.spec.datastructures.operations.IndexedAttestation
      asInternalIndexedAttestation(final Spec spec) {
    return asInternalIndexedAttestation(spec.atSlot(data.slot));
  }

  public tech.pegasys.teku.spec.datastructures.operations.IndexedAttestation
      asInternalIndexedAttestation(final SpecVersion spec) {
    final IndexedAttestationSchema indexedAttestationSchema =
        spec.getSchemaDefinitions().getIndexedAttestationSchema();
    return indexedAttestationSchema.create(
        indexedAttestationSchema.getAttestingIndicesSchema().of(attesting_indices),
        data.asInternalAttestationData(),
        signature.asInternalBLSSignature());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof IndexedAttestation)) {
      return false;
    }
    IndexedAttestation that = (IndexedAttestation) o;
    return Objects.equals(attesting_indices, that.attesting_indices)
        && Objects.equals(data, that.data)
        && Objects.equals(signature, that.signature);
  }

  @Override
  public int hashCode() {
    return Objects.hash(attesting_indices, data, signature);
  }
}
