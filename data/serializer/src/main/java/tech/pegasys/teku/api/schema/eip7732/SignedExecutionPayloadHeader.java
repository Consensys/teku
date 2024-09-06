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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import tech.pegasys.teku.api.schema.BLSSignature;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsEip7732;

public class SignedExecutionPayloadHeader {
  private final ExecutionPayloadHeaderEip7732 message;

  @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES96)
  private final BLSSignature signature;

  public SignedExecutionPayloadHeader(
      final tech.pegasys.teku.spec.datastructures.execution.SignedExecutionPayloadHeader
          internalSignedExecutionPayloadHeader) {
    this.message =
        new ExecutionPayloadHeaderEip7732(
            tech.pegasys.teku.spec.datastructures.execution.versions.eip7732
                .ExecutionPayloadHeaderEip7732.required(
                internalSignedExecutionPayloadHeader.getMessage()));
    this.signature = new BLSSignature(internalSignedExecutionPayloadHeader.getSignature());
  }

  @JsonCreator
  public SignedExecutionPayloadHeader(
      @JsonProperty("message") final ExecutionPayloadHeaderEip7732 message,
      @JsonProperty("signature") final BLSSignature signature) {
    this.message = message;
    this.signature = signature;
  }

  public tech.pegasys.teku.spec.datastructures.execution.SignedExecutionPayloadHeader
      asInternalSignedExecutionPayloadHeader(final SpecVersion spec) {
    final SchemaDefinitionsEip7732 schemaDefinitions =
        SchemaDefinitionsEip7732.required(spec.getSchemaDefinitions());
    return schemaDefinitions
        .getSignedExecutionPayloadHeaderSchema()
        .create(
            message.asInternalExecutionPayloadHeader(
                schemaDefinitions.getExecutionPayloadHeaderSchema()),
            signature.asInternalBLSSignature());
  }

  public ExecutionPayloadHeaderEip7732 getMessage() {
    return message;
  }

  public BLSSignature getSignature() {
    return signature;
  }
}
