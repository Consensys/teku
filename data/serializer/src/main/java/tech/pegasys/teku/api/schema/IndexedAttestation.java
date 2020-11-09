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

import static tech.pegasys.teku.api.schema.SchemaConstants.DESCRIPTION_BYTES96;
import static tech.pegasys.teku.util.config.Constants.MAX_VALIDATORS_PER_COMMITTEE;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;

public class IndexedAttestation {
  @ArraySchema(schema = @Schema(type = "string", format = "uint64"))
  public final List<UInt64> attesting_indices;

  public final AttestationData data;

  @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES96)
  public final BLSSignature signature;

  public IndexedAttestation(
      tech.pegasys.teku.datastructures.operations.IndexedAttestation indexedAttestation) {
    this.attesting_indices =
        indexedAttestation.getAttesting_indices().stream().collect(Collectors.toList());
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

  public tech.pegasys.teku.datastructures.operations.IndexedAttestation
      asInternalIndexedAttestation() {
    return new tech.pegasys.teku.datastructures.operations.IndexedAttestation(
        SSZList.createMutable(attesting_indices, MAX_VALIDATORS_PER_COMMITTEE, UInt64.class),
        data.asInternalAttestationData(),
        signature.asInternalBLSSignature());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof IndexedAttestation)) return false;
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
