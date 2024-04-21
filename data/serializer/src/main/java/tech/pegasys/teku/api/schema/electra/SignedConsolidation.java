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

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Optional;
import tech.pegasys.teku.api.schema.BLSSignature;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;

public class SignedConsolidation {
  @JsonProperty("message")
  public final Consolidation message;

  @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES96)
  public final BLSSignature signature;

  public SignedConsolidation(
      @JsonProperty("message") final Consolidation message,
      @JsonProperty("signature") final BLSSignature signature) {
    this.message = message;
    this.signature = signature;
  }

  public SignedConsolidation(
      final tech.pegasys.teku.spec.datastructures.consolidations.SignedConsolidation
          internalConsolidation) {
    this.message = new Consolidation(internalConsolidation.getMessage());
    this.signature = new BLSSignature(internalConsolidation.getSignature());
  }

  public tech.pegasys.teku.spec.datastructures.consolidations.SignedConsolidation
      asInternalSignedConsolidation(final SpecVersion spec) {
    final Optional<SchemaDefinitionsElectra> schemaDefinitionsElectra =
        spec.getSchemaDefinitions().toVersionElectra();
    if (schemaDefinitionsElectra.isEmpty()) {
      throw new IllegalArgumentException(
          "Could not create PendingConsolidation for pre-electra spec");
    }
    return schemaDefinitionsElectra
        .get()
        .getSignedConsolidationSchema()
        .create(message.asInternalConsolidation(spec), signature.asInternalBLSSignature());
  }
}
