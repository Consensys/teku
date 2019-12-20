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

import static tech.pegasys.artemis.datastructures.networking.libp2p.rpc.GoodbyeMessage.REASON_FAULT_ERROR;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;
import static tech.pegasys.artemis.util.config.Constants.MAX_BLOCK_BY_RANGE_REQUEST_SIZE;

import com.google.common.base.Throwables;
import com.google.common.primitives.UnsignedLong;
import java.util.concurrent.CompletableFuture;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.networking.eth2.peers.Eth2Peer;
import tech.pegasys.artemis.networking.eth2.rpc.core.InvalidResponseException;
import tech.pegasys.artemis.statetransition.StateTransitionException;
import tech.pegasys.artemis.statetransition.blockimport.BlockImportResult;
import tech.pegasys.artemis.statetransition.blockimport.BlockImportResult.FailureReason;
import tech.pegasys.artemis.statetransition.blockimport.BlockImporter;
import tech.pegasys.artemis.storage.ChainStorageClient;

public class PeerSync {

  private static final UnsignedLong STEP = UnsignedLong.ONE;

  private final Eth2Peer peer;
  private final UnsignedLong advertisedFinalizedEpoch;
  private final UnsignedLong advertisedHeadBlockSlot;
  private final Bytes32 advertisedHeadBlockRoot;
  private final ChainStorageClient storageClient;
  private final BlockImporter blockImporter;

  private volatile UnsignedLong latestRequestedSlot;

  public PeerSync(
      final Eth2Peer peer,
      final ChainStorageClient storageClient,
      final BlockImporter blockImporter) {
    this.peer = peer;
    this.storageClient = storageClient;
    this.blockImporter = blockImporter;

    this.advertisedFinalizedEpoch = peer.getStatus().getFinalizedEpoch();
    this.advertisedHeadBlockSlot = peer.getStatus().getHeadSlot();
    this.advertisedHeadBlockRoot = peer.getStatus().getHeadRoot();
    this.latestRequestedSlot = compute_start_slot_at_epoch(storageClient.getFinalizedEpoch());
  }

  public CompletableFuture<PeerSyncResult> sync() {
    return executeSync();
  }

  private CompletableFuture<PeerSyncResult> executeSync() {
    return requestSyncBlocks(peer)
        .thenCompose(
            res -> {
              if (storageClient.getFinalizedEpoch().compareTo(advertisedFinalizedEpoch) >= 0) {
                return CompletableFuture.completedFuture(PeerSyncResult.SUCCESSFUL_SYNC);
              } else if (latestRequestedSlot.compareTo(advertisedHeadBlockSlot) < 0) {
                return executeSync();
              } else {
                disconnectFromPeer(peer);
                return CompletableFuture.completedFuture(PeerSyncResult.FAULTY_ADVERTISEMENT);
              }
            })
        .exceptionally(
            err -> {
              Throwable rootException = Throwables.getRootCause(err);
              if (rootException instanceof StateTransitionException) {
                disconnectFromPeer(peer);
                return PeerSyncResult.BAD_BLOCK;
              }
              if (err instanceof RuntimeException) {
                throw (RuntimeException) err;
              } else {
                throw new RuntimeException("Unhandled error while syncing", err);
              }
            });
  }

  private CompletableFuture<Void> requestSyncBlocks(Eth2Peer peer) {
    UnsignedLong diff = advertisedHeadBlockSlot.minus(latestRequestedSlot);
    UnsignedLong count =
        diff.compareTo(MAX_BLOCK_BY_RANGE_REQUEST_SIZE) > 0
            ? MAX_BLOCK_BY_RANGE_REQUEST_SIZE
            : diff;
    CompletableFuture<Void> future =
        peer.requestBlocksByRange(
            advertisedHeadBlockRoot, latestRequestedSlot, count, STEP, this::blockResponseListener);
    latestRequestedSlot = latestRequestedSlot.plus(count);
    return future;
  }

  private void blockResponseListener(BeaconBlock block) {
    final BlockImportResult result = blockImporter.importBlock(block);
    if (result.isSuccessful()) {
      return;
    } else if (result.getFailureReason() == FailureReason.FAILED_STATE_TRANSITION) {
      throw new BadBlockException("State transition error", result.getFailureCause());
    }
  }

  private void disconnectFromPeer(Eth2Peer peer) {
    peer.sendGoodbye(REASON_FAULT_ERROR);
  }

  public static class BadBlockException extends InvalidResponseException {
    public BadBlockException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
