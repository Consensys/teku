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

import static tech.pegasys.teku.networking.eth2.rpc.core.RpcResponseStatus.INVALID_REQUEST_CODE;

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
import tech.pegasys.teku.networking.p2p.rpc.StreamClosedException;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip4844.BlobsSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobsSidecarsByRangeRequestMessage;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

public class BlobsSidecarsByRangeMessageHandler
    extends PeerRequiredLocalMessageHandler<BlobsSidecarsByRangeRequestMessage, BlobsSidecar> {

  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final CombinedChainDataClient combinedChainDataClient;
  private final UInt64 maxRequestSize;
  private final LabelledMetric<Counter> requestCounter;
  private final Counter totalBlobsSidecarsRequestedCounter;

  public BlobsSidecarsByRangeMessageHandler(
      final Spec spec,
      final MetricsSystem metricsSystem,
      final CombinedChainDataClient combinedChainDataClient,
      final UInt64 maxRequestSize) {
    this.spec = spec;
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
  public Optional<RpcException> validateRequest(
      final String protocolId, final BlobsSidecarsByRangeRequestMessage request) {
    final UInt64 requestEpoch = spec.computeEpochAtSlot(request.getMaxSlot());
    final SpecMilestone latestMilestoneRequested =
        spec.getForkSchedule().getSpecMilestoneAtEpoch(requestEpoch);

    if (!latestMilestoneRequested.isGreaterThanOrEqualTo(SpecMilestone.EIP4844)) {
      return Optional.of(
          new RpcException(
              INVALID_REQUEST_CODE, "Can't request blobs sidecars before the EIP4844 milestone."));
    }

    return Optional.empty();
  }

  @Override
  protected void onIncomingMessage(
      final String protocolId,
      final Eth2Peer peer,
      final BlobsSidecarsByRangeRequestMessage message,
      final ResponseCallback<BlobsSidecar> callback) {
    LOG.trace(
        "Peer {} requested {} blobs sidecars starting at slot {}.",
        peer.getId(),
        message.getCount(),
        message.getStartSlot());
    requestCounter.labels("ok").inc();
    totalBlobsSidecarsRequestedCounter.inc(message.getCount().longValue());

    final Bytes32 headBlockRoot =
        combinedChainDataClient
            .getBestBlockRoot()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Can't retrieve the block root chosen by fork choice."));

    final RequestState initialState =
        new RequestState(callback, headBlockRoot, message.getStartSlot(), message.getMaxSlot());

    sendBlobsSidecars(initialState)
        .finish(
            requestState -> {
              LOG.trace(
                  "Sent {} blobs sidecars to peer {}.",
                  requestState.sentBlobsSidecars.get(),
                  peer.getId());
              callback.completeSuccessfully();
            },
            error -> handleProcessingRequestError(error, callback));
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
                      .map(combinedChainDataClient::getBlobsSidecarByBlockRoot)
                      .orElse(SafeFuture.completedFuture(Optional.empty())));
    }

    boolean isComplete() {
      return currentSlot.get().equals(maxSlot)
          || maxRequestSize.isLessThanOrEqualTo(sentBlobsSidecars.get());
    }

    void incrementCurrentSlot() {
      currentSlot.updateAndGet(UInt64::increment);
    }
  }
}
