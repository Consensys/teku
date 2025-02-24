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

import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.networking.eth2.rpc.core.RpcResponseStatus.INVALID_REQUEST_CODE;

import com.google.common.base.Throwables;
import java.nio.channels.ClosedChannelException;
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
import tech.pegasys.teku.networking.eth2.peers.RequestApproval;
import tech.pegasys.teku.networking.eth2.rpc.core.PeerRequiredLocalMessageHandler;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseCallback;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.networking.p2p.rpc.StreamClosedException;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.MinimalBeaconBlockSummary;
import tech.pegasys.teku.spec.datastructures.execution.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.ExecutionPayloadEnvelopesByRangeRequestMessage;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

public class ExecutionPayloadEnvelopesByRangeMessageHandler
    extends PeerRequiredLocalMessageHandler<
        ExecutionPayloadEnvelopesByRangeRequestMessage, SignedExecutionPayloadEnvelope> {
  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final CombinedChainDataClient combinedChainDataClient;
  private final LabelledMetric<Counter> requestCounter;
  private final Counter totalExecutionPayloadEnvelopesRequestedCounter;

  public ExecutionPayloadEnvelopesByRangeMessageHandler(
      final Spec spec,
      final MetricsSystem metricsSystem,
      final CombinedChainDataClient combinedChainDataClient) {
    this.spec = spec;
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
            "rpc_execution_payload_envelopes_by_range_requested_blocks_total",
            "Total number of execution payload envelopes requested in accepted execution payload envelopes by range requests from peers");
  }

  @Override
  public Optional<RpcException> validateRequest(
      final String protocolId, final ExecutionPayloadEnvelopesByRangeRequestMessage request) {
    final SpecMilestone latestMilestoneRequested;
    try {
      latestMilestoneRequested =
          spec.getForkSchedule().getSpecMilestoneAtSlot(request.getMaxSlot());
    } catch (final ArithmeticException __) {
      return Optional.of(
          new RpcException(INVALID_REQUEST_CODE, "Requested slot is too far in the future"));
    }

    final int maxRequestExecutionPayloadEnvelopes =
        spec.forMilestone(latestMilestoneRequested).miscHelpers().getMaxRequestBlocks().intValue();

    int requestedCount;
    try {
      requestedCount = request.getCount().intValue();
    } catch (final ArithmeticException __) {
      // handle overflows
      requestedCount = -1;
    }

    if (requestedCount == -1 || requestedCount > maxRequestExecutionPayloadEnvelopes) {
      requestCounter.labels("count_too_big").inc();
      return Optional.of(
          new RpcException(
              INVALID_REQUEST_CODE,
              "Only a maximum of "
                  + maxRequestExecutionPayloadEnvelopes
                  + " execution payload envelopes can be requested per request"));
    }

    return Optional.empty();
  }

  @Override
  public void onIncomingMessage(
      final String protocolId,
      final Eth2Peer peer,
      final ExecutionPayloadEnvelopesByRangeRequestMessage message,
      final ResponseCallback<SignedExecutionPayloadEnvelope> callback) {
    LOG.trace(
        "Peer {} requested {} ExecutionPayloadEnvelopes starting at slot {}",
        peer.getId(),
        message.getCount(),
        message.getStartSlot());

    final Optional<RequestApproval> executionPayloadsRequestApproval =
        peer.approveExecutionPayloadEnvelopesRequest(callback, message.getCount().longValue());

    if (!peer.approveRequest() || executionPayloadsRequestApproval.isEmpty()) {
      requestCounter.labels("rate_limited").inc();
      return;
    }

    requestCounter.labels("ok").inc();
    totalExecutionPayloadEnvelopesRequestedCounter.inc(message.getCount().longValue());
    sendMatchingExecutionPayloadEnvelopes(message, callback)
        .finish(
            requestState -> {
              if (requestState.sentExecutionPayloads.get() != message.getCount().longValue()) {
                peer.adjustExecutionPayloadEnvelopesRequest(
                    executionPayloadsRequestApproval.get(),
                    requestState.sentExecutionPayloads.get());
              }
              callback.completeSuccessfully();
            },
            error -> {
              peer.adjustExecutionPayloadEnvelopesRequest(
                  executionPayloadsRequestApproval.get(), 0);
              final Throwable rootCause = Throwables.getRootCause(error);
              if (rootCause instanceof RpcException) {
                LOG.trace(
                    "Rejecting execution payload envelopes by range request",
                    error); // Keep full context
                callback.completeWithErrorResponse((RpcException) rootCause);
              } else {
                if (rootCause instanceof StreamClosedException
                    || rootCause instanceof ClosedChannelException) {
                  LOG.trace(
                      "Stream closed while sending requested execution payload envelopes", error);
                } else {
                  LOG.error(
                      "Failed to process execution payload envelopes by range request", error);
                }
                callback.completeWithUnexpectedError(error);
              }
            });
  }

  private SafeFuture<RequestState> sendMatchingExecutionPayloadEnvelopes(
      final ExecutionPayloadEnvelopesByRangeRequestMessage message,
      final ResponseCallback<SignedExecutionPayloadEnvelope> callback) {
    final UInt64 startSlot = message.getStartSlot();
    final UInt64 count = message.getCount();

    return combinedChainDataClient
        .getEarliestAvailableBlockSlot()
        .thenCompose(
            earliestSlot -> {
              if (earliestSlot.map(s -> s.isGreaterThan(startSlot)).orElse(true)) {
                // We're missing the first block so execution payload is missing as well so return
                // an error
                return SafeFuture.failedFuture(
                    new RpcException.ResourceUnavailableException(
                        "Requested historical blocks are currently unavailable"));
              }
              final UInt64 headBlockSlot =
                  combinedChainDataClient
                      .getChainHead()
                      .map(MinimalBeaconBlockSummary::getSlot)
                      .orElse(ZERO);
              final NavigableMap<UInt64, Bytes32> hotRoots;
              if (combinedChainDataClient.isFinalized(message.getMaxSlot())) {
                // All execution payloads are finalized so skip scanning the protoarray
                hotRoots = new TreeMap<>();
              } else {
                hotRoots = combinedChainDataClient.getAncestorRoots(startSlot, UInt64.ONE, count);
              }
              // Don't send anything past the last slot found in protoarray to ensure execution
              // payloads are consistent
              // If we didn't find any execution payloads in protoarray, every execution payload in
              // the range must be finalized so we don't need to worry about inconsistent execution
              // payloads
              final UInt64 headSlot = hotRoots.isEmpty() ? headBlockSlot : hotRoots.lastKey();
              final RequestState initialState =
                  new RequestState(startSlot, count, headSlot, hotRoots, callback);
              if (initialState.isComplete()) {
                return SafeFuture.completedFuture(initialState);
              }
              return sendNextExecutionPayload(initialState);
            });
  }

  private SafeFuture<RequestState> sendNextExecutionPayload(final RequestState requestState) {
    SafeFuture<Boolean> executionPayloadFuture = processNextExecutionPayload(requestState);
    // Avoid risk of StackOverflowException by iterating when the execution payload future is
    // already complete. Using thenCompose on the completed future would execute immediately and
    // recurse back into
    // this method to send the next execution payload.  When not already complete, thenCompose is
    // executed on a separate thread so doesn't recurse on the same stack.
    while (executionPayloadFuture.isDone() && !executionPayloadFuture.isCompletedExceptionally()) {
      if (executionPayloadFuture.join()) {
        return completedFuture(requestState);
      }
      executionPayloadFuture = processNextExecutionPayload(requestState);
    }
    return executionPayloadFuture.thenCompose(
        complete ->
            complete ? completedFuture(requestState) : sendNextExecutionPayload(requestState));
  }

  private SafeFuture<Boolean> processNextExecutionPayload(final RequestState requestState) {
    // Ensure execution payloads are loaded off of the event thread
    return requestState
        .loadNextExecutionPayload()
        .thenCompose(
            block -> {
              requestState.decrementRemainingExecutionPayloads();
              return handleLoadedExecutionPayload(requestState, block);
            });
  }

  /** Sends the execution payload and returns true if the request is now complete. */
  private SafeFuture<Boolean> handleLoadedExecutionPayload(
      final RequestState requestState,
      final Optional<SignedExecutionPayloadEnvelope> executionPayload) {
    return executionPayload
        .map(requestState::sendExecutionPayload)
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

  private class RequestState {

    private final UInt64 headSlot;
    private final ResponseCallback<SignedExecutionPayloadEnvelope> callback;
    private final NavigableMap<UInt64, Bytes32> knownBlockRoots;
    private UInt64 currentSlot;
    private UInt64 remainingExecutionPayloads;

    private final AtomicInteger sentExecutionPayloads = new AtomicInteger(0);

    RequestState(
        final UInt64 startSlot,
        final UInt64 count,
        final UInt64 headSlot,
        final NavigableMap<UInt64, Bytes32> knownBlockRoots,
        final ResponseCallback<SignedExecutionPayloadEnvelope> callback) {
      this.currentSlot = startSlot;
      this.knownBlockRoots = knownBlockRoots;
      this.remainingExecutionPayloads = count;
      this.headSlot = headSlot;
      this.callback = callback;
    }

    private boolean needsMoreBlocks() {
      return !remainingExecutionPayloads.equals(ZERO);
    }

    private boolean hasReachedHeadSlot() {
      return currentSlot.compareTo(headSlot) >= 0;
    }

    boolean isComplete() {
      return !needsMoreBlocks() || hasReachedHeadSlot();
    }

    SafeFuture<Void> sendExecutionPayload(final SignedExecutionPayloadEnvelope executionPayload) {
      return callback.respond(executionPayload).thenRun(sentExecutionPayloads::incrementAndGet);
    }

    void decrementRemainingExecutionPayloads() {
      remainingExecutionPayloads = remainingExecutionPayloads.minusMinZero(1);
    }

    void incrementCurrentSlot() {
      currentSlot = currentSlot.increment();
    }

    SafeFuture<Optional<SignedExecutionPayloadEnvelope>> loadNextExecutionPayload() {
      final UInt64 slot = this.currentSlot;
      final Bytes32 knownBlockRoot = knownBlockRoots.get(slot);
      if (knownBlockRoot != null) {
        // Known root so lookup by root
        return combinedChainDataClient
            .getExecutionPayloadByBlockRoot(knownBlockRoot)
            .thenApply(
                maybeExecutionPayload ->
                    maybeExecutionPayload.filter(
                        executionPayload -> executionPayload.getMessage().getSlot().equals(slot)));
      } else if ((!knownBlockRoots.isEmpty() && slot.compareTo(knownBlockRoots.firstKey()) >= 0)
          || slot.compareTo(headSlot) > 0) {
        // Unknown root but not finalized means this is an empty slot
        // Could also be because the first execution payload requested is above our head slot
        return SafeFuture.completedFuture(Optional.empty());
      } else {
        // EIP-7732 TODO: Must be a finalized execution payload so lookup by slot
        return SafeFuture.completedFuture(Optional.empty());
      }
    }
  }
}
