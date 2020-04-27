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

package tech.pegasys.teku.cli;

import java.io.File;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import org.apache.logging.log4j.Level;
import org.hyperledger.besu.metrics.StandardMetricCategory;
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Unmatched;
import tech.pegasys.teku.cli.converter.MetricCategoryConverter;
import tech.pegasys.teku.cli.options.BeaconRestApiOptions;
import tech.pegasys.teku.cli.options.DataOptions;
import tech.pegasys.teku.cli.options.DepositOptions;
import tech.pegasys.teku.cli.options.InteropOptions;
import tech.pegasys.teku.cli.options.LoggingOptions;
import tech.pegasys.teku.cli.options.MetricsOptions;
import tech.pegasys.teku.cli.options.NetworkOptions;
import tech.pegasys.teku.cli.options.OutputOptions;
import tech.pegasys.teku.cli.options.P2POptions;
import tech.pegasys.teku.cli.options.ValidatorOptions;
import tech.pegasys.teku.cli.subcommand.DepositCommand;
import tech.pegasys.teku.cli.subcommand.GenesisCommand;
import tech.pegasys.teku.cli.subcommand.PeerCommand;
import tech.pegasys.teku.cli.subcommand.TransitionCommand;
import tech.pegasys.teku.cli.util.CascadingDefaultProvider;
import tech.pegasys.teku.cli.util.EnvironmentVariableDefaultProvider;
import tech.pegasys.teku.cli.util.YamlConfigFileDefaultProvider;
import tech.pegasys.teku.logging.LoggingConfigurator;
import tech.pegasys.teku.metrics.TekuMetricCategory;
import tech.pegasys.teku.storage.server.DatabaseStorageException;
import tech.pegasys.teku.util.cli.LogTypeConverter;
import tech.pegasys.teku.util.cli.VersionProvider;
import tech.pegasys.teku.util.config.NetworkDefinition;
import tech.pegasys.teku.util.config.TekuConfiguration;

@SuppressWarnings("unused")
@Command(
    name = "teku",
    subcommands = {
      CommandLine.HelpCommand.class,
      TransitionCommand.class,
      PeerCommand.class,
      DepositCommand.class,
      GenesisCommand.class
    },
    showDefaultValues = true,
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

  public static final String CONFIG_FILE_OPTION_NAME = "--config-file";
  static final String TEKU_CONFIG_FILE_ENV = "TEKU_CONFIG_FILE";
  private final PrintWriter outputWriter;
  private final PrintWriter errorWriter;
  private final Map<String, String> environment;
  private final Consumer<TekuConfiguration> startAction;
  private final MetricCategoryConverter metricCategoryConverter = new MetricCategoryConverter();

  // allows two pass approach to obtain optional config file
  private static class ConfigFileCommand {
    @Option(
        names = {"-c", CONFIG_FILE_OPTION_NAME},
        arity = "1")
    File configFile;

    @SuppressWarnings("UnunsedVariable")
    @Unmatched
    List<String> otherOptions;
  }

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
      description = "Path/filename of the yaml config file (default: none)",
      arity = "1")
  private File configFile;

  @Option(
      names = {"--Xstartup-target-peer-count"},
      paramLabel = "<NUMBER>",
      description = "Number of peers to wait for before considering the node in sync.",
      hidden = true)
  private Integer startupTargetPeerCount;

  @Option(
      names = {"--Xstartup-timeout-seconds"},
      paramLabel = "<NUMBER>",
      description =
          "Timeout in seconds to allow the node to be in sync even if startup target peer count has not yet been reached.",
      hidden = true)
  private Integer startupTimeoutSeconds;

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

  public BeaconNodeCommand(
      final PrintWriter outputWriter,
      final PrintWriter errorWriter,
      final Map<String, String> environment,
      final Consumer<TekuConfiguration> startAction) {
    this.outputWriter = outputWriter;
    this.errorWriter = errorWriter;
    this.environment = environment;
    this.startAction = startAction;

    metricCategoryConverter.addCategories(TekuMetricCategory.class);
    metricCategoryConverter.addCategories(StandardMetricCategory.class);
  }

  private CommandLine getConfigFileCommandLine(final ConfigFileCommand configFileCommand) {
    return new CommandLine(configFileCommand)
        .registerConverter(MetricCategory.class, metricCategoryConverter);
  }

  private CommandLine getCommandLine() {
    return new CommandLine(this).registerConverter(MetricCategory.class, metricCategoryConverter);
  }

  public int parse(final String[] args) {
    // first pass to obtain config file if specified and print usage/version help
    final ConfigFileCommand configFileCommand = new ConfigFileCommand();
    final CommandLine configFileCommandLine = getConfigFileCommandLine(configFileCommand);
    configFileCommandLine.parseArgs(args);
    if (configFileCommandLine.isUsageHelpRequested()) {
      return executeCommandUsageHelp();
    } else if (configFileCommandLine.isVersionHelpRequested()) {
      return executeCommandVersion();
    }

    final Optional<File> configFile = getConfigFileFromCliOrEnv(configFileCommand);

    // final pass
    final CommandLine commandLine = getCommandLine();
    commandLine.setCaseInsensitiveEnumValuesAllowed(true);
    commandLine.setOut(outputWriter);
    commandLine.setErr(errorWriter);
    commandLine.setParameterExceptionHandler(this::handleParseException);
    commandLine.setDefaultValueProvider(defaultValueProvider(commandLine, configFile));

    return commandLine.execute(args);
  }

  private Optional<File> getConfigFileFromCliOrEnv(final ConfigFileCommand configFileCommand) {
    return Optional.ofNullable(configFileCommand.configFile)
        .or(() -> Optional.ofNullable(environment.get(TEKU_CONFIG_FILE_ENV)).map(File::new));
  }

  private int executeCommandVersion() {
    final CommandLine baseCommandLine = getCommandLine();
    baseCommandLine.printVersionHelp(outputWriter);
    return baseCommandLine.getCommandSpec().exitCodeOnVersionHelp();
  }

  private int executeCommandUsageHelp() {
    final CommandLine baseCommandLine = getCommandLine();
    baseCommandLine.usage(outputWriter);
    return baseCommandLine.getCommandSpec().exitCodeOnUsageHelp();
  }

  private CommandLine.IDefaultValueProvider defaultValueProvider(
      final CommandLine commandLine, final Optional<File> configFile) {
    if (configFile.isEmpty()) {
      return new EnvironmentVariableDefaultProvider(environment);
    }

    return new CascadingDefaultProvider(
        new EnvironmentVariableDefaultProvider(environment),
        new YamlConfigFileDefaultProvider(commandLine, configFile.get()));
  }

  private int handleParseException(final CommandLine.ParameterException ex, final String[] args) {
    errorWriter.println(ex.getMessage());

    CommandLine.UnmatchedArgumentException.printSuggestions(ex, outputWriter);
    outputWriter.println();
    outputWriter.println("To display full help:");
    outputWriter.println("teku [COMMAND] --help");

    return ex.getCommandLine().getCommandSpec().exitCodeOnInvalidInput();
  }

  @Override
  public Integer call() {
    try {
      setLogLevels();
      final TekuConfiguration tekuConfiguration = tekuConfiguration();
      startAction.accept(tekuConfiguration);
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

  private void setLogLevels() {
    if (logLevel != null) {
      // set log level per CLI flags
      LoggingConfigurator.setAllLevels(logLevel);
    }
  }

  private TekuConfiguration tekuConfiguration() {
    // TODO: validate option dependencies
    return TekuConfiguration.builder()
        .setNetwork(NetworkDefinition.fromCliArg(networkOptions.getNetwork()))
        .setStartupTargetPeerCount(startupTargetPeerCount)
        .setStartupTimeoutSeconds(startupTimeoutSeconds)
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
        .setLogColorEnabled(loggingOptions.isLogColorEnabled())
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
