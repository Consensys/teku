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
import com.google.common.primitives.UnsignedLong;
import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import tech.pegasys.teku.core.results.BlockImportResult;
import tech.pegasys.teku.core.results.BlockImportResult.FailureReason;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.metrics.TekuMetricCategory;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.peers.PeerStatus;
import tech.pegasys.teku.networking.p2p.peer.DisconnectRequestHandler.DisconnectReason;
import tech.pegasys.teku.statetransition.blockimport.BlockImporter;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.util.async.AsyncRunner;
import tech.pegasys.teku.util.async.SafeFuture;

public class PeerSync {
  private static final Duration NEXT_REQUEST_TIMEOUT = Duration.ofSeconds(3);

  private static final Logger LOG = LogManager.getLogger();
  private static final UnsignedLong STEP = UnsignedLong.ONE;

  private final AtomicBoolean stopped = new AtomicBoolean(false);
  private final RecentChainData storageClient;
  private final BlockImporter blockImporter;

  private final AsyncRunner asyncRunner;
  private final Counter blockImportSuccessResult;
  private final Counter blockImportFailureResult;

  private volatile UnsignedLong startingSlot = UnsignedLong.valueOf(0);

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
    final UnsignedLong finalizedEpoch = storageClient.getFinalizedEpoch();
    final UnsignedLong latestFinalizedSlot = compute_start_slot_at_epoch(finalizedEpoch);
    final UnsignedLong firstNonFinalSlot = latestFinalizedSlot.plus(UnsignedLong.ONE);

    this.startingSlot = firstNonFinalSlot;

    return executeSync(peer, peer.getStatus(), firstNonFinalSlot, SafeFuture.COMPLETE)
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
      final PeerStatus status,
      final UnsignedLong startSlot,
      final SafeFuture<Void> readyForRequest) {
    if (stopped.get()) {
      return SafeFuture.completedFuture(PeerSyncResult.CANCELLED);
    }

    final UnsignedLong count = calculateNumberOfBlocksToRequest(startSlot, status);
    if (count.longValue() == 0) {
      return completeSyncWithPeer(peer, status);
    }

    return readyForRequest
        .thenCompose(
            (__) -> {
              LOG.debug(
                  "Request {} blocks starting at {} from peer {}", count, startSlot, peer.getId());
              final SafeFuture<Void> readyForNextRequest =
                  asyncRunner.getDelayedFuture(
                      NEXT_REQUEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
              return peer.requestBlocksByRange(startSlot, count, STEP, this::blockResponseListener)
                  .thenApply((res) -> readyForNextRequest);
            })
        .thenCompose(
            (readyForNextRequest) -> {
              LOG.trace(
                  "Completed request for {} blocks starting at {} from peer {}",
                  count,
                  startSlot,
                  peer.getId());
              final UnsignedLong nextSlot = startSlot.plus(count);
              return executeSync(peer, status, nextSlot, readyForNextRequest);
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

  private void blockResponseListener(final SignedBeaconBlock block) {
    if (stopped.get()) {
      throw new CancellationException("Peer sync was cancelled");
    }
    final BlockImportResult result = blockImporter.importBlock(block);
    LOG.trace("Block import result for block at {}: {}", block.getMessage().getSlot(), result);
    if (!result.isSuccessful()) {
      this.blockImportFailureResult.inc();
      throw new FailedBlockImportException(block, result);
    } else {
      this.blockImportSuccessResult.inc();
    }
  }

  private void disconnectFromPeer(Eth2Peer peer) {
    peer.disconnectCleanly(DisconnectReason.REMOTE_FAULT);
  }

  public UnsignedLong getStartingSlot() {
    return startingSlot;
  }
}
