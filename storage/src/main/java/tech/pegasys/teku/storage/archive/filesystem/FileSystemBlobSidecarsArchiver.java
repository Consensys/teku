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

import static tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition.listOf;

import com.google.common.annotations.VisibleForTesting;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;
import tech.pegasys.teku.storage.archive.BlobSidecarsArchiver;

public class FileSystemBlobSidecarsArchiver implements BlobSidecarsArchiver {

  static final String INDEX_FILE = "index.dat";

  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final Path baseDirectory;
  private final Path indexFile;

  public FileSystemBlobSidecarsArchiver(final Spec spec, final Path baseDirectory) {
    this.spec = spec;
    this.baseDirectory = baseDirectory;
    this.indexFile = baseDirectory.resolve(INDEX_FILE);
  }

  @Override
  public boolean archive(
      final SlotAndBlockRoot slotAndBlockRoot, final List<BlobSidecar> blobSidecars) {
    if (blobSidecars == null || blobSidecars.isEmpty()) {
      return true;
    }

    final Path path = resolve(slotAndBlockRoot);
    if (Files.exists(path)) {
      LOG.error("Failed to write BlobSidecars. File exists: {}", path);
      return false;
    }

    try {
      Files.createDirectories(path.getParent());
    } catch (final IOException __) {
      LOG.error("Failed to write BlobSidecars. Could not create directories: {}", path.getParent());
      return false;
    }

    try (final OutputStream output = Files.newOutputStream(path);
        final BufferedWriter indexWriter =
            Files.newBufferedWriter(
                indexFile,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND)) {
      writeBlobSidecars(output, slotAndBlockRoot, blobSidecars);
      indexWriter.write(formatIndexFileOutput(slotAndBlockRoot));
      indexWriter.newLine();
      return true;
    } catch (final IOException ex) {
      LOG.error(String.format("Failed to write BlobSidecars for %s", slotAndBlockRoot), ex);
      return false;
    }
  }

  /**
   * Given a basePath, slot and block root, return where to store/find the BlobSidecar. Initial
   * implementation uses blockRoot as a hex string in the directory of the first two characters.
   *
   * @param slotAndBlockRoot The slot and block root.
   * @return a path of where to store or find the BlobSidecar
   */
  @VisibleForTesting
  public Path resolve(final SlotAndBlockRoot slotAndBlockRoot) {
    // For blockroot 0x1a2bcd...  the directory is basePath/1a/2b/1a2bcd...
    // 256 * 256 directories = 65,536.
    // Assume 8000 to 10000 blobs per day. With perfect hash distribution,
    // all directories have one file after a week. After 1 year, expect 50 files in each directory.
    String blockRootString = slotAndBlockRoot.getBlockRoot().toUnprefixedHexString();
    final String dir1 = blockRootString.substring(0, 2);
    final String dir2 = blockRootString.substring(2, 4);
    final String blobSidecarFilename =
        dir1 + File.separator + dir2 + File.separator + blockRootString;
    return baseDirectory.resolve(blobSidecarFilename);
  }

  private void writeBlobSidecars(
      final OutputStream out,
      final SlotAndBlockRoot slotAndBlockRoot,
      final List<BlobSidecar> blobSidecars)
      throws IOException {
    final SerializableTypeDefinition<List<BlobSidecar>> type =
        listOf(
            SchemaDefinitionsDeneb.required(
                    spec.atSlot(slotAndBlockRoot.getSlot()).getSchemaDefinitions())
                .getBlobSidecarSchema()
                .getJsonTypeDefinition());
    JsonUtil.serializeToBytes(blobSidecars, type, out);
  }

  private String formatIndexFileOutput(final SlotAndBlockRoot slotAndBlockRoot) {
    return slotAndBlockRoot.getSlot()
        + " "
        + slotAndBlockRoot.getBlockRoot().toUnprefixedHexString();
  }
}
