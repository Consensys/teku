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

import java.util.Optional;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSchema;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainerSchema;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockSchema;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainerSchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodyBuilder;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.phase0.BeaconBlockBodyBuilderPhase0;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.phase0.BeaconBlockBodySchemaPhase0;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.versions.phase0.MetadataMessageSchemaPhase0;
import tech.pegasys.teku.spec.datastructures.operations.AggregateAndProof.AggregateAndProofSchema;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationSchema;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashingSchema;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestationSchema;
import tech.pegasys.teku.spec.datastructures.operations.SignedAggregateAndProof.SignedAggregateAndProofSchema;
import tech.pegasys.teku.spec.datastructures.operations.versions.phase0.AttestationPhase0Schema;
import tech.pegasys.teku.spec.datastructures.operations.versions.phase0.AttesterSlashingPhase0Schema;
import tech.pegasys.teku.spec.datastructures.operations.versions.phase0.IndexedAttestationPhase0Schema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.phase0.BeaconStateSchemaPhase0;

public class SchemaDefinitionsPhase0 extends AbstractSchemaDefinitions {
  private final IndexedAttestationSchema<IndexedAttestation> indexedAttestationSchema;
  private final AttesterSlashingSchema<AttesterSlashing> attesterSlashingSchema;
  private final AttestationSchema<Attestation> attestationSchema;
  private final SignedAggregateAndProofSchema signedAggregateAndProofSchema;
  private final AggregateAndProofSchema aggregateAndProofSchema;
  private final BeaconStateSchemaPhase0 beaconStateSchema;
  private final BeaconBlockBodySchemaPhase0 beaconBlockBodySchema;
  private final MetadataMessageSchemaPhase0 metadataMessageSchema;
  private final BeaconBlockSchema beaconBlockSchema;
  private final SignedBeaconBlockSchema signedBeaconBlockSchema;

  public SchemaDefinitionsPhase0(final SchemaRegistry schemaRegistry) {
    super(schemaRegistry);
    final SpecConfig specConfig = schemaRegistry.getSpecConfig();
    this.indexedAttestationSchema =
        new IndexedAttestationPhase0Schema(getMaxValidatorPerAttestation(specConfig))
            .castTypeToIndexedAttestationSchema();
    this.attesterSlashingSchema =
        new AttesterSlashingPhase0Schema(indexedAttestationSchema)
            .castTypeToAttesterSlashingSchema();

    this.aggregateAndProofSchema =
        new AggregateAndProofSchema(
            new AttestationPhase0Schema(getMaxValidatorPerAttestation(specConfig)));
    this.attestationSchema = this.aggregateAndProofSchema.getAttestationSchema();
    this.signedAggregateAndProofSchema = new SignedAggregateAndProofSchema(aggregateAndProofSchema);
    this.beaconStateSchema = BeaconStateSchemaPhase0.create(specConfig);
    this.beaconBlockBodySchema =
        BeaconBlockBodySchemaPhase0.create(
            specConfig,
            getAttesterSlashingSchema(),
            getMaxValidatorPerAttestation(specConfig),
            "BeaconBlockBodyPhase0");
    this.metadataMessageSchema = new MetadataMessageSchemaPhase0(specConfig.getNetworkingConfig());
    beaconBlockSchema = new BeaconBlockSchema(beaconBlockBodySchema, "BeaconBlockPhase0");
    signedBeaconBlockSchema =
        new SignedBeaconBlockSchema(beaconBlockSchema, "SignedBeaconBlockPhase0");
  }

  @Override
  long getMaxValidatorPerAttestation(final SpecConfig specConfig) {
    return specConfig.getMaxValidatorsPerCommittee();
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
  public IndexedAttestationSchema<IndexedAttestation> getIndexedAttestationSchema() {
    return indexedAttestationSchema;
  }

  @Override
  public AttesterSlashingSchema<AttesterSlashing> getAttesterSlashingSchema() {
    return attesterSlashingSchema;
  }

  @Override
  public BeaconStateSchema<?, ?> getBeaconStateSchema() {
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
    return new BeaconBlockBodyBuilderPhase0(beaconBlockBodySchema);
  }

  @Override
  public MetadataMessageSchemaPhase0 getMetadataMessageSchema() {
    return metadataMessageSchema;
  }

  @Override
  public Optional<SchemaDefinitionsAltair> toVersionAltair() {
    return Optional.empty();
  }
}
