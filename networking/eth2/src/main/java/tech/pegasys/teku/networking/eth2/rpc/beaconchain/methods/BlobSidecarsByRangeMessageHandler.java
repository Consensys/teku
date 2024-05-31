/*
 * Copyright Consensys Software Inc., 2023
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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSortedMap;
import java.nio.channels.ClosedChannelException;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.concurrent.atomic.AtomicInteger;
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
import tech.pegasys.teku.networking.eth2.peers.RequestApproval;
import tech.pegasys.teku.networking.eth2.rpc.core.PeerRequiredLocalMessageHandler;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseCallback;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.ResourceUnavailableException;
import tech.pegasys.teku.networking.p2p.rpc.StreamClosedException;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobSidecarsByRangeRequestMessage;
import tech.pegasys.teku.spec.datastructures.util.SlotAndBlockRootAndBlobIndex;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

/**
 * <a
 * href="https://github.com/ethereum/consensus-specs/blob/dev/specs/deneb/p2p-interface.md#blobsidecarsbyrange-v1">BlobSidecarsByRange
 * v1</a>
 */
public class BlobSidecarsByRangeMessageHandler
    extends PeerRequiredLocalMessageHandler<BlobSidecarsByRangeRequestMessage, BlobSidecar> {

  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final SpecConfigDeneb specConfigDeneb;
  private final CombinedChainDataClient combinedChainDataClient;
  private final LabelledMetric<Counter> requestCounter;
  private final Counter totalBlobSidecarsRequestedCounter;

  public BlobSidecarsByRangeMessageHandler(
      final Spec spec,
      final SpecConfigDeneb specConfigDeneb,
      final MetricsSystem metricsSystem,
      final CombinedChainDataClient combinedChainDataClient) {
    this.spec = spec;
    this.specConfigDeneb = specConfigDeneb;
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
  public Optional<RpcException> validateRequest(
      final String protocolId, final BlobSidecarsByRangeRequestMessage request) {

    final long requestedCount = calculateRequestedCount(request);

    if (requestedCount > specConfigDeneb.getMaxRequestBlobSidecars()) {
      requestCounter.labels("count_too_big").inc();
      return Optional.of(
          new RpcException(
              INVALID_REQUEST_CODE,
              String.format(
                  "Only a maximum of %s blob sidecars can be requested per request",
                  specConfigDeneb.getMaxRequestBlobSidecars())));
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
    final UInt64 endSlot = message.getMaxSlot();

    LOG.trace(
        "Peer {} requested {} slots of blob sidecars starting at slot {}.",
        peer.getId(),
        message.getCount(),
        startSlot);

    final long requestedCount = calculateRequestedCount(message);

    final Optional<RequestApproval> blobSidecarsRequestApproval =
        peer.approveBlobSidecarsRequest(callback, requestedCount);

    if (!peer.approveRequest() || blobSidecarsRequestApproval.isEmpty()) {
      requestCounter.labels("rate_limited").inc();
      return;
    }

    requestCounter.labels("ok").inc();
    totalBlobSidecarsRequestedCounter.inc(message.getCount().longValue());

    combinedChainDataClient
        .getEarliestAvailableBlobSidecarSlot()
        .thenCompose(
            earliestAvailableSlot -> {
              final UInt64 requestEpoch = spec.computeEpochAtSlot(startSlot);
              if (spec.isAvailabilityOfBlobSidecarsRequiredAtEpoch(
                      combinedChainDataClient.getStore(), requestEpoch)
                  && !checkBlobSidecarsAreAvailable(earliestAvailableSlot, endSlot)) {
                return SafeFuture.failedFuture(
                    new ResourceUnavailableException("Requested blob sidecars are not available."));
              }

              UInt64 finalizedSlot =
                  combinedChainDataClient.getFinalizedBlockSlot().orElse(UInt64.ZERO);

              final SortedMap<UInt64, Bytes32> canonicalHotRoots;
              if (endSlot.isGreaterThan(finalizedSlot)) {
                final UInt64 hotSlotsCount = endSlot.increment().minusMinZero(startSlot);

                canonicalHotRoots =
                    combinedChainDataClient.getAncestorRoots(startSlot, UInt64.ONE, hotSlotsCount);

                // refresh finalized slot to avoid race condition that can occur if we finalize just
                // before getting hot canonical roots
                finalizedSlot = combinedChainDataClient.getFinalizedBlockSlot().orElse(UInt64.ZERO);
              } else {
                canonicalHotRoots = ImmutableSortedMap.of();
              }

              final RequestState initialState =
                  new RequestState(callback, startSlot, endSlot, canonicalHotRoots, finalizedSlot);
              if (message.getCount().isZero()) {
                return SafeFuture.completedFuture(initialState);
              }
              return sendBlobSidecars(initialState);
            })
        .finish(
            requestState -> {
              final int sentBlobSidecars = requestState.sentBlobSidecars.get();
              if (sentBlobSidecars != requestedCount) {
                peer.adjustBlobSidecarsRequest(blobSidecarsRequestApproval.get(), sentBlobSidecars);
              }
              LOG.trace("Sent {} blob sidecars to peer {}.", sentBlobSidecars, peer.getId());
              callback.completeSuccessfully();
            },
            error -> {
              peer.adjustBlobSidecarsRequest(blobSidecarsRequestApproval.get(), 0);
              handleProcessingRequestError(error, callback);
            });
  }

  private long calculateRequestedCount(final BlobSidecarsByRangeRequestMessage message) {
    return specConfigDeneb.getMaxBlobsPerBlock() * message.getCount().longValue();
  }

  private boolean checkBlobSidecarsAreAvailable(
      final Optional<UInt64> earliestAvailableSidecarSlot, final UInt64 requestSlot) {
    return earliestAvailableSidecarSlot
        .map(earliestSlot -> earliestSlot.isLessThanOrEqualTo(requestSlot))
        .orElse(false);
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
        LOG.trace("Stream closed while sending requested blob sidecars", error);
      } else {
        LOG.error("Failed to process blob sidecars request", error);
      }
      callback.completeWithUnexpectedError(error);
    }
  }

  @VisibleForTesting
  class RequestState {

    private final ResponseCallback<BlobSidecar> callback;
    private final UInt64 startSlot;
    private final UInt64 endSlot;
    private final UInt64 finalizedSlot;
    private final Map<UInt64, Bytes32> canonicalHotRoots;

    private final AtomicInteger sentBlobSidecars = new AtomicInteger(0);

    // since our storage stores hot and finalized blobs on the same "table", this iterator can span
    // over hot and finalized blobs
    private Optional<Iterator<SlotAndBlockRootAndBlobIndex>> blobSidecarKeysIterator =
        Optional.empty();

    RequestState(
        final ResponseCallback<BlobSidecar> callback,
        final UInt64 startSlot,
        final UInt64 endSlot,
        final Map<UInt64, Bytes32> canonicalHotRoots,
        final UInt64 finalizedSlot) {
      this.callback = callback;
      this.startSlot = startSlot;
      this.endSlot = endSlot;
      this.finalizedSlot = finalizedSlot;
      this.canonicalHotRoots = canonicalHotRoots;
    }

    SafeFuture<Void> sendBlobSidecar(final BlobSidecar blobSidecar) {
      return callback.respond(blobSidecar).thenRun(sentBlobSidecars::incrementAndGet);
    }

    SafeFuture<Optional<BlobSidecar>> loadNextBlobSidecar() {
      if (blobSidecarKeysIterator.isEmpty()) {
        return combinedChainDataClient
            .getBlobSidecarKeys(startSlot, endSlot, specConfigDeneb.getMaxRequestBlobSidecars())
            .thenCompose(
                keys -> {
                  blobSidecarKeysIterator = Optional.of(keys.iterator());
                  return getNextBlobSidecar(blobSidecarKeysIterator.get());
                });
      } else {
        return getNextBlobSidecar(blobSidecarKeysIterator.get());
      }
    }

    private SafeFuture<Optional<BlobSidecar>> getNextBlobSidecar(
        final Iterator<SlotAndBlockRootAndBlobIndex> blobSidecarKeysIterator) {
      if (blobSidecarKeysIterator.hasNext()) {
        final SlotAndBlockRootAndBlobIndex slotAndBlockRootAndBlobIndex =
            blobSidecarKeysIterator.next();

        if (finalizedSlot.isGreaterThanOrEqualTo(slotAndBlockRootAndBlobIndex.getSlot())) {
          return combinedChainDataClient.getBlobSidecarByKey(slotAndBlockRootAndBlobIndex);
        }

        // not finalized, let's check if it is on canonical chain
        if (isCanonicalHotBlobSidecar(slotAndBlockRootAndBlobIndex)) {
          return combinedChainDataClient.getBlobSidecarByKey(slotAndBlockRootAndBlobIndex);
        }

        // non-canonical, try next one
        return getNextBlobSidecar(blobSidecarKeysIterator);
      }

      return SafeFuture.completedFuture(Optional.empty());
    }

    private boolean isCanonicalHotBlobSidecar(
        final SlotAndBlockRootAndBlobIndex slotAndBlockRootAndBlobIndex) {
      return Optional.ofNullable(canonicalHotRoots.get(slotAndBlockRootAndBlobIndex.getSlot()))
          .map(blockRoot -> blockRoot.equals(slotAndBlockRootAndBlobIndex.getBlockRoot()))
          .orElse(false);
    }

    boolean isComplete() {
      return blobSidecarKeysIterator.map(iterator -> !iterator.hasNext()).orElse(false);
    }
  }
}
