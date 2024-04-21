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

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;

public class Consolidation {
  @JsonProperty("source_index")
  public final int sourceIndex;

  @JsonProperty("target_index")
  public final int targetIndex;

  @JsonProperty("epoch")
  public final UInt64 epoch;

  public Consolidation(
      @JsonProperty("source_index") final int sourceIndex,
      @JsonProperty("target_index") final int targetIndex,
      @JsonProperty("epoch") final UInt64 epoch) {
    this.sourceIndex = sourceIndex;
    this.targetIndex = targetIndex;
    this.epoch = epoch;
  }

  public Consolidation(
      final tech.pegasys.teku.spec.datastructures.consolidations.Consolidation
          internalConsolidation) {
    this.sourceIndex = internalConsolidation.getSourceIndex();
    this.targetIndex = internalConsolidation.getTargetIndex();
    this.epoch = internalConsolidation.getEpoch();
  }

  public tech.pegasys.teku.spec.datastructures.consolidations.Consolidation asInternalConsolidation(
      final SpecVersion spec) {
    final Optional<SchemaDefinitionsElectra> schemaDefinitionsElectra =
        spec.getSchemaDefinitions().toVersionElectra();
    if (schemaDefinitionsElectra.isEmpty()) {
      throw new IllegalArgumentException(
          "Could not create PendingConsolidation for pre-electra spec");
    }
    return schemaDefinitionsElectra
        .get()
        .getConsolidationSchema()
        .create(
            SszUInt64.of(UInt64.valueOf(this.sourceIndex)),
            SszUInt64.of(UInt64.valueOf(this.targetIndex)),
            SszUInt64.of(epoch));
  }
}
