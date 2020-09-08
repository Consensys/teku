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

package tech.pegasys.teku.validator.client.loader;

import static java.nio.file.StandardOpenOption.CREATE_NEW;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.primitives.Longs;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.util.config.InvalidConfigurationException;

public class KeystoreLocker {

  private static final Logger LOG = LogManager.getLogger();
  private final byte[] processPID = Longs.toByteArray(ProcessHandle.current().pid());

  @VisibleForTesting
  public void lockKeystoreFile(Path keystoreFile) {
    deleteIfStaleLockfileExists(keystoreFile);
    try {
      final Path keystoreLockFile =
          Files.write(Path.of(keystoreFile.toString() + ".lock"), processPID, CREATE_NEW);
      keystoreLockFile.toFile().deleteOnExit();
    } catch (FileAlreadyExistsException e) {
      throw new InvalidConfigurationException("Keystore file " + keystoreFile + " already in use.");
    } catch (IOException e) {
      throw new RuntimeException(
          "Unexpected error at KeystoreValidatorKeyProvider when locking keystore file.", e);
    }
  }

  public void deleteIfStaleLockfileExists(Path keystoreFile) {
    Path keystoreLockfile = Path.of(keystoreFile.toString() + ".lock");
    try {
      byte[] pidInBytes = Files.readAllBytes(keystoreLockfile);
      if (pidInBytes.length != 0) {
        long pid = Longs.fromByteArray(pidInBytes);
        ProcessHandle.of(pid)
            .ifPresent(
                p -> {
                  if (!p.isAlive()) {
                    if (!keystoreLockfile.toFile().delete()) {
                      LOG.warn("Could not delete stale lockfile.");
                    }
                  }
                });
      }
    } catch (IOException e) {
      if (e.equals(new NoSuchFileException(keystoreLockfile.toString()))) {
        throw new RuntimeException("Unexpected error when trying read keystore lockfile.");
      }
    }
  }
}
