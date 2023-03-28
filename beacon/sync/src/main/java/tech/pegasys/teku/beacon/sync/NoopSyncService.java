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

import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.beacon.sync.events.SyncState;
import tech.pegasys.teku.beacon.sync.events.SyncingStatus;
import tech.pegasys.teku.beacon.sync.forward.ForwardSync;
import tech.pegasys.teku.beacon.sync.gossip.BlockSubscriber;
import tech.pegasys.teku.beacon.sync.gossip.RecentBlockFetcher;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice.OptimisticHeadSubscriber;

public class NoopSyncService
    implements ForwardSync, RecentBlockFetcher, SyncService, OptimisticHeadSubscriber {

  @Override
  public SafeFuture<?> start() {
    return SafeFuture.completedFuture(null);
  }

  @Override
  public SafeFuture<?> stop() {
    return SafeFuture.completedFuture(null);
  }

  @Override
  public ForwardSync getForwardSync() {
    return this;
  }

  @Override
  public RecentBlockFetcher getRecentBlockFetcher() {
    return this;
  }

  @Override
  public SyncingStatus getSyncStatus() {
    return new SyncingStatus(false, ZERO);
  }

  @Override
  public boolean isSyncActive() {
    return false;
  }

  @Override
  public long subscribeToSyncChanges(final SyncSubscriber subscriber) {
    return 0;
  }

  @Override
  public long subscribeToSyncStateChangesAndUpdate(SyncStateSubscriber subscriber) {
    final long subscriptionId = subscribeToSyncStateChanges(subscriber);
    subscriber.onSyncStateChange(getCurrentSyncState());
    return subscriptionId;
  }

  @Override
  public void unsubscribeFromSyncChanges(final long subscriberId) {}

  @Override
  public void subscribeBlockFetched(final BlockSubscriber subscriber) {
    // No-op
  }

  @Override
  public void requestRecentBlock(final Bytes32 blockRoot) {
    // No-op
  }

  @Override
  public void cancelRecentBlockRequest(final Bytes32 blockRoot) {
    // No-op
  }

  @Override
  public void onOptimisticHeadChanged(boolean isSyncingOptimistically) {
    // No-op
  }

  @Override
  public OptimisticHeadSubscriber getOptimisticSyncSubscriber() {
    return this;
  }

  @Override
  public SyncState getCurrentSyncState() {
    return SyncState.IN_SYNC;
  }

  @Override
  public long subscribeToSyncStateChanges(final SyncStateSubscriber subscriber) {
    return 0;
  }

  @Override
  public boolean unsubscribeFromSyncStateChanges(final long subscriberId) {
    return false;
  }
}
