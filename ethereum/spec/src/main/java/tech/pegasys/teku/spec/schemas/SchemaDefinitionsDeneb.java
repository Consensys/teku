/*
 * Copyright ConsenSys Software Inc., 2022
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
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlindedBlobSidecarSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlindedBlobSidecarsSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecarSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecarsSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobsSidecarSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.SignedBlindedBlobSidecarSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.SignedBlindedBlobSidecarsSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.SignedBlobSidecarSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.SignedBlobSidecarsSchema;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSchema;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockSchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.BeaconBlockBodySchemaDeneb;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.BeaconBlockBodySchemaDenebImpl;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.BlindedBeaconBlockBodySchemaDeneb;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.BlindedBeaconBlockBodySchemaDenebImpl;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.SignedBeaconBlockAndBlobsSidecarSchema;
import tech.pegasys.teku.spec.datastructures.blocks.versions.deneb.BlindedBlockContentsSchema;
import tech.pegasys.teku.spec.datastructures.blocks.versions.deneb.BlockContentsSchema;
import tech.pegasys.teku.spec.datastructures.blocks.versions.deneb.SignedBlindedBlockContentsSchema;
import tech.pegasys.teku.spec.datastructures.blocks.versions.deneb.SignedBlockContentsSchema;
import tech.pegasys.teku.spec.datastructures.builder.BuilderBidSchema;
import tech.pegasys.teku.spec.datastructures.builder.SignedBuilderBidSchema;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeaderSchema;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.ExecutionPayloadHeaderSchemaDeneb;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.ExecutionPayloadSchemaDeneb;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChangeSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.deneb.BeaconStateDeneb;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.deneb.BeaconStateSchemaDeneb;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.deneb.MutableBeaconStateDeneb;

public class SchemaDefinitionsDeneb extends SchemaDefinitionsCapella {

  private final BeaconStateSchemaDeneb beaconStateSchema;

  private final ExecutionPayloadSchemaDeneb executionPayloadSchemaDeneb;
  private final ExecutionPayloadHeaderSchemaDeneb executionPayloadHeaderSchemaDeneb;

  private final BeaconBlockBodySchemaDeneb<?> beaconBlockBodySchema;
  private final BlindedBeaconBlockBodySchemaDeneb<?> blindedBeaconBlockBodySchema;

  private final BeaconBlockSchema beaconBlockSchema;
  private final BeaconBlockSchema blindedBeaconBlockSchema;
  private final SignedBeaconBlockSchema signedBeaconBlockSchema;
  private final SignedBeaconBlockSchema signedBlindedBeaconBlockSchema;

  private final BuilderBidSchema builderBidSchemaDeneb;
  private final SignedBuilderBidSchema signedBuilderBidSchemaDeneb;

  private final BlobSchema blobSchema;
  private final BlobSidecarSchema blobSidecarSchema;
  private final BlobsSidecarSchema blobsSidecarSchema;
  private final SignedBeaconBlockAndBlobsSidecarSchema signedBeaconBlockAndBlobsSidecarSchema;
  private final SignedBlobSidecarSchema signedBlobSidecarSchema;
  private final BlobSidecarsSchema blobSidecarsSchema;
  private final SignedBlobSidecarsSchema signedBlobSidecarsSchema;
  private final BlindedBlobSidecarSchema blindedBlobSidecarSchema;
  private final BlindedBlobSidecarsSchema blindedBlobSidecarsSchema;
  private final SignedBlindedBlobSidecarSchema signedBlindedBlobSidecarSchema;
  private final SignedBlindedBlobSidecarsSchema signedBlindedBlobSidecarsSchema;
  private final BlockContentsSchema blockContentsSchema;
  private final SignedBlockContentsSchema signedBlockContentsSchema;
  private final BlindedBlockContentsSchema blindedBlockContentsSchema;
  private final SignedBlindedBlockContentsSchema signedBlindedBlockContentsSchema;

  public SchemaDefinitionsDeneb(final SpecConfigDeneb specConfig) {
    super(specConfig.toVersionDeneb().orElseThrow());
    this.executionPayloadSchemaDeneb = new ExecutionPayloadSchemaDeneb(specConfig);
    final SignedBlsToExecutionChangeSchema signedBlsToExecutionChangeSchema =
        new SignedBlsToExecutionChangeSchema();

    this.beaconStateSchema = BeaconStateSchemaDeneb.create(specConfig);
    this.executionPayloadHeaderSchemaDeneb =
        beaconStateSchema.getLastExecutionPayloadHeaderSchema();
    this.beaconBlockBodySchema =
        BeaconBlockBodySchemaDenebImpl.create(
            specConfig,
            getAttesterSlashingSchema(),
            signedBlsToExecutionChangeSchema,
            "BeaconBlockBodyDeneb");
    this.blindedBeaconBlockBodySchema =
        BlindedBeaconBlockBodySchemaDenebImpl.create(
            specConfig,
            getAttesterSlashingSchema(),
            signedBlsToExecutionChangeSchema,
            "BlindedBlockBodyDeneb");
    this.beaconBlockSchema = new BeaconBlockSchema(beaconBlockBodySchema, "BeaconBlockDeneb");
    this.blindedBeaconBlockSchema =
        new BeaconBlockSchema(blindedBeaconBlockBodySchema, "BlindedBlockDeneb");
    this.signedBeaconBlockSchema =
        new SignedBeaconBlockSchema(beaconBlockSchema, "SignedBeaconBlockDeneb");
    this.signedBlindedBeaconBlockSchema =
        new SignedBeaconBlockSchema(blindedBeaconBlockSchema, "SignedBlindedBlockDeneb");
    this.builderBidSchemaDeneb =
        new BuilderBidSchema("BuilderBidDeneb", executionPayloadHeaderSchemaDeneb);
    this.signedBuilderBidSchemaDeneb =
        new SignedBuilderBidSchema("SignedBuilderBidDeneb", builderBidSchemaDeneb);

    this.blobSchema = new BlobSchema(specConfig);
    this.blobsSidecarSchema = BlobsSidecarSchema.create(specConfig, blobSchema);
    this.blobSidecarSchema = BlobSidecarSchema.create(blobSchema);
    this.signedBeaconBlockAndBlobsSidecarSchema =
        SignedBeaconBlockAndBlobsSidecarSchema.create(signedBeaconBlockSchema, blobsSidecarSchema);
    this.signedBlobSidecarSchema = SignedBlobSidecarSchema.create(blobSidecarSchema);
    this.signedBlobSidecarsSchema =
        SignedBlobSidecarsSchema.create(specConfig, signedBlobSidecarSchema);
    this.blobSidecarsSchema = BlobSidecarsSchema.create(specConfig, blobSidecarSchema);
    this.blindedBlobSidecarSchema = BlindedBlobSidecarSchema.create();
    this.blindedBlobSidecarsSchema =
        BlindedBlobSidecarsSchema.create(specConfig, blindedBlobSidecarSchema);
    this.signedBlindedBlobSidecarSchema =
        SignedBlindedBlobSidecarSchema.create(blindedBlobSidecarSchema);
    this.signedBlindedBlobSidecarsSchema =
        SignedBlindedBlobSidecarsSchema.create(specConfig, signedBlindedBlobSidecarSchema);
    this.blockContentsSchema = BlockContentsSchema.create(beaconBlockSchema, blobSidecarsSchema);
    this.signedBlockContentsSchema =
        SignedBlockContentsSchema.create(signedBeaconBlockSchema, signedBlobSidecarsSchema);
    this.blindedBlockContentsSchema =
        BlindedBlockContentsSchema.create(beaconBlockSchema, blindedBlobSidecarsSchema);
    this.signedBlindedBlockContentsSchema =
        SignedBlindedBlockContentsSchema.create(
            signedBeaconBlockSchema, signedBlindedBlobSidecarsSchema);
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
  public ExecutionPayloadSchema<?> getExecutionPayloadSchema() {
    return executionPayloadSchemaDeneb;
  }

  @Override
  public ExecutionPayloadHeaderSchema<?> getExecutionPayloadHeaderSchema() {
    return executionPayloadHeaderSchemaDeneb;
  }

  @Override
  public BuilderBidSchema getBuilderBidSchema() {
    return builderBidSchemaDeneb;
  }

  @Override
  public SignedBuilderBidSchema getSignedBuilderBidSchema() {
    return signedBuilderBidSchemaDeneb;
  }

  public BlobSchema getBlobSchema() {
    return blobSchema;
  }

  public BlobsSidecarSchema getBlobsSidecarSchema() {
    return blobsSidecarSchema;
  }

  public BlobSidecarSchema getBlobSidecarSchema() {
    return blobSidecarSchema;
  }

  public SignedBeaconBlockAndBlobsSidecarSchema getSignedBeaconBlockAndBlobsSidecarSchema() {
    return signedBeaconBlockAndBlobsSidecarSchema;
  }

  public SignedBlobSidecarSchema getSignedBlobSidecarSchema() {
    return signedBlobSidecarSchema;
  }

  public BlobSidecarsSchema getBlobSidecarsSchema() {
    return blobSidecarsSchema;
  }

  public SignedBlobSidecarsSchema getSignedBlobSidecarsSchema() {
    return signedBlobSidecarsSchema;
  }

  public BlindedBlobSidecarSchema getBlindedBlobSidecarSchema() {
    return blindedBlobSidecarSchema;
  }

  public BlindedBlobSidecarsSchema getBlindedBlobSidecarsSchema() {
    return blindedBlobSidecarsSchema;
  }

  public SignedBlindedBlobSidecarSchema getSignedBlindedBlobSidecarSchema() {
    return signedBlindedBlobSidecarSchema;
  }

  public SignedBlindedBlobSidecarsSchema getSignedBlindedBlobSidecarsSchema() {
    return signedBlindedBlobSidecarsSchema;
  }

  public BlockContentsSchema getBlockContentsSchema() {
    return blockContentsSchema;
  }

  public BlindedBlockContentsSchema getBlindedBlockContentsSchema() {
    return blindedBlockContentsSchema;
  }

  public SignedBlockContentsSchema getSignedBlockContentsSchema() {
    return signedBlockContentsSchema;
  }

  public SignedBlindedBlockContentsSchema getSignedBlindedBlockContentsSchema() {
    return signedBlindedBlockContentsSchema;
  }

  @Override
  public Optional<SchemaDefinitionsDeneb> toVersionDeneb() {
    return Optional.of(this);
  }
}
