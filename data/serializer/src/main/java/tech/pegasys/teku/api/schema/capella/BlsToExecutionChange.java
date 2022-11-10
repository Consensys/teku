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

package tech.pegasys.teku.api.schema.capella;

import static tech.pegasys.teku.api.schema.SchemaConstants.DESCRIPTION_BYTES20;
import static tech.pegasys.teku.api.schema.SchemaConstants.DESCRIPTION_BYTES48;
import static tech.pegasys.teku.api.schema.SchemaConstants.EXAMPLE_PUBKEY;
import static tech.pegasys.teku.api.schema.SchemaConstants.PATTERN_BYTES20;
import static tech.pegasys.teku.api.schema.SchemaConstants.PATTERN_PUBKEY;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Optional;
import tech.pegasys.teku.api.schema.BLSPubKey;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsCapella;

public class BlsToExecutionChange {

  @JsonProperty("validator_index")
  @Schema(type = "string", format = "uint64")
  public final UInt64 validatorIndex;

  @JsonProperty("from_bls_pubkey")
  @Schema(
      type = "string",
      pattern = PATTERN_PUBKEY,
      example = EXAMPLE_PUBKEY,
      description = DESCRIPTION_BYTES48)
  public final BLSPubKey fromBlsPubkey;

  @JsonProperty("to_execution_address")
  @Schema(
      type = "string",
      format = "byte",
      pattern = PATTERN_BYTES20,
      description = DESCRIPTION_BYTES20)
  public final Bytes20 toExecutionAddress;

  @JsonCreator
  public BlsToExecutionChange(
      @JsonProperty("validator_index") final UInt64 validatorIndex,
      @JsonProperty("from_bls_pubkey") final BLSPubKey fromBlsPubkey,
      @JsonProperty("to_execution_address") final Bytes20 toExecutionAddress) {
    this.validatorIndex = validatorIndex;
    this.fromBlsPubkey = fromBlsPubkey;
    this.toExecutionAddress = toExecutionAddress;
  }

  public BlsToExecutionChange(
      final tech.pegasys.teku.spec.datastructures.operations.BlsToExecutionChange
          blsToExecutionChanges) {
    this.validatorIndex = blsToExecutionChanges.getValidatorIndex();
    this.fromBlsPubkey = new BLSPubKey(blsToExecutionChanges.getFromBlsPubkey());
    this.toExecutionAddress = blsToExecutionChanges.getToExecutionAddress();
  }

  public tech.pegasys.teku.spec.datastructures.operations.BlsToExecutionChange
      asInternalBlsToExecutionChange(final SpecVersion spec) {
    final Optional<SchemaDefinitionsCapella> schemaDefinitionsCapella =
        spec.getSchemaDefinitions().toVersionCapella();

    if (schemaDefinitionsCapella.isEmpty()) {
      throw new IllegalArgumentException(
          "Could not create BlsToExecutionChange for non-capella spec");
    }

    return schemaDefinitionsCapella
        .get()
        .getBlsToExecutionChangeSchema()
        .create(validatorIndex, fromBlsPubkey.asBLSPublicKey(), toExecutionAddress);
  }
}
