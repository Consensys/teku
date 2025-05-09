/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.spec;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigAndParent;
import tech.pegasys.teku.spec.config.SpecConfigLoader;

@TestSpecContext(allMilestones = true)
class SpecFactoryTest {
  private static final Logger LOG = LogManager.getLogger();
  private SpecMilestone milestone;
  private TestSpecInvocationContextProvider.SpecContext specContext;

  @BeforeEach
  void setup(final TestSpecInvocationContextProvider.SpecContext specContext) {
    this.milestone = specContext.getSpecMilestone();
    this.specContext = specContext;
  }

  @TestTemplate
  void shouldSetHighestSupportedMilestone() {
    final UInt64 forkEpoch = UInt64.valueOf(1);
    final SpecConfigAndParent<? extends SpecConfig> config =
        SpecConfigLoader.loadConfig(
            specContext.getNetwork().configName(),
            builder -> {
              switch (milestone) {
                case PHASE0 -> LOG.info("PHASE0");
                case ALTAIR -> builder.altairBuilder(a -> a.altairForkEpoch(forkEpoch));
                case BELLATRIX ->
                    builder
                        .altairBuilder(a -> a.altairForkEpoch(UInt64.ZERO))
                        .bellatrixBuilder(b -> b.bellatrixForkEpoch(forkEpoch));
                case CAPELLA ->
                    builder
                        .altairBuilder(a -> a.altairForkEpoch(UInt64.ZERO))
                        .bellatrixBuilder(b -> b.bellatrixForkEpoch(UInt64.ZERO))
                        .capellaBuilder(c -> c.capellaForkEpoch(forkEpoch));
                case DENEB ->
                    builder
                        .altairBuilder(a -> a.altairForkEpoch(UInt64.ZERO))
                        .bellatrixBuilder(b -> b.bellatrixForkEpoch(UInt64.ZERO))
                        .capellaBuilder(c -> c.capellaForkEpoch(UInt64.ZERO))
                        .denebBuilder(d -> d.denebForkEpoch(forkEpoch));
                case ELECTRA ->
                    builder
                        .altairBuilder(a -> a.altairForkEpoch(UInt64.ZERO))
                        .bellatrixBuilder(b -> b.bellatrixForkEpoch(UInt64.ZERO))
                        .capellaBuilder(c -> c.capellaForkEpoch(UInt64.ZERO))
                        .denebBuilder(d -> d.denebForkEpoch(UInt64.ZERO))
                        .electraBuilder(e -> e.electraForkEpoch(forkEpoch));
                case FULU ->
                    builder
                        .altairBuilder(a -> a.altairForkEpoch(UInt64.ZERO))
                        .bellatrixBuilder(b -> b.bellatrixForkEpoch(UInt64.ZERO))
                        .capellaBuilder(c -> c.capellaForkEpoch(UInt64.ZERO))
                        .denebBuilder(d -> d.denebForkEpoch(UInt64.ZERO))
                        .electraBuilder(e -> e.electraForkEpoch(UInt64.ZERO))
                        .fuluBuilder(f -> f.fuluForkEpoch(forkEpoch));
                default ->
                    throw new IllegalStateException(
                        "Unhandled fork transition for test "
                            + specContext.getDisplayName()
                            + ": "
                            + milestone);
              }
            });
    final Spec testSpec = SpecFactory.create(config);
    for (SpecMilestone currentMilestone : SpecMilestone.getAllPriorMilestones(milestone)) {
      LOG.info("Previous milestone " + currentMilestone);
      assertThat(testSpec.getForkSchedule().getFork(currentMilestone).getEpoch())
          .isEqualTo(UInt64.ZERO);
    }
    LOG.info("Highest milestone supported " + milestone);
    assertThat(testSpec.atEpoch(forkEpoch).getMilestone()).isEqualTo(milestone);

    for (SpecMilestone currentMilestone : SpecMilestone.getAllMilestonesFrom(milestone)) {
      if (currentMilestone == milestone) {
        continue;
      }
      LOG.info("Undefined milestone " + currentMilestone);
      assertThatThrownBy(() -> testSpec.getForkSchedule().getFork(currentMilestone))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("not a part of fork schedule");
    }
  }
}
