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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockInvariants;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.util.SlotAndBlockRootAndBlobIndex;
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
      name = "dump-blob-sidecars",
      description = "Writes all blob sidecars in the database as a zip of SSZ files",
      mixinStandardHelpOptions = true,
      showDefaultValues = true,
      abbreviateSynopsis = true,
      versionProvider = PicoCliVersionProvider.class,
      synopsisHeading = "%n",
      descriptionHeading = "%nDescription:%n%n",
      optionListHeading = "%nOptions:%n",
      footerHeading = "%n",
      footer = "Teku is licensed under the Apache License 2.0")
  public int dumpBlobSidecars(
      @Mixin final BeaconNodeDataOptions beaconNodeDataOptions,
      @Mixin final Eth2NetworkOptions eth2NetworkOptions,
      @Option(
              names = {"--from-slot"},
              description = "Dump blob sidecars starting from a given slot (inclusive)")
          final Long fromSlot,
      @Option(
              names = {"--to-slot"},
              description = "Dump blob sidecars up to a given slot (inclusive)")
          final Long toSlot,
      @Option(
              required = true,
              names = {"--output", "-o"},
              description = "File to write blob sidecars to")
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

      try (final Stream<SlotAndBlockRootAndBlobIndex> keyStream =
          database.streamBlobSidecarKeys(from, to)) {

        for (final Iterator<SlotAndBlockRootAndBlobIndex> it = keyStream.iterator();
            it.hasNext(); ) {
          final SlotAndBlockRootAndBlobIndex key = it.next();
          if (key.isNoBlobsKey()) {
            continue;
          }
          final BlobSidecar blobSidecar = database.getBlobSidecar(key).orElseThrow();
          out.putNextEntry(
              new ZipEntry(
                  blobSidecar.getSlot()
                      + "_"
                      + blobSidecar.getBlockRoot().toUnprefixedHexString()
                      + ".ssz"));
          blobSidecar.sszSerialize(out);
          index++;
        }
      }

      System.out.println("Wrote " + index + " blob sidecars to " + outputFile.toAbsolutePath());
    }
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
