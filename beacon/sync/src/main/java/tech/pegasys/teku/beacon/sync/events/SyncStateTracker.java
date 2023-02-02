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

package tech.pegasys.teku.beacon.sync.events;

import static tech.pegasys.teku.infrastructure.logging.EventLogger.EVENT_LOG;

import java.time.Duration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.beacon.sync.forward.ForwardSync;
import tech.pegasys.teku.ethereum.executionclient.events.ExecutionClientEventsChannel;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.metrics.SettableGauge;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice.OptimisticHeadSubscriber;

public class SyncStateTracker extends Service
    implements SyncStateProvider, OptimisticHeadSubscriber, ExecutionClientEventsChannel {
  private static final Logger LOG = LogManager.getLogger();
  private final AsyncRunner asyncRunner;
  private final ForwardSync syncService;
  private final P2PNetwork<? extends Peer> network;
  private final Subscribers<SyncStateSubscriber> subscribers = Subscribers.create(true);
  private final EventLogger eventLogger;
  private final SettableGauge isSyncingGauge;

  private final Duration startupTimeout;
  private final int startupTargetPeerCount;

  private boolean startingUp;
  private boolean syncActive = false;
  private long peerConnectedSubscriptionId;
  private long syncSubscriptionId;
  private boolean headIsOptimistic = false;

  private boolean executionLayerIsAvailable = true;

  private volatile SyncState currentState;

  public SyncStateTracker(
      final AsyncRunner asyncRunner,
      final ForwardSync syncService,
      final P2PNetwork<? extends Peer> network,
      final int startupTargetPeerCount,
      final Duration startupTimeout,
      final MetricsSystem metricsSystem) {
    this(
        asyncRunner,
        syncService,
        network,
        startupTargetPeerCount,
        startupTimeout,
        EVENT_LOG,
        metricsSystem);
  }

  SyncStateTracker(
      final AsyncRunner asyncRunner,
      final ForwardSync syncService,
      final P2PNetwork<? extends Peer> network,
      final int startupTargetPeerCount,
      final Duration startupTimeout,
      final EventLogger eventLogger,
      final MetricsSystem metricsSystem) {
    this.asyncRunner = asyncRunner;
    this.syncService = syncService;
    this.network = network;
    this.startupTargetPeerCount = startupTargetPeerCount;
    this.startupTimeout = startupTimeout;
    this.eventLogger = eventLogger;
    this.isSyncingGauge =
        SettableGauge.create(
            metricsSystem,
            TekuMetricCategory.BEACON,
            "node_syncing_active",
            "Indicator to show the node sync is currently running.");
    if (startupTargetPeerCount == 0 || startupTimeout.toMillis() == 0) {
      startingUp = false;
      currentState = SyncState.IN_SYNC;
    } else {
      startingUp = true;
      currentState = SyncState.START_UP;
    }
    this.isSyncingGauge.set(currentState.isSyncing() ? 1.0 : 0.0);
  }

  @Override
  public SyncState getCurrentSyncState() {
    return currentState;
  }

  @Override
  public void onOptimisticHeadChanged(final boolean active) {
    logSyncStateOnOptimisticHeadChanged(headIsOptimistic, active);
    headIsOptimistic = active;
    updateCurrentState();
  }

  @Override
  public long subscribeToSyncStateChanges(final SyncStateSubscriber subscriber) {
    return subscribers.subscribe(subscriber);
  }

  @Override
  public long subscribeToSyncStateChangesAndUpdate(final SyncStateSubscriber subscriber) {
    final long subscriptionId = subscribeToSyncStateChanges(subscriber);
    subscriber.onSyncStateChange(getCurrentSyncState());
    return subscriptionId;
  }

  @Override
  public boolean unsubscribeFromSyncStateChanges(long subscriberId) {
    return subscribers.unsubscribe(subscriberId);
  }

  private void updateCurrentState() {
    final SyncState previousState = currentState;
    if (!executionLayerIsAvailable) {
      currentState = SyncState.EL_OFFLINE;
    } else if (headIsOptimistic) {
      currentState = syncActive ? SyncState.OPTIMISTIC_SYNCING : SyncState.AWAITING_EL;
    } else if (syncActive) {
      currentState = SyncState.SYNCING;
    } else if (startingUp) {
      currentState = SyncState.START_UP;
    } else {
      currentState = SyncState.IN_SYNC;
    }

    if (currentState != previousState) {
      isSyncingGauge.set(currentState.isSyncing() ? 1.0 : 0.0);
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
        .runAfterDelay(this::onStartupTimeout, startupTimeout)
        .ifExceptionGetsHereRaiseABug();
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
    logSyncStateOnSyncingChanged(syncActive, active);
    syncActive = active;
    updateCurrentState();
  }

  private void logSyncStateOnOptimisticHeadChanged(
      final boolean wasHeadPreviouslyOptimistic, final boolean isHeadOptimistic) {

    if (wasHeadPreviouslyOptimistic == isHeadOptimistic || startingUp) {
      return;
    }

    if (isHeadOptimistic) {
      if (syncActive) {
        eventLogger.headTurnedOptimisticWhileSyncing();
      } else {
        eventLogger.headTurnedOptimisticWhileInSync();
      }
      return;
    }

    if (syncActive) {
      eventLogger.headNoLongerOptimisticWhileSyncing();
    } else {
      eventLogger.syncCompleted();
    }
  }

  private void logSyncStateOnSyncingChanged(
      final boolean wasPreviouslySyncing, final boolean isSyncing) {

    if (wasPreviouslySyncing == isSyncing) {
      return;
    }

    if (isSyncing) {
      eventLogger.syncStart();
      return;
    }

    if (headIsOptimistic) {
      eventLogger.syncCompletedWhileHeadIsOptimistic();
    } else {
      eventLogger.syncCompleted();
    }
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

  @Override
  public void onAvailabilityUpdated(boolean isAvailable) {
    this.executionLayerIsAvailable = isAvailable;
    updateCurrentState();
  }
}
