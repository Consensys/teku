/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.networks;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import java.net.URL;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.spec.networks.Eth2Network;

public class Eth2NetworkConfigurationTest {

  @ParameterizedTest(name = "{0}")
  @MethodSource("getDefinedNetworks")
  public void build_shouldBuildKnownNetworks(
      final Eth2Network network, final NetworkDefinition networkDefinition) {
    final Eth2NetworkConfiguration networkConfig =
        Eth2NetworkConfiguration.builder(network).build();
    final Eth2NetworkConfiguration.Builder networkConfigBuilder =
        Eth2NetworkConfiguration.builder();
    networkDefinition.configure(networkConfigBuilder);

    assertThat(networkConfig.getConstants()).isEqualTo(network.configName());
    assertThat(networkConfigBuilder.build()).usingRecursiveComparison().isEqualTo(networkConfig);
  }

  @Test
  public void builder_usingConstantsUrl() {
    final URL url =
        getClass().getClassLoader().getResource("tech/pegasys/teku/networks/test-constants.yaml");
    final Eth2NetworkConfiguration config =
        Eth2NetworkConfiguration.builder(url.toString()).build();
    assertThat(config.getConstants()).isEqualTo(url.toString());
    assertThat(config.getSpec().getGenesisSpecConfig().getConfigName())
        .isEqualTo("Custom Constants");
  }

  @Test
  public void constants_usingConstantsUrl() {
    final URL url =
        getClass().getClassLoader().getResource("tech/pegasys/teku/networks/test-constants.yaml");
    final Eth2NetworkConfiguration config =
        Eth2NetworkConfiguration.builder().constants(url.toString()).build();
    assertThat(config.getConstants()).isEqualTo(url.toString());
    assertThat(config.getSpec().getGenesisSpecConfig().getConfigName())
        .isEqualTo("Custom Constants");
  }

  @Test
  public void applyNetworkDefaults_shouldOverwritePreviouslySetValues() {
    List<Arguments> definedNetworks = getDefinedNetworks().collect(Collectors.toList());

    for (Arguments networkA : definedNetworks) {
      for (Arguments networkB : definedNetworks) {
        final Eth2Network networkAName = ((Eth2Network) networkA.get()[0]);
        final Eth2Network networkBName = ((Eth2Network) networkB.get()[0]);

        final Eth2NetworkConfiguration.Builder builder = Eth2NetworkConfiguration.builder();
        builder.applyNetworkDefaults(networkAName);
        builder.applyNetworkDefaults(networkBName);

        assertThat(builder)
            .usingRecursiveComparison()
            .isEqualTo(Eth2NetworkConfiguration.builder(networkBName));
      }
    }
  }

  @Test
  public void applyNamedNetworkDefaults_shouldOverwritePreviouslySetValues() {
    List<Arguments> definedNetworks = getDefinedNetworks().collect(Collectors.toList());

    for (Arguments networkA : definedNetworks) {
      for (Arguments networkB : definedNetworks) {
        final NetworkDefinition networkADef = ((NetworkDefinition) networkA.get()[1]);
        final NetworkDefinition networkBDef = ((NetworkDefinition) networkB.get()[1]);

        Eth2NetworkConfiguration.Builder builder = Eth2NetworkConfiguration.builder();
        networkADef.configure(builder);
        networkBDef.configure(builder);

        final Eth2Network networkBName = ((Eth2Network) networkB.get()[0]);
        assertThat(builder)
            .usingRecursiveComparison()
            .isEqualTo(Eth2NetworkConfiguration.builder(networkBName));
      }
    }
  }

  public static Stream<Arguments> getDefinedNetworks() {
    return Stream.of(
        Arguments.of(Eth2Network.MAINNET, (NetworkDefinition) b -> b.applyMainnetNetworkDefaults()),
        Arguments.of(Eth2Network.MINIMAL, (NetworkDefinition) b -> b.applyMinimalNetworkDefaults()),
        Arguments.of(Eth2Network.PYRMONT, (NetworkDefinition) b -> b.applyPyrmontNetworkDefaults()),
        Arguments.of(Eth2Network.SWIFT, (NetworkDefinition) b -> b.applySwiftNetworkDefaults()),
        Arguments.of(
            Eth2Network.LESS_SWIFT, (NetworkDefinition) b -> b.applyLessSwiftNetworkDefaults()));
  }

  @FunctionalInterface
  private interface NetworkDefinition {
    Eth2NetworkConfiguration.Builder configure(Eth2NetworkConfiguration.Builder builder);
  }
}
