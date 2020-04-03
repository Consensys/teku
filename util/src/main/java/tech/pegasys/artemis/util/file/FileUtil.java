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

package tech.pegasys.artemis.util.file;

import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class FileUtil {
  private static final Logger LOG = LogManager.getLogger();

  public static void unTar(final File inputTarFile, final File outputDir)
      throws IOException, ArchiveException {
    final InputStream fileStream = new FileInputStream(inputTarFile);
    try (final ArchiveInputStream archiveStream =
        new ArchiveStreamFactory().createArchiveInputStream("tar", fileStream)) {
      ArchiveEntry entry;
      while ((entry = archiveStream.getNextEntry()) != null) {
        final File outputFile = new File(outputDir, entry.getName());
        if (entry.isDirectory() && !outputFile.exists()) {
          // Create directory
          if (!outputFile.mkdirs()) {
            throw new IllegalStateException(
                "Couldn't create directory " + outputFile.getAbsolutePath());
          }
        } else if (!entry.isDirectory()) {
          // Create file
          try (final OutputStream outputFileStream = new FileOutputStream(outputFile)) {
            IOUtils.copy(archiveStream, outputFileStream);
          }
        }
      }
    }
  }

  public static void recursivelyDeleteDirectories(final List<File> directories) {
    for (File tmpDirectory : directories) {
      try {
        MoreFiles.deleteRecursively(tmpDirectory.toPath(), RecursiveDeleteOption.ALLOW_INSECURE);
      } catch (IOException e) {
        LOG.error("Failed to delete directory: " + tmpDirectory.getAbsolutePath(), e);
        throw new RuntimeException(e);
      }
    }
  }
}
