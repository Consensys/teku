/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.storage.server.rocksdb;

import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.MustBeClosed;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.rocksdb.AbstractRocksIterator;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.TransactionDB;
import tech.pegasys.teku.storage.server.ShuttingDownException;
import tech.pegasys.teku.storage.server.kvstore.ColumnEntry;
import tech.pegasys.teku.storage.server.kvstore.KvStoreAccessor;
import tech.pegasys.teku.storage.server.kvstore.schema.KvStoreColumn;
import tech.pegasys.teku.storage.server.kvstore.schema.KvStoreVariable;

public class RocksDbInstance implements KvStoreAccessor {

  private final TransactionDB db;
  private final ColumnFamilyHandle defaultHandle;
  private final ImmutableMap<KvStoreColumn<?, ?>, ColumnFamilyHandle> columnHandles;
  private final List<AutoCloseable> resources;
  private final Set<RocksDbTransaction> openTransactions = new HashSet<>();

  private final AtomicBoolean closed = new AtomicBoolean(false);

  RocksDbInstance(
      final TransactionDB db,
      final ColumnFamilyHandle defaultHandle,
      final ImmutableMap<KvStoreColumn<?, ?>, ColumnFamilyHandle> columnHandles,
      final List<AutoCloseable> resources) {
    this.db = db;
    this.defaultHandle = defaultHandle;
    this.columnHandles = columnHandles;
    this.resources = resources;
  }

  @Override
  public <T> Optional<T> get(KvStoreVariable<T> variable) {
    return getRaw(variable).map(data -> variable.getSerializer().deserialize(data.toArrayUnsafe()));
  }

  @Override
  public Optional<Bytes> getRaw(final KvStoreVariable<?> variable) {
    assertOpen();
    try {
      return Optional.ofNullable(db.get(defaultHandle, variable.getId().toArrayUnsafe()))
          .map(Bytes::wrap);
    } catch (RocksDBException e) {
      throw RocksDbExceptionUtil.wrapException("Failed to get value", e);
    }
  }

  @Override
  public <K, V> Optional<V> get(KvStoreColumn<K, V> column, K key) {
    assertOpen();
    final ColumnFamilyHandle handle = columnHandles.get(column);
    final byte[] keyBytes = column.getKeySerializer().serialize(key);
    try {
      return Optional.ofNullable(db.get(handle, keyBytes))
          .map(data -> column.getValueSerializer().deserialize(data));
    } catch (RocksDBException e) {
      throw RocksDbExceptionUtil.wrapException("Failed to get value", e);
    }
  }

  @Override
  public long size(final KvStoreColumn<?, ?> column) {
    assertOpen();
    try (final Stream<?> rawStream = streamRaw(column)) {
      return rawStream.count();
    }
  }

  @Override
  public <K, V> Map<K, V> getAll(KvStoreColumn<K, V> column) {
    assertOpen();
    try (final Stream<ColumnEntry<K, V>> stream = stream(column)) {
      return stream.collect(Collectors.toMap(ColumnEntry::getKey, ColumnEntry::getValue));
    }
  }

  @Override
  public <K, V> Optional<ColumnEntry<K, V>> getFloorEntry(KvStoreColumn<K, V> column, final K key) {
    assertOpen();
    final byte[] keyBytes = column.getKeySerializer().serialize(key);
    final Consumer<RocksIterator> setupIterator = it -> it.seekForPrev(keyBytes);
    try (final Stream<ColumnEntry<K, V>> stream = createStream(column, setupIterator)) {
      return stream.findFirst();
    }
  }

  @Override
  public <K, V> Optional<ColumnEntry<K, V>> getFirstEntry(final KvStoreColumn<K, V> column) {
    assertOpen();
    try (final Stream<ColumnEntry<K, V>> stream =
        createStream(column, AbstractRocksIterator::seekToFirst)) {
      return stream.findFirst();
    }
  }

  @Override
  public <K, V> Optional<K> getLastKey(final KvStoreColumn<K, V> column) {
    assertOpen();
    final ColumnFamilyHandle handle = columnHandles.get(column);
    try (final RocksIterator rocksDbIterator = db.newIterator(handle)) {
      rocksDbIterator.seekToLast();
      return rocksDbIterator.isValid()
          ? Optional.of(column.getKeySerializer().deserialize(rocksDbIterator.key()))
          : Optional.empty();
    }
  }

  @Override
  @MustBeClosed
  public <K, V> Stream<ColumnEntry<K, V>> stream(KvStoreColumn<K, V> column) {
    assertOpen();
    return createStream(column, RocksIterator::seekToFirst);
  }

  @Override
  @MustBeClosed
  public <K, V> Stream<K> streamKeys(KvStoreColumn<K, V> column) {
    assertOpen();
    return createKeyStream(column, RocksIterator::seekToFirst);
  }

  @Override
  @MustBeClosed
  public Stream<ColumnEntry<Bytes, Bytes>> streamRaw(final KvStoreColumn<?, ?> column) {
    assertOpen();
    return createStreamRaw(column, RocksIterator::seekToFirst, key -> true)
        .map(entry -> ColumnEntry.create(Bytes.wrap(entry.getKey()), Bytes.wrap(entry.getValue())));
  }

  @Override
  @MustBeClosed
  public Stream<Bytes> streamKeysRaw(final KvStoreColumn<?, ?> column) {
    return createStreamKeyRaw(column, RocksIterator::seekToFirst, key -> true).map(Bytes::wrap);
  }

  @Override
  public <K, V> Optional<Bytes> getRaw(final KvStoreColumn<K, V> column, final K key) {
    assertOpen();
    final ColumnFamilyHandle handle = columnHandles.get(column);
    final byte[] keyBytes = column.getKeySerializer().serialize(key);
    try {
      return Optional.ofNullable(db.get(handle, keyBytes)).map(Bytes::wrap);
    } catch (RocksDBException e) {
      throw RocksDbExceptionUtil.wrapException("Failed to get value", e);
    }
  }

  @Override
  @MustBeClosed
  public <K extends Comparable<K>, V> Stream<ColumnEntry<K, V>> stream(
      final KvStoreColumn<K, V> column, final K from, final K to) {
    assertOpen();
    return createStream(
        column,
        iter -> iter.seek(column.getKeySerializer().serialize(from)),
        key -> key.compareTo(to) <= 0);
  }

  @Override
  @MustBeClosed
  public <K extends Comparable<K>, V> Stream<K> streamKeys(
      KvStoreColumn<K, V> column, K from, K to) {
    assertOpen();
    return createKeyStream(
        column,
        iter -> iter.seek(column.getKeySerializer().serialize(from)),
        key -> key.compareTo(to) <= 0);
  }

  @Override
  @MustBeClosed
  public synchronized KvStoreTransaction startTransaction() {
    assertOpen();
    RocksDbTransaction tx =
        new RocksDbTransaction(db, defaultHandle, columnHandles, openTransactions::remove);
    openTransactions.add(tx);
    return tx;
  }

  @MustBeClosed
  private <K, V> Stream<ColumnEntry<K, V>> createStream(
      KvStoreColumn<K, V> column, Consumer<RocksIterator> setupIterator) {
    return createStream(column, setupIterator, key -> true);
  }

  @MustBeClosed
  private <K, V> Stream<K> createKeyStream(
      KvStoreColumn<K, V> column, Consumer<RocksIterator> setupIterator) {
    return createKeyStream(column, setupIterator, key -> true);
  }

  @MustBeClosed
  private <K, V> Stream<ColumnEntry<K, V>> createStream(
      KvStoreColumn<K, V> column,
      Consumer<RocksIterator> setupIterator,
      Predicate<K> continueTest) {

    return createStreamRaw(column, setupIterator, continueTest)
        .map(
            entry ->
                ColumnEntry.create(
                    column.getKeySerializer().deserialize(entry.getKey()),
                    column.getValueSerializer().deserialize(entry.getValue())));
  }

  @MustBeClosed
  private <K, V> Stream<K> createKeyStream(
      KvStoreColumn<K, V> column,
      Consumer<RocksIterator> setupIterator,
      Predicate<K> continueTest) {

    return createStreamKeyRaw(column, setupIterator, continueTest)
        .map(entry -> column.getKeySerializer().deserialize(entry));
  }

  @SuppressWarnings("MustBeClosedChecker")
  @MustBeClosed
  private <K, V> Stream<ColumnEntry<byte[], byte[]>> createStreamRaw(
      KvStoreColumn<K, V> column,
      Consumer<RocksIterator> setupIterator,
      Predicate<K> continueTest) {
    final ColumnFamilyHandle handle = columnHandles.get(column);
    final RocksIterator rocksDbIterator = db.newIterator(handle);
    setupIterator.accept(rocksDbIterator);
    return RocksDbIterator.create(column, rocksDbIterator, continueTest, closed::get).toStream();
  }

  @SuppressWarnings("MustBeClosedChecker")
  @MustBeClosed
  private <K, V> Stream<byte[]> createStreamKeyRaw(
      KvStoreColumn<K, V> column,
      Consumer<RocksIterator> setupIterator,
      Predicate<K> continueTest) {
    final ColumnFamilyHandle handle = columnHandles.get(column);
    final RocksIterator rocksDbIterator = db.newIterator(handle);
    setupIterator.accept(rocksDbIterator);
    return RocksDbKeyIterator.create(column, rocksDbIterator, continueTest, closed::get).toStream();
  }

  @Override
  public synchronized void close() throws Exception {
    if (closed.compareAndSet(false, true)) {
      for (RocksDbTransaction openTransaction : openTransactions) {
        openTransaction.closeViaDatabase();
      }
      db.syncWal();
      for (final AutoCloseable resource : resources) {
        resource.close();
      }
    }
  }

  private void assertOpen() {
    if (closed.get()) {
      throw new ShuttingDownException();
    }
  }
}
