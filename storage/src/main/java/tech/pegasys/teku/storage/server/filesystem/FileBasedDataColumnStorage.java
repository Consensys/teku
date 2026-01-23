/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.storage.server.filesystem;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.SszMutableList;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;

/**
 * File-based storage for DataColumnSidecars.
 *
 * <p>Storage structure: {@code <baseDirectory>/<epoch>/<slot>_<blockRoot>.ssz}
 *
 * <p>Both canonical and non-canonical sidecars are stored in the same directory structure and
 * distinguished by their blockRoot. The Database layer maintains knowledge of which blockRoots are
 * canonical.
 *
 * <p>Metadata is stored in {@code <baseDirectory>/metadata.yml} containing earliest available slot
 * and other tracking information.
 */
public class FileBasedDataColumnStorage {
  private static final Logger LOG = LogManager.getLogger();
  private static final String METADATA_FILE_NAME = "metadata.properties";
  private static final String SSZ_FILE_EXTENSION = ".ssz";
  public static final String EARLIEST_AVAILABLE_SLOT = "earliestAvailableSlot";
  public static final String FIRST_CUSTODY_INCOMPLETE_SLOT = "firstCustodyIncompleteSlot";
  public static final String DATA_MIGRATION_COMPLETE = "dataMigrationComplete";

  private final Spec spec;
  private final Path baseDirectory;
  private final Path metadataFile;
  private final SszListSchema<DataColumnSidecar, ?> sidecarsSchema;

  public FileBasedDataColumnStorage(final Spec spec, final Path baseDirectory) {
    this.spec = spec;
    this.baseDirectory = baseDirectory;
    this.metadataFile = baseDirectory.resolve(METADATA_FILE_NAME);
    if (!spec.isMilestoneSupported(SpecMilestone.FULU)) {
      sidecarsSchema = null;
      return;
    }
    sidecarsSchema =
        SszListSchema.create(
            spec.forMilestone(SpecMilestone.FULU)
                .getSchemaDefinitions()
                .toVersionFulu()
                .orElseThrow()
                .getDataColumnSidecarSchema(),
            128);
    initializeStorage();
  }

  private void initializeStorage() {
    LOG.trace("Initializing file-based data column storage at: {}", baseDirectory);
    try {
      Files.createDirectories(baseDirectory);
      LOG.trace("Data column storage directory created or already exists: {}", baseDirectory);
    } catch (IOException e) {
      LOG.error("Failed to create base directory: {}", baseDirectory, e);
      throw new RuntimeException("Failed to initialize file-based data column storage", e);
    }
  }

  // ==================== Write Operations ====================

  public SszListSchema<DataColumnSidecar, ?> getDataColumnSidecarsSchema() {
    return sidecarsSchema;
  }

  /** Add a canonical sidecar to storage. */
  public void addSidecar(final DataColumnSidecar sidecar) {
    //    writeSidecar(sidecar);
    writeToSidecars(sidecar);
  }

  /** Add a non-canonical sidecar to storage. */
  public void addNonCanonicalSidecar(final DataColumnSidecar sidecar) {
    // Both canonical and non-canonical use same storage structure
    //    writeSidecar(sidecar);
    writeToSidecars(sidecar);
  }

  @VisibleForTesting
  synchronized void writeToSidecars(final DataColumnSidecar sidecar) {
    final Optional<SszList<DataColumnSidecar>> maybeExisting =
        readSidecars(sidecar.getSlot(), sidecar.getBeaconBlockRoot());
    final SszList<DataColumnSidecar> sidecars;
    if (maybeExisting.isPresent()) {
      final SszMutableList<DataColumnSidecar> list = maybeExisting.get().createWritableCopy();
      list.append(sidecar);
      sidecars = list.commitChanges();
    } else {
      sidecars = sidecarsSchema.createFromElements(List.of(sidecar));
    }

    writeToSidecars(sidecars, sidecar.getSlot(), sidecar.getBeaconBlockRoot());
  }

  @VisibleForTesting
  void writeToSidecars(
      final SszList<DataColumnSidecar> sidecars, final UInt64 slot, final Bytes32 blockRoot) {
    final Path sidecarPath = resolveSidecarsPath(slot, blockRoot);
    LOG.trace("Writing data column sidecars to file: {}", sidecarPath);
    // Create epoch directory if it doesn't exist
    try {
      Files.createDirectories(sidecarPath.getParent());
    } catch (IOException e) {
      LOG.error("Failed to create directory for sidecars: {}", sidecarPath, e);
      throw new RuntimeException("Failed to create directory for data column sidecar", e);
    }

    // Write SSZ data to file
    try (final OutputStream output =
        Files.newOutputStream(
            sidecarPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
      sidecars.sszSerialize(output);
    } catch (IOException e) {
      LOG.error("Failed to write sidecar to file: {}", sidecarPath, e);
      throw new RuntimeException("Failed to write data column sidecar to file", e);
    }
  }

  // ==================== Read Operations ====================
  public synchronized Optional<SszList<DataColumnSidecar>> readSidecars(
      final UInt64 slot, final Bytes32 blockRoot) {
    final Path sidecarsPath = resolveSidecarsPath(slot, blockRoot);
    if (Files.notExists(sidecarsPath)) {
      LOG.trace("Data column sidecars file does not exist: {}", blockRoot.toUnprefixedHexString());
      return Optional.empty();
    }

    try (final InputStream input = Files.newInputStream(sidecarsPath)) {
      final Bytes sszBytes = Bytes.wrap(input.readAllBytes());
      final SszList<DataColumnSidecar> sidecars = sidecarsSchema.sszDeserialize(sszBytes);
      LOG.trace(
          "Successfully read data column {} sidecars ({} bytes): {}",
          sidecars.size(),
          sszBytes.size(),
          sidecarsPath);
      return Optional.of(sidecars);
    } catch (IOException e) {
      LOG.warn("Failed to read sidecars from file: {}", sidecarsPath, e);
      return Optional.empty();
    } catch (Exception e) {
      LOG.error("Failed to deserialize sidecars from file: {}", sidecarsPath, e);
      return Optional.empty();
    }
  }

  /** Get a canonical sidecar by identifier. */
  public Optional<DataColumnSidecar> getSidecar(final DataColumnSlotAndIdentifier identifier) {
    return readSidecar(identifier);
  }

  /** Get a non-canonical sidecar by identifier. */
  public Optional<DataColumnSidecar> getNonCanonicalSidecar(
      final DataColumnSlotAndIdentifier identifier) {
    return readSidecar(identifier);
  }

  private Optional<DataColumnSidecar> readSidecar(final DataColumnSlotAndIdentifier identifier) {
    final Optional<SszList<DataColumnSidecar>> maybeSidecars =
        readSidecars(identifier.slot(), identifier.blockRoot());

    if (maybeSidecars.isPresent()) {
      return maybeSidecars.flatMap(
          sidecars ->
              sidecars.stream()
                  .filter(p -> p.getIndex().equals(identifier.columnIndex()))
                  .findFirst());
    }
    return Optional.empty();
  }

  // ==================== Stream Operations ====================

  /** Stream identifiers in a slot range (inclusive). */
  public Stream<DataColumnSlotAndIdentifier> streamIdentifiers(
      final UInt64 startSlot, final UInt64 endSlot) {
    return streamIdentifiersInRange(startSlot, endSlot);
  }

  /** Stream non-canonical identifiers in a slot range (inclusive). */
  public Stream<DataColumnSlotAndIdentifier> streamNonCanonicalIdentifiers(
      final UInt64 startSlot, final UInt64 endSlot) {
    return streamIdentifiersInRange(startSlot, endSlot);
  }

  private Stream<DataColumnSlotAndIdentifier> streamIdentifiersInRange(
      final UInt64 startSlot, final UInt64 endSlot) {
    final UInt64 startEpoch = spec.computeEpochAtSlot(startSlot);
    final UInt64 endEpoch = spec.computeEpochAtSlot(endSlot);

    final List<DataColumnSlotAndIdentifier> identifiers = new ArrayList<>();

    for (UInt64 epoch = startEpoch;
        epoch.isLessThanOrEqualTo(endEpoch);
        epoch = epoch.increment()) {
      final Path epochDir = baseDirectory.resolve(epoch.toString());

      if (!Files.exists(epochDir)) {
        continue;
      }

      try (DirectoryStream<Path> stream =
          Files.newDirectoryStream(epochDir, "*" + SSZ_FILE_EXTENSION)) {
        for (Path path : stream) {
          final Optional<SlotAndBlockRoot> maybeSlotAndBlockRoot =
              parseFilename(path.getFileName().toString());
          if (maybeSlotAndBlockRoot.isEmpty()) {
            LOG.debug("File went missing  {}", path);
          } else {
            final Optional<SszList<DataColumnSidecar>> maybeSidecars =
                readSidecars(
                    maybeSlotAndBlockRoot.get().getSlot(),
                    maybeSlotAndBlockRoot.get().getBlockRoot());
            if (maybeSidecars.isEmpty()) {
              continue;
            }
            identifiers.addAll(
                maybeSidecars.orElseThrow().stream()
                    .map(
                        sidecar ->
                            new DataColumnSlotAndIdentifier(
                                sidecar.getSlot(),
                                sidecar.getBeaconBlockRoot(),
                                sidecar.getIndex()))
                    .toList());
          }
        }
      } catch (IOException e) {
        LOG.warn("Failed to stream identifiers from epoch directory: {}", epoch, e);
      }
    }

    return identifiers.stream().sorted();
  }

  // ==================== Metadata Operations ====================

  /** Get the earliest available slot (after pruning). */
  public Optional<UInt64> getEarliestAvailableSlot() {
    final Map<String, Object> metadata = loadMetadata();
    final Object value = metadata.get(EARLIEST_AVAILABLE_SLOT);
    if (value != null) {
      return Optional.of(UInt64.valueOf(value.toString()));
    }
    return Optional.empty();
  }

  /** Set the earliest available slot (after pruning). */
  public void setEarliestAvailableSlot(final UInt64 slot) {
    LOG.trace("Setting earliest available data column slot to: {}", slot);
    final Map<String, Object> metadata = loadMetadata();
    metadata.put(EARLIEST_AVAILABLE_SLOT, slot.toString());
    saveMetadata(metadata);
  }

  /** Get the earliest slot by scanning the filesystem. */
  public Optional<UInt64> getEarliestSlot() {
    try (Stream<Path> epochDirs = Files.list(baseDirectory)) {
      return epochDirs
          .filter(Files::isDirectory)
          .map(Path::getFileName)
          .map(Path::toString)
          .filter(this::isNumeric)
          .map(UInt64::valueOf)
          .min(UInt64::compareTo)
          .flatMap(
              earliestEpoch -> {
                try (Stream<Path> files =
                    Files.list(baseDirectory.resolve(earliestEpoch.toString()))) {
                  return files
                      .filter(p -> p.toString().endsWith(SSZ_FILE_EXTENSION))
                      .map(Path::getFileName)
                      .map(Path::toString)
                      .map(this::parseFilename)
                      .filter(Optional::isPresent)
                      .map(Optional::get)
                      .map(SlotAndBlockRoot::getSlot)
                      .min(UInt64::compareTo);
                } catch (IOException e) {
                  LOG.warn("Failed to scan epoch directory: {}", earliestEpoch, e);
                  return Optional.empty();
                }
              });
    } catch (IOException e) {
      LOG.warn("Failed to scan for earliest slot", e);
      return Optional.empty();
    }
  }

  /** Get first custody incomplete slot from metadata. */
  public Optional<UInt64> getFirstCustodyIncompleteSlot() {
    final Map<String, Object> metadata = loadMetadata();
    final Object value = metadata.get(FIRST_CUSTODY_INCOMPLETE_SLOT);
    if (value != null) {
      return Optional.of(UInt64.valueOf(value.toString()));
    }
    return Optional.empty();
  }

  /** Set first custody incomplete slot in metadata. */
  public void setFirstCustodyIncompleteSlot(final UInt64 slot) {
    LOG.trace("Setting first custody incomplete slot to: {}", slot);
    final Map<String, Object> metadata = loadMetadata();
    metadata.put(FIRST_CUSTODY_INCOMPLETE_SLOT, slot.toString());
    saveMetadata(metadata);
  }

  /** Check if data migration from database to file storage is complete. */
  public boolean isMigrationComplete() {
    final Map<String, Object> metadata = loadMetadata();
    final Object value = metadata.get(DATA_MIGRATION_COMPLETE);
    return value != null && Boolean.parseBoolean(value.toString());
  }

  /** Set the data migration complete flag. */
  public void setMigrationComplete(final boolean complete) {
    LOG.info("Setting data migration complete flag to: {}", complete);
    final Map<String, Object> metadata = loadMetadata();
    metadata.put(DATA_MIGRATION_COMPLETE, String.valueOf(complete));
    saveMetadata(metadata);
  }

  private Map<String, Object> loadMetadata() {
    final Map<String, Object> metadata = new HashMap<>();
    metadata.put("version", "1");

    if (!Files.exists(metadataFile)) {
      return metadata;
    }

    final Properties props = new Properties();
    try (final BufferedReader reader = Files.newBufferedReader(metadataFile)) {
      props.load(reader);
      props.forEach((key, value) -> metadata.put(key.toString(), value));
    } catch (IOException e) {
      LOG.error("Failed to load metadata file, returning default metadata", e);
    }

    return metadata;
  }

  private void saveMetadata(final Map<String, Object> metadata) {
    final Properties props = new Properties();
    metadata.forEach((key, value) -> props.setProperty(key, value.toString()));

    try (final BufferedWriter writer = Files.newBufferedWriter(metadataFile)) {
      props.store(writer, "DataColumnSidecar Storage Metadata");
    } catch (IOException e) {
      LOG.error("Failed to save metadata file", e);
      throw new RuntimeException("Failed to save metadata", e);
    }
  }

  // ==================== Pruning ====================

  /**
   * Prune data columns up to and including the given slot.
   *
   * <p>Deletes entire epoch directories for epochs before the epoch containing tillSlotInclusive.
   * The epochLimit parameter limits how many epochs can be deleted in one call.
   *
   * @param tillSlotInclusive the last slot to prune (inclusive)
   * @param epochLimit maximum number of epochs to delete
   * @return the last successfully pruned epoch, or empty if nothing was pruned
   */
  public Optional<UInt64> pruneEpochs(final UInt64 tillSlotInclusive, final int epochLimit) {
    LOG.trace(
        "Starting data column pruning up to slot {} (epoch limit: {})",
        tillSlotInclusive,
        epochLimit);

    final UInt64 cutoffEpoch = spec.computeEpochAtSlot(tillSlotInclusive);

    // Find all epochs that should be pruned
    final List<UInt64> epochsToPrune = new ArrayList<>();

    try (Stream<Path> epochDirs = Files.list(baseDirectory)) {
      epochDirs
          .filter(Files::isDirectory)
          .map(Path::getFileName)
          .map(Path::toString)
          .filter(this::isNumeric)
          .map(UInt64::valueOf)
          .filter(epoch -> epoch.isLessThanOrEqualTo(cutoffEpoch))
          .sorted()
          .limit(epochLimit)
          .forEach(epochsToPrune::add);
    } catch (IOException e) {
      LOG.error("Failed to list epoch directories for pruning", e);
      return Optional.empty();
    }

    if (epochsToPrune.isEmpty()) {
      LOG.trace("No data column epochs to prune");
      return Optional.empty();
    }

    LOG.debug(
        "Pruning data column epochs from {} to {}",
        epochsToPrune.getFirst(),
        epochsToPrune.getLast());

    UInt64 lastPrunedEpoch = null;
    int prunedEpochs = 0;
    for (final UInt64 epoch : epochsToPrune) {
      final Path epochDir = baseDirectory.resolve(epoch.toString());
      LOG.trace("Deleting epoch directory: {}", epoch);
      if (deleteDirectory(epochDir)) {
        lastPrunedEpoch = epoch;
        prunedEpochs++;
        LOG.trace("Successfully deleted epoch directory: {}", epoch);
      }
    }

    LOG.debug("Pruned {} data column epoch directories", prunedEpochs);
    return Optional.ofNullable(lastPrunedEpoch);
  }

  private boolean deleteDirectory(final Path directory) {
    try {
      // Delete all files in the directory first
      try (Stream<Path> files = Files.list(directory)) {
        files.forEach(
            file -> {
              try {
                Files.deleteIfExists(file);
              } catch (IOException e) {
                LOG.warn("Failed to delete file: {}", file, e);
              }
            });
      }

      // Delete the directory itself
      Files.deleteIfExists(directory);
      return true;
    } catch (IOException e) {
      LOG.error("Failed to delete directory: {}", directory, e);
      return false;
    }
  }

  // ==================== Utilities ====================

  /** Get total count of stored sidecars (approximate, scans filesystem). */
  public long getCount() {
    LOG.trace("Counting data column sidecars in storage");
    try (Stream<Path> epochDirs = Files.list(baseDirectory)) {
      final long count =
          epochDirs
              .filter(Files::isDirectory)
              .mapToLong(
                  epochDir -> {
                    try (Stream<Path> files = Files.list(epochDir)) {
                      return files.filter(p -> p.toString().endsWith(SSZ_FILE_EXTENSION)).count();
                    } catch (IOException e) {
                      LOG.warn("Failed to count files in epoch directory: {}", epochDir, e);
                      return 0;
                    }
                  })
              .sum();
      LOG.trace("Total data column sidecars in storage: {}", count);
      return count;
    } catch (IOException e) {
      LOG.warn("Failed to count sidecars", e);
      return 0;
    }
  }

  /**
   * Resolve the file path for a sidecar.
   *
   * <p>Format: {@code <baseDirectory>/<epoch>/<slot>_<blockRoot>_<columnIndex>.ssz}
   */
  @VisibleForTesting
  Path resolveSidecarPath(final DataColumnSlotAndIdentifier identifier) {
    final Path epochDir = resolveEpochDirectory(identifier.slot());
    final String filename = formatFilename(identifier);
    return epochDir.resolve(filename);
  }

  @VisibleForTesting
  Path resolveSidecarsPath(final UInt64 slot, final Bytes32 blockRoot) {
    final Path epochDir = resolveEpochDirectory(slot);
    return epochDir.resolve(
        String.format("%s_%s%s", slot, blockRoot.toUnprefixedHexString(), SSZ_FILE_EXTENSION));
  }

  /**
   * Resolve the epoch directory for a slot.
   *
   * <p>Format: {@code <baseDirectory>/<epoch>}
   */
  @VisibleForTesting
  Path resolveEpochDirectory(final UInt64 slot) {
    final UInt64 epoch = spec.computeEpochAtSlot(slot);
    return baseDirectory.resolve(epoch.toString());
  }

  /**
   * Format the filename for a sidecar.
   *
   * <p>Format: {@code <slot>_<blockRoot>_<columnIndex>.ssz}
   */
  @VisibleForTesting
  String formatFilename(final DataColumnSlotAndIdentifier identifier) {
    return String.format(
        "%s_%s_%s%s",
        identifier.slot(),
        identifier.blockRoot().toUnprefixedHexString(),
        identifier.columnIndex(),
        SSZ_FILE_EXTENSION);
  }

  /**
   * Parse a filename into a SlotAndBlockRoot.
   *
   * <p>Format: {@code <slot>_<blockRoot>.ssz}
   */
  @VisibleForTesting
  Optional<SlotAndBlockRoot> parseFilename(final String filename) {
    if (!filename.endsWith(SSZ_FILE_EXTENSION)) {
      return Optional.empty();
    }

    try {
      final String withoutExtension =
          filename.substring(0, filename.length() - SSZ_FILE_EXTENSION.length());
      final List<String> parts = Splitter.on('_').splitToList(withoutExtension);

      if (parts.size() != 2) {
        return Optional.empty();
      }

      final UInt64 slot = UInt64.valueOf(parts.get(0));
      final Bytes32 blockRoot = Bytes32.fromHexString("0x" + parts.get(1));

      return Optional.of(new SlotAndBlockRoot(slot, blockRoot));
    } catch (Exception e) {
      LOG.debug("Failed to parse filename: {}", filename, e);
      return Optional.empty();
    }
  }

  private boolean isNumeric(final String str) {
    try {
      Long.parseLong(str);
      return true;
    } catch (NumberFormatException e) {
      return false;
    }
  }

  public void delete(final UInt64 slot, final Bytes32 blockRoot) {
    final Path sidecarsPath = resolveSidecarsPath(slot, blockRoot);
    try {
      Files.deleteIfExists(sidecarsPath);
    } catch (IOException e) {
      LOG.warn("Failed to delete file: {}", sidecarsPath, e);
    }
  }
}
