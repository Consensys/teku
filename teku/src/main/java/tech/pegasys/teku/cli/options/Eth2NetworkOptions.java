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

import static tech.pegasys.teku.spec.constants.NetworkConstants.DEFAULT_SAFE_SLOTS_TO_IMPORT_OPTIMISTICALLY;

import org.apache.commons.lang3.StringUtils;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import picocli.CommandLine;
import picocli.CommandLine.Help.Visibility;
import picocli.CommandLine.Option;
import tech.pegasys.teku.cli.converter.Bytes32Converter;
import tech.pegasys.teku.cli.converter.UInt256Converter;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networks.Eth2NetworkConfiguration;
import tech.pegasys.teku.spec.config.ProgressiveBalancesMode;

public class Eth2NetworkOptions {

  @Option(
      names = {"-n", "--network"},
      paramLabel = "<NETWORK>",
      description = "Represents which network to use.",
      arity = "1")
  private String network = "mainnet";

  @CommandLine.Option(
      names = {"--initial-state"},
      paramLabel = "<STRING>",
      description =
          "The initial state. This value should be a file or URL pointing to an SSZ-encoded finalized checkpoint state.",
      arity = "1")
  private String initialState;

  @CommandLine.Option(
      names = {"--Xgenesis-state"},
      hidden = true,
      paramLabel = "<STRING>",
      description =
          "The genesis state. This value should be a file or URL pointing to an SSZ-encoded finalized checkpoint state.",
      arity = "1")
  private String genesisState;

  @Option(
      names = {"--eth1-deposit-contract-address"},
      paramLabel = "<ADDRESS>",
      description =
          "Contract address for the deposit contract. Only required when creating a custom network.",
      arity = "1")
  private String eth1DepositContractAddress = null; // Depends on network configuration

  @Option(
      names = {"--Xnetwork-altair-fork-epoch"},
      hidden = true,
      paramLabel = "<epoch>",
      description = "Override the Altair fork activation epoch.",
      arity = "1")
  private UInt64 altairForkEpoch;

  @Option(
      names = {"--Xnetwork-bellatrix-fork-epoch"},
      hidden = true,
      paramLabel = "<epoch>",
      description = "Override the Bellatrix fork activation epoch.",
      arity = "1")
  private UInt64 bellatrixForkEpoch;

  @Option(
      names = {"--Xnetwork-capella-fork-epoch"},
      hidden = true,
      paramLabel = "<epoch>",
      description = "Override the capella fork activation epoch.",
      arity = "1")
  private UInt64 capellaForkEpoch;

  @Option(
      names = {"--Xnetwork-total-terminal-difficulty-override"},
      hidden = true,
      paramLabel = "<uint256>",
      description = "Override total terminal difficulty for The Merge",
      arity = "1",
      converter = UInt256Converter.class)
  private UInt256 totalTerminalDifficultyOverride;

  @Option(
      names = {"--Xnetwork-terminal-block-hash-override"},
      hidden = true,
      paramLabel = "<Bytes32 hex>",
      description =
          "Override terminal block hash for The Merge. To be used in conjunction with --Xnetwork-bellatrix-terminal-block-hash-epoch-override",
      arity = "1",
      converter = Bytes32Converter.class)
  private Bytes32 terminalBlockHashOverride;

  @Option(
      names = {"--Xnetwork-terminal-block-hash-epoch-override"},
      hidden = true,
      paramLabel = "<epoch>",
      description =
          "Override terminal block hash for The Merge. To be used in conjunction with --Xnetwork-bellatrix-terminal-block-hash-override",
      arity = "1")
  private UInt64 terminalBlockHashEpochOverride;

  @Option(
      names = {"--Xnetwork-safe-slots-to-import-optimistically"},
      hidden = true,
      paramLabel = "<NUMBER>",
      description =
          "Override the the number of slots that must pass before it is considered safe to optimistically import a block",
      arity = "1")
  private Integer safeSlotsToImportOptimistically = DEFAULT_SAFE_SLOTS_TO_IMPORT_OPTIMISTICALLY;

  @Option(
      names = {"--Xstartup-target-peer-count"},
      paramLabel = "<NUMBER>",
      description = "Number of peers to wait for before considering the node in sync.",
      hidden = true)
  private Integer startupTargetPeerCount;

  @Option(
      names = {"--Xstartup-timeout-seconds"},
      paramLabel = "<NUMBER>",
      description =
          "Timeout in seconds to allow the node to be in sync even if startup target peer count has not yet been reached.",
      hidden = true)
  private Integer startupTimeoutSeconds;

  @Option(
      names = {"--Xprogressive-balances-mode"},
      paramLabel = "<MODE>",
      description = "Progressive balance optimization mode to use.",
      arity = "1",
      hidden = true)
  private ProgressiveBalancesMode progressiveBalancesMode = null;

  // can be removed after investigating the consequences of not doing it anymore
  @Option(
      names = {"--Xfork-choice-update-head-on-block-import-enabled"},
      paramLabel = "<BOOLEAN>",
      description = "Make the first descendent of head the new chain head.",
      arity = "0..1",
      fallbackValue = "true",
      showDefaultValue = Visibility.ALWAYS,
      hidden = true)
  private boolean forkChoiceUpdateHeadOnBlockImportEnabled =
      Eth2NetworkConfiguration.DEFAULT_FORK_CHOICE_UPDATE_HEAD_ON_BLOCK_IMPORT_ENABLED;

  public Eth2NetworkConfiguration getNetworkConfiguration() {
    return createEth2NetworkConfig();
  }

  public void configure(final TekuConfiguration.Builder builder) {
    builder.eth2NetworkConfig(this::configureEth2Network);
  }

  private Eth2NetworkConfiguration createEth2NetworkConfig() {
    Eth2NetworkConfiguration.Builder builder = Eth2NetworkConfiguration.builder();
    configureEth2Network(builder);
    return builder.build();
  }

  private void configureEth2Network(Eth2NetworkConfiguration.Builder builder) {
    builder.applyNetworkDefaults(network);
    if (startupTargetPeerCount != null) {
      builder.startupTargetPeerCount(startupTargetPeerCount);
    }
    if (startupTimeoutSeconds != null) {
      builder.startupTimeoutSeconds(startupTimeoutSeconds);
    }
    if (eth1DepositContractAddress != null) {
      builder.eth1DepositContractAddress(eth1DepositContractAddress);
    }
    if (StringUtils.isNotBlank(initialState)) {
      builder.customInitialState(initialState);
    }
    if (StringUtils.isNotBlank(genesisState)) {
      builder.customGenesisState(genesisState);
    }
    if (altairForkEpoch != null) {
      builder.altairForkEpoch(altairForkEpoch);
    }
    if (bellatrixForkEpoch != null) {
      builder.bellatrixForkEpoch(bellatrixForkEpoch);
    }
    if (capellaForkEpoch != null) {
      builder.capellaForkEpoch(capellaForkEpoch);
    }
    if (totalTerminalDifficultyOverride != null) {
      builder.totalTerminalDifficultyOverride(totalTerminalDifficultyOverride);
    }
    if (terminalBlockHashOverride != null) {
      builder.terminalBlockHashOverride(terminalBlockHashOverride);
    }
    if (terminalBlockHashEpochOverride != null) {
      builder.terminalBlockHashEpochOverride(terminalBlockHashEpochOverride);
    }
    if (progressiveBalancesMode != null) {
      builder.progressiveBalancesEnabled(progressiveBalancesMode);
    }
    builder
        .safeSlotsToImportOptimistically(safeSlotsToImportOptimistically)
        .forkChoiceUpdateHeadOnBlockImportEnabled(forkChoiceUpdateHeadOnBlockImportEnabled);
  }

  public String getNetwork() {
    return network;
  }
}
