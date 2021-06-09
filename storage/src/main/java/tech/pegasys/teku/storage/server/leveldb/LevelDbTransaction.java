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
import java.util.concurrent.locks.ReentrantLock;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.WriteBatch;
import tech.pegasys.teku.storage.server.ShuttingDownException;
import tech.pegasys.teku.storage.server.kvstore.KvStoreAccessor.KvStoreTransaction;
import tech.pegasys.teku.storage.server.kvstore.schema.KvStoreColumn;
import tech.pegasys.teku.storage.server.kvstore.schema.KvStoreVariable;

public class LevelDbTransaction implements KvStoreTransaction {

  private boolean closed = false;
  private final ReentrantLock lock = new ReentrantLock();

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
  public <T> void put(final KvStoreVariable<T> variable, final T value) {
    applyUpdate(
        () -> writeBatch.put(getVariableKey(variable), variable.getSerializer().serialize(value)));
  }

  @Override
  public <K, V> void put(final KvStoreColumn<K, V> column, final K key, final V value) {
    applyUpdate(() -> writeBatch.put(getColumnKey(column, key), serializeValue(column, value)));
  }

  @Override
  public <K, V> void put(final KvStoreColumn<K, V> column, final Map<K, V> data) {
    applyUpdate(
        () ->
            data.forEach(
                (key, value) ->
                    writeBatch.put(getColumnKey(column, key), serializeValue(column, value))));
  }

  @Override
  public <K, V> void delete(final KvStoreColumn<K, V> column, final K key) {
    applyUpdate(() -> writeBatch.delete(getColumnKey(column, key)));
  }

  @Override
  public <T> void delete(final KvStoreVariable<T> variable) {
    applyUpdate(() -> writeBatch.delete(getVariableKey(variable)));
  }

  @Override
  public void commit() {
    applyUpdate(
        () -> {
          try {
            db.write(writeBatch);
          } finally {
            close();
          }
        });
  }

  @Override
  public void rollback() {
    close();
  }

  @Override
  public void close() {
    lock.lock();
    try {
      if (closed) {
        // Already closed
        return;
      }
      closed = true;
      writeBatch.close();
      dbInstance.onTransactionClosed(this);
    } catch (IOException e) {
      dbInstance.onTransactionClosed(this);
      throw new UncheckedIOException(e);
    } finally {
      lock.unlock();
    }
  }

  private void applyUpdate(final Runnable operation) {
    lock.lock();
    try {
      assertOpen();
      operation.run();
    } finally {
      lock.unlock();
    }
  }

  private void assertOpen() {
    if (closed) {
      throw new ShuttingDownException();
    }
    dbInstance.assertOpen();
  }

  private <K, V> byte[] serializeValue(final KvStoreColumn<K, V> column, final V value) {
    return column.getValueSerializer().serialize(value);
  }
}
