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
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.bellatrix.BeaconBlockBodySchemaBellatrixImpl;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.bellatrix.BlindedBeaconBlockBodySchemaBellatrix;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.bellatrix.BlindedBeaconBlockBodySchemaBellatrixImpl;
import tech.pegasys.teku.spec.datastructures.execution.BuilderBidV1Schema;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeaderSchema;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSchema;
import tech.pegasys.teku.spec.datastructures.execution.SignedBuilderBidV1Schema;
import tech.pegasys.teku.spec.datastructures.execution.SignedValidatorRegistrationV1Schema;
import tech.pegasys.teku.spec.datastructures.execution.ValidatorRegistrationV1Schema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.bellatrix.BeaconStateBellatrix;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.bellatrix.BeaconStateSchemaBellatrix;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.bellatrix.MutableBeaconStateBellatrix;

public class SchemaDefinitionsBellatrix extends SchemaDefinitionsAltair {
  private final BeaconStateSchemaBellatrix beaconStateSchema;
  private final BeaconBlockBodySchemaBellatrix<?> beaconBlockBodySchema;
  private final BlindedBeaconBlockBodySchemaBellatrix<?> blindedBeaconBlockBodySchema;
  private final BeaconBlockSchema beaconBlockSchema;
  private final BeaconBlockSchema blindedBeaconBlockSchema;
  private final SignedBeaconBlockSchema signedBeaconBlockSchema;
  private final SignedBeaconBlockSchema signedBlindedBeaconBlockSchema;
  private final ExecutionPayloadHeaderSchema executionPayloadHeaderSchema;
  private final BuilderBidV1Schema builderBidV1Schema;
  private final SignedBuilderBidV1Schema signedBuilderBidV1Schema;
  private final ValidatorRegistrationV1Schema validatorRegistrationSchema;
  private final SignedValidatorRegistrationV1Schema signedValidatorRegistrationSchema;

  public SchemaDefinitionsBellatrix(final SpecConfigBellatrix specConfig) {
    super(specConfig.toVersionAltair().orElseThrow());
    this.beaconStateSchema = BeaconStateSchemaBellatrix.create(specConfig);
    this.beaconBlockBodySchema =
        BeaconBlockBodySchemaBellatrixImpl.create(
            specConfig, getAttesterSlashingSchema(), "BeaconBlockBodyBellatrix");
    this.blindedBeaconBlockBodySchema =
        BlindedBeaconBlockBodySchemaBellatrixImpl.create(
            specConfig, getAttesterSlashingSchema(), "BlindedBlockBodyBellatrix");
    this.beaconBlockSchema = new BeaconBlockSchema(beaconBlockBodySchema, "BeaconBlockBellatrix");
    this.blindedBeaconBlockSchema =
        new BeaconBlockSchema(blindedBeaconBlockBodySchema, "BlindedBlockBellatrix");
    this.signedBeaconBlockSchema =
        new SignedBeaconBlockSchema(beaconBlockSchema, "SignedBeaconBlockBellatrix");
    this.signedBlindedBeaconBlockSchema =
        new SignedBeaconBlockSchema(blindedBeaconBlockSchema, "SignedBlindedBlockBellatrix");
    this.executionPayloadHeaderSchema = new ExecutionPayloadHeaderSchema(specConfig);
    this.builderBidV1Schema = new BuilderBidV1Schema(executionPayloadHeaderSchema);
    this.signedBuilderBidV1Schema = new SignedBuilderBidV1Schema(builderBidV1Schema);
    this.validatorRegistrationSchema = new ValidatorRegistrationV1Schema();
    this.signedValidatorRegistrationSchema =
        new SignedValidatorRegistrationV1Schema(validatorRegistrationSchema);
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
  public BeaconBlockSchema getBlindedBeaconBlockSchema() {
    return blindedBeaconBlockSchema;
  }

  @Override
  public BeaconBlockBodySchema<?> getBlindedBeaconBlockBodySchema() {
    return blindedBeaconBlockBodySchema;
  }

  @Override
  public BeaconBlockBodySchema<?> getBeaconBlockBodySchema() {
    return beaconBlockBodySchema;
  }

  @Override
  public SignedBeaconBlockSchema getSignedBlindedBeaconBlockSchema() {
    return signedBlindedBeaconBlockSchema;
  }

  public ExecutionPayloadSchema getExecutionPayloadSchema() {
    return beaconBlockBodySchema.getExecutionPayloadSchema();
  }

  public ExecutionPayloadHeaderSchema getExecutionPayloadHeaderSchema() {
    return executionPayloadHeaderSchema;
  }

  public BuilderBidV1Schema getBuilderBidV1Schema() {
    return builderBidV1Schema;
  }

  public SignedBuilderBidV1Schema getSignedBuilderBidV1Schema() {
    return signedBuilderBidV1Schema;
  }

  public ValidatorRegistrationV1Schema getValidatorRegistrationSchema() {
    return validatorRegistrationSchema;
  }

  public SignedValidatorRegistrationV1Schema getSignedValidatorRegistrationSchema() {
    return signedValidatorRegistrationSchema;
  }

  @Override
  public Optional<SchemaDefinitionsBellatrix> toVersionBellatrix() {
    return Optional.of(this);
  }
}
