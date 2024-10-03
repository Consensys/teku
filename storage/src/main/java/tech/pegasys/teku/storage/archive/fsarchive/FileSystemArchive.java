/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.storage.archive.fsarchive;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.storage.archive.DataArchive;
import tech.pegasys.teku.storage.archive.DataArchiveWriter;
import tech.pegasys.teku.storage.archive.DataArchiveWriterFactory;

/**
 * A file system based implementations of the DataArchive. Writes to a directory using the
 * PathResolver method to decide where to write the files.
 */
public class FileSystemArchive implements DataArchive, DataArchiveWriterFactory {
  private static final String INDEX_FILE = "index.txt";

  private static final Logger LOG = LogManager.getLogger();

  private final Path baseDirectory;
  private final BlobSidecarJsonWriter jsonWriter;

  public FileSystemArchive(final Path baseDirectory) {
    this.baseDirectory = baseDirectory;
    this.jsonWriter = new BlobSidecarJsonWriter();
  }

  public DataArchiveWriter<BlobSidecar> getBlobSidecarWriter() throws IOException {

    try {
      File indexFile = baseDirectory.resolve(INDEX_FILE).toFile();
      return new FileSystemBlobSidecarWriter(indexFile);
    } catch (IOException e) {
      LOG.warn("Unable to create BlobSidecar archive writer", e);
      throw e;
    }
  }

  private class FileSystemBlobSidecarWriter implements DataArchiveWriter<BlobSidecar>, Closeable {
    final BufferedWriter indexWriter;

    public FileSystemBlobSidecarWriter(final File indexFile) throws IOException {
      indexWriter =
          new BufferedWriter(
              new OutputStreamWriter(
                  new FileOutputStream(indexFile, true), StandardCharsets.UTF_8));
    }

    @Override
    public boolean archive(final BlobSidecar blobSidecar) {

      SlotAndBlockRoot slotAndBlockRoot = blobSidecar.getSlotAndBlockRoot();
      File file = resolve(baseDirectory, slotAndBlockRoot);
      if (file.exists()) {
        LOG.warn("Failed to write BlobSidecar. File exists: {}", file.toString());
        return false;
      }

      if (!file.mkdirs()) {
        LOG.warn("Failed to write BlobSidecar. Could not make directories to: {}", file.toString());
        return false;
      }

      try (FileOutputStream output = new FileOutputStream(file)) {
        jsonWriter.writeBlobSidecar(output, blobSidecar);
        indexWriter.write(formatIndexOutput(slotAndBlockRoot));
        indexWriter.newLine();
        return true;
      } catch (IOException e) {
        LOG.warn("Failed to write BlobSidecar.", e);
        return false;
      }
    }

    private String formatIndexOutput(final SlotAndBlockRoot slotAndBlockRoot) {
      return slotAndBlockRoot.getSlot()
          + " "
          + slotAndBlockRoot.getBlockRoot().toUnprefixedHexString();
    }

    @Override
    public void close() throws IOException {
      indexWriter.flush();
      indexWriter.close();
    }
  }

  /**
   * Given a basePath, slot and block root, return where to store/find the BlobSidecar. Initial
   * implementation uses blockRoot as a hex string in the directory of the first two characters.
   *
   * @param basePath The base directory for the BlobSidecar.
   * @param slotAndBlockRoot The slot and block root.
   * @return a path of where to store or find the BlobSidecar
   */
  File resolve(final Path basePath, final SlotAndBlockRoot slotAndBlockRoot) {
    // For blockroot 0x1a2bcd...  the directory is basePath/1a/2b/1a2bcd...
    // 256 * 256 directories = 65,536.
    // Assume 8000 to 10000 blobs per day. With perfect hash distribution,
    // all directories have one file after a week. After 1 year, expect 50 files in each directory.
    String blockRootString = slotAndBlockRoot.getBlockRoot().toUnprefixedHexString();
    final String dir1 = blockRootString.substring(0, 1);
    final String dir2 = blockRootString.substring(2, 3);
    final String blobSidecarFilename =
        dir1 + File.pathSeparator + dir2 + File.pathSeparator + blockRootString;
    return basePath.resolve(blobSidecarFilename).toFile();
  }
}
