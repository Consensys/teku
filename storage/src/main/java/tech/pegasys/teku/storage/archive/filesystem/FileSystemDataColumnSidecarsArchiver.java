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

package tech.pegasys.teku.storage.archive.filesystem;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.io.SyncDataAccessor;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.storage.archive.DataColumnSidecarsArchiver;

public class FileSystemDataColumnSidecarsArchiver implements DataColumnSidecarsArchiver {

  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final Path baseDirectory;
  private final SyncDataAccessor syncDataAccessor;

  public FileSystemDataColumnSidecarsArchiver(final Spec spec, final Path baseDirectory) {
    LOG.debug("Creating FileSystemDataColumnSidecarsArchiver stored at {}", baseDirectory);
    this.spec = spec;
    this.baseDirectory = baseDirectory;
    this.syncDataAccessor = SyncDataAccessor.create(baseDirectory);
  }

  @Override
  public void archive(
      final DataColumnSlotAndIdentifier identifier, final DataColumnSidecar sidecar) {
    final Path archivePath = resolveArchivePath(identifier);

    if (Files.exists(archivePath)) {
      LOG.debug("Sidecar already archived for {}", identifier);
      return;
    }

    try {
      Files.createDirectories(archivePath.getParent());
      final Bytes sszData = sidecar.sszSerialize();
      syncDataAccessor.syncedWrite(archivePath, sszData);
    } catch (final IOException ex) {
      LOG.error(String.format("Failed to archive data column sidecar for %s", identifier), ex);
    }
  }

  @Override
  public Optional<DataColumnSidecar> retrieve(final DataColumnSlotAndIdentifier identifier) {
    try {
      final Path archivePath = resolveArchivePath(identifier);
      if (!Files.exists(archivePath)) {
        return Optional.empty();
      }
      final Optional<Bytes> maybeBytes = syncDataAccessor.read(archivePath);
      return maybeBytes.map(bytes -> spec.deserializeSidecar(bytes, identifier.slot()));
    } catch (final IOException ex) {
      LOG.warn(String.format("Failed to retrieve data column sidecar for %s", identifier), ex);
      return Optional.empty();
    }
  }

  @Override
  public List<DataColumnSidecar> retrieveForSlot(final UInt64 slot) {
    final UInt64 epoch = spec.computeEpochAtSlot(slot);
    final Path epochDirectory = resolveEpochDirectory(epoch);

    if (!Files.exists(epochDirectory)) {
      return List.of();
    }

    final List<DataColumnSidecar> sidecars = new ArrayList<>();
    final String slotPrefix = slot.toString() + "_";

    try (final Stream<Path> files = Files.list(epochDirectory)) {
      files
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().startsWith(slotPrefix))
          .forEach(
              path -> {
                try {
                  final DataColumnSlotAndIdentifier identifier = parseFilename(path);
                  final Optional<Bytes> maybeBytes = syncDataAccessor.read(path);
                  maybeBytes
                      .map(bytes -> spec.deserializeSidecar(bytes, identifier.slot()))
                      .ifPresent(sidecars::add);
                } catch (final IOException ex) {
                  LOG.warn(String.format("Failed to read sidecar file %s", path), ex);
                } catch (final IllegalArgumentException ex) {
                  LOG.warn(String.format("Invalid filename format: %s", path), ex);
                }
              });
    } catch (final IOException ex) {
      LOG.error(String.format("Failed to list sidecars for slot %s", slot), ex);
    }

    return sidecars;
  }

  @Override
  public List<DataColumnSidecar> retrieveForBlock(final SlotAndBlockRoot slotAndBlockRoot) {
    final UInt64 epoch = spec.computeEpochAtSlot(slotAndBlockRoot.getSlot());
    final Path epochDirectory = resolveEpochDirectory(epoch);

    if (!Files.exists(epochDirectory)) {
      return List.of();
    }

    final List<DataColumnSidecar> sidecars = new ArrayList<>();
    final String filePrefix =
        slotAndBlockRoot.getSlot()
            + "_"
            + slotAndBlockRoot.getBlockRoot().toUnprefixedHexString()
            + "_";

    try (final Stream<Path> files = Files.list(epochDirectory)) {
      files
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().startsWith(filePrefix))
          .forEach(
              path -> {
                try {
                  final DataColumnSlotAndIdentifier identifier = parseFilename(path);
                  final Optional<Bytes> maybeBytes = syncDataAccessor.read(path);
                  maybeBytes
                      .map(bytes -> spec.deserializeSidecar(bytes, identifier.slot()))
                      .ifPresent(sidecars::add);
                } catch (final IOException ex) {
                  LOG.warn(String.format("Failed to read sidecar file %s", path), ex);
                } catch (final IllegalArgumentException ex) {
                  LOG.warn(String.format("Invalid filename format: %s", path), ex);
                }
              });
    } catch (final IOException ex) {
      LOG.error(
          String.format("Failed to list sidecars for block %s", slotAndBlockRoot.getBlockRoot()),
          ex);
    }

    return sidecars;
  }

  @Override
  public void pruneEpoch(final UInt64 epoch) {
    final Path epochDirectory = resolveEpochDirectory(epoch);

    if (!Files.exists(epochDirectory)) {
      return;
    }

    try (final Stream<Path> files = Files.list(epochDirectory)) {
      files.forEach(
          path -> {
            try {
              Files.delete(path);
            } catch (final IOException ex) {
              LOG.warn(String.format("Failed to delete file %s during epoch pruning", path), ex);
            }
          });
      Files.delete(epochDirectory);
    } catch (final IOException ex) {
      LOG.error(String.format("Failed to prune epoch %s", epoch), ex);
    }
  }

  /**
   * Resolves the file path for a given data column sidecar identifier.
   *
   * <p>Path format: {@code <baseDirectory>/<epoch>/<slot>_<blockRoot>_<columnIndex>.ssz}
   *
   * @param identifier the sidecar identifier
   * @return the resolved path
   */
  @VisibleForTesting
  Path resolveArchivePath(final DataColumnSlotAndIdentifier identifier) {
    final UInt64 epoch = spec.computeEpochAtSlot(identifier.slot());
    final String filename =
        identifier.slot()
            + "_"
            + identifier.blockRoot().toUnprefixedHexString()
            + "_"
            + identifier.columnIndex()
            + ".ssz";
    return baseDirectory.resolve(epoch.toString()).resolve(filename);
  }

  /**
   * Resolves the epoch directory path.
   *
   * @param epoch the epoch
   * @return the resolved directory path
   */
  @VisibleForTesting
  Path resolveEpochDirectory(final UInt64 epoch) {
    return baseDirectory.resolve(epoch.toString());
  }

  /**
   * Parses a sidecar filename to extract the identifier.
   *
   * <p>Expected format: {@code <slot>_<blockRoot>_<columnIndex>.ssz}
   *
   * @param path the file path
   * @return the parsed identifier
   * @throws IllegalArgumentException if the filename format is invalid
   */
  private DataColumnSlotAndIdentifier parseFilename(final Path path) {
    final String filename = path.getFileName().toString();
    if (!filename.endsWith(".ssz")) {
      throw new IllegalArgumentException("File does not have .ssz extension: " + filename);
    }

    final String nameWithoutExtension = filename.substring(0, filename.length() - 4);
    final List<String> parts = Splitter.on('_').splitToList(nameWithoutExtension);

    if (parts.size() != 3) {
      throw new IllegalArgumentException("Invalid filename format: " + filename);
    }

    try {
      final UInt64 slot = UInt64.valueOf(parts.getFirst());
      final Bytes32 blockRoot = Bytes32.fromHexString(parts.get(1));
      final UInt64 columnIndex = UInt64.valueOf(parts.getLast());
      return new DataColumnSlotAndIdentifier(slot, blockRoot, columnIndex);
    } catch (final IllegalArgumentException ex) {
      throw new IllegalArgumentException("Invalid filename format: " + filename, ex);
    }
  }
}
