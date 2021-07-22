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

package tech.pegasys.teku.storage.server.rocksdb;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import org.apache.tuweni.bytes.Bytes;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDBException;
import org.rocksdb.TransactionDB;
import org.rocksdb.WriteOptions;
import tech.pegasys.teku.storage.server.ShuttingDownException;
import tech.pegasys.teku.storage.server.kvstore.KvStoreAccessor.KvStoreTransaction;
import tech.pegasys.teku.storage.server.kvstore.schema.KvStoreColumn;
import tech.pegasys.teku.storage.server.kvstore.schema.KvStoreVariable;

public class RocksDbTransaction implements KvStoreTransaction {
  private final ColumnFamilyHandle defaultHandle;
  private final ImmutableMap<KvStoreColumn<?, ?>, ColumnFamilyHandle> columnHandles;
  private final org.rocksdb.Transaction rocksDbTx;
  private final WriteOptions writeOptions;

  private final ReentrantLock lock = new ReentrantLock();
  private final AtomicBoolean closedViaDatabase = new AtomicBoolean(false);
  private final Consumer<RocksDbTransaction> onClosed;
  private boolean closed = false;

  RocksDbTransaction(
      final TransactionDB db,
      final ColumnFamilyHandle defaultHandle,
      final ImmutableMap<KvStoreColumn<?, ?>, ColumnFamilyHandle> columnHandles,
      final Consumer<RocksDbTransaction> onClosed) {
    this.defaultHandle = defaultHandle;
    this.columnHandles = columnHandles;
    this.writeOptions = new WriteOptions();
    this.rocksDbTx = db.beginTransaction(writeOptions);
    this.onClosed = onClosed;
  }

  @Override
  public <T> void put(KvStoreVariable<T> variable, T value) {
    final Bytes serialized = Bytes.wrap(variable.getSerializer().serialize(value));
    putRaw(variable, serialized);
  }

  @Override
  public <T> void putRaw(final KvStoreVariable<T> variable, final Bytes value) {
    applyUpdate(
        () -> {
          try {
            rocksDbTx.put(defaultHandle, variable.getId().toArrayUnsafe(), value.toArrayUnsafe());
          } catch (RocksDBException e) {
            throw RocksDbExceptionUtil.wrapException("Failed to put variable", e);
          }
        });
  }

  @Override
  public <K, V> void put(KvStoreColumn<K, V> column, K key, V value) {
    final Bytes keyBytes = Bytes.wrap(column.getKeySerializer().serialize(key));
    final Bytes valueBytes = Bytes.wrap(column.getValueSerializer().serialize(value));
    putRaw(column, keyBytes, valueBytes);
  }

  @Override
  public <K, V> void putRaw(
      final KvStoreColumn<K, V> column, final Bytes keyBytes, final Bytes valueBytes) {
    applyUpdate(
        () -> {
          final ColumnFamilyHandle handle = columnHandles.get(column);
          try {
            rocksDbTx.put(handle, keyBytes.toArrayUnsafe(), valueBytes.toArrayUnsafe());
          } catch (RocksDBException e) {
            throw RocksDbExceptionUtil.wrapException("Failed to put column data", e);
          }
        });
  }

  @Override
  public <K, V> void put(KvStoreColumn<K, V> column, Map<K, V> data) {
    applyUpdate(
        () -> {
          final ColumnFamilyHandle handle = columnHandles.get(column);
          for (Map.Entry<K, V> kvEntry : data.entrySet()) {
            final byte[] key = column.getKeySerializer().serialize(kvEntry.getKey());
            final byte[] value = column.getValueSerializer().serialize(kvEntry.getValue());
            try {
              rocksDbTx.put(handle, key, value);
            } catch (RocksDBException e) {
              throw RocksDbExceptionUtil.wrapException("Failed to put column data", e);
            }
          }
        });
  }

  @Override
  public <K, V> void delete(KvStoreColumn<K, V> column, K key) {
    applyUpdate(
        () -> {
          final ColumnFamilyHandle handle = columnHandles.get(column);
          try {
            rocksDbTx.delete(handle, column.getKeySerializer().serialize(key));
          } catch (RocksDBException e) {
            throw RocksDbExceptionUtil.wrapException("Failed to delete key", e);
          }
        });
  }

  @Override
  public <T> void delete(KvStoreVariable<T> variable) {
    applyUpdate(
        () -> {
          try {
            rocksDbTx.delete(defaultHandle, variable.getId().toArrayUnsafe());
          } catch (RocksDBException e) {
            throw RocksDbExceptionUtil.wrapException("Failed to delete variable", e);
          }
        });
  }

  @Override
  public void commit() {
    applyUpdate(
        () -> {
          try {
            this.rocksDbTx.commit();
          } catch (RocksDBException e) {
            throw RocksDbExceptionUtil.wrapException("Failed to commit transaction", e);
          } finally {
            close();
          }
        });
  }

  @Override
  public void rollback() {
    applyUpdate(
        () -> {
          try {
            this.rocksDbTx.rollback();
          } catch (RocksDBException e) {
            throw RocksDbExceptionUtil.wrapException("Failed to rollback transaction", e);
          } finally {
            close();
          }
        });
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
      if (closedViaDatabase.get()) {
        throw new ShuttingDownException();
      } else {
        throw new IllegalStateException("Attempt to update a closed transaction");
      }
    }
  }

  void closeViaDatabase() {
    closedViaDatabase.set(true);
    close();
  }

  @Override
  public void close() {
    lock.lock();
    try {
      if (!closed) {
        closed = true;
        onClosed.accept(this);
        writeOptions.close();
        rocksDbTx.close();
      }
    } finally {
      lock.unlock();
    }
  }
}
