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

package tech.pegasys.teku.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.spec.SpecMilestone.ALTAIR;
import static tech.pegasys.teku.spec.SpecMilestone.BELLATRIX;
import static tech.pegasys.teku.spec.SpecMilestone.PHASE0;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigLoader;
import tech.pegasys.teku.spec.logic.versions.altair.statetransition.attestation.AttestationWorthinessCheckerAltair;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class SpecFactoryTest {

  private static final Set<String> NON_BELLATRIX_NETWORKS = Set.of("gnosis", "swift", "less-swift");

  @Test
  public void defaultFactoryShouldScheduleAltairAndBellatrixForMainNet() {
    final Spec spec = SpecFactory.create("mainnet");
    assertThat(spec.getForkSchedule().getSupportedMilestones())
        .containsExactly(PHASE0, ALTAIR, BELLATRIX);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getKnownConfigNames")
  public void defaultFactoryShouldNotEnableBellatrixUnlessForkEpochIsSet(final String configName) {
    final Spec spec = SpecFactory.create(configName);
    if (configName.equals("minimal")) { // Minimal doesn't have altair scheduled
      assertThat(spec.getForkSchedule().getSupportedMilestones()).containsExactly(PHASE0);
    } else if (NON_BELLATRIX_NETWORKS.contains(configName)) {
      assertThat(spec.getForkSchedule().getSupportedMilestones()).containsExactly(PHASE0, ALTAIR);
    } else {
      assertThat(spec.getForkSchedule().getSupportedMilestones())
          .containsExactly(PHASE0, ALTAIR, BELLATRIX);
    }
  }

  @Test
  void shouldSupportAltairWhenForkEpochSetInConfig() {
    final SpecConfig config =
        SpecConfigLoader.loadConfig(
            "minimal",
            phase0Builder ->
                phase0Builder.altairBuilder(
                    altairBuilder -> altairBuilder.altairForkEpoch(UInt64.valueOf(10))));
    final Spec spec = SpecFactory.create(config);
    assertThat(spec.getForkSchedule().getSupportedMilestones()).containsExactly(PHASE0, ALTAIR);
    assertThat(spec.getForkSchedule().getSpecMilestoneAtEpoch(UInt64.valueOf(10)))
        .isEqualTo(ALTAIR);
  }

  @SuppressWarnings("unused")
  static Stream<Arguments> getKnownConfigNames() {
    return Arrays.stream(Eth2Network.values()).map(Eth2Network::configName).map(Arguments::of);
  }

  @ParameterizedTest
  @EnumSource(
      value = SpecMilestone.class,
      names = {"BELLATRIX"},
      mode = EnumSource.Mode.EXCLUDE)
  public void shouldCreateTheRightAttestationWorthinessChecker(SpecMilestone milestone) {
    final Spec spec = TestSpecFactory.createMainnet(milestone);
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

    if (milestone.isGreaterThanOrEqualTo(ALTAIR)) {
      assertThat(spec.createAttestationWorthinessChecker(dataStructureUtil.randomBeaconState()))
          .isInstanceOf(AttestationWorthinessCheckerAltair.class);
    } else {
      assertThat(spec.createAttestationWorthinessChecker(dataStructureUtil.randomBeaconState()))
          .isInstanceOf(AttestationWorthinessCheckerAltair.NOOP.getClass());
    }
  }
}
