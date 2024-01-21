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
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSchema;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainerSchema;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockSchema;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainerSchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodyBuilder;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.electra.BeaconBlockBodyBuilderElectra;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.electra.BeaconBlockBodySchemaElectraImpl;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.electra.BlindedBeaconBlockBodySchemaElectraImpl;
import tech.pegasys.teku.spec.datastructures.builder.BuilderBidSchema;
import tech.pegasys.teku.spec.datastructures.builder.SignedBuilderBidSchema;
import tech.pegasys.teku.spec.datastructures.builder.versions.bellatrix.BuilderBidSchemaBellatrix;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeaderSchema;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSchema;
import tech.pegasys.teku.spec.datastructures.execution.verkle.ExecutionWitnessSchema;
import tech.pegasys.teku.spec.datastructures.execution.verkle.IpaProofSchema;
import tech.pegasys.teku.spec.datastructures.execution.verkle.StemStateDiffSchema;
import tech.pegasys.teku.spec.datastructures.execution.verkle.SuffixStateDiffSchema;
import tech.pegasys.teku.spec.datastructures.execution.verkle.VerkleProofSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionPayloadHeaderSchemaElectra;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionPayloadSchemaElectra;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChangeSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateElectra;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateSchemaElectra;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.MutableBeaconStateElectra;

public class SchemaDefinitionsElectra extends SchemaDefinitionsBellatrix {

  private final BeaconStateSchemaElectra beaconStateSchema;

  private final ExecutionPayloadSchemaElectra executionPayloadSchemaElectra;
  private final ExecutionPayloadHeaderSchemaElectra executionPayloadHeaderSchemaElectra;

  private final BeaconBlockBodySchemaElectraImpl beaconBlockBodySchema;
  private final BlindedBeaconBlockBodySchemaElectraImpl blindedBeaconBlockBodySchema;

  private final BeaconBlockSchema beaconBlockSchema;
  private final BeaconBlockSchema blindedBeaconBlockSchema;
  private final SignedBeaconBlockSchema signedBeaconBlockSchema;
  private final SignedBeaconBlockSchema signedBlindedBeaconBlockSchema;
  private final SignedBlsToExecutionChangeSchema signedBlsToExecutionChangeSchema;
  private final BuilderBidSchema<?> builderBidSchemaElectra;
  private final SignedBuilderBidSchema signedBuilderBidSchemaElectra;

  // Experimental implementation of Verkle Trees as of
  // https://github.com/ethereum/consensus-specs/pull/3230
  private final SuffixStateDiffSchema suffixStateDiffSchema;
  private final StemStateDiffSchema stemStateDiffSchema;
  private final IpaProofSchema ipaProofSchema;
  private final VerkleProofSchema verkleProofSchema;
  private final ExecutionWitnessSchema executionWitnessSchema;

  public SchemaDefinitionsElectra(final SpecConfigElectra specConfig) {
    super(specConfig);

    this.suffixStateDiffSchema = SuffixStateDiffSchema.INSTANCE;
    this.stemStateDiffSchema = new StemStateDiffSchema(specConfig.getVerkleWidth());
    this.ipaProofSchema = new IpaProofSchema(specConfig.getIpaProofDepth());
    this.verkleProofSchema =
        new VerkleProofSchema(
            ipaProofSchema, specConfig.getMaxStems(), specConfig.getMaxCommitmentsPerStem());
    this.executionWitnessSchema =
        new ExecutionWitnessSchema(
            specConfig.getMaxStems(), stemStateDiffSchema, verkleProofSchema);

    this.executionPayloadSchemaElectra =
        new ExecutionPayloadSchemaElectra(specConfig, executionWitnessSchema);
    this.signedBlsToExecutionChangeSchema = new SignedBlsToExecutionChangeSchema();

    this.beaconStateSchema = BeaconStateSchemaElectra.create(specConfig, executionWitnessSchema);
    this.executionPayloadHeaderSchemaElectra =
        beaconStateSchema.getLastExecutionPayloadHeaderSchema();
    this.beaconBlockBodySchema =
        BeaconBlockBodySchemaElectraImpl.create(
            specConfig,
            getAttesterSlashingSchema(),
            signedBlsToExecutionChangeSchema,
            executionWitnessSchema,
            "BeaconBlockBodyElectra");
    this.blindedBeaconBlockBodySchema =
        BlindedBeaconBlockBodySchemaElectraImpl.create(
            specConfig,
            getAttesterSlashingSchema(),
            signedBlsToExecutionChangeSchema,
            executionWitnessSchema,
            "BlindedBlockBodyElectra");
    this.beaconBlockSchema = new BeaconBlockSchema(beaconBlockBodySchema, "BeaconBlockElectra");
    this.blindedBeaconBlockSchema =
        new BeaconBlockSchema(blindedBeaconBlockBodySchema, "BlindedBlockElectra");
    this.signedBeaconBlockSchema =
        new SignedBeaconBlockSchema(beaconBlockSchema, "SignedBeaconBlockElectra");
    this.signedBlindedBeaconBlockSchema =
        new SignedBeaconBlockSchema(blindedBeaconBlockSchema, "SignedBlindedBlockElectra");
    this.builderBidSchemaElectra =
        new BuilderBidSchemaBellatrix("BuilderBidElectra", executionPayloadHeaderSchemaElectra);
    this.signedBuilderBidSchemaElectra =
        new SignedBuilderBidSchema("SignedBuilderBidElectra", builderBidSchemaElectra);
  }

  public static SchemaDefinitionsElectra required(final SchemaDefinitions schemaDefinitions) {
    checkArgument(
        schemaDefinitions instanceof SchemaDefinitionsElectra,
        "Expected definitions of type %s by got %s",
        SchemaDefinitionsElectra.class,
        schemaDefinitions.getClass());
    return (SchemaDefinitionsElectra) schemaDefinitions;
  }

  @Override
  public BeaconStateSchema<? extends BeaconStateElectra, ? extends MutableBeaconStateElectra>
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
    return getBeaconBlockSchema().castTypeToBlockContainer();
  }

  @Override
  public BlockContainerSchema<BlockContainer> getBlindedBlockContainerSchema() {
    return getBlindedBeaconBlockSchema().castTypeToBlockContainer();
  }

  @Override
  public SignedBlockContainerSchema<SignedBlockContainer> getSignedBlockContainerSchema() {
    return getSignedBeaconBlockSchema().castTypeToSignedBlockContainer();
  }

  @Override
  public SignedBlockContainerSchema<SignedBlockContainer> getSignedBlindedBlockContainerSchema() {
    return getSignedBlindedBeaconBlockSchema().castTypeToSignedBlockContainer();
  }

  @Override
  public ExecutionPayloadSchema<?> getExecutionPayloadSchema() {
    return executionPayloadSchemaElectra;
  }

  @Override
  public ExecutionPayloadHeaderSchema<?> getExecutionPayloadHeaderSchema() {
    return executionPayloadHeaderSchemaElectra;
  }

  @Override
  public BeaconBlockBodyBuilder createBeaconBlockBodyBuilder() {
    return new BeaconBlockBodyBuilderElectra(beaconBlockBodySchema, blindedBeaconBlockBodySchema);
  }

  @Override
  public BuilderBidSchema<?> getBuilderBidSchema() {
    return builderBidSchemaElectra;
  }

  @Override
  public SignedBuilderBidSchema getSignedBuilderBidSchema() {
    return signedBuilderBidSchemaElectra;
  }

  public SuffixStateDiffSchema getSuffixStateDiffSchema() {
    return suffixStateDiffSchema;
  }

  public StemStateDiffSchema getStemStateDiffSchema() {
    return stemStateDiffSchema;
  }

  public IpaProofSchema getIpaProofSchema() {
    return ipaProofSchema;
  }

  public VerkleProofSchema getVerkleProofSchema() {
    return verkleProofSchema;
  }

  public ExecutionWitnessSchema getExecutionWitnessSchema() {
    return executionWitnessSchema;
  }

  @Override
  public Optional<SchemaDefinitionsElectra> toVersionElectra() {
    return Optional.of(this);
  }
}
