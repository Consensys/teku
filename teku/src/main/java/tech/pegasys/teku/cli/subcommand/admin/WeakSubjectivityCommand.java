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

package tech.pegasys.teku.cli.subcommand.admin;

import static tech.pegasys.teku.infrastructure.logging.SubCommandLogger.SUB_COMMAND_LOG;

import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import picocli.CommandLine;
import tech.pegasys.teku.cli.converter.PicoCliVersionProvider;
import tech.pegasys.teku.cli.options.BeaconNodeDataOptions;
import tech.pegasys.teku.cli.options.DataStorageOptions;
import tech.pegasys.teku.cli.options.Eth2NetworkOptions;
import tech.pegasys.teku.service.serviceutils.layout.DataDirLayout;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.storage.events.WeakSubjectivityState;
import tech.pegasys.teku.storage.events.WeakSubjectivityUpdate;
import tech.pegasys.teku.storage.server.Database;
import tech.pegasys.teku.storage.server.VersionedDatabaseFactory;

@CommandLine.Command(
    name = "weak-subjectivity",
    description = "Commands related to weak subjectivity configuration",
    showDefaultValues = true,
    abbreviateSynopsis = true,
    mixinStandardHelpOptions = true,
    versionProvider = PicoCliVersionProvider.class,
    synopsisHeading = "%n",
    descriptionHeading = "%nDescription:%n%n",
    optionListHeading = "%nOptions:%n",
    footerHeading = "%n",
    footer = "Teku is licensed under the Apache License 2.0")
public class WeakSubjectivityCommand implements Runnable {

  @Override
  public void run() {
    CommandLine.usage(this, System.out);
  }

  @CommandLine.Command(
      name = "clear-state",
      description = "Clears stored weak subjectivity configuration",
      mixinStandardHelpOptions = true,
      showDefaultValues = true,
      abbreviateSynopsis = true,
      versionProvider = PicoCliVersionProvider.class,
      synopsisHeading = "%n",
      descriptionHeading = "%nDescription:%n%n",
      optionListHeading = "%nOptions:%n",
      footerHeading = "%n",
      footer = "Teku is licensed under the Apache License 2.0")
  public int clearWeakSubjectivityState(
      @CommandLine.Mixin final BeaconNodeDataOptions dataOptions,
      @CommandLine.Mixin final DataStorageOptions dataStorageOptions,
      @CommandLine.Mixin final Eth2NetworkOptions eth2NetworkOptions)
      throws Exception {
    try (final Database db = createDatabase(dataOptions, dataStorageOptions, eth2NetworkOptions)) {
      // Pull value before updating
      final WeakSubjectivityState original = db.getWeakSubjectivityState();
      if (original.isEmpty()) {
        SUB_COMMAND_LOG.display("Weak subjectivity state is already empty - nothing to clear.");
        return 0;
      }
      SUB_COMMAND_LOG.display("Clearing weak subjectivity state: " + stateToString(original));
      db.updateWeakSubjectivityState(WeakSubjectivityUpdate.clearWeakSubjectivityCheckpoint());
      SUB_COMMAND_LOG.display("Successfully cleared weak subjectivity state.");
      return 0;
    }
  }

  @CommandLine.Command(
      name = "display-state",
      description = "Display the stored weak subjectivity configuration",
      mixinStandardHelpOptions = true,
      showDefaultValues = true,
      abbreviateSynopsis = true,
      versionProvider = PicoCliVersionProvider.class,
      synopsisHeading = "%n",
      descriptionHeading = "%nDescription:%n%n",
      optionListHeading = "%nOptions:%n",
      footerHeading = "%n",
      footer = "Teku is licensed under the Apache License 2.0")
  public int displayWeakSubjectivityState(
      @CommandLine.Mixin final BeaconNodeDataOptions dataOptions,
      @CommandLine.Mixin final DataStorageOptions dataStorageOptions,
      @CommandLine.Mixin final Eth2NetworkOptions eth2NetworkOptions)
      throws Exception {
    try (final Database db = createDatabase(dataOptions, dataStorageOptions, eth2NetworkOptions)) {
      final WeakSubjectivityState wsState = db.getWeakSubjectivityState();
      SUB_COMMAND_LOG.display("Stored weak subjectivity state: " + stateToString(wsState));
      return 0;
    }
  }

  private Database createDatabase(
      final BeaconNodeDataOptions dataOptions,
      final DataStorageOptions dataStorageOptions,
      final Eth2NetworkOptions eth2NetworkOptions) {
    final Spec spec = eth2NetworkOptions.getNetworkConfiguration().getSpecProvider();
    final VersionedDatabaseFactory databaseFactory =
        new VersionedDatabaseFactory(
            new NoOpMetricsSystem(),
            DataDirLayout.createFrom(dataOptions.getDataConfig()).getBeaconDataDirectory(),
            dataStorageOptions.getDataStorageMode(),
            eth2NetworkOptions.getNetworkConfiguration().getEth1DepositContractAddress(),
            spec);
    return databaseFactory.createDatabase();
  }

  private String stateToString(final WeakSubjectivityState wsState) {
    return wsState.toString();
  }
}
