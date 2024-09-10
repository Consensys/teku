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

import static tech.pegasys.teku.spec.schemas.SchemaTypes.AGGREGATE_AND_PROOF_SCHEMA;
import static tech.pegasys.teku.spec.schemas.SchemaTypes.ATTESTATION_SCHEMA;
import static tech.pegasys.teku.spec.schemas.SchemaTypes.ATTESTER_SLASHING_SCHEMA;
import static tech.pegasys.teku.spec.schemas.SchemaTypes.BEACON_BLOCK_BODY_SCHEMA;
import static tech.pegasys.teku.spec.schemas.SchemaTypes.BEACON_BLOCK_SCHEMA;
import static tech.pegasys.teku.spec.schemas.SchemaTypes.BEACON_STATE_SCHEMA;
import static tech.pegasys.teku.spec.schemas.SchemaTypes.INDEXED_ATTESTATION_SCHEMA;
import static tech.pegasys.teku.spec.schemas.SchemaTypes.METADATA_MESSAGE_SCHEMA;
import static tech.pegasys.teku.spec.schemas.SchemaTypes.SIGNED_AGGREGATE_AND_PROOF_SCHEMA;
import static tech.pegasys.teku.spec.schemas.SchemaTypes.SIGNED_BEACON_BLOCK_SCHEMA;

import java.util.Optional;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSchema;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainerSchema;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockSchema;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainerSchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodyBuilder;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.phase0.BeaconBlockBodyBuilderPhase0;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.MetadataMessageSchema;
import tech.pegasys.teku.spec.datastructures.operations.AggregateAndProof.AggregateAndProofSchema;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationSchema;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashingSchema;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestationSchema;
import tech.pegasys.teku.spec.datastructures.operations.SignedAggregateAndProof.SignedAggregateAndProofSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateSchema;

public class SchemaDefinitionsPhase0 extends AbstractSchemaDefinitions {

  public SchemaDefinitionsPhase0(final SchemaRegistry schemaRegistry) {
    super(schemaRegistry);
  }

  @Override
  public SignedAggregateAndProofSchema getSignedAggregateAndProofSchema() {
    return schemaRegistry.get(SIGNED_AGGREGATE_AND_PROOF_SCHEMA);
  }

  @Override
  public AggregateAndProofSchema getAggregateAndProofSchema() {
    return schemaRegistry.get(AGGREGATE_AND_PROOF_SCHEMA);
  }

  @Override
  public AttestationSchema<Attestation> getAttestationSchema() {
    return schemaRegistry.get(ATTESTATION_SCHEMA);
  }

  @Override
  public IndexedAttestationSchema<IndexedAttestation> getIndexedAttestationSchema() {
    return schemaRegistry.get(INDEXED_ATTESTATION_SCHEMA);
  }

  @Override
  public AttesterSlashingSchema<AttesterSlashing> getAttesterSlashingSchema() {
    return schemaRegistry.get(ATTESTER_SLASHING_SCHEMA);
  }

  @Override
  public BeaconStateSchema<?, ?> getBeaconStateSchema() {
    return schemaRegistry.get(BEACON_STATE_SCHEMA);
  }

  @Override
  public SignedBeaconBlockSchema getSignedBeaconBlockSchema() {
    return schemaRegistry.get(SIGNED_BEACON_BLOCK_SCHEMA);
  }

  @Override
  public BeaconBlockSchema getBeaconBlockSchema() {
    return schemaRegistry.get(BEACON_BLOCK_SCHEMA);
  }

  @Override
  public BeaconBlockBodySchema<?> getBeaconBlockBodySchema() {
    return schemaRegistry.get(BEACON_BLOCK_BODY_SCHEMA);
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
    return new BeaconBlockBodyBuilderPhase0(getBeaconBlockBodySchema());
  }

  @Override
  public MetadataMessageSchema<?> getMetadataMessageSchema() {
    return schemaRegistry.get(METADATA_MESSAGE_SCHEMA);
  }

  @Override
  public Optional<SchemaDefinitionsAltair> toVersionAltair() {
    return Optional.empty();
  }
}
