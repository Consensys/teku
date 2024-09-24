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
import static tech.pegasys.teku.spec.schemas.SchemaTypes.BLOB_KZG_COMMITMENTS_SCHEMA;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobKzgCommitmentsSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecarSchema;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainerSchema;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainerSchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodyBuilder;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.BeaconBlockBodyBuilderDeneb;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.BlindedBeaconBlockBodyDeneb;
import tech.pegasys.teku.spec.datastructures.blocks.versions.deneb.BlockContentsSchema;
import tech.pegasys.teku.spec.datastructures.blocks.versions.deneb.SignedBlockContentsSchema;
import tech.pegasys.teku.spec.datastructures.builder.BlobsBundleSchema;
import tech.pegasys.teku.spec.datastructures.builder.BuilderBidSchema;
import tech.pegasys.teku.spec.datastructures.builder.BuilderPayloadSchema;
import tech.pegasys.teku.spec.datastructures.builder.ExecutionPayloadAndBlobsBundleSchema;
import tech.pegasys.teku.spec.datastructures.builder.SignedBuilderBidSchema;
import tech.pegasys.teku.spec.datastructures.builder.versions.deneb.BuilderBidSchemaDeneb;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobSidecarsByRootRequestMessageSchema;

public class SchemaDefinitionsDeneb extends SchemaDefinitionsCapella {

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

  public SchemaDefinitionsDeneb(final SchemaRegistry schemaRegistry) {
    super(schemaRegistry);
    final SpecConfigDeneb specConfig = SpecConfigDeneb.required(schemaRegistry.getSpecConfig());

    this.builderBidSchemaDeneb =
        new BuilderBidSchemaDeneb(
            "BuilderBidDeneb", getExecutionPayloadHeaderSchema(), getBlobKzgCommitmentsSchema());
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
        BlockContentsSchema.create(
            specConfig, getBeaconBlockSchema(), blobSchema, "BlockContentsDeneb");
    this.signedBlockContentsSchema =
        SignedBlockContentsSchema.create(
            specConfig, getSignedBeaconBlockSchema(), blobSchema, "SignedBlockContentsDeneb");
    this.blobsBundleSchema =
        new BlobsBundleSchema(
            "BlobsBundleDeneb", blobSchema, getBlobKzgCommitmentsSchema(), specConfig);
    this.executionPayloadAndBlobsBundleSchema =
        new ExecutionPayloadAndBlobsBundleSchema(getExecutionPayloadSchema(), blobsBundleSchema);
    this.blobSidecarsByRootRequestMessageSchema =
        new BlobSidecarsByRootRequestMessageSchema(specConfig);
  }

  public static SchemaDefinitionsDeneb required(final SchemaDefinitions schemaDefinitions) {
    checkArgument(
        schemaDefinitions instanceof SchemaDefinitionsDeneb,
        "Expected definitions of type %s but got %s",
        SchemaDefinitionsDeneb.class,
        schemaDefinitions.getClass());
    return (SchemaDefinitionsDeneb) schemaDefinitions;
  }

  @Override
  @SuppressWarnings("unchecked")
  public BeaconBlockBodySchema<? extends BlindedBeaconBlockBodyDeneb>
      getBlindedBeaconBlockBodySchema() {
    return (BeaconBlockBodySchema<? extends BlindedBeaconBlockBodyDeneb>)
        schemaRegistry.get(BLINDED_BEACON_BLOCK_BODY_SCHEMA);
  }

  @Override
  public BlockContainerSchema<BlockContainer> getBlockContainerSchema() {
    return getBlockContentsSchema().castTypeToBlockContainer();
  }

  @Override
  public SignedBlockContainerSchema<SignedBlockContainer> getSignedBlockContainerSchema() {
    return getSignedBlockContentsSchema().castTypeToSignedBlockContainer();
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
    return new BeaconBlockBodyBuilderDeneb(
        getBeaconBlockBodySchema().toVersionDeneb().orElseThrow(),
        getBlindedBeaconBlockBodySchema());
  }

  public BlobSchema getBlobSchema() {
    return blobSchema;
  }

  public BlobKzgCommitmentsSchema getBlobKzgCommitmentsSchema() {
    return schemaRegistry.get(BLOB_KZG_COMMITMENTS_SCHEMA);
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
