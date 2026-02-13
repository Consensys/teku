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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory.NETWORK;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.peers.RequestKey;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.BeaconChainMethodIds;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseCallback;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.epbs.SignedExecutionPayloadAndState;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.ExecutionPayloadEnvelopesByRangeRequestMessage;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

public class ExecutionPayloadEnvelopesByRangeMessageHandlerTest {
  private final Spec spec = TestSpecFactory.createMinimalGloas();
  private static final RpcEncoding RPC_ENCODING =
      RpcEncoding.createSszSnappyEncoding(
          TestSpecFactory.createDefault().getNetworkingConfig().getMaxPayloadSize());

  private static final String PROTOCOL_ID =
      BeaconChainMethodIds.getExecutionPayloadEnvelopesByRangeMethodId(1, RPC_ENCODING);
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();
  private final CombinedChainDataClient combinedChainDataClient =
      mock(CombinedChainDataClient.class);
  private final ExecutionPayloadEnvelopesByRangeMessageHandler handler =
      new ExecutionPayloadEnvelopesByRangeMessageHandler(
          SpecConfigGloas.required(spec.getGenesisSpecConfig()),
          metricsSystem,
          combinedChainDataClient);
  final Eth2Peer peer = mock(Eth2Peer.class);

  @SuppressWarnings("unchecked")
  final ResponseCallback<SignedExecutionPayloadEnvelope> callback = mock(ResponseCallback.class);

  private final ChainBuilder chainBuilder = ChainBuilder.create(spec);

  @BeforeEach
  public void setup() {
    chainBuilder.generateGenesis();
    when(peer.approveRequest()).thenReturn(true);
    when(peer.approveExecutionPayloadEnvelopesRequest(any(), anyLong()))
        .thenReturn(Optional.of(new RequestKey(ZERO, 42)));
    // Forward hot roots requests from the mock to the ChainBuilder
    when(combinedChainDataClient.getAncestorRoots(any(), eq(UInt64.ONE), any()))
        .thenAnswer(
            i -> {
              final UInt64 startSlot = i.getArgument(0);
              return chainBuilder
                  .streamBlocksAndStates(
                      startSlot, startSlot.plus(i.getArgument(2)).minusMinZero(1))
                  .collect(
                      Collectors.toMap(
                          blockAndState -> blockAndState.getBlock().getSlot(),
                          blockAndState -> blockAndState.getBlock().getRoot(),
                          (e1, e2) -> e1,
                          TreeMap::new));
            });
    // Forward execution payload envelope block root requests from the mock to the ChainBuilder
    when(combinedChainDataClient.getExecutionPayloadByBlockRoot(any()))
        .thenAnswer(
            i -> SafeFuture.completedFuture(chainBuilder.getExecutionPayload(i.getArgument(0))));

    when(callback.respond(any())).thenReturn(SafeFuture.COMPLETE);
  }

  @Test
  void validateRequest_countTooBig() {
    final Optional<RpcException> response =
        handler.validateRequest(
            PROTOCOL_ID,
            new ExecutionPayloadEnvelopesByRangeRequestMessage(UInt64.ONE, UInt64.valueOf(256)));
    assertThat(response).isNotEmpty();
    assertThat(response.get().getMessage())
        .containsIgnoringCase("maximum of 128 execution payload envelopes");
    assertThat(getLabelledCounterValue("count_too_big")).isEqualTo(1);
    verifyNoInteractions(combinedChainDataClient);
  }

  @Test
  void validateRequest_maxCount() {
    final Optional<RpcException> response =
        handler.validateRequest(
            PROTOCOL_ID,
            new ExecutionPayloadEnvelopesByRangeRequestMessage(UInt64.ONE, UInt64.valueOf(128)));
    assertThat(response).isEmpty();
    assertThat(getLabelledCounterValue("count_too_big")).isEqualTo(0);
  }

  @Test
  void onIncomingMessage_requestNotApproved() {
    when(peer.approveRequest()).thenReturn(false);

    final ExecutionPayloadEnvelopesByRangeRequestMessage message =
        new ExecutionPayloadEnvelopesByRangeRequestMessage(UInt64.ONE, UInt64.valueOf(2));
    handler.onIncomingMessage(PROTOCOL_ID, peer, message, callback);

    // Requesting 2 execution payload envelopes
    verify(peer).approveExecutionPayloadEnvelopesRequest(any(), eq(2L));
    verify(callback, never()).completeSuccessfully();
    verify(callback, never()).respond(any());

    // verify counters
    assertThat(getLabelledCounterValue("rate_limited")).isOne();
    assertThat(getLabelledCounterValue("ok")).isZero();
  }

  @Test
  void onIncomingMessage_respondsAllPayloads() {
    final List<SignedExecutionPayloadEnvelope> executionPayloadEnvelopes = buildChain(5);
    final UInt64 payloadCount = UInt64.valueOf(5);

    final ExecutionPayloadEnvelopesByRangeRequestMessage message =
        new ExecutionPayloadEnvelopesByRangeRequestMessage(UInt64.ONE, payloadCount);
    handler.onIncomingMessage(PROTOCOL_ID, peer, message, callback);

    // Requesting 5 execution payload envelopes
    verify(peer).approveExecutionPayloadEnvelopesRequest(any(), eq(payloadCount.longValue()));
    verify(callback, times(5)).respond(any());
    for (final SignedExecutionPayloadEnvelope executionPayloadEnvelope :
        executionPayloadEnvelopes) {
      verify(callback).respond(executionPayloadEnvelope);
    }

    verify(callback).completeSuccessfully();

    // verify counters
    assertThat(getLabelledCounterValue("ok")).isOne();
    assertThat(getExecutionPayloadEnvelopesRequestedCounterValue()).isEqualTo(5);
  }

  @Test
  public void onIncomingMessage_respondsWithSomeOfTheExecutionPayloadEnvelopes() {
    final List<SignedExecutionPayloadEnvelope> executionPayloadEnvelopes = buildChain(4);
    final UInt64 payloadCount = UInt64.valueOf(5);

    final ExecutionPayloadEnvelopesByRangeRequestMessage message =
        new ExecutionPayloadEnvelopesByRangeRequestMessage(UInt64.ONE, payloadCount);
    handler.onIncomingMessage(PROTOCOL_ID, peer, message, callback);

    // Requesting 5 execution payload envelopes
    verify(peer).approveExecutionPayloadEnvelopesRequest(any(), eq(5L));
    // Sending 4 execution payload envelopes: Rate limiter adjustment required
    verify(peer).adjustExecutionPayloadEnvelopesRequest(any(), eq(4L));
    verify(callback, times(4)).respond(any());

    for (final SignedExecutionPayloadEnvelope executionPayloadEnvelope :
        executionPayloadEnvelopes) {
      verify(callback).respond(executionPayloadEnvelope);
    }
    verify(callback).completeSuccessfully();

    // verify counters
    assertThat(getLabelledCounterValue("ok")).isOne();
    assertThat(getExecutionPayloadEnvelopesRequestedCounterValue()).isEqualTo(5);
  }

  private List<SignedExecutionPayloadEnvelope> buildChain(final int chainSize) {
    // Create some blocks to request
    final UInt64 latestSlot = chainBuilder.getLatestSlot();
    chainBuilder.generateBlocksUpToSlot(latestSlot.plus(chainSize));

    return chainBuilder
        .streamExecutionPayloadsAndStates(latestSlot.plus(1))
        .map(SignedExecutionPayloadAndState::executionPayload)
        .toList();
  }

  private long getLabelledCounterValue(final String label) {
    return metricsSystem.getLabelledCounterValue(
        NETWORK, "rpc_execution_payload_envelopes_by_range_requests_total", label);
  }

  private long getExecutionPayloadEnvelopesRequestedCounterValue() {
    return metricsSystem.getCounterValue(
        TekuMetricCategory.NETWORK,
        "rpc_execution_payload_envelopes_by_range_requested_envelopes_total");
  }
}
