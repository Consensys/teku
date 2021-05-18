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
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.phase0.BeaconBlockBodySchemaPhase0;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.versions.phase0.MetadataMessageSchemaPhase0;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.phase0.BeaconStateSchemaPhase0;

public class SchemaDefinitionsPhase0 extends AbstractSchemaDefinitions {
  private final BeaconStateSchema<?, ?> beaconStateSchema;
  private final BeaconBlockBodySchema<?> beaconBlockBodySchema;
  private final MetadataMessageSchemaPhase0 metadataMessageSchema;

  public SchemaDefinitionsPhase0(final SpecConfig specConfig) {
    this.beaconStateSchema = BeaconStateSchemaPhase0.create(specConfig);
    this.beaconBlockBodySchema = BeaconBlockBodySchemaPhase0.create(specConfig);
    this.metadataMessageSchema = new MetadataMessageSchemaPhase0();
  }

  @Override
  public BeaconStateSchema<?, ?> getBeaconStateSchema() {
    return beaconStateSchema;
  }

  @Override
  public SignedBeaconBlockSchema getSignedBeaconBlockSchema() {
    return new SignedBeaconBlockSchema(getBeaconBlockSchema());
  }

  @Override
  public BeaconBlockSchema getBeaconBlockSchema() {
    return new BeaconBlockSchema(getBeaconBlockBodySchema());
  }

  @Override
  public BeaconBlockBodySchema<?> getBeaconBlockBodySchema() {
    return beaconBlockBodySchema;
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
