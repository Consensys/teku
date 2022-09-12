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

package tech.pegasys.teku.cli.options;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import tech.pegasys.teku.cli.AbstractBeaconNodeCommandTest;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networks.Eth2NetworkConfiguration;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.ProgressiveBalancesMode;

class Eth2NetworkOptionsTest extends AbstractBeaconNodeCommandTest {
  @Test
  void shouldEnableBellatrixByDefault() {
    final TekuConfiguration config = getTekuConfigurationFromArguments();
    final Spec spec = config.eth2NetworkConfiguration().getSpec();
    assertThat(spec.getForkSchedule().getHighestSupportedMilestone())
        .isEqualTo(SpecMilestone.BELLATRIX);
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
  void shouldUseBellatrixForkEpochIfSpecified() {
    final TekuConfiguration config =
        getTekuConfigurationFromArguments(
            "--Xnetwork-bellatrix-fork-epoch", "120000", "--ee-endpoint", "someEndpoint");
    final Spec spec = config.eth2NetworkConfiguration().getSpec();
    assertThat(spec.getForkSchedule().getSpecMilestoneAtEpoch(UInt64.valueOf(119999)))
        .isEqualTo(SpecMilestone.ALTAIR);
    assertThat(spec.getForkSchedule().getSpecMilestoneAtEpoch(UInt64.valueOf(120000)))
        .isEqualTo(SpecMilestone.BELLATRIX);
    assertThat(
            createConfigBuilder()
                .executionLayer(b -> b.engineEndpoint("someEndpoint"))
                .eth2NetworkConfig(b -> b.bellatrixForkEpoch(UInt64.valueOf(120000)))
                .build())
        .usingRecursiveComparison()
        .isEqualTo(config);
  }

  @Test
  void shouldUseCustomSafeSlotsToImportOptimistically() {
    final TekuConfiguration config =
        getTekuConfigurationFromArguments(
            "--Xnetwork-bellatrix-fork-epoch",
            "120000",
            "--ee-endpoint",
            "someEndpoint",
            "--Xnetwork-safe-slots-to-import-optimistically",
            "256");
    final Spec spec = config.eth2NetworkConfiguration().getSpec();
    assertThat(
            spec.getGenesisSpecConfig()
                .toVersionBellatrix()
                .orElseThrow()
                .getSafeSlotsToImportOptimistically())
        .isEqualTo(256);
  }

  @ParameterizedTest
  @EnumSource(value = ProgressiveBalancesMode.class)
  void shouldUseCustomProgressiveBalancesMode(final ProgressiveBalancesMode mode) {
    final TekuConfiguration config =
        getTekuConfigurationFromArguments("--Xprogressive-balances-mode", mode.name());
    final Spec spec = config.eth2NetworkConfiguration().getSpec();
    assertThat(spec.getGenesisSpecConfig().getProgressiveBalancesMode()).isEqualTo(mode);
  }

  @ParameterizedTest
  @ValueSource(strings = {"true", "false"})
  void shouldSetFirstDescendent(final String value) {
    final TekuConfiguration config =
        getTekuConfigurationFromArguments("--Xfork-choice-first-descendent-as-head-enabled", value);
    final Spec spec = config.eth2NetworkConfiguration().getSpec();
    assertThat(spec.getGenesisSpecConfig().isFirstDescendentAsChainHeadEnabled())
        .isEqualTo(Boolean.valueOf(value));
  }

  @Test
  void shouldMergeTransitionsOverrideBeEmptyByDefault() {
    final TekuConfiguration config = getTekuConfigurationFromArguments();
    assertThat(config.eth2NetworkConfiguration().getTotalTerminalDifficultyOverride())
        .isEqualTo(Optional.empty());
    assertThat(config.eth2NetworkConfiguration().getTerminalBlockHashOverride())
        .isEqualTo(Optional.empty());
    assertThat(config.eth2NetworkConfiguration().getTerminalBlockHashEpochOverride())
        .isEqualTo(Optional.empty());
  }

  @Test
  void minimalNetwork_shouldMergeTransitionsOverrideBeEmptyByDefault() {
    final TekuConfiguration config = getTekuConfigurationFromArguments("--network", "minimal");
    assertThat(config.eth2NetworkConfiguration().getGenesisState()).isEqualTo(Optional.empty());
  }

  @Test
  void shouldLoadMergeTerminalTotalDifficultyOverride() {
    final TekuConfiguration config =
        getTekuConfigurationFromArguments(
            "--Xnetwork-total-terminal-difficulty-override", "123456789012345678901");
    assertThat(config.eth2NetworkConfiguration().getTotalTerminalDifficultyOverride())
        .isEqualTo(Optional.of(UInt256.valueOf(new BigInteger("123456789012345678901"))));
    assertThat(
            createConfigBuilder()
                .eth2NetworkConfig(
                    b ->
                        b.totalTerminalDifficultyOverride(
                            UInt256.valueOf(new BigInteger("123456789012345678901"))))
                .build())
        .usingRecursiveComparison()
        .isEqualTo(config);
  }

  @Test
  void shouldLoadMergeTerminalBlockHashOverride() {
    final TekuConfiguration config =
        getTekuConfigurationFromArguments(
            "--Xnetwork-terminal-block-hash-override",
            "0x7562f205a2d14e80a3a67da9df0b769b0ba0111a8e81034606f8f27f51f4dd8e");
    assertThat(config.eth2NetworkConfiguration().getTerminalBlockHashOverride())
        .isEqualTo(
            Optional.of(
                Bytes32.fromHexStringStrict(
                    "0x7562f205a2d14e80a3a67da9df0b769b0ba0111a8e81034606f8f27f51f4dd8e")));
    assertThat(
            createConfigBuilder()
                .eth2NetworkConfig(
                    b ->
                        b.terminalBlockHashOverride(
                            Bytes32.fromHexStringStrict(
                                "0x7562f205a2d14e80a3a67da9df0b769b0ba0111a8e81034606f8f27f51f4dd8e")))
                .build())
        .usingRecursiveComparison()
        .isEqualTo(config);
  }

  @Test
  void shouldLoadTerminalBlockHashEpochOverride() {
    final TekuConfiguration config =
        getTekuConfigurationFromArguments(
            "--Xnetwork-terminal-block-hash-epoch-override", "120000");
    assertThat(config.eth2NetworkConfiguration().getTerminalBlockHashEpochOverride())
        .isEqualTo(Optional.of(UInt64.valueOf(120000)));
    assertThat(
            createConfigBuilder()
                .eth2NetworkConfig(b -> b.terminalBlockHashEpochOverride(UInt64.valueOf(120000)))
                .build())
        .usingRecursiveComparison()
        .isEqualTo(config);
  }

  @Test
  void shouldFailLoadingInvalidTransitionOverrides() {
    assertThrows(
        AssertionError.class,
        () ->
            getTekuConfigurationFromArguments(
                "--Xnetwork-total-terminal-difficulty-override", "asd"));

    assertThrows(
        AssertionError.class,
        () -> getTekuConfigurationFromArguments("--Xnetwork-terminal-block-hash-override", "756"));

    assertThrows(
        AssertionError.class,
        () ->
            getTekuConfigurationFromArguments(
                "--Xnetwork-terminal-block-hash-epoch-override", "asd"));
  }

  @Test
  void shouldEnableEquivocatingIndices() {
    final Eth2NetworkConfiguration networkConfig =
        getTekuConfigurationFromArguments("--Xfork-choice-equivocating-indices-enabled")
            .eth2NetworkConfiguration();
    assertThat(networkConfig.isEquivocatingIndicesEnabled()).isTrue();
  }

  @Test
  void shouldDisableEquivocatingIndices() {
    final Eth2NetworkConfiguration networkConfig =
        getTekuConfigurationFromArguments("--Xfork-choice-equivocating-indices-enabled=false")
            .eth2NetworkConfiguration();
    assertThat(networkConfig.isEquivocatingIndicesEnabled()).isFalse();
  }

  @Test
  void shouldEnableProposerBoost() {
    final Eth2NetworkConfiguration networkConfig =
        getTekuConfigurationFromArguments("--Xfork-choice-proposer-boost-enabled")
            .eth2NetworkConfiguration();
    assertThat(networkConfig.isProposerBoostEnabled()).isTrue();
  }

  @Test
  void shouldDisableProposerBoost() {
    final Eth2NetworkConfiguration networkConfig =
        getTekuConfigurationFromArguments("--Xfork-choice-proposer-boost-enabled=false")
            .eth2NetworkConfiguration();
    assertThat(networkConfig.isProposerBoostEnabled()).isFalse();
  }

  @Test
  void shouldEnableForkChoiceBeforeProposing() {
    final Eth2NetworkConfiguration networkConfig =
        getTekuConfigurationFromArguments("--Xfork-choice-before-proposing-enabled")
            .eth2NetworkConfiguration();
    assertThat(networkConfig.isForkChoiceBeforeProposingEnabled()).isTrue();
  }

  @Test
  void shouldDisableForkChoiceBeforeProposing() {
    final Eth2NetworkConfiguration networkConfig =
        getTekuConfigurationFromArguments("--Xfork-choice-before-proposing-enabled=false")
            .eth2NetworkConfiguration();
    assertThat(networkConfig.isForkChoiceBeforeProposingEnabled()).isFalse();
  }

  @Test
  void shouldLoadGenesisState() {
    final String genesisState =
        "https://221EMZ2YSdriVVdXx:5058f100c7@eth2-beacon-mainnet.infura.io/eth/v1/debug/beacon/states/finalized";
    final TekuConfiguration config =
        getTekuConfigurationFromArguments("--Xgenesis-state", genesisState);
    assertThat(config.eth2NetworkConfiguration().getGenesisState())
        .isEqualTo(Optional.of(genesisState));
    assertThat(
            createConfigBuilder()
                .eth2NetworkConfig(b -> b.customGenesisState(genesisState))
                .build())
        .usingRecursiveComparison()
        .isEqualTo(config);
  }
}
