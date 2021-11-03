/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.spec.schemas;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Optional;
import tech.pegasys.teku.spec.config.SpecConfigMerge;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSchema;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockSchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.merge.BeaconBlockBodySchemaMerge;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeaderSchema;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.merge.BeaconStateMerge;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.merge.BeaconStateSchemaMerge;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.merge.MutableBeaconStateMerge;

public class SchemaDefinitionsMerge extends SchemaDefinitionsAltair {
  private final BeaconStateSchemaMerge beaconStateSchema;
  private final BeaconBlockBodySchemaMerge<?> beaconBlockBodySchema;
  private final BeaconBlockSchema beaconBlockSchema;
  private final SignedBeaconBlockSchema signedBeaconBlockSchema;
  private final ExecutionPayloadHeaderSchema executionPayloadHeaderSchema;

  public SchemaDefinitionsMerge(final SpecConfigMerge specConfig) {
    super(specConfig.toVersionAltair().orElseThrow());
    this.beaconStateSchema = BeaconStateSchemaMerge.create(specConfig);
    this.beaconBlockBodySchema = BeaconBlockBodySchemaMerge.create(specConfig);
    this.beaconBlockSchema = new BeaconBlockSchema(beaconBlockBodySchema);
    this.signedBeaconBlockSchema = new SignedBeaconBlockSchema(beaconBlockSchema);
    this.executionPayloadHeaderSchema = new ExecutionPayloadHeaderSchema(specConfig);
  }

  public static SchemaDefinitionsMerge required(final SchemaDefinitions schemaDefinitions) {
    checkArgument(
        schemaDefinitions instanceof SchemaDefinitionsMerge,
        "Expected definitions of type %s by got %s",
        SchemaDefinitionsMerge.class,
        schemaDefinitions.getClass());
    return (SchemaDefinitionsMerge) schemaDefinitions;
  }

  @Override
  public BeaconStateSchema<? extends BeaconStateMerge, ? extends MutableBeaconStateMerge>
      getBeaconStateSchema() {
    return beaconStateSchema;
  }

  @Override
  public SignedBeaconBlockSchema getSignedBeaconBlockSchema() {
    return signedBeaconBlockSchema;
  }

  @Override
  public BeaconBlockSchema getBeaconBlockSchema() {
    return beaconBlockSchema;
  }

  @Override
  public BeaconBlockBodySchema<?> getBeaconBlockBodySchema() {
    return beaconBlockBodySchema;
  }

  public ExecutionPayloadSchema getExecutionPayloadSchema() {
    return beaconBlockBodySchema.getExecutionPayloadSchema();
  }

  public ExecutionPayloadHeaderSchema getExecutionPayloadHeaderSchema() {
    return executionPayloadHeaderSchema;
  }

  @Override
  public Optional<SchemaDefinitionsMerge> toVersionMerge() {
    return Optional.of(this);
  }
}
