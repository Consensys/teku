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

package tech.pegasys.teku.cli.subcommand.debug;

import com.google.common.primitives.UnsignedLong;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import tech.pegasys.teku.cli.options.DataOptions;
import tech.pegasys.teku.cli.options.NetworkOptions;
import tech.pegasys.teku.core.lookup.BlockProvider;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.teku.storage.server.Database;
import tech.pegasys.teku.storage.server.DepositStorage;
import tech.pegasys.teku.storage.server.VersionedDatabaseFactory;
import tech.pegasys.teku.util.cli.PicoCliVersionProvider;
import tech.pegasys.teku.util.config.Constants;
import tech.pegasys.teku.util.config.NetworkDefinition;

@Command(
    name = "db",
    description = "Debugging tools for inspecting the contents of the database",
    showDefaultValues = true,
    abbreviateSynopsis = true,
    mixinStandardHelpOptions = true,
    versionProvider = PicoCliVersionProvider.class,
    synopsisHeading = "%n",
    descriptionHeading = "%nDescription:%n%n",
    optionListHeading = "%nOptions:%n",
    footerHeading = "%n",
    footer = "Teku is licensed under the Apache License 2.0")
public class DebugDbCommand implements Runnable {
  @Override
  public void run() {
    CommandLine.usage(this, System.out);
  }

  @Command(
      name = "get-deposits",
      description = "List the ETH1 deposit information stored in the database",
      mixinStandardHelpOptions = true,
      showDefaultValues = true,
      abbreviateSynopsis = true,
      versionProvider = PicoCliVersionProvider.class,
      synopsisHeading = "%n",
      descriptionHeading = "%nDescription:%n%n",
      optionListHeading = "%nOptions:%n",
      footerHeading = "%n",
      footer = "Teku is licensed under the Apache License 2.0")
  public int getDeposits(@Mixin final DataOptions dataOptions) throws Exception {
    try (final YamlEth1EventsChannel eth1EventsChannel = new YamlEth1EventsChannel(System.out);
        final Database database = createDatabase(dataOptions)) {
      final DepositStorage depositStorage =
          DepositStorage.create(eth1EventsChannel, database, true);
      depositStorage.replayDepositEvents().join();
    }
    return 0;
  }

  @Command(
      name = "get-finalized-state",
      description = "Get the finalized state, if available, as SSZ",
      mixinStandardHelpOptions = true,
      showDefaultValues = true,
      abbreviateSynopsis = true,
      versionProvider = PicoCliVersionProvider.class,
      synopsisHeading = "%n",
      descriptionHeading = "%nDescription:%n%n",
      optionListHeading = "%nOptions:%n",
      footerHeading = "%n",
      footer = "Teku is licensed under the Apache License 2.0")
  public int getFinalizedState(
      @Mixin final DataOptions dataOptions,
      @Mixin final NetworkOptions networkOptions,
      @Option(
              required = true,
              names = {"--output", "-o"},
              description = "File to write state to")
          final Path outputFile,
      @Option(
              required = true,
              names = {"--slot", "-s"},
              description =
                  "The slot to retrive the state for. If unavailable the closest available state will be returned")
          final long slot)
      throws Exception {
    setConstants(networkOptions);
    try (final Database database = createDatabase(dataOptions)) {
      return writeState(
          outputFile, database.getLatestAvailableFinalizedState(UnsignedLong.valueOf(slot)));
    }
  }

  @Command(
      name = "get-latest-finalized-state",
      description = "Get the latest finalized state, if available, as SSZ",
      mixinStandardHelpOptions = true,
      showDefaultValues = true,
      abbreviateSynopsis = true,
      versionProvider = PicoCliVersionProvider.class,
      synopsisHeading = "%n",
      descriptionHeading = "%nDescription:%n%n",
      optionListHeading = "%nOptions:%n",
      footerHeading = "%n",
      footer = "Teku is licensed under the Apache License 2.0")
  public int getLatestFinalizedState(
      @Mixin final DataOptions dataOptions,
      @Mixin final NetworkOptions networkOptions,
      @Option(
              required = true,
              names = {"--output", "-o"},
              description = "File to write state to")
          final Path outputFile)
      throws Exception {
    setConstants(networkOptions);
    try (final Database database = createDatabase(dataOptions)) {
      final Optional<BeaconState> state =
          database
              .createMemoryStore()
              .map(builder -> builder.blockProvider(BlockProvider.NOOP).build())
              .map(store -> store.getLatestFinalizedBlockAndState().getState());
      return writeState(outputFile, state);
    }
  }

  private void setConstants(@Mixin final NetworkOptions networkOptions) {
    Constants.setConstants(
        NetworkDefinition.fromCliArg(networkOptions.getNetwork()).getConstants());
  }

  private Database createDatabase(final DataOptions dataOptions) {
    final VersionedDatabaseFactory databaseFactory =
        new VersionedDatabaseFactory(
            new NoOpMetricsSystem(), dataOptions.getDataPath(), dataOptions.getDataStorageMode());
    return databaseFactory.createDatabase();
  }

  private int writeState(final Path outputFile, final Optional<BeaconState> state) {
    if (state.isEmpty()) {
      System.err.println("No state available.");
      return 2;
    }
    try {
      Files.write(outputFile, SimpleOffsetSerializer.serialize(state.get()).toArrayUnsafe());
    } catch (IOException e) {
      System.err.println("Unable to write state to " + outputFile + ": " + e.getMessage());
      return 1;
    }
    return 0;
  }
}
