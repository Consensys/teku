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

package tech.pegasys.teku.storage.storageSystem;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.mockito.Mockito.mock;

import java.util.List;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.interop.MockStartValidatorKeyPairFactory;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.storage.server.Database;
import tech.pegasys.teku.storage.server.DatabaseVersion;
import tech.pegasys.teku.storage.server.StateStorageMode;
import tech.pegasys.teku.storage.server.kvstore.InMemoryKvStoreDatabaseFactory;
import tech.pegasys.teku.storage.server.kvstore.MockKvStoreInstance;
import tech.pegasys.teku.storage.server.kvstore.schema.Schema;
import tech.pegasys.teku.storage.server.kvstore.schema.SchemaFinalizedSnapshotStateAdapter;
import tech.pegasys.teku.storage.server.kvstore.schema.V6SchemaCombinedSnapshot;
import tech.pegasys.teku.storage.server.kvstore.schema.V6SchemaCombinedTreeState;
import tech.pegasys.teku.storage.store.StoreConfig;

public class InMemoryStorageSystemBuilder {
  // Optional
  private DatabaseVersion version = DatabaseVersion.DEFAULT_VERSION;
  private StateStorageMode storageMode = StateStorageMode.ARCHIVE;
  private StoreConfig storeConfig = StoreConfig.createDefault();
  private int numberOfValidators = 3;
  private long stateStorageFrequency = 1L;
  private boolean storeNonCanonicalBlocks = false;
  private boolean storeVotesEquivocation = false;

  private EventChannels eventChannels = mock(EventChannels.class);
  private AsyncRunner asyncRunner = new StubAsyncRunner();

  private Spec spec = TestSpecFactory.createMinimalPhase0();

  // Internal variables
  MockKvStoreInstance unifiedDb;
  private MockKvStoreInstance hotDb;
  private MockKvStoreInstance coldDb;
  private boolean storeBlockExecutionPayloadSeparately = false;

  private InMemoryStorageSystemBuilder() {}

  public static InMemoryStorageSystemBuilder create() {
    return new InMemoryStorageSystemBuilder();
  }

  public static StorageSystem buildDefault() {
    return create().build();
  }

  public static StorageSystem buildDefault(final StateStorageMode storageMode) {
    return create().storageMode(storageMode).build();
  }

  public static StorageSystem buildDefault(final Spec spec) {
    return create().specProvider(spec).build();
  }

  public StorageSystem build() {
    final Database database;
    switch (version) {
      case LEVELDB_TREE:
        database = createLevelDbTreeDatabase();
        break;
      case LEVELDB2: // Leveldb only varies by db type which doesn't apply to in-memory
      case V6:
        database = createV6Database();
        break;
      case LEVELDB1: // Leveldb only varies by db type which doesn't apply to in-memory
      case V5:
        database = createV5Database();
        break;
      case V4:
        database = createV4Database();
        break;
      default:
        throw new UnsupportedOperationException("Unsupported database version: " + version);
    }

    final List<BLSKeyPair> validatorKeys =
        new MockStartValidatorKeyPairFactory().generateKeyPairs(0, numberOfValidators);
    return StorageSystem.create(
        database,
        createRestartSupplier(),
        storageMode,
        storeConfig,
        spec,
        ChainBuilder.create(spec, validatorKeys));
  }

  public InMemoryStorageSystemBuilder specProvider(final Spec spec) {
    this.spec = spec;
    return this;
  }

  private InMemoryStorageSystemBuilder copy() {
    final InMemoryStorageSystemBuilder copy =
        create()
            .version(version)
            .storageMode(storageMode)
            .stateStorageFrequency(stateStorageFrequency)
            .asyncRunner(asyncRunner)
            .storeBlockExecutionPayloadSeparately(storeBlockExecutionPayloadSeparately)
            .storeConfig(storeConfig);

    copy.unifiedDb = unifiedDb;
    copy.hotDb = hotDb;
    copy.coldDb = coldDb;
    copy.spec = spec;

    return copy;
  }

  public InMemoryStorageSystemBuilder asyncRunner(final AsyncRunner asyncRunner) {
    this.asyncRunner = asyncRunner;
    return this;
  }

  public InMemoryStorageSystemBuilder eventChannels(final EventChannels eventChannels) {
    this.eventChannels = eventChannels;
    return this;
  }

  public InMemoryStorageSystemBuilder version(final DatabaseVersion version) {
    checkNotNull(version);
    this.version = version;
    return this;
  }

  public InMemoryStorageSystemBuilder storeNonCanonicalBlocks(
      final boolean storeNonCanonicalBlocks) {
    this.storeNonCanonicalBlocks = storeNonCanonicalBlocks;
    return this;
  }

  public InMemoryStorageSystemBuilder storeBlockExecutionPayloadSeparately(
      final boolean storeBlockExecutionPayloadSeparately) {
    this.storeBlockExecutionPayloadSeparately = storeBlockExecutionPayloadSeparately;
    return this;
  }

  public InMemoryStorageSystemBuilder storageMode(final StateStorageMode storageMode) {
    checkNotNull(storageMode);
    this.storageMode = storageMode;
    return this;
  }

  public InMemoryStorageSystemBuilder stateStorageFrequency(final long stateStorageFrequency) {
    this.stateStorageFrequency = stateStorageFrequency;
    return this;
  }

  public InMemoryStorageSystemBuilder storeConfig(final StoreConfig storeConfig) {
    checkNotNull(storeConfig);
    this.storeConfig = storeConfig;
    return this;
  }

  public InMemoryStorageSystemBuilder numberOfValidators(final int numberOfValidators) {
    this.numberOfValidators = numberOfValidators;
    return this;
  }

  private StorageSystem.RestartedStorageSupplier createRestartSupplier() {
    return (mode) -> {
      final InMemoryStorageSystemBuilder copy = copy().storageMode(mode);
      copy.reopenDatabases();
      return copy.build();
    };
  }

  private Database createLevelDbTreeDatabase() {
    if (hotDb == null) {
      final V6SchemaCombinedTreeState schema =
          new V6SchemaCombinedTreeState(spec, storeVotesEquivocation);
      hotDb = MockKvStoreInstance.createEmpty(schema.getAllColumns(), schema.getAllVariables());
    }
    return InMemoryKvStoreDatabaseFactory.createTree(
        hotDb,
        storageMode,
        storeNonCanonicalBlocks,
        storeBlockExecutionPayloadSeparately,
        storeVotesEquivocation,
        eventChannels,
        asyncRunner,
        spec);
  }

  private Database createV6Database() {
    if (hotDb == null) {
      final V6SchemaCombinedSnapshot schema =
          V6SchemaCombinedSnapshot.createV6(spec, storeVotesEquivocation);
      hotDb = MockKvStoreInstance.createEmpty(schema.getAllColumns(), schema.getAllVariables());
    }
    return InMemoryKvStoreDatabaseFactory.createV6(
        hotDb,
        storageMode,
        stateStorageFrequency,
        storeNonCanonicalBlocks,
        storeBlockExecutionPayloadSeparately,
        storeVotesEquivocation,
        eventChannels,
        asyncRunner,
        spec);
  }

  // V5 only differs by the RocksDB configuration which doesn't apply to the in-memory version
  private Database createV5Database() {
    return createV4Database();
  }

  private Database createV4Database() {
    final V6SchemaCombinedSnapshot combinedSchema =
        V6SchemaCombinedSnapshot.createV4(spec, storeVotesEquivocation);
    if (hotDb == null) {
      final Schema v4SchemaHot = combinedSchema.asSchemaHot();
      hotDb =
          MockKvStoreInstance.createEmpty(
              v4SchemaHot.getAllColumns(), v4SchemaHot.getAllVariables());
    }
    if (coldDb == null) {
      final SchemaFinalizedSnapshotStateAdapter v4SchemaFinalized =
          combinedSchema.asSchemaFinalized();
      coldDb =
          MockKvStoreInstance.createEmpty(
              v4SchemaFinalized.getAllColumns(), v4SchemaFinalized.getAllVariables());
    }
    return InMemoryKvStoreDatabaseFactory.createV4(
        hotDb,
        coldDb,
        storageMode,
        stateStorageFrequency,
        storeNonCanonicalBlocks,
        storeBlockExecutionPayloadSeparately,
        storeVotesEquivocation,
        eventChannels,
        asyncRunner,
        spec);
  }

  private void reopenDatabases() {
    if (hotDb != null) {
      hotDb = hotDb.reopen();
    }
    if (coldDb != null) {
      coldDb = coldDb.reopen();
    }
    if (unifiedDb != null) {
      unifiedDb = unifiedDb.reopen();
    }
  }
}
