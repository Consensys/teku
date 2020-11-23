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

package tech.pegasys.teku.sync;

import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.sync.events.SyncState;
import tech.pegasys.teku.sync.events.SyncStateTracker;
import tech.pegasys.teku.sync.forward.ForwardSync;
import tech.pegasys.teku.sync.forward.ForwardSyncService;
import tech.pegasys.teku.sync.gossip.RecentBlockFetcher;
import tech.pegasys.teku.sync.gossip.RecentBlockFetcherService;
import tech.pegasys.teku.sync.historical.HistoricalBlockSyncService;

public class DefaultSyncService extends Service implements SyncService {
  private final ForwardSyncService forwardSyncService;
  private final RecentBlockFetcherService blockFetcherService;
  private final SyncStateTracker syncStateTracker;
  private final HistoricalBlockSyncService historicalBlockSyncService;

  public DefaultSyncService(
      final ForwardSyncService forwardSyncService,
      final RecentBlockFetcherService blockFetcherService,
      final SyncStateTracker syncStateTracker,
      final HistoricalBlockSyncService historicalBlockSyncService) {
    this.forwardSyncService = forwardSyncService;
    this.blockFetcherService = blockFetcherService;
    this.syncStateTracker = syncStateTracker;
    this.historicalBlockSyncService = historicalBlockSyncService;
  }

  @Override
  protected SafeFuture<?> doStart() {
    return SafeFuture.allOfFailFast(
        forwardSyncService.start(),
        blockFetcherService.start(),
        syncStateTracker.start(),
        historicalBlockSyncService.start());
  }

  @Override
  protected SafeFuture<?> doStop() {
    return SafeFuture.allOf(
        forwardSyncService.stop(),
        blockFetcherService.stop(),
        syncStateTracker.stop(),
        historicalBlockSyncService.stop());
  }

  @Override
  public ForwardSync getForwardSync() {
    return forwardSyncService;
  }

  @Override
  public RecentBlockFetcher getRecentBlockFetcher() {
    return blockFetcherService;
  }

  @Override
  public SyncState getCurrentSyncState() {
    return syncStateTracker.getCurrentSyncState();
  }

  @Override
  public long subscribeToSyncStateChanges(final SyncStateSubscriber subscriber) {
    return syncStateTracker.subscribeToSyncStateChanges(subscriber);
  }

  @Override
  public boolean unsubscribeFromSyncStateChanges(final long subscriberId) {
    return syncStateTracker.unsubscribeFromSyncStateChanges(subscriberId);
  }
}
