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

package tech.pegasys.teku.sync.multipeer;

import java.util.Optional;
import java.util.concurrent.Executor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.eventthread.EventThread;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.sync.SyncService.SyncSubscriber;
import tech.pegasys.teku.sync.SyncingStatus;
import tech.pegasys.teku.sync.multipeer.chains.TargetChain;
import tech.pegasys.teku.sync.multipeer.chains.TargetChains;

public class SyncController {
  private static final Logger LOG = LogManager.getLogger();

  private final Subscribers<SyncSubscriber> subscribers = Subscribers.create(true);

  private final EventThread eventThread;
  private final Executor subscriberExecutor;
  private final RecentChainData recentChainData;
  private final ChainSelector finalizedTargetChainSelector;
  private final Sync finalizedSync;

  /**
   * The current sync. When empty, no sync has started, otherwise contains the details of the last
   * started sync, which may have completed.
   *
   * <p>Note that this field may be accessed from off the event thread so that the sync status can
   * be queried from any thread. It should only be written from the event thread.
   */
  private Optional<InProgressSync> currentSync = Optional.empty();

  public SyncController(
      final EventThread eventThread,
      final Executor subscriberExecutor,
      final RecentChainData recentChainData,
      final ChainSelector finalizedTargetChainSelector,
      final Sync finalizedSync) {
    this.eventThread = eventThread;
    this.subscriberExecutor = subscriberExecutor;
    this.recentChainData = recentChainData;
    this.finalizedTargetChainSelector = finalizedTargetChainSelector;
    this.finalizedSync = finalizedSync;
  }

  /**
   * Notify Must be called on the sync event thread.
   *
   * @param finalizedChains the currently known finalized chains to consider
   */
  public void onTargetChainsUpdated(final TargetChains finalizedChains) {
    eventThread.checkOnEventThread();
    final boolean currentlySyncing = isSyncActive();
    final Optional<InProgressSync> newFinalizedSync =
        finalizedTargetChainSelector
            .selectTargetChain(finalizedChains, currentlySyncing)
            .map(this::startFinalizedSync);
    if (newFinalizedSync.isEmpty() && currentlySyncing) {
      return;
    }
    if (!currentlySyncing && newFinalizedSync.isPresent()) {
      notifySubscribers(true);
    }
    currentSync = newFinalizedSync;
  }

  private void onSyncComplete(final SyncResult result) {
    eventThread.checkOnEventThread();
    if (isSyncActive() || result == SyncResult.TARGET_CHANGED) {
      // A different sync is now running so ignore this change.
      return;
    }
    notifySubscribers(false);
  }

  public boolean isSyncActive() {
    return currentSync.map(InProgressSync::isActive).orElse(false);
  }

  public SyncingStatus getSyncStatus() {
    return currentSync.map(InProgressSync::asSyncingStatus).orElseGet(this::notSyncingStatus);
  }

  private SyncingStatus notSyncingStatus() {
    return new SyncingStatus(false, recentChainData.getHeadSlot());
  }

  public long subscribeToSyncChanges(final SyncSubscriber subscriber) {
    return subscribers.subscribe(subscriber);
  }

  public void unsubscribeFromSyncChanges(final long subscriberId) {
    subscribers.unsubscribe(subscriberId);
  }

  private void notifySubscribers(final boolean syncing) {
    subscriberExecutor.execute(() -> subscribers.deliver(SyncSubscriber::onSyncingChange, syncing));
  }

  private InProgressSync startFinalizedSync(final TargetChain chain) {
    eventThread.checkOnEventThread();
    if (currentSync.map(current -> current.hasSameTarget(chain)).orElse(false)) {
      return currentSync.get();
    }
    final UInt64 startSlot = recentChainData.getHeadSlot();
    final SafeFuture<SyncResult> syncResult = finalizedSync.syncToChain(chain);
    syncResult.finishAsync(
        this::onSyncComplete,
        error -> {
          LOG.error("Error encountered during sync", error);
          onSyncComplete(SyncResult.FAILED);
        },
        eventThread);
    return new InProgressSync(startSlot, chain, syncResult);
  }

  private class InProgressSync {
    private final UInt64 startSlot;
    private final TargetChain targetChain;
    private final SafeFuture<SyncResult> result;

    private InProgressSync(
        final UInt64 startSlot,
        final TargetChain targetChain,
        final SafeFuture<SyncResult> result) {
      this.startSlot = startSlot;
      this.targetChain = targetChain;
      this.result = result;
    }

    public boolean isActive() {
      return !result.isDone();
    }

    public SyncingStatus asSyncingStatus() {
      return result.isDone()
          ? notSyncingStatus()
          : new SyncingStatus(
              true, recentChainData.getHeadSlot(), startSlot, targetChain.getChainHead().getSlot());
    }

    public boolean hasSameTarget(final TargetChain chain) {
      return targetChain.equals(chain);
    }
  }
}
