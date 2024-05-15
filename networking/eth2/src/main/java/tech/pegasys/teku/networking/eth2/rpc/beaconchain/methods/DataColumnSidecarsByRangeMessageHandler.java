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
import java.util.List;
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
import tech.pegasys.teku.spec.config.SpecConfigEip7594;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnSidecarsByRangeRequestMessage;
import tech.pegasys.teku.spec.datastructures.util.ColumnSlotAndIdentifier;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

/**
 * <a
 * href="https://github.com/ethereum/consensus-specs/blob/dev/specs/_features/eip7594/p2p-interface.md#datacolumnsidecarsbyrange-v1">DataColumnSidecarsByRange
 * v1</a>
 */
public class DataColumnSidecarsByRangeMessageHandler
    extends PeerRequiredLocalMessageHandler<
        DataColumnSidecarsByRangeRequestMessage, DataColumnSidecar> {

  private static final Logger LOG = LogManager.getLogger();
  private static final Logger LOG_DAS = LogManager.getLogger("das-nyota");

  private final Spec spec;
  private final SpecConfigEip7594 specConfigEip7594;
  private final CombinedChainDataClient combinedChainDataClient;
  private final LabelledMetric<Counter> requestCounter;
  private final Counter totalDataColumnSidecarsRequestedCounter;

  public DataColumnSidecarsByRangeMessageHandler(
      final Spec spec,
      final SpecConfigEip7594 specConfigEip7594,
      final MetricsSystem metricsSystem,
      final CombinedChainDataClient combinedChainDataClient) {
    this.spec = spec;
    this.specConfigEip7594 = specConfigEip7594;
    this.combinedChainDataClient = combinedChainDataClient;
    requestCounter =
        metricsSystem.createLabelledCounter(
            TekuMetricCategory.NETWORK,
            "rpc_data_column_sidecars_by_range_requests_total",
            "Total number of data column sidecars by range requests received",
            "status");
    totalDataColumnSidecarsRequestedCounter =
        metricsSystem.createCounter(
            TekuMetricCategory.NETWORK,
            "rpc_data_column_sidecars_by_range_requested_sidecars_total",
            "Total number of data column sidecars requested in accepted blob sidecars by range requests from peers");
  }

  @Override
  public void onIncomingMessage(
      final String protocolId,
      final Eth2Peer peer,
      final DataColumnSidecarsByRangeRequestMessage message,
      final ResponseCallback<DataColumnSidecar> callback) {
    final UInt64 startSlot = message.getStartSlot();
    final UInt64 endSlot = message.getMaxSlot();
    final List<UInt64> columns = message.getColumns();

    LOG.trace(
        "Peer {} requested {} slots with columns {} of data column sidecars starting at slot {}.",
        peer.getId(),
        message.getCount(),
        columns,
        startSlot);
    LOG_DAS.info(
        "[nyota] DataColumnSidecarsByRangeMessageHandler: REQUEST {} slots with columns {} of data column sidecars starting at slot {} from {}",
        message.getCount(),
        columns,
        startSlot,
        peer.getId());

    final int requestedCount = message.getMaximumResponseChunks();

    if (requestedCount > specConfigEip7594.getMaxRequestDataColumnSidecars()) {
      requestCounter.labels("count_too_big").inc();
      callback.completeWithErrorResponse(
          new RpcException(
              INVALID_REQUEST_CODE,
              String.format(
                  "Only a maximum of %s blob sidecars can be requested per request. Requested: %s",
                  specConfigEip7594.getMaxRequestDataColumnSidecars(), requestedCount)));
      return;
    }

    final Optional<RequestApproval> dataColumnSidecarsRequestApproval =
        peer.approveDataColumnSidecarsRequest(callback, requestedCount);

    if (!peer.approveRequest() || dataColumnSidecarsRequestApproval.isEmpty()) {
      requestCounter.labels("rate_limited").inc();
      return;
    }

    requestCounter.labels("ok").inc();
    totalDataColumnSidecarsRequestedCounter.inc(message.getCount().longValue());
    final SafeFuture<Optional<UInt64>> earliestDataColumnSidecarSlotFuture =
        combinedChainDataClient.getEarliestDataColumnSidecarSlot();
    final SafeFuture<Optional<UInt64>> firstIncompleteSlotFuture =
        combinedChainDataClient.getFirstIncompleteSlot();
    SafeFuture.collectAll(earliestDataColumnSidecarSlotFuture, firstIncompleteSlotFuture)
        .thenCompose(
            slotOptionals -> {
              final Optional<UInt64> earliestSidecarSlot = slotOptionals.get(0);
              final Optional<UInt64> firstIncompleteSlot = slotOptionals.get(1);
              final UInt64 requestEpoch = spec.computeEpochAtSlot(startSlot);
              if (spec.isAvailabilityOfDataColumnSidecarsRequiredAtEpoch(
                      combinedChainDataClient.getStore(), requestEpoch)
                  && !checkDataColumnSidecarsAreAvailable(
                      earliestSidecarSlot, firstIncompleteSlot, endSlot)) {
                return SafeFuture.failedFuture(
                    new ResourceUnavailableException(
                        "Requested data column sidecars are not available."));
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
                  new RequestState(
                      callback,
                      specConfigEip7594.getMaxRequestDataColumnSidecars(),
                      startSlot,
                      endSlot,
                      columns,
                      canonicalHotRoots,
                      finalizedSlot);
              if (initialState.isComplete()) {
                return SafeFuture.completedFuture(initialState);
              }
              return sendDataColumnSidecars(initialState);
            })
        .finish(
            requestState -> {
              final int sentDataColumnSidecars = requestState.sentDataColumnSidecars.get();
              if (sentDataColumnSidecars != requestedCount) {
                peer.adjustDataColumnSidecarsRequest(
                    dataColumnSidecarsRequestApproval.get(), sentDataColumnSidecars);
              }
              LOG.trace(
                  "Sent {} data column sidecars to peer {}.", sentDataColumnSidecars, peer.getId());
              LOG_DAS.info(
                  "[nyota] DataColumnSidecarsByRangeMessageHandler: RESPONSE sent {} sidecars to {}",
                  sentDataColumnSidecars,
                  peer.getId());
              callback.completeSuccessfully();
            },
            error -> {
              peer.adjustDataColumnSidecarsRequest(dataColumnSidecarsRequestApproval.get(), 0);
              handleProcessingRequestError(error, callback);
              LOG_DAS.info(
                  "[nyota] DataColumnSidecarsByRangeMessageHandler: ERROR to {}: {}",
                  peer.getId(),
                  error.toString());
            });
    ;
  }

  private boolean checkDataColumnSidecarsAreAvailable(
      final Optional<UInt64> earliestAvailableSidecarSlotOptional,
      final Optional<UInt64> firstIncompleteSlotOptional,
      final UInt64 requestSlot) {
    if (earliestAvailableSidecarSlotOptional.isPresent()) {
      if (earliestAvailableSidecarSlotOptional.get().isLessThanOrEqualTo(requestSlot)) {
        return true;
      }
      return firstIncompleteSlotOptional
          .map(firstIncompleteSlot -> firstIncompleteSlot.isGreaterThan(requestSlot))
          .orElse(false);
    } else {
      return false;
    }
  }

  private SafeFuture<RequestState> sendDataColumnSidecars(final RequestState requestState) {
    return requestState
        .loadNextDataColumnSidecar()
        .thenCompose(
            maybeDataColumnSidecar ->
                maybeDataColumnSidecar
                    .map(requestState::sendDataColumnSidecar)
                    .orElse(SafeFuture.COMPLETE))
        .thenCompose(
            __ -> {
              if (requestState.isComplete()) {
                return SafeFuture.completedFuture(requestState);
              } else {
                return sendDataColumnSidecars(requestState);
              }
            });
  }

  private void handleProcessingRequestError(
      final Throwable error, final ResponseCallback<DataColumnSidecar> callback) {
    final Throwable rootCause = Throwables.getRootCause(error);
    if (rootCause instanceof RpcException) {
      LOG.trace("Rejecting data column sidecars by range request", error);
      callback.completeWithErrorResponse((RpcException) rootCause);
    } else {
      if (rootCause instanceof StreamClosedException
          || rootCause instanceof ClosedChannelException) {
        LOG.trace("Stream closed while sending requested data column sidecars", error);
      } else {
        LOG.error("Failed to process data column sidecars request", error);
      }
      callback.completeWithUnexpectedError(error);
    }
  }

  @VisibleForTesting
  class RequestState {

    private final AtomicInteger sentDataColumnSidecars = new AtomicInteger(0);
    private final ResponseCallback<DataColumnSidecar> callback;
    private final UInt64 maxRequestDataColumnSidecars;
    private final UInt64 startSlot;
    private final UInt64 endSlot;
    private final List<UInt64> columns;
    private final UInt64 finalizedSlot;
    private final Map<UInt64, Bytes32> canonicalHotRoots;

    // since our storage stores hot and finalized data columns sidecar on the same "table", this
    // iterator can span
    // over hot and finalized data column sidecar
    private Optional<Iterator<ColumnSlotAndIdentifier>> dataColumnSidecarKeysIterator =
        Optional.empty();

    RequestState(
        final ResponseCallback<DataColumnSidecar> callback,
        final int maxRequestDataColumnSidecars,
        final UInt64 startSlot,
        final UInt64 endSlot,
        final List<UInt64> columns,
        final Map<UInt64, Bytes32> canonicalHotRoots,
        final UInt64 finalizedSlot) {
      this.callback = callback;
      this.maxRequestDataColumnSidecars = UInt64.valueOf(maxRequestDataColumnSidecars);
      this.startSlot = startSlot;
      this.endSlot = endSlot;
      this.columns = columns;
      this.finalizedSlot = finalizedSlot;
      this.canonicalHotRoots = canonicalHotRoots;
    }

    SafeFuture<Void> sendDataColumnSidecar(final DataColumnSidecar dataColumnSidecar) {
      return callback.respond(dataColumnSidecar).thenRun(sentDataColumnSidecars::incrementAndGet);
    }

    SafeFuture<Optional<DataColumnSidecar>> loadNextDataColumnSidecar() {
      if (dataColumnSidecarKeysIterator.isEmpty()) {
        return combinedChainDataClient
            .getDataColumnIdentifiers(startSlot, endSlot, maxRequestDataColumnSidecars)
            .thenCompose(
                keys -> {
                  dataColumnSidecarKeysIterator = Optional.of(keys.iterator());
                  return getNextDataColumnSidecar(dataColumnSidecarKeysIterator.get());
                });
      } else {
        return getNextDataColumnSidecar(dataColumnSidecarKeysIterator.get());
      }
    }

    private SafeFuture<Optional<DataColumnSidecar>> getNextDataColumnSidecar(
        final Iterator<ColumnSlotAndIdentifier> dataColumnSidecarIdentifiers) {
      if (dataColumnSidecarIdentifiers.hasNext()) {
        final ColumnSlotAndIdentifier columnSlotAndIdentifier = dataColumnSidecarIdentifiers.next();

        // Column that was not requested. TODO: get identifiers only for requested columns from DB
        if (!columns.contains(columnSlotAndIdentifier.identifier().getIndex())) {
          return getNextDataColumnSidecar(dataColumnSidecarIdentifiers);
        }

        if (finalizedSlot.isGreaterThanOrEqualTo(columnSlotAndIdentifier.slot())) {
          return combinedChainDataClient.getSidecar(columnSlotAndIdentifier);
        }

        // not finalized, let's check if it is on canonical chain
        if (isCanonicalHotDataColumnSidecar(columnSlotAndIdentifier)) {
          return combinedChainDataClient.getSidecar(columnSlotAndIdentifier);
        }

        // non-canonical, try next one
        return getNextDataColumnSidecar(dataColumnSidecarIdentifiers);
      }

      return SafeFuture.completedFuture(Optional.empty());
    }

    private boolean isCanonicalHotDataColumnSidecar(
        final ColumnSlotAndIdentifier columnSlotAndIdentifier) {
      return Optional.ofNullable(canonicalHotRoots.get(columnSlotAndIdentifier.slot()))
          .map(blockRoot -> blockRoot.equals(columnSlotAndIdentifier.identifier().getBlockRoot()))
          .orElse(false);
    }

    boolean isComplete() {
      return endSlot.isLessThan(startSlot)
          || dataColumnSidecarKeysIterator.map(iterator -> !iterator.hasNext()).orElse(false);
    }
  }
}
