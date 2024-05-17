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

package tech.pegasys.teku.spec.schemas;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Optional;
import tech.pegasys.teku.spec.config.SpecConfigEip7594;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.CellSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecarSchema;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSchema;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainerSchema;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockSchema;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainerSchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodyBuilder;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.eip7594.BeaconBlockBodyBuilderEip7594;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.eip7594.BeaconBlockBodySchemaEip7594;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.eip7594.BeaconBlockBodySchemaEip7594Impl;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.eip7594.BlindedBeaconBlockBodySchemaEip7594Impl;
import tech.pegasys.teku.spec.datastructures.blocks.versions.deneb.BlockContentsSchema;
import tech.pegasys.teku.spec.datastructures.blocks.versions.deneb.SignedBlockContentsSchema;
import tech.pegasys.teku.spec.datastructures.builder.BlobsBundleSchema;
import tech.pegasys.teku.spec.datastructures.builder.BuilderBidSchema;
import tech.pegasys.teku.spec.datastructures.builder.BuilderPayloadSchema;
import tech.pegasys.teku.spec.datastructures.builder.ExecutionPayloadAndBlobsBundleSchema;
import tech.pegasys.teku.spec.datastructures.builder.SignedBuilderBidSchema;
import tech.pegasys.teku.spec.datastructures.builder.versions.deneb.BuilderBidSchemaDeneb;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeaderSchema;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip7594.ExecutionPayloadHeaderSchemaEip7594;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip7594.ExecutionPayloadSchemaEip7594;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnSidecarsByRangeRequestMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnSidecarsByRootRequestMessageSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.eip7594.BeaconStateEip7594;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.eip7594.BeaconStateSchemaEip7594;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.eip7594.MutableBeaconStateEip7594;

public class SchemaDefinitionsEip7594 extends SchemaDefinitionsDeneb {

  private final BeaconStateSchemaEip7594 beaconStateSchema;

  private final ExecutionPayloadSchemaEip7594 executionPayloadSchemaEip7594;
  private final ExecutionPayloadHeaderSchemaEip7594 executionPayloadHeaderSchemaEip7594;

  private final BeaconBlockBodySchemaEip7594Impl beaconBlockBodySchema;
  private final BlindedBeaconBlockBodySchemaEip7594Impl blindedBeaconBlockBodySchema;

  private final BeaconBlockSchema beaconBlockSchema;
  private final BeaconBlockSchema blindedBeaconBlockSchema;
  private final SignedBeaconBlockSchema signedBeaconBlockSchema;
  private final SignedBeaconBlockSchema signedBlindedBeaconBlockSchema;

  private final BuilderBidSchema<?> builderBidSchemaEip7594;
  private final SignedBuilderBidSchema signedBuilderBidSchemaEip7594;

  private final BlockContentsSchema blockContentsSchema;
  private final SignedBlockContentsSchema signedBlockContentsSchema;
  private final BlobsBundleSchema blobsBundleSchema;
  private final ExecutionPayloadAndBlobsBundleSchema executionPayloadAndBlobsBundleSchema;

  private final CellSchema cellSchema;
  private final DataColumnSchema dataColumnSchema;
  private final DataColumnSidecarSchema dataColumnSidecarSchema;
  private final DataColumnSidecarsByRootRequestMessageSchema
      dataColumnSidecarsByRootRequestMessageSchema;
  private final DataColumnSidecarsByRangeRequestMessage
          .DataColumnSidecarsByRangeRequestMessageSchema
      dataColumnSidecarsByRangeRequestMessageSchema;

  public SchemaDefinitionsEip7594(final SpecConfigEip7594 specConfig) {
    super(specConfig);
    this.executionPayloadSchemaEip7594 = new ExecutionPayloadSchemaEip7594(specConfig);

    this.beaconStateSchema = BeaconStateSchemaEip7594.create(specConfig);
    this.executionPayloadHeaderSchemaEip7594 =
        beaconStateSchema.getLastExecutionPayloadHeaderSchema();
    this.beaconBlockBodySchema =
        BeaconBlockBodySchemaEip7594Impl.create(
            specConfig,
            getAttesterSlashingSchema(),
            getSignedBlsToExecutionChangeSchema(),
            getBlobKzgCommitmentsSchema(),
            "BeaconBlockBodyEip7594");
    this.blindedBeaconBlockBodySchema =
        BlindedBeaconBlockBodySchemaEip7594Impl.create(
            specConfig,
            getAttesterSlashingSchema(),
            getSignedBlsToExecutionChangeSchema(),
            getBlobKzgCommitmentsSchema(),
            "BlindedBlockBodyEip7594");
    this.beaconBlockSchema = new BeaconBlockSchema(beaconBlockBodySchema, "BeaconBlockEip7594");
    this.blindedBeaconBlockSchema =
        new BeaconBlockSchema(blindedBeaconBlockBodySchema, "BlindedBlockEip7594");
    this.signedBeaconBlockSchema =
        new SignedBeaconBlockSchema(beaconBlockSchema, "SignedBeaconBlockEip7594");
    this.signedBlindedBeaconBlockSchema =
        new SignedBeaconBlockSchema(blindedBeaconBlockSchema, "SignedBlindedBlockEip7594");
    this.builderBidSchemaEip7594 =
        new BuilderBidSchemaDeneb(
            "BuilderBidEip7594",
            executionPayloadHeaderSchemaEip7594,
            getBlobKzgCommitmentsSchema());
    this.signedBuilderBidSchemaEip7594 =
        new SignedBuilderBidSchema("SignedBuilderBidEip7594", builderBidSchemaEip7594);

    this.blockContentsSchema =
        BlockContentsSchema.create(
            specConfig, beaconBlockSchema, getBlobSchema(), "BlockContentsEip7594");
    this.signedBlockContentsSchema =
        SignedBlockContentsSchema.create(
            specConfig, signedBeaconBlockSchema, getBlobSchema(), "SignedBlockContentsEip7594");
    this.blobsBundleSchema =
        new BlobsBundleSchema(
            "BlobsBundleEip7594", getBlobSchema(), getBlobKzgCommitmentsSchema(), specConfig);
    this.executionPayloadAndBlobsBundleSchema =
        new ExecutionPayloadAndBlobsBundleSchema(executionPayloadSchemaEip7594, blobsBundleSchema);

    this.cellSchema = new CellSchema(specConfig);
    this.dataColumnSchema = new DataColumnSchema(specConfig);
    this.dataColumnSidecarSchema =
        DataColumnSidecarSchema.create(
            SignedBeaconBlockHeader.SSZ_SCHEMA, dataColumnSchema, specConfig);
    this.dataColumnSidecarsByRootRequestMessageSchema =
        new DataColumnSidecarsByRootRequestMessageSchema(specConfig);
    this.dataColumnSidecarsByRangeRequestMessageSchema =
        new DataColumnSidecarsByRangeRequestMessage.DataColumnSidecarsByRangeRequestMessageSchema(
            specConfig);
  }

  public static SchemaDefinitionsEip7594 required(final SchemaDefinitions schemaDefinitions) {
    checkArgument(
        schemaDefinitions instanceof SchemaDefinitionsEip7594,
        "Expected definitions of type %s by got %s",
        SchemaDefinitionsEip7594.class,
        schemaDefinitions.getClass());
    return (SchemaDefinitionsEip7594) schemaDefinitions;
  }

  @Override
  public BeaconStateSchema<? extends BeaconStateEip7594, ? extends MutableBeaconStateEip7594>
      getBeaconStateSchema() {
    return beaconStateSchema;
  }

  @Override
  public BeaconBlockBodySchemaEip7594<?> getBeaconBlockBodySchema() {
    return beaconBlockBodySchema;
  }

  @Override
  public BeaconBlockBodySchema<?> getBlindedBeaconBlockBodySchema() {
    return blindedBeaconBlockBodySchema;
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
  public SignedBeaconBlockSchema getSignedBeaconBlockSchema() {
    return signedBeaconBlockSchema;
  }

  @Override
  public SignedBeaconBlockSchema getSignedBlindedBeaconBlockSchema() {
    return signedBlindedBeaconBlockSchema;
  }

  @Override
  public BlockContainerSchema<BlockContainer> getBlockContainerSchema() {
    return getBlockContentsSchema().castTypeToBlockContainer();
  }

  @Override
  public BlockContainerSchema<BlockContainer> getBlindedBlockContainerSchema() {
    return getBlindedBeaconBlockSchema().castTypeToBlockContainer();
  }

  @Override
  public SignedBlockContainerSchema<SignedBlockContainer> getSignedBlockContainerSchema() {
    return getSignedBlockContentsSchema().castTypeToSignedBlockContainer();
  }

  @Override
  public SignedBlockContainerSchema<SignedBlockContainer> getSignedBlindedBlockContainerSchema() {
    return getSignedBlindedBeaconBlockSchema().castTypeToSignedBlockContainer();
  }

  @Override
  public ExecutionPayloadSchema<?> getExecutionPayloadSchema() {
    return executionPayloadSchemaEip7594;
  }

  @Override
  public ExecutionPayloadHeaderSchema<?> getExecutionPayloadHeaderSchema() {
    return executionPayloadHeaderSchemaEip7594;
  }

  @Override
  public BuilderBidSchema<?> getBuilderBidSchema() {
    return builderBidSchemaEip7594;
  }

  @Override
  public SignedBuilderBidSchema getSignedBuilderBidSchema() {
    return signedBuilderBidSchemaEip7594;
  }

  @Override
  public BuilderPayloadSchema<?> getBuilderPayloadSchema() {
    return getExecutionPayloadAndBlobsBundleSchema();
  }

  @Override
  public BeaconBlockBodyBuilder createBeaconBlockBodyBuilder() {
    return new BeaconBlockBodyBuilderEip7594(beaconBlockBodySchema, blindedBeaconBlockBodySchema);
  }

  @Override
  public BlockContentsSchema getBlockContentsSchema() {
    return blockContentsSchema;
  }

  @Override
  public SignedBlockContentsSchema getSignedBlockContentsSchema() {
    return signedBlockContentsSchema;
  }

  @Override
  public BlobsBundleSchema getBlobsBundleSchema() {
    return blobsBundleSchema;
  }

  @Override
  public ExecutionPayloadAndBlobsBundleSchema getExecutionPayloadAndBlobsBundleSchema() {
    return executionPayloadAndBlobsBundleSchema;
  }

  @Override
  public Optional<SchemaDefinitionsEip7594> toVersionEip7594() {
    return Optional.of(this);
  }

  public DataColumnSidecarSchema getDataColumnSidecarSchema() {
    return dataColumnSidecarSchema;
  }

  public DataColumnSchema getDataColumnSchema() {
    return dataColumnSchema;
  }

  public CellSchema getCellSchema() {
    return cellSchema;
  }

  public DataColumnSidecarsByRootRequestMessageSchema
      getDataColumnSidecarsByRootRequestMessageSchema() {
    return dataColumnSidecarsByRootRequestMessageSchema;
  }

  public DataColumnSidecarsByRangeRequestMessage.DataColumnSidecarsByRangeRequestMessageSchema
      getDataColumnSidecarsByRangeRequestMessageSchema() {
    return dataColumnSidecarsByRangeRequestMessageSchema;
  }
}
