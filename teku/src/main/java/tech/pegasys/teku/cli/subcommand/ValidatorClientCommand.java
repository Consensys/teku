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

package tech.pegasys.teku.cli.subcommand;

import com.google.common.base.Throwables;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionException;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.ParentCommand;
import tech.pegasys.teku.cli.BeaconNodeCommand;
import tech.pegasys.teku.cli.converter.PicoCliVersionProvider;
import tech.pegasys.teku.cli.options.InteropOptions;
import tech.pegasys.teku.cli.options.LoggingOptions;
import tech.pegasys.teku.cli.options.MetricsOptions;
import tech.pegasys.teku.cli.options.NetworkOptions;
import tech.pegasys.teku.cli.options.ValidatorClientDataOptions;
import tech.pegasys.teku.cli.options.ValidatorClientOptions;
import tech.pegasys.teku.cli.options.ValidatorOptions;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.storage.server.DatabaseStorageException;
import tech.pegasys.teku.util.config.GlobalConfigurationBuilder;
import tech.pegasys.teku.util.config.InvalidConfigurationException;

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

  @Mixin(name = "Validator")
  private ValidatorOptions validatorOptions;

  @Mixin(name = "Validator Client")
  private ValidatorClientOptions validatorClientOptions;

  @Mixin(name = "Network")
  private NetworkOptions networkOptions;

  @Mixin(name = "Data")
  private ValidatorClientDataOptions dataOptions;

  @Mixin(name = "Interop")
  private InteropOptions interopOptions;

  @Mixin(name = "Logging")
  @SuppressWarnings("FieldMayBeFinal")
  private LoggingOptions loggingOptions = new LoggingOptions();

  @Mixin(name = "Metrics")
  private MetricsOptions metricsOptions;

  @ParentCommand private BeaconNodeCommand parentCommand;

  @Override
  public Integer call() {
    try {
      parentCommand.setLogLevels();
      final TekuConfiguration globalConfiguration = tekuConfiguration();
      parentCommand.getStartAction().start(globalConfiguration, true);
      return 0;
    } catch (InvalidConfigurationException | DatabaseStorageException ex) {
      parentCommand.reportUserError(ex);
    } catch (CompletionException e) {
      if (Throwables.getRootCause(e) instanceof InvalidConfigurationException) {
        parentCommand.reportUserError(Throwables.getRootCause(e));
      } else {
        parentCommand.reportUnexpectedError(e);
      }
    } catch (Throwable t) {
      parentCommand.reportUnexpectedError(t);
    }
    return 1;
  }

  private TekuConfiguration tekuConfiguration() {
    final TekuConfiguration.Builder builder = TekuConfiguration.builder();
    builder.globalConfig(this::buildGlobalConfiguration);
    validatorOptions.configure(builder);
    validatorClientOptions.configure(builder);
    dataOptions.configure(builder);
    return builder.build();
  }

  private void buildGlobalConfiguration(final GlobalConfigurationBuilder builder) {
    builder
        .setNetwork(networkOptions.getNetwork())
        .setInteropGenesisTime(interopOptions.getInteropGenesisTime())
        .setInteropOwnedValidatorStartIndex(interopOptions.getInteropOwnerValidatorStartIndex())
        .setInteropOwnedValidatorCount(interopOptions.getInteropOwnerValidatorCount())
        .setInteropNumberOfValidators(interopOptions.getInteropNumberOfValidators())
        .setInteropEnabled(interopOptions.isInteropEnabled())
        .setLogColorEnabled(loggingOptions.isLogColorEnabled())
        .setLogIncludeEventsEnabled(loggingOptions.isLogIncludeEventsEnabled())
        .setLogIncludeValidatorDutiesEnabled(loggingOptions.isLogIncludeValidatorDutiesEnabled())
        .setLogDestination(loggingOptions.getLogDestination())
        .setLogWireCipher(loggingOptions.isLogWireCipherEnabled())
        .setLogWirePlain(loggingOptions.isLogWirePlainEnabled())
        .setLogWireMuxFrames(loggingOptions.isLogWireMuxEnabled())
        .setLogWireGossip(loggingOptions.isLogWireGossipEnabled())
        .setMetricsEnabled(metricsOptions.isMetricsEnabled())
        .setMetricsPort(metricsOptions.getMetricsPort())
        .setMetricsInterface(metricsOptions.getMetricsInterface())
        .setMetricsCategories(metricsOptions.getMetricsCategories())
        .setMetricsHostAllowlist(metricsOptions.getMetricsHostAllowlist());

    String logFile =
        loggingOptions
            .getMaybeLogFile()
            .orElse(
                LoggingOptions.getDefaultLogFileGivenDataDir(
                    dataOptions.getDataBasePath().toString(), true));
    builder.setLogFile(logFile);

    final String logPattern =
        LoggingOptions.getLogPatternGivenDataDir(
            dataOptions.getDataBasePath().toString(), loggingOptions.getLogFileNamePattern(),true);

    builder.setLogFileNamePattern(logPattern);
  }
}
