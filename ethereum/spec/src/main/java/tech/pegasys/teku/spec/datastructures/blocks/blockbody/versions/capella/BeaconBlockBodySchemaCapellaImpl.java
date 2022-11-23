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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.capella;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import java.util.Optional;
import java.util.function.Consumer;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema11;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.config.SpecConfigCapella;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodyBuilder;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.common.BlockBodyFields;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregateSchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.bellatrix.BeaconBlockBodySchemaBellatrix;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.ExecutionPayloadCapellaImpl;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.ExecutionPayloadSchemaCapella;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.Attestation.AttestationSchema;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing.AttesterSlashingSchema;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChangeSchema;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.spec.datastructures.type.SszSignatureSchema;

public class BeaconBlockBodySchemaCapellaImpl
    extends ContainerSchema11<
        BeaconBlockBodyCapellaImpl,
        SszSignature,
        Eth1Data,
        SszBytes32,
        SszList<ProposerSlashing>,
        SszList<AttesterSlashing>,
        SszList<Attestation>,
        SszList<Deposit>,
        SszList<SignedVoluntaryExit>,
        SyncAggregate,
        ExecutionPayloadCapellaImpl,
        SszList<SignedBlsToExecutionChange>>
    implements BeaconBlockBodySchemaCapella<BeaconBlockBodyCapellaImpl> {

  protected BeaconBlockBodySchemaCapellaImpl(
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
      final NamedSchema<ExecutionPayloadCapellaImpl> executionPayloadSchema,
      final NamedSchema<SszList<SignedBlsToExecutionChange>> blsToExecutionChange) {
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
        blsToExecutionChange);
  }

  public static BeaconBlockBodySchemaCapellaImpl create(
      final SpecConfigCapella specConfig,
      final AttesterSlashingSchema attesterSlashingSchema,
      final SignedBlsToExecutionChangeSchema blsToExecutionChangeSchema,
      final String containerName) {
    return new BeaconBlockBodySchemaCapellaImpl(
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
            BlockBodyFields.EXECUTION_PAYLOAD, new ExecutionPayloadSchemaCapella(specConfig)),
        namedSchema(
            BlockBodyFields.BLS_TO_EXECUTION_CHANGES,
            SszListSchema.create(
                blsToExecutionChangeSchema, specConfig.getMaxBlsToExecutionChanges().longValue())));
  }

  @Override
  public SafeFuture<? extends BeaconBlockBody> createBlockBody(
      final Consumer<BeaconBlockBodyBuilder> builderConsumer) {
    final BeaconBlockBodyBuilderCapella builder = new BeaconBlockBodyBuilderCapella().schema(this);
    builderConsumer.accept(builder);
    return builder.build();
  }

  @Override
  public BeaconBlockBody createEmpty() {
    return new BeaconBlockBodyCapellaImpl(this);
  }

  @SuppressWarnings("unchecked")
  @Override
  public SszListSchema<ProposerSlashing, ?> getProposerSlashingsSchema() {
    return (SszListSchema<ProposerSlashing, ?>) getFieldSchema3();
  }

  @SuppressWarnings("unchecked")
  @Override
  public SszListSchema<AttesterSlashing, ?> getAttesterSlashingsSchema() {
    return (SszListSchema<AttesterSlashing, ?>) getFieldSchema4();
  }

  @SuppressWarnings("unchecked")
  @Override
  public SszListSchema<Attestation, ?> getAttestationsSchema() {
    return (SszListSchema<Attestation, ?>) getFieldSchema5();
  }

  @SuppressWarnings("unchecked")
  @Override
  public SszListSchema<Deposit, ?> getDepositsSchema() {
    return (SszListSchema<Deposit, ?>) getFieldSchema6();
  }

  @SuppressWarnings("unchecked")
  @Override
  public SszListSchema<SignedVoluntaryExit, ?> getVoluntaryExitsSchema() {
    return (SszListSchema<SignedVoluntaryExit, ?>) getFieldSchema7();
  }

  @Override
  public SyncAggregateSchema getSyncAggregateSchema() {
    return (SyncAggregateSchema) getFieldSchema8();
  }

  @Override
  public BeaconBlockBodyCapellaImpl createFromBackingNode(TreeNode node) {
    return new BeaconBlockBodyCapellaImpl(this, node);
  }

  @Override
  public ExecutionPayloadSchema<?> getExecutionPayloadSchema() {
    return (ExecutionPayloadSchema<?>) getFieldSchema9();
  }

  @SuppressWarnings("unchecked")
  @Override
  public SszListSchema<SignedBlsToExecutionChange, ?> getBlsToExecutionChangesSchema() {
    return (SszListSchema<SignedBlsToExecutionChange, ?>) getFieldSchema10();
  }

  @Override
  public Optional<BeaconBlockBodySchemaBellatrix<?>> toVersionBellatrix() {
    return Optional.of(this);
  }

  @Override
  public LongList getBlindedNodeGeneralizedIndices() {
    final long childGeneralizedIndex =
        getChildGeneralizedIndex(getFieldIndex(BlockBodyFields.EXECUTION_PAYLOAD));
    final LongList schemaGeneralizedIndices =
        getExecutionPayloadSchema().getBlindedNodeGeneralizedIndices();
    final LongList blindedNodeGeneralizedIndices =
        new LongArrayList(schemaGeneralizedIndices.size());
    for (long relativeIndex : schemaGeneralizedIndices) {
      blindedNodeGeneralizedIndices.add(
          GIndexUtil.gIdxCompose(childGeneralizedIndex, relativeIndex));
    }
    return blindedNodeGeneralizedIndices;
  }
}
