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

import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;
import static tech.pegasys.teku.util.config.Constants.MAX_BLOCK_BY_RANGE_REQUEST_SIZE;

import com.google.common.base.Throwables;
import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import tech.pegasys.teku.core.results.BlockImportResult.FailureReason;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.peers.PeerStatus;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.BlocksByRangeResponseInvalidResponseException;
import tech.pegasys.teku.networking.p2p.peer.DisconnectReason;
import tech.pegasys.teku.statetransition.blockimport.BlockImporter;
import tech.pegasys.teku.storage.client.RecentChainData;

public class PeerSync {
  private static final Duration NEXT_REQUEST_TIMEOUT = Duration.ofSeconds(3);

  /**
   * Peers are allowed to limit the number of blocks they actually return to use. We tolerate this
   * up to a point, but if the peer is throttling too excessively we would be better syncing from a
   * different peer. This value sets how many slots we should progress per request. Since some slots
   * may be empty we check that we're progressing through slots, even if not many blocks are being
   * returned.
   */
  static final UInt64 MIN_SLOTS_TO_PROGRESS_PER_REQUEST = UInt64.valueOf(50);

  private static final Logger LOG = LogManager.getLogger();
  private static final UInt64 STEP = UInt64.ONE;
  static final int MAX_THROTTLED_REQUESTS = 10;

  private final AtomicBoolean stopped = new AtomicBoolean(false);
  private final RecentChainData storageClient;
  private final BlockImporter blockImporter;

  private final AsyncRunner asyncRunner;
  private final Counter blockImportSuccessResult;
  private final Counter blockImportFailureResult;

  private final AtomicInteger throttledRequestCount = new AtomicInteger(0);
  private volatile UInt64 startingSlot = UInt64.valueOf(0);

  public PeerSync(
      final AsyncRunner asyncRunner,
      final RecentChainData storageClient,
      final BlockImporter blockImporter,
      final MetricsSystem metricsSystem) {
    this.asyncRunner = asyncRunner;
    this.storageClient = storageClient;
    this.blockImporter = blockImporter;
    final LabelledMetric<Counter> blockImportCounter =
        metricsSystem.createLabelledCounter(
            TekuMetricCategory.BEACON,
            "block_import_total",
            "The number of block imports performed",
            "result");
    this.blockImportSuccessResult = blockImportCounter.labels("imported");
    this.blockImportFailureResult = blockImportCounter.labels("rejected");
  }

  public SafeFuture<PeerSyncResult> sync(final Eth2Peer peer) {
    LOG.debug("Start syncing to peer {}", peer);
    // Begin requesting blocks at our first non-finalized slot
    final UInt64 finalizedEpoch = storageClient.getFinalizedEpoch();
    final UInt64 latestFinalizedSlot = compute_start_slot_at_epoch(finalizedEpoch);
    final UInt64 firstNonFinalSlot = latestFinalizedSlot.plus(UInt64.ONE);

    this.startingSlot = firstNonFinalSlot;

    return executeSync(peer, firstNonFinalSlot, SafeFuture.COMPLETE, true)
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

  @SuppressWarnings("FutureReturnValueIgnored")
  private SafeFuture<PeerSyncResult> executeSync(
      final Eth2Peer peer,
      final UInt64 startSlot,
      final SafeFuture<Void> readyForRequest,
      final boolean findCommonAncestor) {
    if (stopped.get()) {
      return SafeFuture.completedFuture(PeerSyncResult.CANCELLED);
    }

    final PeerStatus status = peer.getStatus();
    final UInt64 count = calculateNumberOfBlocksToRequest(startSlot, status);
    if (count.longValue() == 0) {
      return completeSyncWithPeer(peer, status);
    }

    return readyForRequest
        .thenCompose(
            __ -> {
              if (!findCommonAncestor) {
                return SafeFuture.completedFuture(startSlot);
              }
              CommonAncestor ancestor = new CommonAncestor(storageClient);
              return ancestor.getCommonAncestor(peer, status, startSlot);
            })
        .thenCompose(
            (ancestorStartSlot) -> {
              if (findCommonAncestor) {
                LOG.trace("Start sync from slot {}, instead of {}", ancestorStartSlot, startSlot);
              }
              LOG.debug(
                  "Request {} blocks starting at {} from peer {}",
                  count,
                  ancestorStartSlot,
                  peer.getId());
              final SafeFuture<Void> readyForNextRequest =
                  asyncRunner.getDelayedFuture(
                      NEXT_REQUEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
              final PeerSyncBlockRequest request =
                  new PeerSyncBlockRequest(
                      readyForNextRequest,
                      ancestorStartSlot.plus(count),
                      this::blockResponseListener);
              return peer.requestBlocksByRange(ancestorStartSlot, count, STEP, request)
                  .thenApply((res) -> request);
            })
        .thenCompose(
            (blockRequest) -> {
              final UInt64 nextSlot = blockRequest.getActualEndSlot().plus(UInt64.ONE);
              LOG.trace(
                  "Completed request for {} slots from peer {}. Next request starts from {}",
                  count,
                  peer.getId(),
                  nextSlot);
              if (count.compareTo(MIN_SLOTS_TO_PROGRESS_PER_REQUEST) > 0
                  && startSlot.plus(MIN_SLOTS_TO_PROGRESS_PER_REQUEST).compareTo(nextSlot) > 0) {
                final int throttledRequests = throttledRequestCount.incrementAndGet();
                LOG.debug(
                    "Received {} consecutive excessively throttled response from {}",
                    throttledRequests,
                    peer.getId());
                if (throttledRequests > MAX_THROTTLED_REQUESTS) {
                  LOG.debug(
                      "Rejecting peer {} as sync target because it excessively throttled returned blocks",
                      peer.getId());
                  return SafeFuture.completedFuture(PeerSyncResult.EXCESSIVE_THROTTLING);
                }
              } else {
                throttledRequestCount.set(0);
              }
              return executeSync(peer, nextSlot, blockRequest.getReadyForNextRequest(), false);
            })
        .exceptionally(err -> handleFailedRequestToPeer(peer, err));
  }

  private PeerSyncResult handleFailedRequestToPeer(Eth2Peer peer, Throwable err) {
    Throwable rootException = Throwables.getRootCause(err);
    if (rootException instanceof FailedBlockImportException) {
      final FailedBlockImportException importException = (FailedBlockImportException) rootException;
      final FailureReason reason = importException.getResult().getFailureReason();
      final SignedBeaconBlock block = importException.getBlock();
      LOG.warn("Failed to import block from peer (err: {}) {}: {}", reason, block, peer);
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

    if (rootException instanceof BlocksByRangeResponseInvalidResponseException) {
      disconnectFromPeer(peer);
      return PeerSyncResult.INVALID_RESPONSE;
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
      LOG.debug(
          "Disconnecting from peer ({}) due to inaccurate advertised finalized block at {}",
          peer,
          status.getFinalizedEpoch());
      disconnectFromPeer(peer);
      return SafeFuture.completedFuture(PeerSyncResult.FAULTY_ADVERTISEMENT);
    }
  }

  private UInt64 calculateNumberOfBlocksToRequest(final UInt64 nextSlot, final PeerStatus status) {
    if (nextSlot.compareTo(status.getHeadSlot()) > 0) {
      // We've synced the advertised head, nothing left to request
      return UInt64.ZERO;
    }

    final UInt64 diff = status.getHeadSlot().minus(nextSlot).plus(UInt64.ONE);
    return diff.compareTo(MAX_BLOCK_BY_RANGE_REQUEST_SIZE) > 0
        ? MAX_BLOCK_BY_RANGE_REQUEST_SIZE
        : diff;
  }

  private SafeFuture<?> blockResponseListener(final SignedBeaconBlock block) {
    if (stopped.get()) {
      throw new CancellationException("Peer sync was cancelled");
    }
    return blockImporter
        .importBlock(block)
        .thenAccept(
            (result) -> {
              LOG.trace("Block import result for block at {}: {}", block.getSlot(), result);
              if (!result.isSuccessful()) {
                this.blockImportFailureResult.inc();
                throw new FailedBlockImportException(block, result);
              } else {
                this.blockImportSuccessResult.inc();
              }
            });
  }

  private void disconnectFromPeer(Eth2Peer peer) {
    peer.disconnectCleanly(DisconnectReason.REMOTE_FAULT);
  }

  public UInt64 getStartingSlot() {
    return startingSlot;
  }
}
