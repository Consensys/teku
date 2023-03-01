/*
 * Copyright ConsenSys Software Inc., 2023
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

package tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods;

import static tech.pegasys.teku.spec.config.Constants.MAX_REQUEST_BLOCKS_DENEB;
import static tech.pegasys.teku.spec.config.Constants.MIN_EPOCHS_FOR_BLOB_SIDECARS_REQUESTS;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import java.nio.channels.ClosedChannelException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.rpc.core.PeerRequiredLocalMessageHandler;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseCallback;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.networking.p2p.rpc.StreamClosedException;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobSidecarsByRangeRequestMessage;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

public class BlobSidecarsByRangeMessageHandler
    extends PeerRequiredLocalMessageHandler<BlobSidecarsByRangeRequestMessage, BlobSidecar> {

  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final UInt64 denebForkEpoch;
  private final CombinedChainDataClient combinedChainDataClient;
  private final LabelledMetric<Counter> requestCounter;
  private final Counter totalBlobSidecarsRequestedCounter;

  public BlobSidecarsByRangeMessageHandler(
      final Spec spec,
      final UInt64 denebForkEpoch,
      final MetricsSystem metricsSystem,
      final CombinedChainDataClient combinedChainDataClient) {
    this.spec = spec;
    this.denebForkEpoch = denebForkEpoch;
    this.combinedChainDataClient = combinedChainDataClient;
    requestCounter =
        metricsSystem.createLabelledCounter(
            TekuMetricCategory.NETWORK,
            "rpc_blob_sidecars_by_range_requests_total",
            "Total number of blob sidecars by range requests received",
            "status");
    totalBlobSidecarsRequestedCounter =
        metricsSystem.createCounter(
            TekuMetricCategory.NETWORK,
            "rpc_blob_sidecars_by_range_requested_sidecars_total",
            "Total number of blob sidecars requested in accepted blob sidecars by range requests from peers");
  }

  @Override
  public void onIncomingMessage(
      final String protocolId,
      final Eth2Peer peer,
      final BlobSidecarsByRangeRequestMessage message,
      final ResponseCallback<BlobSidecar> callback) {
    final UInt64 startSlot = message.getStartSlot();
    LOG.trace(
        "Peer {} requested {} blob sidecars starting at slot {}.",
        peer.getId(),
        message.getCount(),
        startSlot);

    final UInt64 maxBlobsPerBlock = getMaxBlobsPerBlock(startSlot);
    final UInt64 maxRequestBlobSidecars = MAX_REQUEST_BLOCKS_DENEB.times(maxBlobsPerBlock);

    if (!peer.wantToMakeRequest()
        || !peer.wantToReceiveBlobSidecars(
            callback, maxRequestBlobSidecars.min(message.getCount()).longValue())) {
      requestCounter.labels("rate_limited").inc();
      return;
    }
    requestCounter.labels("ok").inc();
    totalBlobSidecarsRequestedCounter.inc(message.getCount().longValue());

    final Bytes32 headBlockRoot =
        combinedChainDataClient
            .getBestBlockRoot()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Can't retrieve the block root chosen by fork choice."));

    combinedChainDataClient
        .getEarliestAvailableBlobSidecarEpoch()
        .thenCompose(
            earliestAvailableEpoch -> {
              final UInt64 requestEpoch = spec.computeEpochAtSlot(startSlot);
              if (checkRequestInMinEpochsRange(requestEpoch)
                  && !checkBlobSidecarsAreAvailable(earliestAvailableEpoch, requestEpoch)) {
                return SafeFuture.failedFuture(
                    new RpcException.ResourceUnavailableException(
                        "Requested blob sidecars are not available."));
              }
              final BlobSidecarsByRangeMessageHandler.RequestState initialState =
                  new BlobSidecarsByRangeMessageHandler.RequestState(
                      callback,
                      maxBlobsPerBlock,
                      maxRequestBlobSidecars,
                      headBlockRoot,
                      startSlot,
                      message.getMaxSlot());
              if (initialState.isComplete()) {
                return SafeFuture.completedFuture(initialState);
              }
              return sendBlobSidecars(initialState);
            })
        .finish(
            requestState -> {
              final int sentBlobSidecars = requestState.sentBlobSidecars.get();
              LOG.trace("Sent {} blob sidecars to peer {}.", sentBlobSidecars, peer.getId());
              callback.completeSuccessfully();
            },
            error -> handleProcessingRequestError(error, callback));
  }

  @VisibleForTesting
  UInt64 getMaxBlobsPerBlock(final UInt64 slot) {
    final int maxBlobsPerBlock =
        SpecConfigDeneb.required(spec.atSlot(slot).getConfig()).getMaxBlobsPerBlock();
    return UInt64.valueOf(maxBlobsPerBlock);
  }

  private boolean checkBlobSidecarsAreAvailable(
      final Optional<UInt64> earliestAvailableSidecarEpoch, final UInt64 requestEpoch) {
    return earliestAvailableSidecarEpoch
        .map(earliestEpoch -> earliestEpoch.isLessThanOrEqualTo(requestEpoch))
        .orElse(false);
  }

  private boolean checkRequestInMinEpochsRange(final UInt64 requestEpoch) {
    final UInt64 currentEpoch = combinedChainDataClient.getCurrentEpoch();
    final UInt64 minEpochForBlobsSidecar =
        denebForkEpoch.max(currentEpoch.minusMinZero(MIN_EPOCHS_FOR_BLOB_SIDECARS_REQUESTS));
    return requestEpoch.isGreaterThanOrEqualTo(minEpochForBlobsSidecar)
        && requestEpoch.isLessThanOrEqualTo(currentEpoch);
  }

  private SafeFuture<RequestState> sendBlobSidecars(final RequestState requestState) {
    return requestState
        .loadNextBlobSidecar()
        .thenCompose(
            maybeBlobSidecar ->
                maybeBlobSidecar.map(requestState::sendBlobSidecar).orElse(SafeFuture.COMPLETE))
        .thenCompose(
            __ -> {
              requestState.incrementSlotAndIndex();
              if (requestState.isComplete()) {
                return SafeFuture.completedFuture(requestState);
              } else {
                return sendBlobSidecars(requestState);
              }
            });
  }

  private void handleProcessingRequestError(
      final Throwable error, final ResponseCallback<BlobSidecar> callback) {
    final Throwable rootCause = Throwables.getRootCause(error);
    if (rootCause instanceof RpcException) {
      LOG.trace("Rejecting blob sidecars by range request", error);
      callback.completeWithErrorResponse((RpcException) rootCause);
    } else {
      if (rootCause instanceof StreamClosedException
          || rootCause instanceof ClosedChannelException) {
        LOG.trace("Stream closed while sending requested blobs sidecars", error);
      } else {
        LOG.error("Failed to process blob sidecars request", error);
      }
      callback.completeWithUnexpectedError(error);
    }
  }

  @VisibleForTesting
  class RequestState {

    private final AtomicInteger sentBlobSidecars = new AtomicInteger(0);
    private final ResponseCallback<BlobSidecar> callback;
    private final UInt64 maxBlobsPerBlock;
    private final UInt64 maxRequestBlobSidecars;
    private final Bytes32 headBlockRoot;
    private final AtomicReference<UInt64> currentSlot;
    private final AtomicReference<UInt64> currentIndex;
    private final UInt64 maxSlot;

    private Optional<SignedBeaconBlock> maybeCurrentBlock = Optional.empty();

    RequestState(
        final ResponseCallback<BlobSidecar> callback,
        final UInt64 maxBlobsPerBlock,
        final UInt64 maxRequestBlobSidecars,
        final Bytes32 headBlockRoot,
        final UInt64 currentSlot,
        final UInt64 maxSlot) {
      this.callback = callback;
      this.maxBlobsPerBlock = maxBlobsPerBlock;
      this.maxRequestBlobSidecars = maxRequestBlobSidecars;
      this.headBlockRoot = headBlockRoot;
      this.currentSlot = new AtomicReference<>(currentSlot);
      this.currentIndex = new AtomicReference<>(UInt64.ZERO);
      this.maxSlot = maxSlot;
    }

    @VisibleForTesting
    UInt64 getCurrentSlot() {
      return currentSlot.get();
    }

    @VisibleForTesting
    UInt64 getCurrentIndex() {
      return currentIndex.get();
    }

    SafeFuture<Void> sendBlobSidecar(final BlobSidecar blobSidecar) {
      return callback.respond(blobSidecar).thenRun(sentBlobSidecars::incrementAndGet);
    }

    SafeFuture<Optional<BlobSidecar>> loadNextBlobSidecar() {
      // currentBlock is used to avoid querying the combinedChainDataClient for the same slot again
      if (maybeCurrentBlock.isEmpty()) {
        return combinedChainDataClient
            .getBlockAtSlotExact(currentSlot.get(), headBlockRoot)
            .thenCompose(
                block -> {
                  maybeCurrentBlock = block;
                  return retrieveBlobSidecar();
                });
      } else {
        return retrieveBlobSidecar();
      }
    }

    boolean isComplete() {
      return currentSlot.get().isGreaterThan(maxSlot)
          || maxRequestBlobSidecars.isLessThanOrEqualTo(sentBlobSidecars.get());
    }

    void incrementSlotAndIndex() {
      if (currentIndex.get().equals(maxBlobsPerBlock.minus(UInt64.ONE))) {
        currentIndex.set(UInt64.ZERO);
        currentSlot.updateAndGet(UInt64::increment);
      } else {
        currentIndex.updateAndGet(UInt64::increment);
      }
    }

    @NotNull
    private SafeFuture<Optional<BlobSidecar>> retrieveBlobSidecar() {
      SafeFuture<Optional<BlobSidecar>> blobSidecar =
          maybeCurrentBlock
              .map(SignedBeaconBlock::getRoot)
              .map(
                  blockRoot ->
                      combinedChainDataClient.getBlobSidecarByBlockRootAndIndex(
                          blockRoot, currentIndex.get()))
              .orElse(SafeFuture.completedFuture(Optional.empty()));

      refreshCurrentBlock();

      return blobSidecar;
    }

    private void refreshCurrentBlock() {
      maybeCurrentBlock.ifPresent(
          block -> {
            if (block.getSlot().equals(currentSlot.get())
                && currentIndex.get().equals(maxBlobsPerBlock.minus(UInt64.ONE))) {
              maybeCurrentBlock = Optional.empty();
            }
          });
    }
  }
}
