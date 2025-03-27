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

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static tech.pegasys.teku.spec.SpecMilestone.ALTAIR;
import static tech.pegasys.teku.spec.SpecMilestone.BELLATRIX;
import static tech.pegasys.teku.spec.SpecMilestone.CAPELLA;
import static tech.pegasys.teku.spec.SpecMilestone.DENEB;
import static tech.pegasys.teku.spec.SpecMilestone.ELECTRA;
import static tech.pegasys.teku.spec.SpecMilestone.PHASE0;

import java.util.stream.Stream;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.config.SpecConfigBellatrix;
import tech.pegasys.teku.spec.config.SpecConfigCapella;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.config.SpecConfigLoader;
import tech.pegasys.teku.spec.networks.Eth2Network;

public class SpecMilestoneTest {
  private final SpecConfigElectra electraSpecConfig =
      SpecConfigLoader.loadConfig(Eth2Network.MINIMAL.configName())
          .specConfig()
          .toVersionElectra()
          .orElseThrow();
  private final SpecConfigDeneb denebSpecConfig =
      SpecConfigLoader.loadConfig(Eth2Network.MINIMAL.configName())
          .specConfig()
          .toVersionDeneb()
          .orElseThrow();
  private final SpecConfigCapella capellaSpecConfig =
      SpecConfigLoader.loadConfig(Eth2Network.MINIMAL.configName())
          .specConfig()
          .toVersionCapella()
          .orElseThrow();
  private final SpecConfigBellatrix bellatrixSpecConfig =
      SpecConfigLoader.loadConfig(Eth2Network.MINIMAL.configName())
          .specConfig()
          .toVersionBellatrix()
          .orElseThrow();
  private final SpecConfigAltair altairSpecConfig =
      SpecConfigLoader.loadConfig(Eth2Network.MINIMAL.configName())
          .specConfig()
          .toVersionAltair()
          .orElseThrow();
  private final SpecConfig phase0SpecConfig =
      SpecConfigLoader.loadConfig(Eth2Network.MINIMAL.configName()).specConfig();

  public static Stream<Arguments> isLessThanPermutations() {
    return Stream.of(
        Arguments.of(PHASE0, PHASE0, false),
        Arguments.of(PHASE0, ALTAIR, true),
        Arguments.of(PHASE0, BELLATRIX, true),
        Arguments.of(PHASE0, CAPELLA, true),
        Arguments.of(PHASE0, DENEB, true),
        Arguments.of(PHASE0, ELECTRA, true),
        Arguments.of(ALTAIR, PHASE0, false),
        Arguments.of(ALTAIR, ALTAIR, false),
        Arguments.of(ALTAIR, BELLATRIX, true),
        Arguments.of(ALTAIR, CAPELLA, true),
        Arguments.of(ALTAIR, DENEB, true),
        Arguments.of(ALTAIR, ELECTRA, true),
        Arguments.of(BELLATRIX, PHASE0, false),
        Arguments.of(BELLATRIX, ALTAIR, false),
        Arguments.of(BELLATRIX, BELLATRIX, false),
        Arguments.of(BELLATRIX, CAPELLA, true),
        Arguments.of(BELLATRIX, DENEB, true),
        Arguments.of(BELLATRIX, ELECTRA, true),
        Arguments.of(CAPELLA, PHASE0, false),
        Arguments.of(CAPELLA, ALTAIR, false),
        Arguments.of(CAPELLA, BELLATRIX, false),
        Arguments.of(CAPELLA, CAPELLA, false),
        Arguments.of(CAPELLA, DENEB, true),
        Arguments.of(CAPELLA, ELECTRA, true),
        Arguments.of(DENEB, PHASE0, false),
        Arguments.of(DENEB, ALTAIR, false),
        Arguments.of(DENEB, BELLATRIX, false),
        Arguments.of(DENEB, CAPELLA, false),
        Arguments.of(DENEB, DENEB, false),
        Arguments.of(DENEB, ELECTRA, true),
        Arguments.of(ELECTRA, PHASE0, false),
        Arguments.of(ELECTRA, ALTAIR, false),
        Arguments.of(ELECTRA, BELLATRIX, false),
        Arguments.of(ELECTRA, CAPELLA, false),
        Arguments.of(ELECTRA, DENEB, false),
        Arguments.of(ELECTRA, ELECTRA, false));
  }

  public static Stream<Arguments> isGreaterThanOrEqualToPermutations() {
    return Stream.of(
        Arguments.of(PHASE0, PHASE0, true),
        Arguments.of(PHASE0, ALTAIR, false),
        Arguments.of(PHASE0, BELLATRIX, false),
        Arguments.of(PHASE0, CAPELLA, false),
        Arguments.of(PHASE0, DENEB, false),
        Arguments.of(PHASE0, ELECTRA, false),
        Arguments.of(ALTAIR, PHASE0, true),
        Arguments.of(ALTAIR, ALTAIR, true),
        Arguments.of(ALTAIR, BELLATRIX, false),
        Arguments.of(ALTAIR, CAPELLA, false),
        Arguments.of(ALTAIR, DENEB, false),
        Arguments.of(ALTAIR, ELECTRA, false),
        Arguments.of(BELLATRIX, PHASE0, true),
        Arguments.of(BELLATRIX, ALTAIR, true),
        Arguments.of(BELLATRIX, BELLATRIX, true),
        Arguments.of(BELLATRIX, CAPELLA, false),
        Arguments.of(BELLATRIX, DENEB, false),
        Arguments.of(BELLATRIX, ELECTRA, false),
        Arguments.of(CAPELLA, PHASE0, true),
        Arguments.of(CAPELLA, ALTAIR, true),
        Arguments.of(CAPELLA, BELLATRIX, true),
        Arguments.of(CAPELLA, CAPELLA, true),
        Arguments.of(CAPELLA, DENEB, false),
        Arguments.of(CAPELLA, ELECTRA, false),
        Arguments.of(DENEB, PHASE0, true),
        Arguments.of(DENEB, ALTAIR, true),
        Arguments.of(DENEB, BELLATRIX, true),
        Arguments.of(DENEB, CAPELLA, true),
        Arguments.of(DENEB, DENEB, true),
        Arguments.of(DENEB, ELECTRA, false),
        Arguments.of(ELECTRA, PHASE0, true),
        Arguments.of(ELECTRA, ALTAIR, true),
        Arguments.of(ELECTRA, BELLATRIX, true),
        Arguments.of(ELECTRA, CAPELLA, true),
        Arguments.of(ELECTRA, DENEB, true),
        Arguments.of(ELECTRA, ELECTRA, true));
  }

  public static Stream<Arguments> getPreviousPermutations() {
    return Stream.of(
        Arguments.of(ALTAIR, PHASE0),
        Arguments.of(BELLATRIX, ALTAIR),
        Arguments.of(CAPELLA, BELLATRIX),
        Arguments.of(DENEB, CAPELLA),
        Arguments.of(ELECTRA, DENEB));
  }

  @ParameterizedTest
  @MethodSource("isGreaterThanOrEqualToPermutations")
  public void isGreaterThanOrEqualTo(
      final SpecMilestone a, final SpecMilestone b, final boolean comparisonResult) {
    assertThat(a.isGreaterThanOrEqualTo(b)).isEqualTo(comparisonResult);
  }

  @ParameterizedTest
  @MethodSource("isLessThanPermutations")
  public void isLessThan(
      final SpecMilestone a, final SpecMilestone b, final boolean comparisonResult) {
    AssertionsForClassTypes.assertThat(a.isLessThan(b)).isEqualTo(comparisonResult);
  }

  @ParameterizedTest
  @MethodSource("getPreviousPermutations")
  public void getPreviousMilestone(final SpecMilestone current, final SpecMilestone previous) {
    assertThat(current.getPreviousMilestone()).isEqualTo(previous);
  }

  @Test
  void getPreviousMilestone_throws() {
    assertThrows(IllegalArgumentException.class, PHASE0::getPreviousMilestone);
  }

  @Test
  void getPreviousMilestoneIfPresent() {
    assertThat(PHASE0.getPreviousMilestoneIfExists()).isEmpty();
    assertThat(ALTAIR.getPreviousMilestoneIfExists()).contains(PHASE0);
    assertThat(BELLATRIX.getPreviousMilestoneIfExists()).contains(ALTAIR);
    assertThat(CAPELLA.getPreviousMilestoneIfExists()).contains(BELLATRIX);
  }

  @Test
  public void getAllPriorMilestones_phase0() {
    assertThat(SpecMilestone.getAllPriorMilestones(PHASE0)).isEmpty();
  }

  @Test
  public void getAllPriorMilestones_altair() {
    assertThat(SpecMilestone.getAllPriorMilestones(SpecMilestone.ALTAIR)).contains(PHASE0);
  }

  @Test
  public void getAllPriorMilestones_bellatrix() {
    assertThat(SpecMilestone.getAllPriorMilestones(SpecMilestone.BELLATRIX))
        .contains(PHASE0, SpecMilestone.ALTAIR);
  }

  @Test
  public void getAllPriorMilestones_capella() {
    assertThat(SpecMilestone.getAllPriorMilestones(CAPELLA))
        .contains(PHASE0, SpecMilestone.ALTAIR, SpecMilestone.BELLATRIX);
  }

  @Test
  public void getAllPriorMilestones_deneb() {
    assertThat(SpecMilestone.getAllPriorMilestones(DENEB))
        .contains(PHASE0, SpecMilestone.ALTAIR, SpecMilestone.BELLATRIX, CAPELLA);
  }

  @Test
  public void getAllPriorMilestones_electra() {
    assertThat(SpecMilestone.getAllPriorMilestones(SpecMilestone.ELECTRA))
        .contains(PHASE0, SpecMilestone.ALTAIR, SpecMilestone.BELLATRIX, CAPELLA, DENEB);
  }

  @Test
  public void getMilestonesUpTo_phase0() {
    assertThat(SpecMilestone.getMilestonesUpTo(PHASE0)).contains(PHASE0);
  }

  @Test
  public void getMilestonesUpTo_altair() {
    assertThat(SpecMilestone.getMilestonesUpTo(SpecMilestone.ALTAIR))
        .contains(PHASE0, SpecMilestone.ALTAIR);
  }

  @Test
  public void getMilestonesUpTo_bellatrix() {
    assertThat(SpecMilestone.getMilestonesUpTo(SpecMilestone.BELLATRIX))
        .contains(PHASE0, SpecMilestone.ALTAIR, SpecMilestone.BELLATRIX);
  }

  @Test
  public void getMilestonesUpTo_capella() {
    assertThat(SpecMilestone.getMilestonesUpTo(CAPELLA))
        .contains(PHASE0, SpecMilestone.ALTAIR, SpecMilestone.BELLATRIX);
  }

  @Test
  public void getMilestonesUpTo_deneb() {
    assertThat(SpecMilestone.getMilestonesUpTo(DENEB))
        .contains(PHASE0, SpecMilestone.ALTAIR, SpecMilestone.BELLATRIX, CAPELLA);
  }

  @Test
  public void getMilestonesUpTo_electra() {
    assertThat(SpecMilestone.getMilestonesUpTo(SpecMilestone.ELECTRA))
        .contains(PHASE0, SpecMilestone.ALTAIR, SpecMilestone.BELLATRIX, CAPELLA, DENEB);
  }

  @Test
  public void areMilestonesInOrder() {
    assertThat(SpecMilestone.areMilestonesInOrder(PHASE0, SpecMilestone.ALTAIR)).isTrue();
    assertThat(SpecMilestone.areMilestonesInOrder(PHASE0)).isTrue();
    assertThat(SpecMilestone.areMilestonesInOrder(SpecMilestone.ALTAIR)).isTrue();
    assertThat(SpecMilestone.areMilestonesInOrder(SpecMilestone.ALTAIR, PHASE0)).isFalse();
    assertThat(
            SpecMilestone.areMilestonesInOrder(
                PHASE0, SpecMilestone.ALTAIR, SpecMilestone.BELLATRIX))
        .isTrue();
    assertThat(
            SpecMilestone.areMilestonesInOrder(
                SpecMilestone.ALTAIR, PHASE0, SpecMilestone.BELLATRIX))
        .isFalse();
    assertThat(
            SpecMilestone.areMilestonesInOrder(
                PHASE0, SpecMilestone.BELLATRIX, SpecMilestone.ALTAIR))
        .isFalse();
    assertThat(SpecMilestone.areMilestonesInOrder(SpecMilestone.BELLATRIX, CAPELLA)).isTrue();
    assertThat(SpecMilestone.areMilestonesInOrder(CAPELLA, SpecMilestone.BELLATRIX)).isFalse();
    assertThat(SpecMilestone.areMilestonesInOrder(CAPELLA, DENEB)).isTrue();
    assertThat(SpecMilestone.areMilestonesInOrder(DENEB, CAPELLA)).isFalse();
    assertThat(SpecMilestone.areMilestonesInOrder(DENEB, SpecMilestone.ELECTRA)).isTrue();
    assertThat(SpecMilestone.areMilestonesInOrder(SpecMilestone.ELECTRA, DENEB)).isFalse();
  }

  @Test
  public void getForkVersion_phase0() {
    final Bytes4 expected = altairSpecConfig.getGenesisForkVersion();
    assertThat(SpecMilestone.getForkVersion(altairSpecConfig, PHASE0)).contains(expected);
  }

  @Test
  public void getForkVersion_altair() {
    final Bytes4 expected = altairSpecConfig.getAltairForkVersion();
    assertThat(SpecMilestone.getForkVersion(altairSpecConfig, SpecMilestone.ALTAIR))
        .contains(expected);
  }

  @Test
  public void getForkVersion_bellatrix() {
    final Bytes4 expected = bellatrixSpecConfig.getBellatrixForkVersion();
    assertThat(SpecMilestone.getForkVersion(bellatrixSpecConfig, SpecMilestone.BELLATRIX))
        .contains(expected);
  }

  @Test
  public void getForkVersion_capella() {
    final Bytes4 expected = capellaSpecConfig.getCapellaForkVersion();
    assertThat(SpecMilestone.getForkVersion(capellaSpecConfig, CAPELLA)).contains(expected);
  }

  @Test
  public void getForkVersion_deneb() {
    final Bytes4 expected = denebSpecConfig.getDenebForkVersion();
    assertThat(SpecMilestone.getForkVersion(denebSpecConfig, DENEB)).contains(expected);
  }

  @Test
  public void getForkVersion_electra() {
    final Bytes4 expected = electraSpecConfig.getElectraForkVersion();
    assertThat(SpecMilestone.getForkVersion(electraSpecConfig, SpecMilestone.ELECTRA))
        .contains(expected);
  }

  @Test
  public void getForkEpoch_phase0() {
    final UInt64 expected = UInt64.ZERO;
    assertThat(SpecMilestone.getForkEpoch(altairSpecConfig, PHASE0)).contains(expected);
  }

  @Test
  public void getForEpoch_altair() {
    final UInt64 expected = altairSpecConfig.getAltairForkEpoch();
    assertThat(SpecMilestone.getForkEpoch(altairSpecConfig, SpecMilestone.ALTAIR))
        .contains(expected);
  }

  @Test
  public void getForkEpoch_bellatrix() {
    final UInt64 expected = bellatrixSpecConfig.getBellatrixForkEpoch();
    assertThat(SpecMilestone.getForkEpoch(bellatrixSpecConfig, SpecMilestone.BELLATRIX))
        .contains(expected);
  }

  @Test
  public void getForkEpoch_capella() {
    final UInt64 expected = capellaSpecConfig.getCapellaForkEpoch();
    assertThat(SpecMilestone.getForkEpoch(capellaSpecConfig, CAPELLA)).contains(expected);
  }

  @Test
  public void getForkEpoch_deneb() {
    final UInt64 expected = denebSpecConfig.getDenebForkEpoch();
    assertThat(SpecMilestone.getForkEpoch(denebSpecConfig, DENEB)).contains(expected);
  }

  @Test
  public void getForkEpoch_electra() {
    final UInt64 expected = electraSpecConfig.getElectraForkEpoch();
    assertThat(SpecMilestone.getForkEpoch(electraSpecConfig, SpecMilestone.ELECTRA))
        .contains(expected);
  }

  @Test
  public void getForkSlot_altairNotScheduled() {
    assertThat(SpecMilestone.getForkEpoch(phase0SpecConfig, SpecMilestone.ALTAIR))
        .contains(UInt64.MAX_VALUE);
  }

  @Test
  public void getForkSlot_bellatrixNotScheduled() {
    assertThat(SpecMilestone.getForkEpoch(phase0SpecConfig, SpecMilestone.BELLATRIX))
        .contains(UInt64.MAX_VALUE);
  }

  @Test
  public void getForkEpoch_capellaNotScheduled() {
    assertThat(SpecMilestone.getForkEpoch(bellatrixSpecConfig, CAPELLA)).contains(UInt64.MAX_VALUE);
  }

  @Test
  public void getForkEpoch_denebNotScheduled() {
    assertThat(SpecMilestone.getForkEpoch(capellaSpecConfig, DENEB)).contains(UInt64.MAX_VALUE);
  }

  @Test
  public void getForkEpoch_electraNotScheduled() {
    assertThat(SpecMilestone.getForkEpoch(denebSpecConfig, SpecMilestone.ELECTRA))
        .contains(UInt64.MAX_VALUE);
  }
}
