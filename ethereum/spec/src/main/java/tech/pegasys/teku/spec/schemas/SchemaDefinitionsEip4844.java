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
import tech.pegasys.teku.spec.config.SpecConfigEip4844;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSchema;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockSchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.eip4844.BeaconBlockBodySchemaEip4844;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.eip4844.BeaconBlockBodySchemaEip4844Impl;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.eip4844.BlindedBeaconBlockBodySchemaEip4844;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.eip4844.BlindedBeaconBlockBodySchemaEip4844Impl;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.eip4844.SignedBeaconBlockAndBlobsSidecarSchema;
import tech.pegasys.teku.spec.datastructures.builder.BuilderBidSchema;
import tech.pegasys.teku.spec.datastructures.builder.SignedBuilderBidSchema;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeaderSchema;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip4844.BlobSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip4844.BlobsSidecarSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip4844.ExecutionPayloadHeaderSchemaEip4844;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip4844.ExecutionPayloadSchemaEip4844;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChangeSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.eip4844.BeaconStateEip4844;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.eip4844.BeaconStateSchemaEip4844;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.eip4844.MutableBeaconStateEip4844;

public class SchemaDefinitionsEip4844 extends SchemaDefinitionsCapella {

  private final BeaconStateSchemaEip4844 beaconStateSchema;

  private final ExecutionPayloadSchemaEip4844 executionPayloadSchemaEip4844;
  private final ExecutionPayloadHeaderSchemaEip4844 executionPayloadHeaderSchemaEip4844;

  private final BeaconBlockBodySchemaEip4844<?> beaconBlockBodySchema;
  private final BlindedBeaconBlockBodySchemaEip4844<?> blindedBeaconBlockBodySchema;

  private final BeaconBlockSchema beaconBlockSchema;
  private final BeaconBlockSchema blindedBeaconBlockSchema;
  private final SignedBeaconBlockSchema signedBeaconBlockSchema;
  private final SignedBeaconBlockSchema signedBlindedBeaconBlockSchema;

  private final BuilderBidSchema builderBidSchemaEip4844;
  private final SignedBuilderBidSchema signedBuilderBidSchemaEip4844;

  private final BlobSchema blobSchema;
  private final BlobsSidecarSchema blobsSidecarSchema;
  private final SignedBeaconBlockAndBlobsSidecarSchema signedBeaconBlockAndBlobsSidecarSchema;

  public SchemaDefinitionsEip4844(final SpecConfigEip4844 specConfig) {
    super(specConfig.toVersionEip4844().orElseThrow());
    this.executionPayloadSchemaEip4844 = new ExecutionPayloadSchemaEip4844(specConfig);
    final SignedBlsToExecutionChangeSchema signedBlsToExecutionChangeSchema =
        new SignedBlsToExecutionChangeSchema();

    this.beaconStateSchema = BeaconStateSchemaEip4844.create(specConfig);
    this.executionPayloadHeaderSchemaEip4844 =
        beaconStateSchema.getLastExecutionPayloadHeaderSchema();
    this.beaconBlockBodySchema =
        BeaconBlockBodySchemaEip4844Impl.create(
            specConfig,
            getAttesterSlashingSchema(),
            signedBlsToExecutionChangeSchema,
            "BeaconBlockBodyEip4844");
    this.blindedBeaconBlockBodySchema =
        BlindedBeaconBlockBodySchemaEip4844Impl.create(
            specConfig,
            getAttesterSlashingSchema(),
            signedBlsToExecutionChangeSchema,
            "BlindedBlockBodyEip4844");
    this.beaconBlockSchema = new BeaconBlockSchema(beaconBlockBodySchema, "BeaconBlockEip4844");
    this.blindedBeaconBlockSchema =
        new BeaconBlockSchema(blindedBeaconBlockBodySchema, "BlindedBlockEip4844");
    this.signedBeaconBlockSchema =
        new SignedBeaconBlockSchema(beaconBlockSchema, "SignedBeaconBlockEip4844");
    this.signedBlindedBeaconBlockSchema =
        new SignedBeaconBlockSchema(blindedBeaconBlockSchema, "SignedBlindedBlockEip4844");
    this.builderBidSchemaEip4844 =
        new BuilderBidSchema("BuilderBidEip4844", executionPayloadHeaderSchemaEip4844);
    this.signedBuilderBidSchemaEip4844 =
        new SignedBuilderBidSchema("SignedBuilderBidEip4844", builderBidSchemaEip4844);

    this.blobSchema = new BlobSchema(specConfig);
    this.blobsSidecarSchema = BlobsSidecarSchema.create(specConfig, blobSchema);
    this.signedBeaconBlockAndBlobsSidecarSchema =
        SignedBeaconBlockAndBlobsSidecarSchema.create(signedBeaconBlockSchema, blobsSidecarSchema);
  }

  public static SchemaDefinitionsEip4844 required(final SchemaDefinitions schemaDefinitions) {
    checkArgument(
        schemaDefinitions instanceof SchemaDefinitionsEip4844,
        "Expected definitions of type %s by got %s",
        SchemaDefinitionsEip4844.class,
        schemaDefinitions.getClass());
    return (SchemaDefinitionsEip4844) schemaDefinitions;
  }

  @Override
  public BeaconStateSchema<? extends BeaconStateEip4844, ? extends MutableBeaconStateEip4844>
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
    return executionPayloadSchemaEip4844;
  }

  @Override
  public ExecutionPayloadHeaderSchema<?> getExecutionPayloadHeaderSchema() {
    return executionPayloadHeaderSchemaEip4844;
  }

  @Override
  public BuilderBidSchema getBuilderBidSchema() {
    return builderBidSchemaEip4844;
  }

  @Override
  public SignedBuilderBidSchema getSignedBuilderBidSchema() {
    return signedBuilderBidSchemaEip4844;
  }

  public BlobSchema getBlobSchema() {
    return blobSchema;
  }

  public BlobsSidecarSchema getBlobsSidecarSchema() {
    return blobsSidecarSchema;
  }

  public SignedBeaconBlockAndBlobsSidecarSchema getSignedBeaconBlockAndBlobsSidecarSchema() {
    return signedBeaconBlockAndBlobsSidecarSchema;
  }

  @Override
  public Optional<SchemaDefinitionsEip4844> toVersionEip4844() {
    return Optional.of(this);
  }
}
