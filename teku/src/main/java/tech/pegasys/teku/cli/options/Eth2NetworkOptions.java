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

import static tech.pegasys.teku.networking.eth2.P2PConfig.Builder.DEFAULT_PEER_RATE_LIMIT;
import static tech.pegasys.teku.networking.eth2.P2PConfig.Builder.DEFAULT_PEER_REQUEST_LIMIT;

import picocli.CommandLine.Option;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.networks.Eth2NetworkConfiguration;

public class Eth2NetworkOptions {

  @Option(
      names = {"-n", "--network"},
      paramLabel = "<NETWORK>",
      description = "Represents which network to use.",
      arity = "1")
  private String network = "mainnet";

  @Option(
      names = {"--eth1-deposit-contract-address"},
      paramLabel = "<ADDRESS>",
      description =
          "Contract address for the deposit contract. Only required when creating a custom network.",
      arity = "1")
  private String eth1DepositContractAddress = null; // Depends on network configuration

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
      names = {"--Xpeer-rate-limit"},
      paramLabel = "<NUMBER>",
      description =
          "The number of requested objects per peer to allow per minute before disconnecting the peer.",
      arity = "1",
      hidden = true)
  private Integer peerRateLimit = DEFAULT_PEER_RATE_LIMIT;

  @Option(
      names = {"--Xpeer-request-limit"},
      paramLabel = "<NUMBER>",
      description =
          "The number of requests per peer to allow per minute before disconnecting the peer.",
      arity = "1",
      hidden = true)
  private Integer peerRequestLimit = DEFAULT_PEER_REQUEST_LIMIT;

  public Eth2NetworkConfiguration getNetworkConfiguration() {
    return createEth2NetworkConfig();
  }

  public void configure(final TekuConfiguration.Builder builder) {
    // Create a config instance so we can inspect the values in the other builders
    final Eth2NetworkConfiguration eth2Config = createEth2NetworkConfig();

    builder
        .eth2NetworkConfig(this::configureEth2Network)
        .powchain(
            b -> {
              eth2Config.getEth1DepositContractAddress().ifPresent(b::depositContract);
              b.depositContractDeployBlock(eth2Config.getEth1DepositContractDeployBlock());
            })
        .storageConfiguration(
            b -> b.eth1DepositContract(eth2Config.getEth1DepositContractAddress()))
        .p2p(b -> b.peerRateLimit(peerRateLimit).peerRequestLimit(peerRequestLimit))
        .discovery(b -> b.bootnodes(eth2Config.getDiscoveryBootnodes()))
        .restApi(b -> b.eth1DepositContractAddress(eth2Config.getEth1DepositContractAddress()))
        .weakSubjectivity(
            b -> eth2Config.getInitialState().ifPresent(b::weakSubjectivityStateResource));
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
  }
}
