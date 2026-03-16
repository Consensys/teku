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

import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.networking.eth2.rpc.core.RpcResponseStatus.INVALID_REQUEST_CODE;

import com.google.common.annotations.VisibleForTesting;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
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
import tech.pegasys.teku.networking.eth2.peers.RequestKey;
import tech.pegasys.teku.networking.eth2.rpc.core.PeerRequiredLocalMessageHandler;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseCallback;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.blocks.MinimalBeaconBlockSummary;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.ExecutionPayloadEnvelopesByRangeRequestMessage;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

/**
 * <a
 * href="https://github.com/ethereum/consensus-specs/blob/master/specs/gloas/p2p-interface.md#executionpayloadenvelopesbyrange-v1">ExecutionPayloadEnvelopesByRange</a>
 */
public class ExecutionPayloadEnvelopesByRangeMessageHandler
    extends PeerRequiredLocalMessageHandler<
        ExecutionPayloadEnvelopesByRangeRequestMessage, SignedExecutionPayloadEnvelope> {

  private static final Logger LOG = LogManager.getLogger();

  private final SpecConfigGloas config;
  private final LabelledMetric<Counter> requestCounter;
  private final CombinedChainDataClient combinedChainDataClient;
  private final Counter totalExecutionPayloadEnvelopesRequestedCounter;

  public ExecutionPayloadEnvelopesByRangeMessageHandler(
      final SpecConfigGloas config,
      final MetricsSystem metricsSystem,
      final CombinedChainDataClient combinedChainDataClient) {
    ;
    this.config = config;
    this.combinedChainDataClient = combinedChainDataClient;
    requestCounter =
        metricsSystem.createLabelledCounter(
            TekuMetricCategory.NETWORK,
            "rpc_execution_payload_envelopes_by_range_requests_total",
            "Total number of execution payload envelopes by range requests received",
            "status");
    totalExecutionPayloadEnvelopesRequestedCounter =
        metricsSystem.createCounter(
            TekuMetricCategory.NETWORK,
            "rpc_execution_payload_envelopes_by_range_requested_envelopes_total",
            "Total number of execution payload envelopes requested in accepted execution payload envelopes by range requests from peers");
  }

  @Override
  public Optional<RpcException> validateRequest(
      final String protocolId, final ExecutionPayloadEnvelopesByRangeRequestMessage request) {
    if (request.getCount().isGreaterThan(config.getMaxRequestBlocksDeneb())) {
      requestCounter.labels("count_too_big").inc();
      return Optional.of(
          new RpcException(
              INVALID_REQUEST_CODE,
              String.format(
                  "Only a maximum of %s execution payload envelopes can be requested per request",
                  config.getMaxRequestBlocksDeneb())));
    }
    return Optional.empty();
  }

  @Override
  public void onIncomingMessage(
      final String protocolId,
      final Eth2Peer peer,
      final ExecutionPayloadEnvelopesByRangeRequestMessage message,
      final ResponseCallback<SignedExecutionPayloadEnvelope> callback) {
    final UInt64 startSlot = message.getStartSlot();
    final UInt64 count = message.getCount();
    LOG.trace(
        "Peer {} requested {} execution payload envelopes starting at slot {}",
        peer.getId(),
        count,
        startSlot);

    final Optional<RequestKey> maybeRequestKey =
        peer.approveExecutionPayloadEnvelopesRequest(callback, count.longValue());

    if (!peer.approveRequest() || maybeRequestKey.isEmpty()) {
      requestCounter.labels("rate_limited").inc();
      return;
    }
    requestCounter.labels("ok").inc();
    totalExecutionPayloadEnvelopesRequestedCounter.inc(count.longValue());

    final NavigableMap<UInt64, Bytes32> hotRoots;

    if (combinedChainDataClient.isFinalized(message.getMaxSlot())) {
      // All execution payloads are finalized so skip scanning the protoarray
      hotRoots = new TreeMap<>();
    } else {
      hotRoots = combinedChainDataClient.getAncestorRoots(startSlot, UInt64.ONE, count);
    }

    final UInt64 headSlot =
        hotRoots.isEmpty()
            ? combinedChainDataClient
                .getChainHead()
                .map(MinimalBeaconBlockSummary::getSlot)
                .orElse(ZERO)
            : hotRoots.lastKey();

    final RequestState initialState =
        new RequestState(callback, startSlot, count, headSlot, hotRoots);

    final SafeFuture<RequestState> response;

    if (initialState.isComplete()) {
      response = SafeFuture.completedFuture(initialState);
    } else {
      response = sendNextExecutionPayloadEnvelope(initialState);
    }

    response.finish(
        requestState -> {
          if (requestState.sentExecutionPayloadEnvelopes.get() != count.longValue()) {
            peer.adjustExecutionPayloadEnvelopesRequest(
                maybeRequestKey.get(), requestState.sentExecutionPayloadEnvelopes.get());
          }
          callback.completeSuccessfully();
        },
        err -> handleError(err, callback, "execution payload envelopes by range"));
  }

  private SafeFuture<RequestState> sendNextExecutionPayloadEnvelope(
      final RequestState requestState) {
    SafeFuture<Boolean> executionPayloadFuture = processNextExecutionPayloadEnvelope(requestState);
    // similar logic to BeaconBlocksByRange
    while (executionPayloadFuture.isDone() && !executionPayloadFuture.isCompletedExceptionally()) {
      if (executionPayloadFuture.join()) {
        return completedFuture(requestState);
      }
      executionPayloadFuture = processNextExecutionPayloadEnvelope(requestState);
    }
    return executionPayloadFuture.thenCompose(
        complete ->
            complete
                ? completedFuture(requestState)
                : sendNextExecutionPayloadEnvelope(requestState));
  }

  private SafeFuture<Boolean> processNextExecutionPayloadEnvelope(final RequestState requestState) {
    // Ensure execution payloads are loaded off of the event thread
    return requestState
        .loadNextExecutionPayloadEnvelope()
        .thenCompose(
            block -> {
              requestState.decrementRemainingExecutionPayloadEnvelopes();
              return handleLoadedExecutionPayloadEnvelope(requestState, block);
            });
  }

  /** Sends the execution payload and returns true if the request is now complete. */
  private SafeFuture<Boolean> handleLoadedExecutionPayloadEnvelope(
      final RequestState requestState,
      final Optional<SignedExecutionPayloadEnvelope> executionPayloadEnvelope) {
    return executionPayloadEnvelope
        .map(requestState::sendExecutionPayloadEnvelope)
        .orElse(SafeFuture.COMPLETE)
        .thenApply(
            __ -> {
              if (requestState.isComplete()) {
                return true;
              } else {
                requestState.incrementCurrentSlot();
                return false;
              }
            });
  }

  @VisibleForTesting
  class RequestState {

    private final ResponseCallback<SignedExecutionPayloadEnvelope> callback;
    private UInt64 currentSlot;
    private UInt64 remainingExecutionPayloadEnvelopes;
    private final UInt64 headSlot;
    private final NavigableMap<UInt64, Bytes32> knownBlockRoots;

    private final AtomicInteger sentExecutionPayloadEnvelopes = new AtomicInteger(0);

    RequestState(
        final ResponseCallback<SignedExecutionPayloadEnvelope> callback,
        final UInt64 startSlot,
        final UInt64 count,
        final UInt64 headSlot,
        final NavigableMap<UInt64, Bytes32> knownBlockRoots) {
      this.callback = callback;
      this.currentSlot = startSlot;
      this.remainingExecutionPayloadEnvelopes = count;
      this.headSlot = headSlot;
      this.knownBlockRoots = knownBlockRoots;
    }

    private boolean needsMoreExecutionPayloadEnvelopes() {
      return !remainingExecutionPayloadEnvelopes.equals(ZERO);
    }

    private boolean hasReachedHeadSlot() {
      return currentSlot.isGreaterThanOrEqualTo(headSlot);
    }

    boolean isComplete() {
      return !needsMoreExecutionPayloadEnvelopes() || hasReachedHeadSlot();
    }

    SafeFuture<Void> sendExecutionPayloadEnvelope(
        final SignedExecutionPayloadEnvelope executionPayloadEnvelope) {
      return callback
          .respond(executionPayloadEnvelope)
          .thenRun(sentExecutionPayloadEnvelopes::incrementAndGet);
    }

    void decrementRemainingExecutionPayloadEnvelopes() {
      remainingExecutionPayloadEnvelopes = remainingExecutionPayloadEnvelopes.minusMinZero(1);
    }

    void incrementCurrentSlot() {
      currentSlot = currentSlot.increment();
    }

    SafeFuture<Optional<SignedExecutionPayloadEnvelope>> loadNextExecutionPayloadEnvelope() {
      final UInt64 slot = this.currentSlot;
      final Bytes32 knownBlockRoot = knownBlockRoots.get(slot);
      if (knownBlockRoot != null) {
        // Known root so lookup by root
        return combinedChainDataClient
            .getExecutionPayloadByBlockRoot(knownBlockRoot)
            .thenApply(
                maybeExecutionPayload ->
                    maybeExecutionPayload.filter(
                        executionPayload -> executionPayload.getSlot().equals(slot)));
      } else if ((!knownBlockRoots.isEmpty() && slot.compareTo(knownBlockRoots.firstKey()) >= 0)
          || slot.compareTo(headSlot) > 0) {
        // Unknown root but not finalized means this is an empty slot (or payload absent)
        // Could also be because the first execution payload requested is above our head slot
        return SafeFuture.completedFuture(Optional.empty());
      } else {
        // TODO-GLOAS: https://github.com/Consensys/teku/issues/9974 implement when we support
        // finalized execution payload lookup
        return SafeFuture.failedFuture(
            new UnsupportedOperationException(
                "Lookup of finalized execution payload envelopes is not implemented yet"));
      }
    }
  }
}
