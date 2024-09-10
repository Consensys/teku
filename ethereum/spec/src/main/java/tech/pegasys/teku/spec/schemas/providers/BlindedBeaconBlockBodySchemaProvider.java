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

import static tech.pegasys.teku.spec.schemas.SchemaTypes.BLINDED_BEACON_BLOCK_BODY_SCHEMA;

import java.util.Set;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigBellatrix;
import tech.pegasys.teku.spec.config.SpecConfigCapella;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.bellatrix.BlindedBeaconBlockBodySchemaBellatrix;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.bellatrix.BlindedBeaconBlockBodySchemaBellatrixImpl;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.capella.BlindedBeaconBlockBodySchemaCapellaImpl;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.BlindedBeaconBlockBodySchemaDenebImpl;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.electra.BlindedBeaconBlockBodySchemaElectraImpl;
import tech.pegasys.teku.spec.schemas.AbstractSchemaProvider;
import tech.pegasys.teku.spec.schemas.SchemaRegistry;

public class BlindedBeaconBlockBodySchemaProvider
    extends AbstractSchemaProvider<BlindedBeaconBlockBodySchemaBellatrix<?>> {

  public BlindedBeaconBlockBodySchemaProvider() {
    super(BLINDED_BEACON_BLOCK_BODY_SCHEMA);
  }

  @Override
  protected BlindedBeaconBlockBodySchemaBellatrix<?> createSchema(
      final SchemaRegistry registry,
      final SpecMilestone effectiveMilestone,
      final SpecConfig specConfig) {
    return switch (effectiveMilestone) {
      case BELLATRIX ->
          BlindedBeaconBlockBodySchemaBellatrixImpl.create(
              SpecConfigBellatrix.required(registry.getSpecConfig()),
              registry,
              "BlindedBlockBodyBellatrix");

      case CAPELLA ->
          BlindedBeaconBlockBodySchemaCapellaImpl.create(
              SpecConfigCapella.required(specConfig), registry, "BlindedBlockBodyCapella");
      case DENEB ->
          BlindedBeaconBlockBodySchemaDenebImpl.create(
              SpecConfigDeneb.required(specConfig), registry, "BlindedBlockBodyDeneb");

      case ELECTRA ->
          BlindedBeaconBlockBodySchemaElectraImpl.create(
              SpecConfigElectra.required(specConfig), registry, "BlindedBlockBodyElectra");
      default -> throw new IllegalStateException("");
    };
  }

  @Override
  public Set<SpecMilestone> getSupportedMilestones() {
    return FROM_BELLATRIX;
  }
}
