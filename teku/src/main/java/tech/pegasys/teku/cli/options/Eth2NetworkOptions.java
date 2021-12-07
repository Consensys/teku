/*
 * Copyright 2020 ConsenSys AG.
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

import org.apache.commons.lang3.StringUtils;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import tech.pegasys.teku.cli.converter.Bytes32Converter;
import tech.pegasys.teku.cli.converter.UInt256Converter;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networks.Eth2NetworkConfiguration;

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
      names = {"--Xnetwork-merge-fork-epoch"},
      hidden = true,
      paramLabel = "<epoch>",
      description = "Override the Merge fork activation epoch.",
      arity = "1")
  private UInt64 mergeForkEpoch;

  @Option(
      names = {"--Xnetwork-merge-total-terminal-difficulty-override"},
      hidden = true,
      paramLabel = "<uint256>",
      description = "Override total terminal difficulty for The Merge",
      arity = "1",
      converter = UInt256Converter.class)
  private UInt256 mergeTotalTerminalDifficultyOverride;

  @Option(
      names = {"--Xnetwork-merge-terminal-block-hash-override"},
      hidden = true,
      paramLabel = "<Bytes32 hex>",
      description =
          "Override terminal block hash for The Merge. To be used in conjunction with --Xnetwork-merge-terminal-block-hash-epoch-override",
      arity = "1",
      converter = Bytes32Converter.class)
  private Bytes32 mergeTerminalBlockHashOverride;

  @Option(
      names = {"--Xnetwork-merge-terminal-block-hash-epoch-override"},
      hidden = true,
      paramLabel = "<epoch>",
      description =
          "Override terminal block hash for The Merge. To be used in conjunction with --Xnetwork-merge-terminal-block-hash-override",
      arity = "1")
  private UInt64 mergeTerminalBlockHashEpochOverride;

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
      names = {"--Xfork-choice-proposer-boost-enabled"},
      paramLabel = "<BOOLEAN>",
      description = "Whether to enable the fork choice proposer boost feature.",
      arity = "0..1",
      fallbackValue = "false",
      hidden = true)
  private Boolean proposerBoostEnabled = null;

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
    if (proposerBoostEnabled != null) {
      builder.proposerBoostEnabled(proposerBoostEnabled);
    }
    if (altairForkEpoch != null) {
      builder.altairForkEpoch(altairForkEpoch);
    }
    if (mergeForkEpoch != null) {
      builder.mergeForkEpoch(mergeForkEpoch);
    }
    if (mergeTotalTerminalDifficultyOverride != null) {
      builder.mergeTotalTerminalDifficultyOverride(mergeTotalTerminalDifficultyOverride);
    }
    if (mergeTerminalBlockHashOverride != null) {
      builder.mergeTerminalBlockHashOverride(mergeTerminalBlockHashOverride);
    }
    if (mergeTerminalBlockHashEpochOverride != null) {
      builder.mergeTerminalBlockHashEpochOverride(mergeTerminalBlockHashEpochOverride);
    }
  }

  public String getNetwork() {
    return network;
  }
}
