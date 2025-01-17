/*
 * Copyright Consensys Software Inc., 2022
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
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
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
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnIdentifier;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnSidecarsByRootRequestMessage;
import tech.pegasys.teku.statetransition.datacolumns.DataColumnSidecarByRootCustody;
import tech.pegasys.teku.statetransition.datacolumns.log.rpc.DasReqRespLogger;
import tech.pegasys.teku.statetransition.datacolumns.log.rpc.LoggingPeerId;
import tech.pegasys.teku.statetransition.datacolumns.log.rpc.ReqRespResponseLogger;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

/**
 * <a
 * href="https://github.com/ethereum/consensus-specs/blob/dev/specs/fulu/p2p-interface.md#datacolumnsidecarsbyroot-v1">DataColumnSidecarsByRoot
 * v1</a>
 */
public class DataColumnSidecarsByRootMessageHandler
    extends PeerRequiredLocalMessageHandler<
        DataColumnSidecarsByRootRequestMessage, DataColumnSidecar> {

  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final CombinedChainDataClient combinedChainDataClient;
  private final DataColumnSidecarByRootCustody dataColumnSidecarCustody;

  private final LabelledMetric<Counter> requestCounter;
  private final Counter totalDataColumnSidecarsRequestedCounter;
  private final DasReqRespLogger dasLogger;

  public DataColumnSidecarsByRootMessageHandler(
      final Spec spec,
      final MetricsSystem metricsSystem,
      final CombinedChainDataClient combinedChainDataClient,
      final DataColumnSidecarByRootCustody dataColumnSidecarCustody,
      final DasReqRespLogger dasLogger) {
    this.spec = spec;
    this.combinedChainDataClient = combinedChainDataClient;
    requestCounter =
        metricsSystem.createLabelledCounter(
            TekuMetricCategory.NETWORK,
            "rpc_data_column_sidecars_by_root_requests_total",
            "Total number of data column sidecars by root requests received",
            "status");
    totalDataColumnSidecarsRequestedCounter =
        metricsSystem.createCounter(
            TekuMetricCategory.NETWORK,
            "rpc_data_column_sidecars_by_root_requested_blob_sidecars_total",
            "Total number of data column sidecars requested in accepted data column sidecars by root requests from peers");
    this.dataColumnSidecarCustody = dataColumnSidecarCustody;
    this.dasLogger = dasLogger;
  }

  private SafeFuture<Boolean> validateAndSendMaybeRespond(
      final DataColumnIdentifier identifier,
      final Optional<DataColumnSidecar> maybeSidecar,
      final UInt64 finalizedEpoch,
      final ResponseCallback<DataColumnSidecar> callback) {
    return validateMinimumRequestEpoch(identifier, maybeSidecar, finalizedEpoch)
        .thenCompose(
            __ ->
                maybeSidecar
                    .map(sideCar -> callback.respond(sideCar).thenApply(___ -> true))
                    .orElse(SafeFuture.completedFuture(false)));
  }

  @Override
  public void onIncomingMessage(
      final String protocolId,
      final Eth2Peer peer,
      final DataColumnSidecarsByRootRequestMessage message,
      final ResponseCallback<DataColumnSidecar> responseCallback) {

    ReqRespResponseLogger<DataColumnSidecar> responseLogger =
        dasLogger
            .getDataColumnSidecarsByRootLogger()
            .onInboundRequest(
                LoggingPeerId.fromPeerAndNodeId(
                    peer.getId().toBase58(), peer.getDiscoveryNodeId().orElseThrow()),
                message.asList());

    LoggingResponseCallback<DataColumnSidecar> responseCallbackWithLogging =
        new LoggingResponseCallback<>(responseCallback, responseLogger);

    final Optional<RequestApproval> dataColumnSidecarsRequestApproval =
        peer.approveDataColumnSidecarsRequest(responseCallbackWithLogging, message.size());

    if (!peer.approveRequest() || dataColumnSidecarsRequestApproval.isEmpty()) {
      requestCounter.labels("rate_limited").inc();
      return;
    }

    requestCounter.labels("ok").inc();
    totalDataColumnSidecarsRequestedCounter.inc(message.size());

    final UInt64 finalizedEpoch = getFinalizedEpoch();

    Stream<SafeFuture<Boolean>> responseStream =
        message.stream()
            .map(
                identifier ->
                    retrieveDataColumnSidecar(identifier)
                        .thenCompose(
                            maybeSidecar ->
                                validateAndSendMaybeRespond(
                                    identifier,
                                    maybeSidecar,
                                    finalizedEpoch,
                                    responseCallbackWithLogging)));

    SafeFuture<List<Boolean>> listOfResponses = SafeFuture.collectAll(responseStream);

    listOfResponses
        .thenApply(list -> list.stream().filter(isSent -> isSent).count())
        .thenAccept(
            sentDataColumnSidecarsCount -> {
              if (sentDataColumnSidecarsCount != message.size()) {
                peer.adjustDataColumnSidecarsRequest(
                    dataColumnSidecarsRequestApproval.get(), sentDataColumnSidecarsCount);
              }
              responseCallbackWithLogging.completeSuccessfully();
            })
        .finish(
            err -> {
              peer.adjustDataColumnSidecarsRequest(dataColumnSidecarsRequestApproval.get(), 0);
              handleError(responseCallbackWithLogging, err);
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
  @SuppressWarnings("unused")
  private SafeFuture<Void> validateMinimumRequestEpoch(
      final DataColumnIdentifier identifier,
      final Optional<DataColumnSidecar> maybeSidecar,
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
              if (!spec.isAvailabilityOfDataColumnSidecarsRequiredAtEpoch(
                  combinedChainDataClient.getStore(), requestedEpoch)
              // TODO uncomment when sync by range is ready
              /* || requestedEpoch.isLessThan(finalizedEpoch)*/ ) {
                throw new RpcException(
                    INVALID_REQUEST_CODE,
                    String.format(
                        "Block root (%s) references a block earlier than the minimum_request_epoch",
                        identifier.getBlockRoot()));
              }
            });
  }

  private SafeFuture<Optional<DataColumnSidecar>> retrieveDataColumnSidecar(
      final DataColumnIdentifier identifier) {
    return dataColumnSidecarCustody.getCustodyDataColumnSidecarByRoot(identifier);
  }

  private void handleError(
      final ResponseCallback<DataColumnSidecar> callback, final Throwable error) {
    final Throwable rootCause = Throwables.getRootCause(error);
    if (rootCause instanceof RpcException) {
      LOG.trace("Rejecting data column sidecars by root request", error);
      callback.completeWithErrorResponse((RpcException) rootCause);
    } else {
      if (rootCause instanceof StreamClosedException
          || rootCause instanceof ClosedChannelException) {
        LOG.trace("Stream closed while sending requested data column sidecars", error);
      } else {
        LOG.error("Failed to process data column sidecars by root request", error);
      }
      callback.completeWithUnexpectedError(error);
    }
  }
}
