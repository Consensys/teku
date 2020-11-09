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

package tech.pegasys.teku.sync.forward.multipeer;

import com.google.common.base.MoreObjects;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.eventthread.EventThread;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.sync.events.SyncingStatus;
import tech.pegasys.teku.sync.forward.ForwardSync.SyncSubscriber;
import tech.pegasys.teku.sync.forward.multipeer.chains.TargetChain;

public class SyncController {
  private static final Logger LOG = LogManager.getLogger();

  private final Subscribers<SyncSubscriber> subscribers = Subscribers.create(true);

  private final EventThread eventThread;
  private final Executor subscriberExecutor;
  private final RecentChainData recentChainData;
  private final ChainSelector finalizedTargetChainSelector;
  private final ChainSelector nonfinalizedTargetChainSelector;
  private final Sync sync;

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
      final ChainSelector nonfinalizedTargetChainSelector,
      final Sync sync) {
    this.eventThread = eventThread;
    this.subscriberExecutor = subscriberExecutor;
    this.recentChainData = recentChainData;
    this.finalizedTargetChainSelector = finalizedTargetChainSelector;
    this.nonfinalizedTargetChainSelector = nonfinalizedTargetChainSelector;
    this.sync = sync;
  }

  /**
   * Notify that chains have been updated.
   *
   * <p>Must be called on the sync event thread.
   */
  public void onTargetChainsUpdated() {
    eventThread.checkOnEventThread();
    final boolean currentlySyncing = isSyncActive();
    final Optional<InProgressSync> newSync = selectNewSyncTarget(currentlySyncing);
    if (newSync.isEmpty() && currentlySyncing) {
      return;
    }
    if (!currentlySyncing && newSync.isPresent()) {
      notifySubscribers(true);
    }
    currentSync = newSync;
  }

  private Optional<InProgressSync> selectNewSyncTarget(final boolean currentlySyncing) {
    final Optional<TargetChain> bestFinalizedChain =
        finalizedTargetChainSelector.selectTargetChain(currentlySyncing);
    // We may not have run fork choice to update our chain head, so check if the best finalized
    // chain is the one we just finished syncing and move on to non-finalized if it is.
    if (bestFinalizedChain.isPresent() && !isCompletedSync(bestFinalizedChain.get())) {
      return bestFinalizedChain.map(chain -> startSync(chain, true));
    } else if (!isSyncingFinalizedChain()) {
      final Optional<TargetChain> targetChain =
          nonfinalizedTargetChainSelector.selectTargetChain(currentlySyncing);
      return targetChain.map(chain -> startSync(chain, false));
    }
    return Optional.empty();
  }

  private boolean isCompletedSync(final TargetChain targetChain) {
    return currentSync
        .map(sync -> !sync.isActive() && sync.hasSameTarget(targetChain))
        .orElse(false);
  }

  private Boolean isSyncingFinalizedChain() {
    return currentSync.map(current -> current.isActive() && current.isFinalizedChain).orElse(false);
  }

  private void onSyncComplete(final SyncResult result) {
    eventThread.checkOnEventThread();
    if (isSyncActive() || result == SyncResult.TARGET_CHANGED) {
      // A different sync is now running so ignore this change.
      LOG.debug(
          "Ignoring sync complete because another sync is already active. Result: {}", result);
      return;
    }
    LOG.debug(
        "Completed sync to chain {} with result {}",
        currentSync.map(Objects::toString).orElse("<unknown>"),
        result);
    // See if there's a new sync we should start (possibly switching to non-finalized sync)
    currentSync = selectNewSyncTarget(true);
    if (!isSyncActive()) {
      currentSync = Optional.empty();
      notifySubscribers(false);
    }
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

  private InProgressSync startSync(final TargetChain chain, final boolean isFinalized) {
    eventThread.checkOnEventThread();
    if (currentSync.map(current -> current.hasSameTarget(chain)).orElse(false)) {
      return currentSync.get();
    }
    final UInt64 startSlot = recentChainData.getHeadSlot();
    final SafeFuture<SyncResult> syncResult = sync.syncToChain(chain);
    syncResult.finishAsync(
        this::onSyncComplete,
        error -> {
          LOG.error("Error encountered during sync", error);
          onSyncComplete(SyncResult.FAILED);
        },
        eventThread);
    return new InProgressSync(startSlot, chain, isFinalized, syncResult);
  }

  private class InProgressSync {
    private final UInt64 startSlot;
    private final TargetChain targetChain;
    private final boolean isFinalizedChain;
    private final SafeFuture<SyncResult> result;

    private InProgressSync(
        final UInt64 startSlot,
        final TargetChain targetChain,
        final boolean isFinalizedChain,
        final SafeFuture<SyncResult> result) {
      this.startSlot = startSlot;
      this.targetChain = targetChain;
      this.isFinalizedChain = isFinalizedChain;
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

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("startSlot", startSlot)
          .add("targetChain", targetChain.getChainHead())
          .add("isFinalizedChain", isFinalizedChain)
          .toString();
    }
  }
}
