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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
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
  void shouldUseAltairForkEpochIfSpecified() {
    final TekuConfiguration config =
        getTekuConfigurationFromArguments("--Xnetwork-altair-fork-epoch", "64");
    final Spec spec = config.eth2NetworkConfiguration().getSpec();
    assertThat(spec.getForkSchedule().getSpecMilestoneAtEpoch(UInt64.valueOf(63)))
        .isEqualTo(SpecMilestone.PHASE0);
    assertThat(spec.getForkSchedule().getSpecMilestoneAtEpoch(UInt64.valueOf(64)))
        .isEqualTo(SpecMilestone.ALTAIR);
    assertThat(
            createConfigBuilder()
                .eth2NetworkConfig(b -> b.altairForkEpoch(UInt64.valueOf(64)))
                .build())
        .usingRecursiveComparison()
        .isEqualTo(config);
  }

  @Test
  void shouldUseMergeForkEpochIfSpecified() {
    final TekuConfiguration config =
        getTekuConfigurationFromArguments(
            "--Xnetwork-merge-fork-epoch", "120000", "--Xee-endpoint", "someEndpoint");
    final Spec spec = config.eth2NetworkConfiguration().getSpec();
    assertThat(spec.getForkSchedule().getSpecMilestoneAtEpoch(UInt64.valueOf(119999)))
        .isEqualTo(SpecMilestone.ALTAIR);
    assertThat(spec.getForkSchedule().getSpecMilestoneAtEpoch(UInt64.valueOf(120000)))
        .isEqualTo(SpecMilestone.MERGE);
    assertThat(
            createConfigBuilder()
                .executionEngine(b -> b.endpoint("someEndpoint"))
                .eth2NetworkConfig(b -> b.mergeForkEpoch(UInt64.valueOf(120000)))
                .build())
        .usingRecursiveComparison()
        .isEqualTo(config);
  }

  @Test
  void shouldMergeTransitionsOverrideBeEmptyByDefault() {
    final TekuConfiguration config = getTekuConfigurationFromArguments();
    assertThat(config.eth2NetworkConfiguration().getMergeTotalTerminalDifficultyOverride())
        .isEqualTo(Optional.empty());
    assertThat(config.eth2NetworkConfiguration().getMergeTerminalBlockHashOverride())
        .isEqualTo(Optional.empty());
    assertThat(config.eth2NetworkConfiguration().getMergeTerminalBlockHashEpochOverride())
        .isEqualTo(Optional.empty());
  }

  @Test
  void shouldLoadMergeTerminalTotalDifficultyOverride() {
    final TekuConfiguration config =
        getTekuConfigurationFromArguments(
            "--Xnetwork-merge-total-terminal-difficulty-override", "123456789012345678901");
    assertThat(config.eth2NetworkConfiguration().getMergeTotalTerminalDifficultyOverride())
        .isEqualTo(Optional.of(UInt256.valueOf(new BigInteger("123456789012345678901"))));
    assertThat(
            createConfigBuilder()
                .eth2NetworkConfig(
                    b ->
                        b.mergeTotalTerminalDifficultyOverride(
                            UInt256.valueOf(new BigInteger("123456789012345678901"))))
                .build())
        .usingRecursiveComparison()
        .isEqualTo(config);
  }

  @Test
  void shouldLoadMergeTerminalBlockHashOverride() {
    final TekuConfiguration config =
        getTekuConfigurationFromArguments(
            "--Xnetwork-merge-terminal-block-hash-override",
            "0x7562f205a2d14e80a3a67da9df0b769b0ba0111a8e81034606f8f27f51f4dd8e");
    assertThat(config.eth2NetworkConfiguration().getMergeTerminalBlockHashOverride())
        .isEqualTo(
            Optional.of(
                Bytes32.fromHexStringStrict(
                    "0x7562f205a2d14e80a3a67da9df0b769b0ba0111a8e81034606f8f27f51f4dd8e")));
    assertThat(
            createConfigBuilder()
                .eth2NetworkConfig(
                    b ->
                        b.mergeTerminalBlockHashOverride(
                            Bytes32.fromHexStringStrict(
                                "0x7562f205a2d14e80a3a67da9df0b769b0ba0111a8e81034606f8f27f51f4dd8e")))
                .build())
        .usingRecursiveComparison()
        .isEqualTo(config);
  }

  @Test
  void shouldLoadMergeTerminalBlockHashEpochOverride() {
    final TekuConfiguration config =
        getTekuConfigurationFromArguments(
            "--Xnetwork-merge-terminal-block-hash-epoch-override", "120000");
    assertThat(config.eth2NetworkConfiguration().getMergeTerminalBlockHashEpochOverride())
        .isEqualTo(Optional.of(UInt64.valueOf(120000)));
    assertThat(
            createConfigBuilder()
                .eth2NetworkConfig(
                    b -> b.mergeTerminalBlockHashEpochOverride(UInt64.valueOf(120000)))
                .build())
        .usingRecursiveComparison()
        .isEqualTo(config);
  }

  @Test
  void shouldFailLoadingInvalidMergeTransitionOverrides() {
    assertThrows(
        AssertionError.class,
        () -> {
          getTekuConfigurationFromArguments(
              "--Xnetwork-merge-total-terminal-difficulty-override", "asd");
        });

    assertThrows(
        AssertionError.class,
        () -> {
          getTekuConfigurationFromArguments("--Xnetwork-merge-terminal-block-hash-override", "756");
        });

    assertThrows(
        AssertionError.class,
        () -> {
          getTekuConfigurationFromArguments(
              "--Xnetwork-merge-terminal-block-hash-epoch-override", "asd");
        });
  }
}
