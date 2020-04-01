/*
 * Copyright 2019 ConsenSys AG.
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

import com.google.common.annotations.VisibleForTesting;
import java.io.File;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import org.apache.logging.log4j.Level;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import tech.pegasys.artemis.BeaconNode;
import tech.pegasys.artemis.cli.options.BeaconRestApiOptions;
import tech.pegasys.artemis.cli.options.DataOptions;
import tech.pegasys.artemis.cli.options.DepositOptions;
import tech.pegasys.artemis.cli.options.InteropOptions;
import tech.pegasys.artemis.cli.options.LoggingOptions;
import tech.pegasys.artemis.cli.options.MetricsOptions;
import tech.pegasys.artemis.cli.options.NetworkOptions;
import tech.pegasys.artemis.cli.options.OutputOptions;
import tech.pegasys.artemis.cli.options.P2POptions;
import tech.pegasys.artemis.cli.options.ValidatorOptions;
import tech.pegasys.artemis.cli.subcommand.DepositCommand;
import tech.pegasys.artemis.cli.subcommand.GenesisCommand;
import tech.pegasys.artemis.cli.subcommand.PeerCommand;
import tech.pegasys.artemis.cli.subcommand.TransitionCommand;
import tech.pegasys.artemis.cli.util.CascadingDefaultProvider;
import tech.pegasys.artemis.cli.util.EnvironmentVariableDefaultProvider;
import tech.pegasys.artemis.cli.util.TomlConfigFileDefaultProvider;
import tech.pegasys.artemis.storage.server.DatabaseStorageException;
import tech.pegasys.artemis.util.cli.LogTypeConverter;
import tech.pegasys.artemis.util.cli.VersionProvider;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;
import tech.pegasys.teku.logging.LoggingConfigurator;

@SuppressWarnings("unused")
@Command(
    name = "teku",
    subcommands = {
      TransitionCommand.class,
      PeerCommand.class,
      DepositCommand.class,
      GenesisCommand.class
    },
    abbreviateSynopsis = true,
    description = "Run the Teku beacon chain client and validator",
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider.class,
    synopsisHeading = "%n",
    descriptionHeading = "%nDescription:%n%n",
    optionListHeading = "%nOptions:%n",
    footerHeading = "%n",
    footer = "Teku is licensed under the Apache License 2.0")
public class BeaconNodeCommand implements Callable<Integer> {

  static final String CONFIG_FILE_OPTION_NAME = "--config-file";
  static final String DEFAULT_CONFIG_FILE = "./config/config.toml";
  private final Map<String, String> environment;

  @Option(
      names = {"-l", "--logging"},
      converter = LogTypeConverter.class,
      paramLabel = "<LOG VERBOSITY LEVEL>",
      description =
          "Logging verbosity levels: OFF, FATAL, WARN, INFO, DEBUG, TRACE, ALL (default: INFO).",
      arity = "1")
  private Level logLevel;

  @Option(
      names = {"-c", CONFIG_FILE_OPTION_NAME},
      paramLabel = "<FILENAME>",
      description = "Path/filename of the config file",
      arity = "1")
  private String configFile = DEFAULT_CONFIG_FILE;

  @Mixin private NetworkOptions networkOptions;
  @Mixin private P2POptions p2POptions;
  @Mixin private InteropOptions interopOptions;
  @Mixin private ValidatorOptions validatorOptions;
  @Mixin private DepositOptions depositOptions;
  @Mixin private LoggingOptions loggingOptions;
  @Mixin private OutputOptions outputOptions;
  @Mixin private MetricsOptions metricsOptions;
  @Mixin private DataOptions dataOptions;
  @Mixin private BeaconRestApiOptions beaconRestApiOptions;

  private ArtemisConfiguration artemisConfiguration;
  private BeaconNode node;

  public BeaconNodeCommand(final Map<String, String> environment) {
    this.environment = environment;
  }

  public String getConfigFile() {
    return configFile;
  }

  public void parse(final String[] args) {
    final CommandLine commandLine = new CommandLine(this).setCaseInsensitiveEnumValuesAllowed(true);

    final Optional<File> maybeConfigFile = maybeFindConfigFile(commandLine, args);
    final EnvironmentVariableDefaultProvider environmentVariableDefaultProvider =
        new EnvironmentVariableDefaultProvider(environment);
    final CommandLine.IDefaultValueProvider defaultValueProvider;
    if (maybeConfigFile.isPresent()) {
      defaultValueProvider =
          new CascadingDefaultProvider(
              environmentVariableDefaultProvider,
              new TomlConfigFileDefaultProvider(commandLine, maybeConfigFile.get()));
    } else {
      defaultValueProvider = environmentVariableDefaultProvider;
    }
    commandLine.setDefaultValueProvider(defaultValueProvider).execute(args);
  }

  @Override
  public Integer call() {
    try {
      setLogLevels();
      artemisConfiguration = artemisConfiguration();
      node = new BeaconNode(artemisConfiguration);
      node.start();
      // Detect SIGTERM
      Runtime.getRuntime()
          .addShutdownHook(
              new Thread(
                  () -> {
                    System.out.println("Teku is shutting down");
                    node.stop();
                  }));
      return 0;
    } catch (DatabaseStorageException ex) {
      System.err.println(ex.getMessage());
      System.exit(1);
    } catch (Throwable t) {
      System.err.println("Teku failed to start.");
      t.printStackTrace();
      System.exit(1);
    }
    return 1;
  }

  @VisibleForTesting
  ArtemisConfiguration getArtemisConfiguration() {
    return artemisConfiguration;
  }

  @VisibleForTesting
  public void stop() {
    node.stop();
  }

  private Optional<File> maybeFindConfigFile(final CommandLine commandLine, final String[] args) {
    final CommandLine.ParseResult parseResult = commandLine.parseArgs(args);
    if (parseResult.hasMatchedOption(CONFIG_FILE_OPTION_NAME)) {
      final CommandLine.Model.OptionSpec configFileOption =
          parseResult.matchedOption(CONFIG_FILE_OPTION_NAME);
      try {
        return Optional.of(new File(configFileOption.getter().get().toString()));
      } catch (final Exception e) {
        throw new CommandLine.ExecutionException(commandLine, e.getMessage(), e);
      }
    } else {
      return Optional.empty();
    }
  }

  private void setLogLevels() {
    if (logLevel != null) {
      // set log level per CLI flags
      LoggingConfigurator.setAllLevels(logLevel);
    }
  }

  private ArtemisConfiguration artemisConfiguration() {
    // TODO: validate option dependencies
    return ArtemisConfiguration.builder()
        .setNetwork(networkOptions.getNetwork())
        .setP2pEnabled(p2POptions.isP2pEnabled())
        .setP2pInterface(p2POptions.getP2pInterface())
        .setP2pPort(p2POptions.getP2pPort())
        .setP2pDiscoveryEnabled(p2POptions.isP2pDiscoveryEnabled())
        .setP2pDiscoveryBootnodes(p2POptions.getP2pDiscoveryBootnodes())
        .setP2pAdvertisedIp(p2POptions.getP2pAdvertisedIp())
        .setP2pAdvertisedPort(p2POptions.getP2pAdvertisedPort())
        .setP2pPrivateKeyFile(p2POptions.getP2pPrivateKeyFile())
        .setP2pPeerLowerBound(p2POptions.getP2pLowerBound())
        .setP2pPeerUpperBound(p2POptions.getP2pUpperBound())
        .setP2pStaticPeers(p2POptions.getP2pStaticPeers())
        .setInteropGenesisTime(interopOptions.getInteropGenesisTime())
        .setInteropOwnedValidatorStartIndex(interopOptions.getInteropOwnerValidatorStartIndex())
        .setInteropOwnedValidatorCount(interopOptions.getInteropOwnerValidatorCount())
        .setInteropStartState(interopOptions.getInteropStartState())
        .setInteropNumberOfValidators(interopOptions.getInteropNumberOfValidators())
        .setInteropEnabled(interopOptions.isInteropEnabled())
        .setValidatorKeyFile(validatorOptions.getValidatorKeyFile())
        .setValidatorKeystoreFiles(validatorOptions.getValidatorKeystoreFiles())
        .setValidatorKeystorePasswordFiles(validatorOptions.getValidatorKeystorePasswordFiles())
        .setValidatorExternalSignerPublicKeys(
            validatorOptions.getValidatorExternalSignerPublicKeys())
        .setValidatorExternalSignerUrl(validatorOptions.getValidatorExternalSignerUrl())
        .setValidatorExternalSignerTimeout(validatorOptions.getValidatorExternalSignerTimeout())
        .setEth1DepositContractAddress(depositOptions.getEth1DepositContractAddress())
        .setEth1Endpoint(depositOptions.getEth1Endpoint())
        .setLogColourEnabled(loggingOptions.isLogColourEnabled())
        .setLogIncludeEventsEnabled(loggingOptions.isLogIncludeEventsEnabled())
        .setLogDestination(loggingOptions.getLogDestination())
        .setLogFile(loggingOptions.getLogFile())
        .setLogFileNamePattern(loggingOptions.getLogFileNamePattern())
        .setTransitionRecordDirectory(outputOptions.getTransitionRecordDirectory())
        .setMetricsEnabled(metricsOptions.isMetricsEnabled())
        .setMetricsPort(metricsOptions.getMetricsPort())
        .setMetricsInterface(metricsOptions.getMetricsInterface())
        .setMetricsCategories(metricsOptions.getMetricsCategories())
        .setDataPath(dataOptions.getDataPath())
        .setDataStorageMode(dataOptions.getDataStorageMode())
        .setRestApiPort(beaconRestApiOptions.getRestApiPort())
        .setRestApiDocsEnabled(beaconRestApiOptions.isRestApiDocsEnabled())
        .setRestApiEnabled(beaconRestApiOptions.isRestApiEnabled())
        .setRestApiInterface(beaconRestApiOptions.getRestApiInterface())
        .build();
  }
}
