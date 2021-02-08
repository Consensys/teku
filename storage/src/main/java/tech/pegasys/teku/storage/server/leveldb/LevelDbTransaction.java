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

import static tech.pegasys.teku.storage.server.leveldb.LevelDbUtils.getColumnKey;
import static tech.pegasys.teku.storage.server.leveldb.LevelDbUtils.getVariableKey;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.WriteBatch;
import tech.pegasys.teku.storage.server.ShuttingDownException;
import tech.pegasys.teku.storage.server.rocksdb.core.RocksDbAccessor.RocksDbTransaction;
import tech.pegasys.teku.storage.server.rocksdb.schema.RocksDbColumn;
import tech.pegasys.teku.storage.server.rocksdb.schema.RocksDbVariable;

public class LevelDbTransaction implements RocksDbTransaction {

  private final AtomicBoolean closed = new AtomicBoolean(false);

  private final LevelDbInstance dbInstance;
  private final DB db;
  private final WriteBatch writeBatch;

  public LevelDbTransaction(
      final LevelDbInstance dbInstance, final DB db, final WriteBatch writeBatch) {
    this.dbInstance = dbInstance;
    this.db = db;
    this.writeBatch = writeBatch;
  }

  @Override
  public <T> void put(final RocksDbVariable<T> variable, final T value) {
    assertOpen();
    writeBatch.put(getVariableKey(variable), variable.getSerializer().serialize(value));
  }

  @Override
  public <K, V> void put(final RocksDbColumn<K, V> column, final K key, final V value) {
    assertOpen();
    writeBatch.put(getColumnKey(column, key), serializeValue(column, value));
  }

  @Override
  public <K, V> void put(final RocksDbColumn<K, V> column, final Map<K, V> data) {
    assertOpen();
    data.forEach(
        (key, value) -> writeBatch.put(getColumnKey(column, key), serializeValue(column, value)));
  }

  @Override
  public <K, V> void delete(final RocksDbColumn<K, V> column, final K key) {
    assertOpen();
    writeBatch.delete(getColumnKey(column, key));
  }

  @Override
  public <T> void delete(final RocksDbVariable<T> variable) {
    assertOpen();
    writeBatch.delete(getVariableKey(variable));
  }

  @Override
  public void commit() {
    assertOpen();
    try {
      db.write(writeBatch);
    } finally {
      close();
    }
  }

  @Override
  public void rollback() {
    assertOpen();
    close();
  }

  @Override
  public void close() {
    if (!closed.compareAndSet(false, true)) {
      return;
    }
    try {
      writeBatch.close();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } finally {
      dbInstance.onTransactionClosed(this);
    }
  }

  private void assertOpen() {
    if (closed.get()) {
      throw new ShuttingDownException();
    }
    dbInstance.assertOpen();
  }

  private <K, V> byte[] serializeValue(final RocksDbColumn<K, V> column, final V value) {
    return column.getValueSerializer().serialize(value);
  }
}
