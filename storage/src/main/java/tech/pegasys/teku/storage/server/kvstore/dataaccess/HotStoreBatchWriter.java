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
import tech.pegasys.teku.storage.server.kvstore.KvStoreAccessor;
import tech.pegasys.teku.storage.server.kvstore.schema.SchemaHot;

class HotStoreBatchWriter extends BatchWriter implements AutoCloseable {
  private V4HotKvStoreDao.V4HotUpdater updater = null;
  final SchemaHot schema;
  /**
   * @param targetBatchSize target size (MB) for a batch
   * @param db database accessor object
   * @param schema schema definition of the database
   */
  HotStoreBatchWriter(
      final int targetBatchSize,
      final Consumer<String> logger,
      final KvStoreAccessor db,
      final SchemaHot schema) {
    super(targetBatchSize, logger, db);
    this.schema = schema;
  }

  @Override
  void startTransaction() {
    if (updater == null) {
      updater = new V4HotKvStoreDao.V4HotUpdater(db, schema);
    }
  }

  @Override
  KvStoreAccessor.KvStoreTransaction getTransaction() {
    return updater.getTransaction();
  }

  @Override
  void commit() {
    if (updater != null) {
      updater.commit();
      updater = null;
    }
  }
}
