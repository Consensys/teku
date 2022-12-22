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

import static tech.pegasys.teku.infrastructure.logging.StatusLogger.STATUS_LOG;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import picocli.CommandLine.Help.Visibility;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import tech.pegasys.teku.beacon.sync.SyncConfig;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.networking.eth2.P2PConfig;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryConfig;
import tech.pegasys.teku.networking.p2p.network.config.NetworkConfig;

public class P2POptions {

  @Mixin private NatOptions natOptions = new NatOptions();

  @Option(
      names = {"--p2p-enabled"},
      paramLabel = "<BOOLEAN>",
      showDefaultValue = Visibility.ALWAYS,
      description = "Enables P2P",
      fallbackValue = "true",
      arity = "0..1")
  private boolean p2pEnabled = true;

  @Option(
      names = {"--p2p-interface"},
      paramLabel = "<NETWORK>",
      description = "P2P network interface",
      arity = "1")
  private String p2pInterface = NetworkConfig.DEFAULT_P2P_INTERFACE;

  @Option(
      names = {"--p2p-port"},
      paramLabel = "<INTEGER>",
      description = "P2P port",
      arity = "1")
  private int p2pPort = NetworkConfig.DEFAULT_P2P_PORT;

  @Option(
      names = {"--p2p-udp-port"},
      paramLabel = "<INTEGER>",
      description = "UDP port used for discovery. The default is the port specified in --p2p-port",
      arity = "1")
  private Integer p2pUdpPort;

  @Option(
      names = {"--p2p-discovery-enabled"},
      paramLabel = "<BOOLEAN>",
      showDefaultValue = Visibility.ALWAYS,
      description = "Enables discv5 discovery",
      fallbackValue = "true",
      arity = "0..1")
  private boolean p2pDiscoveryEnabled = DiscoveryConfig.DEFAULT_P2P_DISCOVERY_ENABLED;

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
      description = "P2P advertised IP (Default: 127.0.0.1)",
      arity = "1")
  private String p2pAdvertisedIp;

  @Option(
      names = {"--p2p-advertised-port"},
      paramLabel = "<INTEGER>",
      description = "P2P advertised port. The default is the port specified in --p2p-port",
      arity = "1")
  private Integer p2pAdvertisedPort;

  @Option(
      names = {"--p2p-advertised-udp-port"},
      paramLabel = "<INTEGER>",
      description =
          "Advertised UDP port to external peers. The default is the port specified in --p2p-advertised-port",
      arity = "1")
  private Integer p2pAdvertisedUdpPort;

  @Option(
      names = {"--p2p-private-key-file"},
      paramLabel = "<FILENAME>",
      description =
          "This node's private key file. If not specified, uses or generates a key which is stored within the <beacon-data-dir>.",
      arity = "1")
  private String p2pPrivateKeyFile = null;

  @Option(
      names = {"--p2p-peer-lower-bound"},
      paramLabel = "<INTEGER>",
      description = "Lower bound on the target number of peers",
      arity = "1")
  private int p2pLowerBound = DiscoveryConfig.DEFAULT_P2P_PEERS_LOWER_BOUND;

  @Option(
      names = {"--p2p-peer-upper-bound"},
      paramLabel = "<INTEGER>",
      description = "Upper bound on the target number of peers",
      arity = "1")
  private int p2pUpperBound = DiscoveryConfig.DEFAULT_P2P_PEERS_UPPER_BOUND;

  @Option(
      names = {"--Xp2p-target-subnet-subscriber-count"},
      paramLabel = "<INTEGER>",
      description = "Target number of peers subscribed to each attestation subnet",
      arity = "1",
      hidden = true)
  private int p2pTargetSubnetSubscriberCount = P2PConfig.DEFAULT_P2P_TARGET_SUBNET_SUBSCRIBER_COUNT;

  @Option(
      names = {"--Xp2p-minimum-randomly-selected-peer-count"},
      paramLabel = "<INTEGER>",
      description =
          "Number of peers that should be selected randomly (default 20%% of lower-bound target)",
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
      showDefaultValue = Visibility.ALWAYS,
      description = "Enables experimental multipeer sync",
      fallbackValue = "true",
      hidden = true,
      arity = "1")
  private boolean multiPeerSyncEnabled = SyncConfig.DEFAULT_MULTI_PEER_SYNC_ENABLED;

  @Option(
      names = {"--p2p-subscribe-all-subnets-enabled"},
      paramLabel = "<BOOLEAN>",
      showDefaultValue = Visibility.ALWAYS,
      description = "",
      arity = "0..1",
      fallbackValue = "true")
  private boolean subscribeAllSubnetsEnabled = P2PConfig.DEFAULT_SUBSCRIBE_ALL_SUBNETS_ENABLED;

  @Option(
      names = {"--Xp2p-minimum-subnet-subscriptions"},
      paramLabel = "<NUMBER>",
      description = "The minimum number of subnet subscriptions.",
      arity = "1",
      hidden = true)
  private Integer minimumSubnetSubscriptions = P2PConfig.DEFAULT_MINIMUM_SUBNET_SUBSCRIPTIONS;

  @Option(
      names = {"--Xp2p-gossip-scoring-enabled"},
      paramLabel = "<BOOLEAN>",
      showDefaultValue = Visibility.ALWAYS,
      description = "Enables experimental gossip scoring",
      hidden = true,
      arity = "0..1",
      fallbackValue = "true")
  private boolean gossipScoringEnabled = P2PConfig.DEFAULT_GOSSIP_SCORING_ENABLED;

  @Option(
      names = {"--Xpeer-rate-limit"},
      paramLabel = "<NUMBER>",
      description =
          "The number of requested objects per peer to allow per minute before disconnecting the peer.",
      arity = "1",
      hidden = true)
  private Integer peerRateLimit = P2PConfig.DEFAULT_PEER_RATE_LIMIT;

  @Option(
      names = {"--Xpeer-request-limit"},
      paramLabel = "<NUMBER>",
      description =
          "The number of requests per peer to allow per minute before disconnecting the peer.",
      arity = "1",
      hidden = true)
  private Integer peerRequestLimit = P2PConfig.DEFAULT_PEER_REQUEST_LIMIT;

  @Option(
      names = {"--Xp2p-batch-verify-signatures-max-threads"},
      paramLabel = "<NUMBER>",
      description = "Maximum number of threads to use for aggregated signature verification",
      arity = "1",
      hidden = true)
  private int batchVerifyMaxThreads = P2PConfig.DEFAULT_BATCH_VERIFY_MAX_THREADS;

  @Option(
      names = {"--Xp2p-batch-verify-signatures-queue-capacity"},
      paramLabel = "<NUMBER>",
      description = "Maximum queue size for pending aggregated signature verification",
      arity = "1",
      hidden = true)
  private int batchVerifyQueueCapacity = P2PConfig.DEFAULT_BATCH_VERIFY_QUEUE_CAPACITY;

  @Option(
      names = {"--Xp2p-batch-verify-signatures-max-batch-size"},
      paramLabel = "<NUMBER>",
      description = "Maximum number of verification tasks to include in a single batch",
      arity = "1",
      hidden = true)
  private int batchVerifyMaxBatchSize = P2PConfig.DEFAULT_BATCH_VERIFY_MAX_BATCH_SIZE;

  @Option(
      names = {"--Xp2p-batch-verify-signatures-strict-thread-limit-enabled"},
      paramLabel = "<BOOLEAN>",
      showDefaultValue = Visibility.ALWAYS,
      description =
          "When enabled, signature verification is entirely constrained to the max threads with no use of shared executor pools",
      arity = "0..1",
      hidden = true,
      fallbackValue = "true")
  private boolean batchVerifyStrictThreadLimitEnabled =
      P2PConfig.DEFAULT_BATCH_VERIFY_STRICT_THREAD_LIMIT_ENABLED;

  @Option(
      names = {"--Xp2p-discovery-site-local-addresses-enabled"},
      paramLabel = "<BOOLEAN>",
      showDefaultValue = Visibility.ALWAYS,
      description =
          "Whether discover accepts messages and peer records with site local (RFC1918) addresses",
      arity = "0..1",
      hidden = true,
      fallbackValue = "true")
  private boolean siteLocalAddressesEnabled = DiscoveryConfig.DEFAULT_SITE_LOCAL_ADDRESSES_ENABLED;

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

  public void configure(final TekuConfiguration.Builder builder) {
    builder
        .p2p(
            b ->
                b.subscribeAllSubnetsEnabled(subscribeAllSubnetsEnabled)
                    .minimumSubnetSubscriptions(minimumSubnetSubscriptions)
                    .batchVerifyMaxThreads(batchVerifyMaxThreads)
                    .batchVerifyQueueCapacity(batchVerifyQueueCapacity)
                    .batchVerifyMaxBatchSize(batchVerifyMaxBatchSize)
                    .batchVerifyStrictThreadLimitEnabled(batchVerifyStrictThreadLimitEnabled)
                    .targetSubnetSubscriberCount(p2pTargetSubnetSubscriberCount)
                    .isGossipScoringEnabled(gossipScoringEnabled)
                    .peerRateLimit(peerRateLimit)
                    .peerRequestLimit(peerRequestLimit))
        .discovery(
            d -> {
              if (p2pDiscoveryBootnodes != null) {
                d.bootnodes(p2pDiscoveryBootnodes);
              }
              if (minimumRandomlySelectedPeerCount != null) {
                d.minRandomlySelectedPeers(minimumRandomlySelectedPeerCount);
              }
              if (p2pUdpPort != null) {
                d.listenUdpPort(p2pUdpPort);
              }
              if (p2pAdvertisedUdpPort != null) {
                d.advertisedUdpPort(OptionalInt.of(p2pAdvertisedUdpPort));
              }
              d.isDiscoveryEnabled(p2pDiscoveryEnabled)
                  .staticPeers(p2pStaticPeers)
                  .minPeers(getP2pLowerBound())
                  .maxPeers(getP2pUpperBound())
                  .siteLocalAddressesEnabled(siteLocalAddressesEnabled);
            })
        .network(
            n -> {
              if (p2pPrivateKeyFile != null) {
                n.privateKeyFile(p2pPrivateKeyFile);
              }
              if (p2pAdvertisedPort != null) {
                n.advertisedPort(OptionalInt.of(p2pAdvertisedPort));
              }
              n.networkInterface(p2pInterface)
                  .isEnabled(p2pEnabled)
                  .listenPort(p2pPort)
                  .advertisedIp(Optional.ofNullable(p2pAdvertisedIp));
            })
        .sync(s -> s.isMultiPeerSyncEnabled(multiPeerSyncEnabled));
    natOptions.configure(builder);
  }
}
