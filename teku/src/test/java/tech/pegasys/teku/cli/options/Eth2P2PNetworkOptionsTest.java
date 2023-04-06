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

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tech.pegasys.teku.cli.AbstractBeaconNodeCommandTest;
import tech.pegasys.teku.cli.OSUtils;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.P2PConfig;
import tech.pegasys.teku.networks.Eth2NetworkConfiguration;
import tech.pegasys.teku.spec.networks.Eth2Network;

public class Eth2P2PNetworkOptionsTest extends AbstractBeaconNodeCommandTest {

  @Test
  public void shouldReadFromConfigurationFile() {
    final Eth2NetworkConfiguration eth2NetworkConfig =
        Eth2NetworkConfiguration.builder("prater").build();
    final TekuConfiguration config = getTekuConfigurationFromFile("networkOptions_config.yaml");
    assertThat(config.eth2NetworkConfiguration().getConstants())
        .isEqualTo(eth2NetworkConfig.getConstants());
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"mainnet", "minimal", "swift", "prater"})
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
    assertThat(tekuConfig.eth2NetworkConfiguration().getInitialState())
        .isEqualTo(eth2NetworkConfig.getInitialState());

    // p2p config
    assertThat(tekuConfig.discovery().getBootnodes())
        .isEqualTo(eth2NetworkConfig.getDiscoveryBootnodes());

    // Rest api
    assertThat(tekuConfig.beaconChain().beaconRestApiConfig().getEth1DepositContractAddress())
        .isEqualTo(eth2NetworkConfig.getEth1DepositContractAddress());

    // Powchain
    assertThat(tekuConfig.powchain().getDepositContract())
        .isEqualTo(eth2NetworkConfig.getEth1DepositContractAddress());
    assertThat(tekuConfig.powchain().getDepositContractDeployBlock())
        .isEqualTo(eth2NetworkConfig.getEth1DepositContractDeployBlock());

    assertThat(
            createConfigBuilder()
                .eth2NetworkConfig(b -> b.applyNetworkDefaults(networkName))
                .build())
        .usingRecursiveComparison()
        .isEqualTo(tekuConfig);
  }

  @Test
  public void overrideDepositContract() {
    beaconNodeCommand.parse(
        new String[] {
          "--network",
          "mainnet",
          "--eth1-deposit-contract-address",
          "0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73"
        });

    TekuConfiguration tekuConfiguration = getResultingTekuConfiguration();
    Eth2NetworkConfiguration configuration = tekuConfiguration.eth2NetworkConfiguration();
    final Eth1Address configuredDepositContract = configuration.getEth1DepositContractAddress();
    assertThat(configuredDepositContract)
        .isEqualTo(Eth1Address.fromHexString("0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73"));
    assertThat(
            createConfigBuilder()
                .eth2NetworkConfig(
                    b -> {
                      b.applyNetworkDefaults(Eth2Network.MAINNET);
                      b.eth1DepositContractAddress("0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73");
                    })
                .build())
        .usingRecursiveComparison()
        .isEqualTo(tekuConfiguration);
  }

  @Test
  public void overrideDepositContractDeployBlock() {
    beaconNodeCommand.parse(
        new String[] {
          "--network", "mainnet", "--Xeth1-deposit-contract-deploy-block-override", "345"
        });

    final TekuConfiguration tekuConfiguration = getResultingTekuConfiguration();
    final Eth2NetworkConfiguration configuration = tekuConfiguration.eth2NetworkConfiguration();
    final Optional<UInt64> configuredDepositContractDeployBlock =
        configuration.getEth1DepositContractDeployBlock();
    assertThat(configuredDepositContractDeployBlock).isEqualTo(Optional.of(UInt64.valueOf(345L)));
    assertThat(
            createConfigBuilder()
                .eth2NetworkConfig(
                    b -> {
                      b.applyNetworkDefaults(Eth2Network.MAINNET);
                      b.eth1DepositContractDeployBlock(345L);
                    })
                .build())
        .usingRecursiveComparison()
        .isEqualTo(tekuConfiguration);
  }

  @Test
  public void overrideDefaultBootnodesWithEmptyList() {
    beaconNodeCommand.parse(new String[] {"--network", "prater", "--p2p-discovery-bootnodes"});

    TekuConfiguration tekuConfiguration = getResultingTekuConfiguration();
    final List<String> bootnodes = tekuConfiguration.discovery().getBootnodes();
    assertThat(bootnodes).isEmpty();

    assertThat(
            createConfigBuilder()
                .eth2NetworkConfig(
                    b -> {
                      b.applyNetworkDefaults(Eth2Network.PRATER);
                    })
                .discovery(b -> b.bootnodes(List.of()))
                .build())
        .usingRecursiveComparison()
        .isEqualTo(tekuConfiguration);
  }

  @Test
  public void usingNetworkFromUrl() {
    final URL url =
        getClass().getClassLoader().getResource("tech/pegasys/teku/cli/options/constants.yaml");
    beaconNodeCommand.parse(new String[] {"--network", url.toString()});

    final TekuConfiguration config = getResultingTekuConfiguration();
    assertThat(config.eth2NetworkConfiguration().getConstants().toLowerCase())
        .isEqualToIgnoringWhitespace(url.toString().toLowerCase());
    assertThat(
            createConfigBuilder()
                .eth2NetworkConfig(
                    b -> {
                      b.applyNetworkDefaults(url.toString());
                      b.discoveryBootnodes();
                    })
                .build())
        .usingRecursiveComparison()
        .isEqualTo(config);
  }

  @Test
  public void setPeerRateLimit() {
    TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments("--Xpeer-rate-limit", "10");
    final P2PConfig config = tekuConfiguration.beaconChain().p2pConfig();
    assertThat(config.getPeerRateLimit()).isEqualTo(10);
    assertThat(createConfigBuilder().p2p(b -> b.peerRateLimit(10)).build())
        .usingRecursiveComparison()
        .isEqualTo(tekuConfiguration);
  }

  @Test
  public void setPeerRequestLimit() {
    TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments("--Xpeer-request-limit", "10");
    final P2PConfig config = tekuConfiguration.beaconChain().p2pConfig();
    assertThat(config.getPeerRequestLimit()).isEqualTo(10);
    assertThat(createConfigBuilder().p2p(b -> b.peerRequestLimit(10)).build())
        .usingRecursiveComparison()
        .isEqualTo(tekuConfiguration);
  }

  @Test
  public void helpDisplaysDefaultNetwork() {
    beaconNodeCommand.parse(new String[] {"--help"});

    final String output = getCommandLineOutput();
    assertThat(output)
        .contains(
            "-n, --network=<NETWORK>    Represents which network to use."
                + OSUtils.CR
                + "                               Default: mainnet");
  }

  @Test
  public void initialState_shouldAcceptValue() {
    final String state = "state.ssz";
    final TekuConfiguration config = getTekuConfigurationFromArguments("--initial-state", state);
    assertThat(config.eth2NetworkConfiguration().getInitialState()).contains(state);
    assertThat(createConfigBuilder().eth2NetworkConfig(b -> b.customInitialState(state)).build())
        .usingRecursiveComparison()
        .isEqualTo(config);
  }

  @Test
  public void initialState_shouldDefaultToNetworkValue() {
    final String network = "prater";
    final Eth2NetworkConfiguration networkConfig =
        Eth2NetworkConfiguration.builder(network).build();
    assertThat(networkConfig.getInitialState()).isPresent();

    final TekuConfiguration config = getTekuConfigurationFromArguments("--network", network);
    assertThat(config.eth2NetworkConfiguration().getInitialState())
        .isEqualTo(networkConfig.getInitialState());
    assertThat(config.eth2NetworkConfiguration().isUsingCustomInitialState()).isFalse();
  }

  @Test
  public void initialState_shouldOverrideNetworkValue() {
    final String state = "state.ssz";
    final String network = "prater";
    final Eth2NetworkConfiguration networkConfig =
        Eth2NetworkConfiguration.builder(network).build();
    assertThat(networkConfig.getInitialState()).isPresent();

    final TekuConfiguration config =
        getTekuConfigurationFromArguments("--initial-state", state, "--network", network);
    assertThat(config.eth2NetworkConfiguration().getInitialState()).contains(state);
    assertThat(config.eth2NetworkConfiguration().isUsingCustomInitialState()).isTrue();
    assertThat(
            createConfigBuilder()
                .eth2NetworkConfig(
                    b -> {
                      b.applyNetworkDefaults(network);
                      b.customInitialState(state);
                    })
                .build())
        .usingRecursiveComparison()
        .isEqualTo(config);
  }

  @Test
  public void initialState_shouldDefault() {
    final TekuConfiguration config = getTekuConfigurationFromArguments();
    final Optional<String> defaultState = config.eth2NetworkConfiguration().getInitialState();
    assertThat(config.eth2NetworkConfiguration().getInitialState()).isEqualTo(defaultState);
    assertThat(config.eth2NetworkConfiguration().isUsingCustomInitialState()).isFalse();
  }
}
