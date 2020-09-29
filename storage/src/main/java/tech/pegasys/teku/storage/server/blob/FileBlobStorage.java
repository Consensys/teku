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

package tech.pegasys.teku.storage.server.blob;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;
import tech.pegasys.teku.storage.server.DatabaseStorageException;

public class FileBlobStorage implements BlobStorage {
  private final Path baseDir;

  public FileBlobStorage(final Path baseDir) {
    this.baseDir = baseDir;
    if (!baseDir.toFile().mkdir() && !baseDir.toFile().isDirectory()) {
      throw new DatabaseStorageException("Failed to create storage directory");
    }
  }

  @Override
  public Optional<Bytes> load(final Bytes32 id) {
    final Path blobPath = getPath(id);
    if (!blobPath.toFile().exists()) {
      return Optional.empty();
    }
    try {
      return Optional.of(Bytes.wrap(Files.readAllBytes(blobPath)));
    } catch (final FileNotFoundException e) {
      return Optional.empty();
    } catch (IOException e) {
      throw new DatabaseStorageException("Failed to load blob " + id, e);
    }
  }

  @Override
  public BlobTransaction startTransaction() {
    return new Transaction();
  }

  @Override
  public void close() {}

  private Path getPath(final Bytes32 id) {
    return baseDir.resolve(id.toUnprefixedHexString() + ".blob");
  }

  private class Transaction implements BlobTransaction {
    private final Map<Bytes32, SimpleOffsetSerializable> storedItems = new HashMap<>();
    private final Set<Bytes32> deletedItems = new HashSet<>();

    @Override
    public Bytes32 store(final Bytes32 id, final SimpleOffsetSerializable data) {
      storedItems.put(id, data);
      return id;
    }

    @Override
    public void delete(final Bytes32 id) {
      deletedItems.add(id);
      storedItems.remove(id);
    }

    @Override
    public void commit() {
      storedItems.forEach(
          (id, data) -> {
            try {
              final Path path = getPath(id);
              if (path.toFile().exists()) {
                // Already exists, so skip.
                return;
              }
              Files.write(path, serialize(data));
            } catch (IOException e) {
              // TODO: Try and delete already written items
              throw new DatabaseStorageException("Failed to store blob " + id, e);
            }
          });
      deletedItems.forEach(
          id -> {
            final File blobFile = getPath(id).toFile();
            if (!blobFile.delete() && blobFile.exists()) {
              throw new DatabaseStorageException("Failed to delete blob: " + id);
            }
          });
    }

    @Override
    public void close() {
      // Nothing written to disk yet, just ignore.
    }
  }

  private byte[] serialize(final SimpleOffsetSerializable data) {
    final long start = System.nanoTime();
    final byte[] bytes = SimpleOffsetSerializer.serialize(data).toArrayUnsafe();
    final long end = System.nanoTime();
    System.out.println("SSZ: " + TimeUnit.NANOSECONDS.toMicros(end - start));
    return bytes;
  }
}
