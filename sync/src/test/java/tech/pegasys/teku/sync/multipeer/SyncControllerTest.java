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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.FutureUtil.ignoreFuture;
import static tech.pegasys.teku.sync.multipeer.chains.TargetChainTestUtil.chainWith;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

  private final TargetChains finalizedChains = new TargetChains();
  private final TargetChain targetChain = chainWith(dataStructureUtil.randomSlotAndBlockRoot());

  private final SyncController syncController =
      new SyncController(eventThread, recentChainData, finalizedChainSelector, finalizedSync);
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
    final SafeFuture<Void> syncResult = startFinalizedSync();

    assertThat(syncController.isSyncActive()).isTrue();

    syncResult.complete(null);

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

    when(finalizedChainSelector.selectTargetChain(finalizedChains))
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
    final SafeFuture<Void> previousSync = startFinalizedSync();

    assertThat(syncController.isSyncActive()).isTrue();

    when(finalizedChainSelector.selectTargetChain(finalizedChains)).thenReturn(Optional.empty());
    onTargetChainsUpdated();

    assertThat(syncController.isSyncActive()).isTrue();

    previousSync.complete(null);

    assertNotSyncing();
  }

  @Test
  void shouldNotifySubscribersWhenSyncStatusChanges() {
    final SyncSubscriber subscriber = mock(SyncSubscriber.class);
    syncController.subscribeToSyncChanges(subscriber);

    final SafeFuture<Void> syncResult = startFinalizedSync();

    verify(subscriber).onSyncingChange(true);

    syncResult.complete(null);

    verify(subscriber).onSyncingChange(false);
  }

  @Test
  void shouldNotNotifySubscribersAgainWhenSyncTargetChanges() {
    final SyncSubscriber subscriber = mock(SyncSubscriber.class);
    syncController.subscribeToSyncChanges(subscriber);
    ignoreFuture(startFinalizedSync());

    verify(subscriber).onSyncingChange(true);

    // Sync switches to a better chain
    final TargetChain newTargetChain = chainWith(dataStructureUtil.randomSlotAndBlockRoot());
    when(finalizedChainSelector.selectTargetChain(finalizedChains))
        .thenReturn(Optional.of(newTargetChain));
    when(finalizedSync.syncToChain(newTargetChain)).thenReturn(new SafeFuture<>());
    onTargetChainsUpdated();

    // But subscribers are not notified because we're already syncing.
    verifyNoMoreInteractions(subscriber);
  }

  private SafeFuture<Void> startFinalizedSync() {
    final SafeFuture<Void> syncResult = new SafeFuture<>();
    when(finalizedChainSelector.selectTargetChain(finalizedChains))
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
