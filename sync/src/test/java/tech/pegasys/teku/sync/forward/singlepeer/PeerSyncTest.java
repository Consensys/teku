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

package tech.pegasys.teku.sync.forward.singlepeer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;
import static tech.pegasys.teku.util.config.Constants.MAX_BLOCK_BY_RANGE_REQUEST_SIZE;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.function.Supplier;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.OngoingStubbing;
import tech.pegasys.teku.core.StateTransitionException;
import tech.pegasys.teku.core.results.BlockImportResult;
import tech.pegasys.teku.data.BlockProcessingRecord;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.StatusMessage;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.PeerStatus;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.BlocksByRangeResponseInvalidResponseException;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseStreamListener;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.DecompressFailedException;
import tech.pegasys.teku.networking.p2p.peer.DisconnectReason;
import tech.pegasys.teku.util.config.Constants;

public class PeerSyncTest extends AbstractSyncTest {

  private static final SignedBeaconBlock BLOCK = new DataStructureUtil().randomSignedBeaconBlock(1);
  private static final Bytes32 PEER_HEAD_BLOCK_ROOT = Bytes32.fromHexString("0x1234");
  private static final UInt64 PEER_HEAD_SLOT = UInt64.valueOf(30);
  private static final UInt64 PEER_FINALIZED_EPOCH = UInt64.valueOf(3);

  private static final PeerStatus PEER_STATUS =
      PeerStatus.fromStatusMessage(
          new StatusMessage(
              Constants.GENESIS_FORK_VERSION,
              Bytes32.ZERO,
              PEER_FINALIZED_EPOCH,
              PEER_HEAD_BLOCK_ROOT,
              PEER_HEAD_SLOT));

  private final PeerSync peerSync =
      new PeerSync(asyncRunner, storageClient, blockImporter, new NoOpMetricsSystem());

  @BeforeEach
  public void setUp() {
    when(storageClient.getFinalizedEpoch()).thenReturn(UInt64.ZERO);
    when(peer.getStatus()).thenReturn(PEER_STATUS);
    when(peer.disconnectCleanly(any())).thenReturn(SafeFuture.completedFuture(null));
    // By default set up block import to succeed
    final BlockProcessingRecord processingRecord = mock(BlockProcessingRecord.class);
    final SignedBeaconBlock block = mock(SignedBeaconBlock.class);
    final SafeFuture<BlockImportResult> result =
        SafeFuture.completedFuture(BlockImportResult.successful(processingRecord));
    when(processingRecord.getBlock()).thenReturn(block);
    when(blockImporter.importBlock(any())).thenReturn(result);
    when(storageClient.getHeadSlot()).thenReturn(UInt64.ONE);
  }

  @Test
  void sync_failedImport_stateTransitionError() {
    final BlockImportResult importResult =
        BlockImportResult.failedStateTransition(new StateTransitionException());
    testFailedBlockImport(() -> importResult, true);
  }

  @Test
  void sync_failedImport_unknownParent() {
    testFailedBlockImport(() -> BlockImportResult.FAILED_UNKNOWN_PARENT, true);
  }

  @Test
  void sync_failedImport_failedWeakSubjectivityChecks() {
    testFailedBlockImport(() -> BlockImportResult.FAILED_WEAK_SUBJECTIVITY_CHECKS, true);
  }

  @Test
  void sync_failedImport_unknownAncestry() {
    testFailedBlockImport(() -> BlockImportResult.FAILED_INVALID_ANCESTRY, false);
  }

  @Test
  void sync_failedImport_unknownBlockIsFromFuture() {
    testFailedBlockImport(() -> BlockImportResult.FAILED_BLOCK_IS_FROM_FUTURE, false);
  }

  void testFailedBlockImport(
      final Supplier<BlockImportResult> importResult, final boolean shouldDisconnect) {
    final SafeFuture<Void> requestFuture = new SafeFuture<>();
    when(peer.requestBlocksByRange(any(), any(), any(), any())).thenReturn(requestFuture);

    final SafeFuture<PeerSyncResult> syncFuture = peerSync.sync(peer);
    assertThat(syncFuture).isNotDone();

    verify(peer)
        .requestBlocksByRange(
            any(), any(), eq(UInt64.ONE), responseListenerArgumentCaptor.capture());

    // Respond with blocks and check they're passed to the block importer.
    final ResponseStreamListener<SignedBeaconBlock> responseListener =
        responseListenerArgumentCaptor.getValue();

    // Importing the returned block fails
    when(blockImporter.importBlock(BLOCK))
        .thenReturn(SafeFuture.completedFuture(importResult.get()));
    // Probably want to have a specific exception type to indicate bad data.
    try {
      responseListener.onResponse(BLOCK).join();
      fail("Should have thrown an error to indicate the response was bad");
    } catch (final Exception e) {
      // RpcMessageHandler will consider the request complete if there's an error processing a
      // response
      assertThat(e).hasCauseInstanceOf(FailedBlockImportException.class);
      requestFuture.completeExceptionally(e);
    }

    assertThat(syncFuture).isCompleted();
    PeerSyncResult result = syncFuture.join();
    if (shouldDisconnect) {
      verify(peer).disconnectCleanly(DisconnectReason.REMOTE_FAULT);
      assertThat(result).isEqualByComparingTo(PeerSyncResult.BAD_BLOCK);
    } else {
      verify(peer, never()).disconnectCleanly(any());
      assertThat(result).isEqualByComparingTo(PeerSyncResult.IMPORT_FAILED);
    }
  }

  @Test
  void sync_stoppedBeforeBlockImport() {
    UInt64 step = UInt64.ONE;
    UInt64 startHere = UInt64.ONE;
    final SafeFuture<Void> requestFuture = new SafeFuture<>();
    when(peer.requestBlocksByRange(any(), any(), any(), any())).thenReturn(requestFuture);

    final SafeFuture<PeerSyncResult> syncFuture = peerSync.sync(peer);
    assertThat(syncFuture).isNotDone();

    verify(peer)
        .requestBlocksByRange(any(), any(), eq(step), responseListenerArgumentCaptor.capture());

    // Respond with blocks and check they're passed to the block importer.
    final ResponseStreamListener<SignedBeaconBlock> responseListener =
        responseListenerArgumentCaptor.getValue();

    // Stop the sync, no further blocks should be imported
    peerSync.stop();

    try {
      responseListener.onResponse(BLOCK).join();
      fail("Should have thrown an error to indicate the sync was stopped");
    } catch (final CancellationException e) {
      // RpcMessageHandler will consider the request complete if there's an error processing a
      // response
      requestFuture.completeExceptionally(e);
    }

    // Should not disconnect the peer as it wasn't their fault
    verify(peer, never()).disconnectCleanly(any());
    verifyNoInteractions(blockImporter);
    assertThat(syncFuture).isCompleted();
    PeerSyncResult result = syncFuture.join();
    assertThat(result).isEqualByComparingTo(PeerSyncResult.CANCELLED);

    // check startingSlot
    UInt64 startingSlot = peerSync.getStartingSlot();
    assertThat(startingSlot).isEqualTo(startHere);
  }

  @Test
  void sync_badAdvertisedFinalizedEpoch() {
    final SafeFuture<Void> requestFuture = new SafeFuture<>();
    when(peer.requestBlocksByRange(any(), any(), any(), any())).thenReturn(requestFuture);

    final SafeFuture<PeerSyncResult> syncFuture = peerSync.sync(peer);
    assertThat(syncFuture).isNotDone();

    verify(peer)
        .requestBlocksByRange(
            any(), any(), eq(UInt64.ONE), responseListenerArgumentCaptor.capture());

    // Respond with blocks and check they're passed to the block importer.
    final ResponseStreamListener<SignedBeaconBlock> responseListener =
        responseListenerArgumentCaptor.getValue();
    final List<SignedBeaconBlock> blocks =
        respondWithBlocksAtSlots(responseListener, UInt64.ONE, PEER_HEAD_SLOT);
    for (SignedBeaconBlock block : blocks) {
      verify(blockImporter).importBlock(block);
    }
    assertThat(syncFuture).isNotDone();

    // Now that we've imported the block, our finalized epoch has updated but hasn't reached what
    // the peer claimed
    when(storageClient.getFinalizedEpoch()).thenReturn(PEER_FINALIZED_EPOCH.minus(UInt64.ONE));

    // Signal the request for data from the peer is complete.
    requestFuture.complete(null);

    // Check that the sync is done and the peer was not disconnected.
    assertThat(syncFuture).isCompleted();
    verify(peer).disconnectCleanly(DisconnectReason.REMOTE_FAULT);
  }

  @Test
  void sync_longSyncWithTwoRequests() {
    final UInt64 secondRequestSize = UInt64.ONE;
    UInt64 peerHeadSlot = MAX_BLOCK_BY_RANGE_REQUEST_SIZE.plus(secondRequestSize);

    withPeerHeadSlot(peerHeadSlot);

    final SafeFuture<Void> requestFuture1 = new SafeFuture<>();
    final SafeFuture<Void> requestFuture2 = new SafeFuture<>();
    when(peer.requestBlocksByRange(any(), any(), any(), any()))
        .thenReturn(requestFuture1)
        .thenReturn(requestFuture2);

    final SafeFuture<PeerSyncResult> syncFuture = peerSync.sync(peer);
    assertThat(syncFuture).isNotDone();

    final UInt64 startSlot = UInt64.ONE;
    verify(peer)
        .requestBlocksByRange(
            eq(startSlot),
            eq(MAX_BLOCK_BY_RANGE_REQUEST_SIZE),
            eq(UInt64.ONE),
            responseListenerArgumentCaptor.capture());

    final int lastReceivedBlockSlot = peerHeadSlot.intValue() - secondRequestSize.intValue();
    completeRequestWithBlockAtSlot(requestFuture1, lastReceivedBlockSlot);

    final UInt64 nextSlotStart = UInt64.valueOf(lastReceivedBlockSlot + 1);
    verify(peer)
        .requestBlocksByRange(
            eq(nextSlotStart),
            eq(secondRequestSize),
            eq(UInt64.ONE),
            responseListenerArgumentCaptor.capture());

    when(storageClient.getFinalizedEpoch()).thenReturn(PEER_FINALIZED_EPOCH);

    // Respond with blocks and check they're passed to the block importer.
    completeRequestWithBlockAtSlot(requestFuture2, peerHeadSlot.intValue());

    // Check that the sync is done and the peer was not disconnected.
    assertThat(syncFuture).isCompleted();
    verify(peer, never()).disconnectCleanly(any());
  }

  @Test
  void sync_withPeerStatusUpdatedWhileSyncing() {
    final UInt64 initialPeerHeadSlot = PEER_HEAD_SLOT;

    final SafeFuture<Void> requestFuture1 = new SafeFuture<>();
    final SafeFuture<Void> requestFuture2 = new SafeFuture<>();
    when(peer.requestBlocksByRange(any(), any(), any(), any()))
        .thenReturn(requestFuture1)
        .thenReturn(requestFuture2);

    final SafeFuture<PeerSyncResult> syncFuture = peerSync.sync(peer);
    assertThat(syncFuture).isNotDone();

    final UInt64 startSlot = UInt64.ONE;
    verify(peer)
        .requestBlocksByRange(
            eq(startSlot),
            eq(initialPeerHeadSlot),
            eq(UInt64.ONE),
            responseListenerArgumentCaptor.capture());

    // Update peer's status before completing first request, which should prompt a second request
    final UInt64 secondRequestSize = UInt64.valueOf(5);
    UInt64 updatedPeerHeadSlot = initialPeerHeadSlot.plus(secondRequestSize);
    withPeerHeadSlot(updatedPeerHeadSlot);

    final int lastReceivedBlockSlot = initialPeerHeadSlot.intValue();
    completeRequestWithBlockAtSlot(requestFuture1, lastReceivedBlockSlot);

    final UInt64 nextSlotStart = UInt64.valueOf(lastReceivedBlockSlot + 1);
    verify(peer)
        .requestBlocksByRange(
            eq(nextSlotStart),
            eq(secondRequestSize),
            eq(UInt64.ONE),
            responseListenerArgumentCaptor.capture());

    when(storageClient.getFinalizedEpoch()).thenReturn(PEER_FINALIZED_EPOCH);

    // Respond with blocks and check they're passed to the block importer.
    completeRequestWithBlockAtSlot(requestFuture2, updatedPeerHeadSlot);

    // Check that the sync is done and the peer was not disconnected.
    assertThat(syncFuture).isCompleted();
    verify(peer, never()).disconnectCleanly(any());
  }

  @Test
  void sync_handleEmptyResponse() {
    final UInt64 secondRequestSize = UInt64.valueOf(5);
    UInt64 peerHeadSlot = MAX_BLOCK_BY_RANGE_REQUEST_SIZE.plus(secondRequestSize);

    withPeerHeadSlot(peerHeadSlot);

    final SafeFuture<Void> requestFuture1 = new SafeFuture<>();
    final SafeFuture<Void> requestFuture2 = new SafeFuture<>();
    final SafeFuture<Void> requestFuture3 = new SafeFuture<>();
    when(peer.requestBlocksByRange(any(), any(), any(), any()))
        .thenReturn(requestFuture1)
        .thenReturn(requestFuture2)
        .thenReturn(requestFuture3);

    final SafeFuture<PeerSyncResult> syncFuture = peerSync.sync(peer);
    assertThat(syncFuture).isNotDone();

    final UInt64 startSlot = UInt64.ONE;
    verify(peer)
        .requestBlocksByRange(
            eq(startSlot),
            eq(MAX_BLOCK_BY_RANGE_REQUEST_SIZE),
            eq(UInt64.ONE),
            responseListenerArgumentCaptor.capture());

    // Complete request with no returned blocks
    requestFuture1.complete(null);
    verify(blockImporter, never()).importBlock(any());

    // check startingSlot
    final UInt64 syncStatusStartingSlot = peerSync.getStartingSlot();
    assertThat(syncStatusStartingSlot).isEqualTo(startSlot);

    asyncRunner.executeQueuedActions();
    final UInt64 nextSlotStart = startSlot.plus(MAX_BLOCK_BY_RANGE_REQUEST_SIZE);
    verify(peer)
        .requestBlocksByRange(
            eq(nextSlotStart),
            eq(secondRequestSize),
            eq(UInt64.ONE),
            responseListenerArgumentCaptor.capture());

    when(storageClient.getFinalizedEpoch()).thenReturn(PEER_FINALIZED_EPOCH);
    completeRequestWithBlockAtSlot(requestFuture2, peerHeadSlot.intValue());

    // Check that the sync is done
    assertThat(syncFuture).isCompleted();

    // check startingSlot is still where it was
    final UInt64 syncStatusStartingSlot2 = peerSync.getStartingSlot();
    assertThat(syncStatusStartingSlot2).isEqualTo(startSlot);

    // do another sync and check that things are further along.
    UInt64 thirdRequestSize = UInt64.valueOf(6);
    withPeerHeadSlot(peerHeadSlot.plus(thirdRequestSize));
    final SafeFuture<PeerSyncResult> syncFuture2 = peerSync.sync(peer);
    assertThat(syncFuture2).isNotDone();

    // first non-finalized slot after syncing with peer
    final UInt64 secondSyncStartingSlot =
        PEER_FINALIZED_EPOCH.times(Constants.SLOTS_PER_EPOCH).plus(UInt64.ONE);

    verify(peer)
        .requestBlocksByRange(
            eq(secondSyncStartingSlot),
            any(),
            eq(UInt64.ONE),
            responseListenerArgumentCaptor.capture());

    // Signal that second sync is complete
    when(storageClient.getFinalizedEpoch()).thenReturn(PEER_FINALIZED_EPOCH);
    requestFuture3.complete(null);

    // Check that the sync is done and the peer was not disconnected.
    assertThat(syncFuture2).isCompleted();
    verify(peer, never()).disconnectCleanly(any());

    // check that starting slot for second sync is the first slot after peer's finalized epoch
    final UInt64 syncStatusStartingSlot3 = peerSync.getStartingSlot();
    assertThat(syncStatusStartingSlot3).isEqualTo(secondSyncStartingSlot);
  }

  @Test
  void sync_failSyncIfPeerThrottlesTooAggressively() {
    final UInt64 startSlot = UInt64.ONE;
    UInt64 minPeerSlot = MAX_BLOCK_BY_RANGE_REQUEST_SIZE.plus(startSlot);
    withPeerFinalizedEpoch(compute_epoch_at_slot(minPeerSlot));

    final List<SafeFuture<Void>> requestFutures = new ArrayList<>();
    OngoingStubbing<SafeFuture<Void>> requestStub =
        when(peer.requestBlocksByRange(any(), any(), any(), any()));
    for (int i = 0; i < PeerSync.MAX_THROTTLED_REQUESTS + 1; i++) {
      final SafeFuture<Void> future = new SafeFuture<>();
      requestStub = requestStub.thenReturn(future);
      requestFutures.add(future);
    }

    final SafeFuture<PeerSyncResult> syncFuture = peerSync.sync(peer);
    assertThat(syncFuture).isNotDone();

    verify(peer)
        .requestBlocksByRange(
            eq(startSlot),
            eq(MAX_BLOCK_BY_RANGE_REQUEST_SIZE),
            eq(UInt64.ONE),
            responseListenerArgumentCaptor.capture());

    // Peer only returns a couple of blocks for each request
    int nextBlock = startSlot.intValue();
    for (int i = 0; i < PeerSync.MAX_THROTTLED_REQUESTS; i++) {
      completeRequestWithBlockAtSlot(requestFutures.get(i), nextBlock);
      nextBlock += 1;
    }

    // We haven't hit our limit yet
    assertThat(syncFuture).isNotDone();

    // Next request hits our limit
    final int lastRequestIndex = PeerSync.MAX_THROTTLED_REQUESTS;
    completeRequestWithBlockAtSlot(requestFutures.get(lastRequestIndex), nextBlock + 2);

    // We hit our limit
    assertThat(syncFuture).isCompletedWithValue(PeerSyncResult.EXCESSIVE_THROTTLING);
    // We don't disconnect the peer, the SyncManager just excludes the peer as a sync target for a
    // period
    verify(peer, never()).disconnectCleanly(any());
  }

  @Test
  void sync_stopSyncIfPeerSendsBlocksInWrongOrder() {
    final UInt64 startSlot = UInt64.ONE;
    UInt64 peerHeadSlot = UInt64.valueOf(1000000);

    withPeerHeadSlot(peerHeadSlot);

    final SafeFuture<Void> requestFuture = new SafeFuture<>();
    when(peer.requestBlocksByRange(any(), any(), any(), any())).thenReturn(requestFuture);

    final SafeFuture<PeerSyncResult> syncFuture = peerSync.sync(peer);
    assertThat(syncFuture).isNotDone();

    verify(peer)
        .requestBlocksByRange(
            eq(startSlot),
            eq(MAX_BLOCK_BY_RANGE_REQUEST_SIZE),
            eq(UInt64.ONE),
            responseListenerArgumentCaptor.capture());

    requestFuture.completeExceptionally(
        new BlocksByRangeResponseInvalidResponseException(
            peer,
            BlocksByRangeResponseInvalidResponseException.InvalidResponseType
                .BLOCK_SLOT_NOT_GREATER_THAN_PREVIOUS_BLOCK_SLOT));

    // Peer returns some blocks but they are not ordered
    assertThat(syncFuture).isCompletedWithValue(PeerSyncResult.INVALID_RESPONSE);

    verify(peer).disconnectCleanly(any());
  }

  @Test
  void sync_continueSyncIfPeerThrottlesAReasonableAmount() {
    final UInt64 startSlot = UInt64.ONE;
    UInt64 peerHeadSlot = UInt64.valueOf(1000000);

    withPeerHeadSlot(peerHeadSlot);

    final SafeFuture<Void> requestFuture1 = new SafeFuture<>();
    final SafeFuture<Void> requestFuture2 = new SafeFuture<>();
    when(peer.requestBlocksByRange(any(), any(), any(), any()))
        .thenReturn(requestFuture1)
        .thenReturn(requestFuture2);

    final SafeFuture<PeerSyncResult> syncFuture = peerSync.sync(peer);
    assertThat(syncFuture).isNotDone();

    verify(peer)
        .requestBlocksByRange(
            eq(startSlot),
            eq(MAX_BLOCK_BY_RANGE_REQUEST_SIZE),
            eq(UInt64.ONE),
            responseListenerArgumentCaptor.capture());

    // Peer only returns some blocks but not as many as were requested
    final int lastReceivedBlockSlot = 70;
    completeRequestWithBlockAtSlot(requestFuture1, lastReceivedBlockSlot);

    assertThat(syncFuture).isNotDone();

    // Next request should start after the last received block
    verify(peer)
        .requestBlocksByRange(
            eq(UInt64.valueOf(lastReceivedBlockSlot + 1)),
            eq(MAX_BLOCK_BY_RANGE_REQUEST_SIZE),
            eq(UInt64.ONE),
            any());
    verify(peer, never()).disconnectCleanly(any());
  }

  @Test
  void sync_invalidResponseResultWhenMalformedResponse() {
    when(peer.requestBlocksByRange(any(), any(), any(), any()))
        .thenReturn(SafeFuture.failedFuture(new DecompressFailedException()));

    final SafeFuture<PeerSyncResult> syncFuture = peerSync.sync(peer);
    assertThat(syncFuture).isCompletedWithValue(PeerSyncResult.INVALID_RESPONSE);
  }

  private void withPeerHeadSlot(final UInt64 peerHeadSlot) {
    withPeerHeadSlot(peerHeadSlot, PEER_FINALIZED_EPOCH, PEER_HEAD_BLOCK_ROOT);
  }

  private void withPeerFinalizedEpoch(final UInt64 finalizedEpoch) {
    final UInt64 headSlot =
        compute_start_slot_at_epoch(finalizedEpoch).plus(2 * Constants.SLOTS_PER_EPOCH);
    final PeerStatus peer_status =
        PeerStatus.fromStatusMessage(
            new StatusMessage(
                Constants.GENESIS_FORK_VERSION,
                Bytes32.ZERO,
                finalizedEpoch,
                PEER_HEAD_BLOCK_ROOT,
                headSlot));

    when(peer.getStatus()).thenReturn(peer_status);
  }
}
