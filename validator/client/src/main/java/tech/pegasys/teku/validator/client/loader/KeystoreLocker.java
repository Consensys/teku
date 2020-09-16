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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.WRITE;

public class KeystoreLocker {

  private static final Logger LOG = LogManager.getLogger();
  private final byte[] processPID = getProcessPID();

  public void lockKeystore(Path keystoreFile) {
    Path lockfilePath = Path.of(keystoreFile.toString() + ".lock");
    try {
      Path lockfile;
      if (lockfilePath.toFile().exists()) {
        FileLock lockfileLock = FileChannel.open(lockfilePath, WRITE).lock();
        if (isLockFileValid(lockfilePath)) {
          lockfileLock.release();
          throw new InvalidConfigurationException("Keystore file " + keystoreFile + " already in use.");
        }
        lockfile = Files.write(lockfilePath, processPID, WRITE);
        lockfileLock.release();
      } else {
        lockfile = Files.write(lockfilePath, processPID, CREATE_NEW);
      }
      lockfile.toFile().deleteOnExit();
    } catch (FileAlreadyExistsException e) {
      throw new InvalidConfigurationException("Keystore file " + keystoreFile + " already in use.");
    } catch (IOException e) {
      throw new UncheckedIOException("Unexpected error when trying to lock a keystore file.", e);
    }
  }

  private static boolean isLockFileValid(Path lockfilePath) {
    try {
      byte[] pidInBytes = Files.readAllBytes(lockfilePath);
      if (pidInBytes.length != Long.BYTES) {
        return true;
      }
      long pid = nativeByteArrayToLong(pidInBytes);
      Optional<ProcessHandle> processHandle =
              ProcessHandle.of(pid).filter(ProcessHandle::isAlive);
      return processHandle.isPresent();
    } catch (FileNotFoundException ignored) {
      return false;
    } catch (IOException e) {
      throw new UncheckedIOException("Unexpected error when trying read a keystore lockfile.", e);
    }
  }

  private static byte[] getProcessPID() {
    byte[] pidBytes;
    try {
      long pid = ProcessHandle.current().pid();
      pidBytes = longPidToNativeByteArray(pid);
    } catch (final UnsupportedOperationException e) {
      LOG.warn(
          "Process ID can not be detected. This will inhibit Teku from "
              + "deleting stale validator keystore lockfiles in the future");
      pidBytes = new byte[0];
    }
    return pidBytes;
  }

  static byte[] longPidToNativeByteArray(final long pid) {
    final ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES).order(ByteOrder.nativeOrder());
    buffer.putLong(pid);
    return buffer.array();
  }

  static long nativeByteArrayToLong(final byte[] bytes) {
    final ByteBuffer readBuffer = ByteBuffer.allocate(Long.BYTES).order(ByteOrder.nativeOrder());
    readBuffer.put(bytes);
    return readBuffer.getLong(0);
  }
}
