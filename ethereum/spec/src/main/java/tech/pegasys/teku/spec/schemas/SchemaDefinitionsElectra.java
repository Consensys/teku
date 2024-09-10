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
import static tech.pegasys.teku.spec.schemas.SchemaTypes.BEACON_STATE_SCHEMA;
import static tech.pegasys.teku.spec.schemas.SchemaTypes.BLINDED_BEACON_BLOCK_BODY_SCHEMA;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainerSchema;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainerSchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodyBuilder;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.electra.BeaconBlockBodyBuilderElectra;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.electra.BlindedBeaconBlockBodyElectra;
import tech.pegasys.teku.spec.datastructures.blocks.versions.deneb.BlockContentsSchema;
import tech.pegasys.teku.spec.datastructures.blocks.versions.deneb.SignedBlockContentsSchema;
import tech.pegasys.teku.spec.datastructures.builder.BlobsBundleSchema;
import tech.pegasys.teku.spec.datastructures.builder.BuilderBidSchema;
import tech.pegasys.teku.spec.datastructures.builder.BuilderPayloadSchema;
import tech.pegasys.teku.spec.datastructures.builder.ExecutionPayloadAndBlobsBundleSchema;
import tech.pegasys.teku.spec.datastructures.builder.SignedBuilderBidSchema;
import tech.pegasys.teku.spec.datastructures.builder.versions.deneb.BuilderBidSchemaDeneb;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ConsolidationRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ConsolidationRequestSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.DepositRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.DepositRequestSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionRequestsSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.WithdrawalRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.WithdrawalRequestSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateSchemaElectra;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingBalanceDeposit;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingConsolidation;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingPartialWithdrawal;

public class SchemaDefinitionsElectra extends SchemaDefinitionsDeneb {
  private final BuilderBidSchema<?> builderBidSchemaElectra;
  private final SignedBuilderBidSchema signedBuilderBidSchemaElectra;

  private final BlockContentsSchema blockContentsSchema;
  private final SignedBlockContentsSchema signedBlockContentsSchema;
  private final BlobsBundleSchema blobsBundleSchema;
  private final ExecutionPayloadAndBlobsBundleSchema executionPayloadAndBlobsBundleSchema;

  private final ExecutionRequestsSchema executionRequestsSchema;
  private final DepositRequestSchema depositRequestSchema;
  private final WithdrawalRequestSchema withdrawalRequestSchema;
  private final ConsolidationRequestSchema consolidationRequestSchema;

  private final PendingBalanceDeposit.PendingBalanceDepositSchema pendingBalanceDepositSchema;

  private final PendingPartialWithdrawal.PendingPartialWithdrawalSchema
      pendingPartialWithdrawalSchema;
  private final PendingConsolidation.PendingConsolidationSchema pendingConsolidationSchema;

  public SchemaDefinitionsElectra(final SchemaRegistry schemaRegistry) {
    super(schemaRegistry);

    final SpecConfigElectra specConfig = SpecConfigElectra.required(schemaRegistry.getSpecConfig());

    this.builderBidSchemaElectra =
        new BuilderBidSchemaDeneb(
            "BuilderBidElectra", getExecutionPayloadHeaderSchema(), getBlobKzgCommitmentsSchema());
    this.signedBuilderBidSchemaElectra =
        new SignedBuilderBidSchema("SignedBuilderBidElectra", builderBidSchemaElectra);

    this.blockContentsSchema =
        BlockContentsSchema.create(
            specConfig, getBeaconBlockSchema(), getBlobSchema(), "BlockContentsElectra");
    this.signedBlockContentsSchema =
        SignedBlockContentsSchema.create(
            specConfig,
            getSignedBeaconBlockSchema(),
            getBlobSchema(),
            "SignedBlockContentsElectra");
    this.blobsBundleSchema =
        new BlobsBundleSchema(
            "BlobsBundleElectra", getBlobSchema(), getBlobKzgCommitmentsSchema(), specConfig);
    this.executionPayloadAndBlobsBundleSchema =
        new ExecutionPayloadAndBlobsBundleSchema(getExecutionPayloadSchema(), blobsBundleSchema);

    this.executionRequestsSchema = new ExecutionRequestsSchema(specConfig);
    this.depositRequestSchema = DepositRequest.SSZ_SCHEMA;
    this.withdrawalRequestSchema = WithdrawalRequest.SSZ_SCHEMA;
    this.consolidationRequestSchema = ConsolidationRequest.SSZ_SCHEMA;
    this.pendingBalanceDepositSchema = new PendingBalanceDeposit.PendingBalanceDepositSchema();
    this.pendingPartialWithdrawalSchema =
        new PendingPartialWithdrawal.PendingPartialWithdrawalSchema();
    this.pendingConsolidationSchema = new PendingConsolidation.PendingConsolidationSchema();
  }

  public static SchemaDefinitionsElectra required(final SchemaDefinitions schemaDefinitions) {
    checkArgument(
        schemaDefinitions instanceof SchemaDefinitionsElectra,
        "Expected definitions of type %s but got %s",
        SchemaDefinitionsElectra.class,
        schemaDefinitions.getClass());
    return (SchemaDefinitionsElectra) schemaDefinitions;
  }

  @Override
  @SuppressWarnings("unchecked")
  public BeaconBlockBodySchema<? extends BlindedBeaconBlockBodyElectra>
      getBlindedBeaconBlockBodySchema() {
    return (BeaconBlockBodySchema<? extends BlindedBeaconBlockBodyElectra>)
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
    return new BeaconBlockBodyBuilderElectra(
        getBeaconBlockBodySchema().toVersionElectra().orElseThrow(),
        getBlindedBeaconBlockBodySchema());
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

  public ExecutionRequestsSchema getExecutionRequestsSchema() {
    return executionRequestsSchema;
  }

  public DepositRequestSchema getDepositRequestSchema() {
    return depositRequestSchema;
  }

  public WithdrawalRequestSchema getWithdrawalRequestSchema() {
    return withdrawalRequestSchema;
  }

  public PendingBalanceDeposit.PendingBalanceDepositSchema getPendingBalanceDepositSchema() {
    return pendingBalanceDepositSchema;
  }

  public SszListSchema<PendingBalanceDeposit, ?> getPendingBalanceDepositsSchema() {
    return BeaconStateSchemaElectra.required(schemaRegistry.get(BEACON_STATE_SCHEMA))
        .getPendingBalanceDepositsSchema();
  }

  public SszListSchema<PendingConsolidation, ?> getPendingConsolidationsSchema() {
    return BeaconStateSchemaElectra.required(schemaRegistry.get(BEACON_STATE_SCHEMA))
        .getPendingConsolidationsSchema();
  }

  public SszListSchema<PendingPartialWithdrawal, ?> getPendingPartialWithdrawalsSchema() {
    return BeaconStateSchemaElectra.required(schemaRegistry.get(BEACON_STATE_SCHEMA))
        .getPendingPartialWithdrawalsSchema();
  }

  public PendingPartialWithdrawal.PendingPartialWithdrawalSchema
      getPendingPartialWithdrawalSchema() {
    return pendingPartialWithdrawalSchema;
  }

  @Override
  public Optional<SchemaDefinitionsElectra> toVersionElectra() {
    return Optional.of(this);
  }

  public PendingConsolidation.PendingConsolidationSchema getPendingConsolidationSchema() {
    return pendingConsolidationSchema;
  }

  public ConsolidationRequestSchema getConsolidationRequestSchema() {
    return consolidationRequestSchema;
  }
}
