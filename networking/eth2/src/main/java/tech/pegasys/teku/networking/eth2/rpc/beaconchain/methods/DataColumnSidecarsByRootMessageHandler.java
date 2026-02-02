/*
 * Copyright Consensys Software Inc., 2026
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

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.peers.RequestKey;
import tech.pegasys.teku.networking.eth2.rpc.core.PeerRequiredLocalMessageHandler;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseCallback;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnSidecarsByRootRequestMessage;
import tech.pegasys.teku.spec.datastructures.util.DataColumnIdentifier;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.statetransition.datacolumns.CustodyGroupCountManager;
import tech.pegasys.teku.statetransition.datacolumns.DataColumnSidecarByRootCustody;
import tech.pegasys.teku.statetransition.datacolumns.log.rpc.DasReqRespLogger;
import tech.pegasys.teku.statetransition.datacolumns.log.rpc.LoggingPeerId;
import tech.pegasys.teku.statetransition.datacolumns.log.rpc.ReqRespResponseLogger;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

/**
 * <a href="https://github.com/ethereum/consensus-specs/blob/master/specs/fulu/p2p-interface
 * .md#datacolumnsidecarsbyroot-v1">DataColumnSidecarsByRoot v1</a>
 */
public class DataColumnSidecarsByRootMessageHandler
    extends PeerRequiredLocalMessageHandler<
        DataColumnSidecarsByRootRequestMessage, DataColumnSidecar> {

  private final Spec spec;
  private final CombinedChainDataClient combinedChainDataClient;
  private final Supplier<? extends DataColumnSidecarByRootCustody> dataColumnSidecarCustodySupplier;
  private final Supplier<CustodyGroupCountManager> custodyGroupCountManagerSupplier;

  private final LabelledMetric<Counter> requestCounter;
  private final Counter totalDataColumnSidecarsRequestedCounter;
  private final DasReqRespLogger dasLogger;

  public DataColumnSidecarsByRootMessageHandler(
      final Spec spec,
      final MetricsSystem metricsSystem,
      final CombinedChainDataClient combinedChainDataClient,
      final Supplier<? extends DataColumnSidecarByRootCustody> dataColumnSidecarCustodySupplier,
      final Supplier<CustodyGroupCountManager> custodyGroupCountManagerSupplier,
      final DasReqRespLogger dasLogger) {
    this.spec = spec;
    this.combinedChainDataClient = combinedChainDataClient;
    this.custodyGroupCountManagerSupplier = custodyGroupCountManagerSupplier;
    this.dataColumnSidecarCustodySupplier = dataColumnSidecarCustodySupplier;
    this.dasLogger = dasLogger;
    this.requestCounter =
        metricsSystem.createLabelledCounter(
            TekuMetricCategory.NETWORK,
            "rpc_data_column_sidecars_by_root_requests_total",
            "Total number of data column sidecars by root requests received",
            "status");
    this.totalDataColumnSidecarsRequestedCounter =
        metricsSystem.createCounter(
            TekuMetricCategory.NETWORK,
            "rpc_data_column_sidecars_by_root_requested_sidecars_total",
            "Total number of data column sidecars requested in accepted data column sidecars by root requests from "
                + "peers");
  }

  private SafeFuture<Boolean> validateAndMaybeRespond(
      final DataColumnIdentifier identifier,
      final Optional<DataColumnSidecar> maybeSidecar,
      final ResponseCallback<DataColumnSidecar> callback) {
    return validateMinimumRequestEpoch(identifier, maybeSidecar)
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
    final int requestedDataColumnSidecarsCount = message.getMaximumResponseChunks();

    final ReqRespResponseLogger<DataColumnSidecar> responseLogger =
        dasLogger
            .getDataColumnSidecarsByRootLogger()
            .onInboundRequest(
                LoggingPeerId.fromPeerAndNodeId(
                    peer.getId().toBase58(), peer.getDiscoveryNodeId().orElseThrow()),
                message.asList());

    final LoggingResponseCallback<DataColumnSidecar> responseCallbackWithLogging =
        new LoggingResponseCallback<>(responseCallback, responseLogger);

    final Optional<RequestKey> maybeRequestKey =
        peer.approveDataColumnSidecarsRequest(
            responseCallbackWithLogging, requestedDataColumnSidecarsCount);

    if (!peer.approveRequest() || maybeRequestKey.isEmpty()) {
      requestCounter.labels("rate_limited").inc();
      return;
    }

    requestCounter.labels("ok").inc();
    totalDataColumnSidecarsRequestedCounter.inc(requestedDataColumnSidecarsCount);

    final Set<UInt64> myCustodyColumns =
        new HashSet<>(custodyGroupCountManagerSupplier.get().getCustodyColumnIndices());
    final Stream<SafeFuture<Boolean>> responseStream =
        message.stream()
            .flatMap(
                byRootIdentifier ->
                    byRootIdentifier.getColumns().stream()
                        .filter(myCustodyColumns::contains)
                        .map(
                            column ->
                                new DataColumnIdentifier(byRootIdentifier.getBlockRoot(), column)))
            .map(
                dataColumnIdentifier ->
                    retrieveDataColumnSidecar(dataColumnIdentifier)
                        .thenCompose(
                            maybeSidecar ->
                                validateAndMaybeRespond(
                                    dataColumnIdentifier,
                                    maybeSidecar,
                                    responseCallbackWithLogging)));

    final SafeFuture<List<Boolean>> listOfResponses = SafeFuture.collectAll(responseStream);

    listOfResponses
        .thenApply(list -> list.stream().filter(isSent -> isSent).count())
        .thenAccept(
            sentDataColumnSidecarsCount -> {
              if (sentDataColumnSidecarsCount != requestedDataColumnSidecarsCount) {
                peer.adjustDataColumnSidecarsRequest(
                    maybeRequestKey.get(), sentDataColumnSidecarsCount);
              }
              responseCallbackWithLogging.completeSuccessfully();
            })
        .finish(
            err -> handleError(err, responseCallbackWithLogging, "data column sidecars by root"));
  }

  private SafeFuture<Optional<DataColumnSidecar>> getNonCanonicalDataColumnSidecar(
      final DataColumnIdentifier identifier) {
    return combinedChainDataClient
        .getBlockByBlockRoot(identifier.blockRoot())
        .thenApply(maybeBlock -> maybeBlock.map(SignedBeaconBlock::getSlot))
        .thenCompose(
            maybeSlot -> {
              if (maybeSlot.isPresent()) {
                return combinedChainDataClient.getNonCanonicalSidecar(
                    new DataColumnSlotAndIdentifier(
                        maybeSlot.get(), identifier.blockRoot(), identifier.columnIndex()));
              } else {
                return SafeFuture.completedFuture(Optional.empty());
              }
            });
  }

  /**
   * Validations:
   *
   * <ul>
   *   <li>The block root references a block greater than or equal to the minimum_request_epoch
   * </ul>
   */
  private SafeFuture<Void> validateMinimumRequestEpoch(
      final DataColumnIdentifier identifier, final Optional<DataColumnSidecar> maybeSidecar) {
    return maybeSidecar
        .map(sidecar -> SafeFuture.completedFuture(Optional.of(sidecar.getSlot())))
        .orElseGet(
            () ->
                combinedChainDataClient
                    .getBlockByBlockRoot(identifier.blockRoot())
                    .thenApply(maybeBlock -> maybeBlock.map(SignedBeaconBlock::getSlot)))
        .thenAcceptChecked(
            maybeSlot -> {
              if (maybeSlot.isEmpty()) {
                return;
              }
              final UInt64 requestedEpoch = spec.computeEpochAtSlot(maybeSlot.get());
              if (!spec.isAvailabilityOfDataColumnSidecarsRequiredAtEpoch(
                  combinedChainDataClient.getStore(), requestedEpoch)) {
                throw new RpcException(
                    INVALID_REQUEST_CODE,
                    String.format(
                        "Block root (%s) references a block earlier than the minimum_request_epoch",
                        identifier.blockRoot()));
              }
            });
  }

  private SafeFuture<Optional<DataColumnSidecar>> retrieveDataColumnSidecar(
      final DataColumnIdentifier identifier) {
    return dataColumnSidecarCustodySupplier
        .get()
        .getCustodyDataColumnSidecarByRoot(identifier)
        .thenCompose(
            maybeSidecar -> {
              if (maybeSidecar.isPresent()) {
                return SafeFuture.completedFuture(maybeSidecar);
              }
              // Fallback to non-canonical sidecar if the canonical one is not found
              return getNonCanonicalDataColumnSidecar(identifier);
            });
  }
}
