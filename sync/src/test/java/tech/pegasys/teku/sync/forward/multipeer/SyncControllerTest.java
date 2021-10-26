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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.FutureUtil.ignoreFuture;
import static tech.pegasys.teku.sync.forward.multipeer.chains.TargetChainTestUtil.chainWith;

import java.util.Optional;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.eventthread.InlineEventThread;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.sync.events.SyncingStatus;
import tech.pegasys.teku.sync.forward.ForwardSync.SyncSubscriber;
import tech.pegasys.teku.sync.forward.multipeer.chains.TargetChain;

class SyncControllerTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final InlineEventThread eventThread = new InlineEventThread();
  private final Sync sync = mock(Sync.class);
  private final SyncTargetSelector syncTargetSelector = mock(SyncTargetSelector.class);
  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private final Executor subscriberExecutor = mock(Executor.class);
  private final EventLogger eventLogger = mock(EventLogger.class);

  private final TargetChain targetChain = chainWith(dataStructureUtil.randomSlotAndBlockRoot());

  private final SyncController syncController =
      new SyncController(
          eventThread, subscriberExecutor, recentChainData, syncTargetSelector, sync, eventLogger);
  private static final UInt64 HEAD_SLOT = UInt64.valueOf(2338);

  @BeforeEach
  void setUp() {
    when(recentChainData.getHeadSlot()).thenReturn(HEAD_SLOT);
  }

  @Test
  void shouldNotBeSyncingInitially() {
    assertNotSyncing();
  }

  @Test
  void shouldStartFinalizedSyncWhenTargetChainsUpdatedWithSuitableFinalizedChain() {
    ignoreFuture(startFinalizedSync());

    verify(sync).syncToChain(targetChain);

    assertThat(syncController.isSyncActive()).isTrue();
    assertThat(syncController.getSyncStatus())
        .isEqualTo(
            new SyncingStatus(true, HEAD_SLOT, HEAD_SLOT, targetChain.getChainHead().getSlot()));
  }

  @Test
  void shouldNotBeSyncingWhenSyncCompletes() {
    final SafeFuture<SyncResult> syncResult = startFinalizedSync();

    assertThat(syncController.isSyncActive()).isTrue();

    syncResult.complete(SyncResult.COMPLETE);

    assertNotSyncing();
  }

  @Test
  void shouldNotOverwriteCurrentSyncWhenChainsUpdatedButNoBetterChainAvailable() {
    ignoreFuture(startFinalizedSync());
    verify(sync).syncToChain(targetChain);

    // Updated but still the same best chain
    onTargetChainsUpdated();

    // Should not restart the sync
    verifyNoMoreInteractions(sync);
  }

  @Test
  void shouldSwitchSyncTargetWhenBetterChainAvailable() {
    final TargetChain newTargetChain = chainWith(dataStructureUtil.randomSlotAndBlockRoot());
    ignoreFuture(startFinalizedSync());
    verify(sync).syncToChain(targetChain);

    when(syncTargetSelector.selectSyncTarget(Optional.of(SyncTarget.finalizedTarget(targetChain))))
        .thenReturn(Optional.of(SyncTarget.finalizedTarget(newTargetChain)));
    when(sync.syncToChain(newTargetChain)).thenReturn(new SafeFuture<>());
    onTargetChainsUpdated();

    verify(sync).syncToChain(newTargetChain);

    assertThat(syncController.isSyncActive()).isTrue();
    assertThat(syncController.getSyncStatus())
        .isEqualTo(
            new SyncingStatus(true, HEAD_SLOT, HEAD_SLOT, newTargetChain.getChainHead().getSlot()));
  }

  @Test
  void shouldPassFailedSyncsToSyncTargetSelector() {
    // When a sync fails, we want to stay in sync mode and switch to another target chain even
    // if it's not much ahead of our current head.

    final SafeFuture<SyncResult> syncFuture = startFinalizedSync();

    // First selection has no current sync
    verify(syncTargetSelector).selectSyncTarget(Optional.empty());

    when(sync.syncToChain(targetChain)).thenReturn(new SafeFuture<>());
    syncFuture.complete(SyncResult.FAILED);

    verify(syncTargetSelector)
        .selectSyncTarget(Optional.of(SyncTarget.finalizedTarget(targetChain)));
    // Should restart sync to the chain even though it has the same target
    verify(sync, times(2)).syncToChain(targetChain);
  }

  @Test
  void shouldPassExceptionallyCompletedSyncsToSyncTargetSelector() {
    // When a sync fails, we want to stay in sync mode and switch to another target chain even
    // if it's not much ahead of our current head.

    final SafeFuture<SyncResult> syncFuture = startFinalizedSync();

    // First selection has no current sync
    verify(syncTargetSelector).selectSyncTarget(Optional.empty());

    when(sync.syncToChain(targetChain)).thenReturn(new SafeFuture<>());
    syncFuture.completeExceptionally(new RuntimeException());

    verify(syncTargetSelector)
        .selectSyncTarget(Optional.of(SyncTarget.finalizedTarget(targetChain)));
    // Should restart sync to the chain even though it has the same target
    verify(sync, times(2)).syncToChain(targetChain);
  }

  @Test
  void shouldRemainSyncingWhenNoTargetChainSelectedButPreviousSyncStillActive() {
    final SafeFuture<SyncResult> previousSync = startFinalizedSync();

    assertThat(syncController.isSyncActive()).isTrue();

    when(syncTargetSelector.selectSyncTarget(Optional.of(SyncTarget.finalizedTarget(targetChain))))
        .thenReturn(Optional.empty());
    onTargetChainsUpdated();

    assertThat(syncController.isSyncActive()).isTrue();

    previousSync.complete(SyncResult.COMPLETE);

    assertNotSyncing();
  }

  @Test
  void shouldNotifySubscribersWhenSyncStatusChanges() {
    final SyncSubscriber subscriber = mock(SyncSubscriber.class);
    syncController.subscribeToSyncChanges(subscriber);

    final SafeFuture<SyncResult> syncResult = startFinalizedSync();

    assertSyncSubscriberNotified(subscriber, true);

    syncResult.complete(SyncResult.COMPLETE);

    assertSyncSubscriberNotified(subscriber, false);
  }

  @Test
  void shouldNotNotifySubscribersAgainWhenSyncTargetChanges() {
    final SyncSubscriber subscriber = mock(SyncSubscriber.class);
    syncController.subscribeToSyncChanges(subscriber);
    final SafeFuture<SyncResult> previousSync = startFinalizedSync();

    assertSyncSubscriberNotified(subscriber, true);

    // Sync switches to a better chain
    final TargetChain newTargetChain = chainWith(dataStructureUtil.randomSlotAndBlockRoot());
    when(syncTargetSelector.selectSyncTarget(Optional.of(SyncTarget.finalizedTarget(targetChain))))
        .thenReturn(Optional.of(SyncTarget.finalizedTarget(targetChain)));
    when(sync.syncToChain(newTargetChain))
        .thenAnswer(
            invocation -> {
              previousSync.complete(SyncResult.TARGET_CHANGED);
              return new SafeFuture<>();
            });
    onTargetChainsUpdated();

    // But subscribers are not notified because we're already syncing.
    verifyNoMoreInteractions(subscriberExecutor);
    verifyNoMoreInteractions(subscriber);
  }

  @Test
  void shouldStartNonFinalizedSyncWhenNoSuitableFinalizedTargetChainAvailable() {
    when(syncTargetSelector.selectSyncTarget(Optional.empty()))
        .thenReturn(Optional.of(SyncTarget.nonfinalizedTarget(targetChain)));

    when(sync.syncToChain(targetChain)).thenReturn(new SafeFuture<>());

    onTargetChainsUpdated();

    ignoreFuture(verify(sync).syncToChain(targetChain));
  }

  @Test
  void shouldExecuteCompleteSyncMessage() {
    final SafeFuture<SyncResult> syncResult = startFinalizedSync();

    assertThat(syncController.isSyncActive()).isTrue();
    verify(eventLogger, never()).syncCompleted();
    syncResult.complete(SyncResult.COMPLETE);
    assertNotSyncing();
    verify(eventLogger, times(1)).syncCompleted();
  }

  @Test
  void shouldNotNotifySubscribersWhenRunningSpeculativeTarget() {
    final SyncSubscriber subscriber = mock(SyncSubscriber.class);
    syncController.subscribeToSyncChanges(subscriber);

    final SafeFuture<SyncResult> syncResult = new SafeFuture<>();
    when(syncTargetSelector.selectSyncTarget(any()))
        .thenReturn(Optional.of(SyncTarget.speculativeTarget(targetChain)));
    when(sync.syncToChain(targetChain)).thenReturn(syncResult);

    onTargetChainsUpdated();
    syncResult.complete(SyncResult.COMPLETE);

    verify(subscriberExecutor, never()).execute(any());
    verify(eventLogger, never()).syncCompleted();
  }

  @Test
  void shouldExecuteStartSyncMessage() {
    final SyncSubscriber subscriber = mock(SyncSubscriber.class);
    syncController.subscribeToSyncChanges(subscriber);
    verify(eventLogger, never()).syncStart();
    ignoreFuture(startFinalizedSync());
    verify(eventLogger, times(1)).syncStart();
  }

  private void assertSyncSubscriberNotified(
      final SyncSubscriber subscriber, final boolean syncing) {
    // Shouldn't notify on the event thread
    verifyNoMoreInteractions(subscriber);

    // Notification happens via the subscriberExecutor
    final ArgumentCaptor<Runnable> notificationCaptor = ArgumentCaptor.forClass(Runnable.class);
    verify(subscriberExecutor, atLeastOnce()).execute(notificationCaptor.capture());
    notificationCaptor.getValue().run();

    verify(subscriber).onSyncingChange(syncing);
  }

  private SafeFuture<SyncResult> startFinalizedSync() {
    final SafeFuture<SyncResult> syncResult = new SafeFuture<>();
    when(syncTargetSelector.selectSyncTarget(any()))
        .thenReturn(Optional.of(SyncTarget.finalizedTarget(targetChain)));
    when(sync.syncToChain(targetChain)).thenReturn(syncResult);
    onTargetChainsUpdated();
    return syncResult;
  }

  private void onTargetChainsUpdated() {
    eventThread.execute(syncController::onTargetChainsUpdated);
  }

  private void assertNotSyncing() {
    assertThat(syncController.isSyncActive()).isFalse();
    assertThat(syncController.getSyncStatus()).isEqualTo(new SyncingStatus(false, HEAD_SLOT));
  }
}
