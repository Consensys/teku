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
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import tech.pegasys.teku.storage.server.rocksdb.schema.RocksDbColumn;
import tech.pegasys.teku.storage.server.rocksdb.schema.RocksDbVariable;

public interface RocksDbAccessor extends AutoCloseable {

  <T> Optional<T> get(RocksDbVariable<T> variable);

  <K, V> Optional<V> get(RocksDbColumn<K, V> column, K key);

  <K, V> Map<K, V> getAll(RocksDbColumn<K, V> column);

  /**
   * Returns the last entry with a key less than or equal to the given key.
   *
   * @param column The column we want to query
   * @param key The requested key
   * @param <K> The key type of the column
   * @param <V> The value type of the column
   * @return The last entry with a key less than or equal to the given {@code key}
   */
  <K, V> Optional<ColumnEntry<K, V>> getFloorEntry(RocksDbColumn<K, V> column, K key);

  /**
   * Returns the first entry in the given column.
   *
   * @param column The column we want to query
   * @param <K> The key type of the column
   * @param <V> The value type of the column
   * @return The first entry in this column - the entry with the lowest key value
   */
  <K, V> Optional<ColumnEntry<K, V>> getFirstEntry(RocksDbColumn<K, V> column);

  /**
   * Returns the last entry in the given column.
   *
   * @param column The column we want to query
   * @param <K> The key type of the column
   * @param <V> The value type of the column
   * @return The last entry in this column - the entry with the greatest key value
   */
  <K, V> Optional<ColumnEntry<K, V>> getLastEntry(RocksDbColumn<K, V> column);

  /**
   * Returns the last key in the given column without loading the associated value.
   *
   * @param column The column we want to query
   * @param <K> The key type of the column
   * @param <V> The value type of the column
   * @return The last key in this column - the key with the greatest value
   */
  <K, V> Optional<K> getLastKey(RocksDbColumn<K, V> column);

  @MustBeClosed
  <K, V> Stream<ColumnEntry<K, V>> stream(RocksDbColumn<K, V> column);

  /**
   * Stream entries from a column between keys from and to fully inclusive.
   *
   * @param column the column to stream entries from
   * @param from the first key to return
   * @param to the last key to return
   * @param <K> the key type of the column
   * @param <V> the value type of the column
   * @return a Stream of entries between from and to (fully inclusive).
   */
  @MustBeClosed
  <K extends Comparable<K>, V> Stream<ColumnEntry<K, V>> stream(
      RocksDbColumn<K, V> column, K from, K to);

  RocksDbTransaction startTransaction();

  interface RocksDbTransaction extends AutoCloseable {

    <T> void put(RocksDbVariable<T> variable, T value);

    <K, V> void put(RocksDbColumn<K, V> column, K key, V value);

    <K, V> void put(RocksDbColumn<K, V> column, Map<K, V> data);

    <K, V> void delete(RocksDbColumn<K, V> column, K key);

    <T> void delete(RocksDbVariable<T> variable);

    void commit();

    void rollback();

    @Override
    void close();
  }
}
