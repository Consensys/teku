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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.storage.server.ShuttingDownException;
import tech.pegasys.teku.storage.server.rocksdb.schema.RocksDbColumn;
import tech.pegasys.teku.storage.server.rocksdb.schema.RocksDbVariable;

public class MockRocksDbInstance implements RocksDbAccessor {
  private final Set<RocksDbColumn<?, ?>> columns;
  private final Set<RocksDbVariable<?>> variables;

  private final Map<RocksDbColumn<?, ?>, NavigableMap<Bytes, Bytes>> columnData;
  private final Map<RocksDbVariable<?>, Bytes> variableData;

  private AtomicBoolean closed = new AtomicBoolean(false);

  public MockRocksDbInstance(
      Collection<RocksDbColumn<?, ?>> columns,
      Collection<RocksDbVariable<?>> variables,
      final Map<RocksDbColumn<?, ?>, NavigableMap<Bytes, Bytes>> columnData,
      final Map<RocksDbVariable<?>, Bytes> variableData) {
    this.columns = new HashSet<>(columns);
    this.variables = new HashSet<>(variables);
    this.columnData = columnData;
    this.variableData = variableData;
  }

  public MockRocksDbInstance reopen() {
    if (closed.get()) {
      return new MockRocksDbInstance(columns, variables, columnData, variableData);
    } else {
      return this;
    }
  }

  public static MockRocksDbInstance createEmpty(
      final Collection<RocksDbColumn<?, ?>> columns, List<RocksDbVariable<?>> variables) {
    checkArgument(columns.size() > 0, "No columns attached to schema");

    final Map<RocksDbColumn<?, ?>, NavigableMap<Bytes, Bytes>> columnData =
        columns.stream()
            .collect(Collectors.toConcurrentMap(col -> col, __ -> new ConcurrentSkipListMap<>()));
    final Map<RocksDbVariable<?>, Bytes> variableData = new ConcurrentHashMap<>();
    return new MockRocksDbInstance(columns, variables, columnData, variableData);
  }

  @Override
  public <T> Optional<T> get(final RocksDbVariable<T> variable) {
    assertOpen();
    assertValidVariable(variable);
    return Optional.ofNullable(variableData.get(variable))
        .map(Bytes::toArrayUnsafe)
        .map(variable.getSerializer()::deserialize);
  }

  @Override
  public <K, V> Optional<V> get(final RocksDbColumn<K, V> column, final K key) {
    assertOpen();
    assertValidColumn(column);
    final Bytes keyBytes = keyToBytes(column, key);
    final Bytes valueBytes = columnData.get(column).get(keyBytes);
    return columnValue(column, valueBytes);
  }

  @Override
  public <K, V> Map<K, V> getAll(final RocksDbColumn<K, V> column) {
    assertOpen();
    assertValidColumn(column);
    return stream(column).collect(Collectors.toMap(ColumnEntry::getKey, ColumnEntry::getValue));
  }

  @Override
  public <K, V> Optional<ColumnEntry<K, V>> getFloorEntry(
      final RocksDbColumn<K, V> column, final K key) {
    assertOpen();
    assertValidColumn(column);
    final Bytes keyBytes = keyToBytes(column, key);
    return Optional.ofNullable(columnData.get(column).floorEntry(keyBytes))
        .map(e -> columnEntry(column, e));
  }

  @Override
  public <K, V> Optional<ColumnEntry<K, V>> getFirstEntry(final RocksDbColumn<K, V> column) {
    assertOpen();
    assertValidColumn(column);
    return Optional.ofNullable(columnData.get(column).firstEntry())
        .map(e -> columnEntry(column, e));
  }

  @Override
  public <K, V> Optional<ColumnEntry<K, V>> getLastEntry(final RocksDbColumn<K, V> column) {
    assertOpen();
    assertValidColumn(column);
    return Optional.ofNullable(columnData.get(column).lastEntry()).map(e -> columnEntry(column, e));
  }

  @Override
  public <K, V> Optional<K> getLastKey(final RocksDbColumn<K, V> column) {
    assertOpen();
    assertValidColumn(column);
    final NavigableMap<Bytes, Bytes> values = columnData.get(column);
    return values.isEmpty()
        ? Optional.empty()
        : Optional.of(values.lastKey()).map(data -> columnKey(column, data));
  }

  @Override
  public <K, V> Stream<ColumnEntry<K, V>> stream(final RocksDbColumn<K, V> column) {
    assertOpen();
    assertValidColumn(column);
    return columnData.get(column).entrySet().stream()
        .peek(value -> assertOpen())
        .map(e -> columnEntry(column, e));
  }

  @Override
  public <K extends Comparable<K>, V> Stream<ColumnEntry<K, V>> stream(
      final RocksDbColumn<K, V> column, final K from, final K to) {
    assertOpen();
    return columnData.get(column)
        .subMap(keyToBytes(column, from), true, keyToBytes(column, to), true).entrySet().stream()
        .peek(value -> assertOpen())
        .map(e -> columnEntry(column, e));
  }

  @Override
  public RocksDbTransaction startTransaction() {
    assertOpen();
    return new MockRocksDbTransaction(this);
  }

  private <K, V> Optional<V> columnValue(final RocksDbColumn<K, V> column, final Bytes bytes) {
    return Optional.ofNullable(bytes)
        .map(Bytes::toArrayUnsafe)
        .map(column.getValueSerializer()::deserialize);
  }

  private <K, V> K columnKey(final RocksDbColumn<K, V> column, final Bytes bytes) {
    return column.getKeySerializer().deserialize(bytes.toArrayUnsafe());
  }

  private <K, V> ColumnEntry<K, V> columnEntry(
      final RocksDbColumn<K, V> column, final Map.Entry<Bytes, Bytes> entry) {
    final K key = columnKey(column, entry.getKey());
    final V value = columnValue(column, entry.getValue()).get();
    return ColumnEntry.create(key, value);
  }

  private <K, V> Bytes keyToBytes(final RocksDbColumn<K, V> column, K key) {
    return Bytes.wrap(column.getKeySerializer().serialize(key));
  }

  private <K, V> Bytes valueToBytes(final RocksDbColumn<K, V> column, V value) {
    return Bytes.wrap(column.getValueSerializer().serialize(value));
  }

  @Override
  public void close() {
    closed.set(true);
  }

  private void assertValidVariable(RocksDbVariable<?> variable) {
    checkArgument(variables.contains(variable), "Unknown RocksDbVariable supplied");
  }

  private void assertValidColumn(RocksDbColumn<?, ?> column) {
    checkArgument(columns.contains(column), "Unknown RocksDbColumn supplied");
  }

  private void assertOpen() {
    if (closed.get()) {
      throw new ShuttingDownException();
    }
  }

  private static class MockRocksDbTransaction implements RocksDbTransaction {

    private final MockRocksDbInstance dbInstance;
    private final Map<RocksDbColumn<?, ?>, Map<Bytes, Bytes>> columnUpdates = new HashMap<>();
    private final Map<RocksDbColumn<?, ?>, Set<Bytes>> deletedColumnKeys = new HashMap<>();
    private final Map<RocksDbVariable<?>, Optional<Bytes>> variableUpdates = new HashMap<>();
    private boolean closed = false;

    public MockRocksDbTransaction(final MockRocksDbInstance mockRocksDbInstance) {
      this.dbInstance = mockRocksDbInstance;
    }

    @Override
    public <T> void put(final RocksDbVariable<T> variable, final T value) {
      assertOpen();
      dbInstance.assertValidVariable(variable);
      final Bytes valueBytes = Bytes.wrap(variable.getSerializer().serialize(value));
      variableUpdates.put(variable, Optional.of(valueBytes));
    }

    @Override
    public <K, V> void put(final RocksDbColumn<K, V> column, final K key, final V value) {
      assertOpen();
      dbInstance.assertValidColumn(column);
      final Map<Bytes, Bytes> updates =
          columnUpdates.computeIfAbsent(column, (col) -> new HashMap<>());
      final Bytes keyBytes = dbInstance.keyToBytes(column, key);
      final Bytes valueBytes = dbInstance.valueToBytes(column, value);
      updates.put(keyBytes, valueBytes);
    }

    @Override
    public <K, V> void put(final RocksDbColumn<K, V> column, final Map<K, V> data) {
      assertOpen();
      dbInstance.assertValidColumn(column);
      data.forEach((key, value) -> put(column, key, value));
    }

    @Override
    public <K, V> void delete(final RocksDbColumn<K, V> column, final K key) {
      assertOpen();
      dbInstance.assertValidColumn(column);
      final Bytes keyBytes = dbInstance.keyToBytes(column, key);
      final Map<Bytes, Bytes> updates = columnUpdates.get(column);
      if (updates != null) {
        updates.remove(keyBytes);
      }

      final Set<Bytes> deletedKeys =
          deletedColumnKeys.computeIfAbsent(column, __ -> new HashSet<>());
      deletedKeys.add(keyBytes);
    }

    @Override
    public <T> void delete(RocksDbVariable<T> variable) {
      assertOpen();
      dbInstance.assertValidVariable(variable);
      variableUpdates.put(variable, Optional.empty());
    }

    @Override
    public void commit() {
      assertOpen();
      dbInstance.assertOpen();
      variableUpdates.forEach(
          (key, value) -> {
            if (value.isPresent()) {
              dbInstance.variableData.put(key, value.get());
            } else {
              dbInstance.variableData.remove(key);
            }
          });
      columnUpdates.forEach(
          (col, updates) -> {
            final NavigableMap<Bytes, Bytes> targetColumn = dbInstance.columnData.get(col);
            updates.forEach(targetColumn::put);
          });
      deletedColumnKeys.forEach(
          (col, deletedKeys) -> {
            final NavigableMap<Bytes, Bytes> targetColumn = dbInstance.columnData.get(col);
            deletedKeys.forEach(targetColumn::remove);
          });
    }

    private void assertOpen() {
      checkState(!closed, "Attempt to modify a closed transaction");
      if (dbInstance.closed.get()) {
        throw new ShuttingDownException();
      }
    }

    @Override
    public void rollback() {
      closed = true;
      variableUpdates.clear();
      columnUpdates.clear();
      deletedColumnKeys.clear();
    }

    @Override
    public void close() {
      closed = true;
    }
  }
}
