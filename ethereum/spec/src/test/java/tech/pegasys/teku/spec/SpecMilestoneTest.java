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

package tech.pegasys.teku.spec;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.config.SpecConfigLoader;
import tech.pegasys.teku.spec.config.TestConfigLoader;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.teku.ssz.type.Bytes4;

public class SpecMilestoneTest {
  private final SpecConfigAltair specConfig =
      SpecConfigAltair.required(SpecConfigLoader.loadConfig(Eth2Network.MINIMAL.configName()));
  private final SpecConfig phase0SpecConfig =
      TestConfigLoader.loadPhase0Config(Eth2Network.MINIMAL.configName());

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
  public void areMilestonesInOrder() {
    assertThat(SpecMilestone.areMilestonesInOrder(SpecMilestone.PHASE0, SpecMilestone.ALTAIR))
        .isTrue();
    assertThat(SpecMilestone.areMilestonesInOrder(SpecMilestone.PHASE0)).isTrue();
    assertThat(SpecMilestone.areMilestonesInOrder(SpecMilestone.ALTAIR)).isTrue();
    assertThat(SpecMilestone.areMilestonesInOrder(SpecMilestone.ALTAIR, SpecMilestone.PHASE0))
        .isFalse();
  }

  @Test
  public void getForkVersion_phase0() {
    final Bytes4 expected = specConfig.getGenesisForkVersion();
    assertThat(SpecMilestone.getForkVersion(specConfig, SpecMilestone.PHASE0)).contains(expected);
  }

  @Test
  public void getForkVersion_altair() {
    final Bytes4 expected = specConfig.getAltairForkVersion();
    assertThat(SpecMilestone.getForkVersion(specConfig, SpecMilestone.ALTAIR)).contains(expected);
  }

  @Test
  public void getForkVersion_altairNotScheduled() {
    assertThat(SpecMilestone.getForkVersion(phase0SpecConfig, SpecMilestone.ALTAIR)).isEmpty();
  }

  @Test
  public void getForkSlot_phase0() {
    final UInt64 expected = UInt64.ZERO;
    assertThat(SpecMilestone.getForkSlot(specConfig, SpecMilestone.PHASE0)).contains(expected);
  }

  @Test
  public void getForkSlot_altair() {
    final UInt64 expected = specConfig.getAltairForkSlot();
    assertThat(SpecMilestone.getForkSlot(specConfig, SpecMilestone.ALTAIR)).contains(expected);
  }

  @Test
  public void getForkSlot_altairNotScheduled() {
    assertThat(SpecMilestone.getForkSlot(phase0SpecConfig, SpecMilestone.ALTAIR)).isEmpty();
  }
}
