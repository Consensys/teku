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
import tech.pegasys.teku.util.config.TekuConfiguration;

public class P2POptionsTest extends AbstractBeaconNodeCommandTest {

  @Test
  public void shouldReadFromConfigurationFile() {
    final TekuConfiguration config = getTekuConfigurationFromFile("P2POptions_config.yaml");

    assertThat(config.getP2pAdvertisedIp()).isEqualTo(Optional.of("127.200.0.1"));
    assertThat(config.getP2pInterface()).isEqualTo("127.100.0.1");
    assertThat(config.isP2pEnabled()).isTrue();
    assertThat(config.isP2pDiscoveryEnabled()).isTrue();
    assertThat(config.getP2pPort()).isEqualTo(4321);
    assertThat(config.getP2pPrivateKeyFile()).isEqualTo("/the/file");
    assertThat(config.getP2pStaticPeers()).isEqualTo(List.of("127.1.0.1", "127.1.1.1"));
    assertThat(config.getP2pPeerLowerBound()).isEqualTo(11);
    assertThat(config.getP2pPeerUpperBound()).isEqualTo(12);
  }

  @Test
  public void p2pEnabled_shouldNotRequireAValue() {
    final TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments(
            "--p2p-enabled", "--eth1-deposit-contract-address", ETH1_ADDRESS_STRING);
    assertThat(tekuConfiguration.isP2pEnabled()).isTrue();
  }

  @Test
  public void p2pDiscoveryEnabled_shouldNotRequireAValue() {
    final TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments(
            "--p2p-discovery-enabled", "--eth1-deposit-contract-address", ETH1_ADDRESS_STRING);
    assertThat(tekuConfiguration.isP2pEnabled()).isTrue();
  }

  @Test
  public void snappyCompressionDefaultValueIsSet() {
    final String[] args = {"--eth1-deposit-contract-address", ETH1_ADDRESS_STRING};

    beaconNodeCommand.parse(args);

    final TekuConfiguration tekuConfiguration = getResultingTekuConfiguration();
    assertThat(tekuConfiguration.isP2pSnappyEnabled()).isFalse();
  }

  @Test
  public void mainnetNetworkDefaultsSnappyCompressionOn() {
    final String[] args = {
      "--network", "mainnet", "--eth1-deposit-contract-address", ETH1_ADDRESS_STRING
    };

    beaconNodeCommand.parse(args);

    final TekuConfiguration tekuConfiguration = getResultingTekuConfiguration();
    assertThat(tekuConfiguration.isP2pSnappyEnabled()).isTrue();
  }

  @Test
  public void minimalNetworkDefaultsSnappyCompressionOff() {
    final String[] args = {
      "--network", "minimal", "--eth1-deposit-contract-address", ETH1_ADDRESS_STRING
    };

    beaconNodeCommand.parse(args);

    final TekuConfiguration tekuConfiguration = getResultingTekuConfiguration();
    assertThat(tekuConfiguration.isP2pSnappyEnabled()).isFalse();
  }

  @Test
  public void overrideMainnetSnappyDefault() {
    final String[] args = {
      "--network",
      "mainnet",
      "--p2p-snappy-enabled",
      "false",
      "--eth1-deposit-contract-address",
      ETH1_ADDRESS_STRING
    };

    beaconNodeCommand.parse(args);

    final TekuConfiguration tekuConfiguration = getResultingTekuConfiguration();
    assertThat(tekuConfiguration.isP2pSnappyEnabled()).isFalse();
  }

  @Test
  public void overrideMinimalSnappyDefault() {
    final String[] args = {
      "--network",
      "minimal",
      "--p2p-snappy-enabled",
      "true",
      "--eth1-deposit-contract-address",
      ETH1_ADDRESS_STRING
    };

    beaconNodeCommand.parse(args);

    final TekuConfiguration tekuConfiguration = getResultingTekuConfiguration();
    assertThat(tekuConfiguration.isP2pSnappyEnabled()).isTrue();
  }

  @Test
  public void advertisedIp_shouldDefaultToEmpty() {
    assertThat(
            getTekuConfigurationFromArguments(
                    "--eth1-deposit-contract-address", ETH1_ADDRESS_STRING)
                .getP2pAdvertisedIp())
        .isEmpty();
  }

  @Test
  public void advertisedIp_shouldAcceptValue() {
    final String ip = "10.0.1.200";
    assertThat(
            getTekuConfigurationFromArguments(
                    "--p2p-advertised-ip",
                    ip,
                    "--eth1-deposit-contract-address",
                    ETH1_ADDRESS_STRING)
                .getP2pAdvertisedIp())
        .contains(ip);
  }

  @Test
  public void advertisedPort_shouldDefaultToEmpty() {
    assertThat(
            getTekuConfigurationFromArguments(
                    "--eth1-deposit-contract-address", ETH1_ADDRESS_STRING)
                .getP2pAdvertisedPort())
        .isEmpty();
  }

  @Test
  public void advertisedPort_shouldAcceptValue() {
    assertThat(
            getTekuConfigurationFromArguments(
                    "--p2p-advertised-port",
                    "8056",
                    "--eth1-deposit-contract-address",
                    ETH1_ADDRESS_STRING)
                .getP2pAdvertisedPort())
        .hasValue(8056);
  }
}
