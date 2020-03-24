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

package tech.pegasys.artemis.test.acceptance.dsl.docker;

import static com.google.common.io.MoreFiles.deleteRecursively;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.commons.lang3.SystemUtils;

/**
 * A temporary folder abstraction that can be mounted to test container API using docker containers.
 */
public class TestContainerMountableFolder {
  private static final String TEMP_DIR_PREFIX = ".teku-acceptancetest-tmp-";
  // mountable in docker in Mac
  private static final String MAC_TEMP_DIR = "/tmp";

  public Path createTempDirectory() {
    try {
      if (SystemUtils.IS_OS_MAC) {
        final Path tempDirectory =
            Files.createTempDirectory(Paths.get(MAC_TEMP_DIR), TEMP_DIR_PREFIX);
        deleteOnExit(tempDirectory);
        return tempDirectory;
      }

      final Path tempDirectory = Files.createTempDirectory(TEMP_DIR_PREFIX);
      deleteOnExit(tempDirectory);
      return tempDirectory;
    } catch (final IOException e) {
      try {
        final Path tempDirectory = Files.createTempDirectory(Path.of("."), TEMP_DIR_PREFIX);
        deleteOnExit(tempDirectory);
        return tempDirectory;
      } catch (final IOException ex) {
        throw new UncheckedIOException(ex);
      }
    }
  }

  private void deleteOnExit(final Path path) {
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  try {
                    deleteRecursively(path);
                  } catch (IOException e) {
                    throw new UncheckedIOException(e);
                  }
                }));
  }
}
