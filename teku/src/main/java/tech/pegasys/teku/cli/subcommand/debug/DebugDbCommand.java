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
import tech.pegasys.teku.cli.converter.PicoCliVersionProvider;
import tech.pegasys.teku.cli.options.BeaconNodeDataOptions;
import tech.pegasys.teku.cli.options.DataStorageOptions;
import tech.pegasys.teku.cli.options.Eth2NetworkOptions;
import tech.pegasys.teku.core.lookup.BlockProvider;
import tech.pegasys.teku.core.lookup.StateAndBlockSummaryProvider;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.datastructures.state.AnchorPoint;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.MetricTrackingExecutorFactory;
import tech.pegasys.teku.infrastructure.async.ScheduledExecutorAsyncRunner;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.protoarray.ProtoArraySnapshot;
import tech.pegasys.teku.service.serviceutils.layout.DataDirLayout;
import tech.pegasys.teku.storage.server.Database;
import tech.pegasys.teku.storage.server.DepositStorage;
import tech.pegasys.teku.storage.server.VersionedDatabaseFactory;
import tech.pegasys.teku.storage.store.UpdatableStore;
import tech.pegasys.teku.util.config.Constants;

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
      @Mixin final BeaconNodeDataOptions dataOptions,
      @Mixin final DataStorageOptions dataStorageOptions,
      @Mixin final Eth2NetworkOptions eth2NetworkOptions)
      throws Exception {
    try (final YamlEth1EventsChannel eth1EventsChannel = new YamlEth1EventsChannel(System.out);
        final Database database =
            createDatabase(dataOptions, dataStorageOptions, eth2NetworkOptions)) {
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
      @Mixin final BeaconNodeDataOptions dataOptions,
      @Mixin final DataStorageOptions dataStorageOptions,
      @Mixin final Eth2NetworkOptions eth2NetworkOptions,
      @Option(
              required = true,
              names = {"--output", "-o"},
              description = "File to write state to")
          final Path outputFile,
      @Option(
              required = true,
              names = {"--slot", "-s"},
              description =
                  "The slot to retrieve the state for. If unavailable the closest available state will be returned")
          final long slot)
      throws Exception {
    setConstants(eth2NetworkOptions);
    try (final Database database =
        createDatabase(dataOptions, dataStorageOptions, eth2NetworkOptions)) {
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
      @Mixin final BeaconNodeDataOptions dataOptions,
      @Mixin final DataStorageOptions dataStorageOptions,
      @Mixin final Eth2NetworkOptions eth2NetworkOptions,
      @Option(
              required = true,
              names = {"--output", "-o"},
              description = "File to write state to")
          final Path outputFile,
      @Option(
              names = {"--block-output"},
              description = "File to write the block matching the latest finalized state to")
          final Path blockOutputFile)
      throws Exception {
    setConstants(eth2NetworkOptions);
    final AsyncRunner asyncRunner =
        ScheduledExecutorAsyncRunner.create(
            "async", 1, new MetricTrackingExecutorFactory(new NoOpMetricsSystem()));
    try (final Database database =
        createDatabase(dataOptions, dataStorageOptions, eth2NetworkOptions)) {
      final Optional<AnchorPoint> finalizedAnchor =
          database
              .createMemoryStore()
              .map(
                  builder ->
                      builder
                          .blockProvider(BlockProvider.NOOP)
                          .asyncRunner(asyncRunner)
                          .stateProvider(StateAndBlockSummaryProvider.NOOP)
                          .build())
              .map(UpdatableStore::getLatestFinalized);
      int result = writeState(outputFile, finalizedAnchor.map(AnchorPoint::getState));
      if (result == 0 && blockOutputFile != null) {
        final Optional<SignedBeaconBlock> finalizedBlock =
            finalizedAnchor.flatMap(AnchorPoint::getSignedBeaconBlock);
        result = writeBlock(blockOutputFile, finalizedBlock);
      }
      return result;
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
      @Mixin final BeaconNodeDataOptions dataOptions,
      @Mixin final DataStorageOptions dataStorageOptions,
      @Mixin final Eth2NetworkOptions eth2NetworkOptions,
      @Option(
              names = {"--output", "-o"},
              description = "File to write output to")
          final Path outputFile)
      throws Exception {
    setConstants(eth2NetworkOptions);
    try (final Database database =
        createDatabase(dataOptions, dataStorageOptions, eth2NetworkOptions)) {
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

  private void setConstants(@Mixin final Eth2NetworkOptions eth2NetworkOptions) {
    Constants.setConstants(eth2NetworkOptions.getNetworkConfiguration().getConstants());
  }

  private Database createDatabase(
      final BeaconNodeDataOptions dataOptions,
      final DataStorageOptions dataStorageOptions,
      final Eth2NetworkOptions eth2NetworkOptions) {
    final VersionedDatabaseFactory databaseFactory =
        new VersionedDatabaseFactory(
            new NoOpMetricsSystem(),
            DataDirLayout.createFrom(dataOptions.getDataConfig()).getBeaconDataDirectory(),
            dataStorageOptions.getDataStorageMode(),
            eth2NetworkOptions.getNetworkConfiguration().getEth1DepositContractAddress());
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

  private int writeBlock(final Path outputFile, final Optional<SignedBeaconBlock> block) {
    if (block.isEmpty()) {
      System.err.println("No block available.");
      return 2;
    }
    try {
      Files.write(outputFile, SimpleOffsetSerializer.serialize(block.get()).toArrayUnsafe());
    } catch (IOException e) {
      System.err.println("Unable to write block to " + outputFile + ": " + e.getMessage());
      return 1;
    }
    return 0;
  }
}
