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

package tech.pegasys.teku.storage.server.kvstore.dataaccess;

import java.util.function.Consumer;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.storage.server.kvstore.ColumnEntry;
import tech.pegasys.teku.storage.server.kvstore.KvStoreAccessor;
import tech.pegasys.teku.storage.server.kvstore.schema.KvStoreColumn;

abstract class BatchWriter implements AutoCloseable {
  private static final int OUTPUT_PER_TRANSACTION_COUNT = 10;
  final KvStoreAccessor db;

  private final Consumer<String> logger;
  private final long targetBatchSize;

  private long transactionCounter = 0;
  private long entryCounter = 0;
  private long bytes = 0;

  /**
   * @param targetBatchSize target size (MB) for a batch
   * @param db database accessor object
   */
  BatchWriter(final int targetBatchSize, final Consumer<String> logger, final KvStoreAccessor db) {
    // target batch size comes in MB, can store in Bytes to make life simpler for comparison
    this.targetBatchSize = targetBatchSize * 1_000_000L;
    this.logger = logger;
    this.db = db;
  }

  abstract void startTransaction();

  abstract KvStoreAccessor.KvStoreTransaction getTransaction();

  abstract void commit();

  void add(final KvStoreColumn<?, ?> column, final ColumnEntry<Bytes, Bytes> entry) {
    startTransaction();

    bytes += entry.getKey().size() + entry.getValue().size();
    getTransaction().putRaw(column, entry.getKey(), entry.getValue());
    entryCounter++;
    if (bytes >= targetBatchSize) {
      commit();
      bytes = 0;
      transactionCounter++;
      if (transactionCounter % OUTPUT_PER_TRANSACTION_COUNT == 0L) {
        logger.accept(String.format(" -- %,d...", entryCounter));
      }
    }
  }

  @Override
  public void close() {
    try {
      commit();
      if (entryCounter > 0) {
        logger.accept(String.format(" => Inserted %,d entries...", entryCounter));
      }
    } catch (Exception ex) {
      logger.accept("Failed to commit transaction on close: " + ex.getCause());
    }
  }
}
