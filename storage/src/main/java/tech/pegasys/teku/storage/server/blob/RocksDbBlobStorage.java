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

package tech.pegasys.teku.storage.server.blob;

import static tech.pegasys.teku.storage.server.blob.BlobStorageSchema.BLOBS;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;
import tech.pegasys.teku.storage.server.DatabaseStorageException;
import tech.pegasys.teku.storage.server.rocksdb.core.RocksDbAccessor;
import tech.pegasys.teku.storage.server.rocksdb.core.RocksDbAccessor.RocksDbTransaction;

public class RocksDbBlobStorage implements BlobStorage {
  private final RocksDbAccessor rocksDb;

  public RocksDbBlobStorage(final RocksDbAccessor rocksDb) {
    this.rocksDb = rocksDb;
  }

  @Override
  public Optional<Bytes> load(final Bytes32 id) {
    return rocksDb.get(BLOBS, id);
  }

  @Override
  public BlobTransaction startTransaction() {
    return new Transaction(rocksDb.startTransaction());
  }

  @Override
  public void close() {
    try {
      // TODO: Need to put in place proper controls to ensure we don't access rocksdb after close
      rocksDb.close();
    } catch (final Exception e) {
      throw new DatabaseStorageException("Failed to close blob db", e);
    }
  }

  public static class Transaction implements BlobTransaction {

    private final RocksDbTransaction transaction;

    public Transaction(final RocksDbTransaction transaction) {
      this.transaction = transaction;
    }

    @Override
    public Bytes32 store(final Bytes32 id, final SimpleOffsetSerializable data) {
      transaction.put(BLOBS, id, SimpleOffsetSerializer.serialize(data));
      return id;
    }

    @Override
    public void delete(final Bytes32 id) {
      transaction.delete(BLOBS, id);
    }

    @Override
    public void commit() {
      transaction.commit();
    }

    @Override
    public void close() {
      transaction.close();
    }
  }
}
