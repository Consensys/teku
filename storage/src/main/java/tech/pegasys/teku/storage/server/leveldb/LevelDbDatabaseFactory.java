/*
 * Copyright ConsenSys Software Inc., 2022
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

import java.util.Optional;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.storage.api.StateStorageMode;
import tech.pegasys.teku.storage.server.Database;
import tech.pegasys.teku.storage.server.kvstore.KvStoreAccessor;
import tech.pegasys.teku.storage.server.kvstore.KvStoreConfiguration;
import tech.pegasys.teku.storage.server.kvstore.KvStoreDatabase;
import tech.pegasys.teku.storage.server.kvstore.schema.SchemaFinalizedSnapshotStateAdapter;
import tech.pegasys.teku.storage.server.kvstore.schema.SchemaHotAdapter;
import tech.pegasys.teku.storage.server.kvstore.schema.V6SchemaCombinedSnapshot;
import tech.pegasys.teku.storage.server.kvstore.schema.V6SchemaCombinedTreeState;

public class LevelDbDatabaseFactory {

  public static Database createLevelDb(
      final MetricsSystem metricsSystem,
      final KvStoreConfiguration hotConfiguration,
      final KvStoreConfiguration finalizedConfiguration,
      final StateStorageMode stateStorageMode,
      final long stateStorageFrequency,
      final boolean storeNonCanonicalBlocks,
      final boolean storeBlockExecutionPayloadSeparately,
      final boolean storeVotesEquivocation,
      final Optional<AsyncRunner> asyncRunner,
      final Spec spec) {
    final V6SchemaCombinedSnapshot combinedSchema =
        V6SchemaCombinedSnapshot.createV4(spec, storeVotesEquivocation);
    final SchemaHotAdapter schemaHot = combinedSchema.asSchemaHot();
    final SchemaFinalizedSnapshotStateAdapter schemaFinalized = combinedSchema.asSchemaFinalized();
    final KvStoreAccessor hotDb =
        LevelDbInstanceFactory.create(
            metricsSystem, STORAGE_HOT_DB, hotConfiguration, schemaHot.getAllColumns());
    final KvStoreAccessor finalizedDb =
        LevelDbInstanceFactory.create(
            metricsSystem,
            STORAGE_FINALIZED_DB,
            finalizedConfiguration,
            schemaFinalized.getAllColumns());
    return KvStoreDatabase.createV4(
        hotDb,
        finalizedDb,
        schemaHot,
        schemaFinalized,
        stateStorageMode,
        stateStorageFrequency,
        storeNonCanonicalBlocks,
        storeBlockExecutionPayloadSeparately,
        asyncRunner,
        spec);
  }

  public static Database createLevelDbV2(
      final MetricsSystem metricsSystem,
      final KvStoreConfiguration hotConfiguration,
      final StateStorageMode stateStorageMode,
      final long stateStorageFrequency,
      final boolean storeNonCanonicalBlocks,
      final boolean storeBlockExecutionPayloadSeparately,
      final boolean storeVotesEquivocation,
      final Optional<AsyncRunner> asyncRunner,
      final Spec spec) {
    final V6SchemaCombinedSnapshot schema =
        V6SchemaCombinedSnapshot.createV6(spec, storeVotesEquivocation);
    final KvStoreAccessor db =
        LevelDbInstanceFactory.create(
            metricsSystem, STORAGE, hotConfiguration, schema.getAllColumns());

    return KvStoreDatabase.createWithStateSnapshots(
        db,
        schema,
        stateStorageMode,
        stateStorageFrequency,
        storeNonCanonicalBlocks,
        storeBlockExecutionPayloadSeparately,
        asyncRunner,
        spec);
  }

  public static Database createLevelDbTree(
      final MetricsSystem metricsSystem,
      final KvStoreConfiguration hotConfiguration,
      final StateStorageMode stateStorageMode,
      final boolean storeNonCanonicalBlocks,
      final boolean storeBlockExecutionPayloadSeparately,
      final int maxKnownNodeCacheSize,
      final boolean storeVotesEquivocation,
      final Optional<AsyncRunner> asyncRunner,
      final Spec spec) {

    final V6SchemaCombinedTreeState schema =
        new V6SchemaCombinedTreeState(spec, storeVotesEquivocation);
    final KvStoreAccessor db =
        LevelDbInstanceFactory.create(
            metricsSystem, STORAGE, hotConfiguration, schema.getAllColumns());
    return KvStoreDatabase.createWithStateTree(
        metricsSystem,
        db,
        schema,
        stateStorageMode,
        storeNonCanonicalBlocks,
        storeBlockExecutionPayloadSeparately,
        maxKnownNodeCacheSize,
        asyncRunner,
        spec);
  }
}
