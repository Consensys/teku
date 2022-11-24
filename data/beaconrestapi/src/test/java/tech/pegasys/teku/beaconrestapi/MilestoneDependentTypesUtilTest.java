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

package tech.pegasys.teku.beaconrestapi;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.MilestoneDependentTypesUtil.getSchemaDefinitionForAllMilestones;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.json.types.SerializableOneOfTypeDefinition;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigLoader;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSchema;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;

public class MilestoneDependentTypesUtilTest {

  @Test
  public void shouldNotThrowExceptionWithSpecUnderHighestSupported() {
    final Spec spec = TestSpecFactory.createBellatrix(SpecConfigLoader.loadConfig("prater"));
    final SchemaDefinitionCache schemaDefinitionCache = new SchemaDefinitionCache(spec);
    SerializableOneOfTypeDefinition<BeaconBlock> blockType =
        getSchemaDefinitionForAllMilestones(
            schemaDefinitionCache,
            "BeaconBlockPhase0",
            SchemaDefinitions::getBeaconBlockSchema,
            (beaconBlock, milestone) ->
                schemaDefinitionCache.milestoneAtSlot(beaconBlock.getSlot()).equals(milestone));
    final BeaconBlockSchema schema = spec.getGenesisSchemaDefinitions().getBeaconBlockSchema();
    assertThat(schema.getContainerName()).isEqualTo("BeaconBlockPhase0");
    assertThat(blockType.isEquivalentToDeserializableType(schema.getJsonTypeDefinition())).isTrue();
  }

  @Test
  public void shouldSupportHighestSupportedMilestone() {
    final Spec spec = TestSpecFactory.createMainnetCapella();
    final SchemaDefinitionCache schemaDefinitionCache = new SchemaDefinitionCache(spec);
    SerializableOneOfTypeDefinition<BeaconBlock> blindedBlockType =
        getSchemaDefinitionForAllMilestones(
            schemaDefinitionCache,
            "BlindedBlock",
            SchemaDefinitions::getBlindedBeaconBlockSchema,
            (beaconBlock, milestone) ->
                schemaDefinitionCache.milestoneAtSlot(beaconBlock.getSlot()).equals(milestone));
    final BeaconBlockSchema schema =
        spec.getGenesisSchemaDefinitions().getBlindedBeaconBlockSchema();
    assertThat(schema.getContainerName()).isEqualTo("BlindedBlockCapella");
    assertThat(blindedBlockType.isEquivalentToDeserializableType(schema.getJsonTypeDefinition()))
        .isTrue();
  }
}
