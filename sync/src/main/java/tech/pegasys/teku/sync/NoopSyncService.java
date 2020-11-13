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

import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.sync.events.SyncState;
import tech.pegasys.teku.sync.events.SyncingStatus;
import tech.pegasys.teku.sync.forward.ForwardSync;
import tech.pegasys.teku.sync.gossip.FetchRecentBlocksService;
import tech.pegasys.teku.sync.gossip.RecentBlockFetcher;

public class NoopSyncService implements ForwardSync, RecentBlockFetcher, SyncService {

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
  public void unsubscribeFromSyncChanges(final long subscriberId) {}

  @Override
  public long subscribeBlockFetched(final FetchRecentBlocksService.BlockSubscriber subscriber) {
    return 0;
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
