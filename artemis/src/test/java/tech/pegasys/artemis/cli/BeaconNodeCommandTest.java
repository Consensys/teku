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

package tech.pegasys.artemis.cli;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static tech.pegasys.artemis.cli.BeaconNodeCommand.CONFIG_FILE_OPTION_NAME;
import static tech.pegasys.artemis.cli.options.DepositOptions.DEFAULT_ETH1_DEPOSIT_CONTRACT_ADDRESS;
import static tech.pegasys.artemis.cli.options.DepositOptions.DEFAULT_ETH1_ENDPOINT;
import static tech.pegasys.artemis.cli.options.InteropOptions.DEFAULT_X_INTEROP_ENABLED;
import static tech.pegasys.artemis.cli.options.InteropOptions.DEFAULT_X_INTEROP_GENESIS_TIME;
import static tech.pegasys.artemis.cli.options.InteropOptions.DEFAULT_X_INTEROP_OWNED_VALIDATOR_COUNT;
import static tech.pegasys.artemis.cli.options.LoggingOptions.DEFAULT_LOG_DESTINATION;
import static tech.pegasys.artemis.cli.options.LoggingOptions.DEFAULT_LOG_FILE;
import static tech.pegasys.artemis.cli.options.LoggingOptions.DEFAULT_LOG_FILE_NAME_PATTERN;
import static tech.pegasys.artemis.cli.options.MetricsOptions.DEFAULT_METRICS_CATEGORIES;
import static tech.pegasys.artemis.cli.options.P2POptions.DEFAULT_P2P_ADVERTISED_PORT;
import static tech.pegasys.artemis.cli.options.P2POptions.DEFAULT_P2P_DISCOVERY_ENABLED;
import static tech.pegasys.artemis.cli.options.P2POptions.DEFAULT_P2P_INTERFACE;
import static tech.pegasys.artemis.cli.options.P2POptions.DEFAULT_P2P_PORT;
import static tech.pegasys.artemis.cli.options.P2POptions.DEFAULT_P2P_PRIVATE_KEY_FILE;

import com.google.common.io.Resources;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;
import tech.pegasys.artemis.util.config.ArtemisConfigurationBuilder;
import tech.pegasys.artemis.util.config.NetworkDefinition;
import tech.pegasys.teku.logging.LoggingDestination;

public class BeaconNodeCommandTest {

  private final PrintWriter outputWriter = new PrintWriter(new StringWriter(), true);
  private final PrintWriter errorWriter = new PrintWriter(new StringWriter(), true);

  @SuppressWarnings("unchecked")
  private final Consumer<ArtemisConfiguration> startAction = mock(Consumer.class);

  private BeaconNodeCommand beaconNodeCommand =
      new BeaconNodeCommand(outputWriter, errorWriter, Collections.emptyMap(), startAction);

  @TempDir Path dataPath;

  @Test
  public void loadDefaultsWhenNoArgsArePassed() {
    // p2p-enabled default is "true" which require p2p-private-key-file to be non-null
    final String[] args = {"--data-path", dataPath.toString(), "--p2p-enabled", "false"};

    beaconNodeCommand.parse(args);

    assertArtemisConfiguration(expectedDefaultConfigurationBuilder().build());
  }

  @Test
  public void overrideEnvironmentValuesIfKeyIsPresentInCLIOptions() {
    final String[] args = createCliArgs();
    args[5] = "1.2.3.5";
    beaconNodeCommand =
        new BeaconNodeCommand(
            outputWriter,
            errorWriter,
            Collections.singletonMap("TEKU_P2P_INTERFACE", "1.2.3.4"),
            startAction);

    beaconNodeCommand.parse(args);

    assertArtemisConfiguration(expectedConfigurationBuilder().setP2pInterface("1.2.3.5").build());
  }

  @Test
  public void overrideConfigFileValuesIfKeyIsPresentInEnvironmentVariables() throws IOException {
    final Path configFile = createConfigFile();
    final String[] args = {CONFIG_FILE_OPTION_NAME, configFile.toString()};
    beaconNodeCommand =
        new BeaconNodeCommand(
            outputWriter,
            errorWriter,
            Collections.singletonMap("TEKU_P2P_INTERFACE", "1.2.3.5"),
            startAction);

    beaconNodeCommand.parse(args);

    assertArtemisConfiguration(
        expectedCompleteConfigInFileBuilder().setP2pInterface("1.2.3.5").build());
  }

  @Test
  public void overrideConfigFileValuesIfKeyIsPresentInCLIOptions() throws IOException {
    final Path configFile = createConfigFile();
    final String[] args = {
      CONFIG_FILE_OPTION_NAME, configFile.toString(), "--p2p-interface", "1.2.3.5"
    };

    beaconNodeCommand.parse(args);

    assertArtemisConfiguration(
        expectedCompleteConfigInFileBuilder().setP2pInterface("1.2.3.5").build());
  }

  @Test
  public void overrideDefaultValuesIfKeyIsPresentInEnvironmentVariables() {
    beaconNodeCommand =
        new BeaconNodeCommand(
            outputWriter,
            errorWriter,
            Map.of("TEKU_DATA_PATH", dataPath.toString(), "TEKU_P2P_ENABLED", "false"),
            startAction);

    beaconNodeCommand.parse(new String[] {});

    assertArtemisConfiguration(expectedDefaultConfigurationBuilder().build());
  }

  @Test
  public void overrideDefaultValuesIfKeyIsPresentInCLIOptions() {
    final String[] args = createCliArgs();

    beaconNodeCommand.parse(args);

    assertArtemisConfiguration(expectedConfigurationBuilder().build());
  }

  @Test
  public void overrideDefaultValuesIfKeyIsPresentInConfigFile() throws IOException {
    final Path configFile = createConfigFile();
    final String[] args = {CONFIG_FILE_OPTION_NAME, configFile.toString()};

    beaconNodeCommand.parse(args);

    assertArtemisConfiguration(expectedCompleteConfigInFileBuilder().build());
  }

  @Test
  public void overrideDefaultMetricsCategory() throws IOException {
    final Path configFile = createConfigFile();
    final String[] args = {
      CONFIG_FILE_OPTION_NAME, configFile.toString(), "--metrics-categories", "BEACON"
    };

    beaconNodeCommand.parse(args);
    assertArtemisConfiguration(
        expectedCompleteConfigInFileBuilder().setMetricsCategories(List.of("BEACON")).build());
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"mainnet", "minimal", "topaz"})
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

    final ArtemisConfiguration config = getResultingArtemisConfiguration();
    assertThat(config.getP2pDiscoveryBootnodes()).isEmpty();
  }

  @Test
  public void shouldUseDefaultOfBothAsLogDestinationDefault() {
    // This is important!
    // If it defaults to "both" or some other value custom log4j configs get overwritten
    beaconNodeCommand.parse(new String[0]);

    final ArtemisConfiguration config = getResultingArtemisConfiguration();
    assertThat(LoggingDestination.get(config.getLogDestination()))
        .isEqualTo(LoggingDestination.DEFAULT_BOTH);
  }

  private Path createConfigFile() throws IOException {
    final URL configFile = this.getClass().getResource("/complete_config.yaml");
    final String updatedConfig =
        Resources.toString(configFile, UTF_8)
            .replace("data-path: \".\"", "data-path: \"" + dataPath.toString() + "\"");
    return createTempFile(updatedConfig.getBytes(UTF_8));
  }

  private String[] createCliArgs() {
    return new String[] {
      "--network", "minimal",
      "--p2p-enabled", "false",
      "--p2p-interface", "1.2.3.4",
      "--p2p-port", "1234",
      "--p2p-discovery-enabled", "false",
      "--p2p-advertised-port", "9000",
      "--p2p-private-key-file", "path/to/file",
      "--Xinterop-genesis-time", "1",
      "--Xinterop-owned-validator-start-index", "0",
      "--Xinterop-owned-validator-count", "64",
      "--Xinterop-start-state", "",
      "--Xinterop-number-of-validators", "64",
      "--Xinterop-enabled", "true",
      "--eth1-deposit-contract-address", "0x77f7bED277449F51505a4C54550B074030d989bC",
      "--eth1-endpoint", "http://localhost:8545",
      "--metrics-enabled", "false",
      "--metrics-port", "8008",
      "--metrics-interface", "127.0.0.1",
      "--metrics-categories", "BEACON,LIBP2P,NETWORK,EVENTBUS,JVM,PROCESS",
      "--data-path", dataPath.toString(),
      "--data-storage-mode", "prune",
      "--rest-api-port", "5051",
      "--rest-api-docs-enabled", "false",
      "--rest-api-enabled", "false",
      "--rest-api-interface", "127.0.0.1"
    };
  }

  private ArtemisConfigurationBuilder expectedDefaultConfigurationBuilder() {
    return expectedConfigurationBuilder()
        .setEth1DepositContractAddress(DEFAULT_ETH1_DEPOSIT_CONTRACT_ADDRESS)
        .setEth1Endpoint(DEFAULT_ETH1_ENDPOINT)
        .setMetricsCategories(
            DEFAULT_METRICS_CATEGORIES.stream().map(Object::toString).collect(Collectors.toList()))
        .setP2pAdvertisedPort(DEFAULT_P2P_ADVERTISED_PORT)
        .setP2pDiscoveryEnabled(DEFAULT_P2P_DISCOVERY_ENABLED)
        .setP2pInterface(DEFAULT_P2P_INTERFACE)
        .setP2pPort(DEFAULT_P2P_PORT)
        .setP2pPrivateKeyFile(DEFAULT_P2P_PRIVATE_KEY_FILE)
        .setInteropEnabled(DEFAULT_X_INTEROP_ENABLED)
        .setInteropGenesisTime(DEFAULT_X_INTEROP_GENESIS_TIME)
        .setInteropOwnedValidatorCount(DEFAULT_X_INTEROP_OWNED_VALIDATOR_COUNT)
        .setLogDestination(DEFAULT_LOG_DESTINATION)
        .setLogFile(DEFAULT_LOG_FILE)
        .setLogFileNamePattern(DEFAULT_LOG_FILE_NAME_PATTERN);
  }

  private ArtemisConfigurationBuilder expectedCompleteConfigInFileBuilder() {
    return expectedConfigurationBuilder()
        .setLogFile("teku.log")
        .setLogDestination("both")
        .setLogFileNamePattern("teku_%d{yyyy-MM-dd}.log");
  }

  private ArtemisConfigurationBuilder expectedConfigurationBuilder() {
    return ArtemisConfiguration.builder()
        .setNetwork(NetworkDefinition.fromCliArg("minimal"))
        .setP2pEnabled(false)
        .setP2pInterface("1.2.3.4")
        .setP2pPort(1234)
        .setP2pDiscoveryEnabled(false)
        .setP2pDiscoveryBootnodes(Collections.emptyList())
        .setP2pAdvertisedPort(9000)
        .setP2pAdvertisedIp("127.0.0.1")
        .setP2pPrivateKeyFile("path/to/file")
        .setP2pPeerLowerBound(20)
        .setP2pPeerUpperBound(30)
        .setP2pStaticPeers(Collections.emptyList())
        .setInteropGenesisTime(1)
        .setInteropStartState("")
        .setInteropOwnedValidatorStartIndex(0)
        .setInteropOwnedValidatorCount(64)
        .setInteropNumberOfValidators(64)
        .setInteropEnabled(true)
        .setEth1DepositContractAddress("0x77f7bED277449F51505a4C54550B074030d989bC")
        .setEth1Endpoint("http://localhost:8545")
        .setMetricsEnabled(false)
        .setMetricsPort(8008)
        .setMetricsInterface("127.0.0.1")
        .setMetricsCategories(
            Arrays.asList("BEACON", "LIBP2P", "NETWORK", "EVENTBUS", "JVM", "PROCESS"))
        .setLogColorEnabled(true)
        .setLogDestination(DEFAULT_LOG_DESTINATION)
        .setLogFile(DEFAULT_LOG_FILE)
        .setLogFileNamePattern(DEFAULT_LOG_FILE_NAME_PATTERN)
        .setLogIncludeEventsEnabled(true)
        .setValidatorKeystoreFiles(Collections.emptyList())
        .setValidatorKeystorePasswordFiles(Collections.emptyList())
        .setValidatorExternalSignerTimeout(1000)
        .setDataPath(dataPath.toString())
        .setDataStorageMode("prune")
        .setRestApiPort(5051)
        .setRestApiDocsEnabled(false)
        .setRestApiEnabled(false)
        .setRestApiInterface("127.0.0.1");
  }

  private void assertArtemisConfiguration(final ArtemisConfiguration expected) {
    final ArtemisConfiguration actual = getResultingArtemisConfiguration();
    assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
  }

  private ArtemisConfiguration getResultingArtemisConfiguration() {
    final ArgumentCaptor<ArtemisConfiguration> configCaptor =
        ArgumentCaptor.forClass(ArtemisConfiguration.class);
    verify(startAction).accept(configCaptor.capture());

    return configCaptor.getValue();
  }

  private Path createTempFile(final byte[] contents) throws IOException {
    final Path file = java.nio.file.Files.createTempFile("config", "yaml");
    java.nio.file.Files.write(file, contents);
    file.toFile().deleteOnExit();
    return file;
  }
}
