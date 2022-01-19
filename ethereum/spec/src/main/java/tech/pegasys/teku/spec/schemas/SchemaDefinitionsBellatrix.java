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
import tech.pegasys.teku.spec.config.SpecConfigBellatrix;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSchema;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockSchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.bellatrix.BeaconBlockBodySchemaBellatrix;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeaderSchema;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.bellatrix.BeaconStateBellatrix;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.bellatrix.BeaconStateSchemaBellatrix;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.bellatrix.MutableBeaconStateBellatrix;

public class SchemaDefinitionsBellatrix extends SchemaDefinitionsAltair {
  private final BeaconStateSchemaBellatrix beaconStateSchema;
  private final BeaconBlockBodySchemaBellatrix<?> beaconBlockBodySchema;
  private final BeaconBlockSchema beaconBlockSchema;
  private final SignedBeaconBlockSchema signedBeaconBlockSchema;
  private final ExecutionPayloadHeaderSchema executionPayloadHeaderSchema;

  public SchemaDefinitionsBellatrix(final SpecConfigBellatrix specConfig) {
    super(specConfig.toVersionAltair().orElseThrow());
    this.beaconStateSchema = BeaconStateSchemaBellatrix.create(specConfig);
    this.beaconBlockBodySchema = BeaconBlockBodySchemaBellatrix.create(specConfig);
    this.beaconBlockSchema = new BeaconBlockSchema(beaconBlockBodySchema);
    this.signedBeaconBlockSchema = new SignedBeaconBlockSchema(beaconBlockSchema);
    this.executionPayloadHeaderSchema = new ExecutionPayloadHeaderSchema(specConfig);
  }

  public static SchemaDefinitionsBellatrix required(final SchemaDefinitions schemaDefinitions) {
    checkArgument(
        schemaDefinitions instanceof SchemaDefinitionsBellatrix,
        "Expected definitions of type %s by got %s",
        SchemaDefinitionsBellatrix.class,
        schemaDefinitions.getClass());
    return (SchemaDefinitionsBellatrix) schemaDefinitions;
  }

  @Override
  public BeaconStateSchema<? extends BeaconStateBellatrix, ? extends MutableBeaconStateBellatrix>
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
  public Optional<SchemaDefinitionsBellatrix> toVersionBellatrix() {
    return Optional.of(this);
  }
}
