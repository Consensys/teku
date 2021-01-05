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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tech.pegasys.teku.cli.AbstractBeaconNodeCommandTest;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.networking.eth2.P2PConfig;
import tech.pegasys.teku.util.config.GlobalConfiguration;
import tech.pegasys.teku.util.config.NetworkDefinition;

public class NetworkOptionsTest extends AbstractBeaconNodeCommandTest {

  @Test
  public void shouldReadFromConfigurationFile() {
    final NetworkDefinition networkDefinition = NetworkDefinition.fromCliArg("mainnet");
    final GlobalConfiguration globalConfiguration =
        getGlobalConfigurationFromFile("networkOptions_config.yaml");
    assertThat(globalConfiguration.getConstants()).isEqualTo(networkDefinition.getConstants());
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"mainnet", "minimal", "swift", "medalla"})
  public void useDefaultsFromNetworkDefinition(final String networkName) {
    final NetworkDefinition networkDefinition = NetworkDefinition.fromCliArg(networkName);

    beaconNodeCommand.parse(new String[] {"--network", networkName});
    final TekuConfiguration tekuConfig = getResultingTekuConfiguration();
    final GlobalConfiguration config = tekuConfig.global();
    assertThat(tekuConfig.beaconChain().p2pConfig().getP2pDiscoveryBootnodes())
        .isEqualTo(networkDefinition.getDiscoveryBootnodes());
    assertThat(config.getConstants()).isEqualTo(networkDefinition.getConstants());
    assertThat(tekuConfig.weakSubjectivity().getWeakSubjectivityStateResource())
        .isEqualTo(networkDefinition.getInitialState());
    assertThat(config.getStartupTargetPeerCount())
        .isEqualTo(networkDefinition.getStartupTargetPeerCount());
    assertThat(config.getStartupTimeoutSeconds())
        .isEqualTo(networkDefinition.getStartupTimeoutSeconds());
    assertThat(config.getEth1DepositContractAddress())
        .isEqualTo(networkDefinition.getEth1DepositContractAddress().orElse(null));
    assertThat(config.getEth1Endpoints())
        .isEqualTo(networkDefinition.getEth1Endpoints().orElse(null));
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

    final GlobalConfiguration globalConfiguration = getResultingGlobalConfiguration();
    assertThat(globalConfiguration.getConstants()).isEqualTo(url);
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
