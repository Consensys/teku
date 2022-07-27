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

package tech.pegasys.teku.storage.server.kvstore;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.storage.server.Database;
import tech.pegasys.teku.storage.server.StateStorageMode;
import tech.pegasys.teku.storage.server.kvstore.schema.SchemaFinalizedSnapshotStateAdapter;
import tech.pegasys.teku.storage.server.kvstore.schema.SchemaHotAdapter;
import tech.pegasys.teku.storage.server.kvstore.schema.V6SchemaCombinedSnapshot;
import tech.pegasys.teku.storage.server.kvstore.schema.V6SchemaCombinedTreeState;

public class InMemoryKvStoreDatabaseFactory {

  public static Database createV4(
      MockKvStoreInstance hotDb,
      MockKvStoreInstance coldDb,
      final StateStorageMode storageMode,
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
    return KvStoreDatabase.createV4(
        hotDb,
        coldDb,
        schemaHot,
        schemaFinalized,
        storageMode,
        stateStorageFrequency,
        storeNonCanonicalBlocks,
        storeBlockExecutionPayloadSeparately,
        asyncRunner,
        spec);
  }

  public static Database createV6(
      MockKvStoreInstance db,
      final StateStorageMode storageMode,
      final long stateStorageFrequency,
      final boolean storeNonCanonicalBlocks,
      final boolean storeBlockExecutionPayloadSeparately,
      final boolean storeVotesEquivocation,
      final Optional<AsyncRunner> asyncRunner,
      final Spec spec) {
    final V6SchemaCombinedSnapshot combinedSchema =
        V6SchemaCombinedSnapshot.createV6(spec, storeVotesEquivocation);
    return KvStoreDatabase.createWithStateSnapshots(
        db,
        combinedSchema,
        storageMode,
        stateStorageFrequency,
        storeNonCanonicalBlocks,
        storeBlockExecutionPayloadSeparately,
        asyncRunner,
        spec);
  }

  public static Database createTree(
      MockKvStoreInstance db,
      final StateStorageMode storageMode,
      final boolean storeNonCanonicalBlocks,
      final boolean storeBlockExecutionPayloadSeparately,
      final boolean storeVotesEquivocation,
      final Optional<AsyncRunner> asyncRunner,
      final Spec spec) {
    final V6SchemaCombinedTreeState schema =
        new V6SchemaCombinedTreeState(spec, storeVotesEquivocation);
    return KvStoreDatabase.createWithStateTree(
        new StubMetricsSystem(),
        db,
        schema,
        storageMode,
        storeNonCanonicalBlocks,
        storeBlockExecutionPayloadSeparately,
        1000,
        asyncRunner,
        spec);
  }
}
