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

package tech.pegasys.teku.networks;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import java.net.URL;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
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
  void shouldAliasGoerliToPrater() {
    final Eth2NetworkConfiguration goerliConfig =
        Eth2NetworkConfiguration.builder("goerli").build();
    final Eth2NetworkConfiguration praterConfig =
        Eth2NetworkConfiguration.builder("prater").build();
    assertThat(goerliConfig).usingRecursiveComparison().isEqualTo(praterConfig);
  }

  @Test
  public void builder_usingConstantsUrl() {
    final URL url =
        getClass().getClassLoader().getResource("tech/pegasys/teku/networks/test-constants.yaml");
    final Eth2NetworkConfiguration config =
        Eth2NetworkConfiguration.builder(url.toString()).build();
    assertThat(config.getConstants()).isEqualTo(url.toString());
    assertThat(config.getSpec().getGenesisSpecConfig().getMaxCommitteesPerSlot()).isEqualTo(4);
  }

  @Test
  public void constants_usingConstantsUrl() {
    final URL url =
        getClass().getClassLoader().getResource("tech/pegasys/teku/networks/test-constants.yaml");
    final Eth2NetworkConfiguration config =
        Eth2NetworkConfiguration.builder().constants(url.toString()).build();
    assertThat(config.getConstants()).isEqualTo(url.toString());
    assertThat(config.getSpec().getGenesisSpecConfig().getMaxCommitteesPerSlot()).isEqualTo(4);
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

  @ParameterizedTest(name = "{0}")
  @MethodSource("getDefinedNetworks")
  public void bootnodesFromNetworkDefaults_CanBeParsed(
      final Eth2Network network, final NetworkDefinition networkDefinition) {
    final Eth2NetworkConfiguration config = Eth2NetworkConfiguration.builder(network).build();
    final Eth2NetworkConfiguration.Builder networkConfigBuilder =
        Eth2NetworkConfiguration.builder();
    networkDefinition.configure(networkConfigBuilder);

    List<NodeRecord> nodeRecords = parseBootnodes(config.getDiscoveryBootnodes());

    assertThat(nodeRecords.stream().map(NodeRecord::asEnr))
        .containsExactlyInAnyOrderElementsOf(config.getDiscoveryBootnodes());
  }

  @SuppressWarnings("Convert2MethodRef")
  public static Stream<Arguments> getDefinedNetworks() {
    return Stream.of(
        Arguments.of(Eth2Network.MAINNET, (NetworkDefinition) b -> b.applyMainnetNetworkDefaults()),
        Arguments.of(Eth2Network.MINIMAL, (NetworkDefinition) b -> b.applyMinimalNetworkDefaults()),
        Arguments.of(Eth2Network.PRATER, (NetworkDefinition) b -> b.applyPraterNetworkDefaults()),
        Arguments.of(Eth2Network.ROPSTEN, (NetworkDefinition) b -> b.applyRopstenNetworkDefaults()),
        Arguments.of(Eth2Network.KILN, (NetworkDefinition) b -> b.applyKilnNetworkDefaults()),
        Arguments.of(Eth2Network.SWIFT, (NetworkDefinition) b -> b.applySwiftNetworkDefaults()),
        Arguments.of(
            Eth2Network.LESS_SWIFT, (NetworkDefinition) b -> b.applyLessSwiftNetworkDefaults()),
        Arguments.of(Eth2Network.GNOSIS, (NetworkDefinition) b -> b.applyGnosisNetworkDefaults()));
  }

  private List<NodeRecord> parseBootnodes(List<String> bootnodes) {
    final NodeRecordFactory nodeRecordFactory = NodeRecordFactory.DEFAULT;

    return bootnodes.stream()
        .map(enr -> enr.startsWith("enr:") ? enr.substring("enr:".length()) : enr)
        .map(nodeRecordFactory::fromBase64)
        .collect(Collectors.toList());
  }

  @FunctionalInterface
  private interface NetworkDefinition {
    Eth2NetworkConfiguration.Builder configure(Eth2NetworkConfiguration.Builder builder);
  }
}
