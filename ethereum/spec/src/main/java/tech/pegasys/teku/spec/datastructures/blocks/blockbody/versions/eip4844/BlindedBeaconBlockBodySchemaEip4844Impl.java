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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.eip4844;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import java.util.Optional;
import java.util.function.Consumer;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema12;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.config.SpecConfigEip4844;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodyBuilder;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.common.BlockBodyFields;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregateSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip4844.ExecutionPayloadHeaderEip4844Impl;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip4844.ExecutionPayloadHeaderSchemaEip4844;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.Attestation.AttestationSchema;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing.AttesterSlashingSchema;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChangeSchema;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitmentSchema;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.spec.datastructures.type.SszSignatureSchema;

public class BlindedBeaconBlockBodySchemaEip4844Impl
    extends ContainerSchema12<
        BlindedBeaconBlockBodyEip4844Impl,
        SszSignature,
        Eth1Data,
        SszBytes32,
        SszList<ProposerSlashing>,
        SszList<AttesterSlashing>,
        SszList<Attestation>,
        SszList<Deposit>,
        SszList<SignedVoluntaryExit>,
        SyncAggregate,
        ExecutionPayloadHeaderEip4844Impl,
        SszList<SignedBlsToExecutionChange>,
        SszList<SszKZGCommitment>>
    implements BlindedBeaconBlockBodySchemaEip4844<BlindedBeaconBlockBodyEip4844Impl> {

  private BlindedBeaconBlockBodySchemaEip4844Impl(
      final String containerName,
      final NamedSchema<SszSignature> randaoReveal,
      final NamedSchema<Eth1Data> eth1Data,
      final NamedSchema<SszBytes32> graffiti,
      final NamedSchema<SszList<ProposerSlashing>> proposerSlashings,
      final NamedSchema<SszList<AttesterSlashing>> attesterSlashings,
      final NamedSchema<SszList<Attestation>> attestations,
      final NamedSchema<SszList<Deposit>> deposits,
      final NamedSchema<SszList<SignedVoluntaryExit>> voluntaryExits,
      final NamedSchema<SyncAggregate> syncAggregate,
      final NamedSchema<ExecutionPayloadHeaderEip4844Impl> executionPayloadHeader,
      final NamedSchema<SszList<SignedBlsToExecutionChange>> blsToExecutionChanges,
      final NamedSchema<SszList<SszKZGCommitment>> blobKzgCommitments) {
    super(
        containerName,
        randaoReveal,
        eth1Data,
        graffiti,
        proposerSlashings,
        attesterSlashings,
        attestations,
        deposits,
        voluntaryExits,
        syncAggregate,
        executionPayloadHeader,
        blsToExecutionChanges,
        blobKzgCommitments);
  }

  public static BlindedBeaconBlockBodySchemaEip4844Impl create(
      final SpecConfigEip4844 specConfig,
      final AttesterSlashingSchema attesterSlashingSchema,
      final SignedBlsToExecutionChangeSchema signedBlsToExecutionChangeSchema,
      final String containerName) {
    return new BlindedBeaconBlockBodySchemaEip4844Impl(
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
            SszListSchema.create(attesterSlashingSchema, specConfig.getMaxAttesterSlashings())),
        namedSchema(
            BlockBodyFields.ATTESTATIONS,
            SszListSchema.create(
                new AttestationSchema(specConfig), specConfig.getMaxAttestations())),
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
            BlockBodyFields.EXECUTION_PAYLOAD_HEADER,
            new ExecutionPayloadHeaderSchemaEip4844(specConfig)),
        namedSchema(
            BlockBodyFields.BLS_TO_EXECUTION_CHANGES,
            SszListSchema.create(
                signedBlsToExecutionChangeSchema,
                specConfig.getMaxBlsToExecutionChanges().longValue())),
        namedSchema(
            BlockBodyFields.BLOB_KZG_COMMITMENTS,
            SszListSchema.create(
                SszKZGCommitmentSchema.INSTANCE, specConfig.getMaxBlobsPerBlock())));
  }

  @Override
  public SafeFuture<BeaconBlockBody> createBlockBody(
      final Consumer<BeaconBlockBodyBuilder> builderConsumer) {
    final BeaconBlockBodyBuilderEip4844 builder =
        new BeaconBlockBodyBuilderEip4844().blindedSchema(this);
    builderConsumer.accept(builder);
    return builder.build();
  }

  @Override
  public BlindedBeaconBlockBodyEip4844Impl createEmpty() {
    return new BlindedBeaconBlockBodyEip4844Impl(this);
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
  public BlindedBeaconBlockBodyEip4844Impl createFromBackingNode(final TreeNode node) {
    return new BlindedBeaconBlockBodyEip4844Impl(this, node);
  }

  @Override
  public ExecutionPayloadHeaderSchemaEip4844 getExecutionPayloadHeaderSchema() {
    return (ExecutionPayloadHeaderSchemaEip4844)
        getChildSchema(getFieldIndex(BlockBodyFields.EXECUTION_PAYLOAD_HEADER));
  }

  @SuppressWarnings("unchecked")
  @Override
  public SszListSchema<SignedBlsToExecutionChange, ?> getBlsToExecutionChanges() {
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
  public Optional<BlindedBeaconBlockBodySchemaEip4844<?>> toBlindedVersionEip4844() {
    return Optional.of(this);
  }

  @Override
  public LongList getBlindedNodeGeneralizedIndices() {
    final long childGeneralizedIndex =
        getChildGeneralizedIndex(getFieldIndex(BlockBodyFields.EXECUTION_PAYLOAD_HEADER));
    final LongList schemaGeneralizedIndices =
        getExecutionPayloadHeaderSchema().getBlindedNodeGeneralizedIndices();
    final LongList blindedNodeGeneralizedIndices =
        new LongArrayList(schemaGeneralizedIndices.size());
    for (long relativeIndex : schemaGeneralizedIndices) {
      blindedNodeGeneralizedIndices.add(
          GIndexUtil.gIdxCompose(childGeneralizedIndex, relativeIndex));
    }
    return blindedNodeGeneralizedIndices;
  }
}
