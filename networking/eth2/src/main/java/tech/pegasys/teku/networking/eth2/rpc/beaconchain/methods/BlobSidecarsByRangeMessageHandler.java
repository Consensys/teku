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

import static tech.pegasys.teku.spec.config.Constants.MIN_EPOCHS_FOR_BLOB_SIDECARS_REQUESTS;

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
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobSidecarsByRangeRequestMessage;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.RecentChainData;

public class BlobSidecarsByRangeMessageHandler
    extends PeerRequiredLocalMessageHandler<BlobSidecarsByRangeRequestMessage, BlobSidecar> {

  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final UInt64 denebForkEpoch;
  private final RecentChainData recentChainData;
  private final CombinedChainDataClient combinedChainDataClient;
  private final UInt64 maxRequestSize;
  private final UInt64 maxBlobsPerBlock;
  private final LabelledMetric<Counter> requestCounter;
  private final Counter totalBlobSidecarsRequestedCounter;

  public BlobSidecarsByRangeMessageHandler(
      final Spec spec,
      final UInt64 denebForkEpoch,
      final MetricsSystem metricsSystem,
      final RecentChainData recentChainData,
      final CombinedChainDataClient combinedChainDataClient,
      final UInt64 maxRequestSize,
      final UInt64 maxBlobsPerBlock) {
    this.spec = spec;
    this.denebForkEpoch = denebForkEpoch;
    this.recentChainData = recentChainData;
    this.combinedChainDataClient = combinedChainDataClient;
    this.maxRequestSize = maxRequestSize;
    this.maxBlobsPerBlock = maxBlobsPerBlock;
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
  public Optional<RpcException> validateRequest(
      final String protocolId, final BlobSidecarsByRangeRequestMessage request) {
    final UInt64 finalizedEpoch = recentChainData.getFinalizedEpoch();
    final UInt64 minEpochForBlobSidecars =
        recentChainData
            .getCurrentEpoch()
            .orElse(UInt64.ZERO)
            .minusMinZero(MIN_EPOCHS_FOR_BLOB_SIDECARS_REQUESTS);
    final UInt64 minimumRequestEpoch =
        finalizedEpoch.max(minEpochForBlobSidecars).max(denebForkEpoch);

    if (!verifyRequestedEpochIsInSupportedRange(request.getStartSlot(), minimumRequestEpoch)) {
      requestCounter.labels("resource_unavailable").inc();
      return Optional.of(
          new RpcException.ResourceUnavailableException(
              "Can't request blob sidecars earlier than epoch " + minimumRequestEpoch));
    }
    return Optional.empty();
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

    if (!peer.wantToMakeRequest()
        || !peer.wantToReceiveBlobSidecars(
            callback, maxRequestSize.min(message.getCount()).longValue())) {
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
                      callback, headBlockRoot, startSlot, message.getMaxSlot());
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
              if (requestState.isComplete()) {
                return SafeFuture.completedFuture(requestState);
              } else {
                requestState.incrementSlotAndIndex();
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

  private boolean verifyRequestedEpochIsInSupportedRange(
      final UInt64 requestedSlot, final UInt64 minimumRequestEpoch) {
    return spec.computeEpochAtSlot(requestedSlot).isGreaterThanOrEqualTo(minimumRequestEpoch);
  }

  private class RequestState {

    private final AtomicInteger sentBlobSidecars = new AtomicInteger(0);
    private final ResponseCallback<BlobSidecar> callback;
    private final Bytes32 headBlockRoot;
    private final AtomicReference<UInt64> currentSlot;
    private final AtomicReference<UInt64> currentIndex;
    private final UInt64 maxSlot;

    private Optional<SignedBeaconBlock> maybeCurrentBlock = Optional.empty();

    RequestState(
        final ResponseCallback<BlobSidecar> callback,
        final Bytes32 headBlockRoot,
        final UInt64 currentSlot,
        final UInt64 maxSlot) {
      this.callback = callback;
      this.headBlockRoot = headBlockRoot;
      this.currentSlot = new AtomicReference<>(currentSlot);
      this.currentIndex = new AtomicReference<>(UInt64.ZERO);
      this.maxSlot = maxSlot;
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
      return (currentSlot.get().equals(maxSlot)
              && currentIndex.get().equals(maxBlobsPerBlock.minus(UInt64.ONE)))
          || maxRequestSize.isLessThanOrEqualTo(sentBlobSidecars.get());
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
                      combinedChainDataClient.getBlobSidecarBySlotIndexAndBlockRoot(
                          currentSlot.get(), currentIndex.get(), blockRoot))
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
