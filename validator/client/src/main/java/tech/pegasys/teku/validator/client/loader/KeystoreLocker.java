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
import java.nio.file.StandardOpenOption;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.util.config.InvalidConfigurationException;

public class KeystoreLocker {

  private static final Logger LOG = LogManager.getLogger();
  private final byte[] processPID = getProcessPID();

  public void lockKeystore(Path keystoreFile) {
    Path lockfilePath = Path.of(keystoreFile.toString() + ".lock");
    try {
      if (lockfilePath.toFile().exists()) {
        attemptReplaceStaleLockFile(lockfilePath);
      } else {
        createNewLock(lockfilePath);
      }
      lockfilePath.toFile().deleteOnExit();
    } catch (FileAlreadyExistsException e) {
      throw keystoreInUseException(keystoreFile);
    } catch (IOException e) {
      throw new UncheckedIOException("Unexpected error when trying to lock a keystore file.", e);
    }
  }

  public void attemptReplaceStaleLockFile(final Path lockfilePath) throws IOException {
    try (final FileChannel channel =
            FileChannel.open(lockfilePath, StandardOpenOption.READ, StandardOpenOption.WRITE);
        final FileLock lock = channel.tryLock()) {
      if (lock == null) {
        // File is already locked, consider it a valid lock
        throw keystoreInUseException(lockfilePath);
      }
      if (channel.size() != Long.BYTES) {
        throw keystoreInUseException(lockfilePath);
      }
      final long pidFromFile = readPidFromFile(channel);
      if (processIsAlive(pidFromFile)) {
        throw keystoreInUseException(lockfilePath);
      }

      LOG.warn("Stale PID file for process ID {} detected. Overwriting.", pidFromFile);
      channel.write(ByteBuffer.wrap(processPID), 0);
      lock.release();
    } catch (final FileNotFoundException e) {
      // File doesn't exist so try to create it new
      createNewLock(lockfilePath);
    }
  }

  private Boolean processIsAlive(final long pid) {
    return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
  }

  private InvalidConfigurationException keystoreInUseException(final Path keystoreFile) {
    return new InvalidConfigurationException("Keystore file " + keystoreFile + " already in use.");
  }

  private long readPidFromFile(final FileChannel channel) throws IOException {
    final ByteBuffer content = ByteBuffer.allocate(Long.BYTES).order(ByteOrder.nativeOrder());
    channel.read(content, 0);
    return content.getLong(0);
  }

  private void createNewLock(final Path lockfilePath) throws IOException {
    Files.write(lockfilePath, processPID, CREATE_NEW);
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
