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
import tech.pegasys.teku.util.config.GlobalConfiguration;

public class P2POptionsTest extends AbstractBeaconNodeCommandTest {

  @Test
  public void shouldReadFromConfigurationFile() {
    final GlobalConfiguration config = getGlobalConfigurationFromFile("P2POptions_config.yaml");

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
  }

  @Test
  public void p2pEnabled_shouldNotRequireAValue() {
    final GlobalConfiguration globalConfiguration =
        getGlobalConfigurationFromArguments("--p2p-enabled");
    assertThat(globalConfiguration.isP2pEnabled()).isTrue();
  }

  @Test
  public void p2pDiscoveryEnabled_shouldNotRequireAValue() {
    final GlobalConfiguration globalConfiguration =
        getGlobalConfigurationFromArguments("--p2p-discovery-enabled");
    assertThat(globalConfiguration.isP2pEnabled()).isTrue();
  }

  @Test
  public void snappyCompressionDefaultValueIsSet() {
    final String[] args = {};

    beaconNodeCommand.parse(args);

    final GlobalConfiguration globalConfiguration = getResultingGlobalConfiguration();
    assertThat(globalConfiguration.isP2pSnappyEnabled()).isTrue();
  }

  @Test
  public void snappyCompressionDefaultsToTrueForCustomNetwork() {
    final String[] args = {"--network=/tmp/foo.yaml"};

    beaconNodeCommand.parse(args);

    final GlobalConfiguration globalConfiguration = getResultingGlobalConfiguration();
    assertThat(globalConfiguration.isP2pSnappyEnabled()).isTrue();
  }

  @Test
  public void mainnetNetworkDefaultsSnappyCompressionOn() {
    final String[] args = {"--network", "mainnet"};

    beaconNodeCommand.parse(args);

    final GlobalConfiguration globalConfiguration = getResultingGlobalConfiguration();
    assertThat(globalConfiguration.isP2pSnappyEnabled()).isTrue();
  }

  @Test
  public void minimalNetworkDefaultsSnappyCompressionOff() {
    final String[] args = {"--network", "minimal"};

    beaconNodeCommand.parse(args);

    final GlobalConfiguration globalConfiguration = getResultingGlobalConfiguration();
    assertThat(globalConfiguration.isP2pSnappyEnabled()).isFalse();
  }

  @Test
  public void overrideMainnetSnappyDefault() {
    final String[] args = {
      "--network", "mainnet",
      "--p2p-snappy-enabled", "false"
    };

    beaconNodeCommand.parse(args);

    final GlobalConfiguration globalConfiguration = getResultingGlobalConfiguration();
    assertThat(globalConfiguration.isP2pSnappyEnabled()).isFalse();
  }

  @Test
  public void overrideMinimalSnappyDefault() {
    final String[] args = {
      "--network", "minimal",
      "--p2p-snappy-enabled", "true"
    };

    beaconNodeCommand.parse(args);

    final GlobalConfiguration globalConfiguration = getResultingGlobalConfiguration();
    assertThat(globalConfiguration.isP2pSnappyEnabled()).isTrue();
  }

  @Test
  public void advertisedIp_shouldDefaultToEmpty() {
    assertThat(getGlobalConfigurationFromArguments().getP2pAdvertisedIp()).isEmpty();
  }

  @Test
  public void advertisedIp_shouldAcceptValue() {
    final String ip = "10.0.1.200";
    assertThat(getGlobalConfigurationFromArguments("--p2p-advertised-ip", ip).getP2pAdvertisedIp())
        .contains(ip);
  }

  @Test
  public void advertisedPort_shouldDefaultToEmpty() {
    assertThat(getGlobalConfigurationFromArguments().getP2pAdvertisedPort()).isEmpty();
  }

  @Test
  public void advertisedPort_shouldAcceptValue() {
    assertThat(
            getGlobalConfigurationFromArguments("--p2p-advertised-port", "8056")
                .getP2pAdvertisedPort())
        .hasValue(8056);
  }
}
