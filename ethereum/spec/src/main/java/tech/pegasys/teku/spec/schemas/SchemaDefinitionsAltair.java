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

package tech.pegasys.teku.spec.schemas;

import com.google.common.base.Preconditions;
import java.util.Optional;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSchema;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockSchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.BeaconBlockBodySchemaAltair;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.BeaconBlockBodySchemaAltairImpl;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientBootstrapSchema;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientUpdateSchema;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.versions.altair.MetadataMessageSchemaAltair;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.ContributionAndProofSchema;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SignedContributionAndProofSchema;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncAggregatorSelectionDataSchema;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeContributionSchema;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeMessageSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateAltair;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateSchemaAltair;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.MutableBeaconStateAltair;

public class SchemaDefinitionsAltair extends AbstractSchemaDefinitions {
  private final BeaconStateSchemaAltair beaconStateSchema;
  private final BeaconBlockBodySchemaAltair<?> beaconBlockBodySchema;
  private final BeaconBlockSchema beaconBlockSchema;
  private final SignedBeaconBlockSchema signedBeaconBlockSchema;
  private final SyncCommitteeContributionSchema syncCommitteeContributionSchema;
  private final ContributionAndProofSchema contributionAndProofSchema;
  private final SignedContributionAndProofSchema signedContributionAndProofSchema;
  private final MetadataMessageSchemaAltair metadataMessageSchema;
  private final LightClientBootstrapSchema lightClientBootstrapSchema;
  private final LightClientUpdateSchema lightClientUpdateSchema;

  public SchemaDefinitionsAltair(final SpecConfigAltair specConfig) {
    super(specConfig);
    this.beaconStateSchema = BeaconStateSchemaAltair.create(specConfig);
    this.beaconBlockBodySchema =
        BeaconBlockBodySchemaAltairImpl.create(
            specConfig, getAttesterSlashingSchema(), "BeaconBlockBodyAltair");
    this.beaconBlockSchema = new BeaconBlockSchema(beaconBlockBodySchema, "BeaconBlockAltair");
    this.signedBeaconBlockSchema =
        new SignedBeaconBlockSchema(beaconBlockSchema, "SignedBeaconBlockAltair");
    this.syncCommitteeContributionSchema = SyncCommitteeContributionSchema.create(specConfig);
    this.contributionAndProofSchema =
        ContributionAndProofSchema.create(syncCommitteeContributionSchema);
    this.signedContributionAndProofSchema =
        SignedContributionAndProofSchema.create(contributionAndProofSchema);
    this.metadataMessageSchema = new MetadataMessageSchemaAltair();
    this.lightClientBootstrapSchema = new LightClientBootstrapSchema(specConfig);
    this.lightClientUpdateSchema = new LightClientUpdateSchema(specConfig);
  }

  public static SchemaDefinitionsAltair required(final SchemaDefinitions schemaDefinitions) {
    Preconditions.checkArgument(
        schemaDefinitions instanceof SchemaDefinitionsAltair,
        "Expected definitions of type %s by got %s",
        SchemaDefinitionsAltair.class,
        schemaDefinitions.getClass());
    return (SchemaDefinitionsAltair) schemaDefinitions;
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

  public LightClientBootstrapSchema getLightClientBootstrapSchema() {
    return lightClientBootstrapSchema;
  }

  public LightClientUpdateSchema getLightClientUpdateSchema() {
    return lightClientUpdateSchema;
  }
}
