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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import tech.pegasys.teku.cli.options.DataOptions;
import tech.pegasys.teku.cli.options.NetworkOptions;
import tech.pegasys.teku.core.lookup.BlockProvider;
import tech.pegasys.teku.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.MetricTrackingExecutorFactory;
import tech.pegasys.teku.infrastructure.async.ScheduledExecutorAsyncRunner;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.protoarray.ProtoArraySnapshot;
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
  public int getDeposits(
      @Mixin final DataOptions dataOptions, @Mixin final NetworkOptions networkOptions)
      throws Exception {
    try (final YamlEth1EventsChannel eth1EventsChannel = new YamlEth1EventsChannel(System.out);
        final Database database = createDatabase(dataOptions, networkOptions)) {
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
    try (final Database database = createDatabase(dataOptions, networkOptions)) {
      return writeState(
          outputFile, database.getLatestAvailableFinalizedState(UInt64.valueOf(slot)));
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
    final AsyncRunner asyncRunner =
        ScheduledExecutorAsyncRunner.create(
            "async", 1, new MetricTrackingExecutorFactory(new NoOpMetricsSystem()));
    try (final Database database = createDatabase(dataOptions, networkOptions)) {
      final Optional<BeaconState> state =
          database
              .createMemoryStore()
              .map(
                  builder ->
                      builder
                          .blockProvider(BlockProvider.NOOP)
                          .asyncRunner(asyncRunner)
                          .build()
                          .join())
              .map(store -> store.getLatestFinalizedBlockAndState().getState());
      return writeState(outputFile, state);
    } finally {
      asyncRunner.shutdown();
    }
  }

  @Command(
      name = "get-forkchoice-snapshot",
      description = "Get the stored fork choice data",
      mixinStandardHelpOptions = true,
      showDefaultValues = true,
      abbreviateSynopsis = true,
      versionProvider = PicoCliVersionProvider.class,
      synopsisHeading = "%n",
      descriptionHeading = "%nDescription:%n%n",
      optionListHeading = "%nOptions:%n",
      footerHeading = "%n",
      footer = "Teku is licensed under the Apache License 2.0")
  public int getForkChoiceSnapshot(
      @Mixin final DataOptions dataOptions,
      @Mixin final NetworkOptions networkOptions,
      @Option(
              names = {"--output", "-o"},
              description = "File to write output to")
          final Path outputFile)
      throws Exception {
    setConstants(networkOptions);
    try (final Database database = createDatabase(dataOptions, networkOptions)) {
      final Optional<ProtoArraySnapshot> snapshot = database.getProtoArraySnapshot();
      if (snapshot.isEmpty()) {
        System.err.println("No fork choice snapshot available.");
        return 2;
      }
      final Map<UInt64, VoteTracker> votes = database.getVotes();
      final String report = ForkChoiceDataWriter.writeForkChoiceData(snapshot.get(), votes);
      if (outputFile != null) {
        Files.writeString(outputFile, report, StandardCharsets.UTF_8);
      } else {
        System.out.println(report);
      }
      return 0;
    }
  }

  private void setConstants(@Mixin final NetworkOptions networkOptions) {
    Constants.setConstants(
        NetworkDefinition.fromCliArg(networkOptions.getNetwork()).getConstants());
  }

  private Database createDatabase(
      final DataOptions dataOptions, final NetworkOptions networkOptions) {
    final VersionedDatabaseFactory databaseFactory =
        new VersionedDatabaseFactory(
            new NoOpMetricsSystem(),
            dataOptions.getDataPath(),
            dataOptions.getDataStorageMode(),
            NetworkDefinition.fromCliArg(networkOptions.getNetwork())
                .getEth1DepositContractAddress()
                .orElse(null));
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
