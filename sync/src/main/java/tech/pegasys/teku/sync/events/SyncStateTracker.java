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

package tech.pegasys.teku.sync.events;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.sync.forward.ForwardSync;

public class SyncStateTracker extends Service implements SyncStateProvider {
  private static final Logger LOG = LogManager.getLogger();
  private final AsyncRunner asyncRunner;
  private final ForwardSync syncService;
  private final P2PNetwork<? extends Peer> network;
  private final Subscribers<SyncStateSubscriber> subscribers = Subscribers.create(true);

  private final Duration startupTimeout;
  private final int startupTargetPeerCount;

  private boolean startingUp;
  private boolean syncActive = false;
  private long peerConnectedSubscriptionId;
  private long syncSubscriptionId;

  private volatile SyncState currentState;

  public SyncStateTracker(
      final AsyncRunner asyncRunner,
      final ForwardSync syncService,
      final P2PNetwork<? extends Peer> network,
      final int startupTargetPeerCount,
      final Duration startupTimeout) {
    this.asyncRunner = asyncRunner;
    this.syncService = syncService;
    this.network = network;
    this.startupTargetPeerCount = startupTargetPeerCount;
    this.startupTimeout = startupTimeout;
    if (startupTargetPeerCount == 0 || startupTimeout.toMillis() == 0) {
      startingUp = false;
      currentState = SyncState.IN_SYNC;
    } else {
      startingUp = true;
      currentState = SyncState.START_UP;
    }
  }

  @Override
  public SyncState getCurrentSyncState() {
    return currentState;
  }

  @Override
  public long subscribeToSyncStateChanges(final SyncStateSubscriber subscriber) {
    return subscribers.subscribe(subscriber);
  }

  @Override
  public boolean unsubscribeFromSyncStateChanges(long subscriberId) {
    return subscribers.unsubscribe(subscriberId);
  }

  private void updateCurrentState() {
    final SyncState previousState = currentState;
    if (syncActive) {
      currentState = SyncState.SYNCING;
    } else if (startingUp) {
      currentState = SyncState.START_UP;
    } else {
      currentState = SyncState.IN_SYNC;
    }

    if (currentState != previousState) {
      subscribers.deliver(SyncStateSubscriber::onSyncStateChange, currentState);
    }
  }

  @Override
  protected synchronized SafeFuture<?> doStart() {
    LOG.debug(
        "Starting sync state tracker with initial state: {}, target peer count: {}, startup timeout: {}",
        currentState,
        startupTargetPeerCount,
        startupTimeout);
    peerConnectedSubscriptionId = network.subscribeConnect(peer -> onPeerConnected());
    asyncRunner
        .runAfterDelay(this::onStartupTimeout, startupTimeout.toMillis(), TimeUnit.MILLISECONDS)
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
    syncActive = active;
    updateCurrentState();
  }

  private synchronized void markStartupComplete() {
    startingUp = false;
    network.unsubscribeConnect(peerConnectedSubscriptionId);
    updateCurrentState();
  }

  private synchronized void onStartupTimeout() {
    if (!startingUp) {
      return;
    }
    LOG.debug("Startup timeout reached");
    markStartupComplete();
  }

  private synchronized void onPeerConnected() {
    if (network.getPeerCount() >= startupTargetPeerCount) {
      LOG.debug("Target peer count ({}) was reached", startupTargetPeerCount);
      markStartupComplete();
    }
  }
}
