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

package tech.pegasys.teku.storage.storageSystem;

import static com.google.common.base.Preconditions.checkNotNull;

import tech.pegasys.teku.storage.server.Database;
import tech.pegasys.teku.storage.server.DatabaseVersion;
import tech.pegasys.teku.storage.server.rocksdb.InMemoryRocksDbDatabaseFactory;
import tech.pegasys.teku.storage.server.rocksdb.core.MockRocksDbInstance;
import tech.pegasys.teku.storage.server.rocksdb.schema.V4SchemaFinalized;
import tech.pegasys.teku.storage.server.rocksdb.schema.V4SchemaHot;
import tech.pegasys.teku.storage.store.StoreOptions;
import tech.pegasys.teku.util.config.StateStorageMode;

public class InMemoryStorageSystemBuilder {
  // Optional
  private DatabaseVersion version = DatabaseVersion.DEFAULT_VERSION;
  private StateStorageMode storageMode = StateStorageMode.ARCHIVE;
  private StoreOptions storeOptions = StoreOptions.createDefault();
  private long stateStorageFrequency = 1L;

  // Internal variables
  MockRocksDbInstance unifiedDb;
  private MockRocksDbInstance hotDb;
  private MockRocksDbInstance coldDb;

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

  public StorageSystem build() {
    final Database database;
    switch (version) {
      case V5:
        database = createV5Database();
        break;
      case V4:
        database = createV4Database();
        break;
      case V3:
        database = createV3Database();
        break;
      default:
        throw new UnsupportedOperationException("Unsupported database version: " + version);
    }

    return StorageSystem.create(database, createRestartSupplier(), storageMode, storeOptions);
  }

  private InMemoryStorageSystemBuilder copy() {
    final InMemoryStorageSystemBuilder copy =
        create()
            .version(version)
            .storageMode(storageMode)
            .stateStorageFrequency(stateStorageFrequency)
            .storeOptions(storeOptions);

    copy.unifiedDb = unifiedDb;
    copy.hotDb = hotDb;
    copy.coldDb = coldDb;

    return copy;
  }

  public InMemoryStorageSystemBuilder version(final DatabaseVersion version) {
    checkNotNull(version);
    this.version = version;
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

  public InMemoryStorageSystemBuilder storeOptions(final StoreOptions storeOptions) {
    checkNotNull(storeOptions);
    this.storeOptions = storeOptions;
    return this;
  }

  private StorageSystem.RestartedStorageSupplier createRestartSupplier() {
    return (mode) -> {
      final InMemoryStorageSystemBuilder copy = copy().storageMode(mode);
      copy.reopenDatabases();
      return copy.build();
    };
  }

  // V5 only differs by the RocksDB configuration which doesn't apply to the in-memory version
  private Database createV5Database() {
    return createV4Database();
  }

  private Database createV4Database() {
    if (hotDb == null) {
      hotDb = MockRocksDbInstance.createEmpty(V4SchemaHot.class);
    }
    if (coldDb == null) {
      coldDb = MockRocksDbInstance.createEmpty(V4SchemaFinalized.class);
    }
    return InMemoryRocksDbDatabaseFactory.createV4(
        hotDb, coldDb, storageMode, stateStorageFrequency);
  }

  private Database createV3Database() {
    if (unifiedDb == null) {
      unifiedDb = InMemoryRocksDbDatabaseFactory.createEmptyV3RocksDbInstance();
    }
    return InMemoryRocksDbDatabaseFactory.createV3(unifiedDb, storageMode);
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
