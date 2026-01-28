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

import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.peers.RequestKey;
import tech.pegasys.teku.networking.eth2.rpc.core.PeerRequiredLocalMessageHandler;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseCallback;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.ExecutionPayloadEnvelopesByRangeRequestMessage;

// TODO-GLOAS: https://github.com/Consensys/teku/issues/9974
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
  private final Counter totalExecutionPayloadEnvelopesRequestedCounter;

  public ExecutionPayloadEnvelopesByRangeMessageHandler(
      final SpecConfigGloas config, final MetricsSystem metricsSystem) {
    this.config = config;
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
    LOG.trace(
        "Peer {} requested {} execution payload envelopes starting at slot {}",
        peer.getId(),
        message.getCount(),
        message.getStartSlot());

    final Optional<RequestKey> maybeRequestKey =
        peer.approveExecutionPayloadEnvelopesRequest(callback, message.getCount().longValue());

    if (!peer.approveRequest() || maybeRequestKey.isEmpty()) {
      requestCounter.labels("rate_limited").inc();
      return;
    }

    requestCounter.labels("ok").inc();
    totalExecutionPayloadEnvelopesRequestedCounter.inc(message.getCount().longValue());

    throw new UnsupportedOperationException("Not yet implemented");
  }
}
