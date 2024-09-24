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
import static tech.pegasys.teku.spec.schemas.SchemaTypes.BLS_TO_EXECUTION_CHANGE_SCHEMA;
import static tech.pegasys.teku.spec.schemas.SchemaTypes.SIGNED_BLS_TO_EXECUTION_CHANGE_SCHEMA;

import java.util.Optional;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainerSchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodyBuilder;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.capella.BeaconBlockBodyBuilderCapella;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.capella.BlindedBeaconBlockBodyCapella;
import tech.pegasys.teku.spec.datastructures.builder.BuilderBidSchema;
import tech.pegasys.teku.spec.datastructures.builder.SignedBuilderBidSchema;
import tech.pegasys.teku.spec.datastructures.builder.versions.bellatrix.BuilderBidSchemaBellatrix;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.Withdrawal;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.WithdrawalSchema;
import tech.pegasys.teku.spec.datastructures.operations.BlsToExecutionChangeSchema;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChangeSchema;
import tech.pegasys.teku.spec.datastructures.state.versions.capella.HistoricalSummary;

public class SchemaDefinitionsCapella extends SchemaDefinitionsBellatrix {
  private final WithdrawalSchema withdrawalSchema;

  private final BuilderBidSchema<?> builderBidSchemaCapella;
  private final SignedBuilderBidSchema signedBuilderBidSchemaCapella;

  private final HistoricalSummary.HistoricalSummarySchema historicalSummarySchema;

  public SchemaDefinitionsCapella(final SchemaRegistry schemaRegistry) {
    super(schemaRegistry);
    this.withdrawalSchema = Withdrawal.SSZ_SCHEMA;

    this.builderBidSchemaCapella =
        new BuilderBidSchemaBellatrix("BuilderBidCapella", getExecutionPayloadHeaderSchema());
    this.signedBuilderBidSchemaCapella =
        new SignedBuilderBidSchema("SignedBuilderBidCapella", builderBidSchemaCapella);
    this.historicalSummarySchema = new HistoricalSummary.HistoricalSummarySchema();
  }

  public static SchemaDefinitionsCapella required(final SchemaDefinitions schemaDefinitions) {
    checkArgument(
        schemaDefinitions instanceof SchemaDefinitionsCapella,
        "Expected definitions of type %s but got %s",
        SchemaDefinitionsCapella.class,
        schemaDefinitions.getClass());
    return (SchemaDefinitionsCapella) schemaDefinitions;
  }

  @Override
  @SuppressWarnings("unchecked")
  public BeaconBlockBodySchema<? extends BlindedBeaconBlockBodyCapella>
      getBlindedBeaconBlockBodySchema() {
    return (BeaconBlockBodySchema<? extends BlindedBeaconBlockBodyCapella>)
        schemaRegistry.get(BLINDED_BEACON_BLOCK_BODY_SCHEMA);
  }

  @Override
  public SignedBlockContainerSchema<SignedBlockContainer> getSignedBlindedBlockContainerSchema() {
    return getSignedBlindedBeaconBlockSchema().castTypeToSignedBlockContainer();
  }

  @Override
  public BeaconBlockBodyBuilder createBeaconBlockBodyBuilder() {
    return new BeaconBlockBodyBuilderCapella(
        getBeaconBlockBodySchema().toVersionCapella().orElseThrow(),
        getBlindedBeaconBlockBodySchema());
  }

  public WithdrawalSchema getWithdrawalSchema() {
    return withdrawalSchema;
  }

  public BlsToExecutionChangeSchema getBlsToExecutionChangeSchema() {
    return schemaRegistry.get(BLS_TO_EXECUTION_CHANGE_SCHEMA);
  }

  public SignedBlsToExecutionChangeSchema getSignedBlsToExecutionChangeSchema() {
    return schemaRegistry.get(SIGNED_BLS_TO_EXECUTION_CHANGE_SCHEMA);
  }

  public HistoricalSummary.HistoricalSummarySchema getHistoricalSummarySchema() {
    return historicalSummarySchema;
  }

  @Override
  public BuilderBidSchema<?> getBuilderBidSchema() {
    return builderBidSchemaCapella;
  }

  @Override
  public SignedBuilderBidSchema getSignedBuilderBidSchema() {
    return signedBuilderBidSchemaCapella;
  }

  @Override
  public Optional<SchemaDefinitionsCapella> toVersionCapella() {
    return Optional.of(this);
  }
}
