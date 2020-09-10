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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.util.config.InvalidConfigurationException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import static java.nio.file.StandardOpenOption.CREATE_NEW;

public class KeystoreLocker {

  private static final Logger LOG = LogManager.getLogger();
  private final byte[] processPID = calculateProcessPid();

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
        long pid = readPid(pidInBytes);
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

  private byte[] calculateProcessPid(){
    byte[] pidBytes;
    try {
      final long pid = ProcessHandle.current().pid();
      final ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES).order(ByteOrder.nativeOrder());
      buffer.putLong(pid);
      pidBytes = buffer.array();
    } catch (final UnsupportedOperationException e) {
      LOG.warn("Process ID can not be detected. This will inhibit Teku from " +
              "deleting stale validator keystore lockfiles in the future");
      pidBytes = new byte[0];
    }
    return pidBytes;
  }

  private long readPid(final byte[] pidBytes) {
    final ByteBuffer readBuffer = ByteBuffer.allocate(Long.BYTES).order(ByteOrder.nativeOrder());
    readBuffer.put(pidBytes);
    return readBuffer.getLong(0);
  }
}
