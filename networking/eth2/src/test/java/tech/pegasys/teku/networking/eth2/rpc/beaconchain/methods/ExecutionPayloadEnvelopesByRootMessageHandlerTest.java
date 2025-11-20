/*
 * Copyright Consensys Software Inc., 2025
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
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
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
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.epbs.SignedExecutionPayloadAndState;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.ExecutionPayloadEnvelopesByRootRequestMessage;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.storage.client.RecentChainData;

class ExecutionPayloadEnvelopesByRootMessageHandlerTest {

  private static final RpcEncoding RPC_ENCODING =
      RpcEncoding.createSszSnappyEncoding(
          TestSpecFactory.createDefault().getNetworkingConfig().getMaxPayloadSize());

  private static final String V2_PROTOCOL_ID =
      BeaconChainMethodIds.getExecutionPayloadEnvelopesByRootMethodId(1, RPC_ENCODING);

  private final Spec spec = TestSpecFactory.createMinimalGloas();
  private final ChainBuilder chainBuilder = ChainBuilder.create(spec);
  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();

  final ExecutionPayloadEnvelopesByRootMessageHandler handler =
      new ExecutionPayloadEnvelopesByRootMessageHandler(recentChainData, metricsSystem);

  final Eth2Peer peer = mock(Eth2Peer.class);

  @SuppressWarnings("unchecked")
  final ResponseCallback<SignedExecutionPayloadEnvelope> callback = mock(ResponseCallback.class);

  @BeforeEach
  public void setup() {
    chainBuilder.generateGenesis();
    when(peer.approveRequest()).thenReturn(true);
    when(peer.approveExecutionPayloadEnvelopesRequest(any(), anyLong()))
        .thenReturn(Optional.of(new RequestKey(ZERO, 42)));
    // Forward execution payload envelope requests from the mock to the ChainBuilder
    when(recentChainData.retrieveSignedExecutionPayloadEnvelopeByBlockRoot(any()))
        .thenAnswer(
            i -> SafeFuture.completedFuture(chainBuilder.getExecutionPayload(i.getArgument(0))));
    when(callback.respond(any())).thenReturn(SafeFuture.COMPLETE);
  }

  @Test
  public void checkPeerIsRateLimited_requestNotApproved() {
    when(peer.approveRequest()).thenReturn(false);

    final List<SignedExecutionPayloadEnvelope> executionPayloadEnvelopes = buildChain(2);

    final ExecutionPayloadEnvelopesByRootRequestMessage message =
        createRequestFromExecutionPayloadEnvelopes(executionPayloadEnvelopes);
    handler.onIncomingMessage(V2_PROTOCOL_ID, peer, message, callback);

    // Requesting 2 execution payload envelopes
    verify(peer).approveExecutionPayloadEnvelopesRequest(any(), eq(2L));

    verify(callback, never()).completeSuccessfully();
    verify(callback, never()).respond(any());

    // verify counters
    assertThat(getRequestCounterValueForLabel("rate_limited")).isOne();
    assertThat(getRequestCounterValueForLabel("ok")).isZero();
  }

  @Test
  public void checkPeerIsRateLimited_noRequestKey() {
    when(peer.approveExecutionPayloadEnvelopesRequest(eq(callback), eq(2L)))
        .thenReturn(Optional.empty());

    final List<SignedExecutionPayloadEnvelope> executionPayloadEnvelopes = buildChain(2);

    final ExecutionPayloadEnvelopesByRootRequestMessage message =
        createRequestFromExecutionPayloadEnvelopes(executionPayloadEnvelopes);
    handler.onIncomingMessage(V2_PROTOCOL_ID, peer, message, callback);

    verify(callback, never()).completeSuccessfully();
    verify(callback, never()).respond(any());

    // verify counters
    assertThat(getRequestCounterValueForLabel("rate_limited")).isOne();
    assertThat(getRequestCounterValueForLabel("ok")).isZero();
  }

  @Test
  public void onIncomingMessage_respondsWithAllExecutionPayloadEnvelopes() {
    final List<SignedExecutionPayloadEnvelope> executionPayloadEnvelopes = buildChain(5);

    final ExecutionPayloadEnvelopesByRootRequestMessage message =
        createRequestFromExecutionPayloadEnvelopes(executionPayloadEnvelopes);
    handler.onIncomingMessage(V2_PROTOCOL_ID, peer, message, callback);

    // Requesting 5 execution payload envelopes
    verify(peer)
        .approveExecutionPayloadEnvelopesRequest(
            any(), eq(Long.valueOf(executionPayloadEnvelopes.size())));
    // Sending 5 execution payload envelopes: No rate limiter adjustment required
    verify(peer, never()).adjustExecutionPayloadEnvelopesRequest(any(), anyLong());

    verify(callback, times(5)).respond(any());

    for (final SignedExecutionPayloadEnvelope executionPayloadEnvelope :
        executionPayloadEnvelopes) {
      verify(callback).respond(executionPayloadEnvelope);
    }

    verify(callback).completeSuccessfully();

    // verify counters
    assertThat(getRequestCounterValueForLabel("ok")).isOne();
    assertThat(getExecutionPayloadEnvelopesRequestedCounterValue()).isEqualTo(5);
  }

  @Test
  public void onIncomingMessage_respondsWithSomeOfTheExecutionPayloadEnvelopes() {
    final List<SignedExecutionPayloadEnvelope> executionPayloadEnvelopes = buildChain(4);

    final List<Bytes32> beaconBlockRoots =
        new ArrayList<>(
            executionPayloadEnvelopes.stream()
                .map(SignedExecutionPayloadEnvelope::getMessage)
                .map(ExecutionPayloadEnvelope::getBeaconBlockRoot)
                .toList());

    beaconBlockRoots.add(Bytes32.random());

    final ExecutionPayloadEnvelopesByRootRequestMessage message =
        createRequestFromBeaconBlockRoots(beaconBlockRoots);
    handler.onIncomingMessage(V2_PROTOCOL_ID, peer, message, callback);

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
    assertThat(getRequestCounterValueForLabel("ok")).isOne();
    assertThat(getExecutionPayloadEnvelopesRequestedCounterValue()).isEqualTo(5);
  }

  private ExecutionPayloadEnvelopesByRootRequestMessage createRequestFromExecutionPayloadEnvelopes(
      final List<SignedExecutionPayloadEnvelope> executionPayloadEnvelopes) {
    final List<Bytes32> beaconBlockRoots =
        executionPayloadEnvelopes.stream()
            .map(SignedExecutionPayloadEnvelope::getMessage)
            .map(ExecutionPayloadEnvelope::getBeaconBlockRoot)
            .toList();
    return createRequestFromBeaconBlockRoots(beaconBlockRoots);
  }

  private ExecutionPayloadEnvelopesByRootRequestMessage createRequestFromBeaconBlockRoots(
      final List<Bytes32> beaconBlockRoots) {
    return new ExecutionPayloadEnvelopesByRootRequestMessage(
        SchemaDefinitionsGloas.required(spec.getGenesisSchemaDefinitions())
            .getExecutionPayloadEnvelopesByRootRequestMessageSchema(),
        beaconBlockRoots);
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

  private long getRequestCounterValueForLabel(final String label) {
    return metricsSystem.getLabelledCounterValue(
        TekuMetricCategory.NETWORK,
        "rpc_execution_payload_envelopes_by_root_requests_total",
        label);
  }

  private long getExecutionPayloadEnvelopesRequestedCounterValue() {
    return metricsSystem.getCounterValue(
        TekuMetricCategory.NETWORK,
        "rpc_execution_payload_envelopes_by_root_requested_envelopes_total");
  }
}
