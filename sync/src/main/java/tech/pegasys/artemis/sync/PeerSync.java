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
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.networking.eth2.peers.Eth2Peer;
import tech.pegasys.artemis.networking.eth2.peers.PeerStatus;
import tech.pegasys.artemis.networking.eth2.rpc.core.ResponseStream.ResponseListener;
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
    LOG.debug("Start syncing to peer {}", peer);
    // Begin requesting blocks at our first non-finalized slot
    final UnsignedLong finalizedEpoch = storageClient.getFinalizedEpoch();
    final UnsignedLong latestFinalizedSlot = compute_start_slot_at_epoch(finalizedEpoch);
    final UnsignedLong firstNonFinalSlot = latestFinalizedSlot.plus(UnsignedLong.ONE);

    return executeSync(peer, peer.getStatus(), firstNonFinalSlot)
        .whenComplete(
            (res, err) -> {
              if (err != null) {
                LOG.debug("Failed to sync with peer {}: {}", peer, err);
              } else {
                LOG.debug("Finished syncing (with status {}) to peer {}", res.name(), peer);
              }
            });
  }

  public void stop() {
    stopped.set(true);
  }

  private SafeFuture<PeerSyncResult> executeSync(
      final Eth2Peer peer, final PeerStatus status, final UnsignedLong startSlot) {
    if (stopped.get()) {
      return SafeFuture.completedFuture(PeerSyncResult.CANCELLED);
    }

    final UnsignedLong count = calculateNumberOfBlocksToRequest(startSlot, status);
    if (count.longValue() == 0) {
      return completeSyncWithPeer(peer, status);
    }

    LOG.debug("Request {} blocks starting at {} from peer {}", count, startSlot, peer);
    final AtomicLong latestSlotImported = new AtomicLong(-1);
    return peer.requestBlocksByRange(
            status.getHeadRoot(), startSlot, count, STEP, blockResponseListener(latestSlotImported))
        .thenCompose(
            res -> {
              if (latestSlotImported.get() < 0) {
                // Last request returned no blocks
                // This doesn't necessarily mean the peer has misbehaved - it's possible its chain
                // may have progressed and the blocks we're requesting are no longer available
                return SafeFuture.completedFuture(PeerSyncResult.IMPORT_STALLED);
              }
              final UnsignedLong nextSlot = UnsignedLong.valueOf(latestSlotImported.get() + 1);
              return executeSync(peer, status, nextSlot);
            })
        .exceptionally(err -> handleFailedRequestToPeer(peer, err));
  }

  private PeerSyncResult handleFailedRequestToPeer(Eth2Peer peer, Throwable err) {
    Throwable rootException = Throwables.getRootCause(err);
    if (rootException instanceof FailedBlockImportException) {
      final FailedBlockImportException importException = (FailedBlockImportException) rootException;
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
  }

  private SafeFuture<PeerSyncResult> completeSyncWithPeer(
      final Eth2Peer peer, final PeerStatus status) {
    if (storageClient.getFinalizedEpoch().compareTo(status.getFinalizedEpoch()) >= 0) {
      return SafeFuture.completedFuture(PeerSyncResult.SUCCESSFUL_SYNC);
    } else {
      disconnectFromPeer(peer);
      return SafeFuture.completedFuture(PeerSyncResult.FAULTY_ADVERTISEMENT);
    }
  }

  private UnsignedLong calculateNumberOfBlocksToRequest(
      final UnsignedLong nextSlot, final PeerStatus status) {
    if (nextSlot.compareTo(status.getHeadSlot()) > 0) {
      // We've synced the advertised head, nothing left to request
      return UnsignedLong.ZERO;
    }

    final UnsignedLong diff = status.getHeadSlot().minus(nextSlot).plus(UnsignedLong.ONE);
    return diff.compareTo(MAX_BLOCK_BY_RANGE_REQUEST_SIZE) > 0
        ? MAX_BLOCK_BY_RANGE_REQUEST_SIZE
        : diff;
  }

  private ResponseListener<BeaconBlock> blockResponseListener(final AtomicLong lastImportedSlot) {
    return (block) -> {
      if (stopped.get()) {
        throw new CancellationException("Peer sync was cancelled");
      }
      final BlockImportResult result = blockImporter.importBlock(block);
      LOG.trace("Block import result for block at {}: {}", block.getSlot(), result);
      if (!result.isSuccessful()) {
        throw new FailedBlockImportException(block, result);
      }
      lastImportedSlot.set(block.getSlot().longValue());
    };
  }

  private void disconnectFromPeer(Eth2Peer peer) {
    peer.sendGoodbye(REASON_FAULT_ERROR).reportExceptions();
  }
}
