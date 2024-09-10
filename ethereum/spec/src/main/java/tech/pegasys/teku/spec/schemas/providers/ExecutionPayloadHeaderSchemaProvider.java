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

import static tech.pegasys.teku.spec.schemas.SchemaTypes.BEACON_STATE_SCHEMA;
import static tech.pegasys.teku.spec.schemas.SchemaTypes.EXECUTION_PAYLOAD_HEADER_SCHEMA;

import java.util.Set;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeaderSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.bellatrix.BeaconStateSchemaBellatrix;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.capella.BeaconStateSchemaCapella;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.deneb.BeaconStateSchemaDeneb;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateSchemaElectra;
import tech.pegasys.teku.spec.schemas.AbstractSchemaProvider;
import tech.pegasys.teku.spec.schemas.SchemaRegistry;

public class ExecutionPayloadHeaderSchemaProvider
    extends AbstractSchemaProvider<ExecutionPayloadHeaderSchema<? extends ExecutionPayloadHeader>> {

  public ExecutionPayloadHeaderSchemaProvider() {
    super(EXECUTION_PAYLOAD_HEADER_SCHEMA);
  }

  @Override
  protected ExecutionPayloadHeaderSchema<? extends ExecutionPayloadHeader> createSchema(
      final SchemaRegistry registry,
      final SpecMilestone effectiveMilestone,
      final SpecConfig specConfig) {
    return switch (effectiveMilestone) {
      case BELLATRIX ->
          BeaconStateSchemaBellatrix.required(registry.get(BEACON_STATE_SCHEMA))
              .getLastExecutionPayloadHeaderSchema();
      case CAPELLA ->
          BeaconStateSchemaCapella.required(registry.get(BEACON_STATE_SCHEMA))
              .getLastExecutionPayloadHeaderSchema();
      case DENEB ->
          BeaconStateSchemaDeneb.required(registry.get(BEACON_STATE_SCHEMA))
              .getLastExecutionPayloadHeaderSchema();
      case ELECTRA ->
          BeaconStateSchemaElectra.required(registry.get(BEACON_STATE_SCHEMA))
              .getLastExecutionPayloadHeaderSchema();
      default -> throw new IllegalStateException("Unsupported milestone: " + effectiveMilestone);
    };
  }

  @Override
  public Set<SpecMilestone> getSupportedMilestones() {
    return FROM_BELLATRIX;
  }
}
