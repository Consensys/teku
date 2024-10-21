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
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.spec.config.SpecConfigEip7732;
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
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.eip7732.BeaconBlockBodyBuilderEip7732;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.eip7732.BeaconBlockBodySchemaEip7732Impl;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.eip7732.BlindedBeaconBlockBodySchemaEip7732Impl;
import tech.pegasys.teku.spec.datastructures.blocks.versions.deneb.BlockContentsSchema;
import tech.pegasys.teku.spec.datastructures.blocks.versions.deneb.SignedBlockContentsSchema;
import tech.pegasys.teku.spec.datastructures.builder.BlobsBundleSchema;
import tech.pegasys.teku.spec.datastructures.builder.BuilderBidSchema;
import tech.pegasys.teku.spec.datastructures.builder.BuilderPayloadSchema;
import tech.pegasys.teku.spec.datastructures.builder.ExecutionPayloadAndBlobsBundleSchema;
import tech.pegasys.teku.spec.datastructures.builder.SignedBuilderBidSchema;
import tech.pegasys.teku.spec.datastructures.builder.versions.electra.BuilderBidSchemaElectra;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadEnvelopeSchema;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeaderSchema;
import tech.pegasys.teku.spec.datastructures.execution.SignedExecutionPayloadEnvelopeSchema;
import tech.pegasys.teku.spec.datastructures.execution.SignedExecutionPayloadHeaderSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip7732.ExecutionPayloadHeaderSchemaEip7732;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.ExecutionPayloadEnvelopesByRootRequestMessage.ExecutionPayloadEnvelopesByRootRequestMessageSchema;
import tech.pegasys.teku.spec.datastructures.operations.AggregateAndProof.AggregateAndProofSchema;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationSchema;
import tech.pegasys.teku.spec.datastructures.operations.IndexedPayloadAttestationSchema;
import tech.pegasys.teku.spec.datastructures.operations.PayloadAttestation;
import tech.pegasys.teku.spec.datastructures.operations.PayloadAttestationMessageSchema;
import tech.pegasys.teku.spec.datastructures.operations.PayloadAttestationSchema;
import tech.pegasys.teku.spec.datastructures.operations.SignedAggregateAndProof.SignedAggregateAndProofSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.eip7732.BeaconStateEip7732;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.eip7732.BeaconStateSchemaEip7732;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.eip7732.MutableBeaconStateEip7732;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;
import tech.pegasys.teku.spec.schemas.registry.SchemaTypes;

public class SchemaDefinitionsEip7732 extends SchemaDefinitionsElectra {
  private final AttestationSchema<Attestation> attestationSchema;
  private final SignedAggregateAndProofSchema signedAggregateAndProofSchema;
  private final AggregateAndProofSchema aggregateAndProofSchema;

  private final BeaconStateSchemaEip7732 beaconStateSchema;

  private final ExecutionPayloadHeaderSchemaEip7732 executionPayloadHeaderSchemaEip7732;

  private final BeaconBlockBodySchemaEip7732Impl beaconBlockBodySchema;
  private final BlindedBeaconBlockBodySchemaEip7732Impl blindedBeaconBlockBodySchema;

  private final BeaconBlockSchema beaconBlockSchema;
  private final BeaconBlockSchema blindedBeaconBlockSchema;
  private final SignedBeaconBlockSchema signedBeaconBlockSchema;
  private final SignedBeaconBlockSchema signedBlindedBeaconBlockSchema;

  private final BuilderBidSchema<?> builderBidSchemaElectra;
  private final SignedBuilderBidSchema signedBuilderBidSchemaElectra;

  private final BlobSidecarSchema blobSidecarSchema;
  private final BlockContentsSchema blockContentsSchema;
  private final SignedBlockContentsSchema signedBlockContentsSchema;
  private final BlobsBundleSchema blobsBundleSchema;
  private final ExecutionPayloadAndBlobsBundleSchema executionPayloadAndBlobsBundleSchema;

  private final PayloadAttestationSchema payloadAttestationSchema;
  private final IndexedPayloadAttestationSchema indexedPayloadAttestationSchema;
  private final SignedExecutionPayloadHeaderSchema signedExecutionPayloadHeaderSchema;
  private final ExecutionPayloadEnvelopeSchema executionPayloadEnvelopeSchema;
  private final SignedExecutionPayloadEnvelopeSchema signedExecutionPayloadEnvelopeSchema;
  private final ExecutionPayloadEnvelopesByRootRequestMessageSchema
      executionPayloadEnvelopesByRootRequestMessageSchema;
  private final PayloadAttestationMessageSchema payloadAttestationMessageSchema;

  public SchemaDefinitionsEip7732(final SchemaRegistry schemaRegistry) {
    super(schemaRegistry);
    final SpecConfigEip7732 specConfig = SpecConfigEip7732.required(schemaRegistry.getSpecConfig());

    final long maxValidatorsPerAttestation = getMaxValidatorPerAttestation(specConfig);

    this.attestationSchema = schemaRegistry.get(SchemaTypes.ATTESTATION_SCHEMA);
    this.aggregateAndProofSchema = new AggregateAndProofSchema(attestationSchema);
    this.signedAggregateAndProofSchema = new SignedAggregateAndProofSchema(aggregateAndProofSchema);

    this.beaconStateSchema = BeaconStateSchemaEip7732.create(specConfig);
    this.executionPayloadHeaderSchemaEip7732 =
        beaconStateSchema.getLastExecutionPayloadHeaderSchema();
    this.payloadAttestationSchema = new PayloadAttestationSchema(specConfig.getPtcSize());
    this.payloadAttestationMessageSchema = new PayloadAttestationMessageSchema();
    this.beaconBlockBodySchema =
        BeaconBlockBodySchemaEip7732Impl.create(
            specConfig,
            getSignedBlsToExecutionChangeSchema(),
            maxValidatorsPerAttestation,
            executionPayloadHeaderSchemaEip7732,
            payloadAttestationSchema,
            "BeaconBlockBodyEip7732",
            schemaRegistry);
    this.blindedBeaconBlockBodySchema =
        BlindedBeaconBlockBodySchemaEip7732Impl.create(
            specConfig,
            getSignedBlsToExecutionChangeSchema(),
            maxValidatorsPerAttestation,
            executionPayloadHeaderSchemaEip7732,
            payloadAttestationSchema,
            "BlindedBlockBodyEip7732",
            schemaRegistry);
    this.beaconBlockSchema = new BeaconBlockSchema(beaconBlockBodySchema, "BeaconBlockEip7732");
    this.blindedBeaconBlockSchema =
        new BeaconBlockSchema(blindedBeaconBlockBodySchema, "BlindedBlockEip7732");
    this.signedBeaconBlockSchema =
        new SignedBeaconBlockSchema(beaconBlockSchema, "SignedBeaconBlockEip7732");
    this.signedBlindedBeaconBlockSchema =
        new SignedBeaconBlockSchema(blindedBeaconBlockSchema, "SignedBlindedBlockEip7732");
    this.builderBidSchemaElectra =
        new BuilderBidSchemaElectra(
            "BuilderBidElectra",
            executionPayloadHeaderSchemaEip7732,
            getBlobKzgCommitmentsSchema(),
            getExecutionRequestsSchema());
    this.signedBuilderBidSchemaElectra =
        new SignedBuilderBidSchema("SignedBuilderBidEip7732", builderBidSchemaElectra);

    this.blobSidecarSchema =
        BlobSidecarSchema.create(
            SignedBeaconBlockHeader.SSZ_SCHEMA,
            getBlobSchema(),
            specConfig.getKzgCommitmentInclusionProofDepthEip7732());
    this.blockContentsSchema =
        BlockContentsSchema.create(
            specConfig, beaconBlockSchema, getBlobSchema(), "BlockContentsEip7732");
    this.signedBlockContentsSchema =
        SignedBlockContentsSchema.create(
            specConfig, signedBeaconBlockSchema, getBlobSchema(), "SignedBlockContentsEip7732");
    this.blobsBundleSchema =
        new BlobsBundleSchema(
            "BlobsBundleEip7732", getBlobSchema(), getBlobKzgCommitmentsSchema(), specConfig);
    this.executionPayloadAndBlobsBundleSchema =
        new ExecutionPayloadAndBlobsBundleSchema(getExecutionPayloadSchema(), blobsBundleSchema);

    this.indexedPayloadAttestationSchema =
        new IndexedPayloadAttestationSchema(specConfig.getPtcSize());
    this.signedExecutionPayloadHeaderSchema =
        new SignedExecutionPayloadHeaderSchema(executionPayloadHeaderSchemaEip7732);
    this.executionPayloadEnvelopeSchema =
        new ExecutionPayloadEnvelopeSchema(
            getExecutionPayloadSchema(),
            getExecutionRequestsSchema(),
            getBlobKzgCommitmentsSchema());
    this.signedExecutionPayloadEnvelopeSchema =
        new SignedExecutionPayloadEnvelopeSchema(executionPayloadEnvelopeSchema);
    this.executionPayloadEnvelopesByRootRequestMessageSchema =
        new ExecutionPayloadEnvelopesByRootRequestMessageSchema(specConfig);
  }

  public static SchemaDefinitionsEip7732 required(final SchemaDefinitions schemaDefinitions) {
    checkArgument(
        schemaDefinitions instanceof SchemaDefinitionsEip7732,
        "Expected definitions of type %s but got %s",
        SchemaDefinitionsEip7732.class,
        schemaDefinitions.getClass());
    return (SchemaDefinitionsEip7732) schemaDefinitions;
  }

  @Override
  public SignedAggregateAndProofSchema getSignedAggregateAndProofSchema() {
    return signedAggregateAndProofSchema;
  }

  @Override
  public BlobSidecarSchema getBlobSidecarSchema() {
    return blobSidecarSchema;
  }

  @Override
  public AggregateAndProofSchema getAggregateAndProofSchema() {
    return aggregateAndProofSchema;
  }

  @Override
  public AttestationSchema<Attestation> getAttestationSchema() {
    return attestationSchema;
  }

  @Override
  public BeaconStateSchema<? extends BeaconStateEip7732, ? extends MutableBeaconStateEip7732>
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
  public ExecutionPayloadHeaderSchema<?> getExecutionPayloadHeaderSchema() {
    return executionPayloadHeaderSchemaEip7732;
  }

  @Override
  public BuilderBidSchema<?> getBuilderBidSchema() {
    return builderBidSchemaElectra;
  }

  @Override
  public SignedBuilderBidSchema getSignedBuilderBidSchema() {
    return signedBuilderBidSchemaElectra;
  }

  @Override
  public BuilderPayloadSchema<?> getBuilderPayloadSchema() {
    return getExecutionPayloadAndBlobsBundleSchema();
  }

  @Override
  public BeaconBlockBodyBuilder createBeaconBlockBodyBuilder() {
    return new BeaconBlockBodyBuilderEip7732(beaconBlockBodySchema, blindedBeaconBlockBodySchema);
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

  public PayloadAttestationSchema getPayloadAttestationSchema() {
    return payloadAttestationSchema;
  }

  public IndexedPayloadAttestationSchema getIndexedPayloadAttestationSchema() {
    return indexedPayloadAttestationSchema;
  }

  public SignedExecutionPayloadHeaderSchema getSignedExecutionPayloadHeaderSchema() {
    return signedExecutionPayloadHeaderSchema;
  }

  public ExecutionPayloadEnvelopeSchema getExecutionPayloadEnvelopeSchema() {
    return executionPayloadEnvelopeSchema;
  }

  public SignedExecutionPayloadEnvelopeSchema getSignedExecutionPayloadEnvelopeSchema() {
    return signedExecutionPayloadEnvelopeSchema;
  }

  public ExecutionPayloadEnvelopesByRootRequestMessageSchema
      getExecutionPayloadEnvelopesByRootRequestMessageSchema() {
    return executionPayloadEnvelopesByRootRequestMessageSchema;
  }

  public PayloadAttestationMessageSchema getPayloadAttestationMessageSchema() {
    return payloadAttestationMessageSchema;
  }

  public SszListSchema<PayloadAttestation, ?> getPayloadAttestationsSchema() {
    return beaconBlockBodySchema.getPayloadAttestationsSchema();
  }

  @Override
  public Optional<SchemaDefinitionsEip7732> toVersionEip7732() {
    return Optional.of(this);
  }
}
