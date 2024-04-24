/*
 * Copyright Consensys Software Inc., 2022
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static tech.pegasys.teku.networks.Eth2NetworkConfiguration.FINALIZED_STATE_URL_PATH;
import static tech.pegasys.teku.networks.Eth2NetworkConfiguration.GENESIS_STATE_URL_PATH;

import java.math.BigInteger;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tech.pegasys.teku.cli.AbstractBeaconNodeCommandTest;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networks.Eth2NetworkConfiguration;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;

class Eth2NetworkOptionsTest extends AbstractBeaconNodeCommandTest {
  @Test
  void shouldEnableDenebByDefault() {
    final TekuConfiguration config = getTekuConfigurationFromArguments();
    final Spec spec = config.eth2NetworkConfiguration().getSpec();
    assertThat(spec.getForkSchedule().getHighestSupportedMilestone())
        .isEqualTo(SpecMilestone.DENEB);
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
  }

  @Test
  void shouldUseTrustedSetupIfSpecified() {
    final TekuConfiguration config =
        getTekuConfigurationFromArguments("--Xtrusted-setup", "/test.txt");
    final Optional<String> trustedSetup = config.eth2NetworkConfiguration().getTrustedSetup();
    assertThat(trustedSetup).isEqualTo(Optional.of("/test.txt"));
  }

  @Test
  void shouldUseDefaultTrustedSetupIfUnspecified() {
    final TekuConfiguration config = getTekuConfigurationFromArguments();
    final Optional<String> trustedSetup = config.eth2NetworkConfiguration().getTrustedSetup();
    assertThat(trustedSetup)
        .matches(ts -> ts.map(path -> path.endsWith("mainnet-trusted-setup.txt")).orElse(false));
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
  @ValueSource(strings = {"true", "false"})
  void shouldSetLateBlockImportEnabled(final String value) {
    final TekuConfiguration config =
        getTekuConfigurationFromArguments("--Xfork-choice-late-block-reorg-enabled", value);
    assertThat(config.eth2NetworkConfiguration().isForkChoiceLateBlockReorgEnabled())
        .isEqualTo(Boolean.valueOf(value));
  }

  @Test
  void shouldUseDefaultAlwaysSendPayloadAttributesIfUnspecified() {
    final TekuConfiguration config = getTekuConfigurationFromArguments();
    assertThat(config.eth2NetworkConfiguration().isForkChoiceUpdatedAlwaysSendPayloadAttributes())
        .isEqualTo(false);
  }

  @Test
  void shouldUseAlwaysSendPayloadAttributesIfSpecified() {
    final TekuConfiguration config =
        getTekuConfigurationFromArguments(
            "--Xfork-choice-updated-always-send-payload-attributes", "true");
    assertThat(config.eth2NetworkConfiguration().isForkChoiceUpdatedAlwaysSendPayloadAttributes())
        .isEqualTo(true);
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
    assertThat(config.eth2NetworkConfiguration().getNetworkBoostrapConfig().getGenesisState())
        .isEqualTo(Optional.empty());
  }

  @Test
  void shouldLoadMergeTerminalTotalDifficultyOverride() {
    final TekuConfiguration config =
        getTekuConfigurationFromArguments(
            "--Xnetwork-total-terminal-difficulty-override", "123456789012345678901");
    assertThat(config.eth2NetworkConfiguration().getTotalTerminalDifficultyOverride())
        .isEqualTo(Optional.of(UInt256.valueOf(new BigInteger("123456789012345678901"))));
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
  }

  @Test
  void shouldLoadTerminalBlockHashEpochOverride() {
    final TekuConfiguration config =
        getTekuConfigurationFromArguments(
            "--Xnetwork-terminal-block-hash-epoch-override", "120000");
    assertThat(config.eth2NetworkConfiguration().getTerminalBlockHashEpochOverride())
        .isEqualTo(Optional.of(UInt64.valueOf(120000)));
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
  void shouldLoadGenesisState() {
    final String genesisState =
        "https://221EMZ2YSdriVVdXx:5058f100c7@eth2-beacon-mainnet.infura.io/eth/v1/debug/beacon/states/finalized";
    final TekuConfiguration config =
        getTekuConfigurationFromArguments("--genesis-state", genesisState);
    assertThat(config.eth2NetworkConfiguration().getNetworkBoostrapConfig().getGenesisState())
        .isEqualTo(Optional.of(genesisState));
  }

  @ParameterizedTest
  @ValueSource(strings = {"http://foo:9000", "http://foo:9000/"})
  public void checkpointSyncUrlOptionShouldSetInitialAndGenesisStateOptions(
      final String beaconApiEndpoint) {
    final TekuConfiguration config =
        getTekuConfigurationFromArguments("--checkpoint-sync-url", beaconApiEndpoint);

    final Eth2NetworkConfiguration networkConfiguration = config.eth2NetworkConfiguration();

    assertThat(networkConfiguration.getNetworkBoostrapConfig().getGenesisState())
        .hasValue("http://foo:9000/" + GENESIS_STATE_URL_PATH);
    assertThat(networkConfiguration.getNetworkBoostrapConfig().getInitialState())
        .hasValue("http://foo:9000/" + FINALIZED_STATE_URL_PATH);
  }

  @Test
  public void shouldShowGoerliDeprecationWarning() {
    assertThatThrownBy(() -> getTekuConfigurationFromArguments("--network", "goerli"))
        .isInstanceOf(AssertionError.class) // thrown because we had an error
        .hasMessageContaining("Goerli support has been removed");
  }
}
