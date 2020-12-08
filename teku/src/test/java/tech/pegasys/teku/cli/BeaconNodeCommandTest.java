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

package tech.pegasys.teku.cli;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.cli.BeaconNodeCommand.CONFIG_FILE_OPTION_NAME;
import static tech.pegasys.teku.cli.BeaconNodeCommand.LOG_FILE;
import static tech.pegasys.teku.cli.BeaconNodeCommand.LOG_PATTERN;
import static tech.pegasys.teku.cli.options.MetricsOptions.DEFAULT_METRICS_CATEGORIES;
import static tech.pegasys.teku.infrastructure.logging.LoggingDestination.DEFAULT_BOTH;
import static tech.pegasys.teku.util.config.StateStorageMode.PRUNE;

import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.infrastructure.logging.LoggingDestination;
import tech.pegasys.teku.storage.server.DatabaseVersion;
import tech.pegasys.teku.storage.server.VersionedDatabaseFactory;
import tech.pegasys.teku.util.cli.VersionProvider;
import tech.pegasys.teku.util.config.Eth1Address;
import tech.pegasys.teku.util.config.GlobalConfiguration;
import tech.pegasys.teku.util.config.GlobalConfigurationBuilder;
import tech.pegasys.teku.util.config.NetworkDefinition;
import tech.pegasys.teku.util.config.ValidatorPerformanceTrackingMode;

public class BeaconNodeCommandTest extends AbstractBeaconNodeCommandTest {

  @Test
  public void unknownOptionShouldDisplayShortHelpMessage() {
    final String[] args = {"--hlp"};

    beaconNodeCommand.parse(args);
    String str = getCommandLineOutput();
    assertThat(str).contains("Unknown option");
    assertThat(str).contains("To display full help:");
    assertThat(str).contains("--help");
    assertThat(str).doesNotContain("Default");
  }

  @Test
  public void invalidValueShouldDisplayShortHelpMessage() {
    final String[] args = {"--metrics-enabled=bob"};

    beaconNodeCommand.parse(args);
    String str = getCommandLineOutput();
    assertThat(str).contains("Invalid value");
    assertThat(str).contains("To display full help:");
    assertThat(str).contains("--help");
    assertThat(str).doesNotContain("Default");
  }

  @Test
  public void helpOptionShouldDisplayFullHelp() {
    final String[] args = {"--help"};

    beaconNodeCommand.parse(args);
    String str = getCommandLineOutput();
    assertThat(str).contains("Description:");
    assertThat(str).contains("Default");
    assertThat(str).doesNotContain("To display full help:");
  }

  @Test
  public void loadDefaultsWhenNoArgsArePassed() {
    // p2p-enabled default is "true" which require p2p-private-key-file to be non-null
    final String[] args = {"--data-path", dataPath.toString(), "--p2p-enabled", "false"};

    beaconNodeCommand.parse(args);

    assertTekuConfiguration(expectedDefaultConfigurationBuilder().build());
  }

  @Test
  void ignoreVersionAndHelpEnvVars() {
    beaconNodeCommand =
        new BeaconNodeCommand(
            outputWriter,
            errorWriter,
            Map.of("TEKU_VERSION", "1.2.3", "TEKU_HELP", "what?"),
            startAction);

    // No error from invalid --version or --help arg.
    assertThat(beaconNodeCommand.parse(new String[0])).isZero();
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

    TekuConfiguration expected =
        expectedConfigurationBuilder()
            .p2p(
                b -> {
                  b.p2pInterface("1.2.3.5");
                })
            .build();
    assertTekuConfiguration(expected);
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

    final TekuConfiguration expected =
        expectedCompleteConfigInFileBuilder()
            .p2p(
                b -> {
                  b.p2pInterface("1.2.3.5");
                })
            .build();
    assertTekuConfiguration(expected);
  }

  @Test
  public void overrideConfigFileValuesIfKeyIsPresentInCLIOptions() throws IOException {
    final Path configFile = createConfigFile();
    final String[] args = {
      CONFIG_FILE_OPTION_NAME, configFile.toString(), "--p2p-interface", "1.2.3.5"
    };

    beaconNodeCommand.parse(args);

    final TekuConfiguration expected =
        expectedCompleteConfigInFileBuilder()
            .p2p(
                b -> {
                  b.p2pInterface("1.2.3.5");
                })
            .build();
    assertTekuConfiguration(expected);
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

    assertTekuConfiguration(expectedDefaultConfigurationBuilder().build());
  }

  @Test
  public void overrideDefaultValuesIfKeyIsPresentInCLIOptions() {
    final String[] args = createCliArgs();

    beaconNodeCommand.parse(args);

    TekuConfiguration configuration = expectedConfigurationBuilder().build();
    assertTekuConfiguration(configuration);
  }

  @Test
  public void overrideDefaultValuesIfKeyIsPresentInConfigFile() throws IOException {
    final Path configFile = createConfigFile();
    final String[] args = {CONFIG_FILE_OPTION_NAME, configFile.toString()};

    beaconNodeCommand.parse(args);

    assertTekuConfiguration(expectedCompleteConfigInFileBuilder().build());
  }

  @Test
  public void interopEnabled_shouldNotRequireAValue() {
    final GlobalConfiguration globalConfiguration =
        getGlobalConfigurationFromArguments("--Xinterop-enabled");
    assertThat(globalConfiguration.isInteropEnabled()).isTrue();
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(
      strings = {
        "OFF", "FATAL", "ERROR", "WARN", "INFO", "DEBUG", "TRACE", "ALL", "off", "fatal", "error",
        "warn", "info", "debug", "trace", "all"
      })
  public void loglevel_shouldAcceptValues(String level) {
    final String[] args = {"--logging", level};
    beaconNodeCommand.parse(args);
    assertThat(beaconNodeCommand.getLogLevel().toString()).isEqualToIgnoringCase(level);
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"Off", "Fatal", "eRRoR", "WaRN", "InfO", "DebUG", "trACE", "All"})
  public void loglevel_shouldAcceptValuesMixedCase(String level) {
    final String[] args = {"--logging", level};
    beaconNodeCommand.parse(args);
    assertThat(beaconNodeCommand.getLogLevel().toString()).isEqualTo(level.toUpperCase());
  }

  @Test
  public void logLevel_shouldRejectInvalidValues() {
    final String[] args = {"--logging", "invalid"};
    beaconNodeCommand.parse(args);
    String str = getCommandLineOutput();
    assertThat(str).contains("'invalid' is not a valid log level. Supported values are");
  }

  @Test
  public void shouldSetLogFileToTheOptionProvided() {
    final String[] args = {"--log-file", "/hello/world.log"};
    beaconNodeCommand.parse(args);
    assertThat(beaconNodeCommand.tekuConfiguration().global().getLogFile())
        .isEqualTo("/hello/world.log");
  }

  @Test
  public void shouldSetLogFileToTheOptionProvidedRegardlessOfDataPath() {
    final String[] args = {
      "--log-file", "/hello/world.log",
      "--data-path", "/yo"
    };
    beaconNodeCommand.parse(args);
    assertThat(beaconNodeCommand.tekuConfiguration().global().getLogFile())
        .isEqualTo("/hello/world.log");
  }

  @Test
  public void shouldSetLogFileRelativeToSetDataDirectory() {
    final String[] args = {"--data-path", "/yo"};
    beaconNodeCommand.parse(args);
    assertThat(beaconNodeCommand.tekuConfiguration().global().getLogFile())
        .isEqualTo("/yo/logs/teku.log");
  }

  @Test
  public void shouldSetLogFileToDefaultDataDirectory() {
    final String[] args = {};
    beaconNodeCommand.parse(args);
    assertThat(beaconNodeCommand.tekuConfiguration().global().getLogFile())
        .isEqualTo(
            StringUtils.joinWith("/", VersionProvider.defaultStoragePath(), "logs", LOG_FILE));
  }

  @Test
  public void shouldSetLogPatternToDefaultDataDirectory() {
    final String[] args = {"--data-path", "/my/path"};
    beaconNodeCommand.parse(args);
    assertThat(beaconNodeCommand.tekuConfiguration().global().getLogFileNamePattern())
        .isEqualTo("/my/path/logs/" + LOG_PATTERN);
  }

  @Test
  public void shouldSetLogPatternOnCommandLine() {
    final String[] args = {"--data-path", "/my/path", "--log-file-name-pattern", "/z/%d.log"};
    beaconNodeCommand.parse(args);
    assertThat(beaconNodeCommand.tekuConfiguration().global().getLogFileNamePattern())
        .isEqualTo("/z/%d.log");
  }

  @Test
  public void shouldSetLogPatternOnWithoutPath() {
    final String[] args = {"--log-file-name-pattern", "%d.log"};
    final String expectedLogPatternPath =
        StringUtils.joinWith(
            System.getProperty("file.separator"),
            VersionProvider.defaultStoragePath(),
            "logs",
            "%d.log");
    beaconNodeCommand.parse(args);
    assertThat(beaconNodeCommand.tekuConfiguration().global().getLogFileNamePattern())
        .isEqualTo(expectedLogPatternPath);
  }

  private Path createConfigFile() throws IOException {
    final URL configFile = this.getClass().getResource("/complete_config.yaml");
    final String updatedConfig =
        Resources.toString(configFile, UTF_8)
            .replace(
                "data-path: \".\"",
                "data-path: \"" + dataPath.toString().replace("\\", "\\\\") + "\"");
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
      "--initial-state", "",
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
      "--rest-api-interface", "127.0.0.1",
      "--Xpeer-rate-limit", "500",
      "--Xpeer-request-limit", "50"
    };
  }

  private TekuConfiguration.Builder expectedDefaultConfigurationBuilder() {
    final NetworkDefinition networkDefinition = NetworkDefinition.fromCliArg("mainnet");
    return expectedConfigurationBuilder()
        .globalConfig(
            b ->
                b.setNetwork(networkDefinition)
                    .setEth1DepositContractAddress(null)
                    .setEth1Endpoint(null)
                    .setMetricsCategories(
                        DEFAULT_METRICS_CATEGORIES.stream()
                            .map(Object::toString)
                            .collect(Collectors.toList()))
                    .setInteropEnabled(false)
                    .setPeerRateLimit(500)
                    .setPeerRequestLimit(50)
                    .setInteropGenesisTime(0)
                    .setInteropOwnedValidatorCount(0)
                    .setLogDestination(DEFAULT_BOTH)
                    .setLogFile(StringUtils.joinWith("/", dataPath.toString(), "logs", LOG_FILE))
                    .setLogFileNamePattern(
                        StringUtils.joinWith("/", dataPath.toString(), "logs", LOG_PATTERN)))
        .p2p(
            b ->
                b.p2pAdvertisedPort(OptionalInt.empty())
                    .p2pDiscoveryEnabled(true)
                    .p2pDiscoveryBootnodes(networkDefinition.getDiscoveryBootnodes())
                    .p2pInterface("0.0.0.0")
                    .p2pPort(9000)
                    .p2pPrivateKeyFile(null))
        .validator(
            b ->
                b.validatorKeystoreLockingEnabled(true)
                    .validatorPerformanceTrackingMode(ValidatorPerformanceTrackingMode.ALL));
  }

  private TekuConfiguration.Builder expectedCompleteConfigInFileBuilder() {
    return expectedConfigurationBuilder()
        .globalConfig(
            builder ->
                builder
                    .setLogFile(StringUtils.joinWith("/", dataPath.toString(), "logs", LOG_FILE))
                    .setLogDestination(LoggingDestination.BOTH)
                    .setLogFileNamePattern(
                        StringUtils.joinWith("/", dataPath.toString(), "logs", LOG_PATTERN)));
  }

  private TekuConfiguration.Builder expectedConfigurationBuilder() {
    return TekuConfiguration.builder()
        .globalConfig(this::buildExpectedGlobalConfiguration)
        .data(b -> b.dataBasePath(dataPath))
        .p2p(
            b ->
                b.p2pEnabled(false)
                    .p2pInterface("1.2.3.4")
                    .p2pPort(1234)
                    .p2pDiscoveryEnabled(false)
                    .p2pAdvertisedPort(OptionalInt.of(9000))
                    .p2pAdvertisedIp(Optional.empty())
                    .p2pPrivateKeyFile("path/to/file")
                    .p2pPeerLowerBound(64)
                    .p2pPeerUpperBound(74)
                    .targetSubnetSubscriberCount(2)
                    .minimumRandomlySelectedPeerCount(12) // floor(20% of lower bound)
                    .p2pStaticPeers(Collections.emptyList()))
        .validator(
            b ->
                b.validatorExternalSignerTimeout(Duration.ofSeconds(1))
                    .validatorKeystoreLockingEnabled(true)
                    .validatorPerformanceTrackingMode(ValidatorPerformanceTrackingMode.ALL));
  }

  private void buildExpectedGlobalConfiguration(final GlobalConfigurationBuilder builder) {
    Eth1Address address = Eth1Address.fromHexString("0x77f7bED277449F51505a4C54550B074030d989bC");
    builder
        .setNetwork(NetworkDefinition.fromCliArg("minimal"))
        .setInteropGenesisTime(1)
        .setPeerRateLimit(500)
        .setPeerRequestLimit(50)
        .setInteropOwnedValidatorStartIndex(0)
        .setInteropOwnedValidatorCount(64)
        .setInteropNumberOfValidators(64)
        .setInteropEnabled(true)
        .setEth1DepositContractAddress(address)
        .setEth1Endpoint("http://localhost:8545")
        .setEth1LogsMaxBlockRange(10_000)
        .setEth1DepositsFromStorageEnabled(true)
        .setMetricsEnabled(false)
        .setMetricsPort(8008)
        .setMetricsInterface("127.0.0.1")
        .setMetricsCategories(
            Arrays.asList("BEACON", "LIBP2P", "NETWORK", "EVENTBUS", "JVM", "PROCESS"))
        .setMetricsHostAllowlist(List.of("127.0.0.1", "localhost"))
        .setLogColorEnabled(true)
        .setLogDestination(DEFAULT_BOTH)
        .setLogFile(StringUtils.joinWith("/", dataPath.toString(), "logs", LOG_FILE))
        .setLogFileNamePattern(StringUtils.joinWith("/", dataPath.toString(), "logs", LOG_PATTERN))
        .setLogIncludeEventsEnabled(true)
        .setLogIncludeValidatorDutiesEnabled(true)
        .setDataStorageMode(PRUNE)
        .setDataStorageFrequency(VersionedDatabaseFactory.DEFAULT_STORAGE_FREQUENCY)
        .setDataStorageCreateDbVersion(DatabaseVersion.DEFAULT_VERSION.getValue())
        .setHotStatePersistenceFrequencyInEpochs(2)
        .setIsBlockProcessingAtStartupDisabled(true)
        .setRestApiPort(5051)
        .setRestApiDocsEnabled(false)
        .setRestApiEnabled(false)
        .setRestApiInterface("127.0.0.1")
        .setRestApiHostAllowlist(List.of("127.0.0.1", "localhost"))
        .setRestApiCorsAllowedOrigins(new ArrayList<>());
  }

  private void assertTekuConfiguration(final TekuConfiguration expected) {
    final TekuConfiguration actual = getResultingTekuConfiguration();
    assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
  }

  private Path createTempFile(final byte[] contents) throws IOException {
    final Path file = java.nio.file.Files.createTempFile("config", "yaml");
    java.nio.file.Files.write(file, contents);
    file.toFile().deleteOnExit();
    return file;
  }
}
