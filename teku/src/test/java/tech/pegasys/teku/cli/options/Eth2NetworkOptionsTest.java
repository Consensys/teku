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

package tech.pegasys.teku.cli.options;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.cli.AbstractBeaconNodeCommandTest;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;

class Eth2NetworkOptionsTest extends AbstractBeaconNodeCommandTest {
  @Test
  void shouldEnableAltairByDefault() {
    final TekuConfiguration config = getTekuConfigurationFromArguments();
    final Spec spec = config.eth2NetworkConfiguration().getSpec();
    assertThat(spec.getForkSchedule().getHighestSupportedMilestone())
        .isEqualTo(SpecMilestone.ALTAIR);
  }

  @Test
  void shouldUseAltairForkBlockIfSpecified() {
    final TekuConfiguration config =
        getTekuConfigurationFromArguments("--Xnetwork-altair-fork-epoch", "64");
    final Spec spec = config.eth2NetworkConfiguration().getSpec();
    assertThat(spec.getForkSchedule().getSpecMilestoneAtEpoch(UInt64.valueOf(63)))
        .isEqualTo(SpecMilestone.PHASE0);
    assertThat(spec.getForkSchedule().getSpecMilestoneAtEpoch(UInt64.valueOf(64)))
        .isEqualTo(SpecMilestone.ALTAIR);
  }
}
