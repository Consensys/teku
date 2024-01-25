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

import java.util.Optional;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobKzgCommitmentsSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecarSchema;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSchema;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainerSchema;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockSchema;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainerSchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodyBuilder;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.BeaconBlockBodyBuilderDeneb;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.BeaconBlockBodySchemaDenebImpl;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.BlindedBeaconBlockBodySchemaDenebImpl;
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
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.ExecutionPayloadHeaderSchemaDeneb;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.ExecutionPayloadSchemaDeneb;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobSidecarsByRootRequestMessageSchema;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChangeSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.deneb.BeaconStateDeneb;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.deneb.BeaconStateSchemaDeneb;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.deneb.MutableBeaconStateDeneb;

public class SchemaDefinitionsDeneb extends SchemaDefinitionsCapella {

  private final BeaconStateSchemaDeneb beaconStateSchema;

  private final ExecutionPayloadSchemaDeneb executionPayloadSchemaDeneb;
  private final ExecutionPayloadHeaderSchemaDeneb executionPayloadHeaderSchemaDeneb;

  private final BlobKzgCommitmentsSchema blobKzgCommitmentsSchema;

  private final BeaconBlockBodySchemaDenebImpl beaconBlockBodySchema;
  private final BlindedBeaconBlockBodySchemaDenebImpl blindedBeaconBlockBodySchema;

  private final BeaconBlockSchema beaconBlockSchema;
  private final BeaconBlockSchema blindedBeaconBlockSchema;
  private final SignedBeaconBlockSchema signedBeaconBlockSchema;
  private final SignedBeaconBlockSchema signedBlindedBeaconBlockSchema;

  private final BuilderBidSchema<?> builderBidSchemaDeneb;
  private final SignedBuilderBidSchema signedBuilderBidSchemaDeneb;

  private final BlobSchema blobSchema;
  private final SszListSchema<Blob, ? extends SszList<Blob>> blobsInBlockSchema;
  private final BlobSidecarSchema blobSidecarSchema;
  private final BlockContentsSchema blockContentsSchema;
  private final SignedBlockContentsSchema signedBlockContentsSchema;
  private final BlobsBundleSchema blobsBundleSchema;
  private final ExecutionPayloadAndBlobsBundleSchema executionPayloadAndBlobsBundleSchema;
  private final BlobSidecarsByRootRequestMessageSchema blobSidecarsByRootRequestMessageSchema;

  public SchemaDefinitionsDeneb(final SpecConfigDeneb specConfig) {
    super(specConfig);
    this.executionPayloadSchemaDeneb = new ExecutionPayloadSchemaDeneb(specConfig);
    final SignedBlsToExecutionChangeSchema signedBlsToExecutionChangeSchema =
        new SignedBlsToExecutionChangeSchema();

    this.beaconStateSchema = BeaconStateSchemaDeneb.create(specConfig);
    this.executionPayloadHeaderSchemaDeneb =
        beaconStateSchema.getLastExecutionPayloadHeaderSchema();
    this.blobKzgCommitmentsSchema = new BlobKzgCommitmentsSchema(specConfig);
    this.beaconBlockBodySchema =
        BeaconBlockBodySchemaDenebImpl.create(
            specConfig,
            getAttesterSlashingSchema(),
            signedBlsToExecutionChangeSchema,
            blobKzgCommitmentsSchema,
            "BeaconBlockBodyDeneb");
    this.blindedBeaconBlockBodySchema =
        BlindedBeaconBlockBodySchemaDenebImpl.create(
            specConfig,
            getAttesterSlashingSchema(),
            signedBlsToExecutionChangeSchema,
            blobKzgCommitmentsSchema,
            "BlindedBlockBodyDeneb");
    this.beaconBlockSchema = new BeaconBlockSchema(beaconBlockBodySchema, "BeaconBlockDeneb");
    this.blindedBeaconBlockSchema =
        new BeaconBlockSchema(blindedBeaconBlockBodySchema, "BlindedBlockDeneb");
    this.signedBeaconBlockSchema =
        new SignedBeaconBlockSchema(beaconBlockSchema, "SignedBeaconBlockDeneb");
    this.signedBlindedBeaconBlockSchema =
        new SignedBeaconBlockSchema(blindedBeaconBlockSchema, "SignedBlindedBlockDeneb");
    this.builderBidSchemaDeneb =
        new BuilderBidSchemaDeneb(
            "BuilderBidDeneb", executionPayloadHeaderSchemaDeneb, blobKzgCommitmentsSchema);
    this.signedBuilderBidSchemaDeneb =
        new SignedBuilderBidSchema("SignedBuilderBidDeneb", builderBidSchemaDeneb);

    this.blobSchema = new BlobSchema(specConfig);
    this.blobsInBlockSchema = SszListSchema.create(blobSchema, specConfig.getMaxBlobsPerBlock());
    this.blobSidecarSchema =
        BlobSidecarSchema.create(
            SignedBeaconBlockHeader.SSZ_SCHEMA,
            blobSchema,
            specConfig.getKzgCommitmentInclusionProofDepth());
    this.blockContentsSchema =
        BlockContentsSchema.create(specConfig, beaconBlockSchema, blobSchema, "BlockContentsDeneb");
    this.signedBlockContentsSchema =
        SignedBlockContentsSchema.create(
            specConfig, signedBeaconBlockSchema, blobSchema, "SignedBlockContentsDeneb");
    this.blobsBundleSchema =
        new BlobsBundleSchema("BlobsBundleDeneb", blobSchema, blobKzgCommitmentsSchema, specConfig);
    this.executionPayloadAndBlobsBundleSchema =
        new ExecutionPayloadAndBlobsBundleSchema(executionPayloadSchemaDeneb, blobsBundleSchema);
    this.blobSidecarsByRootRequestMessageSchema =
        new BlobSidecarsByRootRequestMessageSchema(specConfig);
  }

  public static SchemaDefinitionsDeneb required(final SchemaDefinitions schemaDefinitions) {
    checkArgument(
        schemaDefinitions instanceof SchemaDefinitionsDeneb,
        "Expected definitions of type %s by got %s",
        SchemaDefinitionsDeneb.class,
        schemaDefinitions.getClass());
    return (SchemaDefinitionsDeneb) schemaDefinitions;
  }

  @Override
  public BeaconStateSchema<? extends BeaconStateDeneb, ? extends MutableBeaconStateDeneb>
      getBeaconStateSchema() {
    return beaconStateSchema;
  }

  @Override
  public BeaconBlockBodySchema<?> getBeaconBlockBodySchema() {
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
    return executionPayloadSchemaDeneb;
  }

  @Override
  public ExecutionPayloadHeaderSchema<?> getExecutionPayloadHeaderSchema() {
    return executionPayloadHeaderSchemaDeneb;
  }

  @Override
  public BuilderBidSchema<?> getBuilderBidSchema() {
    return builderBidSchemaDeneb;
  }

  @Override
  public SignedBuilderBidSchema getSignedBuilderBidSchema() {
    return signedBuilderBidSchemaDeneb;
  }

  @Override
  public BuilderPayloadSchema<?> getBuilderPayloadSchema() {
    return getExecutionPayloadAndBlobsBundleSchema();
  }

  @Override
  public BeaconBlockBodyBuilder createBeaconBlockBodyBuilder() {
    return new BeaconBlockBodyBuilderDeneb(beaconBlockBodySchema, blindedBeaconBlockBodySchema);
  }

  public BlobSchema getBlobSchema() {
    return blobSchema;
  }

  public BlobKzgCommitmentsSchema getBlobKzgCommitmentsSchema() {
    return blobKzgCommitmentsSchema;
  }

  public SszListSchema<Blob, ? extends SszList<Blob>> getBlobsInBlockSchema() {
    return blobsInBlockSchema;
  }

  public BlobSidecarSchema getBlobSidecarSchema() {
    return blobSidecarSchema;
  }

  public BlockContentsSchema getBlockContentsSchema() {
    return blockContentsSchema;
  }

  public SignedBlockContentsSchema getSignedBlockContentsSchema() {
    return signedBlockContentsSchema;
  }

  public BlobsBundleSchema getBlobsBundleSchema() {
    return blobsBundleSchema;
  }

  public ExecutionPayloadAndBlobsBundleSchema getExecutionPayloadAndBlobsBundleSchema() {
    return executionPayloadAndBlobsBundleSchema;
  }

  public BlobSidecarsByRootRequestMessageSchema getBlobSidecarsByRootRequestMessageSchema() {
    return blobSidecarsByRootRequestMessageSchema;
  }

  @Override
  public Optional<SchemaDefinitionsDeneb> toVersionDeneb() {
    return Optional.of(this);
  }
}
