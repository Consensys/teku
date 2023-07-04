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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
import tech.pegasys.teku.networking.p2p.rpc.StreamClosedException;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobIdentifier;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobSidecarsByRootRequestMessage;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

/**
 * <a
 * href="https://github.com/ethereum/consensus-specs/blob/dev/specs/deneb/p2p-interface.md#blobsidecarsbyroot-v1">BlobSidecarsByRoot
 * v1</a>
 */
public class BlobSidecarsByRootMessageHandler
    extends PeerRequiredLocalMessageHandler<BlobSidecarsByRootRequestMessage, BlobSidecar> {

  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final SpecConfigDeneb specConfigDeneb;
  private final CombinedChainDataClient combinedChainDataClient;

  private final LabelledMetric<Counter> requestCounter;
  private final Counter totalBlobSidecarsRequestedCounter;

  public BlobSidecarsByRootMessageHandler(
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
            "rpc_blob_sidecars_by_root_requests_total",
            "Total number of blob sidecars by root requests received",
            "status");
    totalBlobSidecarsRequestedCounter =
        metricsSystem.createCounter(
            TekuMetricCategory.NETWORK,
            "rpc_blob_sidecars_by_root_requested_blob_sidecars_total",
            "Total number of blob sidecars requested in accepted blob sidecars by root requests from peers");
  }

  @Override
  public Optional<RpcException> validateRequest(
      final String protocolId, final BlobSidecarsByRootRequestMessage request) {
    final int maxRequestBlobSidecars = specConfigDeneb.getMaxRequestBlobSidecars();
    if (request.size() > maxRequestBlobSidecars) {
      requestCounter.labels("count_too_big").inc();
      return Optional.of(
          new RpcException(
              INVALID_REQUEST_CODE,
              String.format(
                  "Only a maximum of %s blob sidecars can be requested per request",
                  maxRequestBlobSidecars)));
    }
    return Optional.empty();
  }

  @Override
  public void onIncomingMessage(
      final String protocolId,
      final Eth2Peer peer,
      final BlobSidecarsByRootRequestMessage message,
      final ResponseCallback<BlobSidecar> callback) {

    LOG.trace(
        "Peer {} requested {} blob sidecars with blob identifiers: {}",
        peer.getId(),
        message.size(),
        message);

    final Optional<RequestApproval> blobSidecarsRequestApproval =
        peer.approveBlobSidecarsRequest(callback, message.size());

    if (!peer.approveRequest() || blobSidecarsRequestApproval.isEmpty()) {
      requestCounter.labels("rate_limited").inc();
      return;
    }

    requestCounter.labels("ok").inc();
    totalBlobSidecarsRequestedCounter.inc(message.size());

    SafeFuture<Void> future = SafeFuture.COMPLETE;
    final AtomicInteger sentBlobSidecars = new AtomicInteger(0);
    final UInt64 finalizedEpoch = getFinalizedEpoch();

    for (final BlobIdentifier identifier : message) {
      future =
          future
              .thenCompose(__ -> retrieveBlobSidecar(identifier))
              .thenCompose(
                  maybeSidecar ->
                      validateMinimumRequestEpoch(identifier, maybeSidecar, finalizedEpoch)
                          .thenApply(__ -> maybeSidecar))
              .thenComposeChecked(
                  maybeSidecar ->
                      maybeSidecar
                          .map(
                              blobSidecar ->
                                  callback
                                      .respond(blobSidecar)
                                      .thenRun(sentBlobSidecars::incrementAndGet))
                          .orElse(SafeFuture.COMPLETE));
    }

    future.finish(
        () -> {
          if (sentBlobSidecars.get() != message.size()) {
            peer.adjustBlobSidecarsRequest(
                blobSidecarsRequestApproval.get(), sentBlobSidecars.get());
          }
          callback.completeSuccessfully();
        },
        err -> {
          peer.adjustBlobSidecarsRequest(blobSidecarsRequestApproval.get(), 0);
          handleError(callback, err);
        });
  }

  private UInt64 getFinalizedEpoch() {
    return combinedChainDataClient
        .getFinalizedBlock()
        .map(SignedBeaconBlock::getSlot)
        .map(spec::computeEpochAtSlot)
        .orElse(UInt64.ZERO);
  }

  /**
   * Validations:
   *
   * <ul>
   *   <li>The block root references a block greater than or equal to the minimum_request_epoch
   * </ul>
   */
  private SafeFuture<Void> validateMinimumRequestEpoch(
      final BlobIdentifier identifier,
      final Optional<BlobSidecar> maybeSidecar,
      final UInt64 finalizedEpoch) {
    return maybeSidecar
        .map(sidecar -> SafeFuture.completedFuture(Optional.of(sidecar.getSlot())))
        .orElse(
            combinedChainDataClient
                .getBlockByBlockRoot(identifier.getBlockRoot())
                .thenApply(maybeBlock -> maybeBlock.map(SignedBeaconBlock::getSlot)))
        .thenAcceptChecked(
            maybeSlot -> {
              if (maybeSlot.isEmpty()) {
                return;
              }
              final UInt64 requestedEpoch = spec.computeEpochAtSlot(maybeSlot.get());
              final UInt64 minimumRequestEpoch = computeMinimumRequestEpoch(finalizedEpoch);
              if (requestedEpoch.isLessThan(minimumRequestEpoch)) {
                throw new RpcException(
                    INVALID_REQUEST_CODE,
                    String.format(
                        "Block root (%s) references a block earlier than the minimum_request_epoch (%s)",
                        identifier.getBlockRoot(), minimumRequestEpoch));
              }
            });
  }

  private UInt64 computeMinimumRequestEpoch(final UInt64 finalizedEpoch) {
    final UInt64 currentEpoch = combinedChainDataClient.getCurrentEpoch();
    return finalizedEpoch
        .max(currentEpoch.minusMinZero(specConfigDeneb.getMinEpochsForBlobSidecarsRequests()))
        .max(specConfigDeneb.getDenebForkEpoch());
  }

  private SafeFuture<Optional<BlobSidecar>> retrieveBlobSidecar(final BlobIdentifier identifier) {
    return combinedChainDataClient.getBlobSidecarByBlockRootAndIndex(
        identifier.getBlockRoot(), identifier.getIndex());
  }

  private void handleError(final ResponseCallback<BlobSidecar> callback, final Throwable error) {
    final Throwable rootCause = Throwables.getRootCause(error);
    if (rootCause instanceof RpcException) {
      LOG.trace("Rejecting blob sidecars by root request", error);
      callback.completeWithErrorResponse((RpcException) rootCause);
    } else {
      if (rootCause instanceof StreamClosedException
          || rootCause instanceof ClosedChannelException) {
        LOG.trace("Stream closed while sending requested blob sidecars", error);
      } else {
        LOG.error("Failed to process blob sidecars by root request", error);
      }
      callback.completeWithUnexpectedError(error);
    }
  }
}
