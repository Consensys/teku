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

package tech.pegasys.teku.storage;

import com.google.common.eventbus.EventBus;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.server.ChainStorageServer;
import tech.pegasys.teku.storage.server.Database;
import tech.pegasys.teku.storage.server.DatabaseFactory;
import tech.pegasys.teku.storage.server.rocksdb.InMemoryRocksDbDatabase;
import tech.pegasys.teku.util.config.StateStorageMode;

public class InMemoryStorageSystem {
  private final RecentChainData recentChainData;
  private final CombinedChainDataClient combinedChainDataClient;

  public InMemoryStorageSystem(
      final RecentChainData recentChainData,
      final CombinedChainDataClient combinedChainDataClient) {
    this.recentChainData = recentChainData;
    this.combinedChainDataClient = combinedChainDataClient;
  }

  public static InMemoryStorageSystem createEmptyV3StorageSystem(
      final StateStorageMode storageMode) {
    try {
      final EventBus eventBus = new EventBus();
      final Database database = InMemoryRocksDbDatabase.createEmptyV3(storageMode);
      final DatabaseFactory dbFactory = () -> database;
      final ChainStorageServer chainStorageServer = ChainStorageServer.create(eventBus, dbFactory);

      final RecentChainData recentChainData =
          MemoryOnlyRecentChainData.builder()
              .eventBus(eventBus)
              .storageUpdateChannel(chainStorageServer)
              .build();

      final CombinedChainDataClient combinedChainDataClient =
          new CombinedChainDataClient(recentChainData, chainStorageServer);

      chainStorageServer.start();
      return new InMemoryStorageSystem(recentChainData, combinedChainDataClient);
    } catch (Exception e) {
      throw new IllegalStateException("Unable to initialize storage system", e);
    }
  }

  public RecentChainData recentChainData() {
    return recentChainData;
  }

  public CombinedChainDataClient combinedChainDataClient() {
    return combinedChainDataClient;
  }
}
