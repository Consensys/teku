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

import java.net.URL;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tech.pegasys.teku.cli.AbstractBeaconNodeCommandTest;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.datastructures.eth1.Eth1Address;
import tech.pegasys.teku.networking.eth2.P2PConfig;
import tech.pegasys.teku.networks.Eth2NetworkConfiguration;

public class Eth2NetworkOptionsTest extends AbstractBeaconNodeCommandTest {

  @Test
  public void shouldReadFromConfigurationFile() {
    final Eth2NetworkConfiguration eth2NetworkConfig =
        Eth2NetworkConfiguration.builder("pyrmont").build();
    final TekuConfiguration config = getTekuConfigurationFromFile("networkOptions_config.yaml");
    assertThat(config.eth2NetworkConfiguration().getConstants())
        .isEqualTo(eth2NetworkConfig.getConstants());
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"mainnet", "minimal", "swift", "medalla", "pyrmont"})
  public void useDefaultsFromNetworkDefinition(final String networkName) {
    final Eth2NetworkConfiguration eth2NetworkConfig =
        Eth2NetworkConfiguration.builder(networkName).build();

    beaconNodeCommand.parse(new String[] {"--network", networkName});
    final TekuConfiguration tekuConfig = getResultingTekuConfiguration();

    // eth2Config
    assertThat(tekuConfig.eth2NetworkConfiguration())
        .usingRecursiveComparison()
        .isEqualTo(eth2NetworkConfig);

    // Storage config
    assertThat(tekuConfig.storageConfiguration().getEth1DepositContract())
        .isEqualTo(eth2NetworkConfig.getEth1DepositContractAddress());

    // WS config
    assertThat(tekuConfig.weakSubjectivity().getWeakSubjectivityStateResource())
        .isEqualTo(eth2NetworkConfig.getInitialState());

    // p2p config
    assertThat(tekuConfig.beaconChain().p2pConfig().getP2pDiscoveryBootnodes())
        .isEqualTo(eth2NetworkConfig.getDiscoveryBootnodes());

    // Rest api
    assertThat(tekuConfig.beaconChain().beaconRestApiConfig().getEth1DepositContractAddress())
        .isEqualTo(eth2NetworkConfig.getEth1DepositContractAddress());

    // Powchain
    assertThat(tekuConfig.powchain().getDepositContract())
        .isEqualTo(eth2NetworkConfig.getEth1DepositContractAddress().orElse(null));
    assertThat(tekuConfig.powchain().getDepositContractDeployBlock())
        .isEqualTo(eth2NetworkConfig.getEth1DepositContractDeployBlock());
  }

  @Test
  public void overrideDepositContract() {
    beaconNodeCommand.parse(
        new String[] {
          "--network",
          "mainnet",
          "--eth1-deposit-contract-address",
          "0xfe3b557e8fb62b89f4916b721be55ceb828dbd73"
        });

    final Optional<Eth1Address> configuredDepositContract =
        getResultingTekuConfiguration().eth2NetworkConfiguration().getEth1DepositContractAddress();
    assertThat(configuredDepositContract)
        .contains(Eth1Address.fromHexString("0xfe3b557e8fb62b89f4916b721be55ceb828dbd73"));
  }

  @Test
  public void overrideDefaultBootnodesWithEmptyList() {
    beaconNodeCommand.parse(new String[] {"--network", "pyrmont", "--p2p-discovery-bootnodes"});

    final P2PConfig config = getResultingTekuConfiguration().beaconChain().p2pConfig();
    assertThat(config.getP2pDiscoveryBootnodes()).isEmpty();
  }

  @Test
  public void usingNetworkFromUrl() {
    final URL url =
        getClass().getClassLoader().getResource("tech/pegasys/teku/cli/options/constants.yaml");
    System.out.println(url);
    beaconNodeCommand.parse(new String[] {"--network", url.toString()});

    final TekuConfiguration config = getResultingTekuConfiguration();
    assertThat(config.eth2NetworkConfiguration().getConstants().toLowerCase())
        .isEqualToIgnoringWhitespace(url.toString().toLowerCase());
  }

  @Test
  public void setPeerRateLimit() {
    final P2PConfig config =
        getTekuConfigurationFromArguments("--Xpeer-rate-limit", "10").beaconChain().p2pConfig();
    assertThat(config.getPeerRateLimit()).isEqualTo(10);
  }

  @Test
  public void setPeerRequestLimit() {
    final P2PConfig config =
        getTekuConfigurationFromArguments("--Xpeer-request-limit", "10").beaconChain().p2pConfig();
    assertThat(config.getPeerRequestLimit()).isEqualTo(10);
  }

  @Test
  public void helpDisplaysDefaultNetwork() {
    beaconNodeCommand.parse(new String[] {"--help"});

    final String output = getCommandLineOutput();
    assertThat(output)
        .contains(
            "-n, --network=<NETWORK>    Represents which network to use.\n"
                + "                               Default: mainnet");
  }
}
