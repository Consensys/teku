/*
 * Copyright Consensys Software Inc., 2022
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
import static tech.pegasys.teku.spec.schemas.SchemaTypes.BLINDED_BEACON_BLOCK_BODY_SCHEMA;
import static tech.pegasys.teku.spec.schemas.SchemaTypes.BLINDED_BEACON_BLOCK_SCHEMA;
import static tech.pegasys.teku.spec.schemas.SchemaTypes.EXECUTION_PAYLOAD_HEADER_SCHEMA;
import static tech.pegasys.teku.spec.schemas.SchemaTypes.EXECUTION_PAYLOAD_SCHEMA;
import static tech.pegasys.teku.spec.schemas.SchemaTypes.SIGNED_BLINDED_BEACON_BLOCK_SCHEMA;

import java.util.Optional;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSchema;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainerSchema;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockSchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodyBuilder;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.bellatrix.BeaconBlockBodyBuilderBellatrix;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.bellatrix.BlindedBeaconBlockBodyBellatrix;
import tech.pegasys.teku.spec.datastructures.builder.BuilderBidSchema;
import tech.pegasys.teku.spec.datastructures.builder.BuilderPayloadSchema;
import tech.pegasys.teku.spec.datastructures.builder.SignedBuilderBidSchema;
import tech.pegasys.teku.spec.datastructures.builder.versions.bellatrix.BuilderBidSchemaBellatrix;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeaderSchema;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSchema;

public class SchemaDefinitionsBellatrix extends SchemaDefinitionsAltair {

  private final BuilderBidSchema<?> builderBidSchema;
  private final SignedBuilderBidSchema signedBuilderBidSchema;

  public SchemaDefinitionsBellatrix(final SchemaRegistry schemaRegistry) {
    super(schemaRegistry);

    this.builderBidSchema =
        new BuilderBidSchemaBellatrix("BuilderBidBellatrix", getExecutionPayloadHeaderSchema());
    this.signedBuilderBidSchema =
        new SignedBuilderBidSchema("SignedBuilderBidBellatrix", builderBidSchema);
  }

  public static SchemaDefinitionsBellatrix required(final SchemaDefinitions schemaDefinitions) {
    checkArgument(
        schemaDefinitions instanceof SchemaDefinitionsBellatrix,
        "Expected definitions of type %s but got %s",
        SchemaDefinitionsBellatrix.class,
        schemaDefinitions.getClass());
    return (SchemaDefinitionsBellatrix) schemaDefinitions;
  }

  @Override
  public BeaconBlockSchema getBlindedBeaconBlockSchema() {
    return schemaRegistry.get(BLINDED_BEACON_BLOCK_SCHEMA);
  }

  @Override
  public BeaconBlockBodySchema<? extends BlindedBeaconBlockBodyBellatrix>
      getBlindedBeaconBlockBodySchema() {
    return schemaRegistry.get(BLINDED_BEACON_BLOCK_BODY_SCHEMA);
  }

  @Override
  public SignedBeaconBlockSchema getSignedBlindedBeaconBlockSchema() {
    return schemaRegistry.get(SIGNED_BLINDED_BEACON_BLOCK_SCHEMA);
  }

  @Override
  public BlockContainerSchema<BlockContainer> getBlindedBlockContainerSchema() {
    return getBlindedBeaconBlockSchema().castTypeToBlockContainer();
  }

  @Override
  public BeaconBlockBodyBuilder createBeaconBlockBodyBuilder() {
    return new BeaconBlockBodyBuilderBellatrix(
        getBeaconBlockBodySchema().toVersionBellatrix().orElseThrow(),
        getBlindedBeaconBlockBodySchema());
  }

  public ExecutionPayloadSchema<?> getExecutionPayloadSchema() {
    return schemaRegistry.get(EXECUTION_PAYLOAD_SCHEMA);
  }

  public ExecutionPayloadHeaderSchema<?> getExecutionPayloadHeaderSchema() {
    return schemaRegistry.get(EXECUTION_PAYLOAD_HEADER_SCHEMA);
  }

  public BuilderBidSchema<?> getBuilderBidSchema() {
    return builderBidSchema;
  }

  public SignedBuilderBidSchema getSignedBuilderBidSchema() {
    return signedBuilderBidSchema;
  }

  public BuilderPayloadSchema<?> getBuilderPayloadSchema() {
    return getExecutionPayloadSchema();
  }

  @Override
  public Optional<SchemaDefinitionsBellatrix> toVersionBellatrix() {
    return Optional.of(this);
  }
}
