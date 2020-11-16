/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.teku.sync.forward.singlepeer;

import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.sync.events.SyncingStatus;
import tech.pegasys.teku.sync.forward.ForwardSyncService;

public class SinglePeerSyncService extends Service implements ForwardSyncService {

  private final SyncManager syncManager;
  private final RecentChainData storageClient;

  public SinglePeerSyncService(final SyncManager syncManager, final RecentChainData storageClient) {
    this.storageClient = storageClient;
    this.syncManager = syncManager;
  }

  @Override
  protected SafeFuture<?> doStart() {
    // We shouldn't start syncing until we have reached genesis.
    // There are also no valid blocks until we've reached genesis so no point in gossipping and
    // queuing them
    storageClient.subscribeStoreInitialized(() -> syncManager.start().reportExceptions());
    return SafeFuture.COMPLETE;
  }

  @Override
  protected SafeFuture<?> doStop() {
    return syncManager.stop();
  }

  @Override
  public SyncingStatus getSyncStatus() {
    return syncManager.getSyncStatus();
  }

  @Override
  public boolean isSyncActive() {
    return syncManager.isSyncActive();
  }

  @Override
  public long subscribeToSyncChanges(final SyncSubscriber subscriber) {
    return syncManager.subscribeToSyncChanges(subscriber);
  }

  @Override
  public void unsubscribeFromSyncChanges(final long subscriberId) {
    syncManager.unsubscribeFromSyncChanges(subscriberId);
  }
}
