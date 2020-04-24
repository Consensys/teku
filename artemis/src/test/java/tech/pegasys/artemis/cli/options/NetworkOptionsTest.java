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

package tech.pegasys.artemis.cli.options;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.artemis.util.config.NetworkDefinition.MAINNET;
import static tech.pegasys.artemis.util.config.NetworkDefinition.MINIMAL;
import static tech.pegasys.artemis.util.config.NetworkDefinition.TOPAZ;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tech.pegasys.artemis.cli.AbstractBeaconNodeCommandTest;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;
import tech.pegasys.artemis.util.config.NetworkDefinition;

public class NetworkOptionsTest extends AbstractBeaconNodeCommandTest {

  @Test
  public void shouldReadFromConfigurationFile() {
    final NetworkDefinition networkDefinition = NetworkDefinition.fromCliArg(MAINNET);
    final ArtemisConfiguration artemisConfiguration =
        getArtemisConfigurationFromFile("networkOptions_config.yaml");
    assertThat(artemisConfiguration.getConstants()).isEqualTo(networkDefinition.getConstants());
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {MAINNET, MINIMAL, TOPAZ})
  public void useDefaultsFromNetworkDefinition(final String networkName) {
    final NetworkDefinition networkDefinition = NetworkDefinition.fromCliArg(networkName);

    beaconNodeCommand.parse(new String[] {"--network", networkName});
    final ArtemisConfiguration config = getResultingArtemisConfiguration();
    assertThat(config.getP2pDiscoveryBootnodes())
        .isEqualTo(networkDefinition.getDiscoveryBootnodes());
    assertThat(config.getConstants()).isEqualTo(networkDefinition.getConstants());
    assertThat(config.getStartupTargetPeerCount())
        .isEqualTo(networkDefinition.getStartupTargetPeerCount());
    assertThat(config.getStartupTimeoutSeconds())
        .isEqualTo(networkDefinition.getStartupTimeoutSeconds());
    assertThat(config.getEth1DepositContractAddress())
        .isEqualTo(networkDefinition.getEth1DepositContractAddress().orElse(null));
    assertThat(config.getEth1Endpoint())
        .isEqualTo(networkDefinition.getEth1Endpoint().orElse(null));
  }

  @Test
  public void overrideDefaultBootnodesWithEmptyList() {
    beaconNodeCommand.parse(new String[] {"--network", "topaz", "--p2p-discovery-bootnodes"});

    final ArtemisConfiguration artemisConfiguration = getResultingArtemisConfiguration();
    assertThat(artemisConfiguration.getP2pDiscoveryBootnodes()).isEmpty();
  }

  @Test
  public void usingNetworkFromUrl() {
    String url =
        "https://github.com/eth2-clients/eth2-testnets/raw/master/prysm/Sapphire(v0.9.4)/config.yaml";
    beaconNodeCommand.parse(new String[] {"--network", url});

    final ArtemisConfiguration artemisConfiguration = getResultingArtemisConfiguration();
    assertThat(artemisConfiguration.getP2pDiscoveryBootnodes()).isEmpty();
    assertThat(artemisConfiguration.getConstants()).isEqualTo(url);
  }
}
