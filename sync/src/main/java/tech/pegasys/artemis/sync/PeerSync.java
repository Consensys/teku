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
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.networking.eth2.peers.Eth2Peer;
import tech.pegasys.artemis.statetransition.blockimport.BlockImportResult;
import tech.pegasys.artemis.statetransition.blockimport.BlockImportResult.FailureReason;
import tech.pegasys.artemis.statetransition.blockimport.BlockImporter;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.util.async.SafeFuture;

public class PeerSync {
  private static final Logger LOG = LogManager.getLogger();
  private static final UnsignedLong STEP = UnsignedLong.ONE;

  private final AtomicBoolean stopped = new AtomicBoolean(false);
  private final ChainStorageClient storageClient;
  private final BlockImporter blockImporter;

  public PeerSync(final ChainStorageClient storageClient, final BlockImporter blockImporter) {
    this.storageClient = storageClient;
    this.blockImporter = blockImporter;
  }

  public SafeFuture<PeerSyncResult> sync(final Eth2Peer peer) {
    return executeSync(peer, compute_start_slot_at_epoch(storageClient.getFinalizedEpoch()));
  }

  public void stop() {
    stopped.set(true);
  }

  private SafeFuture<PeerSyncResult> executeSync(
      final Eth2Peer peer, final UnsignedLong latestRequestedSlot) {
    if (stopped.get()) {
      return SafeFuture.completedFuture(PeerSyncResult.CANCELLED);
    }
    final UnsignedLong advertisedHeadBlockSlot = peer.getStatus().getHeadSlot();
    final Bytes32 advertisedHeadRoot = peer.getStatus().getHeadRoot();
    final UnsignedLong advertisedFinalizedEpoch = peer.getStatus().getFinalizedEpoch();
    final UnsignedLong count =
        calculateNumberOfBlocksToRequest(latestRequestedSlot, advertisedHeadBlockSlot);
    return peer.requestBlocksByRange(
            advertisedHeadRoot, latestRequestedSlot, count, STEP, this::blockResponseListener)
        .thenCompose(
            res -> {
              if (storageClient.getFinalizedEpoch().compareTo(advertisedFinalizedEpoch) >= 0) {
                return SafeFuture.completedFuture(PeerSyncResult.SUCCESSFUL_SYNC);
              } else if (latestRequestedSlot.compareTo(advertisedHeadBlockSlot) < 0) {
                return executeSync(peer, latestRequestedSlot.plus(count));
              } else {
                disconnectFromPeer(peer);
                return SafeFuture.completedFuture(PeerSyncResult.FAULTY_ADVERTISEMENT);
              }
            })
        .exceptionally(
            err -> {
              Throwable rootException = Throwables.getRootCause(err);
              if (rootException instanceof FailedBlockImportException) {
                final FailedBlockImportException importException =
                    (FailedBlockImportException) rootException;
                final FailureReason reason = importException.getResult().getFailureReason();
                final BeaconBlock block = importException.getBlock();
                LOG.warn("Failed to import block from peer {}: {}", block, peer);
                if (reason == FailureReason.FAILED_STATE_TRANSITION
                    || reason == FailureReason.UNKNOWN_PARENT) {
                  LOG.debug("Disconnecting from peer ({}) who sent invalid block: {}", peer, block);
                  disconnectFromPeer(peer);
                  return PeerSyncResult.BAD_BLOCK;
                } else {
                  return PeerSyncResult.IMPORT_FAILED;
                }
              }
              if (rootException instanceof CancellationException) {
                return PeerSyncResult.CANCELLED;
              }
              if (err instanceof RuntimeException) {
                throw (RuntimeException) err;
              } else {
                throw new RuntimeException("Unhandled error while syncing", err);
              }
            });
  }

  private UnsignedLong calculateNumberOfBlocksToRequest(
      final UnsignedLong latestRequestedSlot, final UnsignedLong advertisedHeadBlockSlot) {
    final UnsignedLong diff = advertisedHeadBlockSlot.minus(latestRequestedSlot);
    return diff.compareTo(MAX_BLOCK_BY_RANGE_REQUEST_SIZE) > 0
        ? MAX_BLOCK_BY_RANGE_REQUEST_SIZE
        : diff;
  }

  private void blockResponseListener(BeaconBlock block) {
    if (stopped.get()) {
      throw new CancellationException("Peer sync was cancelled");
    }
    final BlockImportResult result = blockImporter.importBlock(block);
    if (!result.isSuccessful()) {
      throw new FailedBlockImportException(block, result);
    }
  }

  private void disconnectFromPeer(Eth2Peer peer) {
    peer.sendGoodbye(REASON_FAULT_ERROR).reportExceptions();
  }
}
