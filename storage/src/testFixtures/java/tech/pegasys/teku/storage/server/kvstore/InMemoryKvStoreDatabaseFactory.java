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

package tech.pegasys.teku.storage.server.kvstore;

import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.storage.server.Database;
import tech.pegasys.teku.storage.server.StateStorageMode;
import tech.pegasys.teku.storage.server.kvstore.schema.V4SchemaHot;
import tech.pegasys.teku.storage.server.kvstore.schema.V6SnapshotSchemaFinalized;
import tech.pegasys.teku.storage.server.kvstore.schema.V6TreeSchemaFinalized;

public class InMemoryKvStoreDatabaseFactory {

  public static Database createV4(
      MockKvStoreInstance hotDb,
      MockKvStoreInstance coldDb,
      final StateStorageMode storageMode,
      final long stateStorageFrequency,
      final boolean storeNonCanonicalBlocks,
      final Spec spec) {
    return KvStoreDatabase.createV4(
        new StubMetricsSystem(),
        hotDb,
        coldDb,
        storageMode,
        stateStorageFrequency,
        storeNonCanonicalBlocks,
        spec);
  }

  public static Database createV6(
      MockKvStoreInstance db,
      final StateStorageMode storageMode,
      final long stateStorageFrequency,
      final boolean storeNonCanonicalBlocks,
      final Spec spec) {
    return KvStoreDatabase.createWithStateSnapshots(
        new StubMetricsSystem(),
        db,
        db,
        new V4SchemaHot(spec),
        new V6SnapshotSchemaFinalized(spec),
        storageMode,
        stateStorageFrequency,
        storeNonCanonicalBlocks,
        spec);
  }

  public static Database createTree(
      MockKvStoreInstance db,
      final StateStorageMode storageMode,
      final long stateStorageFrequency,
      final boolean storeNonCanonicalBlocks,
      final Spec spec) {
    return KvStoreDatabase.createWithStateTree(
        new StubMetricsSystem(),
        db,
        new V4SchemaHot(spec),
        new V6TreeSchemaFinalized(spec),
        storageMode,
        storeNonCanonicalBlocks,
        1000,
        spec);
  }
}
