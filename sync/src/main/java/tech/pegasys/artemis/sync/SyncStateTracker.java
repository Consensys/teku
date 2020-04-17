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

package tech.pegasys.artemis.sync;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import tech.pegasys.artemis.networking.p2p.network.P2PNetwork;
import tech.pegasys.artemis.networking.p2p.peer.Peer;
import tech.pegasys.artemis.service.serviceutils.Service;
import tech.pegasys.artemis.util.async.AsyncRunner;
import tech.pegasys.artemis.util.async.SafeFuture;

public class SyncStateTracker extends Service {
  private final AsyncRunner asyncRunner;
  private final SyncService syncService;
  private final P2PNetwork<? extends Peer> network;

  private final Duration startupTimeout;
  private final int startupTargetPeerCount;

  private boolean startingUp = true;
  private boolean syncActive = false;
  private long peerConnectedSubscriptionId;
  private long syncSubscriptionId;

  private volatile SyncState currentState;

  public SyncStateTracker(
      final AsyncRunner asyncRunner,
      final SyncService syncService,
      final P2PNetwork<? extends Peer> network,
      final int startupTargetPeerCount,
      final Duration startupTimeout) {
    this.asyncRunner = asyncRunner;
    this.syncService = syncService;
    this.network = network;
    this.startupTargetPeerCount = startupTargetPeerCount;
    this.startupTimeout = startupTimeout;
    currentState =
        startupTargetPeerCount == 0 || startupTimeout.toMillis() == 0
            ? SyncState.IN_SYNC
            : SyncState.START_UP;
  }

  public SyncState getCurrentSyncState() {
    return currentState;
  }

  private void updateCurrentState() {
    if (syncActive) {
      currentState = SyncState.SYNCING;
    } else if (startingUp) {
      currentState = SyncState.START_UP;
    } else {
      currentState = SyncState.IN_SYNC;
    }
  }

  @Override
  protected synchronized SafeFuture<?> doStart() {
    peerConnectedSubscriptionId = network.subscribeConnect(peer -> onPeerConnected());
    asyncRunner
        .runAfterDelay(this::markStartupComplete, startupTimeout.toMillis(), TimeUnit.MILLISECONDS)
        .reportExceptions();
    syncSubscriptionId = syncService.subscribeToSyncChanges(this::onSyncingChanged);
    return SafeFuture.COMPLETE;
  }

  @Override
  protected synchronized SafeFuture<?> doStop() {
    network.unsubscribeConnect(peerConnectedSubscriptionId);
    syncService.unsubscribeFromSyncChanges(syncSubscriptionId);
    return SafeFuture.COMPLETE;
  }

  private synchronized void onSyncingChanged(final boolean active) {
    startingUp = false;
    syncActive = active;
    updateCurrentState();
  }

  private synchronized void markStartupComplete() {
    startingUp = false;
    updateCurrentState();
  }

  private synchronized void onPeerConnected() {
    if (network.getPeerCount() >= startupTargetPeerCount) {
      markStartupComplete();
    }
  }
}
