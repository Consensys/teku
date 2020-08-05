/*
 * Copyright 2019 ConsenSys AG.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.common.primitives.UnsignedLong;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.StatusMessage;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.networking.eth2.Eth2Network;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.peers.PeerStatus;
import tech.pegasys.teku.networking.p2p.peer.PeerConnectedSubscriber;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.sync.SyncService.SyncSubscriber;
import tech.pegasys.teku.util.config.Constants;

public class SyncManagerTest {

  private static final long SUBSCRIPTION_ID = 3423;
  private static final Bytes32 PEER_HEAD_BLOCK_ROOT = Bytes32.fromHexString("0x1234");
  private static final UnsignedLong PEER_FINALIZED_EPOCH = UnsignedLong.valueOf(3);
  private static final UnsignedLong PEER_HEAD_SLOT =
      UnsignedLong.valueOf(Constants.SLOTS_PER_EPOCH * 5);
  private static final PeerStatus PEER_STATUS =
      PeerStatus.fromStatusMessage(
          new StatusMessage(
              Constants.GENESIS_FORK_VERSION,
              Bytes32.ZERO,
              PEER_FINALIZED_EPOCH,
              PEER_HEAD_BLOCK_ROOT,
              PEER_HEAD_SLOT));

  private RecentChainData storageClient = mock(RecentChainData.class);
  private Eth2Network network = mock(Eth2Network.class);
  private final PeerSync peerSync = mock(PeerSync.class);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private SyncManager syncManager = new SyncManager(asyncRunner, network, storageClient, peerSync);
  private final Eth2Peer peer = mock(Eth2Peer.class);
  private final SyncSubscriber syncSubscriber = mock(SyncSubscriber.class);

  @SuppressWarnings("unchecked")
  private final ArgumentCaptor<PeerConnectedSubscriber<Eth2Peer>> onConnectionListener =
      ArgumentCaptor.forClass(PeerConnectedSubscriber.class);

  @BeforeEach
  public void setUp() {
    when(network.subscribeConnect(any())).thenReturn(SUBSCRIPTION_ID);
    when(storageClient.getFinalizedEpoch()).thenReturn(UnsignedLong.ZERO);
    when(peer.getStatus()).thenReturn(PEER_STATUS);
  }

  @Test
  void sync_noPeers() {
    when(network.streamPeers()).thenReturn(Stream.empty());
    // Should be immediately completed as there is nothing to do.
    assertThat(syncManager.start()).isCompleted();
    assertThat(syncManager.isSyncActive()).isFalse();
    assertThat(syncManager.isSyncQueued()).isFalse();
    verifyNoInteractions(peerSync);
  }

  @Test
  void sync_noSuitablePeers() {
    // We're already in sync with the peer
    when(storageClient.getFinalizedEpoch()).thenReturn(PEER_STATUS.getFinalizedEpoch());
    when(storageClient.getBestSlot()).thenReturn(PEER_STATUS.getHeadSlot());
    when(network.streamPeers()).thenReturn(Stream.of(peer));
    // Should be immediately completed as there is nothing to do.
    assertThat(syncManager.start()).isCompleted();
    assertThat(syncManager.isSyncActive()).isFalse();
    assertThat(syncManager.isSyncQueued()).isFalse();
    verifyNoInteractions(peerSync);
  }

  @Test
  void sync_existingPeers() {
    when(network.streamPeers()).thenReturn(Stream.of(peer));

    final SafeFuture<PeerSyncResult> syncFuture = new SafeFuture<>();
    when(peerSync.sync(peer)).thenReturn(syncFuture);

    assertThat(syncManager.start()).isCompleted();
    assertThat(syncManager.isSyncActive()).isTrue();
    assertThat(syncManager.isSyncQueued()).isFalse();

    verify(peerSync).sync(peer);

    // Signal the peer sync is complete
    syncFuture.complete(PeerSyncResult.SUCCESSFUL_SYNC);

    // Check that the sync is done and the peer was not disconnected.
    assertThat(syncManager.isSyncActive()).isFalse();
    assertThat(syncManager.isSyncQueued()).isFalse();
  }

  @Test
  void sync_existingPeerWithSameFinalizedEpochButMuchBetterHeadSlot() {
    when(network.streamPeers()).thenReturn(Stream.of(peer));
    when(storageClient.getFinalizedEpoch()).thenReturn(PEER_STATUS.getFinalizedEpoch());
    when(storageClient.getBestSlot())
        .thenReturn(
            PEER_STATUS.getHeadSlot().minus(UnsignedLong.valueOf(Constants.SLOTS_PER_EPOCH + 1)));

    final SafeFuture<PeerSyncResult> syncFuture = new SafeFuture<>();
    when(peerSync.sync(peer)).thenReturn(syncFuture);

    assertThat(syncManager.start()).isCompleted();
    assertThat(syncManager.isSyncActive()).isTrue();
    assertThat(syncManager.isSyncQueued()).isFalse();

    verify(peerSync).sync(peer);

    // Signal the peer sync is complete
    syncFuture.complete(PeerSyncResult.SUCCESSFUL_SYNC);

    // Check that the sync is done and the peer was not disconnected.
    assertThat(syncManager.isSyncActive()).isFalse();
    assertThat(syncManager.isSyncQueued()).isFalse();
  }

  @Test
  void sync_retrySyncIfNotSuccessful() {
    when(network.streamPeers()).thenReturn(Stream.of(peer));

    final SafeFuture<PeerSyncResult> syncFuture = new SafeFuture<>();
    when(peerSync.sync(peer)).thenReturn(syncFuture);

    assertThat(syncManager.start()).isCompleted();
    assertThat(syncManager.isSyncActive()).isTrue();
    assertThat(syncManager.isSyncQueued()).isFalse();

    verify(peerSync).sync(peer);

    // The sync didn't complete correctly so we should start a new one with a new peer
    final Eth2Peer peer2 = mock(Eth2Peer.class);
    when(peer2.getStatus()).thenReturn(PEER_STATUS);
    when(network.streamPeers()).thenReturn(Stream.of(peer2));
    when(peerSync.sync(peer2)).thenReturn(new SafeFuture<>());
    syncFuture.complete(PeerSyncResult.FAULTY_ADVERTISEMENT);

    asyncRunner.executeQueuedActions();
    verify(peerSync).sync(peer2);
    assertThat(syncManager.isSyncActive()).isTrue();
    assertThat(syncManager.isSyncQueued()).isFalse();
  }

  @Test
  void sync_newPeer() {
    assertThat(syncManager.start()).isCompleted();
    // No peers initially so sync doesn't start.
    assertThat(syncManager.isSyncActive()).isFalse();
    assertThat(syncManager.isSyncQueued()).isFalse();

    verify(network).subscribeConnect(onConnectionListener.capture());
    final PeerConnectedSubscriber<Eth2Peer> subscriber = onConnectionListener.getValue();

    final SafeFuture<PeerSyncResult> syncFuture1 = new SafeFuture<>();
    final SafeFuture<PeerSyncResult> syncFuture2 = new SafeFuture<>();
    when(peerSync.sync(peer)).thenReturn(syncFuture1);

    when(network.streamPeers()).thenReturn(Stream.of(peer));
    subscriber.onConnected(peer);

    // Sync is activated by first peer joining.
    verify(peerSync).sync(peer);
    assertThat(syncManager.isSyncActive()).isTrue();
    assertThat(syncManager.isSyncQueued()).isFalse();

    // Second peer connecting causes another sync to be scheduled.
    final Eth2Peer peer2 = mock(Eth2Peer.class);
    when(peer2.getStatus()).thenReturn(PEER_STATUS);
    when(network.streamPeers()).thenReturn(Stream.of(peer2));
    when(peerSync.sync(peer2)).thenReturn(syncFuture2);

    subscriber.onConnected(peer2);
    assertThat(syncManager.isSyncActive()).isTrue();
    assertThat(syncManager.isSyncQueued()).isTrue();

    // First sync completes and should kick off the second sync.
    syncFuture1.complete(PeerSyncResult.SUCCESSFUL_SYNC);
    verify(peerSync).sync(peer2);
    assertThat(syncManager.isSyncActive()).isTrue();
    assertThat(syncManager.isSyncQueued()).isFalse();

    // Stop syncing when second sync completes.
    syncFuture2.complete(PeerSyncResult.SUCCESSFUL_SYNC);
    assertThat(syncManager.isSyncActive()).isFalse();
    assertThat(syncManager.isSyncQueued()).isFalse();
  }

  @Test
  void stop_shouldStopPeerSyncAndRemoveListener() {
    assertThat(syncManager.start()).isCompleted();

    assertThat(syncManager.stop()).isCompleted();
    assertThat(syncManager.isSyncQueued()).isFalse();
    assertThat(syncManager.isSyncActive()).isFalse();
    verify(peerSync).stop();
    verify(network).unsubscribeConnect(SUBSCRIPTION_ID);
  }

  @Test
  void sync_syncStatus() {
    // stream needs to be used more than once
    when(network.streamPeers()).then(i -> Stream.of(peer));

    final SafeFuture<PeerSyncResult> syncFuture = new SafeFuture<>();
    when(peerSync.sync(peer)).thenReturn(syncFuture);
    UnsignedLong startingSlot = UnsignedLong.valueOf(11);
    when(peerSync.getStartingSlot()).thenReturn(startingSlot);

    assertThat(syncManager.start()).isCompleted();
    assertThat(syncManager.isSyncActive()).isTrue();

    UnsignedLong currentSlot = UnsignedLong.valueOf(17);
    when(storageClient.getBestSlot()).thenReturn(currentSlot);

    SyncStatus syncStatus = syncManager.getSyncStatus().getSyncStatus();
    assertThat(syncStatus.getCurrentSlot()).isEqualTo(currentSlot);
    assertThat(syncStatus.getStartingSlot()).isEqualTo(startingSlot);
    assertThat(syncStatus.getHighestSlot()).isEqualTo(PEER_HEAD_SLOT);

    assertThat(syncManager.isSyncQueued()).isFalse();

    verify(peerSync).sync(peer);
  }

  @Test
  void sync_isSyncing_noPeers() {
    when(network.streamPeers()).thenReturn(Stream.empty());
    // Should be immediately completed as there is nothing to do.
    assertThat(syncManager.start()).isCompleted();
    assertThat(syncManager.isSyncActive()).isFalse();
    assertThat(syncManager.isSyncQueued()).isFalse();
    verifyNoInteractions(peerSync);

    // verify that getSyncStatus completes even when no peers
    assertThat(syncManager.getSyncStatus().getSyncStatus()).isNull();
    assertThat(syncManager.isSyncQueued()).isFalse();
  }

  @Test
  void subscribeToSyncChanges_notifiedWhenFirstSyncStarts() {
    syncManager.subscribeToSyncChanges(syncSubscriber);

    when(network.streamPeers()).then(i -> Stream.of(peer));

    when(peerSync.sync(peer)).thenReturn(new SafeFuture<>());
    assertThat(syncManager.start()).isCompleted();

    verify(syncSubscriber).onSyncingChange(true);
    verifyNoMoreInteractions(syncSubscriber);
  }

  @Test
  void subscribeToSyncChanges_notifiedWhenSyncCompletes() {
    syncManager.subscribeToSyncChanges(syncSubscriber);

    when(network.streamPeers()).then(i -> Stream.of(peer));

    final SafeFuture<PeerSyncResult> syncFuture = new SafeFuture<>();
    when(peerSync.sync(peer)).thenReturn(syncFuture);
    assertThat(syncManager.start()).isCompleted();
    verify(syncSubscriber).onSyncingChange(true);
    verifyNoMoreInteractions(syncSubscriber);

    syncFuture.complete(PeerSyncResult.SUCCESSFUL_SYNC);
    verify(syncSubscriber).onSyncingChange(false);
    verifyNoMoreInteractions(syncSubscriber);
  }

  @Test
  void subscribeToSyncChanges_notNotifiedWhenSyncCompletesAndImmediatelyStartsAgain() {
    syncManager.subscribeToSyncChanges(syncSubscriber);

    when(network.streamPeers()).then(i -> Stream.of(peer));

    final SafeFuture<PeerSyncResult> sync1Future = new SafeFuture<>();
    final SafeFuture<PeerSyncResult> sync2Future = new SafeFuture<>();
    when(peerSync.sync(peer)).thenReturn(sync1Future).thenReturn(sync2Future);
    assertThat(syncManager.start()).isCompleted();
    verify(syncSubscriber).onSyncingChange(true);
    verifyNoMoreInteractions(syncSubscriber);

    verify(network).subscribeConnect(onConnectionListener.capture());
    final PeerConnectedSubscriber<Eth2Peer> peerConnectedSubscriber =
        onConnectionListener.getValue();

    // Another peer connects while we're syncing to the first queuing up another sync.
    peerConnectedSubscriber.onConnected(peer);

    // The first sync completes but we should immediately start syncing to the second peer.
    sync1Future.complete(PeerSyncResult.SUCCESSFUL_SYNC);
    assertThat(syncManager.isSyncActive()).isTrue();
    verifyNoMoreInteractions(syncSubscriber);
  }

  @Test
  void subscribeToSyncChanges_notNotifiedWhenSyncFailsToFindPeersToSyncTo() {
    syncManager.subscribeToSyncChanges(syncSubscriber);

    when(network.streamPeers()).thenReturn(Stream.empty());

    final SafeFuture<PeerSyncResult> sync1Future = new SafeFuture<>();
    when(peerSync.sync(peer)).thenReturn(sync1Future);
    assertThat(syncManager.start()).isCompleted();
    verifyNoInteractions(syncSubscriber);
  }
}
