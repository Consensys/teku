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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.FutureUtil.ignoreFuture;
import static tech.pegasys.teku.sync.multipeer.chains.TargetChainTestUtil.chainWith;

import java.util.Optional;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.eventthread.InlineEventThread;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.sync.SyncService.SyncSubscriber;
import tech.pegasys.teku.sync.SyncingStatus;
import tech.pegasys.teku.sync.multipeer.chains.TargetChain;
import tech.pegasys.teku.sync.multipeer.chains.TargetChains;

class SyncControllerTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final InlineEventThread eventThread = new InlineEventThread();
  private final ChainSelector finalizedChainSelector = mock(ChainSelector.class);
  private final Sync finalizedSync = mock(Sync.class);
  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private final Executor subscriberExecutor = mock(Executor.class);

  private final TargetChains finalizedChains = new TargetChains();
  private final TargetChain targetChain = chainWith(dataStructureUtil.randomSlotAndBlockRoot());

  private final SyncController syncController =
      new SyncController(
          eventThread, subscriberExecutor, recentChainData, finalizedChainSelector, finalizedSync);
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

    verify(finalizedSync).syncToChain(targetChain);

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
    verify(finalizedSync).syncToChain(targetChain);

    // Updated but still the same best chain
    onTargetChainsUpdated();

    // Should not restart the sync
    verifyNoMoreInteractions(finalizedSync);
  }

  @Test
  void shouldSwitchSyncTargetWhenBetterChainAvailable() {
    final TargetChain newTargetChain = chainWith(dataStructureUtil.randomSlotAndBlockRoot());
    ignoreFuture(startFinalizedSync());
    verify(finalizedSync).syncToChain(targetChain);

    when(finalizedChainSelector.selectTargetChain(finalizedChains, true))
        .thenReturn(Optional.of(newTargetChain));
    when(finalizedSync.syncToChain(newTargetChain)).thenReturn(new SafeFuture<>());
    onTargetChainsUpdated();

    verify(finalizedSync).syncToChain(newTargetChain);

    assertThat(syncController.isSyncActive()).isTrue();
    assertThat(syncController.getSyncStatus())
        .isEqualTo(
            new SyncingStatus(true, HEAD_SLOT, HEAD_SLOT, newTargetChain.getChainHead().getSlot()));
  }

  @Test
  void shouldRemainSyncingWhenNoTargetChainSelectedButPreviousSyncStillActive() {
    final SafeFuture<SyncResult> previousSync = startFinalizedSync();

    assertThat(syncController.isSyncActive()).isTrue();

    when(finalizedChainSelector.selectTargetChain(finalizedChains, true))
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
    when(finalizedChainSelector.selectTargetChain(finalizedChains, true))
        .thenReturn(Optional.of(newTargetChain));
    when(finalizedSync.syncToChain(newTargetChain))
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
    when(finalizedChainSelector.selectTargetChain(eq(finalizedChains), anyBoolean()))
        .thenReturn(Optional.of(targetChain));
    when(finalizedSync.syncToChain(targetChain)).thenReturn(syncResult);
    onTargetChainsUpdated();
    return syncResult;
  }

  private void onTargetChainsUpdated() {
    eventThread.execute(() -> syncController.onTargetChainsUpdated(finalizedChains));
  }

  private void assertNotSyncing() {
    assertThat(syncController.isSyncActive()).isFalse();
    assertThat(syncController.getSyncStatus()).isEqualTo(new SyncingStatus(false, HEAD_SLOT));
  }
}
