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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.network.p2p.peer.StubPeer;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.networking.p2p.peer.PeerConnectedSubscriber;
import tech.pegasys.teku.sync.forward.ForwardSync;
import tech.pegasys.teku.sync.forward.ForwardSync.SyncSubscriber;

class SyncStateTrackerTest {

  public static final int STARTUP_TARGET_PEER_COUNT = 5;
  public static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(10);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();

  @SuppressWarnings("unchecked")
  private final P2PNetwork<Peer> network = mock(P2PNetwork.class);

  private final ForwardSync syncService = mock(ForwardSync.class);

  private final EventLogger eventLogger = mock(EventLogger.class);

  private final SyncStateTracker tracker =
      new SyncStateTracker(
          asyncRunner,
          syncService,
          network,
          STARTUP_TARGET_PEER_COUNT,
          STARTUP_TIMEOUT,
          eventLogger);
  private SyncSubscriber syncSubscriber;
  private PeerConnectedSubscriber<Peer> peerSubscriber;

  @BeforeEach
  public void setUp() {
    assertThat(tracker.start()).isCompleted();

    final ArgumentCaptor<SyncSubscriber> syncSubscriberArgumentCaptor =
        ArgumentCaptor.forClass(SyncSubscriber.class);
    verify(syncService).subscribeToSyncChanges(syncSubscriberArgumentCaptor.capture());
    syncSubscriber = syncSubscriberArgumentCaptor.getValue();

    @SuppressWarnings("unchecked")
    final ArgumentCaptor<PeerConnectedSubscriber<Peer>> peerSubscriberArgumentCaptor =
        ArgumentCaptor.forClass(PeerConnectedSubscriber.class);
    verify(network).subscribeConnect(peerSubscriberArgumentCaptor.capture());
    peerSubscriber = peerSubscriberArgumentCaptor.getValue();
  }

  @Test
  public void shouldStartInStartupState() {
    assertSyncState(SyncState.START_UP);
  }

  @Test
  public void shouldStartInSyncWhenTargetPeerCountIsZero() {
    final SyncStateProvider tracker =
        new SyncStateTracker(asyncRunner, syncService, network, 0, STARTUP_TIMEOUT);
    assertThat(tracker.getCurrentSyncState()).isEqualTo(SyncState.IN_SYNC);
  }

  @Test
  public void shouldStartInSyncWhenStartupTimeoutIsZero() {
    final SyncStateProvider tracker =
        new SyncStateTracker(
            asyncRunner, syncService, network, STARTUP_TARGET_PEER_COUNT, Duration.ofSeconds(0));
    assertThat(tracker.getCurrentSyncState()).isEqualTo(SyncState.IN_SYNC);
  }

  @Test
  public void shouldBeSyncingWhenSyncStarts() {
    syncSubscriber.onSyncingChange(true);
    assertSyncState(SyncState.SYNCING);
    verify(eventLogger).syncStart();
    verifyNoMoreInteractions(eventLogger);
  }

  @Test
  public void shouldReturnToStartupModeWhenSyncCompletesIfPeerRequirementNotMet() {
    syncSubscriber.onSyncingChange(true);
    assertSyncState(SyncState.SYNCING);
    verify(eventLogger).syncStart();
    syncSubscriber.onSyncingChange(false);
    assertSyncState(SyncState.START_UP);
    verify(eventLogger).syncCompleted();
    verifyNoMoreInteractions(eventLogger);
  }

  @Test
  public void shouldNotLogOptimisticMessagesOnStartUp() {
    // initialize with optimistic
    tracker.onOptimisticHeadChanged(true);

    // start syncing
    syncSubscriber.onSyncingChange(true);
    assertSyncState(SyncState.SYNCING);
    verify(eventLogger).syncStart();

    // head no more optimistic
    tracker.onOptimisticHeadChanged(false);

    // in sync
    syncSubscriber.onSyncingChange(false);
    verify(eventLogger).syncCompleted();
    assertSyncState(SyncState.START_UP);

    // turn head optimistic
    tracker.onOptimisticHeadChanged(true);
    assertSyncState(SyncState.SYNCING);

    verifyNoMoreInteractions(eventLogger);
  }

  @Test
  public void shouldLogCorrectSequenceOfSyncEvents() {
    // start syncing
    syncSubscriber.onSyncingChange(true);
    assertSyncState(SyncState.SYNCING);
    verify(eventLogger).syncStart();

    // get connected
    when(network.getPeerCount()).thenReturn(STARTUP_TARGET_PEER_COUNT);
    peerSubscriber.onConnected(new StubPeer());

    // turn head optimistic
    tracker.onOptimisticHeadChanged(true);
    verify(eventLogger).headTurnedOptimisticWhileSyncing();

    // beacon synced while head is optimistic
    syncSubscriber.onSyncingChange(false);
    verify(eventLogger).syncCompletedWhileHeadIsOptimistic();

    // head no longer optimistic
    tracker.onOptimisticHeadChanged(false);
    assertSyncState(SyncState.IN_SYNC);
    verify(eventLogger).syncCompleted();

    verifyNoMoreInteractions(eventLogger);
  }

  @Test
  void shouldReturnToInSyncAfterSyncCompletesIfStartupModeIsDisabled() {
    reset(syncService);
    final SyncStateTracker tracker =
        new SyncStateTracker(
            asyncRunner, syncService, network, STARTUP_TARGET_PEER_COUNT, Duration.ofSeconds(0));
    tracker.start().join();

    final ArgumentCaptor<SyncSubscriber> syncSubscriberArgumentCaptor =
        ArgumentCaptor.forClass(SyncSubscriber.class);
    verify(syncService).subscribeToSyncChanges(syncSubscriberArgumentCaptor.capture());
    syncSubscriber = syncSubscriberArgumentCaptor.getValue();

    assertThat(tracker.getCurrentSyncState()).isEqualTo(SyncState.IN_SYNC);
    syncSubscriber.onSyncingChange(true);
    assertThat(tracker.getCurrentSyncState()).isEqualTo(SyncState.SYNCING);
    syncSubscriber.onSyncingChange(false);
    assertThat(tracker.getCurrentSyncState()).isEqualTo(SyncState.IN_SYNC);
  }

  @Test
  public void shouldBeInSyncWhenSyncCompletesIfPeerRequirementMet() {
    syncSubscriber.onSyncingChange(true);
    assertSyncState(SyncState.SYNCING);
    when(network.getPeerCount()).thenReturn(STARTUP_TARGET_PEER_COUNT);
    peerSubscriber.onConnected(new StubPeer());
    syncSubscriber.onSyncingChange(false);
    assertSyncState(SyncState.IN_SYNC);
  }

  @Test
  public void shouldBeInSyncWhenSyncCompletesIfTimeoutReached() {
    syncSubscriber.onSyncingChange(true);
    assertSyncState(SyncState.SYNCING);
    asyncRunner.executeQueuedActions();
    syncSubscriber.onSyncingChange(false);
    assertSyncState(SyncState.IN_SYNC);
  }

  @Test
  public void shouldBeInSyncWhenTargetPeerCountReachedAndSyncNotStarted() {
    when(network.getPeerCount()).thenReturn(STARTUP_TARGET_PEER_COUNT);
    peerSubscriber.onConnected(new StubPeer());
    assertSyncState(SyncState.IN_SYNC);
    verifyNoInteractions(eventLogger);
  }

  @Test
  public void shouldRemainInStartupUntilPeerCountIsReached() {
    when(network.getPeerCount()).thenReturn(1);
    peerSubscriber.onConnected(new StubPeer());
    assertSyncState(SyncState.START_UP);
  }

  @Test
  public void shouldCompleteStartupWhenTimeoutIsReached() {
    asyncRunner.executeQueuedActions();
    assertSyncState(SyncState.IN_SYNC);
  }

  private void assertSyncState(final SyncState syncing) {
    assertThat(tracker.getCurrentSyncState()).isEqualTo(syncing);
  }
}
