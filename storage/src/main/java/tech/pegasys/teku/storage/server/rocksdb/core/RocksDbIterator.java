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

package tech.pegasys.teku.storage.server.rocksdb.core;

import com.google.errorprone.annotations.MustBeClosed;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import tech.pegasys.teku.storage.server.ShuttingDownException;
import tech.pegasys.teku.storage.server.rocksdb.schema.RocksDbColumn;

class RocksDbIterator<TKey, TValue> implements Iterator<ColumnEntry<TKey, TValue>>, AutoCloseable {
  private static final Logger LOG = LogManager.getLogger();

  private final RocksDbColumn<TKey, TValue> column;
  private final RocksIterator rocksIterator;
  private final AtomicBoolean closed = new AtomicBoolean(false);
  private final Predicate<TKey> continueTest;
  private final Supplier<Boolean> isDatabaseClosed;

  private RocksDbIterator(
      final RocksDbColumn<TKey, TValue> column,
      final RocksIterator rocksIterator,
      final Predicate<TKey> continueTest,
      final Supplier<Boolean> isDatabaseClosed) {
    this.column = column;
    this.rocksIterator = rocksIterator;
    this.continueTest = continueTest;
    this.isDatabaseClosed = isDatabaseClosed;
  }

  @MustBeClosed
  public static <K, V> RocksDbIterator<K, V> create(
      final RocksDbColumn<K, V> column,
      final RocksIterator rocksIt,
      final Predicate<K> continueTest,
      final Supplier<Boolean> isDatabaseClosed) {
    return new RocksDbIterator<>(column, rocksIt, continueTest, isDatabaseClosed);
  }

  @Override
  public boolean hasNext() {
    assertOpen();
    return rocksIterator.isValid()
        && continueTest.test(column.getKeySerializer().deserialize(rocksIterator.key()));
  }

  @Override
  public ColumnEntry<TKey, TValue> next() {
    assertOpen();
    try {
      rocksIterator.status();
    } catch (final RocksDBException e) {
      LOG.error("RocksDbEntryIterator encountered a problem while iterating.", e);
    }
    if (!hasNext()) {
      throw new NoSuchElementException();
    }
    final TKey key = column.getKeySerializer().deserialize(rocksIterator.key());
    final TValue value = column.getValueSerializer().deserialize(rocksIterator.value());
    final ColumnEntry<TKey, TValue> entry = ColumnEntry.create(key, value);
    rocksIterator.next();
    return entry;
  }

  @MustBeClosed
  public Stream<ColumnEntry<TKey, TValue>> toStream() {
    assertOpen();
    final Spliterator<ColumnEntry<TKey, TValue>> split =
        Spliterators.spliteratorUnknownSize(
            this,
            Spliterator.IMMUTABLE
                | Spliterator.DISTINCT
                | Spliterator.NONNULL
                | Spliterator.ORDERED
                | Spliterator.SORTED);

    return StreamSupport.stream(split, false).onClose(this::close);
  }

  private void assertOpen() {
    if (this.isDatabaseClosed.get()) {
      throw new ShuttingDownException();
    }
    if (closed.get()) {
      throw new IllegalStateException(
          "Attempt to update a closed " + this.getClass().getSimpleName());
    }
  }

  @Override
  public void close() {
    if (closed.compareAndSet(false, true)) {
      rocksIterator.close();
    }
  }
}
