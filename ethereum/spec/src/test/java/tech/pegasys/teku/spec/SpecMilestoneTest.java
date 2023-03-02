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

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.config.SpecConfigBellatrix;
import tech.pegasys.teku.spec.config.SpecConfigCapella;
import tech.pegasys.teku.spec.config.SpecConfigLoader;
import tech.pegasys.teku.spec.networks.Eth2Network;

public class SpecMilestoneTest {
  private final SpecConfigCapella capellaSpecConfig =
      SpecConfigCapella.required(SpecConfigLoader.loadConfig(Eth2Network.MINIMAL.configName()));
  private final SpecConfigBellatrix bellatrixSpecConfig =
      SpecConfigBellatrix.required(SpecConfigLoader.loadConfig(Eth2Network.MINIMAL.configName()));
  private final SpecConfigAltair altairSpecConfig =
      SpecConfigAltair.required(SpecConfigLoader.loadConfig(Eth2Network.MINIMAL.configName()));
  private final SpecConfig phase0SpecConfig =
      SpecConfigLoader.loadConfig(Eth2Network.MINIMAL.configName());

  @Test
  public void isGreaterThanOrEqualTo() {
    // Phase0
    assertThat(SpecMilestone.PHASE0.isGreaterThanOrEqualTo(SpecMilestone.PHASE0)).isTrue();
    assertThat(SpecMilestone.PHASE0.isGreaterThanOrEqualTo(SpecMilestone.ALTAIR)).isFalse();

    // Altair
    assertThat(SpecMilestone.ALTAIR.isGreaterThanOrEqualTo(SpecMilestone.PHASE0)).isTrue();
    assertThat(SpecMilestone.ALTAIR.isGreaterThanOrEqualTo(SpecMilestone.ALTAIR)).isTrue();
    assertThat(SpecMilestone.ALTAIR.isGreaterThanOrEqualTo(SpecMilestone.BELLATRIX)).isFalse();

    // Bellatrix
    assertThat(SpecMilestone.BELLATRIX.isGreaterThanOrEqualTo(SpecMilestone.ALTAIR)).isTrue();
    assertThat(SpecMilestone.BELLATRIX.isGreaterThanOrEqualTo(SpecMilestone.BELLATRIX)).isTrue();
    assertThat(SpecMilestone.BELLATRIX.isGreaterThanOrEqualTo(SpecMilestone.CAPELLA)).isFalse();

    // Capella
    assertThat(SpecMilestone.CAPELLA.isGreaterThanOrEqualTo(SpecMilestone.BELLATRIX)).isTrue();
    assertThat(SpecMilestone.CAPELLA.isGreaterThanOrEqualTo(SpecMilestone.CAPELLA)).isTrue();
    assertThat(SpecMilestone.CAPELLA.isGreaterThanOrEqualTo(SpecMilestone.DENEB)).isFalse();
  }

  @Test
  public void getAllPriorMilestones_phase0() {
    assertThat(SpecMilestone.getAllPriorMilestones(SpecMilestone.PHASE0)).isEmpty();
  }

  @Test
  public void getAllPriorMilestones_altair() {
    assertThat(SpecMilestone.getAllPriorMilestones(SpecMilestone.ALTAIR))
        .contains(SpecMilestone.PHASE0);
  }

  @Test
  public void getAllPriorMilestones_bellatrix() {
    assertThat(SpecMilestone.getAllPriorMilestones(SpecMilestone.BELLATRIX))
        .contains(SpecMilestone.PHASE0, SpecMilestone.ALTAIR);
  }

  @Test
  public void getAllPriorMilestones_capella() {
    assertThat(SpecMilestone.getAllPriorMilestones(SpecMilestone.CAPELLA))
        .contains(SpecMilestone.PHASE0, SpecMilestone.ALTAIR, SpecMilestone.BELLATRIX);
  }

  @Test
  public void getMilestonesUpTo_phase0() {
    assertThat(SpecMilestone.getMilestonesUpTo(SpecMilestone.PHASE0))
        .contains(SpecMilestone.PHASE0);
  }

  @Test
  public void getMilestonesUpTo_altair() {
    assertThat(SpecMilestone.getMilestonesUpTo(SpecMilestone.ALTAIR))
        .contains(SpecMilestone.PHASE0, SpecMilestone.ALTAIR);
  }

  @Test
  public void getMilestonesUpTo_bellatrix() {
    assertThat(SpecMilestone.getMilestonesUpTo(SpecMilestone.BELLATRIX))
        .contains(SpecMilestone.PHASE0, SpecMilestone.ALTAIR, SpecMilestone.BELLATRIX);
  }

  @Test
  public void getMilestonesUpTo_capella() {
    assertThat(SpecMilestone.getMilestonesUpTo(SpecMilestone.CAPELLA))
        .contains(SpecMilestone.PHASE0, SpecMilestone.ALTAIR, SpecMilestone.BELLATRIX);
  }

  @Test
  public void areMilestonesInOrder() {
    assertThat(SpecMilestone.areMilestonesInOrder(SpecMilestone.PHASE0, SpecMilestone.ALTAIR))
        .isTrue();
    assertThat(SpecMilestone.areMilestonesInOrder(SpecMilestone.PHASE0)).isTrue();
    assertThat(SpecMilestone.areMilestonesInOrder(SpecMilestone.ALTAIR)).isTrue();
    assertThat(SpecMilestone.areMilestonesInOrder(SpecMilestone.ALTAIR, SpecMilestone.PHASE0))
        .isFalse();
    assertThat(
            SpecMilestone.areMilestonesInOrder(
                SpecMilestone.PHASE0, SpecMilestone.ALTAIR, SpecMilestone.BELLATRIX))
        .isTrue();
    assertThat(
            SpecMilestone.areMilestonesInOrder(
                SpecMilestone.ALTAIR, SpecMilestone.PHASE0, SpecMilestone.BELLATRIX))
        .isFalse();
    assertThat(
            SpecMilestone.areMilestonesInOrder(
                SpecMilestone.PHASE0, SpecMilestone.BELLATRIX, SpecMilestone.ALTAIR))
        .isFalse();
    assertThat(SpecMilestone.areMilestonesInOrder(SpecMilestone.BELLATRIX, SpecMilestone.CAPELLA))
        .isTrue();
    assertThat(SpecMilestone.areMilestonesInOrder(SpecMilestone.CAPELLA, SpecMilestone.BELLATRIX))
        .isFalse();
  }

  @Test
  public void getForkVersion_phase0() {
    final Bytes4 expected = altairSpecConfig.getGenesisForkVersion();
    assertThat(SpecMilestone.getForkVersion(altairSpecConfig, SpecMilestone.PHASE0))
        .contains(expected);
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
    assertThat(SpecMilestone.getForkVersion(capellaSpecConfig, SpecMilestone.CAPELLA))
        .contains(expected);
  }

  @Test
  public void getForkEpoch_phase0() {
    final UInt64 expected = UInt64.ZERO;
    assertThat(SpecMilestone.getForkEpoch(altairSpecConfig, SpecMilestone.PHASE0))
        .contains(expected);
  }

  @Test
  public void getForkEpoch_altair() {
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
    assertThat(SpecMilestone.getForkEpoch(capellaSpecConfig, SpecMilestone.CAPELLA))
        .contains(expected);
  }

  @Test
  public void getForkEpoch_altairNotScheduled() {
    assertThat(SpecMilestone.getForkEpoch(phase0SpecConfig, SpecMilestone.ALTAIR))
        .contains(UInt64.MAX_VALUE);
  }

  @Test
  public void getForkEpoch_bellatrixNotScheduled() {
    assertThat(SpecMilestone.getForkEpoch(altairSpecConfig, SpecMilestone.BELLATRIX))
        .contains(UInt64.MAX_VALUE);
  }

  @Test
  public void getForkEpoch_capellaNotScheduled() {
    assertThat(SpecMilestone.getForkEpoch(bellatrixSpecConfig, SpecMilestone.CAPELLA))
        .contains(UInt64.MAX_VALUE);
  }
}
