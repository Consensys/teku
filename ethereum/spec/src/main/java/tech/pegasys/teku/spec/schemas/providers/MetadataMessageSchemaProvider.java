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

package tech.pegasys.teku.spec.schemas.providers;

import static tech.pegasys.teku.spec.SpecMilestone.ALTAIR;

import java.util.Set;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.MetadataMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.MetadataMessageSchema;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.versions.altair.MetadataMessageSchemaAltair;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.versions.phase0.MetadataMessageSchemaPhase0;
import tech.pegasys.teku.spec.schemas.AbstractSchemaProvider;
import tech.pegasys.teku.spec.schemas.SchemaRegistry;
import tech.pegasys.teku.spec.schemas.SchemaTypes;

public class MetadataMessageSchemaProvider
    extends AbstractSchemaProvider<MetadataMessageSchema<? extends MetadataMessage>> {

  public MetadataMessageSchemaProvider() {
    super(SchemaTypes.METADATA_MESSAGE_SCHEMA);
    addMilestoneMapping(ALTAIR, SpecMilestone.getHighestMilestone());
  }

  @Override
  protected MetadataMessageSchema<? extends MetadataMessage> createSchema(
      final SchemaRegistry registry,
      final SpecMilestone effectiveMilestone,
      final SpecConfig specConfig) {
    return switch (effectiveMilestone) {
      case PHASE0 -> new MetadataMessageSchemaPhase0(specConfig.getNetworkingConfig());
      case ALTAIR -> new MetadataMessageSchemaAltair(specConfig.getNetworkingConfig());
      default ->
          throw new IllegalArgumentException(
              "It is not supposed to create a specific version for " + effectiveMilestone);
    };
  }

  @Override
  public Set<SpecMilestone> getSupportedMilestones() {
    return ALL_MILESTONES;
  }
}
