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

package tech.pegasys.teku.spec.schemas.providers;

import static tech.pegasys.teku.spec.SpecMilestone.BELLATRIX;
import static tech.pegasys.teku.spec.SpecMilestone.CAPELLA;
import static tech.pegasys.teku.spec.SpecMilestone.DENEB;
import static tech.pegasys.teku.spec.SpecMilestone.ELECTRA;
import static tech.pegasys.teku.spec.schemas.SchemaTypes.EXECUTION_PAYLOAD_SCHEMA;

import java.util.Set;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigBellatrix;
import tech.pegasys.teku.spec.config.SpecConfigCapella;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.bellatrix.ExecutionPayloadSchemaBellatrix;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.ExecutionPayloadSchemaCapella;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.ExecutionPayloadSchemaDeneb;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionPayloadSchemaElectra;
import tech.pegasys.teku.spec.schemas.AbstractSchemaProvider;
import tech.pegasys.teku.spec.schemas.SchemaRegistry;

public class ExecutionPayloadSchemaProvider
    extends AbstractSchemaProvider<ExecutionPayloadSchema<? extends ExecutionPayload>> {

  public ExecutionPayloadSchemaProvider() {
    super(EXECUTION_PAYLOAD_SCHEMA);
  }

  @Override
  protected ExecutionPayloadSchema<? extends ExecutionPayload> createSchema(
      final SchemaRegistry registry,
      final SpecMilestone effectiveMilestone,
      final SpecConfig specConfig) {
    return switch (effectiveMilestone) {
      case BELLATRIX ->
          new ExecutionPayloadSchemaBellatrix(SpecConfigBellatrix.required(specConfig));
      case CAPELLA -> new ExecutionPayloadSchemaCapella(SpecConfigCapella.required(specConfig));
      case DENEB -> new ExecutionPayloadSchemaDeneb(SpecConfigDeneb.required(specConfig));
      case ELECTRA -> new ExecutionPayloadSchemaElectra(SpecConfigElectra.required(specConfig));
      default -> throw new IllegalStateException("");
    };
  }

  @Override
  public Set<SpecMilestone> getSupportedMilestones() {
    return FROM_BELLATRIX;
  }
}
