/*
 * Copyright 2021 ConsenSys AG.
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
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockSchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.merge.BeaconBlockBodySchemaMerge;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.versions.merge.MetadataMessageSchemaMerge;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.merge.BeaconStateSchemaMerge;

public class SchemaDefinitionsMerge extends AbstractSchemaDefinitions {
  private final BeaconStateSchemaMerge beaconStateSchema;
  private final BeaconBlockBodySchemaMerge<?> beaconBlockBodySchema;
  private final BeaconBlockSchema beaconBlockSchema;
  private final SignedBeaconBlockSchema signedBeaconBlockSchema;
  private final MetadataMessageSchemaMerge metadataMessageSchema;

  public SchemaDefinitionsMerge(final SpecConfig specConfig) {
    this.beaconStateSchema = BeaconStateSchemaMerge.create(specConfig);
    this.beaconBlockBodySchema = BeaconBlockBodySchemaMerge.create(specConfig);
    this.beaconBlockSchema = new BeaconBlockSchema(beaconBlockBodySchema);
    this.signedBeaconBlockSchema = new SignedBeaconBlockSchema(beaconBlockSchema);
    this.metadataMessageSchema = new MetadataMessageSchemaMerge();
  }

  @Override
  public BeaconStateSchemaMerge getBeaconStateSchema() {
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
  public MetadataMessageSchemaMerge getMetadataMessageSchema() {
    return metadataMessageSchema;
  }

  @Override
  public Optional<SchemaDefinitionsAltair> toVersionAltair() {
    return Optional.empty();
  }

  @Override
  public Optional<SchemaDefinitionsMerge> toVersionMerge() {
    return Optional.of(this);
  }
}
