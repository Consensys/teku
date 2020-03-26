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
import static tech.pegasys.artemis.cli.DefaultOptionValues.DEFAULT_DATA_PATH;
import static tech.pegasys.artemis.cli.DefaultOptionValues.DEFAULT_ETH1_DEPOSIT_CONTRACT_ADDRESS;
import static tech.pegasys.artemis.cli.DefaultOptionValues.DEFAULT_ETH1_ENDPOINT;
import static tech.pegasys.artemis.cli.DefaultOptionValues.DEFAULT_METRICS_CATEGORIES;
import static tech.pegasys.artemis.cli.DefaultOptionValues.DEFAULT_P2P_ADVERTISED_PORT;
import static tech.pegasys.artemis.cli.DefaultOptionValues.DEFAULT_P2P_DISCOVERY_ENABLED;
import static tech.pegasys.artemis.cli.DefaultOptionValues.DEFAULT_P2P_INTERFACE;
import static tech.pegasys.artemis.cli.DefaultOptionValues.DEFAULT_P2P_PORT;
import static tech.pegasys.artemis.cli.DefaultOptionValues.DEFAULT_P2P_PRIVATE_KEY_FILE;
import static tech.pegasys.artemis.cli.DefaultOptionValues.DEFAULT_X_INTEROP_ENABLED;
import static tech.pegasys.artemis.cli.DefaultOptionValues.DEFAULT_X_INTEROP_GENESIS_TIME;
import static tech.pegasys.artemis.cli.DefaultOptionValues.DEFAULT_X_INTEROP_OWNED_VALIDATOR_COUNT;
import static tech.pegasys.artemis.cli.OptionNames.CONFIG_FILE_OPTION_NAME;

import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;
import tech.pegasys.artemis.util.config.ArtemisConfigurationBuilder;

public class BeaconNodeCommandTest {

  private BeaconNodeCommand beaconNodeCommand;
  @TempDir Path dataPath;

  @BeforeEach
  void setUp() {
    beaconNodeCommand = new BeaconNodeCommand();
  }

  @AfterEach
  void tearDown() {
    beaconNodeCommand.stop();
  }

  @Test
  public void loadDefaultsWhenNoArgsArePassed() {
    // p2p-enabled default is "true" which require p2p-private-key-file to be non-null
    final String[] args = {"--p2p-enabled", "false"};

    beaconNodeCommand.parse(args);

    final ArtemisConfiguration artemisConfiguration = beaconNodeCommand.getArtemisConfiguration();

    assertArtemisConfiguration(
        artemisConfiguration,
        expectedConfigurationBuilder(DEFAULT_DATA_PATH)
            .setEth1DepositContractAddress(DEFAULT_ETH1_DEPOSIT_CONTRACT_ADDRESS)
            .setEth1Endpoint(DEFAULT_ETH1_ENDPOINT)
            .setMetricsCategories(DEFAULT_METRICS_CATEGORIES)
            .setP2pAdvertisedPort(DEFAULT_P2P_ADVERTISED_PORT)
            .setP2pDiscoveryEnabled(DEFAULT_P2P_DISCOVERY_ENABLED)
            .setP2pInterface(DEFAULT_P2P_INTERFACE)
            .setP2pPort(DEFAULT_P2P_PORT)
            .setP2pPrivateKeyFile(DEFAULT_P2P_PRIVATE_KEY_FILE)
            .setInteropEnabled(DEFAULT_X_INTEROP_ENABLED)
            .setInteropGenesisTime(DEFAULT_X_INTEROP_GENESIS_TIME)
            .setInteropOwnedValidatorCount(DEFAULT_X_INTEROP_OWNED_VALIDATOR_COUNT)
            .build());
  }

  @Test
  public void overrideConfigFileValuesIfKeyIsPresentInCLIOptions() throws IOException {
    final Path toml = createConfigFile();
    final String[] args = {CONFIG_FILE_OPTION_NAME, toml.toString(), "--p2p-interface", "1.2.3.5"};

    beaconNodeCommand.parse(args);

    final ArtemisConfiguration artemisConfiguration = beaconNodeCommand.getArtemisConfiguration();

    assertArtemisConfiguration(
        artemisConfiguration,
        expectedConfigurationBuilder(dataPath).setP2pInterface("1.2.3.5").build());
  }

  @Test
  public void overrideDefaultValuesIfKeyIsPresentInCLIOptions() {
    final String[] args = {
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
      "--metrics-categories", "BEACONCHAIN,JVM,PROCESS",
      "--data-path", dataPath.toString(),
      "--data-storage-mode", "prune",
      "--rest-api-port", "5051",
      "--rest-api-docs-enabled", "false",
      "--rest-api-enabled", "false",
      "--rest-api-interface", "127.0.0.1"
    };

    beaconNodeCommand.parse(args);

    final ArtemisConfiguration artemisConfiguration = beaconNodeCommand.getArtemisConfiguration();

    assertArtemisConfiguration(
        artemisConfiguration, expectedConfigurationBuilder(dataPath).build());
  }

  @Test
  public void overrideDefaultValuesIfKeyIsPresentInConfigFile() throws IOException {
    final Path toml = createConfigFile();
    final String[] args = {CONFIG_FILE_OPTION_NAME, toml.toString()};

    beaconNodeCommand.parse(args);

    final ArtemisConfiguration artemisConfiguration = beaconNodeCommand.getArtemisConfiguration();

    assertArtemisConfiguration(
        artemisConfiguration, expectedConfigurationBuilder(dataPath).build());
  }

  private Path createConfigFile() throws IOException {
    final URL configFile = this.getClass().getResource("/complete_config.toml");
    final String updatedConfig =
        Resources.toString(configFile, UTF_8)
            .replace("data-path=\".\"", "data-path=\"" + dataPath.toString() + "\"");
    return createTempFile("toml", updatedConfig.getBytes(UTF_8));
  }

  private ArtemisConfigurationBuilder expectedConfigurationBuilder(final Path dataPath) {
    return expectedConfigurationBuilder(dataPath.toString());
  }

  private ArtemisConfigurationBuilder expectedConfigurationBuilder(final String dataPath) {
    return ArtemisConfiguration.builder()
        .setNetwork("minimal")
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
        .setMetricsCategories(Arrays.asList("BEACONCHAIN", "JVM", "PROCESS"))
        .setLogColourEnabled(true)
        .setLogDestination("both")
        .setLogFile("teku.log")
        .setLogFileNamePattern("teku_%d{yyyy-MM-dd}.log")
        .setLogIncludeEventsEnabled(true)
        .setValidatorKeystoreFiles(Collections.emptyList())
        .setValidatorKeystorePasswordFiles(Collections.emptyList())
        .setValidatorExternalSignerTimeout(1000)
        .setDataPath(dataPath)
        .setDataStorageMode("prune")
        .setRestApiPort(5051)
        .setRestApiDocsEnabled(false)
        .setRestApiEnabled(false)
        .setRestApiInterface("127.0.0.1");
  }

  private void assertArtemisConfiguration(
      final ArtemisConfiguration actual, final ArtemisConfiguration expected) {
    assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
  }

  private Path createTempFile(final String filename, final byte[] contents) throws IOException {
    final Path file = java.nio.file.Files.createTempFile(filename, "");
    java.nio.file.Files.write(file, contents);
    file.toFile().deleteOnExit();
    return file;
  }
}
