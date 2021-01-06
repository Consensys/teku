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

import static tech.pegasys.teku.infrastructure.logging.StatusLogger.STATUS_LOG;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import picocli.CommandLine.Option;
import tech.pegasys.teku.config.TekuConfiguration;

public class P2POptions {

  @Option(
      names = {"--p2p-enabled"},
      paramLabel = "<BOOLEAN>",
      description = "Enables P2P",
      fallbackValue = "true",
      arity = "0..1")
  private boolean p2pEnabled = true;

  @Option(
      names = {"--p2p-interface"},
      paramLabel = "<NETWORK>",
      description = "P2P network interface",
      arity = "1")
  private String p2pInterface = "0.0.0.0";

  @Option(
      names = {"--p2p-port"},
      paramLabel = "<INTEGER>",
      description = "P2P port",
      arity = "1")
  private int p2pPort = 9000;

  @Option(
      names = {"--p2p-discovery-enabled"},
      paramLabel = "<BOOLEAN>",
      description = "Enables discv5 discovery",
      fallbackValue = "true",
      arity = "0..1")
  private boolean p2pDiscoveryEnabled = true;

  @Option(
      names = {"--p2p-discovery-bootnodes"},
      paramLabel = "<enr:-...>",
      description = "List of ENRs of the bootnodes",
      split = ",",
      arity = "0..*")
  private List<String> p2pDiscoveryBootnodes = null;

  @Option(
      names = {"--p2p-advertised-ip"},
      paramLabel = "<NETWORK>",
      description = "P2P advertised IP",
      arity = "1")
  private String p2pAdvertisedIp;

  @Option(
      names = {"--p2p-advertised-port"},
      paramLabel = "<INTEGER>",
      description = "P2P advertised port",
      arity = "1")
  private Integer p2pAdvertisedPort;

  @Option(
      names = {"--p2p-private-key-file"},
      paramLabel = "<FILENAME>",
      description = "This node's private key file",
      arity = "1")
  private String p2pPrivateKeyFile = null;

  @Option(
      names = {"--p2p-peer-lower-bound"},
      paramLabel = "<INTEGER>",
      description = "Lower bound on the target number of peers",
      arity = "1")
  private int p2pLowerBound = 64;

  @Option(
      names = {"--p2p-peer-upper-bound"},
      paramLabel = "<INTEGER>",
      description = "Upper bound on the target number of peers",
      arity = "1")
  private int p2pUpperBound = 74;

  @Option(
      names = {"--Xp2p-target-subnet-subscriber-count"},
      paramLabel = "<INTEGER>",
      description = "Target number of peers subscribed to each attestation subnet",
      arity = "1",
      hidden = true)
  private int p2pTargetSubnetSubscriberCount = 2;

  @Option(
      names = {"--Xp2p-minimum-randomly-selected-peer-count"},
      paramLabel = "<INTEGER>",
      description =
          "Number of peers that should be selected randomly (default 20% of lower-bound target)",
      arity = "1",
      hidden = true)
  private Integer minimumRandomlySelectedPeerCount;

  @Option(
      names = {"--p2p-static-peers"},
      paramLabel = "<PEER_ADDRESSES>",
      description = "Static peers",
      split = ",",
      arity = "0..*")
  private List<String> p2pStaticPeers = new ArrayList<>();

  @Option(
      names = {"--Xp2p-multipeer-sync-enabled"},
      paramLabel = "<BOOLEAN>",
      description = "Enables experimental multipeer sync",
      hidden = true,
      arity = "1")
  private boolean multiPeerSyncEnabled = false;

  @Option(
      names = {"--p2p-subscribe-all-subnets-enabled"},
      paramLabel = "<BOOLEAN>",
      description = "",
      arity = "1",
      fallbackValue = "false")
  private boolean subscribeAllSubnetsEnabled = false;

  private int getP2pLowerBound() {
    if (p2pLowerBound > p2pUpperBound) {
      STATUS_LOG.adjustingP2pLowerBoundToUpperBound(p2pUpperBound);
      return p2pUpperBound;
    } else {
      return p2pLowerBound;
    }
  }

  private int getP2pUpperBound() {
    if (p2pUpperBound < p2pLowerBound) {
      STATUS_LOG.adjustingP2pUpperBoundToLowerBound(p2pLowerBound);
      return p2pLowerBound;
    } else {
      return p2pUpperBound;
    }
  }

  private int getMinimumRandomlySelectedPeerCount() {
    return minimumRandomlySelectedPeerCount == null
        ? Math.max(1, getP2pLowerBound() * 2 / 10)
        : minimumRandomlySelectedPeerCount;
  }

  public void configure(final TekuConfiguration.Builder builder) {
    builder.p2p(
        p2pBuilder -> {
          if (p2pDiscoveryBootnodes != null) {
            p2pBuilder.p2pDiscoveryBootnodes(p2pDiscoveryBootnodes);
          }

          p2pBuilder
              .p2pEnabled(p2pEnabled)
              .p2pInterface(p2pInterface)
              .p2pPort(p2pPort)
              .p2pDiscoveryEnabled(p2pDiscoveryEnabled)
              .p2pAdvertisedIp(Optional.ofNullable(p2pAdvertisedIp))
              .p2pAdvertisedPort(
                  p2pAdvertisedPort == null
                      ? OptionalInt.empty()
                      : OptionalInt.of(p2pAdvertisedPort))
              .p2pPrivateKeyFile(p2pPrivateKeyFile)
              .p2pPeerLowerBound(getP2pLowerBound())
              .p2pPeerUpperBound(getP2pUpperBound())
              .targetSubnetSubscriberCount(p2pTargetSubnetSubscriberCount)
              .minimumRandomlySelectedPeerCount(getMinimumRandomlySelectedPeerCount())
              .p2pStaticPeers(p2pStaticPeers)
              .multiPeerSyncEnabled(multiPeerSyncEnabled)
              .subscribeAllSubnetsEnabled(subscribeAllSubnetsEnabled);
        });
  }
}
