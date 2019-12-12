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

package tech.pegasys.artemis.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.primitives.UnsignedLong;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.GoodbyeMessage;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.StatusMessage;
import tech.pegasys.artemis.datastructures.state.Fork;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.networking.eth2.Eth2Network;
import tech.pegasys.artemis.networking.eth2.peers.Eth2Peer;
import tech.pegasys.artemis.networking.eth2.peers.Eth2Peer.StatusData;
import tech.pegasys.artemis.networking.eth2.rpc.core.InvalidResponseException;
import tech.pegasys.artemis.networking.eth2.rpc.core.ResponseStream.ResponseListener;
import tech.pegasys.artemis.statetransition.BlockImporter;
import tech.pegasys.artemis.statetransition.StateTransitionException;
import tech.pegasys.artemis.storage.ChainStorageClient;

public class SyncManagerTest {

  private static final BeaconBlock BLOCK = DataStructureUtil.randomBeaconBlock(1, 100);
  private ChainStorageClient storageClient = mock(ChainStorageClient.class);
  private Eth2Network network = mock(Eth2Network.class);
  private BlockImporter blockImporter = mock(BlockImporter.class);
  private SyncManager syncManager = new SyncManager(network, storageClient, blockImporter);
  private final Eth2Peer peer = mock(Eth2Peer.class);
  private static final Bytes32 PEER_HEAD_BLOCK_ROOT = Bytes32.fromHexString("0x1234");
  private static final UnsignedLong PEER_HEAD_SLOT = UnsignedLong.valueOf(20);
  private static final UnsignedLong PEER_FINALIZED_EPOCH = UnsignedLong.valueOf(3);
  private static final StatusData PEER_STATUS =
      StatusData.fromStatusMessage(
          new StatusMessage(
              Fork.VERSION_ZERO,
              Bytes32.ZERO,
              PEER_FINALIZED_EPOCH,
              PEER_HEAD_BLOCK_ROOT,
              PEER_HEAD_SLOT));

  @SuppressWarnings("unchecked")
  private final ArgumentCaptor<ResponseListener<BeaconBlock>> responseListenerArgumentCaptor =
      ArgumentCaptor.forClass(ResponseListener.class);

  @BeforeEach
  public void setUp() {
    when(storageClient.getFinalizedEpoch()).thenReturn(UnsignedLong.ZERO);
    when(peer.getStatus()).thenReturn(PEER_STATUS);
    when(peer.sendGoodbye(any())).thenReturn(new CompletableFuture<>());
  }

  @Test
  void sync_noPeers() throws Exception {
    when(network.streamPeers()).thenReturn(Stream.empty());
    // Should be immediately completed as there is nothing to do.
    assertThat(syncManager.sync()).isCompleted();
  }

  @Test
  void sync_correct() throws StateTransitionException {
    when(network.streamPeers()).thenReturn(Stream.of(peer));

    final CompletableFuture<Void> requestFuture = new CompletableFuture<>();
    when(peer.requestBlocksByRange(any(), any(), any(), any(), any())).thenReturn(requestFuture);

    final CompletableFuture<Void> syncFuture = syncManager.sync();
    assertThat(syncFuture).isNotDone();

    verify(peer)
        .requestBlocksByRange(
            eq(PEER_HEAD_BLOCK_ROOT),
            eq(UnsignedLong.ZERO),
            eq(PEER_HEAD_SLOT),
            eq(UnsignedLong.ONE),
            responseListenerArgumentCaptor.capture());

    // Respond with blocks and check they're passed to the block importer.
    final ResponseListener<BeaconBlock> responseListener =
        responseListenerArgumentCaptor.getValue();
    responseListener.onResponse(BLOCK);
    verify(blockImporter).importBlock(BLOCK);
    assertThat(syncFuture).isNotDone();

    // Now that we've imported the block, our finalized epoch has updated.
    when(storageClient.getFinalizedEpoch()).thenReturn(PEER_HEAD_SLOT);

    // Signal the request for data from the peer is complete.
    requestFuture.complete(null);

    // Check that the sync is done and the peer was not disconnected.
    assertThat(syncFuture).isDone();
    verify(peer, never()).sendGoodbye(any());
  }

  @Test
  void sync_badAdvertisedFinalizedEpoch() throws StateTransitionException {
    when(network.streamPeers()).thenReturn(Stream.of(peer));

    final CompletableFuture<Void> requestFuture = new CompletableFuture<>();
    when(peer.requestBlocksByRange(any(), any(), any(), any(), any())).thenReturn(requestFuture);

    final CompletableFuture<Void> syncFuture = syncManager.sync();
    assertThat(syncFuture).isNotDone();

    verify(peer)
        .requestBlocksByRange(
            eq(PEER_HEAD_BLOCK_ROOT),
            eq(UnsignedLong.ZERO),
            eq(PEER_HEAD_SLOT),
            eq(UnsignedLong.ONE),
            responseListenerArgumentCaptor.capture());

    // Respond with blocks and check they're passed to the block importer.

    final ResponseListener<BeaconBlock> responseListener =
        responseListenerArgumentCaptor.getValue();
    responseListener.onResponse(BLOCK);
    verify(blockImporter).importBlock(BLOCK);
    assertThat(syncFuture).isNotDone();

    // Now that we've imported the block, our finalized epoch has updated but hasn't reached what
    // the peer claimed
    when(storageClient.getFinalizedEpoch())
        .thenReturn(PEER_FINALIZED_EPOCH.minus(UnsignedLong.ONE));

    // Signal the request for data from the peer is complete.
    requestFuture.complete(null);

    // Check that the sync is done and the peer was not disconnected.
    assertThat(syncFuture).isCompleted();
    verify(peer).sendGoodbye(GoodbyeMessage.REASON_FAULT_ERROR);
  }

  @Test
  void sync_badBlock() throws StateTransitionException {
    when(network.streamPeers()).thenReturn(Stream.of(peer));

    final CompletableFuture<Void> requestFuture = new CompletableFuture<>();
    when(peer.requestBlocksByRange(any(), any(), any(), any(), any())).thenReturn(requestFuture);

    final CompletableFuture<Void> syncFuture = syncManager.sync();
    assertThat(syncFuture).isNotDone();

    verify(peer)
        .requestBlocksByRange(
            eq(PEER_HEAD_BLOCK_ROOT),
            eq(UnsignedLong.ZERO),
            eq(PEER_HEAD_SLOT),
            eq(UnsignedLong.ONE),
            responseListenerArgumentCaptor.capture());

    // Respond with blocks and check they're passed to the block importer.
    final ResponseListener<BeaconBlock> responseListener =
        responseListenerArgumentCaptor.getValue();

    // Importing the returned block fails
    doThrow(new StateTransitionException("Bad block")).when(blockImporter).importBlock(BLOCK);
    // Probably want to have a specific exception type to indicate bad data.
    try {
      responseListener.onResponse(BLOCK);
      fail("Should have thrown an error to indicate the response was bad");
    } catch (final InvalidResponseException e) {
      // RpcMessageHandler will consider the request complete if there's an error processing a
      // response
      requestFuture.completeExceptionally(e);
    }

    // Should disconnect the peer and consider the sync complete.
    verify(peer).sendGoodbye(GoodbyeMessage.REASON_FAULT_ERROR);
    assertThat(syncFuture).isCompletedExceptionally();
  }
}
