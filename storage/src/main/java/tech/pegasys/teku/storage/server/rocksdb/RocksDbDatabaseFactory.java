/*
 * Copyright Consensys Software Inc., 2025
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

import static tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory.STORAGE;
import static tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory.STORAGE_FINALIZED_DB;
import static tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory.STORAGE_HOT_DB;

import java.nio.file.Path;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.storage.archive.DataColumnSidecarsArchiver;
import tech.pegasys.teku.storage.archive.filesystem.FileSystemDataColumnSidecarsArchiver;
import tech.pegasys.teku.storage.server.Database;
import tech.pegasys.teku.storage.server.StateStorageMode;
import tech.pegasys.teku.storage.server.kvstore.KvStoreAccessor;
import tech.pegasys.teku.storage.server.kvstore.KvStoreConfiguration;
import tech.pegasys.teku.storage.server.kvstore.KvStoreDatabase;
import tech.pegasys.teku.storage.server.kvstore.schema.SchemaCombinedSnapshotState;
import tech.pegasys.teku.storage.server.kvstore.schema.SchemaFinalizedSnapshotStateAdapter;
import tech.pegasys.teku.storage.server.kvstore.schema.SchemaHotAdapter;
import tech.pegasys.teku.storage.server.kvstore.schema.V6SchemaCombinedSnapshot;

public class RocksDbDatabaseFactory {

  public static Database createV4(
      final MetricsSystem metricsSystem,
      final KvStoreConfiguration hotConfiguration,
      final KvStoreConfiguration finalizedConfiguration,
      final StateStorageMode stateStorageMode,
      final long stateStorageFrequency,
      final boolean storeNonCanonicalBlocks,
      final Spec spec) {

    final V6SchemaCombinedSnapshot combinedSchema = V6SchemaCombinedSnapshot.createV4(spec);
    final SchemaHotAdapter schemaHot = combinedSchema.asSchemaHot();
    final SchemaFinalizedSnapshotStateAdapter schemaFinalized = combinedSchema.asSchemaFinalized();
    final KvStoreAccessor hotDb =
        RocksDbInstanceFactory.create(
            metricsSystem,
            STORAGE_HOT_DB,
            hotConfiguration,
            schemaHot.getAllColumns(),
            schemaHot.getDeletedColumnIds(),
            schemaHot.getAllVariables(),
            schemaHot.getDeletedVariableIds());
    final KvStoreAccessor finalizedDb =
        RocksDbInstanceFactory.create(
            metricsSystem,
            STORAGE_FINALIZED_DB,
            finalizedConfiguration,
            schemaFinalized.getAllColumns(),
            schemaFinalized.getDeletedColumnIds(),
            schemaFinalized.getAllVariables(),
            schemaFinalized.getDeletedVariableIds());
    return KvStoreDatabase.createV4(
        hotDb,
        finalizedDb,
        schemaHot,
        schemaFinalized,
        stateStorageMode,
        stateStorageFrequency,
        storeNonCanonicalBlocks,
        spec);
  }

  public static Database createV6(
      final MetricsSystem metricsSystem,
      final KvStoreConfiguration hotConfiguration,
      final SchemaCombinedSnapshotState schema,
      final StateStorageMode stateStorageMode,
      final long stateStorageFrequency,
      final boolean storeNonCanonicalBlocks,
      final Spec spec,
      final Path columnsPath) {

    final KvStoreAccessor db =
        RocksDbInstanceFactory.create(
            metricsSystem,
            STORAGE,
            hotConfiguration,
            schema.getAllColumns(),
            schema.getDeletedColumnIds(),
            schema.getAllVariables(),
            schema.getDeletedVariableIds());

    final DataColumnSidecarsArchiver dataColumnSidecarsArchiver =
        new FileSystemDataColumnSidecarsArchiver(spec, columnsPath);

    return KvStoreDatabase.createWithStateSnapshots(
        db,
        schema,
        stateStorageMode,
        stateStorageFrequency,
        storeNonCanonicalBlocks,
        spec,
        dataColumnSidecarsArchiver);
  }
}
