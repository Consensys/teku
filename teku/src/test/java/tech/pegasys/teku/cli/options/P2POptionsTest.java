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
import static tech.pegasys.teku.infrastructure.async.AsyncRunnerFactory.DEFAULT_MAX_QUEUE_SIZE_ALL_SUBNETS;
import static tech.pegasys.teku.networking.eth2.P2PConfig.DEFAULT_GOSSIP_BLOBS_AFTER_BLOCK_ENABLED;
import static tech.pegasys.teku.networking.p2p.discovery.DiscoveryConfig.DEFAULT_P2P_PEERS_LOWER_BOUND_ALL_SUBNETS;
import static tech.pegasys.teku.networking.p2p.discovery.DiscoveryConfig.DEFAULT_P2P_PEERS_UPPER_BOUND_ALL_SUBNETS;
import static tech.pegasys.teku.networking.p2p.gossip.config.GossipConfig.DEFAULT_FLOOD_PUBLISH_ENABLED;
import static tech.pegasys.teku.networking.p2p.network.config.NetworkConfig.DEFAULT_P2P_PORT;
import static tech.pegasys.teku.networking.p2p.network.config.NetworkConfig.DEFAULT_P2P_PORT_IPV6;
import static tech.pegasys.teku.validator.api.ValidatorConfig.DEFAULT_EXECUTOR_MAX_QUEUE_SIZE_ALL_SUBNETS;

import java.util.List;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beacon.sync.SyncConfig;
import tech.pegasys.teku.cli.AbstractBeaconNodeCommandTest;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.networking.eth2.P2PConfig;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryConfig;
import tech.pegasys.teku.networking.p2p.network.config.FilePrivateKeySource;
import tech.pegasys.teku.networking.p2p.network.config.NetworkConfig;

public class P2POptionsTest extends AbstractBeaconNodeCommandTest {

  @Test
  public void shouldReadFromConfigurationFile() {
    final TekuConfiguration tekuConfig = getTekuConfigurationFromFile("P2POptions_config.yaml");

    final P2PConfig p2pConfig = tekuConfig.p2p();
    assertThat(p2pConfig.getTargetSubnetSubscriberCount()).isEqualTo(5);
    assertThat(p2pConfig.getPeerRateLimit()).isEqualTo(100);
    assertThat(p2pConfig.getPeerRequestLimit()).isEqualTo(101);

    final DiscoveryConfig discoConfig = tekuConfig.discovery();
    assertThat(discoConfig.isDiscoveryEnabled()).isTrue();
    assertThat(discoConfig.getMinPeers()).isEqualTo(70);
    assertThat(discoConfig.getMaxPeers()).isEqualTo(85);
    assertThat(discoConfig.getMinRandomlySelectedPeers()).isEqualTo(1);
    assertThat(discoConfig.getStaticPeers()).isEqualTo(List.of("127.1.0.1", "127.1.1.1"));

    final NetworkConfig networkConfig = tekuConfig.network();
    assertThat(networkConfig.isEnabled()).isTrue();
    assertThat(networkConfig.getAdvertisedIps()).containsExactly("127.200.0.1");
    assertThat(networkConfig.getNetworkInterfaces()).containsExactly("127.100.0.1");
    assertThat(networkConfig.getListenPort()).isEqualTo(4321);
    assertThat(networkConfig.getPrivateKeySource()).containsInstanceOf(FilePrivateKeySource.class);
    assertThat(((FilePrivateKeySource) networkConfig.getPrivateKeySource().get()).getFileName())
        .isEqualTo("/the/file");

    final SyncConfig syncConfig = tekuConfig.sync();
    assertThat(syncConfig.getHistoricalSyncBatchSize()).isEqualTo(102);
    assertThat(syncConfig.getForwardSyncBatchSize()).isEqualTo(103);
    assertThat(syncConfig.getForwardSyncMaxPendingBatches()).isEqualTo(8);
    assertThat(syncConfig.getForwardSyncMaxBlocksPerMinute()).isEqualTo(80);
  }

  @Test
  public void p2pEnabled_shouldNotRequireAValue() {
    final TekuConfiguration config = getTekuConfigurationFromArguments("--p2p-enabled");
    assertThat(config.network().isEnabled()).isTrue();
    assertThat(config.sync().isSyncEnabled()).isTrue();
  }

  @Test
  public void p2pEnabled_false() {
    final TekuConfiguration config = getTekuConfigurationFromArguments("--p2p-enabled=false");
    assertThat(config.network().isEnabled()).isFalse();
    assertThat(config.sync().isSyncEnabled()).isFalse();
  }

  @Test
  public void p2pDiscoveryEnabled_shouldNotRequireAValue() {
    TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments("--p2p-discovery-enabled");
    final DiscoveryConfig config = tekuConfiguration.discovery();
    assertThat(config.isDiscoveryEnabled()).isTrue();
  }

  @Test
  void p2pUdpPort_shouldDefaultToP2pPortWhenNeitherSet() {
    final TekuConfiguration tekuConfig = getTekuConfigurationFromArguments();
    assertThat(tekuConfig.discovery().getListenUdpPort())
        .isEqualTo(tekuConfig.network().getListenPort());
  }

  @Test
  void p2pUdpPort_shouldDefaultToP2pPortWhenP2pPortIsSet() {
    final TekuConfiguration tekuConfig = getTekuConfigurationFromArguments("--p2p-port=9999");
    assertThat(tekuConfig.discovery().getListenUdpPort())
        .isEqualTo(tekuConfig.network().getListenPort());
    assertThat(tekuConfig.discovery().getListenUdpPort()).isEqualTo(9999);
  }

  @Test
  void p2pUdpPort_shouldOverrideP2pPortWhenBothSet() {
    final TekuConfiguration tekuConfig =
        getTekuConfigurationFromArguments("--p2p-udp-port=9888", "--p2p-port=9999");
    assertThat(tekuConfig.discovery().getListenUdpPort()).isEqualTo(9888);
    assertThat(tekuConfig.network().getListenPort()).isEqualTo(9999);
  }

  @Test
  public void advertisedIps_shouldDefaultToEmpty() {
    final NetworkConfig config = getTekuConfigurationFromArguments().network();
    assertThat(config.hasUserExplicitlySetAdvertisedIps()).isFalse();
  }

  @Test
  public void advertisedIps_shouldAcceptValue() {
    final String ip = "10.0.1.200";
    TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments("--p2p-advertised-ip", ip);
    assertThat(tekuConfiguration.network().getAdvertisedIps())
        .allMatch(advertisedIp -> advertisedIp.contains(ip));
  }

  @Test
  public void advertisedPort_shouldDefaultToListenPort() {
    assertThat(getTekuConfigurationFromArguments().network().getAdvertisedPort()).isEqualTo(9000);
  }

  @Test
  public void advertisedPort_shouldAcceptValue() {
    TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments("--p2p-advertised-port", "8056");
    assertThat(tekuConfiguration.network().getAdvertisedPort()).isEqualTo(8056);
  }

  @Test
  void advertisedUdpPort_shouldDefaultToTcpListenPortWhenNeitherSet() {
    final TekuConfiguration tekuConfig = getTekuConfigurationFromArguments();
    assertThat(tekuConfig.discovery().getAdvertisedUdpPort())
        .isEqualTo(tekuConfig.network().getAdvertisedPort());
  }

  @Test
  void advertisedUdpPort_shouldDefaultToTcpListenPortWhenListenPortSet() {
    TekuConfiguration tekuConfiguration = getTekuConfigurationFromArguments("--p2p-port=8000");
    assertThat(tekuConfiguration.discovery().getAdvertisedUdpPort()).isEqualTo(8000);
    assertThat(tekuConfiguration.discovery().getAdvertisedUdpPort())
        .isEqualTo(tekuConfiguration.network().getAdvertisedPort());
  }

  @Test
  void advertisedUdpPort_shouldDefaultToAdvertisedTcpPortWhenAdvertisedPortSet() {
    final TekuConfiguration tekuConfig =
        getTekuConfigurationFromArguments("--p2p-port=8000", "--p2p-advertised-port=7000");
    assertThat(tekuConfig.discovery().getAdvertisedUdpPort()).isEqualTo(7000);
    assertThat(tekuConfig.discovery().getAdvertisedUdpPort())
        .isEqualTo(tekuConfig.network().getAdvertisedPort());
  }

  @Test
  void advertisedUdpPort_shouldOverrideAdvertisedUdpPort() {
    final TekuConfiguration tekuConfig =
        getTekuConfigurationFromArguments(
            "--p2p-advertised-udp-port=6000", "--p2p-port=8000", "--p2p-advertised-port=7000");
    assertThat(tekuConfig.discovery().getAdvertisedUdpPort()).isEqualTo(6000);
    assertThat(tekuConfig.network().getAdvertisedPort()).isEqualTo(7000);
    assertThat(tekuConfig.network().getListenPort()).isEqualTo(8000);
  }

  @Test
  public void minimumRandomlySelectedPeerCount_shouldDefaultTo20PercentOfLowerBound() {
    TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments(
            "--p2p-peer-lower-bound", "100",
            "--p2p-peer-upper-bound", "110");
    assertThat(tekuConfiguration.discovery().getMinRandomlySelectedPeers()).isEqualTo(20);
  }

  @Test
  public void privateKeyFile_shouldBeSettable() {
    TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments("--p2p-private-key-file", "/some/file");
    assertThat(tekuConfiguration.network().getPrivateKeySource())
        .containsInstanceOf(FilePrivateKeySource.class);
    assertThat(
            ((FilePrivateKeySource) tekuConfiguration.network().getPrivateKeySource().get())
                .getFileName())
        .isEqualTo("/some/file");
  }

  @Test
  public void privateKeyFile_ignoreBlankStrings() {
    assertThat(
            getTekuConfigurationFromArguments("--p2p-private-key-file", "   ")
                .network()
                .getPrivateKeySource())
        .isEmpty();
  }

  @Test
  public void minimumRandomlySelectedPeerCount_canBeOverridden() {
    TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments(
            "--p2p-peer-lower-bound", "100",
            "--p2p-peer-upper-bound", "110",
            "--Xp2p-minimum-randomly-selected-peer-count", "40");
    assertThat(tekuConfiguration.discovery().getMinRandomlySelectedPeers()).isEqualTo(40);
  }

  @Test
  public void minimumRandomlySelectedPeerCount_shouldBeBoundedByMaxPeers() {
    TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments(
            "--p2p-peer-lower-bound", "0",
            "--p2p-peer-upper-bound", "0");
    assertThat(tekuConfiguration.discovery().getMinRandomlySelectedPeers()).isEqualTo(0);
  }

  @Test
  public void historicalSyncBatchSize_shouldBeSettable() {
    TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments("--Xp2p-historical-sync-batch-size", "10");
    assertThat(tekuConfiguration.sync().getHistoricalSyncBatchSize()).isEqualTo(10);
  }

  @Test
  public void forwardSyncBatchSize_shouldBeSettable() {
    TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments("--Xp2p-sync-batch-size", "10");
    assertThat(tekuConfiguration.sync().getForwardSyncBatchSize()).isEqualTo(10);
  }

  @Test
  public void forwardSyncMaxPendingBatches_shouldBeSettable() {
    TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments("--Xp2p-sync-max-pending-batches", "10");
    assertThat(tekuConfiguration.sync().getForwardSyncMaxPendingBatches()).isEqualTo(10);
  }

  @Test
  public void forwardSyncRateLimit_shouldBeSettable() {
    TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments("--Xp2p-sync-rate-limit", "10");
    assertThat(tekuConfiguration.sync().getForwardSyncMaxBlocksPerMinute()).isEqualTo(10);
  }

  @Test
  public void forwardSyncBatchSize_greaterThanMessageSizeShouldThrowException() {
    assertThatThrownBy(() -> createConfigBuilder().sync(s -> s.forwardSyncBatchSize(3000)).build())
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessage("Forward sync batch size cannot be greater than 128");
  }

  @Test
  public void historicalSyncBatchSize_greaterThanMessageSizeShouldThrowException() {
    assertThatThrownBy(
            () -> createConfigBuilder().sync(s -> s.historicalSyncBatchSize(3000)).build())
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessage("Historical sync batch size cannot be greater than 128");
  }

  @Test
  public void allSubnetsShouldOverrideQueueSizesAndPeers() {
    final TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments("--p2p-subscribe-all-subnets-enabled", "true");

    assertThat(tekuConfiguration.discovery().getMaxPeers())
        .isEqualTo(DEFAULT_P2P_PEERS_UPPER_BOUND_ALL_SUBNETS);
    assertThat(tekuConfiguration.discovery().getMinPeers())
        .isEqualTo(DEFAULT_P2P_PEERS_LOWER_BOUND_ALL_SUBNETS);
    assertThat(tekuConfiguration.eth2NetworkConfiguration().getAsyncBeaconChainMaxQueue())
        .isEqualTo(DEFAULT_MAX_QUEUE_SIZE_ALL_SUBNETS);
    assertThat(tekuConfiguration.eth2NetworkConfiguration().getAsyncP2pMaxQueue())
        .isEqualTo(DEFAULT_MAX_QUEUE_SIZE_ALL_SUBNETS);
    assertThat(tekuConfiguration.validatorClient().getValidatorConfig().getExecutorMaxQueueSize())
        .isEqualTo(DEFAULT_EXECUTOR_MAX_QUEUE_SIZE_ALL_SUBNETS);
    assertThat(tekuConfiguration.p2p().getBatchVerifyQueueCapacity())
        .isEqualTo(DEFAULT_MAX_QUEUE_SIZE_ALL_SUBNETS);
  }

  @Test
  public void allSubnetsShouldNotOverridePeersIfExplicitlySet() {
    final TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments(
            "--p2p-subscribe-all-subnets-enabled",
            "true",
            "--p2p-peer-lower-bound",
            "20",
            "--p2p-peer-upper-bound",
            "21");

    assertThat(tekuConfiguration.discovery().getMaxPeers()).isEqualTo(21);
    assertThat(tekuConfiguration.discovery().getMinPeers()).isEqualTo(20);
  }

  @Test
  public void allSubnetsShouldNotOverridePeersIfExplicitlySetWithDefaults() {
    final TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments(
            "--p2p-peer-lower-bound",
            "64",
            "--p2p-peer-upper-bound",
            "100",
            "--p2p-subscribe-all-subnets-enabled",
            "true");

    assertThat(tekuConfiguration.discovery().getMinPeers()).isEqualTo(64);
    assertThat(tekuConfiguration.discovery().getMaxPeers()).isEqualTo(100);
  }

  @Test
  public void allSubnetsShouldNotOverrideQueuesIfExplicitlySet() {
    final TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments(
            "--p2p-subscribe-all-subnets-enabled",
            "true",
            "--Xnetwork-async-p2p-max-queue",
            "15000",
            "--Xnetwork-async-beaconchain-max-queue",
            "15020",
            "--Xvalidator-executor-max-queue-size",
            "15120",
            "--Xp2p-batch-verify-signatures-queue-capacity",
            "15220");

    assertThat(tekuConfiguration.eth2NetworkConfiguration().getAsyncP2pMaxQueue())
        .isEqualTo(15_000);
    assertThat(tekuConfiguration.eth2NetworkConfiguration().getAsyncBeaconChainMaxQueue())
        .isEqualTo(15_020);
    assertThat(tekuConfiguration.validatorClient().getValidatorConfig().getExecutorMaxQueueSize())
        .isEqualTo(15_120);
    assertThat(tekuConfiguration.p2p().getBatchVerifyQueueCapacity()).isEqualTo(15_220);
  }

  @Test
  public void floodPublishEnabled_defaultIsSetCorrectly() {
    final TekuConfiguration config = getTekuConfigurationFromArguments();
    assertThat(config.network().getGossipConfig().isFloodPublishEnabled())
        .isEqualTo(DEFAULT_FLOOD_PUBLISH_ENABLED);
  }

  @Test
  public void floodPublishEnabled_shouldNotRequireAValue() {
    final TekuConfiguration config =
        getTekuConfigurationFromArguments("--Xp2p-flood-publish-enabled");
    assertThat(config.network().getGossipConfig().isFloodPublishEnabled()).isTrue();
  }

  @Test
  public void floodPublishEnabled_true() {
    final TekuConfiguration config =
        getTekuConfigurationFromArguments("--Xp2p-flood-publish-enabled=true");
    assertThat(config.network().getGossipConfig().isFloodPublishEnabled()).isTrue();
  }

  @Test
  public void floodPublishEnabled_false() {
    final TekuConfiguration config =
        getTekuConfigurationFromArguments("--Xp2p-flood-publish-enabled=false");
    assertThat(config.network().getGossipConfig().isFloodPublishEnabled()).isFalse();
  }

  @Test
  public void gossipBlobsAfterBlockEnabled_defaultIsSetCorrectly() {
    final TekuConfiguration config = getTekuConfigurationFromArguments();
    assertThat(config.p2p().isGossipBlobsAfterBlockEnabled())
        .isEqualTo(DEFAULT_GOSSIP_BLOBS_AFTER_BLOCK_ENABLED);
  }

  @Test
  public void gossipBlobsAfterBlockEnabled_shouldNotRequireAValue() {
    final TekuConfiguration config =
        getTekuConfigurationFromArguments("--Xp2p-gossip-blobs-after-block-enabled");
    assertThat(config.p2p().isGossipBlobsAfterBlockEnabled()).isTrue();
  }

  @Test
  public void gossipBlobsAfterBlockEnabled_true() {
    final TekuConfiguration config =
        getTekuConfigurationFromArguments("--Xp2p-gossip-blobs-after-block-enabled=true");
    assertThat(config.p2p().isGossipBlobsAfterBlockEnabled()).isTrue();
  }

  @Test
  public void gossipBlobsAfterBlockEnabled_false() {
    final TekuConfiguration config =
        getTekuConfigurationFromArguments("--Xp2p-gossip-blobs-after-block-enabled=false");
    assertThat(config.p2p().isGossipBlobsAfterBlockEnabled()).isFalse();
  }

  @Test
  public void defaultPortsAreSetCorrectly() {
    final TekuConfiguration tekuConfiguration = getTekuConfigurationFromArguments();

    final DiscoveryConfig discoveryConfig = tekuConfiguration.discovery();
    assertThat(discoveryConfig.getListenUdpPort()).isEqualTo(DEFAULT_P2P_PORT);
    assertThat(discoveryConfig.getListenUpdPortIpv6()).isEqualTo(DEFAULT_P2P_PORT_IPV6);
    assertThat(discoveryConfig.getAdvertisedUdpPort()).isEqualTo(DEFAULT_P2P_PORT);
    assertThat(discoveryConfig.getAdvertisedUdpPortIpv6()).isEqualTo(DEFAULT_P2P_PORT_IPV6);

    final NetworkConfig networkConfig = tekuConfiguration.network();
    assertThat(networkConfig.getListenPort()).isEqualTo(DEFAULT_P2P_PORT);
    assertThat(networkConfig.getListenPortIpv6()).isEqualTo(DEFAULT_P2P_PORT_IPV6);
    assertThat(networkConfig.getAdvertisedPort()).isEqualTo(DEFAULT_P2P_PORT);
    assertThat(networkConfig.getAdvertisedPortIpv6()).isEqualTo(DEFAULT_P2P_PORT_IPV6);
  }
}
