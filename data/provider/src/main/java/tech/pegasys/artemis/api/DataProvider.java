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

package tech.pegasys.artemis.api;

import tech.pegasys.artemis.networking.p2p.network.P2PNetwork;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.CombinedChainDataClient;
import tech.pegasys.artemis.sync.SyncService;

public class DataProvider {
  private final NetworkDataProvider networkDataProvider;
  private final ChainDataProvider chainDataProvider;
  private final SyncDataProvider syncDataProvider;

  public DataProvider(
      final ChainStorageClient chainStorageClient,
      final CombinedChainDataClient combinedChainDataClient,
      final P2PNetwork<?> p2pNetwork,
      final SyncService syncService) {
    networkDataProvider = new NetworkDataProvider(p2pNetwork);
    chainDataProvider = new ChainDataProvider(chainStorageClient, combinedChainDataClient);
    syncDataProvider = new SyncDataProvider(syncService);
  }

  public DataProvider(
      NetworkDataProvider networkDataProvider,
      ChainDataProvider chainDataProvider,
      SyncDataProvider syncDataProvider) {
    this.networkDataProvider = networkDataProvider;
    this.chainDataProvider = chainDataProvider;
    this.syncDataProvider = syncDataProvider;
  }

  public NetworkDataProvider getNetworkDataProvider() {
    return networkDataProvider;
  }

  public ChainDataProvider getChainDataProvider() {
    return chainDataProvider;
  }

  public SyncDataProvider getSyncDataProvider() {
    return syncDataProvider;
  }

  @Deprecated
  public P2PNetwork<?> getP2pNetwork() {
    return networkDataProvider.getP2pNetwork();
  }

  @Deprecated
  public ChainStorageClient getChainStorageClient() {
    return chainDataProvider.getChainStorageClient();
  }

  @Deprecated
  public CombinedChainDataClient getCombinedChainDataClient() {
    return chainDataProvider.getCombinedChainDataClient();
  }

  @Deprecated
  public SyncService getSyncService() {
    return syncDataProvider.getSyncService();
  }
}
