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

package tech.pegasys.teku.spec.schemas.registry;

import static tech.pegasys.teku.spec.SpecMilestone.BELLATRIX;
import static tech.pegasys.teku.spec.SpecMilestone.CAPELLA;
import static tech.pegasys.teku.spec.SpecMilestone.DENEB;
import static tech.pegasys.teku.spec.SpecMilestone.ELECTRA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BEACON_STATE_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.EXECUTION_PAYLOAD_HEADER_SCHEMA;

import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeaderSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.bellatrix.BeaconStateSchemaBellatrix;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.capella.BeaconStateSchemaCapella;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.deneb.BeaconStateSchemaDeneb;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateSchemaElectra;

public class ExecutionPayloadHeaderSchemaProvider
    extends AbstractSchemaProvider<ExecutionPayloadHeaderSchema<? extends ExecutionPayloadHeader>> {

  public ExecutionPayloadHeaderSchemaProvider() {
    super(
        EXECUTION_PAYLOAD_HEADER_SCHEMA,
        milestoneSchema(
            BELLATRIX,
            ((registry, specConfig) ->
                BeaconStateSchemaBellatrix.required(registry.get(BEACON_STATE_SCHEMA))
                    .getLastExecutionPayloadHeaderSchema())),
        milestoneSchema(
            CAPELLA,
            ((registry, specConfig) ->
                BeaconStateSchemaCapella.required(registry.get(BEACON_STATE_SCHEMA))
                    .getLastExecutionPayloadHeaderSchema())),
        milestoneSchema(
            DENEB,
            ((registry, specConfig) ->
                BeaconStateSchemaDeneb.required(registry.get(BEACON_STATE_SCHEMA))
                    .getLastExecutionPayloadHeaderSchema())),
        milestoneSchema(
            ELECTRA,
            ((registry, specConfig) ->
                BeaconStateSchemaElectra.required(registry.get(BEACON_STATE_SCHEMA))
                    .getLastExecutionPayloadHeaderSchema())));
  }
}
