/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.spec.datastructures.lightclient;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsAltair;
import tech.pegasys.teku.spec.util.DataStructureUtil;

@TestSpecContext(allMilestones = true, ignoredMilestones = SpecMilestone.PHASE0)
public class LightClientHeaderSchemaTest {
  private Spec spec;
  private DataStructureUtil dataStructureUtil;

  @BeforeEach
  void setup(final SpecContext specContext) {
    spec = specContext.getSpec();
    dataStructureUtil = specContext.getDataStructureUtil();
  }

  @TestTemplate
  public void shouldCreateVariantForMilestone() {
    final LightClientHeader header = dataStructureUtil.randomLightClientHeader(UInt64.ZERO);
    final SpecMilestone milestone = spec.getGenesisSpec().getMilestone();

    if (milestone.isGreaterThanOrEqualTo(SpecMilestone.GLOAS)) {
      assertThat(header.toVersionGloas()).isPresent();
    } else if (milestone.isGreaterThanOrEqualTo(SpecMilestone.CAPELLA)) {
      assertThat(header.toVersionCapella()).isPresent();
    } else {
      assertThat(header.toVersionAltair()).isPresent();
    }
  }

  @TestTemplate
  public void shouldRoundTripViaSsz() {
    final LightClientHeader header = dataStructureUtil.randomLightClientHeader(UInt64.ZERO);
    final LightClientHeaderSchema<?> schema = headerSchema();

    final LightClientHeader result = schema.sszDeserialize(header.sszSerialize());

    assertThat(result).isEqualTo(header);
  }

  @TestTemplate
  public void createFromBeacon_shouldExposeBeacon() {
    final BeaconBlockHeader beacon = dataStructureUtil.randomBeaconBlockHeader();
    final LightClientHeader header = headerSchema().create(beacon);

    assertThat(header.getBeacon()).isEqualTo(beacon);
  }

  private LightClientHeaderSchema<?> headerSchema() {
    return SchemaDefinitionsAltair.required(spec.getGenesisSchemaDefinitions())
        .getLightClientHeaderSchema();
  }
}