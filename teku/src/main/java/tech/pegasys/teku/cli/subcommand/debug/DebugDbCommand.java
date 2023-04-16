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

package tech.pegasys.teku.cli.subcommand.debug;

import static tech.pegasys.teku.spec.config.Constants.MIN_EPOCHS_FOR_BLOBS_SIDECARS_REQUESTS;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Visibility;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import tech.pegasys.teku.cli.converter.PicoCliVersionProvider;
import tech.pegasys.teku.cli.options.BeaconNodeDataOptions;
import tech.pegasys.teku.cli.options.Eth2NetworkOptions;
import tech.pegasys.teku.dataproviders.lookup.BlockProvider;
import tech.pegasys.teku.dataproviders.lookup.StateAndBlockSummaryProvider;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.AsyncRunnerFactory;
import tech.pegasys.teku.infrastructure.async.MetricTrackingExecutorFactory;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.service.serviceutils.layout.DataDirLayout;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobsSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockInvariants;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.storage.server.Database;
import tech.pegasys.teku.storage.server.DatabaseStorageException;
import tech.pegasys.teku.storage.server.DepositStorage;
import tech.pegasys.teku.storage.server.StorageConfiguration;
import tech.pegasys.teku.storage.server.VersionedDatabaseFactory;
import tech.pegasys.teku.storage.store.StoreBuilder;
import tech.pegasys.teku.storage.store.UpdatableStore;

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
      @Mixin final BeaconNodeDataOptions beaconNodeDataOptions,
      @Mixin final Eth2NetworkOptions eth2NetworkOptions)
      throws Exception {
    try (final YamlEth1EventsChannel eth1EventsChannel = new YamlEth1EventsChannel(System.out);
        final Database database = createDatabase(beaconNodeDataOptions, eth2NetworkOptions)) {
      final DepositStorage depositStorage =
          DepositStorage.create(eth1EventsChannel, database, false);
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
      @Mixin final BeaconNodeDataOptions beaconNodeDataOptions,
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
    try (final Database database = createDatabase(beaconNodeDataOptions, eth2NetworkOptions)) {
      return writeState(
          outputFile, database.getLatestAvailableFinalizedState(UInt64.valueOf(slot)));
    }
  }

  @Command(
      name = "get-finalized-state-indices",
      description = "Display the slots of finalized states that are stored.",
      mixinStandardHelpOptions = true,
      showDefaultValues = true,
      abbreviateSynopsis = true,
      versionProvider = PicoCliVersionProvider.class,
      synopsisHeading = "%n",
      descriptionHeading = "%nDescription:%n%n",
      optionListHeading = "%nOptions:%n",
      footerHeading = "%n",
      footer = "Teku is licensed under the Apache License 2.0")
  public int getFinalizedStateIndices(
      @Mixin final BeaconNodeDataOptions beaconNodeDataOptions,
      @Mixin final Eth2NetworkOptions eth2NetworkOptions,
      @Option(
              required = true,
              names = {"--start-slot"},
              defaultValue = "0",
              description = "The start index of the range to display")
          final long startSlot,
      @Option(
              required = true,
              names = {"--end-slot"},
              defaultValue = "9223372036854775807",
              description = "The end index of the range to display")
          final long endSlot)
      throws Exception {
    try (final Database database = createDatabase(beaconNodeDataOptions, eth2NetworkOptions)) {
      System.out.println("Searching for finalized states in the finalized store");
      try (Stream<UInt64> stream =
          database.streamFinalizedStateSlots(UInt64.valueOf(startSlot), UInt64.valueOf(endSlot))) {
        stream.forEach(System.out::println);
      }
    }
    return 0;
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
      @Mixin final BeaconNodeDataOptions beaconNodeDataOptions,
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
    final AsyncRunnerFactory asyncRunnerFactory =
        AsyncRunnerFactory.createDefault(
            new MetricTrackingExecutorFactory(new NoOpMetricsSystem()));
    final AsyncRunner asyncRunner = asyncRunnerFactory.create("async", 1);
    try (final Database database = createDatabase(beaconNodeDataOptions, eth2NetworkOptions)) {
      final Optional<AnchorPoint> finalizedAnchor =
          database
              .createMemoryStore()
              .map(
                  storeData ->
                      StoreBuilder.create()
                          .onDiskStoreData(storeData)
                          .metricsSystem(new NoOpMetricsSystem())
                          .specProvider(eth2NetworkOptions.getNetworkConfiguration().getSpec())
                          .blockProvider(BlockProvider.NOOP)
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
      name = "get-column-counts",
      description = "Count entries in columns in storage tables",
      mixinStandardHelpOptions = true,
      showDefaultValues = true,
      abbreviateSynopsis = true,
      versionProvider = PicoCliVersionProvider.class,
      synopsisHeading = "%n",
      descriptionHeading = "%nDescription:%n%n",
      optionListHeading = "%nOptions:%n",
      footerHeading = "%n",
      footer = "Teku is licensed under the Apache License 2.0")
  public int getColumnCounts(
      @Mixin final BeaconNodeDataOptions beaconNodeDataOptions,
      @Mixin final Eth2NetworkOptions eth2NetworkOptions)
      throws Exception {
    try (final Database database = createDatabase(beaconNodeDataOptions, eth2NetworkOptions)) {
      database.getColumnCounts().forEach(this::printColumn);
    }
    return 0;
  }

  @Command(
      name = "validate-block-history",
      description = "Validate the chain of finalized blocks via parent references",
      mixinStandardHelpOptions = true,
      showDefaultValues = true,
      abbreviateSynopsis = true,
      versionProvider = PicoCliVersionProvider.class,
      synopsisHeading = "%n",
      descriptionHeading = "%nDescription:%n%n",
      optionListHeading = "%nOptions:%n",
      footerHeading = "%n",
      footer = "Teku is licensed under the Apache License 2.0")
  public int validateBlockHistory(
      @Mixin final BeaconNodeDataOptions beaconNodeDataOptions,
      @Mixin final Eth2NetworkOptions eth2NetworkOptions)
      throws Exception {
    final long startTimeMillis = System.currentTimeMillis();
    long counter = 0;
    try (final Database database = createDatabase(beaconNodeDataOptions, eth2NetworkOptions)) {

      Optional<SignedBeaconBlock> maybeFinalizedBlock = database.getLastAvailableFinalizedBlock();
      Optional<SignedBeaconBlock> maybeEarliestBlock = database.getEarliestAvailableBlock();
      while (maybeFinalizedBlock.isPresent()) {
        final SignedBeaconBlock currentBlock = maybeFinalizedBlock.get();
        if (counter == 0) {
          System.out.printf(
              "Tracing chain from best finalized block %s (%s)%n",
              currentBlock.getRoot(), currentBlock.getSlot());
        }
        final Optional<SignedBeaconBlock> maybeParent =
            database.getSignedBlock(currentBlock.getParentRoot());
        if (maybeParent.isEmpty()) {
          if (maybeEarliestBlock.isPresent()
              && maybeEarliestBlock.get().getRoot().equals(currentBlock.getRoot())) {
            System.out.printf(
                "Found back to earliest block %s (%s)%n",
                currentBlock.getRoot(), currentBlock.getSlot());
            break;
          }
          System.err.printf(
              "ERROR: Unable to locate parent %s of block %s (%s)%n",
              currentBlock.getParentRoot(), currentBlock.getRoot(), currentBlock.getSlot());
          maybeFinalizedBlock = findFinalizedBlockBeforeSlot(database, currentBlock.getSlot());
          if (maybeFinalizedBlock.isEmpty()) {
            break;
          } else {
            System.out.printf(
                "Starting to dig back from new parent: %s (%s)%n",
                maybeFinalizedBlock.get().getRoot(), maybeFinalizedBlock.get().getSlot());
          }
        } else {
          checkFinalizedIndices(database, maybeParent.get());
          maybeFinalizedBlock = maybeParent;
        }
        counter++;
        if (counter % 5_000 == 0) {
          System.out.printf("%s (%s)...%n", currentBlock.getRoot(), currentBlock.getSlot());
        }
      }
    } catch (DatabaseStorageException ex) {
      System.out.println("Failed to open database");
    }
    final long endTimeMillis = System.currentTimeMillis();
    System.out.printf(
        "Done. Checked %s blocks in %s ms%n", counter, endTimeMillis - startTimeMillis);
    return 0;
  }

  @Command(
      name = "dump-hot-blocks",
      description = "Writes all non-finalized blocks in the database as a zip of SSZ files",
      mixinStandardHelpOptions = true,
      showDefaultValues = true,
      abbreviateSynopsis = true,
      versionProvider = PicoCliVersionProvider.class,
      synopsisHeading = "%n",
      descriptionHeading = "%nDescription:%n%n",
      optionListHeading = "%nOptions:%n",
      footerHeading = "%n",
      footer = "Teku is licensed under the Apache License 2.0")
  public int dumpHotBlocks(
      @Mixin final BeaconNodeDataOptions beaconNodeDataOptions,
      @Mixin final Eth2NetworkOptions eth2NetworkOptions,
      @Option(
              required = true,
              names = {"--output", "-o"},
              description = "File to write blocks to")
          final Path outputFile)
      throws Exception {
    int index = 0;
    try (final Database database = createDatabase(beaconNodeDataOptions, eth2NetworkOptions);
        final Stream<Bytes> blockStream = database.streamHotBlocksAsSsz().map(Map.Entry::getValue);
        final ZipOutputStream out =
            new ZipOutputStream(
                Files.newOutputStream(
                    outputFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
      for (final Iterator<Bytes> iterator = blockStream.iterator(); iterator.hasNext(); ) {
        out.putNextEntry(new ZipEntry(index + ".ssz"));
        final Bytes blockData = iterator.next();
        out.write(blockData.toArrayUnsafe());
        index++;
      }
    }
    System.out.println("Wrote " + index + " blocks to " + outputFile.toAbsolutePath());
    return 0;
  }

  @Command(
      name = "dump-blobs-sidecar",
      description = "Writes all blobs sidecars in the database as a zip of SSZ files",
      mixinStandardHelpOptions = true,
      showDefaultValues = true,
      abbreviateSynopsis = true,
      versionProvider = PicoCliVersionProvider.class,
      synopsisHeading = "%n",
      descriptionHeading = "%nDescription:%n%n",
      optionListHeading = "%nOptions:%n",
      footerHeading = "%n",
      footer = "Teku is licensed under the Apache License 2.0")
  public int dumpBlobsSidecar(
      @Mixin final BeaconNodeDataOptions beaconNodeDataOptions,
      @Mixin final Eth2NetworkOptions eth2NetworkOptions,
      @Option(
              names = {"--from-slot"},
              description = "Dump blobs sidecars starting from a given slot (inclusive)")
          final Long fromSlot,
      @Option(
              names = {"--to-slot"},
              description = "Dump blobs sidecars up to a given slot (inclusive)")
          final Long toSlot,
      @Option(
              required = true,
              names = {"--output", "-o"},
              description = "File to write blobs sidecars to")
          final Path outputFile)
      throws Exception {

    final UInt64 from = Optional.ofNullable(fromSlot).map(UInt64::valueOf).orElse(UInt64.ZERO);
    final UInt64 to = Optional.ofNullable(toSlot).map(UInt64::valueOf).orElse(UInt64.MAX_VALUE);

    if (from.isGreaterThan(to)) {
      throw new InvalidConfigurationException("--from-slot must less then or equal to --to-slot");
    }

    int index = 0;
    try (final Database database = createDatabase(beaconNodeDataOptions, eth2NetworkOptions);
        final ZipOutputStream out =
            new ZipOutputStream(
                Files.newOutputStream(
                    outputFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {

      try (final Stream<Map.Entry<SlotAndBlockRoot, Bytes>> streamBlobsSidecarAsSsz =
          database.streamBlobsSidecarsAsSsz(from, to)) {
        for (final Iterator<Map.Entry<SlotAndBlockRoot, Bytes>> iterator =
                streamBlobsSidecarAsSsz.iterator();
            iterator.hasNext(); ) {
          final Map.Entry<SlotAndBlockRoot, Bytes> entryData = iterator.next();
          out.putNextEntry(
              new ZipEntry(
                  entryData.getKey().getSlot()
                      + "_"
                      + entryData.getKey().getBlockRoot().toUnprefixedHexString()
                      + ".ssz"));
          out.write(entryData.getValue().toArrayUnsafe());
          index++;
        }
      }

      System.out.println("Wrote " + index + " blobs sidecars to " + outputFile.toAbsolutePath());

      index = 0;
      try (final Stream<SlotAndBlockRoot> unconfirmedBlobs =
          database.streamUnconfirmedBlobsSidecars(from, to)) {
        out.putNextEntry(new ZipEntry("unconfirmed.txt"));
        for (final Iterator<SlotAndBlockRoot> iterator = unconfirmedBlobs.iterator();
            iterator.hasNext(); ) {
          final SlotAndBlockRoot entryData = iterator.next();
          out.write(entryData.toLogString().getBytes(StandardCharsets.UTF_8));
          if (iterator.hasNext()) {
            out.write("\n".getBytes(StandardCharsets.UTF_8));
          }
          index++;
        }
      }
    }

    System.out.println(
        "Wrote " + index + " unconfirmed blobs sidecars to " + outputFile.toAbsolutePath());
    return 0;
  }

  @Command(
      name = "validate-blobs-sidecars",
      description = "Validate the blobs sidecars",
      mixinStandardHelpOptions = true,
      showDefaultValues = true,
      abbreviateSynopsis = true,
      versionProvider = PicoCliVersionProvider.class,
      synopsisHeading = "%n",
      descriptionHeading = "%nDescription:%n%n",
      optionListHeading = "%nOptions:%n",
      footerHeading = "%n",
      footer = "Teku is licensed under the Apache License 2.0")
  public int validateBlobsSidecars(
      @Mixin final BeaconNodeDataOptions beaconNodeDataOptions,
      @Mixin final Eth2NetworkOptions eth2NetworkOptions,
      @Option(
              required = true,
              names = {"--current-slot", "-s"},
              description =
                  "Provide the current slot from which calculate Data Availability window")
          final Long currentSlot)
      throws Exception {

    final Spec spec = eth2NetworkOptions.getNetworkConfiguration().getSpec();

    final UInt64 denebActivationEpoch =
        spec.forMilestone(SpecMilestone.DENEB)
            .getConfig()
            .toVersionDeneb()
            .orElseThrow()
            .getDenebForkEpoch();

    final UInt64 currentEpoch =
        eth2NetworkOptions
            .getNetworkConfiguration()
            .getSpec()
            .computeEpochAtSlot(UInt64.valueOf(currentSlot));

    final UInt64 lowerEpochBoundDataAvailability =
        currentEpoch.minusMinZero(MIN_EPOCHS_FOR_BLOBS_SIDECARS_REQUESTS).max(denebActivationEpoch);

    final UInt64 lowerSlotBoundDataAvailability =
        eth2NetworkOptions
            .getNetworkConfiguration()
            .getSpec()
            .computeStartSlotAtEpoch(lowerEpochBoundDataAvailability);

    long missingFinalizedBlobsSidecars = 0;
    long missingHotBlobsSidecars = 0;
    long orphanBlobsSidecars = 0;
    long unconfirmedBlobsSidecarsWithBlocks = 0;
    long unprunedBlobsSidecars = 0;
    long unprunedUnconfirmedBlobsSidecarsNonFinalized = 0;
    long unprunedUnconfirmedBlobsSidecarsFinalized = 0;
    long orphanUnconfirmations = 0;
    long blocksToSidecarsCheckCounter = 0;
    long sidecarsToBlocksCheckCounter = 0;

    final long startTimeMillis = System.currentTimeMillis();

    Optional<UInt64> maybeEarliestBlobsSidecarSlot = Optional.empty();
    Optional<UInt64> maybeLatestFinalizedSlot = Optional.empty();
    Optional<SignedBeaconBlock> maybeEarliestBlock = Optional.empty();
    try (final Database database = createDatabase(beaconNodeDataOptions, eth2NetworkOptions)) {

      // checking blobs for finalized blocks

      Optional<SignedBeaconBlock> maybeFinalizedBlock = database.getLastAvailableFinalizedBlock();
      maybeEarliestBlock = database.getEarliestAvailableBlock();
      maybeEarliestBlobsSidecarSlot = database.getEarliestBlobsSidecarSlot();
      maybeLatestFinalizedSlot = maybeFinalizedBlock.map(SignedBeaconBlock::getSlot);
      while (maybeFinalizedBlock.isPresent()) {
        final SignedBeaconBlock currentBlock = maybeFinalizedBlock.get();
        if (blocksToSidecarsCheckCounter == 0) {
          System.out.printf(
              "Checking blobs sidecars tracing chain from best finalized block %s (%s)%n",
              currentBlock.getRoot(), currentBlock.getSlot());
        }

        // check blobs sidecar existence
        final SlotAndBlockRoot slotAndBlockRoot =
            new SlotAndBlockRoot(currentBlock.getSlot(), currentBlock.getRoot());

        final Optional<BlobsSidecar> maybeCurrentBlobsSidecar;
        if (maybeEarliestBlobsSidecarSlot.isEmpty()
            || maybeEarliestBlobsSidecarSlot.get().isGreaterThan(currentBlock.getSlot())) {
          maybeCurrentBlobsSidecar = Optional.empty();
        } else {
          maybeCurrentBlobsSidecar = database.getBlobsSidecar(slotAndBlockRoot);
        }

        if (maybeCurrentBlobsSidecar.isEmpty()
            && currentBlock.getSlot().isGreaterThanOrEqualTo(lowerSlotBoundDataAvailability)) {
          // inside DA window but missing
          missingFinalizedBlobsSidecars++;
          System.err.printf(
              "ERROR: Unable to locate finalized blobs sidecar %s%n",
              slotAndBlockRoot.toLogString());
        }

        final Optional<SignedBeaconBlock> maybeParent =
            database.getSignedBlock(currentBlock.getParentRoot());
        if (maybeParent.isEmpty()) {
          if (maybeEarliestBlock.isPresent()
              && maybeEarliestBlock.get().getRoot().equals(currentBlock.getRoot())) {
            System.out.printf(
                "Found back to earliest block %s (%s)%n",
                currentBlock.getRoot(), currentBlock.getSlot());
            break;
          }
        } else {
          checkFinalizedIndices(database, maybeParent.get());
          maybeFinalizedBlock = maybeParent;
        }
        blocksToSidecarsCheckCounter++;
        if (blocksToSidecarsCheckCounter % 5_000 == 0) {
          System.out.printf("%s (%s)...%n", currentBlock.getRoot(), currentBlock.getSlot());
        }
      }

      // checking blobs sidecars for hot blocks

      try (final Stream<Map.Entry<Bytes, Bytes>> blockStream = database.streamHotBlocksAsSsz()) {
        // Iterate through hot blocks
        System.out.printf(
            "Checking blobs sidecars excess with respect to finalized or hot blocks block...%n");

        for (final Iterator<Map.Entry<Bytes, Bytes>> iterator = blockStream.iterator();
            iterator.hasNext(); ) {
          blocksToSidecarsCheckCounter++;

          final Map.Entry<Bytes, Bytes> rootAndBlock = iterator.next();
          final Bytes blockData = rootAndBlock.getValue();
          final UInt64 blockSlot = BeaconBlockInvariants.extractSignedBeaconBlockSlot(blockData);

          // check blobs sidecar existence
          final SlotAndBlockRoot slotAndBlockRoot =
              new SlotAndBlockRoot(blockSlot, Bytes32.wrap(rootAndBlock.getKey()));
          final Optional<BlobsSidecar> maybeCurrentBlobsSidecar =
              database.getBlobsSidecar(slotAndBlockRoot);
          if (maybeCurrentBlobsSidecar.isEmpty()
              && blockSlot.isGreaterThanOrEqualTo(lowerSlotBoundDataAvailability)) {
            // inside DA window but missing
            missingFinalizedBlobsSidecars++;
            System.err.printf(
                "ERROR: Unable to locate hot blobs sidecar %s%n", slotAndBlockRoot.toLogString());
          }
        }
      }

      final ArrayList<SlotAndBlockRoot> unconfirmedBlobsSidecars = new ArrayList<>();

      // collect unconfirmed blobs sidecar
      try (final Stream<SlotAndBlockRoot> slotAndBlockRootStream =
          database.streamUnconfirmedBlobsSidecars(UInt64.ZERO, UInt64.MAX_VALUE)) {
        slotAndBlockRootStream.forEach(unconfirmedBlobsSidecars::add);
      }

      // calculate non-pruned or orphaned blobs sidecars
      try (final Stream<SlotAndBlockRoot> blobsSidecarsKeys =
          database.streamBlobsSidecarKeys(UInt64.ZERO, UInt64.MAX_VALUE)) {
        // Iterate through hot blocks
        for (final Iterator<SlotAndBlockRoot> iterator = blobsSidecarsKeys.iterator();
            iterator.hasNext(); ) {
          final SlotAndBlockRoot blobsSidecarsKey = iterator.next();

          sidecarsToBlocksCheckCounter++;

          final boolean isUnconfirmed = unconfirmedBlobsSidecars.contains(blobsSidecarsKey);

          final Optional<SignedBeaconBlock> maybeFinalizedBlockFound =
              database
                  .getFinalizedBlockAtSlot(blobsSidecarsKey.getSlot())
                  .filter(block -> block.getRoot().equals(blobsSidecarsKey.getBlockRoot()));

          final Optional<SignedBeaconBlock> maybeHotBlockFound =
              database.getHotBlock(blobsSidecarsKey.getBlockRoot());

          if (isUnconfirmed) {
            // unconfirmed, check a corresponding hot or finalized block doesn't exist

            if (maybeHotBlockFound.isPresent()) {
              unconfirmedBlobsSidecarsWithBlocks++;
              System.err.printf(
                  "ERROR: unconfirmed blobs sidecar has a corresponding hot block %s%n",
                  blobsSidecarsKey.toLogString());
            } else if (maybeFinalizedBlockFound.isPresent()) {
              unconfirmedBlobsSidecarsWithBlocks++;
              System.err.printf(
                  "ERROR: unconfirmed blobs sidecar has a corresponding finalized block %s%n",
                  blobsSidecarsKey.toLogString());
            }
          } else {
            // is confirmed, hot or finalized blocks must exist

            if (maybeHotBlockFound.isEmpty() && maybeFinalizedBlockFound.isEmpty()) {

              orphanBlobsSidecars++;

              System.err.printf(
                  "ERROR: confirmed blobs sidecar has no corresponding finalized or hot block %s%n",
                  blobsSidecarsKey.toLogString());
            }
          }

          // check Data Availability
          if (blobsSidecarsKey.getSlot().isLessThan(lowerSlotBoundDataAvailability)) {
            unprunedBlobsSidecars++;
            System.err.printf(
                "WARNING: non pruned blobs sidecar %s%n", blobsSidecarsKey.toLogString());
          }
        }
      }

      // check that unconfirmed blobs sidecar exists in the main blobs sidecar table
      System.out.printf("Checking unconfirmed blobs sidecar in main blobs sidecar table...%n");

      for (final SlotAndBlockRoot slotAndBlockRoot : unconfirmedBlobsSidecars) {
        if (maybeLatestFinalizedSlot
            .map(slot -> slotAndBlockRoot.getSlot().isGreaterThan(slot))
            .orElse(false)) {
          // unconfirmed sidecars for a non finalized slot
          unprunedUnconfirmedBlobsSidecarsNonFinalized++;
          System.err.printf(
              "WARNING: non pruned unconfirmed hot blobs sidecar %s%n",
              slotAndBlockRoot.toLogString());

        } else {
          // unconfirmed sidecars for finalized slot
          unprunedUnconfirmedBlobsSidecarsFinalized++;
          System.err.printf(
              "WARNING: non pruned unconfirmed finalized blobs sidecar %s%n",
              slotAndBlockRoot.toLogString());
        }

        if (database.getBlobsSidecar(slotAndBlockRoot).isEmpty()) {
          orphanUnconfirmations++;
          System.err.printf(
              "ERROR: Unable to locate unconfirmed blobs sidecar in main blobs sidecar table %s%n",
              slotAndBlockRoot.toLogString());
        }
      }
    } catch (DatabaseStorageException ex) {
      System.out.println("Failed to open database");
    }

    final long endTimeMillis = System.currentTimeMillis();
    System.out.printf(
        "Done. Performed %s block->blobs_sidecars and %s blobs_sidecars->block checks in %s ms%n",
        blocksToSidecarsCheckCounter,
        sidecarsToBlocksCheckCounter,
        endTimeMillis - startTimeMillis);
    System.out.printf(
        "\tCalculated Data Availability window (slots): %s - %s%n",
        lowerSlotBoundDataAvailability, currentSlot);
    System.out.printf("\tEarliest BlobsSidecar slot: %s%n", maybeEarliestBlobsSidecarSlot);
    System.out.printf(
        "\tEarliest finalized block slot: %s%n",
        maybeEarliestBlock.map(SignedBeaconBlock::getSlot));
    System.out.printf("\tLatest finalized block slot: %s%n", maybeLatestFinalizedSlot);
    System.out.printf("\tMissing finalized BlobsSidecars: %s%n", missingFinalizedBlobsSidecars);
    System.out.printf("\tMissing hot BlobsSidecars: %s%n", missingHotBlobsSidecars);
    System.out.printf(
        "\tUnconfirmed BlobsSidecars with blocks (should have been confirmed): %s%n",
        unconfirmedBlobsSidecarsWithBlocks);
    System.out.printf(
        "\tOrphaned BlobsSidecars (corresponding block is missing): %s%n", orphanBlobsSidecars);
    System.out.printf(
        "\tNon-pruned BlobsSidecars (older than DA lower bound): %s%n", unprunedBlobsSidecars);
    System.out.printf(
        "\tNon-pruned unconfirmed Hot BlobsSidecars (related to non-imported blocks): %s%n",
        unprunedUnconfirmedBlobsSidecarsNonFinalized);
    System.out.printf(
        "\tNon-pruned unconfirmed Finalized BlobsSidecars (related to non-imported blocks): %s%n",
        unprunedUnconfirmedBlobsSidecarsFinalized);
    System.out.printf(
        "\tOrphaned unconfirmations of BlobsSidecars (corresponding BlobsSidecars is missing): %s%n",
        orphanUnconfirmations);

    return 0;
  }

  @Command(
      name = "delete-hot-blocks",
      description = "Writes all non-justified blocks in the database as a zip of SSZ files",
      mixinStandardHelpOptions = true,
      showDefaultValues = true,
      abbreviateSynopsis = true,
      versionProvider = PicoCliVersionProvider.class,
      synopsisHeading = "%n",
      descriptionHeading = "%nDescription:%n%n",
      optionListHeading = "%nOptions:%n",
      footerHeading = "%n",
      footer = "Teku is licensed under the Apache License 2.0")
  public int deleteHotBlocks(
      @Mixin final BeaconNodeDataOptions beaconNodeDataOptions,
      @Mixin final Eth2NetworkOptions eth2NetworkOptions,
      @Option(
              names = {"--delete-all", "-a"},
              description =
                  "Delete all blocks that have not been justified, not just those from an invalid hard fork",
              defaultValue = "false",
              fallbackValue = "true",
              showDefaultValue = Visibility.ALWAYS)
          final boolean deleteAll)
      throws Exception {
    final Spec spec = eth2NetworkOptions.getNetworkConfiguration().getSpec();
    try (final Database database = createDatabase(beaconNodeDataOptions, eth2NetworkOptions)) {
      final Optional<Checkpoint> justified = database.getJustifiedCheckpoint();
      if (justified.isEmpty()) {
        System.out.println(
            "Unable to delete hot blocks because the justified checkpoint is not available");
        return 1;
      }
      final UInt64 justifiedSlot = justified.get().getEpochStartSlot(spec);
      System.out.println("Justified slot is " + justifiedSlot);
      final Set<Bytes32> blockRootsToDelete = new HashSet<>();
      try (final Stream<Map.Entry<Bytes, Bytes>> blockStream = database.streamHotBlocksAsSsz()) {
        // Iterate through and find the block roots to delete first
        for (final Iterator<Map.Entry<Bytes, Bytes>> iterator = blockStream.iterator();
            iterator.hasNext(); ) {
          final Map.Entry<Bytes, Bytes> rootAndBlock = iterator.next();
          final Bytes blockData = rootAndBlock.getValue();
          final UInt64 blockSlot = BeaconBlockInvariants.extractSignedBeaconBlockSlot(blockData);
          final boolean shouldDelete;
          final boolean isJustifiedBlock = blockSlot.isLessThanOrEqualTo(justifiedSlot);
          if (deleteAll) {
            if (isJustifiedBlock) {
              System.out.printf(
                  "Not deleting block at slot %s as it has been justified%n", blockSlot);
              shouldDelete = false;
            } else {
              shouldDelete = true;
            }
          } else {
            final boolean canParse = canParseBlock(spec, blockData);
            if (!canParse && isJustifiedBlock) {
              System.out.printf(
                  "Block at slot %s is justified but cannot be parsed. Unable to recover this database.%n",
                  blockSlot);
              return 1;
            }
            shouldDelete = !canParse;
          }

          if (shouldDelete) {
            blockRootsToDelete.add(Bytes32.wrap(rootAndBlock.getKey()));
          }
        }
      }

      System.out.printf("Deleting %d blocks%n", blockRootsToDelete.size());
      database.deleteHotBlocks(blockRootsToDelete);
    }
    return 0;
  }

  private boolean canParseBlock(final Spec spec, final Bytes blockData) {
    try {
      spec.deserializeSignedBeaconBlock(blockData);
      return true;
    } catch (final Exception e) {
      return false;
    }
  }

  private Optional<SignedBeaconBlock> findFinalizedBlockBeforeSlot(
      final Database database, final UInt64 knownSlot) {
    UInt64 parentSearchSlot = knownSlot.decrement().decrement();

    while (parentSearchSlot.isGreaterThan(knownSlot.minus(128))) {
      Optional<Bytes32> blockRoot = database.getFinalizedBlockRootBySlot(parentSearchSlot);
      if (blockRoot.isPresent()) {
        Optional<SignedBeaconBlock> block = database.getSignedBlock(blockRoot.get());
        if (block.isEmpty()) {
          System.err.printf(
              "ERROR: Found index at slot %s (%s), but block could not be retrieved%n",
              blockRoot.get(), parentSearchSlot);
        } else {
          return block;
        }
      } else {
        System.err.printf("WARNING: No block found at slot %s%n", parentSearchSlot);
      }
      parentSearchSlot = parentSearchSlot.decrement();
    }
    System.err.printf(
        "Searched back 128 blocks from last known block, and couldn't find anything helpful, cannot continue checking via parent references..%n");
    return Optional.empty();
  }

  private void checkFinalizedIndices(final Database database, final SignedBeaconBlock parentBlock) {

    UInt64 parentSlot = parentBlock.getSlot();
    Optional<UInt64> indexSlot = database.getSlotForFinalizedBlockRoot(parentBlock.getRoot());
    if (indexSlot.isEmpty() || !indexSlot.get().equals(parentSlot)) {
      System.err.printf(
          "Finalized block index for root %s reports slot %s, expected slot %s%n",
          parentBlock.getRoot(), (indexSlot.isPresent() ? indexSlot.get() : "BLANK"), parentSlot);
    }
  }

  private void printColumn(final String label, final long count) {
    System.out.printf("%40s: %d%n", label, count);
  }

  private Database createDatabase(
      final BeaconNodeDataOptions beaconNodeDataOptions,
      final Eth2NetworkOptions eth2NetworkOptions) {
    final Spec spec = eth2NetworkOptions.getNetworkConfiguration().getSpec();
    final VersionedDatabaseFactory databaseFactory =
        new VersionedDatabaseFactory(
            new NoOpMetricsSystem(),
            DataDirLayout.createFrom(beaconNodeDataOptions.getDataConfig())
                .getBeaconDataDirectory(),
            StorageConfiguration.builder()
                .eth1DepositContract(
                    eth2NetworkOptions.getNetworkConfiguration().getEth1DepositContractAddress())
                .specProvider(spec)
                .build());
    return databaseFactory.createDatabase();
  }

  private int writeState(final Path outputFile, final Optional<BeaconState> state) {
    if (state.isEmpty()) {
      System.err.println("No state available.");
      return 2;
    }
    try {
      Files.write(outputFile, state.get().sszSerialize().toArrayUnsafe());
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
      Files.write(outputFile, block.get().sszSerialize().toArrayUnsafe());
    } catch (IOException e) {
      System.err.println("Unable to write block to " + outputFile + ": " + e.getMessage());
      return 1;
    }
    return 0;
  }
}
