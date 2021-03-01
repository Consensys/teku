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
import static tech.pegasys.teku.storage.server.leveldb.LevelDbUtils.isFromColumn;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.iq80.leveldb.DBIterator;
import tech.pegasys.teku.storage.server.rocksdb.core.ColumnEntry;
import tech.pegasys.teku.storage.server.rocksdb.schema.RocksDbColumn;

public class LevelDbIterator<K, V> implements Iterator<ColumnEntry<K, V>> {

  private final LevelDbInstance dbInstance;
  private final DBIterator iterator;
  private final RocksDbColumn<K, V> column;
  private final byte[] lastKey;

  public LevelDbIterator(
      final LevelDbInstance dbInstance,
      final DBIterator iterator,
      final RocksDbColumn<K, V> column,
      final byte[] lastKey) {
    this.dbInstance = dbInstance;
    this.iterator = iterator;
    this.column = column;
    this.lastKey = lastKey;
  }

  @Override
  public boolean hasNext() {
    dbInstance.assertOpen();
    return iterator.hasNext() && isValidKey();
  }

  private boolean isValidKey() {
    final byte[] nextKey = iterator.peekNext().getKey();
    return isFromColumn(column, nextKey) && Arrays.compareUnsigned(nextKey, lastKey) <= 0;
  }

  @Override
  public ColumnEntry<K, V> next() {
    dbInstance.assertOpen();
    return asColumnEntry(column, iterator.next());
  }

  public Stream<ColumnEntry<K, V>> toStream() {
    final Spliterator<ColumnEntry<K, V>> split =
        Spliterators.spliteratorUnknownSize(
            this,
            Spliterator.IMMUTABLE
                | Spliterator.DISTINCT
                | Spliterator.NONNULL
                | Spliterator.ORDERED
                | Spliterator.SORTED);

    return StreamSupport.stream(split, false);
  }
}
