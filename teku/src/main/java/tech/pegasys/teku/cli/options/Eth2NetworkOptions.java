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

import static tech.pegasys.teku.networks.Eth2NetworkConfiguration.DEFAULT_ASYNC_BEACON_CHAIN_MAX_QUEUE;
import static tech.pegasys.teku.networks.Eth2NetworkConfiguration.DEFAULT_ASYNC_BEACON_CHAIN_MAX_THREADS;
import static tech.pegasys.teku.networks.Eth2NetworkConfiguration.DEFAULT_ASYNC_P2P_MAX_QUEUE;
import static tech.pegasys.teku.networks.Eth2NetworkConfiguration.DEFAULT_ASYNC_P2P_MAX_THREADS;
import static tech.pegasys.teku.spec.constants.NetworkConstants.DEFAULT_SAFE_SLOTS_TO_IMPORT_OPTIMISTICALLY;

import java.util.function.Consumer;
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

public class Eth2NetworkOptions {

  @Option(
      names = {"-n", "--network"},
      paramLabel = "<NETWORK>",
      description = "Represents which network to use.",
      arity = "1")
  private String network = "mainnet";

  @Option(
      names = {"--initial-state"},
      paramLabel = "<STRING>",
      description =
          "The initial state. This value should be a file or URL pointing to an SSZ-encoded finalized checkpoint state.",
      arity = "1")
  private String initialState;

  @Option(
      names = {"--ignore-weak-subjectivity-period-enabled"},
      paramLabel = "<BOOLEAN>",
      description = "Allows syncing outside of the weak subjectivity period.",
      arity = "0..1",
      fallbackValue = "true",
      showDefaultValue = Visibility.ALWAYS)
  private boolean ignoreWeakSubjectivityPeriodEnabled = false;

  @Option(
      names = {"--genesis-state"},
      paramLabel = "<STRING>",
      description =
          "The genesis state. This value should be a file or URL pointing to an SSZ-encoded finalized checkpoint state.",
      arity = "1")
  private String genesisState;

  @Option(
      names = {"--checkpoint-sync-url"},
      paramLabel = "<STRING>",
      description = "The Checkpointz server that will be used to bootstrap this node.",
      arity = "1")
  private String checkpointSyncUrl;

  @Option(
      names = {"--eth1-deposit-contract-address"},
      paramLabel = "<ADDRESS>",
      description =
          "Contract address for the deposit contract. Only required when creating a custom network.",
      arity = "1")
  private String eth1DepositContractAddress = null; // Depends on network configuration

  @Option(
      names = {"--Xtrusted-setup"},
      hidden = true,
      paramLabel = "<STRING>",
      description =
          "The trusted setup which is needed for KZG commitments. Only required when creating a custom network. This value should be a file or URL pointing to a trusted setup.",
      arity = "1")
  private String trustedSetup = null; // Depends on network configuration

  @Option(
      names = {"--Xrust-kzg-enabled"},
      paramLabel = "<BOOLEAN>",
      description =
          "Use Rust KZG library LibPeerDASKZG with fallback to CKZG4844 for EIP-4844 methods",
      arity = "0..1",
      fallbackValue = "true",
      showDefaultValue = Visibility.ALWAYS,
      hidden = true)
  private boolean rustKzgEnabled = Eth2NetworkConfiguration.DEFAULT_RUST_KZG_ENABLED;

  @Option(
      names = {"--Xfork-choice-late-block-reorg-enabled"},
      paramLabel = "<BOOLEAN>",
      description = "Allow late blocks to be reorged out if they meet the requirements.",
      arity = "0..1",
      fallbackValue = "true",
      showDefaultValue = Visibility.ALWAYS,
      hidden = true)
  private boolean forkChoiceLateBlockReorgEnabled =
      Eth2NetworkConfiguration.DEFAULT_FORK_CHOICE_LATE_BLOCK_REORG_ENABLED;

  @Option(
      names = {"--Xfork-choice-updated-always-send-payload-attributes"},
      paramLabel = "<BOOLEAN>",
      description =
          "Calculate and send payload attributes on every forkChoiceUpdated regardless if a connected validator is due to be a block proposer or not.",
      arity = "0..1",
      fallbackValue = "true",
      showDefaultValue = Visibility.ALWAYS,
      hidden = true)
  private boolean forkChoiceUpdatedAlwaysSendPayloadAttributes =
      Eth2NetworkConfiguration.DEFAULT_FORK_CHOICE_UPDATED_ALWAYS_SEND_PAYLOAD_ATTRIBUTES;

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
      names = {"--Xnetwork-deneb-fork-epoch"},
      hidden = true,
      paramLabel = "<epoch>",
      description = "Override the deneb fork activation epoch.",
      arity = "1")
  private UInt64 denebForkEpoch;

  @Option(
      names = {"--Xnetwork-das-fork-epoch"},
      hidden = true,
      paramLabel = "<epoch>",
      description = "Override the EIP7594 fork activation epoch.",
      arity = "1")
  private UInt64 eip7594ForkEpoch;

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
      names = {"--Xnetwork-async-p2p-max-threads"},
      hidden = true,
      paramLabel = "<NUMBER>",
      description = "Override the number of threads available to the p2p async runner",
      arity = "1")
  private Integer asyncP2pMaxThreads = DEFAULT_ASYNC_P2P_MAX_THREADS;

  @Option(
      names = {"--Xnetwork-async-p2p-max-queue"},
      hidden = true,
      paramLabel = "<NUMBER>",
      description = "Override the queue size of the p2p async runner",
      arity = "1")
  private Integer asyncP2pMaxQueue = DEFAULT_ASYNC_P2P_MAX_QUEUE;

  @Option(
      names = {"--Xnetwork-async-beaconchain-max-threads"},
      hidden = true,
      paramLabel = "<NUMBER>",
      description = "Override the number of threads available to the beaconchain async runner",
      arity = "1")
  private Integer asyncBeaconChainMaxThreads = DEFAULT_ASYNC_BEACON_CHAIN_MAX_THREADS;

  @Option(
      names = {"--Xnetwork-async-beaconchain-max-queue"},
      hidden = true,
      paramLabel = "<NUMBER>",
      description = "Override the queue size of the beaconchain queue",
      arity = "1")
  private Integer asyncBeaconChainMaxQueue = DEFAULT_ASYNC_BEACON_CHAIN_MAX_QUEUE;

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
      names = {"--Xeth1-deposit-contract-deploy-block-override"},
      hidden = true,
      paramLabel = "<NUMBER>",
      description = "Override deposit contract block number.",
      arity = "1")
  private Long eth1DepositContractDeployBlockOverride;

  @CommandLine.Option(
      names = {"--Xepochs-store-blobs"},
      hidden = true,
      paramLabel = "<STRING>",
      description =
          "Sets the number of epochs blob sidecars are stored and requested during the sync. Use MAX to store all blob sidecars. The value cannot be set to be lower than the spec's MIN_EPOCHS_FOR_BLOB_SIDECARS_REQUESTS.",
      fallbackValue = "",
      showDefaultValue = Visibility.ALWAYS,
      arity = "0..1")
  private String epochsStoreBlobs;

  public Eth2NetworkConfiguration getNetworkConfiguration() {
    return createEth2NetworkConfig(builder -> {});
  }

  public Eth2NetworkConfiguration getNetworkConfiguration(
      final Consumer<Eth2NetworkConfiguration.Builder> modifier) {
    return createEth2NetworkConfig(modifier);
  }

  public void configure(final TekuConfiguration.Builder builder) {
    builder.eth2NetworkConfig(this::configureEth2Network);
  }

  private Eth2NetworkConfiguration createEth2NetworkConfig(
      final Consumer<Eth2NetworkConfiguration.Builder> modifier) {
    Eth2NetworkConfiguration.Builder builder = Eth2NetworkConfiguration.builder();
    configureEth2Network(builder);
    modifier.accept(builder);
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
    if (StringUtils.isNotBlank(checkpointSyncUrl)) {
      builder.checkpointSyncUrl(checkpointSyncUrl);
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
    if (denebForkEpoch != null) {
      builder.denebForkEpoch(denebForkEpoch);
    }
    if (eip7594ForkEpoch != null) {
      builder.eip7594ForkEpoch(eip7594ForkEpoch);
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
    if (trustedSetup != null) {
      builder.trustedSetup(trustedSetup);
    }
    if (eth1DepositContractDeployBlockOverride != null) {
      builder.eth1DepositContractDeployBlock(eth1DepositContractDeployBlockOverride);
    }
    builder
        .ignoreWeakSubjectivityPeriodEnabled(ignoreWeakSubjectivityPeriodEnabled)
        .safeSlotsToImportOptimistically(safeSlotsToImportOptimistically)
        .asyncP2pMaxThreads(asyncP2pMaxThreads)
        .asyncP2pMaxQueue(asyncP2pMaxQueue)
        .asyncBeaconChainMaxThreads(asyncBeaconChainMaxThreads)
        .asyncBeaconChainMaxQueue(asyncBeaconChainMaxQueue)
        .forkChoiceLateBlockReorgEnabled(forkChoiceLateBlockReorgEnabled)
        .epochsStoreBlobs(epochsStoreBlobs)
        .forkChoiceUpdatedAlwaysSendPayloadAttributes(forkChoiceUpdatedAlwaysSendPayloadAttributes)
        .rustKzgEnabled(rustKzgEnabled);
  }

  public String getNetwork() {
    return network;
  }
}
