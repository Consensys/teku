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

import static tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory.STORAGE;
import static tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory.STORAGE_FINALIZED_DB;
import static tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory.STORAGE_HOT_DB;

import java.util.ArrayList;
import java.util.Collection;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.storage.server.Database;
import tech.pegasys.teku.storage.server.StateStorageMode;
import tech.pegasys.teku.storage.server.kvstore.KvStoreAccessor;
import tech.pegasys.teku.storage.server.kvstore.KvStoreConfiguration;
import tech.pegasys.teku.storage.server.kvstore.KvStoreDatabase;
import tech.pegasys.teku.storage.server.kvstore.schema.KvStoreColumn;
import tech.pegasys.teku.storage.server.kvstore.schema.SchemaFinalized;
import tech.pegasys.teku.storage.server.kvstore.schema.SchemaHot;
import tech.pegasys.teku.storage.server.kvstore.schema.V4SchemaFinalized;

public class LevelDbDatabaseFactory {

  public static Database createLevelDb(
      final MetricsSystem metricsSystem,
      final KvStoreConfiguration hotConfiguration,
      final KvStoreConfiguration finalizedConfiguration,
      final StateStorageMode stateStorageMode,
      final long stateStorageFrequency,
      final boolean storeNonCanonicalBlocks,
      final Spec spec) {
    final Collection<KvStoreColumn<?, ?>> v4FinalizedColumns =
        V4SchemaFinalized.create(spec).getAllColumns();
    final KvStoreAccessor hotDb =
        LevelDbInstanceFactory.create(
            metricsSystem, STORAGE_HOT_DB, hotConfiguration, v4FinalizedColumns);
    final KvStoreAccessor finalizedDb =
        LevelDbInstanceFactory.create(
            metricsSystem, STORAGE_FINALIZED_DB, finalizedConfiguration, v4FinalizedColumns);
    return KvStoreDatabase.createV4(
        metricsSystem,
        hotDb,
        finalizedDb,
        stateStorageMode,
        stateStorageFrequency,
        storeNonCanonicalBlocks,
        spec);
  }

  public static Database createLevelDbV2(
      final MetricsSystem metricsSystem,
      final KvStoreConfiguration hotConfiguration,
      final SchemaHot schemaHot,
      final SchemaFinalized schemaFinalized,
      final StateStorageMode stateStorageMode,
      final long stateStorageFrequency,
      final boolean storeNonCanonicalBlocks,
      final Spec spec) {

    ArrayList<KvStoreColumn<?, ?>> allColumns = new ArrayList<>(schemaHot.getAllColumns());
    allColumns.addAll(schemaFinalized.getAllColumns());
    final KvStoreAccessor db =
        LevelDbInstanceFactory.create(metricsSystem, STORAGE, hotConfiguration, allColumns);
    return KvStoreDatabase.createV6(
        metricsSystem,
        db,
        schemaHot,
        schemaFinalized,
        stateStorageMode,
        stateStorageFrequency,
        storeNonCanonicalBlocks,
        spec);
  }
}
