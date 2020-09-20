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

package tech.pegasys.teku.infrastructure.io;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;

/**
 * A wrapper class for reading and writing files, ensuring that the contents is immediately flushed
 * to disk.
 */
public class SyncDataAccessor {

  /**
   * Reads the content of the specified path, if it exists.
   *
   * @param path the path to read
   * @return Optional containing the entire file contents or empty optional if the file does not
   *     exist
   * @throws IOException if an IO error occurs while reading
   */
  public Optional<Bytes> read(final Path path) throws IOException {
    if (path.toFile().exists()) {
      return Optional.of(Bytes.wrap(Files.readAllBytes(path)));
    } else {
      return Optional.empty();
    }
  }

  /**
   * Writes data to the specified path, ensuring both the file content and metadata is immediately
   * flushed to hardware storage. If the file exists it is overwritten, otherwise it is created.
   *
   * @param path the path to write to
   * @param data the data to write
   * @exception IOException if an IO error occurs while writing
   */
  public void syncedWrite(final Path path, final Bytes data) throws IOException {
    final Path absolutePath = path.toAbsolutePath();
    if (absolutePath.getParent() != null) {
      final File parentDirectory = absolutePath.getParent().toFile();
      if (!parentDirectory.mkdirs() && !parentDirectory.isDirectory()) {
        throw new IOException("Unable to create directory " + parentDirectory);
      }
    }
    Files.write(
        absolutePath,
        data.toArrayUnsafe(),
        StandardOpenOption.SYNC,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING);
  }
}
