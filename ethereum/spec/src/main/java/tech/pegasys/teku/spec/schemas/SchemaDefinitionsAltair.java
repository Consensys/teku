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

import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.AGGREGATE_AND_PROOF_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.SIGNED_AGGREGATE_AND_PROOF_SCHEMA;

import com.google.common.base.Preconditions;
import java.util.Optional;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSchema;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainerSchema;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockSchema;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainerSchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodyBuilder;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.BeaconBlockBodyBuilderAltair;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.BeaconBlockBodySchemaAltairImpl;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientBootstrapSchema;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientHeaderSchema;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientUpdateResponseSchema;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientUpdateSchema;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.versions.altair.MetadataMessageSchemaAltair;
import tech.pegasys.teku.spec.datastructures.operations.AggregateAndProof.AggregateAndProofSchema;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationSchema;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashingSchema;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestationSchema;
import tech.pegasys.teku.spec.datastructures.operations.SignedAggregateAndProof.SignedAggregateAndProofSchema;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.ContributionAndProofSchema;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SignedContributionAndProofSchema;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncAggregatorSelectionDataSchema;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeContributionSchema;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeMessageSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateAltair;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateSchemaAltair;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.MutableBeaconStateAltair;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;
import tech.pegasys.teku.spec.schemas.registry.SchemaTypes;

public class SchemaDefinitionsAltair extends AbstractSchemaDefinitions {
  private final IndexedAttestationSchema indexedAttestationSchema;
  private final AttesterSlashingSchema attesterSlashingSchema;
  private final AttestationSchema<Attestation> attestationSchema;
  private final SignedAggregateAndProofSchema signedAggregateAndProofSchema;
  private final AggregateAndProofSchema aggregateAndProofSchema;
  private final BeaconStateSchemaAltair beaconStateSchema;
  private final BeaconBlockBodySchemaAltairImpl beaconBlockBodySchema;
  private final BeaconBlockSchema beaconBlockSchema;
  private final SignedBeaconBlockSchema signedBeaconBlockSchema;
  private final SyncCommitteeContributionSchema syncCommitteeContributionSchema;
  private final ContributionAndProofSchema contributionAndProofSchema;
  private final SignedContributionAndProofSchema signedContributionAndProofSchema;
  private final MetadataMessageSchemaAltair metadataMessageSchema;
  private final LightClientHeaderSchema lightClientHeaderSchema;
  private final LightClientBootstrapSchema lightClientBootstrapSchema;
  private final LightClientUpdateSchema lightClientUpdateSchema;
  private final LightClientUpdateResponseSchema lightClientUpdateResponseSchema;

  public SchemaDefinitionsAltair(final SchemaRegistry schemaRegistry) {
    super(schemaRegistry);
    final SpecConfigAltair specConfig = SpecConfigAltair.required(schemaRegistry.getSpecConfig());
    this.indexedAttestationSchema = schemaRegistry.get(SchemaTypes.INDEXED_ATTESTATION_SCHEMA);
    this.attesterSlashingSchema = schemaRegistry.get(SchemaTypes.ATTESTER_SLASHING_SCHEMA);
    this.attestationSchema = schemaRegistry.get(SchemaTypes.ATTESTATION_SCHEMA);
    this.aggregateAndProofSchema = schemaRegistry.get(AGGREGATE_AND_PROOF_SCHEMA);
    this.signedAggregateAndProofSchema = schemaRegistry.get(SIGNED_AGGREGATE_AND_PROOF_SCHEMA);
    this.beaconStateSchema = BeaconStateSchemaAltair.create(specConfig);
    this.beaconBlockBodySchema =
        BeaconBlockBodySchemaAltairImpl.create(
            specConfig,
            getMaxValidatorsPerAttestation(specConfig),
            "BeaconBlockBodyAltair",
            schemaRegistry);
    this.beaconBlockSchema = new BeaconBlockSchema(beaconBlockBodySchema, "BeaconBlockAltair");
    this.signedBeaconBlockSchema =
        new SignedBeaconBlockSchema(beaconBlockSchema, "SignedBeaconBlockAltair");
    this.syncCommitteeContributionSchema = SyncCommitteeContributionSchema.create(specConfig);
    this.contributionAndProofSchema =
        ContributionAndProofSchema.create(syncCommitteeContributionSchema);
    this.signedContributionAndProofSchema =
        SignedContributionAndProofSchema.create(contributionAndProofSchema);
    this.metadataMessageSchema = new MetadataMessageSchemaAltair(specConfig.getNetworkingConfig());
    this.lightClientHeaderSchema = new LightClientHeaderSchema();
    this.lightClientBootstrapSchema = new LightClientBootstrapSchema(specConfig);
    this.lightClientUpdateSchema = new LightClientUpdateSchema(specConfig);
    this.lightClientUpdateResponseSchema = new LightClientUpdateResponseSchema(specConfig);
  }

  public static SchemaDefinitionsAltair required(final SchemaDefinitions schemaDefinitions) {
    Preconditions.checkArgument(
        schemaDefinitions instanceof SchemaDefinitionsAltair,
        "Expected definitions of type %s but got %s",
        SchemaDefinitionsAltair.class,
        schemaDefinitions.getClass());
    return (SchemaDefinitionsAltair) schemaDefinitions;
  }

  @Override
  public SignedAggregateAndProofSchema getSignedAggregateAndProofSchema() {
    return signedAggregateAndProofSchema;
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
  public IndexedAttestationSchema getIndexedAttestationSchema() {
    return indexedAttestationSchema;
  }

  @Override
  public AttesterSlashingSchema getAttesterSlashingSchema() {
    return attesterSlashingSchema;
  }

  @Override
  public BeaconStateSchema<? extends BeaconStateAltair, ? extends MutableBeaconStateAltair>
      getBeaconStateSchema() {
    return beaconStateSchema;
  }

  @Override
  public SignedBeaconBlockSchema getSignedBeaconBlockSchema() {
    return signedBeaconBlockSchema;
  }

  @Override
  public BeaconBlockSchema getBeaconBlockSchema() {
    return beaconBlockSchema;
  }

  @Override
  public BeaconBlockBodySchema<?> getBeaconBlockBodySchema() {
    return beaconBlockBodySchema;
  }

  @Override
  public BeaconBlockSchema getBlindedBeaconBlockSchema() {
    return getBeaconBlockSchema();
  }

  @Override
  public BeaconBlockBodySchema<?> getBlindedBeaconBlockBodySchema() {
    return getBeaconBlockBodySchema();
  }

  @Override
  public SignedBeaconBlockSchema getSignedBlindedBeaconBlockSchema() {
    return getSignedBeaconBlockSchema();
  }

  @Override
  public BlockContainerSchema<BlockContainer> getBlockContainerSchema() {
    return getBeaconBlockSchema().castTypeToBlockContainer();
  }

  @Override
  public BlockContainerSchema<BlockContainer> getBlindedBlockContainerSchema() {
    return getBeaconBlockSchema().castTypeToBlockContainer();
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
  public BeaconBlockBodyBuilder createBeaconBlockBodyBuilder() {
    return new BeaconBlockBodyBuilderAltair(beaconBlockBodySchema);
  }

  @Override
  public MetadataMessageSchemaAltair getMetadataMessageSchema() {
    return metadataMessageSchema;
  }

  @Override
  public Optional<SchemaDefinitionsAltair> toVersionAltair() {
    return Optional.of(this);
  }

  public SyncCommitteeContributionSchema getSyncCommitteeContributionSchema() {
    return syncCommitteeContributionSchema;
  }

  public ContributionAndProofSchema getContributionAndProofSchema() {
    return contributionAndProofSchema;
  }

  public SignedContributionAndProofSchema getSignedContributionAndProofSchema() {
    return signedContributionAndProofSchema;
  }

  public SyncCommitteeMessageSchema getSyncCommitteeMessageSchema() {
    return SyncCommitteeMessageSchema.INSTANCE;
  }

  public SyncAggregatorSelectionDataSchema getSyncAggregatorSelectionDataSchema() {
    return SyncAggregatorSelectionDataSchema.INSTANCE;
  }

  public LightClientHeaderSchema getLightClientHeaderSchema() {
    return lightClientHeaderSchema;
  }

  public LightClientBootstrapSchema getLightClientBootstrapSchema() {
    return lightClientBootstrapSchema;
  }

  public LightClientUpdateSchema getLightClientUpdateSchema() {
    return lightClientUpdateSchema;
  }

  public LightClientUpdateResponseSchema getLightClientUpdateResponseSchema() {
    return lightClientUpdateResponseSchema;
  }

  @Override
  long getMaxValidatorsPerAttestation(final SpecConfig specConfig) {
    return specConfig.getMaxValidatorsPerCommittee();
  }
}
