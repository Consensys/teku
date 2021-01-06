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

import picocli.CommandLine.Option;
import tech.pegasys.teku.cli.converter.Eth2NetworkConverter;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.networks.Eth2NetworkConfiguration;

public class Eth2NetworkOptions {

  @Option(
      converter = Eth2NetworkConverter.class,
      names = {"-n", "--network"},
      paramLabel = "<NETWORK>",
      description = "Represents which network to use.",
      arity = "1",
      defaultValue = "mainnet")
  private Eth2NetworkConfiguration.Builder network = Eth2NetworkConfiguration.builder("mainnet");

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
  private Integer peerRateLimit = 500;

  @Option(
      names = {"--Xpeer-request-limit"},
      paramLabel = "<NUMBER>",
      description =
          "The number of requests per peer to allow per minute before disconnecting the peer.",
      arity = "1",
      hidden = true)
  private Integer peerRequestLimit = 50;

  public Eth2NetworkConfiguration getNetworkConfiguration() {
    return getEth2NetworkConfig().build();
  }

  public Integer getPeerRateLimit() {
    return peerRateLimit;
  }

  public Integer getPeerRequestLimit() {
    return peerRequestLimit;
  }

  public void configure(final TekuConfiguration.Builder builder) {
    final Eth2NetworkConfiguration.Builder eth2Config = getEth2NetworkConfig();

    builder
        .eth2NetworkConfig(eth2Config)
        .storageConfiguration(b -> b.eth1DepositContract(eth2Config.eth1DepositContractAddress()))
        .p2p(b -> b.p2pDiscoveryBootnodes(eth2Config.discoveryBootnodes()))
        .restApi(b -> b.eth1DepositContractAddress(eth2Config.eth1DepositContractAddress()))
        .weakSubjectivity(
            b -> eth2Config.initialState().ifPresent(b::weakSubjectivityStateResource));
  }

  private Eth2NetworkConfiguration.Builder getEth2NetworkConfig() {
    if (startupTargetPeerCount != null) {
      network.startupTargetPeerCount(startupTargetPeerCount);
    }
    if (startupTimeoutSeconds != null) {
      network.startupTimeoutSeconds(startupTimeoutSeconds);
    }
    if (eth1DepositContractAddress != null) {
      network.eth1DepositContractAddress(eth1DepositContractAddress);
    }
    return network;
  }
}
