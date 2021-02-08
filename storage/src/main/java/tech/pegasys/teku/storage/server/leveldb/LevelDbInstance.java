/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.storage.server.leveldb;

import static tech.pegasys.teku.storage.server.leveldb.LevelDbUtils.asColumnEntry;
import static tech.pegasys.teku.storage.server.leveldb.LevelDbUtils.asOptionalColumnEntry;
import static tech.pegasys.teku.storage.server.leveldb.LevelDbUtils.deserializeKey;
import static tech.pegasys.teku.storage.server.leveldb.LevelDbUtils.getColumnKey;
import static tech.pegasys.teku.storage.server.leveldb.LevelDbUtils.getKeyAfterColumn;
import static tech.pegasys.teku.storage.server.leveldb.LevelDbUtils.getVariableKey;
import static tech.pegasys.teku.storage.server.leveldb.LevelDbUtils.isFromColumn;

import com.google.errorprone.annotations.MustBeClosed;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBException;
import org.iq80.leveldb.DBIterator;
import org.iq80.leveldb.ReadOptions;
import org.iq80.leveldb.WriteBatch;
import tech.pegasys.teku.storage.server.DatabaseStorageException;
import tech.pegasys.teku.storage.server.ShuttingDownException;
import tech.pegasys.teku.storage.server.rocksdb.core.ColumnEntry;
import tech.pegasys.teku.storage.server.rocksdb.core.RocksDbAccessor;
import tech.pegasys.teku.storage.server.rocksdb.schema.RocksDbColumn;
import tech.pegasys.teku.storage.server.rocksdb.schema.RocksDbVariable;

public class LevelDbInstance implements RocksDbAccessor {
  private static final Logger LOG = LogManager.getLogger();

  private final Set<LevelDbTransaction> openTransactions =
      Collections.synchronizedSet(new HashSet<>());
  private final Set<DBIterator> openIterators = Collections.synchronizedSet(new HashSet<>());
  private final AtomicBoolean closed = new AtomicBoolean(false);
  private final DB db;

  public LevelDbInstance(final DB db) {
    this.db = db;
  }

  @Override
  public <T> Optional<T> get(final RocksDbVariable<T> variable) {
    assertOpen();
    return Optional.ofNullable(db.get(getVariableKey(variable)))
        .map(variable.getSerializer()::deserialize);
  }

  @Override
  public <K, V> Optional<V> get(final RocksDbColumn<K, V> column, final K key) {
    assertOpen();
    return Optional.ofNullable(db.get(getColumnKey(column, key)))
        .map(column.getValueSerializer()::deserialize);
  }

  @Override
  public <K, V> Map<K, V> getAll(final RocksDbColumn<K, V> column) {
    return withIterator(
        iterator -> {
          iterator.seek(column.getId().toArrayUnsafe());
          final Map<K, V> values = new HashMap<>();
          while (iterator.hasNext()) {
            final Map.Entry<byte[], byte[]> entry = iterator.next();
            if (!isFromColumn(column, entry.getKey())) {
              break;
            }
            values.put(
                deserializeKey(column, entry.getKey()),
                column.getValueSerializer().deserialize(entry.getValue()));
          }
          return values;
        });
  }

  @Override
  public <K, V> Optional<ColumnEntry<K, V>> getFloorEntry(
      final RocksDbColumn<K, V> column, final K key) {
    return withIterator(
        iterator -> {
          final byte[] matchingKey = getColumnKey(column, key);
          iterator.seek(matchingKey);
          if (!iterator.hasNext()) {
            // We seeked past the last item in the database
            iterator.seekToLast();
            if (!iterator.hasNext()) {
              // Empty database
              return Optional.empty();
            }
            final Map.Entry<byte[], byte[]> entry = iterator.peekNext();
            if (isFromColumn(column, entry.getKey())) {
              return asOptionalColumnEntry(column, entry);
            }
            return Optional.empty();
          }

          // Check if an exact match was found
          final Map.Entry<byte[], byte[]> next = iterator.peekNext();
          if (Arrays.equals(next.getKey(), matchingKey)) {
            return Optional.of(
                ColumnEntry.create(key, column.getValueSerializer().deserialize(next.getValue())));
          }

          if (iterator.hasPrev()) {
            final Map.Entry<byte[], byte[]> prev = iterator.peekPrev();
            if (isFromColumn(column, prev.getKey())) {
              return asOptionalColumnEntry(column, prev);
            }
          }
          return Optional.empty();
        });
  }

  @Override
  public <K, V> Optional<ColumnEntry<K, V>> getFirstEntry(final RocksDbColumn<K, V> column) {
    return withIterator(
        iterator -> {
          iterator.seek(column.getId().toArrayUnsafe());
          if (iterator.hasNext()) {
            return Optional.of(iterator.peekNext())
                .filter(entry -> isFromColumn(column, entry.getKey()))
                .map(entry -> asColumnEntry(column, entry));
          }
          return Optional.empty();
        });
  }

  @Override
  public <K, V> Optional<ColumnEntry<K, V>> getLastEntry(final RocksDbColumn<K, V> column) {
    return withIterator(
        iterator -> {
          final byte[] keyAfterColumn = getKeyAfterColumn(column);
          iterator.seek(keyAfterColumn);
          if (!iterator.hasNext()) {
            // We seeked past the end of the database, go back a step
            iterator.seekToLast();
          }
          if (iterator.hasPrev()) {
            return Optional.of(iterator.peekPrev())
                .filter(entry -> isFromColumn(column, entry.getKey()))
                .map(entry -> asColumnEntry(column, entry));
          }
          return Optional.empty();
        });
  }

  @Override
  public <K, V> Optional<K> getLastKey(final RocksDbColumn<K, V> column) {
    return withIterator(
        iterator -> {
          final byte[] keyAfterColumn = getKeyAfterColumn(column);
          iterator.seek(keyAfterColumn);
          if (!iterator.hasNext()) {
            // We seeked past the end of the database, go back a step
            iterator.seekToLast();
          }
          if (iterator.hasPrev()) {
            return Optional.of(iterator.peekPrev())
                .filter(entry -> isFromColumn(column, entry.getKey()))
                .map(entry -> deserializeKey(column, entry.getKey()));
          }
          return Optional.empty();
        });
  }

  @Override
  @MustBeClosed
  public <K, V> Stream<ColumnEntry<K, V>> stream(final RocksDbColumn<K, V> column) {
    return stream(column, column.getId().toArrayUnsafe(), getKeyAfterColumn(column));
  }

  @Override
  @MustBeClosed
  public <K extends Comparable<K>, V> Stream<ColumnEntry<K, V>> stream(
      final RocksDbColumn<K, V> column, final K from, final K to) {
    final byte[] fromBytes = getColumnKey(column, from);
    final byte[] toBytes = getColumnKey(column, to);
    return stream(column, fromBytes, toBytes);
  }

  @MustBeClosed
  private <K, V> Stream<ColumnEntry<K, V>> stream(
      final RocksDbColumn<K, V> column, final byte[] fromBytes, final byte[] toBytes) {
    assertOpen();
    final DBIterator iterator = createIterator();
    iterator.seek(fromBytes);
    return new LevelDbIterator<>(this, iterator, column, toBytes)
        .toStream()
        .onClose(() -> closeIterator(iterator));
  }

  @Override
  public synchronized RocksDbTransaction startTransaction() {
    assertOpen();
    final WriteBatch writeBatch = db.createWriteBatch();
    final LevelDbTransaction transaction = new LevelDbTransaction(this, db, writeBatch);
    openTransactions.add(transaction);
    return transaction;
  }

  @Override
  public synchronized void close() throws Exception {
    if (!closed.compareAndSet(false, true)) {
      return;
    }
    new ArrayList<>(openIterators).forEach(this::closeIterator);
    new ArrayList<>(openTransactions).forEach(LevelDbTransaction::close);
    db.close();
  }

  private synchronized <T> T withIterator(final Function<DBIterator, T> action) {
    assertOpen();
    final DBIterator iterator = createIterator();
    try {
      return action.apply(iterator);
    } catch (final DBException e) {
      throw DatabaseStorageException.unrecoverable("Failed to create iterator", e);
    } finally {
      closeIterator(iterator);
    }
  }

  private synchronized void closeIterator(final DBIterator iterator) {
    if (!openIterators.remove(iterator)) {
      return;
    }
    try {
      iterator.close();
    } catch (final IOException e) {
      LOG.error("Failed to close leveldb iterator", e);
    }
  }

  void onTransactionClosed(final LevelDbTransaction transaction) {
    openTransactions.remove(transaction);
  }

  private DBIterator createIterator() {
    final DBIterator iterator = db.iterator(new ReadOptions().fillCache(false));
    openIterators.add(iterator);
    return iterator;
  }

  void assertOpen() {
    if (closed.get()) {
      throw new ShuttingDownException();
    }
  }
}
