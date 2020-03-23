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
import static tech.pegasys.artemis.cli.OptionNames.CONFIG_FILE_OPTION_NAME;

import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
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

  //  @Test
  //  public void test() {
  //    final String[] args = {
  //      "validator", "generate",
  //      "--X-confirm-enabled", "false",
  //      "--keys-output-path", "/tmp/keys.yaml",
  //      "--deposit-amount-gwei", "32000000000",
  //      "--encrypted-keystore-enabled", "false",
  //      "--eth1-deposit-contract-address", "0xdddddddddddddddddddddddddddddddddddddddd",
  //      "--X-number-of-validators", "64",
  //      "--eth1-private-key",
  // "0x8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63",
  //      "--eth1-endpoint", "http://localhost:8545"
  //    };
  //
  //    beaconNodeCommand.parse(args);
  //
  //    //    final ArtemisConfiguration artemisConfiguration =
  //    //            beaconNodeCommand.getArtemisConfigurationDeprecated();
  //    //
  //    //    assertArtemisConfiguration(
  //    //            artemisConfiguration, expectedConfigurationBuilder(dataPath).build());
  //  }

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
      "--x-interop-genesis-time", "1",
      "--x-interop-owned-validator-start-index", "0",
      "--x-interop-owned-validator-count", "64",
      "--x-interop-start-state", "",
      "--x-interop-number-of-validators", "64",
      "--x-interop-enabled", "true",
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
        .setxInteropGenesisTime(1)
        .setxInteropStartState("")
        .setxInteropOwnedValidatorStartIndex(0)
        .setxInteropOwnedValidatorCount(64)
        .setxInteropNumberOfValidators(64)
        .setxInteropEnabled(true)
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
        .setValidatorsKeystoreFiles(Collections.emptyList())
        .setValidatorsKeystorePasswordFiles(Collections.emptyList())
        .setDataPath(dataPath.toString())
        .setDataStorageMode("prune")
        .setRestApiPort(5051)
        .setRestApiDocsEnabled(false)
        .setRestApiEnabled(false)
        .setRestApiInterface("127.0.0.1");
  }

  @SuppressWarnings("unused")
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
