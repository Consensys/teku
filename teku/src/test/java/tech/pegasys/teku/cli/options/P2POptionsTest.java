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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.cli.AbstractBeaconNodeCommandTest;
import tech.pegasys.teku.networking.eth2.P2PConfig;

public class P2POptionsTest extends AbstractBeaconNodeCommandTest {

  @Test
  public void shouldReadFromConfigurationFile() {
    final P2PConfig config =
        getTekuConfigurationFromFile("P2POptions_config.yaml").beaconChain().p2pConfig();

    assertThat(config.getP2pAdvertisedIp()).isEqualTo(Optional.of("127.200.0.1"));
    assertThat(config.getP2pInterface()).isEqualTo("127.100.0.1");
    assertThat(config.isP2pEnabled()).isTrue();
    assertThat(config.isP2pDiscoveryEnabled()).isTrue();
    assertThat(config.getP2pPort()).isEqualTo(4321);
    assertThat(config.getP2pPrivateKeyFile()).isEqualTo("/the/file");
    assertThat(config.getP2pStaticPeers()).isEqualTo(List.of("127.1.0.1", "127.1.1.1"));
    assertThat(config.getP2pPeerLowerBound()).isEqualTo(70);
    assertThat(config.getP2pPeerUpperBound()).isEqualTo(85);
    assertThat(config.getTargetSubnetSubscriberCount()).isEqualTo(5);
    assertThat(config.getMinimumRandomlySelectedPeerCount()).isEqualTo(1);
  }

  @Test
  public void p2pEnabled_shouldNotRequireAValue() {
    final P2PConfig globalConfiguration =
        getTekuConfigurationFromArguments("--p2p-enabled").beaconChain().p2pConfig();
    assertThat(globalConfiguration.isP2pEnabled()).isTrue();
  }

  @Test
  public void p2pDiscoveryEnabled_shouldNotRequireAValue() {
    final P2PConfig globalConfiguration =
        getTekuConfigurationFromArguments("--p2p-discovery-enabled").beaconChain().p2pConfig();
    assertThat(globalConfiguration.isP2pEnabled()).isTrue();
  }

  @Test
  public void advertisedIp_shouldDefaultToEmpty() {
    assertThat(getTekuConfigurationFromArguments().beaconChain().p2pConfig().getP2pAdvertisedIp())
        .isEmpty();
  }

  @Test
  public void advertisedIp_shouldAcceptValue() {
    final String ip = "10.0.1.200";
    assertThat(
            getTekuConfigurationFromArguments("--p2p-advertised-ip", ip)
                .beaconChain()
                .p2pConfig()
                .getP2pAdvertisedIp())
        .contains(ip);
  }

  @Test
  public void advertisedPort_shouldDefaultToEmpty() {
    assertThat(getTekuConfigurationFromArguments().beaconChain().p2pConfig().getP2pAdvertisedPort())
        .isEmpty();
  }

  @Test
  public void advertisedPort_shouldAcceptValue() {
    assertThat(
            getTekuConfigurationFromArguments("--p2p-advertised-port", "8056")
                .beaconChain()
                .p2pConfig()
                .getP2pAdvertisedPort())
        .hasValue(8056);
  }

  @Test
  public void minimumRandomlySelectedPeerCount_shouldDefaultTo20PercentOfLowerBound() {
    assertThat(
            getTekuConfigurationFromArguments(
                    "--p2p-peer-lower-bound", "100",
                    "--p2p-peer-upper-bound", "110")
                .beaconChain()
                .p2pConfig()
                .getMinimumRandomlySelectedPeerCount())
        .isEqualTo(20);
  }

  @Test
  public void minimumRandomlySelectedPeerCount_canBeOverriden() {
    assertThat(
            getTekuConfigurationFromArguments(
                    "--p2p-peer-lower-bound", "100",
                    "--p2p-peer-upper-bound", "110",
                    "--Xp2p-minimum-randomly-selected-peer-count", "40")
                .beaconChain()
                .p2pConfig()
                .getMinimumRandomlySelectedPeerCount())
        .isEqualTo(40);
  }
}
