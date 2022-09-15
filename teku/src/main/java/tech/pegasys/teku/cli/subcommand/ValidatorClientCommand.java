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

package tech.pegasys.teku.cli.subcommand;

import static tech.pegasys.teku.cli.subcommand.RemoteSpecLoader.getSpecWithRetry;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletionException;
import org.apache.logging.log4j.Level;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.ParentCommand;
import tech.pegasys.teku.cli.BeaconNodeCommand;
import tech.pegasys.teku.cli.converter.PicoCliVersionProvider;
import tech.pegasys.teku.cli.options.InteropOptions;
import tech.pegasys.teku.cli.options.LoggingOptions;
import tech.pegasys.teku.cli.options.MetricsOptions;
import tech.pegasys.teku.cli.options.ValidatorClientDataOptions;
import tech.pegasys.teku.cli.options.ValidatorClientOptions;
import tech.pegasys.teku.cli.options.ValidatorOptions;
import tech.pegasys.teku.cli.options.ValidatorRestApiOptions;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.infrastructure.exceptions.ExceptionUtil;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.logging.LoggingConfig;
import tech.pegasys.teku.infrastructure.logging.LoggingConfigurator;
import tech.pegasys.teku.networks.Eth2NetworkConfiguration;
import tech.pegasys.teku.storage.server.DatabaseStorageException;

@Command(
    name = "validator-client",
    aliases = "vc",
    description = "Run a Validator Client that connects to a remote Beacon Node",
    showDefaultValues = true,
    abbreviateSynopsis = true,
    mixinStandardHelpOptions = true,
    versionProvider = PicoCliVersionProvider.class,
    synopsisHeading = "%n",
    descriptionHeading = "%nDescription:%n%n",
    optionListHeading = "%nOptions:%n",
    footerHeading = "%n",
    footer = "Teku is licensed under the Apache License 2.0")
public class ValidatorClientCommand implements Callable<Integer> {
  public static final String LOG_FILE_PREFIX = "teku-validator";

  @Mixin(name = "Validator")
  private ValidatorOptions validatorOptions;

  @Mixin(name = "Validator Client")
  private ValidatorClientOptions validatorClientOptions;

  @Mixin(name = "Validator REST API")
  private ValidatorRestApiOptions validatorRestApiOptions;

  @Mixin(name = "Data")
  private ValidatorClientDataOptions dataOptions;

  @Mixin(name = "Interop")
  private InteropOptions interopOptions;

  @Mixin(name = "Logging")
  private final LoggingOptions loggingOptions;

  @Mixin(name = "Metrics")
  private MetricsOptions metricsOptions;

  @CommandLine.Option(
      names = {"-n", "--network"},
      paramLabel = "<NETWORK>",
      description =
          "Represents which network to use. "
              + "Use `auto` to fetch network configuration from the beacon node endpoint directly.",
      arity = "1")
  private String networkOption = AUTO_NETWORK_OPTION;

  @ParentCommand private BeaconNodeCommand parentCommand;

  private static final String AUTO_NETWORK_OPTION = "auto";

  public ValidatorClientCommand(final LoggingOptions sharedLoggingOptions) {
    this.loggingOptions = sharedLoggingOptions;
  }

  @Override
  public Integer call() {
    try {
      startLogging();
      final TekuConfiguration globalConfiguration = tekuConfiguration();
      parentCommand.getStartAction().start(globalConfiguration, true);
      return 0;
    } catch (InvalidConfigurationException | DatabaseStorageException ex) {
      parentCommand.reportUserError(ex);
    } catch (CompletionException e) {

      ExceptionUtil.<Throwable>getCause(e, InvalidConfigurationException.class)
          .or(() -> ExceptionUtil.getCause(e, DatabaseStorageException.class))
          .ifPresentOrElse(
              parentCommand::reportUserError, () -> parentCommand.reportUnexpectedError(e));
    } catch (Throwable t) {
      parentCommand.reportUnexpectedError(t);
    }
    return 1;
  }

  private void startLogging() {
    LoggingConfig loggingConfig =
        parentCommand.buildLoggingConfig(dataOptions.getDataPath(), LOG_FILE_PREFIX);
    parentCommand.getLoggingConfigurator().startLogging(loggingConfig);
    // jupnp logs a lot of context to level WARN, and it is quite verbose.
    LoggingConfigurator.setAllLevelsSilently("org.jupnp", Level.ERROR);
  }

  private void configureWithSpecFromBeaconNode(Eth2NetworkConfiguration.Builder builder) {
    try {
      var spec = getSpecWithRetry(validatorClientOptions.getBeaconNodeApiEndpoints());
      builder.spec(spec);
    } catch (Throwable e) {
      throw new InvalidConfigurationException(e);
    }
  }

  private boolean isAutoDetectNetworkOption(String option) {
    return AUTO_NETWORK_OPTION.equalsIgnoreCase(option);
  }

  private void configureEth2Network(TekuConfiguration.Builder builder) {
    if (parentCommand.isOptionSpecified("--network")) {
      throw new InvalidConfigurationException(
          "--network option should not be specified before the validator-client command");
    }

    if (isAutoDetectNetworkOption(networkOption)) {
      builder.eth2NetworkConfig(this::configureWithSpecFromBeaconNode);
    } else {
      builder.eth2NetworkConfig(b -> b.applyNetworkDefaults(networkOption));
    }
  }

  public TekuConfiguration tekuConfiguration() {
    final TekuConfiguration.Builder builder = TekuConfiguration.builder();
    configureEth2Network(builder);
    validatorOptions.configure(builder);
    validatorClientOptions.configure(builder);
    dataOptions.configure(builder);
    validatorRestApiOptions.configure(builder);
    loggingOptions.configureWireLogs(builder);
    interopOptions.configure(builder);
    metricsOptions.configure(builder);
    return builder.build();
  }
}
