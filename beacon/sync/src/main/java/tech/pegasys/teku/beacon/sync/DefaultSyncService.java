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

package tech.pegasys.teku.beacon.sync;

import tech.pegasys.teku.beacon.sync.events.SyncState;
import tech.pegasys.teku.beacon.sync.events.SyncStateTracker;
import tech.pegasys.teku.beacon.sync.forward.ForwardSync;
import tech.pegasys.teku.beacon.sync.forward.ForwardSyncService;
import tech.pegasys.teku.beacon.sync.gossip.blobs.RecentBlobSidecarFetcher;
import tech.pegasys.teku.beacon.sync.gossip.blobs.RecentBlobSidecarFetcherService;
import tech.pegasys.teku.beacon.sync.gossip.blocks.RecentBlockFetcher;
import tech.pegasys.teku.beacon.sync.gossip.blocks.RecentBlockFetcherService;
import tech.pegasys.teku.beacon.sync.historical.HistoricalBlockSyncService;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;

public class DefaultSyncService extends Service implements SyncService {
  private final ForwardSyncService forwardSyncService;
  private final RecentBlockFetcherService blockFetcherService;
  private final RecentBlobSidecarFetcherService blobSidecarFetcherService;
  private final SyncStateTracker syncStateTracker;
  private final HistoricalBlockSyncService historicalBlockSyncService;

  public DefaultSyncService(
      final ForwardSyncService forwardSyncService,
      final RecentBlockFetcherService blockFetcherService,
      final RecentBlobSidecarFetcherService blobSidecarFetcherService,
      final SyncStateTracker syncStateTracker,
      final HistoricalBlockSyncService historicalBlockSyncService) {
    this.forwardSyncService = forwardSyncService;
    this.blockFetcherService = blockFetcherService;
    this.blobSidecarFetcherService = blobSidecarFetcherService;
    this.syncStateTracker = syncStateTracker;
    this.historicalBlockSyncService = historicalBlockSyncService;
  }

  @Override
  protected SafeFuture<?> doStart() {
    return SafeFuture.allOfFailFast(
        forwardSyncService.start(),
        blockFetcherService.start(),
        blobSidecarFetcherService.start(),
        syncStateTracker.start(),
        historicalBlockSyncService.start());
  }

  @Override
  protected SafeFuture<?> doStop() {
    return SafeFuture.allOf(
        forwardSyncService.stop(),
        blockFetcherService.stop(),
        blobSidecarFetcherService.stop(),
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
  public RecentBlobSidecarFetcher getRecentBlobSidecarFetcher() {
    return blobSidecarFetcherService;
  }

  @Override
  public SyncState getCurrentSyncState() {
    return syncStateTracker.getCurrentSyncState();
  }

  @Override
  public ForkChoice.OptimisticHeadSubscriber getOptimisticSyncSubscriber() {
    return syncStateTracker;
  }

  @Override
  public long subscribeToSyncStateChanges(final SyncStateSubscriber subscriber) {
    return syncStateTracker.subscribeToSyncStateChanges(subscriber);
  }

  @Override
  public long subscribeToSyncStateChangesAndUpdate(final SyncStateSubscriber subscriber) {
    final long subscriptionId = subscribeToSyncStateChanges(subscriber);
    subscriber.onSyncStateChange(getCurrentSyncState());
    return subscriptionId;
  }

  @Override
  public boolean unsubscribeFromSyncStateChanges(final long subscriberId) {
    return syncStateTracker.unsubscribeFromSyncStateChanges(subscriberId);
  }
}
