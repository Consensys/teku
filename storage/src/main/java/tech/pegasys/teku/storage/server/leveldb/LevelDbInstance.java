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
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBIterator;
import org.iq80.leveldb.WriteBatch;
import tech.pegasys.teku.storage.server.rocksdb.core.ColumnEntry;
import tech.pegasys.teku.storage.server.rocksdb.core.RocksDbAccessor;
import tech.pegasys.teku.storage.server.rocksdb.schema.RocksDbColumn;
import tech.pegasys.teku.storage.server.rocksdb.schema.RocksDbVariable;

public class LevelDbInstance implements RocksDbAccessor {

  private final DB db;

  public LevelDbInstance(final DB db) {
    this.db = db;
  }

  @Override
  public <T> Optional<T> get(final RocksDbVariable<T> variable) {
    return Optional.ofNullable(db.get(getVariableKey(variable)))
        .map(variable.getSerializer()::deserialize);
  }

  @Override
  public <K, V> Optional<V> get(final RocksDbColumn<K, V> column, final K key) {
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
          if (iterator.hasNext()) {
            final Map.Entry<byte[], byte[]> next = iterator.peekNext();
            if (Arrays.equals(next.getKey(), matchingKey)) {
              return Optional.of(
                  ColumnEntry.create(
                      key, column.getValueSerializer().deserialize(next.getValue())));
            }
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
    final DBIterator iterator = db.iterator();
    iterator.seek(fromBytes);
    return new LevelDbIterator<>(iterator, column, toBytes).toStream();
  }

  @Override
  public RocksDbTransaction startTransaction() {
    final WriteBatch writeBatch = db.createWriteBatch();
    return new LevelDbTransaction(db, writeBatch);
  }

  @Override
  public void close() throws Exception {
    db.close();
  }

  private <T> T withIterator(final Function<DBIterator, T> action) {
    try (final DBIterator iterator = db.iterator()) {
      return action.apply(iterator);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
