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

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tech.pegasys.teku.cli.AbstractBeaconNodeCommandTest;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.datastructures.eth1.Eth1Address;
import tech.pegasys.teku.networking.eth2.P2PConfig;
import tech.pegasys.teku.networks.Eth2NetworkConfiguration;
import tech.pegasys.teku.util.config.GlobalConfiguration;

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
  @ValueSource(strings = {"mainnet", "minimal", "swift", "medalla"})
  public void useDefaultsFromNetworkDefinition(final String networkName) {
    final Eth2NetworkConfiguration eth2NetworkConfig =
        Eth2NetworkConfiguration.builder(networkName).build();

    beaconNodeCommand.parse(new String[] {"--network", networkName});
    final TekuConfiguration tekuConfig = getResultingTekuConfiguration();
    assertThat(tekuConfig.beaconChain().p2pConfig().getP2pDiscoveryBootnodes())
        .isEqualTo(eth2NetworkConfig.getDiscoveryBootnodes());
    assertThat(tekuConfig.eth2NetworkConfiguration().getConstants())
        .isEqualTo(eth2NetworkConfig.getConstants());
    assertThat(tekuConfig.weakSubjectivity().getWeakSubjectivityStateResource())
        .isEqualTo(eth2NetworkConfig.getInitialState());
    assertThat(tekuConfig.eth2NetworkConfiguration().getStartupTargetPeerCount())
        .isEqualTo(eth2NetworkConfig.getStartupTargetPeerCount());
    assertThat(tekuConfig.eth2NetworkConfiguration().getStartupTimeoutSeconds())
        .isEqualTo(eth2NetworkConfig.getStartupTimeoutSeconds());
    assertThat(tekuConfig.eth2NetworkConfiguration().getEth1DepositContractAddress())
        .isEqualTo(eth2NetworkConfig.getEth1DepositContractAddress());
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
    beaconNodeCommand.parse(new String[] {"--network", "topaz", "--p2p-discovery-bootnodes"});

    final P2PConfig config = getResultingTekuConfiguration().beaconChain().p2pConfig();
    assertThat(config.getP2pDiscoveryBootnodes()).isEmpty();
  }

  @Test
  public void usingNetworkFromUrl() {
    String url = "https://some.site/with/config.yaml";
    beaconNodeCommand.parse(new String[] {"--network", url});

    final TekuConfiguration config = getResultingTekuConfiguration();
    assertThat(config.eth2NetworkConfiguration().getConstants()).isEqualTo(url);
  }

  @Test
  public void setPeerRateLimit() {
    final GlobalConfiguration config =
        getGlobalConfigurationFromArguments("--Xpeer-rate-limit", "10");
    assertThat(config.getPeerRateLimit()).isEqualTo(10);
  }

  @Test
  public void setPeerRequestLimit() {
    final GlobalConfiguration config =
        getGlobalConfigurationFromArguments("--Xpeer-request-limit", "10");
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
