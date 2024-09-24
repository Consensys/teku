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

import static tech.pegasys.teku.spec.schemas.SchemaTypes.BEACON_BLOCK_BODY_SCHEMA;

import java.util.Set;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigBellatrix;
import tech.pegasys.teku.spec.config.SpecConfigCapella;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.BeaconBlockBodySchemaAltairImpl;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.bellatrix.BeaconBlockBodySchemaBellatrixImpl;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.capella.BeaconBlockBodySchemaCapellaImpl;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.BeaconBlockBodySchemaDenebImpl;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.electra.BeaconBlockBodySchemaElectraImpl;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.phase0.BeaconBlockBodySchemaPhase0;
import tech.pegasys.teku.spec.schemas.AbstractSchemaProvider;
import tech.pegasys.teku.spec.schemas.SchemaRegistry;

public class BeaconBlockBodySchemaProvider
    extends AbstractSchemaProvider<BeaconBlockBodySchema<? extends BeaconBlockBody>> {

  public BeaconBlockBodySchemaProvider() {
    super(BEACON_BLOCK_BODY_SCHEMA);
  }

  @Override
  protected BeaconBlockBodySchema<? extends BeaconBlockBody> createSchema(
      final SchemaRegistry registry,
      final SpecMilestone effectiveMilestone,
      final SpecConfig specConfig) {
    return switch (effectiveMilestone) {
      case PHASE0 ->
          BeaconBlockBodySchemaPhase0.create(specConfig, registry, "BeaconBlockBodyPhase0");
      case ALTAIR ->
          BeaconBlockBodySchemaAltairImpl.create(specConfig, registry, "BeaconBlockBodyAltair");
      case BELLATRIX ->
          BeaconBlockBodySchemaBellatrixImpl.create(
              SpecConfigBellatrix.required(specConfig), registry, "BeaconBlockBodyBellatrix");
      case CAPELLA ->
          BeaconBlockBodySchemaCapellaImpl.create(
              SpecConfigCapella.required(specConfig), registry, "BeaconBlockBodyCapella");
      case DENEB ->
          BeaconBlockBodySchemaDenebImpl.create(
              SpecConfigDeneb.required(specConfig), registry, "BeaconBlockBodyDeneb");
      case ELECTRA ->
          BeaconBlockBodySchemaElectraImpl.create(
              SpecConfigElectra.required(specConfig), registry, "BeaconBlockBodyElectra");
    };
  }

  @Override
  public Set<SpecMilestone> getSupportedMilestones() {
    return ALL_MILESTONES;
  }
}
