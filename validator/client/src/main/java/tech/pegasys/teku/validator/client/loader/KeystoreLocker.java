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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.util.config.InvalidConfigurationException;

public class KeystoreLocker {

  private static final Logger LOG = LogManager.getLogger();
  private final byte[] processPID = longPidToNativeByteArray(ProcessHandle.current().pid());

  public void lockKeystoreFile(Path keystoreFile) {
    deleteIfStaleLockfileExists(keystoreFile);
    try {
      final Path keystoreLockFile =
          Files.write(Path.of(keystoreFile.toString() + ".lock"), processPID, CREATE_NEW);
      keystoreLockFile.toFile().deleteOnExit();
    } catch (FileAlreadyExistsException e) {
      throw new InvalidConfigurationException("Keystore file " + keystoreFile + " already in use.");
    } catch (IOException e) {
      throw new UncheckedIOException("Unexpected error when trying to lock a keystore file.", e);
    }
  }

  static boolean deleteIfStaleLockfileExists(Path keystoreFile) {
    Path keystoreLockfile = Path.of(keystoreFile.toString() + ".lock");
    if (!keystoreLockfile.toFile().exists()) {
      return false;
    }

    try {
      byte[] pidInBytes = Files.readAllBytes(keystoreLockfile);
      if (pidInBytes.length == Long.BYTES) {
        long pid = nativeByteArrayToLong(pidInBytes);
        Optional<ProcessHandle> processHandle =
            ProcessHandle.of(pid).filter(ProcessHandle::isAlive);
        if (processHandle.isEmpty()) {
          if (!keystoreLockfile.toFile().delete() && keystoreLockfile.toFile().exists()) {
            LOG.warn("Could not delete stale lockfile.");
            return false;
          }
          return true;
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Unexpected error when trying read a keystore lockfile.", e);
    }
    return false;
  }

  static byte[] longPidToNativeByteArray(final long pid) {
    byte[] pidBytes;
    try {
      final ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES).order(ByteOrder.nativeOrder());
      buffer.putLong(pid);
      pidBytes = buffer.array();
    } catch (final UnsupportedOperationException e) {
      LOG.warn(
          "Process ID can not be detected. This will inhibit Teku from "
              + "deleting stale validator keystore lockfiles in the future");
      pidBytes = new byte[0];
    }
    return pidBytes;
  }

  private static long nativeByteArrayToLong(final byte[] bytes) {
    final ByteBuffer readBuffer = ByteBuffer.allocate(Long.BYTES).order(ByteOrder.nativeOrder());
    readBuffer.put(bytes);
    return readBuffer.getLong(0);
  }
}
