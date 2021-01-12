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

import com.google.common.base.Throwables;
import java.io.File;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionException;
import org.apache.logging.log4j.Level;
import org.hyperledger.besu.metrics.StandardMetricCategory;
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.ScopeType;
import picocli.CommandLine.Unmatched;
import tech.pegasys.teku.cli.converter.LogTypeConverter;
import tech.pegasys.teku.cli.converter.MetricCategoryConverter;
import tech.pegasys.teku.cli.converter.PicoCliVersionProvider;
import tech.pegasys.teku.cli.options.BeaconNodeDataOptions;
import tech.pegasys.teku.cli.options.BeaconRestApiOptions;
import tech.pegasys.teku.cli.options.DataStorageOptions;
import tech.pegasys.teku.cli.options.DepositOptions;
import tech.pegasys.teku.cli.options.Eth2NetworkOptions;
import tech.pegasys.teku.cli.options.InteropOptions;
import tech.pegasys.teku.cli.options.LoggingOptions;
import tech.pegasys.teku.cli.options.MetricsOptions;
import tech.pegasys.teku.cli.options.P2POptions;
import tech.pegasys.teku.cli.options.StoreOptions;
import tech.pegasys.teku.cli.options.ValidatorOptions;
import tech.pegasys.teku.cli.options.WeakSubjectivityOptions;
import tech.pegasys.teku.cli.subcommand.GenesisCommand;
import tech.pegasys.teku.cli.subcommand.PeerCommand;
import tech.pegasys.teku.cli.subcommand.SlashingProtectionCommand;
import tech.pegasys.teku.cli.subcommand.TransitionCommand;
import tech.pegasys.teku.cli.subcommand.UnstableOptionsCommand;
import tech.pegasys.teku.cli.subcommand.ValidatorClientCommand;
import tech.pegasys.teku.cli.subcommand.VoluntaryExitCommand;
import tech.pegasys.teku.cli.subcommand.admin.AdminCommand;
import tech.pegasys.teku.cli.subcommand.admin.InternalToolsCommand;
import tech.pegasys.teku.cli.subcommand.debug.DebugToolsCommand;
import tech.pegasys.teku.cli.util.CascadingDefaultProvider;
import tech.pegasys.teku.cli.util.EnvironmentVariableDefaultProvider;
import tech.pegasys.teku.cli.util.YamlConfigFileDefaultProvider;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.infrastructure.logging.LoggingConfigurator;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.server.DatabaseStorageException;
import tech.pegasys.teku.util.config.InvalidConfigurationException;

@SuppressWarnings("unused")
@Command(
    name = "teku",
    subcommands = {
      CommandLine.HelpCommand.class,
      AdminCommand.class,
      TransitionCommand.class,
      PeerCommand.class,
      GenesisCommand.class,
      SlashingProtectionCommand.class,
      DebugToolsCommand.class,
      UnstableOptionsCommand.class,
      ValidatorClientCommand.class,
      VoluntaryExitCommand.class,
      InternalToolsCommand.class,
    },
    showDefaultValues = true,
    abbreviateSynopsis = true,
    description = "Run the Teku beacon chain client and validator",
    mixinStandardHelpOptions = true,
    versionProvider = PicoCliVersionProvider.class,
    synopsisHeading = "%n",
    descriptionHeading = "%nDescription:%n%n",
    optionListHeading = "%nOptions:%n",
    footerHeading = "%n",
    footer = "Teku is licensed under the Apache License 2.0")
public class BeaconNodeCommand implements Callable<Integer> {

  public static final String LOG_FILE = "teku.log";
  public static final String LOG_PATTERN = "teku_%d{yyyy-MM-dd}.log";

  public static final String CONFIG_FILE_OPTION_NAME = "--config-file";
  static final String TEKU_CONFIG_FILE_ENV = "TEKU_CONFIG_FILE";
  private final PrintWriter outputWriter;
  private final PrintWriter errorWriter;
  private final Map<String, String> environment;
  private final StartAction startAction;
  private final MetricCategoryConverter metricCategoryConverter = new MetricCategoryConverter();

  // allows two pass approach to obtain optional config file
  private static class ConfigFileCommand {

    @Option(
        names = {"-c", CONFIG_FILE_OPTION_NAME},
        arity = "1",
        // Available to all subcommands
        scope = ScopeType.INHERIT)
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
          "Logging verbosity levels: OFF, FATAL, ERROR, WARN, INFO, DEBUG, TRACE, ALL (default: INFO).",
      arity = "1")
  private Level logLevel;

  @Option(
      names = {"-c", CONFIG_FILE_OPTION_NAME},
      paramLabel = "<FILENAME>",
      description = "Path/filename of the yaml config file (default: none)",
      arity = "1",
      // Available to all subcommands
      scope = ScopeType.INHERIT)
  private File configFile;

  @Mixin(name = "Network")
  private Eth2NetworkOptions eth2NetworkOptions;

  @Mixin(name = "P2P")
  private P2POptions p2POptions;

  @Mixin(name = "Interop")
  private InteropOptions interopOptions;

  @Mixin(name = "Validator")
  private ValidatorOptions validatorOptions;

  @Mixin(name = "Deposit")
  private DepositOptions depositOptions;

  @Mixin(name = "Logging")
  private LoggingOptions loggingOptions;

  @Mixin(name = "Metrics")
  private MetricsOptions metricsOptions;

  @Mixin(name = "Data")
  private BeaconNodeDataOptions dataOptions;

  @Mixin(name = "Data Storage")
  private DataStorageOptions dataStorageOptions;

  @Mixin(name = "Store")
  private StoreOptions storeOptions;

  @Mixin(name = "REST API")
  private BeaconRestApiOptions beaconRestApiOptions;

  @Mixin(name = "Weak Subjectivity")
  private WeakSubjectivityOptions weakSubjectivityOptions;

  public BeaconNodeCommand(
      final PrintWriter outputWriter,
      final PrintWriter errorWriter,
      final Map<String, String> environment,
      final StartAction startAction) {
    this.outputWriter = outputWriter;
    this.errorWriter = errorWriter;
    this.environment = environment;
    this.startAction = startAction;

    metricCategoryConverter.addCategories(TekuMetricCategory.class);
    metricCategoryConverter.addCategories(StandardMetricCategory.class);
  }

  private CommandLine registerConverters(final CommandLine commandLine) {
    return commandLine
        .registerConverter(MetricCategory.class, metricCategoryConverter)
        .registerConverter(UInt64.class, UInt64::valueOf);
  }

  private CommandLine getConfigFileCommandLine(final ConfigFileCommand configFileCommand) {
    return registerConverters(new CommandLine(configFileCommand));
  }

  private CommandLine getCommandLine() {
    return registerConverters(new CommandLine(this));
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
    printUsage(outputWriter);

    return ex.getCommandLine().getCommandSpec().exitCodeOnInvalidInput();
  }

  private void printUsage(PrintWriter outputWriter) {
    outputWriter.println();
    outputWriter.println("To display full help:");
    outputWriter.println("teku [COMMAND] --help");
  }

  @Override
  public Integer call() {
    try {
      setLogLevels();
      final TekuConfiguration tekuConfig = tekuConfiguration();
      startAction.start(tekuConfig, false);
      return 0;
    } catch (InvalidConfigurationException | DatabaseStorageException ex) {
      reportUserError(ex);
    } catch (CompletionException e) {
      if (Throwables.getRootCause(e) instanceof InvalidConfigurationException
          || Throwables.getRootCause(e) instanceof DatabaseStorageException) {
        reportUserError(Throwables.getRootCause(e));
      } else {
        reportUnexpectedError(e);
      }
    } catch (Throwable t) {
      reportUnexpectedError(t);
    }
    return 1;
  }

  public void reportUnexpectedError(final Throwable t) {
    System.err.println("Teku failed to start.");
    t.printStackTrace();

    errorWriter.println("Teku failed to start");
    printUsage(errorWriter);
  }

  public void reportUserError(final Throwable ex) {
    errorWriter.println(ex.getMessage());
    printUsage(errorWriter);
  }

  public void setLogLevels() {
    if (logLevel != null) {
      // set log level per CLI flags
      LoggingConfigurator.setAllLevels(logLevel);
    }
  }

  public Level getLogLevel() {
    return this.logLevel;
  }

  public StartAction getStartAction() {
    return startAction;
  }

  protected TekuConfiguration tekuConfiguration() {
    try {
      TekuConfiguration.Builder builder = TekuConfiguration.builder();
      // Eth2NetworkOptions configures network defaults across builders, so configure this first
      eth2NetworkOptions.configure(builder);
      depositOptions.configure(builder);
      weakSubjectivityOptions.configure(builder);
      validatorOptions.configure(builder);
      dataOptions.configure(builder);
      p2POptions.configure(builder);
      beaconRestApiOptions.configure(builder);
      loggingOptions.configure(builder, dataOptions.getDataBasePath(), LOG_FILE, LOG_PATTERN);
      interopOptions.configure(builder);
      dataStorageOptions.configure(builder);
      metricsOptions.configure(builder);
      storeOptions.configure(builder);

      return builder.build();
    } catch (IllegalArgumentException | NullPointerException e) {
      throw new InvalidConfigurationException(e);
    }
  }

  @FunctionalInterface
  public interface StartAction {
    void start(TekuConfiguration config, boolean validatorClient);
  }
}
