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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.peers.RequestApproval;
import tech.pegasys.teku.networking.eth2.rpc.core.PeerRequiredLocalMessageHandler;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseCallback;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.networking.p2p.rpc.StreamClosedException;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.SpecConfigEip7732;
import tech.pegasys.teku.spec.datastructures.execution.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.ExecutionPayloadEnvelopesByRootRequestMessage;
import tech.pegasys.teku.storage.client.RecentChainData;

public class ExecutionPayloadEnvelopesByRootMessageHandler
    extends PeerRequiredLocalMessageHandler<
        ExecutionPayloadEnvelopesByRootRequestMessage, SignedExecutionPayloadEnvelope> {
  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final RecentChainData recentChainData;
  private final Counter totalExecutionPayloadEnvelopesRequestedCounter;

  private final LabelledMetric<Counter> requestCounter;

  public ExecutionPayloadEnvelopesByRootMessageHandler(
      final Spec spec, final MetricsSystem metricsSystem, final RecentChainData recentChainData) {
    this.spec = spec;
    this.recentChainData = recentChainData;
    requestCounter =
        metricsSystem.createLabelledCounter(
            TekuMetricCategory.NETWORK,
            "rpc_execution_payload_envelopes_by_root_requests_total",
            "Total number of execution payload envelopes by root requests received",
            "status");
    totalExecutionPayloadEnvelopesRequestedCounter =
        metricsSystem.createCounter(
            TekuMetricCategory.NETWORK,
            "rpc_execution_payload_envelopes_by_root_requested_envelopes_total",
            "Total number of execution payload envelopes requested in accepted execution payload envelopes by root requests from peers");
  }

  @Override
  public Optional<RpcException> validateRequest(
      final String protocolId, final ExecutionPayloadEnvelopesByRootRequestMessage request) {
    final int maxRequestPayloads = getMaxRequestPayloads();

    if (request.size() > maxRequestPayloads) {
      requestCounter.labels("count_too_big").inc();
      return Optional.of(
          new RpcException(
              INVALID_REQUEST_CODE,
              "Only a maximum of "
                  + maxRequestPayloads
                  + " payloads can be requested per request"));
    }

    return Optional.empty();
  }

  @Override
  public void onIncomingMessage(
      final String protocolId,
      final Eth2Peer peer,
      final ExecutionPayloadEnvelopesByRootRequestMessage message,
      final ResponseCallback<SignedExecutionPayloadEnvelope> callback) {
    LOG.trace(
        "Peer {} requested {} ExecutionPayloadEnvelopes with roots: {}",
        peer.getId(),
        message.size(),
        message);

    final Optional<RequestApproval> executionPayloadEnvelopesRequestApproval =
        peer.approveExecutionPayloadEnvelopesRequest(callback, message.size());

    if (!peer.approveRequest() || executionPayloadEnvelopesRequestApproval.isEmpty()) {
      requestCounter.labels("rate_limited").inc();
      return;
    }

    requestCounter.labels("ok").inc();
    totalExecutionPayloadEnvelopesRequestedCounter.inc(message.size());

    final AtomicInteger sentExecutionPayloadEnvelopes = new AtomicInteger(0);

    SafeFuture<Void> future = SafeFuture.COMPLETE;

    for (final SszBytes32 blockRoot : message) {
      future =
          future.thenCompose(
              __ ->
                  retrieveExecutionPayloadEnvelope(blockRoot.get())
                      .thenCompose(
                          maybeEnvelope ->
                              maybeEnvelope
                                  .map(
                                      envelope ->
                                          callback
                                              .respond(envelope)
                                              .thenRun(
                                                  sentExecutionPayloadEnvelopes::incrementAndGet))
                                  .orElse(SafeFuture.COMPLETE)));
    }
    future.finish(
        () -> {
          if (sentExecutionPayloadEnvelopes.get() != message.size()) {
            peer.adjustExecutionPayloadEnvelopesRequest(
                executionPayloadEnvelopesRequestApproval.get(),
                sentExecutionPayloadEnvelopes.get());
          }
          callback.completeSuccessfully();
        },
        err -> {
          peer.adjustExecutionPayloadEnvelopesRequest(
              executionPayloadEnvelopesRequestApproval.get(), 0);
          handleError(callback, err);
        });
  }

  private int getMaxRequestPayloads() {
    final UInt64 currentEpoch = recentChainData.getCurrentEpoch().orElse(UInt64.ZERO);
    final SpecMilestone milestone = spec.getForkSchedule().getSpecMilestoneAtEpoch(currentEpoch);
    return SpecConfigEip7732.required(spec.forMilestone(milestone).getConfig())
        .getMaxRequestPayloads();
  }

  private SafeFuture<Optional<SignedExecutionPayloadEnvelope>> retrieveExecutionPayloadEnvelope(
      final Bytes32 blockRoot) {
    return recentChainData
        .getRecentlyValidatedExecutionPayloadEnvelopeByRoot(blockRoot)
        .map(envelope -> SafeFuture.completedFuture(Optional.of(envelope)))
        .orElseGet(() -> recentChainData.retrieveExecutionPayloadEnvelopeByBlockRoot(blockRoot));
  }

  private void handleError(
      final ResponseCallback<SignedExecutionPayloadEnvelope> callback, final Throwable error) {
    final Throwable rootCause = Throwables.getRootCause(error);
    if (rootCause instanceof RpcException) {
      LOG.trace(
          "Rejecting execution payload envelopes by root request", error); // Keep full context
      callback.completeWithErrorResponse((RpcException) rootCause);
    } else {
      if (rootCause instanceof StreamClosedException
          || rootCause instanceof ClosedChannelException) {
        LOG.trace("Stream closed while sending requested execution payload envelopes", error);
      } else {
        LOG.error("Failed to process execution payload envelopes by root request", error);
      }
      callback.completeWithUnexpectedError(error);
    }
  }
}
