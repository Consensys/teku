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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.electra;

import static tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszContainerSchema.namedSchema;
import static tech.pegasys.teku.spec.datastructures.StableContainerCapacities.MAX_BEACON_BLOCK_BODY_FIELDS;

import it.unimi.dsi.fastutil.longs.LongList;
import java.util.function.Function;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.containers.ProfileSchema12;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszContainerSchema.NamedSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobKzgCommitmentsSchema;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodyBuilder;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.common.BlockBodyFields;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregateSchema;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionPayloadElectraImpl;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionPayloadSchemaElectra;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashingSchema;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChangeSchema;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.operations.versions.electra.AttestationElectraSchema;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.spec.datastructures.type.SszSignatureSchema;

public class BeaconBlockBodySchemaElectraImpl
    extends ProfileSchema12<
        BeaconBlockBodyElectraImpl,
        SszSignature,
        Eth1Data,
        SszBytes32,
        SszList<ProposerSlashing>,
        SszList<AttesterSlashing>,
        SszList<Attestation>,
        SszList<Deposit>,
        SszList<SignedVoluntaryExit>,
        SyncAggregate,
        ExecutionPayloadElectraImpl,
        SszList<SignedBlsToExecutionChange>,
        SszList<SszKZGCommitment>>
    implements BeaconBlockBodySchemaElectra<BeaconBlockBodyElectraImpl> {

  protected BeaconBlockBodySchemaElectraImpl(
      final String containerName,
      final NamedSchema<SszSignature> randaoRevealSchema,
      final NamedSchema<Eth1Data> eth1DataSchema,
      final NamedSchema<SszBytes32> graffitiSchema,
      final NamedSchema<SszList<ProposerSlashing>> proposerSlashingsSchema,
      final NamedSchema<SszList<AttesterSlashing>> attesterSlashingsSchema,
      final NamedSchema<SszList<Attestation>> attestationsSchema,
      final NamedSchema<SszList<Deposit>> depositsSchema,
      final NamedSchema<SszList<SignedVoluntaryExit>> voluntaryExitsSchema,
      final NamedSchema<SyncAggregate> syncAggregateSchema,
      final NamedSchema<ExecutionPayloadElectraImpl> executionPayloadSchema,
      final NamedSchema<SszList<SignedBlsToExecutionChange>> blsToExecutionChange,
      final NamedSchema<SszList<SszKZGCommitment>> blobKzgCommitments) {
    super(
        containerName,
        randaoRevealSchema,
        eth1DataSchema,
        graffitiSchema,
        proposerSlashingsSchema,
        attesterSlashingsSchema,
        attestationsSchema,
        depositsSchema,
        voluntaryExitsSchema,
        syncAggregateSchema,
        executionPayloadSchema,
        blsToExecutionChange,
        blobKzgCommitments,
        MAX_BEACON_BLOCK_BODY_FIELDS);
  }

  public static BeaconBlockBodySchemaElectraImpl create(
      final SpecConfigElectra specConfig,
      final AttesterSlashingSchema<?> attesterSlashingSchema,
      final SignedBlsToExecutionChangeSchema blsToExecutionChangeSchema,
      final BlobKzgCommitmentsSchema blobKzgCommitmentsSchema,
      final long maxValidatorsPerAttestation,
      final String containerName) {
    return new BeaconBlockBodySchemaElectraImpl(
        containerName,
        namedSchema(BlockBodyFields.RANDAO_REVEAL, SszSignatureSchema.INSTANCE),
        namedSchema(BlockBodyFields.ETH1_DATA, Eth1Data.SSZ_SCHEMA),
        namedSchema(BlockBodyFields.GRAFFITI, SszPrimitiveSchemas.BYTES32_SCHEMA),
        namedSchema(
            BlockBodyFields.PROPOSER_SLASHINGS,
            SszListSchema.create(
                ProposerSlashing.SSZ_SCHEMA, specConfig.getMaxProposerSlashings())),
        namedSchema(
            BlockBodyFields.ATTESTER_SLASHINGS,
            SszListSchema.create(
                attesterSlashingSchema.castTypeToAttesterSlashingSchema(),
                specConfig.getMaxAttesterSlashingsElectra())),
        namedSchema(
            BlockBodyFields.ATTESTATIONS,
            SszListSchema.create(
                new AttestationElectraSchema(
                        maxValidatorsPerAttestation, specConfig.getMaxCommitteesPerSlot())
                    .castTypeToAttestationSchema(),
                specConfig.getMaxAttestationsElectra())),
        namedSchema(
            BlockBodyFields.DEPOSITS,
            SszListSchema.create(Deposit.SSZ_SCHEMA, specConfig.getMaxDeposits())),
        namedSchema(
            BlockBodyFields.VOLUNTARY_EXITS,
            SszListSchema.create(
                SignedVoluntaryExit.SSZ_SCHEMA, specConfig.getMaxVoluntaryExits())),
        namedSchema(
            BlockBodyFields.SYNC_AGGREGATE,
            SyncAggregateSchema.create(specConfig.getSyncCommitteeSize())),
        namedSchema(
            BlockBodyFields.EXECUTION_PAYLOAD, new ExecutionPayloadSchemaElectra(specConfig)),
        namedSchema(
            BlockBodyFields.BLS_TO_EXECUTION_CHANGES,
            SszListSchema.create(
                blsToExecutionChangeSchema, specConfig.getMaxBlsToExecutionChanges())),
        namedSchema(BlockBodyFields.BLOB_KZG_COMMITMENTS, blobKzgCommitmentsSchema));
  }

  @Override
  public SafeFuture<? extends BeaconBlockBody> createBlockBody(
      final Function<BeaconBlockBodyBuilder, SafeFuture<Void>> bodyBuilder) {
    final BeaconBlockBodyBuilderElectra builder = new BeaconBlockBodyBuilderElectra(this, null);
    return bodyBuilder.apply(builder).thenApply(__ -> builder.build());
  }

  @Override
  public BeaconBlockBody createEmpty() {
    return new BeaconBlockBodyElectraImpl(this);
  }

  @SuppressWarnings("unchecked")
  @Override
  public SszListSchema<ProposerSlashing, ?> getProposerSlashingsSchema() {
    return (SszListSchema<ProposerSlashing, ?>)
        getChildSchema(getFieldIndex(BlockBodyFields.PROPOSER_SLASHINGS));
  }

  @SuppressWarnings("unchecked")
  @Override
  public SszListSchema<AttesterSlashing, ?> getAttesterSlashingsSchema() {
    return (SszListSchema<AttesterSlashing, ?>)
        getChildSchema(getFieldIndex(BlockBodyFields.ATTESTER_SLASHINGS));
  }

  @SuppressWarnings("unchecked")
  @Override
  public SszListSchema<Attestation, ?> getAttestationsSchema() {
    return (SszListSchema<Attestation, ?>)
        getChildSchema(getFieldIndex(BlockBodyFields.ATTESTATIONS));
  }

  @SuppressWarnings("unchecked")
  @Override
  public SszListSchema<Deposit, ?> getDepositsSchema() {
    return (SszListSchema<Deposit, ?>) getChildSchema(getFieldIndex(BlockBodyFields.DEPOSITS));
  }

  @SuppressWarnings("unchecked")
  @Override
  public SszListSchema<SignedVoluntaryExit, ?> getVoluntaryExitsSchema() {
    return (SszListSchema<SignedVoluntaryExit, ?>)
        getChildSchema(getFieldIndex(BlockBodyFields.VOLUNTARY_EXITS));
  }

  @Override
  public SyncAggregateSchema getSyncAggregateSchema() {
    return (SyncAggregateSchema) getChildSchema(getFieldIndex(BlockBodyFields.SYNC_AGGREGATE));
  }

  @Override
  public BeaconBlockBodyElectraImpl createFromBackingNode(final TreeNode node) {
    return new BeaconBlockBodyElectraImpl(this, node);
  }

  @Override
  public ExecutionPayloadSchema<?> getExecutionPayloadSchema() {
    return (ExecutionPayloadSchema<?>)
        getChildSchema(getFieldIndex(BlockBodyFields.EXECUTION_PAYLOAD));
  }

  @SuppressWarnings("unchecked")
  @Override
  public SszListSchema<SignedBlsToExecutionChange, ?> getBlsToExecutionChangesSchema() {
    return (SszListSchema<SignedBlsToExecutionChange, ?>)
        getChildSchema(getFieldIndex(BlockBodyFields.BLS_TO_EXECUTION_CHANGES));
  }

  @SuppressWarnings("unchecked")
  @Override
  public SszListSchema<SszKZGCommitment, ?> getBlobKzgCommitmentsSchema() {
    return (SszListSchema<SszKZGCommitment, ?>)
        getChildSchema(getFieldIndex(BlockBodyFields.BLOB_KZG_COMMITMENTS));
  }

  @Override
  public long getBlobKzgCommitmentsGeneralizedIndex() {
    return getChildGeneralizedIndex(getFieldIndex(BlockBodyFields.BLOB_KZG_COMMITMENTS));
  }

  @Override
  public LongList getBlindedNodeGeneralizedIndices() {
    return GIndexUtil.gIdxComposeAll(
        getChildGeneralizedIndex(getFieldIndex(BlockBodyFields.EXECUTION_PAYLOAD)),
        getExecutionPayloadSchema().getBlindedNodeGeneralizedIndices());
  }
}
