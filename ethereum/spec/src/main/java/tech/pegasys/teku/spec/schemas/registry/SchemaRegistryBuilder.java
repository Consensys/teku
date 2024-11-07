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

package tech.pegasys.teku.spec.schemas.registry;

import static tech.pegasys.teku.spec.SpecMilestone.CAPELLA;
import static tech.pegasys.teku.spec.SpecMilestone.DENEB;
import static tech.pegasys.teku.spec.SpecMilestone.ELECTRA;
import static tech.pegasys.teku.spec.SpecMilestone.PHASE0;
import static tech.pegasys.teku.spec.schemas.registry.BaseSchemaProvider.constantProviderBuilder;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.AGGREGATE_AND_PROOF_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.ATTESTATION_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.ATTESTER_SLASHING_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.ATTNETS_ENR_FIELD_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BEACON_BLOCKS_BY_ROOT_REQUEST_MESSAGE_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BLOBS_BUNDLE_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BLOBS_IN_BLOCK_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BLOB_KZG_COMMITMENTS_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BLOB_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BLOB_SIDECARS_BY_ROOT_REQUEST_MESSAGE_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BLOB_SIDECAR_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BLS_TO_EXECUTION_CHANGE_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.HISTORICAL_BATCH_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.HISTORICAL_SUMMARY_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.INDEXED_ATTESTATION_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.SIGNED_AGGREGATE_AND_PROOF_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.SIGNED_BLS_TO_EXECUTION_CHANGE_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.SYNCNETS_ENR_FIELD_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.WITHDRAWAL_SCHEMA;

import com.google.common.annotations.VisibleForTesting;
import java.util.HashSet;
import java.util.Set;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.constants.NetworkConstants;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobKzgCommitmentsSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecarSchema;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.builder.BlobsBundleSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.WithdrawalSchema;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BeaconBlocksByRootRequestMessage.BeaconBlocksByRootRequestMessageSchema;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobSidecarsByRootRequestMessageSchema;
import tech.pegasys.teku.spec.datastructures.operations.AggregateAndProof.AggregateAndProofSchema;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashingSchema;
import tech.pegasys.teku.spec.datastructures.operations.BlsToExecutionChangeSchema;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestationSchema;
import tech.pegasys.teku.spec.datastructures.operations.SignedAggregateAndProof.SignedAggregateAndProofSchema;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChangeSchema;
import tech.pegasys.teku.spec.datastructures.operations.versions.electra.AttestationElectraSchema;
import tech.pegasys.teku.spec.datastructures.operations.versions.phase0.AttestationPhase0Schema;
import tech.pegasys.teku.spec.datastructures.state.HistoricalBatch.HistoricalBatchSchema;
import tech.pegasys.teku.spec.datastructures.state.versions.capella.HistoricalSummary.HistoricalSummarySchema;
import tech.pegasys.teku.spec.schemas.registry.SchemaTypes.SchemaId;

public class SchemaRegistryBuilder {
  private final Set<SchemaProvider<?>> providers = new HashSet<>();
  private final Set<SchemaId<?>> schemaIds = new HashSet<>();
  private final SchemaCache cache;

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

        // CAPELLA
        .addProvider(createWithdrawalSchemaProvider())
        .addProvider(createBlsToExecutionChangeSchemaProvider())
        .addProvider(createSignedBlsToExecutionChangeSchemaProvider())
        .addProvider(createHistoricalSummarySchemaProvider())

        // DENEB
        .addProvider(createBlobKzgCommitmentsSchemaProvider())
        .addProvider(createBlobSchemaProvider())
        .addProvider(createBlobsInBlockSchemaProvider())
        .addProvider(createBlobSidecarSchemaProvider())
        .addProvider(createBlobSidecarsByRootRequestMessageSchemaProvider())
        .addProvider(createBlobsBundleSchemaProvider());
  }

  private static SchemaProvider<?> createBlobsBundleSchemaProvider() {
    // we can keep this to be constant because the blob list max length is
    // getMaxBlobCommitmentsPerBlock
    return constantProviderBuilder(BLOBS_BUNDLE_SCHEMA)
        .withCreator(
            DENEB,
            (registry, specConfig) ->
                new BlobsBundleSchema(registry, SpecConfigDeneb.required(specConfig)))
        .build();
  }

  private static SchemaProvider<?> createBlobKzgCommitmentsSchemaProvider() {
    // we can keep this to be constant because the kzg commitment list max length is
    // getMaxBlobCommitmentsPerBlock
    return constantProviderBuilder(BLOB_KZG_COMMITMENTS_SCHEMA)
        .withCreator(
            DENEB,
            (registry, specConfig) ->
                new BlobKzgCommitmentsSchema(SpecConfigDeneb.required(specConfig)))
        .build();
  }

  private static SchemaProvider<?> createBlobSchemaProvider() {
    return constantProviderBuilder(BLOB_SCHEMA)
        .withCreator(
            DENEB, (registry, specConfig) -> new BlobSchema(SpecConfigDeneb.required(specConfig)))
        .build();
  }

  private static SchemaProvider<?> createBlobsInBlockSchemaProvider() {
    return constantProviderBuilder(BLOBS_IN_BLOCK_SCHEMA)
        .withCreator(
            DENEB,
            (registry, specConfig) ->
                SszListSchema.create(
                    registry.get(BLOB_SCHEMA),
                    SpecConfigDeneb.required(specConfig).getMaxBlobsPerBlock()))
        .build();
  }

  private static SchemaProvider<?> createBlobSidecarSchemaProvider() {
    return constantProviderBuilder(BLOB_SIDECAR_SCHEMA)
        .withCreator(
            DENEB,
            (registry, specConfig) ->
                BlobSidecarSchema.create(
                    SignedBeaconBlockHeader.SSZ_SCHEMA,
                    registry.get(BLOB_SCHEMA),
                    SpecConfigDeneb.required(specConfig).getKzgCommitmentInclusionProofDepth()))
        .build();
  }

  private static SchemaProvider<?> createBlobSidecarsByRootRequestMessageSchemaProvider() {
    return constantProviderBuilder(BLOB_SIDECARS_BY_ROOT_REQUEST_MESSAGE_SCHEMA)
        .withCreator(
            DENEB,
            (registry, specConfig) ->
                new BlobSidecarsByRootRequestMessageSchema(SpecConfigDeneb.required(specConfig)))
        .build();
  }

  private static SchemaProvider<?> createHistoricalSummarySchemaProvider() {
    return constantProviderBuilder(HISTORICAL_SUMMARY_SCHEMA)
        .withCreator(CAPELLA, (registry, specConfig) -> new HistoricalSummarySchema())
        .build();
  }

  private static SchemaProvider<?> createSignedBlsToExecutionChangeSchemaProvider() {
    return constantProviderBuilder(SIGNED_BLS_TO_EXECUTION_CHANGE_SCHEMA)
        .withCreator(
            CAPELLA, (registry, specConfig) -> new SignedBlsToExecutionChangeSchema(registry))
        .build();
  }

  private static SchemaProvider<?> createBlsToExecutionChangeSchemaProvider() {
    return constantProviderBuilder(BLS_TO_EXECUTION_CHANGE_SCHEMA)
        .withCreator(CAPELLA, (registry, specConfig) -> new BlsToExecutionChangeSchema())
        .build();
  }

  private static SchemaProvider<?> createWithdrawalSchemaProvider() {
    return constantProviderBuilder(WITHDRAWAL_SCHEMA)
        .withCreator(CAPELLA, (registry, specConfig) -> new WithdrawalSchema())
        .build();
  }

  private static SchemaProvider<?> createAttnetsENRFieldSchemaProvider() {
    return constantProviderBuilder(ATTNETS_ENR_FIELD_SCHEMA)
        .withCreator(
            PHASE0,
            (registry, specConfig) ->
                SszBitvectorSchema.create(specConfig.getAttestationSubnetCount()))
        .build();
  }

  private static SchemaProvider<?> createSyncnetsENRFieldSchemaProvider() {
    return constantProviderBuilder(SYNCNETS_ENR_FIELD_SCHEMA)
        .withCreator(
            PHASE0,
            (registry, specConfig) ->
                SszBitvectorSchema.create(NetworkConstants.SYNC_COMMITTEE_SUBNET_COUNT))
        .build();
  }

  private static SchemaProvider<?> createBeaconBlocksByRootRequestMessageSchemaProvider() {
    return constantProviderBuilder(BEACON_BLOCKS_BY_ROOT_REQUEST_MESSAGE_SCHEMA)
        .withCreator(
            PHASE0,
            (registry, specConfig) -> new BeaconBlocksByRootRequestMessageSchema(specConfig))
        .build();
  }

  private static SchemaProvider<?> createHistoricalBatchSchemaProvider() {
    return constantProviderBuilder(HISTORICAL_BATCH_SCHEMA)
        .withCreator(
            PHASE0,
            (registry, specConfig) ->
                new HistoricalBatchSchema(specConfig.getSlotsPerHistoricalRoot()))
        .build();
  }

  private static SchemaProvider<?> createAttesterSlashingSchemaProvider() {
    return constantProviderBuilder(ATTESTER_SLASHING_SCHEMA)
        .withCreator(
            PHASE0,
            (registry, specConfig) ->
                new AttesterSlashingSchema(
                    ATTESTER_SLASHING_SCHEMA.getContainerName(registry), registry))
        .withCreator(
            ELECTRA,
            (registry, specConfig) ->
                new AttesterSlashingSchema(
                    ATTESTER_SLASHING_SCHEMA.getContainerName(registry), registry))
        .build();
  }

  private static SchemaProvider<?> createIndexedAttestationSchemaProvider() {
    return constantProviderBuilder(INDEXED_ATTESTATION_SCHEMA)
        .withCreator(
            PHASE0,
            (registry, specConfig) ->
                new IndexedAttestationSchema(
                    INDEXED_ATTESTATION_SCHEMA.getContainerName(registry),
                    getMaxValidatorsPerAttestationPhase0(specConfig)))
        .withCreator(
            ELECTRA,
            (registry, specConfig) ->
                new IndexedAttestationSchema(
                    INDEXED_ATTESTATION_SCHEMA.getContainerName(registry),
                    getMaxValidatorsPerAttestationElectra(specConfig)))
        .build();
  }

  private static SchemaProvider<?> createAttestationSchemaProvider() {
    return constantProviderBuilder(ATTESTATION_SCHEMA)
        .withCreator(
            PHASE0,
            (registry, specConfig) ->
                new AttestationPhase0Schema(getMaxValidatorsPerAttestationPhase0(specConfig))
                    .castTypeToAttestationSchema())
        .withCreator(
            ELECTRA,
            (registry, specConfig) ->
                new AttestationElectraSchema(
                        getMaxValidatorsPerAttestationElectra(specConfig),
                        specConfig.getMaxCommitteesPerSlot())
                    .castTypeToAttestationSchema())
        .build();
  }

  private static SchemaProvider<?> createAggregateAndProofSchemaProvider() {
    return constantProviderBuilder(AGGREGATE_AND_PROOF_SCHEMA)
        .withCreator(
            PHASE0,
            (registry, specConfig) ->
                new AggregateAndProofSchema(
                    AGGREGATE_AND_PROOF_SCHEMA.getContainerName(registry), registry))
        .withCreator(
            ELECTRA,
            (registry, specConfig) ->
                new AggregateAndProofSchema(
                    AGGREGATE_AND_PROOF_SCHEMA.getContainerName(registry), registry))
        .build();
  }

  private static SchemaProvider<?> createSignedAggregateAndProofSchemaProvider() {
    return constantProviderBuilder(SIGNED_AGGREGATE_AND_PROOF_SCHEMA)
        .withCreator(
            PHASE0,
            (registry, specConfig) ->
                new SignedAggregateAndProofSchema(
                    SIGNED_AGGREGATE_AND_PROOF_SCHEMA.getContainerName(registry), registry))
        .withCreator(
            ELECTRA,
            (registry, specConfig) ->
                new SignedAggregateAndProofSchema(
                    SIGNED_AGGREGATE_AND_PROOF_SCHEMA.getContainerName(registry), registry))
        .build();
  }

  private static long getMaxValidatorsPerAttestationPhase0(final SpecConfig specConfig) {
    return specConfig.getMaxValidatorsPerCommittee();
  }

  private static long getMaxValidatorsPerAttestationElectra(final SpecConfig specConfig) {
    return (long) specConfig.getMaxValidatorsPerCommittee() * specConfig.getMaxCommitteesPerSlot();
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

  public synchronized SchemaRegistry build(
      final SpecMilestone milestone, final SpecConfig specConfig) {
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
