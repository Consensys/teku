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
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSchema;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockSchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.BeaconBlockBodySchemaDeneb;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.BeaconBlockBodySchemaDenebImpl;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.BlindedBeaconBlockBodySchemaDeneb;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.BlindedBeaconBlockBodySchemaDenebImpl;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.SignedBeaconBlockAndBlobsSidecarSchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.SignedBlobSidecarSchema;
import tech.pegasys.teku.spec.datastructures.builder.BuilderBidSchema;
import tech.pegasys.teku.spec.datastructures.builder.SignedBuilderBidSchema;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeaderSchema;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.BlobSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.BlobSidecarSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.BlobsSidecarSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.ExecutionPayloadHeaderSchemaDeneb;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.ExecutionPayloadSchemaDeneb;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobIdentifier;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobIdentifier.BlobIdentifierSchema;
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
  private final BlobIdentifierSchema blobIdentifierSchema;
  private final BlobSidecarSchema blobSidecarSchema;
  private final BlobsSidecarSchema blobsSidecarSchema;
  private final SignedBeaconBlockAndBlobsSidecarSchema signedBeaconBlockAndBlobsSidecarSchema;
  private final SignedBlobSidecarSchema signedBlobSidecarSchema;

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
    this.blobIdentifierSchema = BlobIdentifier.SSZ_SCHEMA;
    this.blobsSidecarSchema = BlobsSidecarSchema.create(specConfig, blobSchema);
    this.blobSidecarSchema = BlobSidecarSchema.create(blobSchema);
    this.signedBeaconBlockAndBlobsSidecarSchema =
        SignedBeaconBlockAndBlobsSidecarSchema.create(signedBeaconBlockSchema, blobsSidecarSchema);
    this.signedBlobSidecarSchema = SignedBlobSidecarSchema.create(blobSidecarSchema);
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

  public BlobIdentifierSchema getBlobIdentifierSchema() {
    return blobIdentifierSchema;
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

  @Override
  public Optional<SchemaDefinitionsDeneb> toVersionDeneb() {
    return Optional.of(this);
  }
}
