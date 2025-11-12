/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.spec.schemas.registry;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.spec.SpecMilestone.ALTAIR;
import static tech.pegasys.teku.spec.SpecMilestone.BELLATRIX;
import static tech.pegasys.teku.spec.SpecMilestone.CAPELLA;
import static tech.pegasys.teku.spec.SpecMilestone.DENEB;
import static tech.pegasys.teku.spec.SpecMilestone.ELECTRA;
import static tech.pegasys.teku.spec.SpecMilestone.FULU;
import static tech.pegasys.teku.spec.SpecMilestone.GLOAS;
import static tech.pegasys.teku.spec.SpecMilestone.PHASE0;
import static tech.pegasys.teku.spec.schemas.registry.BaseSchemaProvider.providerBuilder;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.AGGREGATE_AND_PROOF_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.ATTESTATION_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.ATTESTER_SLASHING_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.ATTNETS_ENR_FIELD_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BEACON_BLOCKS_BY_ROOT_REQUEST_MESSAGE_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BEACON_BLOCK_BODY_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BEACON_BLOCK_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BEACON_STATE_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BLINDED_BEACON_BLOCK_BODY_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BLINDED_BEACON_BLOCK_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BLOBS_BUNDLE_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BLOBS_IN_BLOCK_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BLOB_KZG_COMMITMENTS_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BLOB_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BLOB_SIDECARS_BY_ROOT_REQUEST_MESSAGE_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BLOB_SIDECAR_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BLOCK_CONTENTS_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BLS_TO_EXECUTION_CHANGE_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BUILDER_BID_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BUILDER_PENDING_PAYMENTS_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BUILDER_PENDING_PAYMENT_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BUILDER_PENDING_WITHDRAWALS_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BUILDER_PENDING_WITHDRAWAL_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.CELL_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.CONSOLIDATION_REQUEST_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.DATA_COLUMNS_BY_ROOT_IDENTIFIER_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.DATA_COLUMN_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.DATA_COLUMN_SIDECARS_BY_RANGE_REQUEST_MESSAGE_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.DATA_COLUMN_SIDECARS_BY_ROOT_REQUEST_MESSAGE_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.DATA_COLUMN_SIDECAR_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.DEPOSIT_REQUEST_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.EXECUTION_PAYLOAD_AND_BLOBS_BUNDLE_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.EXECUTION_PAYLOAD_AVAILABILITY_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.EXECUTION_PAYLOAD_BID_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.EXECUTION_PAYLOAD_ENVELOPES_BY_ROOT_REQUEST_MESSAGE_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.EXECUTION_PAYLOAD_ENVELOPE_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.EXECUTION_PAYLOAD_HEADER_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.EXECUTION_PAYLOAD_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.EXECUTION_PROOF_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.EXECUTION_REQUESTS_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.HISTORICAL_BATCH_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.HISTORICAL_SUMMARIES_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.INDEXED_ATTESTATION_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.INDEXED_PAYLOAD_ATTESTATION_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.LIGHT_CLIENT_BOOTSTRAP_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.MATRIX_ENTRY_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.METADATA_MESSAGE_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.PAYLOAD_ATTESTATION_DATA_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.PAYLOAD_ATTESTATION_MESSAGE_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.PAYLOAD_ATTESTATION_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.PENDING_CONSOLIDATIONS_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.PENDING_DEPOSITS_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.PENDING_PARTIAL_WITHDRAWALS_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.PROPOSER_LOOKAHEAD_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.SIGNED_AGGREGATE_AND_PROOF_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.SIGNED_BEACON_BLOCK_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.SIGNED_BLINDED_BEACON_BLOCK_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.SIGNED_BLOCK_CONTENTS_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.SIGNED_BLS_TO_EXECUTION_CHANGE_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.SIGNED_BUILDER_BID_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.SIGNED_EXECUTION_PAYLOAD_BID_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.SIGNED_EXECUTION_PAYLOAD_ENVELOPE_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.SINGLE_ATTESTATION_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.STATUS_MESSAGE_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.SYNCNETS_ENR_FIELD_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.WITHDRAWAL_REQUEST_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.WITHDRAWAL_SCHEMA;

import com.google.common.annotations.VisibleForTesting;
import java.util.HashSet;
import java.util.Set;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszVectorSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszUInt64VectorSchema;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.config.SpecConfigBellatrix;
import tech.pegasys.teku.spec.config.SpecConfigCapella;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.constants.NetworkConstants;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobKzgCommitmentsSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecarSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.CellSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecarSchemaFulu;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.MatrixEntrySchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.gloas.DataColumnSidecarSchemaGloas;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSchema;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockSchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.BeaconBlockBodySchemaAltairImpl;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.bellatrix.BeaconBlockBodySchemaBellatrixImpl;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.bellatrix.BlindedBeaconBlockBodySchemaBellatrixImpl;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.capella.BeaconBlockBodySchemaCapellaImpl;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.capella.BlindedBeaconBlockBodySchemaCapellaImpl;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.BeaconBlockBodySchemaDenebImpl;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.BlindedBeaconBlockBodySchemaDenebImpl;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.electra.BeaconBlockBodySchemaElectraImpl;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.electra.BlindedBeaconBlockBodySchemaElectraImpl;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.gloas.BeaconBlockBodySchemaGloasImpl;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.phase0.BeaconBlockBodySchemaPhase0;
import tech.pegasys.teku.spec.datastructures.blocks.versions.deneb.BlockContentsSchemaDeneb;
import tech.pegasys.teku.spec.datastructures.blocks.versions.deneb.SignedBlockContentsSchemaDeneb;
import tech.pegasys.teku.spec.datastructures.blocks.versions.fulu.BlockContentsSchemaFulu;
import tech.pegasys.teku.spec.datastructures.blocks.versions.fulu.SignedBlockContentsSchemaFulu;
import tech.pegasys.teku.spec.datastructures.builder.ExecutionPayloadAndBlobsBundleSchema;
import tech.pegasys.teku.spec.datastructures.builder.SignedBuilderBidSchema;
import tech.pegasys.teku.spec.datastructures.builder.versions.bellatrix.BuilderBidSchemaBellatrix;
import tech.pegasys.teku.spec.datastructures.builder.versions.deneb.BlobsBundleSchemaDeneb;
import tech.pegasys.teku.spec.datastructures.builder.versions.deneb.BuilderBidSchemaDeneb;
import tech.pegasys.teku.spec.datastructures.builder.versions.electra.BuilderBidSchemaElectra;
import tech.pegasys.teku.spec.datastructures.builder.versions.fulu.BlobsBundleSchemaFulu;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.BuilderPendingPaymentSchema;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.BuilderPendingWithdrawalSchema;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBidSchema;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadEnvelopeSchema;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.IndexedPayloadAttestationSchema;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationDataSchema;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationMessageSchema;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationSchema;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBidSchema;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelopeSchema;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionProofSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.bellatrix.ExecutionPayloadHeaderSchemaBellatrix;
import tech.pegasys.teku.spec.datastructures.execution.versions.bellatrix.ExecutionPayloadSchemaBellatrix;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.ExecutionPayloadHeaderSchemaCapella;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.ExecutionPayloadSchemaCapella;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.WithdrawalSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.ExecutionPayloadHeaderSchemaDeneb;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.ExecutionPayloadSchemaDeneb;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ConsolidationRequestSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.DepositRequestSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionRequestsSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.WithdrawalRequestSchema;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientBootstrapSchema;
import tech.pegasys.teku.spec.datastructures.lightclient.versions.electra.LightClientBootstrapSchemaElectra;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BeaconBlocksByRootRequestMessage.BeaconBlocksByRootRequestMessageSchema;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobSidecarsByRootRequestMessageSchema;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnSidecarsByRangeRequestMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnSidecarsByRootRequestMessageSchema;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnsByRootIdentifierSchema;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.ExecutionPayloadEnvelopesByRootRequestMessage.ExecutionPayloadEnvelopesByRootRequestMessageSchema;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.versions.altair.MetadataMessageSchemaAltair;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.versions.fulu.MetadataMessageSchemaFulu;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.versions.phase0.MetadataMessageSchemaPhase0;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.status.versions.fulu.StatusMessageSchemaFulu;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.status.versions.phase0.StatusMessageSchemaPhase0;
import tech.pegasys.teku.spec.datastructures.operations.AggregateAndProof.AggregateAndProofSchema;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashingSchema;
import tech.pegasys.teku.spec.datastructures.operations.BlsToExecutionChangeSchema;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestationSchema;
import tech.pegasys.teku.spec.datastructures.operations.SignedAggregateAndProof.SignedAggregateAndProofSchema;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChangeSchema;
import tech.pegasys.teku.spec.datastructures.operations.SingleAttestationSchema;
import tech.pegasys.teku.spec.datastructures.operations.versions.electra.AttestationElectraSchema;
import tech.pegasys.teku.spec.datastructures.operations.versions.phase0.AttestationPhase0Schema;
import tech.pegasys.teku.spec.datastructures.state.HistoricalBatch.HistoricalBatchSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateSchemaAltair;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.bellatrix.BeaconStateSchemaBellatrix;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.capella.BeaconStateSchemaCapella;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.deneb.BeaconStateSchemaDeneb;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateSchemaElectra;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.fulu.BeaconStateSchemaFulu;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateSchemaGloas;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.phase0.BeaconStateSchemaPhase0;
import tech.pegasys.teku.spec.datastructures.state.versions.capella.HistoricalSummary.HistoricalSummarySchema;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingConsolidation.PendingConsolidationSchema;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingDeposit.PendingDepositSchema;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingPartialWithdrawal.PendingPartialWithdrawalSchema;
import tech.pegasys.teku.spec.schemas.registry.SchemaTypes.SchemaId;

public class SchemaRegistryBuilder {

  private final Set<SchemaProvider<?>> providers = new HashSet<>();
  private final Set<SchemaId<?>> schemaIds = new HashSet<>();
  private final SchemaCache cache;
  private SpecMilestone lastBuiltSchemaRegistryMilestone;

  public static SchemaRegistryBuilder create() {
    return new SchemaRegistryBuilder()
        // PHASE0
        .addProvider(createAttnetsENRFieldSchemaProvider())
        .addProvider(createSyncnetsENRFieldSchemaProvider())
        .addProvider(createBeaconBlocksByRootRequestMessageSchemaProvider())
        .addProvider(createHistoricalBatchSchemaProvider())
        .addProvider(createIndexedAttestationSchemaProvider())
        .addProvider(createAttesterSlashingSchemaProvider())
        .addProvider(createAttestationSchemaProvider())
        .addProvider(createAggregateAndProofSchemaProvider())
        .addProvider(createSignedAggregateAndProofSchemaProvider())
        .addProvider(createBeaconBlockBodySchemaProvider())
        .addProvider(createBeaconBlockSchemaProvider())
        .addProvider(createSignedBeaconBlockSchemaProvider())
        .addProvider(createBeaconStateSchemaProvider())
        .addProvider(createMetadataMessageSchemaProvider())
        .addProvider(createStatusMessageSchemaProvider())
        .addProvider(createLightClientBootstrapSchemaProvider())

        // BELLATRIX
        .addProvider(createExecutionPayloadSchemaProvider())
        .addProvider(createExecutionPayloadHeaderSchemaProvider())
        .addProvider(createBlindedBeaconBlockBodySchemaProvider())
        .addProvider(createBlindedBeaconBlockSchemaProvider())
        .addProvider(createSignedBlindedBeaconBlockSchemaProvider())
        .addProvider(createBuilderBidSchemaProvider())
        .addProvider(createSignedBuilderBidSchemaProvider())

        // CAPELLA
        .addProvider(createWithdrawalSchemaProvider())
        .addProvider(createBlsToExecutionChangeSchemaProvider())
        .addProvider(createSignedBlsToExecutionChangeSchemaProvider())
        .addProvider(createHistoricalSummariesSchemaProvider())

        // DENEB
        .addProvider(createBlobKzgCommitmentsSchemaProvider())
        .addProvider(createBlobSchemaProvider())
        .addProvider(createBlobsInBlockSchemaProvider())
        .addProvider(createBlobSidecarSchemaProvider())
        .addProvider(createBlobSidecarsByRootRequestMessageSchemaProvider())
        .addProvider(createBlobsBundleSchemaProvider())
        .addProvider(createBlockContentsSchema())
        .addProvider(createSignedBlockContentsSchema())
        .addProvider(createExecutionPayloadAndBlobsBundleSchemaProvider())

        // ELECTRA
        .addProvider(createPendingConsolidationsSchemaProvider())
        .addProvider(createPendingPartialWithdrawalsSchemaProvider())
        .addProvider(createPendingDepositsSchemaProvider())
        .addProvider(createDepositRequestSchemaProvider())
        .addProvider(createWithdrawalRequestSchemaProvider())
        .addProvider(createConsolidationRequestSchemaProvider())
        .addProvider(createExecutionRequestsSchemaProvider())
        .addProvider(createSingleAttestationSchemaProvider())
        .addProvider(createExecutionProofSchemaProvider())

        // FULU
        .addProvider(createCellSchemaProvider())
        .addProvider(createDataColumnSchemaProvider())
        .addProvider(createDataColumnSidecarSchemaProvider())
        .addProvider(createDataColumnsByRootIdentifierSchemaProvider())
        .addProvider(createMatrixEntrySchemaProvider())
        .addProvider(createProposerLookaheadSchemaProvider())
        .addProvider(createDataColumnSidecarsByRootRequestMessageSchemaProvider())
        .addProvider(createDataColumnSidecarsByRangeRequestMessageSchemaProvider())

        // GLOAS
        .addProvider(createBuilderPendingWithdrawalSchemaProvider())
        .addProvider(createBuilderPendingPaymentSchemaProvider())
        .addProvider(createPayloadAttestationDataSchemaProvider())
        .addProvider(createPayloadAttestationSchemaProvider())
        .addProvider(createPayloadAttestationMessageSchemaProvider())
        .addProvider(createIndexedPayloadAttestationSchemaProvider())
        .addProvider(createExecutionPayloadBidSchemaProvider())
        .addProvider(createSignedExecutionPayloadBidSchemaProvider())
        .addProvider(createExecutionPayloadEnvelopeSchemaProvider())
        .addProvider(createSignedExecutionPayloadEnvelopeSchemaProvider())
        .addProvider(createExecutionPayloadAvailabilitySchemaProvider())
        .addProvider(createBuilderPendingPaymentsSchemaProvider())
        .addProvider(createBuilderPendingWithdrawalsSchemaProvider())
        .addProvider(createExecutionPayloadEnvelopesByRootRequestMessageSchemaProvider());
  }

  private static SchemaProvider<?> createSingleAttestationSchemaProvider() {
    return providerBuilder(SINGLE_ATTESTATION_SCHEMA)
        .withCreator(ELECTRA, (registry, specConfig, schemaName) -> new SingleAttestationSchema())
        .build();
  }

  private static SchemaProvider<?> createDepositRequestSchemaProvider() {
    return providerBuilder(DEPOSIT_REQUEST_SCHEMA)
        .withCreator(ELECTRA, (registry, specConfig, schemaName) -> new DepositRequestSchema())
        .build();
  }

  private static SchemaProvider<?> createWithdrawalRequestSchemaProvider() {
    return providerBuilder(WITHDRAWAL_REQUEST_SCHEMA)
        .withCreator(ELECTRA, (registry, specConfig, schemaName) -> new WithdrawalRequestSchema())
        .build();
  }

  private static SchemaProvider<?> createConsolidationRequestSchemaProvider() {
    return providerBuilder(CONSOLIDATION_REQUEST_SCHEMA)
        .withCreator(
            ELECTRA, (registry, specConfig, schemaName) -> new ConsolidationRequestSchema())
        .build();
  }

  private static SchemaProvider<?> createBlockContentsSchema() {
    return providerBuilder(BLOCK_CONTENTS_SCHEMA)
        .withCreator(
            DENEB,
            (registry, specConfig, schemaName) ->
                new BlockContentsSchemaDeneb(
                    schemaName, SpecConfigDeneb.required(specConfig), registry))
        .withCreator(
            FULU,
            (registry, specConfig, schemaName) ->
                new BlockContentsSchemaFulu(
                    schemaName, SpecConfigFulu.required(specConfig), registry))
        .build();
  }

  private static SchemaProvider<?> createSignedBlockContentsSchema() {
    return providerBuilder(SIGNED_BLOCK_CONTENTS_SCHEMA)
        .withCreator(
            DENEB,
            (registry, specConfig, schemaName) ->
                new SignedBlockContentsSchemaDeneb(
                    schemaName, SpecConfigDeneb.required(specConfig), registry))
        .withCreator(
            FULU,
            (registry, specConfig, schemaName) ->
                new SignedBlockContentsSchemaFulu(
                    schemaName, SpecConfigFulu.required(specConfig), registry))
        .build();
  }

  private static SchemaProvider<?> createSignedBuilderBidSchemaProvider() {
    return providerBuilder(SIGNED_BUILDER_BID_SCHEMA)
        .withCreator(
            BELLATRIX,
            (registry, specConfig, schemaName) -> new SignedBuilderBidSchema(schemaName, registry))
        .build();
  }

  private static SchemaProvider<?> createBuilderBidSchemaProvider() {
    return providerBuilder(BUILDER_BID_SCHEMA)
        .withCreator(
            BELLATRIX,
            (registry, specConfig, schemaName) ->
                new BuilderBidSchemaBellatrix(schemaName, registry))
        .withCreator(
            DENEB,
            (registry, specConfig, schemaName) -> new BuilderBidSchemaDeneb(schemaName, registry))
        .withCreator(
            ELECTRA,
            (registry, specConfig, schemaName) -> new BuilderBidSchemaElectra(schemaName, registry))
        .build();
  }

  private static SchemaProvider<?> createPendingDepositsSchemaProvider() {
    return providerBuilder(PENDING_DEPOSITS_SCHEMA)
        .withCreator(
            ELECTRA,
            (registry, specConfig, schemaName) ->
                SszListSchema.create(
                    new PendingDepositSchema(),
                    SpecConfigElectra.required(specConfig).getPendingDepositsLimit()))
        .build();
  }

  private static SchemaProvider<?> createPendingPartialWithdrawalsSchemaProvider() {
    return providerBuilder(PENDING_PARTIAL_WITHDRAWALS_SCHEMA)
        .withCreator(
            ELECTRA,
            (registry, specConfig, schemaName) ->
                SszListSchema.create(
                    new PendingPartialWithdrawalSchema(),
                    SpecConfigElectra.required(specConfig).getPendingPartialWithdrawalsLimit()))
        .build();
  }

  private static SchemaProvider<?> createPendingConsolidationsSchemaProvider() {
    return providerBuilder(PENDING_CONSOLIDATIONS_SCHEMA)
        .withCreator(
            ELECTRA,
            (registry, specConfig, schemaName) ->
                SszListSchema.create(
                    new PendingConsolidationSchema(),
                    SpecConfigElectra.required(specConfig).getPendingConsolidationsLimit()))
        .build();
  }

  private static SchemaProvider<?> createExecutionPayloadAndBlobsBundleSchemaProvider() {
    return providerBuilder(EXECUTION_PAYLOAD_AND_BLOBS_BUNDLE_SCHEMA)
        .withCreator(
            DENEB,
            (registry, specConfig, schemaName) ->
                new ExecutionPayloadAndBlobsBundleSchema(registry))
        .build();
  }

  private static SchemaProvider<?> createBeaconStateSchemaProvider() {
    return providerBuilder(BEACON_STATE_SCHEMA)
        .withCreator(
            PHASE0,
            (registry, specConfig, schemaName) -> BeaconStateSchemaPhase0.create(specConfig))
        .withCreator(
            ALTAIR,
            (registry, specConfig, schemaName) -> BeaconStateSchemaAltair.create(specConfig))
        .withCreator(
            BELLATRIX,
            (registry, specConfig, schemaName) ->
                BeaconStateSchemaBellatrix.create(specConfig, registry))
        .withCreator(
            CAPELLA,
            (registry, specConfig, schemaName) ->
                BeaconStateSchemaCapella.create(specConfig, registry))
        .withCreator(
            DENEB,
            (registry, specConfig, schemaName) ->
                BeaconStateSchemaDeneb.create(SpecConfigDeneb.required(specConfig), registry))
        .withCreator(
            ELECTRA,
            (registry, specConfig, schemaName) ->
                BeaconStateSchemaElectra.create(SpecConfigElectra.required(specConfig), registry))
        .withCreator(
            FULU,
            (registry, specConfig, schemaName) ->
                BeaconStateSchemaFulu.create(SpecConfigFulu.required(specConfig), registry))
        .withCreator(
            GLOAS,
            (registry, specConfig, schemaName) ->
                BeaconStateSchemaGloas.create(SpecConfigGloas.required(specConfig), registry))
        .build();
  }

  private static SchemaProvider<?> createBlindedBeaconBlockSchemaProvider() {
    return providerBuilder(BLINDED_BEACON_BLOCK_SCHEMA)
        .withCreator(
            BELLATRIX,
            (registry, specConfig, schemaName) ->
                new BeaconBlockSchema(registry.get(BLINDED_BEACON_BLOCK_BODY_SCHEMA), schemaName))
        .build();
  }

  private static SchemaProvider<?> createSignedBlindedBeaconBlockSchemaProvider() {
    return providerBuilder(SIGNED_BLINDED_BEACON_BLOCK_SCHEMA)
        .withCreator(
            BELLATRIX,
            (registry, specConfig, schemaName) ->
                new SignedBeaconBlockSchema(registry.get(BLINDED_BEACON_BLOCK_SCHEMA), schemaName))
        .build();
  }

  private static SchemaProvider<?> createBeaconBlockSchemaProvider() {
    return providerBuilder(BEACON_BLOCK_SCHEMA)
        .withCreator(
            PHASE0,
            (registry, specConfig, schemaName) ->
                new BeaconBlockSchema(registry.get(BEACON_BLOCK_BODY_SCHEMA), schemaName))
        .build();
  }

  private static SchemaProvider<?> createSignedBeaconBlockSchemaProvider() {
    return providerBuilder(SIGNED_BEACON_BLOCK_SCHEMA)
        .withCreator(
            PHASE0,
            (registry, specConfig, schemaName) ->
                new SignedBeaconBlockSchema(registry.get(BEACON_BLOCK_SCHEMA), schemaName))
        .build();
  }

  private static SchemaProvider<?> createExecutionRequestsSchemaProvider() {
    return providerBuilder(EXECUTION_REQUESTS_SCHEMA)
        .withCreator(
            ELECTRA,
            (registry, specConfig, schemaName) ->
                new ExecutionRequestsSchema(
                    SpecConfigElectra.required(specConfig), registry, schemaName))
        .build();
  }

  private static SchemaProvider<?> createBlindedBeaconBlockBodySchemaProvider() {
    return providerBuilder(BLINDED_BEACON_BLOCK_BODY_SCHEMA)
        .withCreator(
            BELLATRIX,
            (registry, specConfig, schemaName) ->
                BlindedBeaconBlockBodySchemaBellatrixImpl.create(
                    SpecConfigBellatrix.required(specConfig), schemaName, registry))
        .withCreator(
            CAPELLA,
            (registry, specConfig, schemaName) ->
                BlindedBeaconBlockBodySchemaCapellaImpl.create(
                    SpecConfigCapella.required(specConfig), schemaName, registry))
        .withCreator(
            DENEB,
            (registry, specConfig, schemaName) ->
                BlindedBeaconBlockBodySchemaDenebImpl.create(
                    SpecConfigDeneb.required(specConfig), schemaName, registry))
        .withCreator(
            ELECTRA,
            (registry, specConfig, schemaName) ->
                BlindedBeaconBlockBodySchemaElectraImpl.create(
                    SpecConfigElectra.required(specConfig), schemaName, registry))
        .build();
  }

  private static SchemaProvider<?> createBeaconBlockBodySchemaProvider() {
    return providerBuilder(BEACON_BLOCK_BODY_SCHEMA)
        .withCreator(
            PHASE0,
            (registry, specConfig, schemaName) ->
                BeaconBlockBodySchemaPhase0.create(specConfig, schemaName, registry))
        .withCreator(
            ALTAIR,
            (registry, specConfig, schemaName) ->
                BeaconBlockBodySchemaAltairImpl.create(specConfig, schemaName, registry))
        .withCreator(
            BELLATRIX,
            (registry, specConfig, schemaName) ->
                BeaconBlockBodySchemaBellatrixImpl.create(
                    SpecConfigBellatrix.required(specConfig), schemaName, registry))
        .withCreator(
            CAPELLA,
            (registry, specConfig, schemaName) ->
                BeaconBlockBodySchemaCapellaImpl.create(
                    SpecConfigCapella.required(specConfig), schemaName, registry))
        .withCreator(
            DENEB,
            (registry, specConfig, schemaName) ->
                BeaconBlockBodySchemaDenebImpl.create(
                    SpecConfigDeneb.required(specConfig), schemaName, registry))
        .withCreator(
            ELECTRA,
            (registry, specConfig, schemaName) ->
                BeaconBlockBodySchemaElectraImpl.create(
                    SpecConfigElectra.required(specConfig), schemaName, registry))
        .withCreator(
            GLOAS,
            (registry, specConfig, schemaName) ->
                BeaconBlockBodySchemaGloasImpl.create(
                    SpecConfigGloas.required(specConfig), schemaName, registry))
        .build();
  }

  private static SchemaProvider<?> createExecutionPayloadHeaderSchemaProvider() {
    return providerBuilder(EXECUTION_PAYLOAD_HEADER_SCHEMA)
        .withCreator(
            BELLATRIX,
            (registry, specConfig, schemaName) ->
                new ExecutionPayloadHeaderSchemaBellatrix(SpecConfigBellatrix.required(specConfig)))
        .withCreator(
            CAPELLA,
            (registry, specConfig, schemaName) ->
                new ExecutionPayloadHeaderSchemaCapella(SpecConfigCapella.required(specConfig)))
        .withCreator(
            DENEB,
            (registry, specConfig, schemaName) ->
                new ExecutionPayloadHeaderSchemaDeneb(SpecConfigDeneb.required(specConfig)))
        .build();
  }

  private static SchemaProvider<?> createExecutionPayloadSchemaProvider() {
    return providerBuilder(EXECUTION_PAYLOAD_SCHEMA)
        .withCreator(
            BELLATRIX,
            (registry, specConfig, schemaName) ->
                new ExecutionPayloadSchemaBellatrix(SpecConfigBellatrix.required(specConfig)))
        .withCreator(
            CAPELLA,
            (registry, specConfig, schemaName) ->
                new ExecutionPayloadSchemaCapella(SpecConfigCapella.required(specConfig)))
        .withCreator(
            DENEB,
            (registry, specConfig, schemaName) ->
                new ExecutionPayloadSchemaDeneb(SpecConfigDeneb.required(specConfig)))
        .build();
  }

  private static SchemaProvider<?> createBlobsBundleSchemaProvider() {
    return providerBuilder(BLOBS_BUNDLE_SCHEMA)
        .withCreator(
            DENEB,
            (registry, specConfig, schemaName) ->
                new BlobsBundleSchemaDeneb(registry, SpecConfigDeneb.required(specConfig)))
        .withCreator(
            FULU,
            (registry, specConfig, schemaName) ->
                new BlobsBundleSchemaFulu(registry, SpecConfigDeneb.required(specConfig)))
        .build();
  }

  private static SchemaProvider<?> createBlobKzgCommitmentsSchemaProvider() {
    return providerBuilder(BLOB_KZG_COMMITMENTS_SCHEMA)
        .withCreator(
            DENEB,
            (registry, specConfig, schemaName) ->
                new BlobKzgCommitmentsSchema(SpecConfigDeneb.required(specConfig)))
        .build();
  }

  private static SchemaProvider<?> createBlobSchemaProvider() {
    return providerBuilder(BLOB_SCHEMA)
        .withCreator(
            DENEB,
            (registry, specConfig, schemaName) ->
                new BlobSchema(SpecConfigDeneb.required(specConfig)))
        .build();
  }

  private static SchemaProvider<?> createBlobsInBlockSchemaProvider() {
    return providerBuilder(BLOBS_IN_BLOCK_SCHEMA)
        .withCreator(
            DENEB,
            (registry, specConfig, schemaName) ->
                SszListSchema.create(
                    registry.get(BLOB_SCHEMA),
                    SpecConfigDeneb.required(specConfig).getMaxBlobsPerBlock()))
        .build();
  }

  private static SchemaProvider<?> createBlobSidecarSchemaProvider() {
    return providerBuilder(BLOB_SIDECAR_SCHEMA)
        .withCreator(
            DENEB,
            (registry, specConfig, schemaName) ->
                BlobSidecarSchema.create(
                    SignedBeaconBlockHeader.SSZ_SCHEMA,
                    registry.get(BLOB_SCHEMA),
                    SpecConfigDeneb.required(specConfig).getKzgCommitmentInclusionProofDepth()))
        .build();
  }

  private static SchemaProvider<?> createBlobSidecarsByRootRequestMessageSchemaProvider() {
    return providerBuilder(BLOB_SIDECARS_BY_ROOT_REQUEST_MESSAGE_SCHEMA)
        .withCreator(
            DENEB,
            (registry, specConfig, schemaName) ->
                new BlobSidecarsByRootRequestMessageSchema(SpecConfigDeneb.required(specConfig)))
        .build();
  }

  private static SchemaProvider<?> createHistoricalSummariesSchemaProvider() {
    return providerBuilder(HISTORICAL_SUMMARIES_SCHEMA)
        .withCreator(
            CAPELLA,
            (registry, specConfig, schemaName) ->
                SszListSchema.create(
                    new HistoricalSummarySchema(), specConfig.getHistoricalRootsLimit()))
        .build();
  }

  private static SchemaProvider<?> createSignedBlsToExecutionChangeSchemaProvider() {
    return providerBuilder(SIGNED_BLS_TO_EXECUTION_CHANGE_SCHEMA)
        .withCreator(
            CAPELLA,
            (registry, specConfig, schemaName) -> new SignedBlsToExecutionChangeSchema(registry))
        .build();
  }

  private static SchemaProvider<?> createBlsToExecutionChangeSchemaProvider() {
    return providerBuilder(BLS_TO_EXECUTION_CHANGE_SCHEMA)
        .withCreator(
            CAPELLA, (registry, specConfig, schemaName) -> new BlsToExecutionChangeSchema())
        .build();
  }

  private static SchemaProvider<?> createWithdrawalSchemaProvider() {
    return providerBuilder(WITHDRAWAL_SCHEMA)
        .withCreator(CAPELLA, (registry, specConfig, schemaName) -> new WithdrawalSchema())
        .build();
  }

  private static SchemaProvider<?> createAttnetsENRFieldSchemaProvider() {
    return providerBuilder(ATTNETS_ENR_FIELD_SCHEMA)
        .withCreator(
            PHASE0,
            (registry, specConfig, schemaName) ->
                SszBitvectorSchema.create(specConfig.getAttestationSubnetCount()))
        .build();
  }

  private static SchemaProvider<?> createSyncnetsENRFieldSchemaProvider() {
    return providerBuilder(SYNCNETS_ENR_FIELD_SCHEMA)
        .withCreator(
            PHASE0,
            (registry, specConfig, schemaName) ->
                SszBitvectorSchema.create(NetworkConstants.SYNC_COMMITTEE_SUBNET_COUNT))
        .build();
  }

  private static SchemaProvider<?> createBeaconBlocksByRootRequestMessageSchemaProvider() {
    return providerBuilder(BEACON_BLOCKS_BY_ROOT_REQUEST_MESSAGE_SCHEMA)
        .withCreator(
            PHASE0,
            (registry, specConfig, schemaName) ->
                new BeaconBlocksByRootRequestMessageSchema(specConfig))
        .build();
  }

  private static SchemaProvider<?> createHistoricalBatchSchemaProvider() {
    return providerBuilder(HISTORICAL_BATCH_SCHEMA)
        .withCreator(
            PHASE0,
            (registry, specConfig, schemaName) ->
                new HistoricalBatchSchema(specConfig.getSlotsPerHistoricalRoot()))
        .build();
  }

  private static SchemaProvider<?> createAttesterSlashingSchemaProvider() {
    return providerBuilder(ATTESTER_SLASHING_SCHEMA)
        .withCreator(
            PHASE0,
            (registry, specConfig, schemaName) -> new AttesterSlashingSchema(schemaName, registry))
        .withCreator(
            ELECTRA,
            (registry, specConfig, schemaName) -> new AttesterSlashingSchema(schemaName, registry))
        .build();
  }

  private static SchemaProvider<?> createIndexedAttestationSchemaProvider() {
    return providerBuilder(INDEXED_ATTESTATION_SCHEMA)
        .withCreator(
            PHASE0,
            (registry, specConfig, schemaName) ->
                new IndexedAttestationSchema(
                    schemaName, getMaxValidatorsPerAttestationPhase0(specConfig)))
        .withCreator(
            ELECTRA,
            (registry, specConfig, schemaName) ->
                new IndexedAttestationSchema(
                    schemaName, getMaxValidatorsPerAttestationElectra(specConfig)))
        .build();
  }

  private static SchemaProvider<?> createAttestationSchemaProvider() {
    return providerBuilder(ATTESTATION_SCHEMA)
        .withCreator(
            PHASE0,
            (registry, specConfig, schemaName) ->
                new AttestationPhase0Schema(getMaxValidatorsPerAttestationPhase0(specConfig))
                    .castTypeToAttestationSchema())
        .withCreator(
            ELECTRA,
            (registry, specConfig, schemaName) ->
                new AttestationElectraSchema(
                        getMaxValidatorsPerAttestationElectra(specConfig),
                        specConfig.getMaxCommitteesPerSlot())
                    .castTypeToAttestationSchema())
        .build();
  }

  private static SchemaProvider<?> createAggregateAndProofSchemaProvider() {
    return providerBuilder(AGGREGATE_AND_PROOF_SCHEMA)
        .withCreator(
            PHASE0,
            (registry, specConfig, schemaName) -> new AggregateAndProofSchema(schemaName, registry))
        .withCreator(
            ELECTRA,
            (registry, specConfig, schemaName) -> new AggregateAndProofSchema(schemaName, registry))
        .build();
  }

  private static SchemaProvider<?> createLightClientBootstrapSchemaProvider() {
    return providerBuilder(LIGHT_CLIENT_BOOTSTRAP_SCHEMA)
        .withCreator(
            ALTAIR,
            (registry, specConfig, schemaName) ->
                new LightClientBootstrapSchema(SpecConfigAltair.required(specConfig)))
        .withCreator(
            ELECTRA,
            (registry, specConfig, schemaName) ->
                new LightClientBootstrapSchemaElectra(SpecConfigElectra.required(specConfig)))
        .build();
  }

  private static SchemaProvider<?> createSignedAggregateAndProofSchemaProvider() {
    return providerBuilder(SIGNED_AGGREGATE_AND_PROOF_SCHEMA)
        .withCreator(
            PHASE0,
            (registry, specConfig, schemaName) ->
                new SignedAggregateAndProofSchema(schemaName, registry))
        .withCreator(
            ELECTRA,
            (registry, specConfig, schemaName) ->
                new SignedAggregateAndProofSchema(schemaName, registry))
        .build();
  }

  private static SchemaProvider<?> createCellSchemaProvider() {
    return providerBuilder(CELL_SCHEMA)
        .withCreator(
            FULU,
            (registry, specConfig, schemaName) ->
                new CellSchema(SpecConfigFulu.required(specConfig)))
        .build();
  }

  private static SchemaProvider<?> createDataColumnSchemaProvider() {
    return providerBuilder(DATA_COLUMN_SCHEMA)
        .withCreator(
            FULU,
            (registry, specConfig, schemaName) ->
                new DataColumnSchema(SpecConfigDeneb.required(specConfig), registry))
        .build();
  }

  private static SchemaProvider<?> createDataColumnSidecarSchemaProvider() {
    return providerBuilder(DATA_COLUMN_SIDECAR_SCHEMA)
        .withCreator(
            FULU,
            (registry, specConfig, schemaName) ->
                new DataColumnSidecarSchemaFulu(
                    SignedBeaconBlockHeader.SSZ_SCHEMA,
                    registry.get(DATA_COLUMN_SCHEMA),
                    SpecConfigFulu.required(specConfig)))
        .withCreator(
            GLOAS,
            (registry, specConfig, schemaName) ->
                new DataColumnSidecarSchemaGloas(
                    registry.get(DATA_COLUMN_SCHEMA), SpecConfigGloas.required(specConfig)))
        .build();
  }

  private static SchemaProvider<?> createDataColumnsByRootIdentifierSchemaProvider() {
    return providerBuilder(DATA_COLUMNS_BY_ROOT_IDENTIFIER_SCHEMA)
        .withCreator(
            FULU,
            (registry, specConfig, schemaName) ->
                new DataColumnsByRootIdentifierSchema(SpecConfigFulu.required(specConfig)))
        .build();
  }

  private static SchemaProvider<?> createMatrixEntrySchemaProvider() {
    return providerBuilder(MATRIX_ENTRY_SCHEMA)
        .withCreator(
            FULU,
            (registry, specConfig, schemaName) ->
                MatrixEntrySchema.create(registry.get(CELL_SCHEMA)))
        .build();
  }

  private static SchemaProvider<?> createProposerLookaheadSchemaProvider() {
    return providerBuilder(PROPOSER_LOOKAHEAD_SCHEMA)
        .withCreator(
            FULU,
            (registry, specConfig, schemaName) ->
                SszUInt64VectorSchema.create(
                    (long) (specConfig.getMinSeedLookahead() + 1) * specConfig.getSlotsPerEpoch()))
        .build();
  }

  private static SchemaProvider<?> createDataColumnSidecarsByRootRequestMessageSchemaProvider() {
    return providerBuilder(DATA_COLUMN_SIDECARS_BY_ROOT_REQUEST_MESSAGE_SCHEMA)
        .withCreator(
            FULU,
            (registry, specConfig, schemaName) ->
                new DataColumnSidecarsByRootRequestMessageSchema(
                    SpecConfigFulu.required(specConfig),
                    registry.get(DATA_COLUMNS_BY_ROOT_IDENTIFIER_SCHEMA)))
        .build();
  }

  private static SchemaProvider<?> createDataColumnSidecarsByRangeRequestMessageSchemaProvider() {
    return providerBuilder(DATA_COLUMN_SIDECARS_BY_RANGE_REQUEST_MESSAGE_SCHEMA)
        .withCreator(
            FULU,
            (registry, specConfig, schemaName) ->
                new DataColumnSidecarsByRangeRequestMessage
                    .DataColumnSidecarsByRangeRequestMessageSchema(
                    SpecConfigFulu.required(specConfig)))
        .build();
  }

  private static SchemaProvider<?> createMetadataMessageSchemaProvider() {
    return providerBuilder(METADATA_MESSAGE_SCHEMA)
        .withCreator(
            PHASE0,
            (registry, specConfig, schemaName) ->
                new MetadataMessageSchemaPhase0(specConfig.getNetworkingConfig()))
        .withCreator(
            ALTAIR,
            (registry, specConfig, schemaName) ->
                new MetadataMessageSchemaAltair(specConfig.getNetworkingConfig()))
        .withCreator(
            FULU, (registry, specConfig, schemaName) -> new MetadataMessageSchemaFulu(specConfig))
        .build();
  }

  private static SchemaProvider<?> createStatusMessageSchemaProvider() {
    return providerBuilder(STATUS_MESSAGE_SCHEMA)
        .withCreator(PHASE0, (registry, specConfig, schemaName) -> new StatusMessageSchemaPhase0())
        .withCreator(FULU, (registry, specConfig, schemaName) -> new StatusMessageSchemaFulu())
        .build();
  }

  private static SchemaProvider<?> createExecutionProofSchemaProvider() {
    return providerBuilder(EXECUTION_PROOF_SCHEMA)
        .withCreator(ELECTRA, (registry, specConfig, schemaName) -> new ExecutionProofSchema())
        .build();
  }

  private static long getMaxValidatorsPerAttestationPhase0(final SpecConfig specConfig) {
    return specConfig.getMaxValidatorsPerCommittee();
  }

  private static long getMaxValidatorsPerAttestationElectra(final SpecConfig specConfig) {
    return (long) specConfig.getMaxValidatorsPerCommittee() * specConfig.getMaxCommitteesPerSlot();
  }

  private static SchemaProvider<?> createBuilderPendingPaymentSchemaProvider() {
    return providerBuilder(BUILDER_PENDING_PAYMENT_SCHEMA)
        .withCreator(
            GLOAS, (registry, specConfig, schemaName) -> new BuilderPendingPaymentSchema(registry))
        .build();
  }

  private static SchemaProvider<?> createBuilderPendingWithdrawalSchemaProvider() {
    return providerBuilder(BUILDER_PENDING_WITHDRAWAL_SCHEMA)
        .withCreator(
            GLOAS, (registry, specConfig, schemaName) -> new BuilderPendingWithdrawalSchema())
        .build();
  }

  private static SchemaProvider<?> createPayloadAttestationDataSchemaProvider() {
    return providerBuilder(PAYLOAD_ATTESTATION_DATA_SCHEMA)
        .withCreator(
            GLOAS, (registry, specConfig, schemaName) -> new PayloadAttestationDataSchema())
        .build();
  }

  private static SchemaProvider<?> createPayloadAttestationSchemaProvider() {
    return providerBuilder(PAYLOAD_ATTESTATION_SCHEMA)
        .withCreator(
            GLOAS,
            (registry, specConfig, schemaName) ->
                new PayloadAttestationSchema(SpecConfigGloas.required(specConfig), registry))
        .build();
  }

  private static SchemaProvider<?> createPayloadAttestationMessageSchemaProvider() {
    return providerBuilder(PAYLOAD_ATTESTATION_MESSAGE_SCHEMA)
        .withCreator(
            GLOAS,
            (registry, specConfig, schemaName) -> new PayloadAttestationMessageSchema(registry))
        .build();
  }

  private static SchemaProvider<?> createIndexedPayloadAttestationSchemaProvider() {
    return providerBuilder(INDEXED_PAYLOAD_ATTESTATION_SCHEMA)
        .withCreator(
            GLOAS,
            (registry, specConfig, schemaName) ->
                new IndexedPayloadAttestationSchema(SpecConfigGloas.required(specConfig), registry))
        .build();
  }

  private static SchemaProvider<?> createExecutionPayloadBidSchemaProvider() {
    return providerBuilder(EXECUTION_PAYLOAD_BID_SCHEMA)
        .withCreator(GLOAS, (registry, specConfig, schemaName) -> new ExecutionPayloadBidSchema())
        .build();
  }

  private static SchemaProvider<?> createSignedExecutionPayloadBidSchemaProvider() {
    return providerBuilder(SIGNED_EXECUTION_PAYLOAD_BID_SCHEMA)
        .withCreator(
            GLOAS,
            (registry, specConfig, schemaName) -> new SignedExecutionPayloadBidSchema(registry))
        .build();
  }

  private static SchemaProvider<?> createExecutionPayloadEnvelopeSchemaProvider() {
    return providerBuilder(EXECUTION_PAYLOAD_ENVELOPE_SCHEMA)
        .withCreator(
            GLOAS,
            (registry, specConfig, schemaName) -> new ExecutionPayloadEnvelopeSchema(registry))
        .build();
  }

  private static SchemaProvider<?> createSignedExecutionPayloadEnvelopeSchemaProvider() {
    return providerBuilder(SIGNED_EXECUTION_PAYLOAD_ENVELOPE_SCHEMA)
        .withCreator(
            GLOAS,
            (registry, specConfig, schemaName) ->
                new SignedExecutionPayloadEnvelopeSchema(registry))
        .build();
  }

  private static SchemaProvider<?> createExecutionPayloadAvailabilitySchemaProvider() {
    return providerBuilder(EXECUTION_PAYLOAD_AVAILABILITY_SCHEMA)
        .withCreator(
            GLOAS,
            (registry, specConfig, schemaName) ->
                SszBitvectorSchema.create(specConfig.getSlotsPerHistoricalRoot()))
        .build();
  }

  private static SchemaProvider<?> createBuilderPendingPaymentsSchemaProvider() {
    return providerBuilder(BUILDER_PENDING_PAYMENTS_SCHEMA)
        .withCreator(
            GLOAS,
            (registry, specConfig, schemaName) ->
                SszVectorSchema.create(
                    registry.get(BUILDER_PENDING_PAYMENT_SCHEMA),
                    specConfig.getSlotsPerEpoch() * 2L))
        .build();
  }

  private static SchemaProvider<?> createBuilderPendingWithdrawalsSchemaProvider() {
    return providerBuilder(BUILDER_PENDING_WITHDRAWALS_SCHEMA)
        .withCreator(
            GLOAS,
            (registry, specConfig, schemaName) ->
                SszListSchema.create(
                    registry.get(BUILDER_PENDING_WITHDRAWAL_SCHEMA),
                    SpecConfigGloas.required(specConfig).getBuilderPendingWithdrawalsLimit()))
        .build();
  }

  private static SchemaProvider<?>
      createExecutionPayloadEnvelopesByRootRequestMessageSchemaProvider() {
    return providerBuilder(EXECUTION_PAYLOAD_ENVELOPES_BY_ROOT_REQUEST_MESSAGE_SCHEMA)
        .withCreator(
            GLOAS,
            (registry, specConfig, schemaName) ->
                new ExecutionPayloadEnvelopesByRootRequestMessageSchema(
                    SpecConfigGloas.required(specConfig)))
        .build();
  }

  public SchemaRegistryBuilder() {
    this.cache = SchemaCache.createDefault();
  }

  @VisibleForTesting
  SchemaRegistryBuilder(final SchemaCache cache) {
    this.cache = cache;
  }

  <T> SchemaRegistryBuilder addProvider(final SchemaProvider<T> provider) {
    if (!providers.add(provider)) {
      throw new IllegalArgumentException(
          "The provider " + provider.getClass().getSimpleName() + " has been already added");
    }
    if (!schemaIds.add(provider.getSchemaId())) {
      throw new IllegalStateException(
          "A previously added provider was already providing the schema for "
              + provider.getSchemaId());
    }
    return this;
  }

  @SuppressWarnings("EnumOrdinal")
  public synchronized SchemaRegistry build(
      final SpecMilestone milestone, final SpecConfig specConfig) {

    if (lastBuiltSchemaRegistryMilestone == null) {
      // we recursively build all previous milestones
      milestone
          .getPreviousMilestoneIfExists()
          .ifPresent(previousMilestone -> build(previousMilestone, specConfig));
    } else {
      checkArgument(
          lastBuiltSchemaRegistryMilestone.ordinal() == milestone.ordinal() - 1,
          "Build must follow the milestone ordering. Last built milestone: %s, requested milestone: %s",
          lastBuiltSchemaRegistryMilestone,
          milestone);
    }

    lastBuiltSchemaRegistryMilestone = milestone;

    final SchemaRegistry registry = new SchemaRegistry(milestone, specConfig, cache);

    for (final SchemaProvider<?> provider : providers) {
      if (provider.getSupportedMilestones().contains(milestone)) {
        registry.registerProvider(provider);
      }
    }

    registry.primeRegistry();

    return registry;
  }
}
