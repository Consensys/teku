/*
 * Copyright ConsenSys Software Inc., 2022
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

import static tech.pegasys.teku.spec.config.Constants.MIN_EPOCHS_FOR_BLOBS_SIDECARS_REQUESTS;

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
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.rpc.core.PeerRequiredLocalMessageHandler;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseCallback;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.ResourceUnavailableException;
import tech.pegasys.teku.networking.p2p.rpc.StreamClosedException;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.BlobsSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobsSidecarsByRangeRequestMessage;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

public class BlobsSidecarsByRangeMessageHandler
    extends PeerRequiredLocalMessageHandler<BlobsSidecarsByRangeRequestMessage, BlobsSidecar> {

  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final UInt64 denebForkEpoch;
  private final CombinedChainDataClient combinedChainDataClient;
  private final UInt64 maxRequestSize;
  private final LabelledMetric<Counter> requestCounter;
  private final Counter totalBlobsSidecarsRequestedCounter;

  public BlobsSidecarsByRangeMessageHandler(
      final Spec spec,
      final UInt64 denebForkEpoch,
      final MetricsSystem metricsSystem,
      final CombinedChainDataClient combinedChainDataClient,
      final UInt64 maxRequestSize) {
    this.spec = spec;
    this.denebForkEpoch = denebForkEpoch;
    this.combinedChainDataClient = combinedChainDataClient;
    this.maxRequestSize = maxRequestSize;
    requestCounter =
        metricsSystem.createLabelledCounter(
            TekuMetricCategory.NETWORK,
            "rpc_blobs_sidecars_by_range_requests_total",
            "Total number of blobs sidecars by range requests received",
            "status");
    totalBlobsSidecarsRequestedCounter =
        metricsSystem.createCounter(
            TekuMetricCategory.NETWORK,
            "rpc_blobs_sidecars_by_range_requested_sidecars_total",
            "Total number of sidecars requested in accepted blobs sidecars by range requests from peers");
  }

  @Override
  public void onIncomingMessage(
      final String protocolId,
      final Eth2Peer peer,
      final BlobsSidecarsByRangeRequestMessage message,
      final ResponseCallback<BlobsSidecar> callback) {
    final UInt64 startSlot = message.getStartSlot();
    LOG.trace(
        "Peer {} requested {} blobs sidecars starting at slot {}.",
        peer.getId(),
        message.getCount(),
        startSlot);

    if (!peer.wantToMakeRequest()
        || !peer.wantToReceiveBlobsSidecars(
            callback, maxRequestSize.min(message.getCount()).longValue())) {
      requestCounter.labels("rate_limited").inc();
      return;
    }
    requestCounter.labels("ok").inc();
    totalBlobsSidecarsRequestedCounter.inc(message.getCount().longValue());

    final Bytes32 headBlockRoot =
        combinedChainDataClient
            .getBestBlockRoot()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Can't retrieve the block root chosen by fork choice."));

    combinedChainDataClient
        .getEarliestAvailableBlobsSidecarEpoch()
        .thenCompose(
            earliestAvailableEpoch -> {
              final UInt64 requestEpoch = spec.computeEpochAtSlot(startSlot);
              if (checkRequestInMinEpochsRange(requestEpoch)
                  && !checkBlobsSidecarsAreAvailable(earliestAvailableEpoch, requestEpoch)) {
                return SafeFuture.failedFuture(
                    new ResourceUnavailableException(
                        "Requested blobs sidecars are not available."));
              }
              final RequestState initialState =
                  new RequestState(callback, headBlockRoot, startSlot, message.getMaxSlot());
              return sendBlobsSidecars(initialState);
            })
        .finish(
            requestState -> {
              final int sentBlobsSidecars = requestState.sentBlobsSidecars.get();
              LOG.trace("Sent {} blobs sidecars to peer {}.", sentBlobsSidecars, peer.getId());
              callback.completeSuccessfully();
            },
            error -> handleProcessingRequestError(error, callback));
  }

  /**
   * TODO: Update link for Deneb
   *
   * <p>See <a
   * href="https://github.com/ethereum/consensus-specs/blob/dev/specs/eip4844/p2p-interface.md#blobssidecarsbyrange-v1">BlobsSidecarsByRange
   * v1</a>
   */
  private boolean checkBlobsSidecarsAreAvailable(
      final Optional<UInt64> earliestAvailableSidecarEpoch, final UInt64 requestEpoch) {
    return earliestAvailableSidecarEpoch
        .map(earliestEpoch -> earliestEpoch.isLessThanOrEqualTo(requestEpoch))
        .orElse(false);
  }

  private boolean checkRequestInMinEpochsRange(final UInt64 requestEpoch) {
    final UInt64 currentEpoch = combinedChainDataClient.getCurrentEpoch();
    final UInt64 minEpochForBlobsSidecar =
        denebForkEpoch.max(currentEpoch.minusMinZero(MIN_EPOCHS_FOR_BLOBS_SIDECARS_REQUESTS));
    return requestEpoch.isGreaterThanOrEqualTo(minEpochForBlobsSidecar)
        && requestEpoch.isLessThanOrEqualTo(currentEpoch);
  }

  private SafeFuture<RequestState> sendBlobsSidecars(final RequestState requestState) {
    return requestState
        .loadNextBlobsSidecar()
        .thenCompose(
            maybeSidecar ->
                maybeSidecar.map(requestState::sendBlobsSidecar).orElse(SafeFuture.COMPLETE))
        .thenCompose(
            __ -> {
              if (requestState.isComplete()) {
                return SafeFuture.completedFuture(requestState);
              } else {
                requestState.incrementCurrentSlot();
                return sendBlobsSidecars(requestState);
              }
            });
  }

  private void handleProcessingRequestError(
      final Throwable error, final ResponseCallback<BlobsSidecar> callback) {
    final Throwable rootCause = Throwables.getRootCause(error);
    if (rootCause instanceof RpcException) {
      LOG.trace("Rejecting blobs sidecars by range request", error);
      callback.completeWithErrorResponse((RpcException) rootCause);
    } else {
      if (rootCause instanceof StreamClosedException
          || rootCause instanceof ClosedChannelException) {
        LOG.trace("Stream closed while sending requested blobs sidecars", error);
      } else {
        LOG.error("Failed to process blobs by sidecars request", error);
      }
      callback.completeWithUnexpectedError(error);
    }
  }

  private class RequestState {

    private final AtomicInteger sentBlobsSidecars = new AtomicInteger(0);

    private final ResponseCallback<BlobsSidecar> callback;
    private final Bytes32 headBlockRoot;
    private final AtomicReference<UInt64> currentSlot;
    private final UInt64 maxSlot;

    RequestState(
        final ResponseCallback<BlobsSidecar> callback,
        final Bytes32 headBlockRoot,
        final UInt64 currentSlot,
        final UInt64 maxSlot) {
      this.callback = callback;
      this.headBlockRoot = headBlockRoot;
      this.currentSlot = new AtomicReference<>(currentSlot);
      this.maxSlot = maxSlot;
    }

    SafeFuture<Void> sendBlobsSidecar(final BlobsSidecar blobsSidecar) {
      return callback.respond(blobsSidecar).thenRun(sentBlobsSidecars::incrementAndGet);
    }

    SafeFuture<Optional<BlobsSidecar>> loadNextBlobsSidecar() {
      return combinedChainDataClient
          .getBlockAtSlotExact(currentSlot.get(), headBlockRoot)
          .thenCompose(
              block ->
                  block
                      .map(SignedBeaconBlock::getRoot)
                      .map(
                          blockRoot ->
                              combinedChainDataClient.getBlobsSidecarBySlotAndBlockRoot(
                                  currentSlot.get(), blockRoot))
                      .orElse(SafeFuture.completedFuture(Optional.empty())));
    }

    boolean isComplete() {
      return currentSlot.get().isGreaterThanOrEqualTo(maxSlot)
          || maxRequestSize.isLessThanOrEqualTo(sentBlobsSidecars.get());
    }

    void incrementCurrentSlot() {
      currentSlot.updateAndGet(UInt64::increment);
    }
  }
}
