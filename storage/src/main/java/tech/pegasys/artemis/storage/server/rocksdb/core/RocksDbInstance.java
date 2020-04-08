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

package tech.pegasys.artemis.storage.server.rocksdb.core;

import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.rocksdb.AbstractRocksIterator;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.TransactionDB;
import org.rocksdb.WriteOptions;
import tech.pegasys.artemis.storage.server.DatabaseStorageException;
import tech.pegasys.artemis.storage.server.rocksdb.schema.RocksDbColumn;
import tech.pegasys.artemis.storage.server.rocksdb.schema.RocksDbVariable;

public class RocksDbInstance implements AutoCloseable {

  private final TransactionDB db;
  private final ColumnFamilyHandle defaultHandle;
  private final ImmutableMap<RocksDbColumn<?, ?>, ColumnFamilyHandle> columnHandles;
  private final List<AutoCloseable> resources;

  private final AtomicBoolean closed = new AtomicBoolean(false);

  RocksDbInstance(
      final TransactionDB db,
      final ColumnFamilyHandle defaultHandle,
      final ImmutableMap<RocksDbColumn<?, ?>, ColumnFamilyHandle> columnHandles,
      final List<AutoCloseable> resources) {
    this.db = db;
    this.defaultHandle = defaultHandle;
    this.columnHandles = columnHandles;
    this.resources = resources;
  }

  public <T> Optional<T> get(RocksDbVariable<T> variableType) {
    final ColumnFamilyHandle handle = defaultHandle;
    try {
      return Optional.ofNullable(db.get(handle, variableType.getId().toArrayUnsafe()))
          .map(data -> variableType.getSerializer().deserialize(data));
    } catch (RocksDBException e) {
      throw new DatabaseStorageException("Failed to get value", e);
    }
  }

  public <T> T getOrThrow(RocksDbVariable<T> variableType) {
    return get(variableType).orElseThrow();
  }

  public <K, V> Optional<V> get(RocksDbColumn<K, V> column, K key) {
    final ColumnFamilyHandle handle = columnHandles.get(column);
    final byte[] keyBytes = column.getKeySerializer().serialize(key);
    try {
      return Optional.ofNullable(db.get(handle, keyBytes))
          .map(data -> column.getValueSerializer().deserialize(data));
    } catch (RocksDBException e) {
      throw new DatabaseStorageException("Failed to get value", e);
    }
  }

  public <K, V> Map<K, V> getAll(RocksDbColumn<K, V> column) {
    return stream(column).collect(Collectors.toMap(ColumnEntry::getKey, ColumnEntry::getValue));
  }

  /**
   * @param column The column we want to query
   * @param key The requested key
   * @param <K> The key type of the column
   * @param <V> The value type of the column
   * @return The last entry with a key less than or equal to the given {@code key}
   */
  public <K, V> Optional<ColumnEntry<K, V>> getFloorEntry(RocksDbColumn<K, V> column, final K key) {
    final byte[] keyBytes = column.getKeySerializer().serialize(key);
    final Consumer<RocksIterator> setupIterator = it -> it.seekForPrev(keyBytes);
    try (final Stream<ColumnEntry<K, V>> stream = stream(column, setupIterator)) {
      return stream.findFirst();
    }
  }

  /**
   * @param column The column we want to query
   * @param <K> The key type of the column
   * @param <V> The value type of the column
   * @return The last entry in this column - the entry with the greatest key value
   */
  public <K, V> Optional<ColumnEntry<K, V>> getLastEntry(RocksDbColumn<K, V> column) {
    try (final Stream<ColumnEntry<K, V>> stream =
        stream(column, AbstractRocksIterator::seekToLast)) {
      return stream.findFirst();
    }
  }

  public <K, V> Stream<ColumnEntry<K, V>> stream(RocksDbColumn<K, V> column) {
    return stream(column, RocksIterator::seekToFirst);
  }

  public Transaction startTransaction() {
    return new Transaction(db, defaultHandle, columnHandles);
  }

  private <K, V> Stream<ColumnEntry<K, V>> stream(
      RocksDbColumn<K, V> column, Consumer<RocksIterator> setupIterator) {
    final ColumnFamilyHandle handle = columnHandles.get(column);
    final RocksIterator rocksDbIterator = db.newIterator(handle);
    setupIterator.accept(rocksDbIterator);
    return RocksDbIterator.create(column, rocksDbIterator).toStream();
  }

  @Override
  public void close() throws Exception {
    if (closed.compareAndSet(false, true)) {
      for (final AutoCloseable resource : resources) {
        resource.close();
      }
    }
  }

  public static class Transaction implements AutoCloseable {
    private final ColumnFamilyHandle defaultHandle;
    private final ImmutableMap<RocksDbColumn<?, ?>, ColumnFamilyHandle> columnHandles;
    private final org.rocksdb.Transaction rocksDbTx;
    private final WriteOptions writeOptions;

    private final AtomicBoolean closed = new AtomicBoolean(false);

    private Transaction(
        final TransactionDB db,
        final ColumnFamilyHandle defaultHandle,
        final ImmutableMap<RocksDbColumn<?, ?>, ColumnFamilyHandle> columnHandles) {
      this.defaultHandle = defaultHandle;
      this.columnHandles = columnHandles;
      this.writeOptions = new WriteOptions();
      this.rocksDbTx = db.beginTransaction(writeOptions);
    }

    public <T> void put(RocksDbVariable<T> variableType, T value) {
      assertOpen();
      final byte[] serialized = variableType.getSerializer().serialize(value);
      try {
        rocksDbTx.put(defaultHandle, variableType.getId().toArrayUnsafe(), serialized);
      } catch (RocksDBException e) {
        throw new DatabaseStorageException("Failed to put variable", e);
      }
    }

    public <K, V> void put(RocksDbColumn<K, V> column, K key, V value) {
      assertOpen();
      final byte[] keyBytes = column.getKeySerializer().serialize(key);
      final byte[] valueBytes = column.getValueSerializer().serialize(value);
      final ColumnFamilyHandle handle = columnHandles.get(column);
      try {
        rocksDbTx.put(handle, keyBytes, valueBytes);
      } catch (RocksDBException e) {
        throw new DatabaseStorageException("Failed to put column data", e);
      }
    }

    public <K, V> void put(RocksDbColumn<K, V> column, Map<K, V> data) {
      assertOpen();
      final ColumnFamilyHandle handle = columnHandles.get(column);
      for (Entry<K, V> kvEntry : data.entrySet()) {
        final byte[] key = column.getKeySerializer().serialize(kvEntry.getKey());
        final byte[] value = column.getValueSerializer().serialize(kvEntry.getValue());
        try {
          rocksDbTx.put(handle, key, value);
        } catch (RocksDBException e) {
          throw new DatabaseStorageException("Failed to put column data", e);
        }
      }
    }

    public <K, V> void delete(RocksDbColumn<K, V> column, K key) {
      final ColumnFamilyHandle handle = columnHandles.get(column);
      try {
        rocksDbTx.delete(handle, column.getKeySerializer().serialize(key));
      } catch (RocksDBException e) {
        throw new DatabaseStorageException("Failed to delete key", e);
      }
    }

    public void commit() {
      assertOpen();
      try {
        this.rocksDbTx.commit();
        close();
      } catch (RocksDBException e) {
        rollback();
        throw new DatabaseStorageException("Failed to commit transaction", e);
      }
    }

    public void rollback() {
      assertOpen();
      try {
        this.rocksDbTx.rollback();
        close();
      } catch (RocksDBException e) {
        throw new DatabaseStorageException("Failed to rollback transaction", e);
      }
    }

    private void assertOpen() {
      if (closed.get()) {
        throw new IllegalStateException("Attempt to update a closed transaction");
      }
    }

    @Override
    public void close() {
      if (closed.compareAndSet(false, true)) {
        writeOptions.close();
        rocksDbTx.close();
      }
    }
  }
}
