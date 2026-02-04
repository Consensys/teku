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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory.NETWORK;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.BeaconChainMethodIds;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.ExecutionPayloadEnvelopesByRangeRequestMessage;
import tech.pegasys.teku.storage.client.RecentChainData;

public class ExecutionPayloadEnvelopesByRangeMessageHandlerTest {
  private final Spec spec = TestSpecFactory.createMinimalGloas();
  private static final RpcEncoding RPC_ENCODING =
      RpcEncoding.createSszSnappyEncoding(
          TestSpecFactory.createDefault().getNetworkingConfig().getMaxPayloadSize());

  private static final String PROTOCOL_ID =
      BeaconChainMethodIds.getExecutionPayloadEnvelopesByRangeMethodId(1, RPC_ENCODING);
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();
  private final RecentChainData recentChainData = mock(RecentChainData.class);

  @Test
  void validateRequest_countTooBig() {
    final ExecutionPayloadEnvelopesByRangeMessageHandler handler =
        new ExecutionPayloadEnvelopesByRangeMessageHandler(spec, metricsSystem, recentChainData);
    final Optional<RpcException> response =
        handler.validateRequest(
            PROTOCOL_ID,
            new ExecutionPayloadEnvelopesByRangeRequestMessage(UInt64.ONE, UInt64.valueOf(256)));
    assertThat(response).isNotEmpty();
    assertThat(response.get().getMessage())
        .containsIgnoringCase("maximum of 128 execution payload envelopes");
    assertThat(getLabelledCounterValue("count_too_big")).isEqualTo(1);
    assertThat(getLabelledCounterValue("start_slot_invalid")).isEqualTo(0);
    verifyNoInteractions(recentChainData);
  }

  @Test
  void validateRequest_maxCount() {
    final ExecutionPayloadEnvelopesByRangeMessageHandler handler =
        new ExecutionPayloadEnvelopesByRangeMessageHandler(spec, metricsSystem, recentChainData);
    when(recentChainData.getFinalizedEpoch()).thenReturn(UInt64.ZERO);
    final Optional<RpcException> response =
        handler.validateRequest(
            PROTOCOL_ID,
            new ExecutionPayloadEnvelopesByRangeRequestMessage(UInt64.ONE, UInt64.valueOf(128)));
    assertThat(response).isEmpty();
    assertThat(getLabelledCounterValue("count_too_big")).isEqualTo(0);
    assertThat(getLabelledCounterValue("start_slot_invalid")).isEqualTo(0);
    verify(recentChainData).getFinalizedEpoch();
  }

  @Test
  void validateRequest_startSlotTooEarly() {
    final ExecutionPayloadEnvelopesByRangeMessageHandler handler =
        new ExecutionPayloadEnvelopesByRangeMessageHandler(spec, metricsSystem, recentChainData);
    when(recentChainData.getFinalizedEpoch()).thenReturn(UInt64.ONE);
    final Optional<RpcException> response =
        handler.validateRequest(
            PROTOCOL_ID,
            new ExecutionPayloadEnvelopesByRangeRequestMessage(UInt64.ONE, UInt64.valueOf(128)));
    assertThat(response).isNotEmpty();
    assertThat(response.get().getMessage()).containsIgnoringCase("prior to finalized");
    assertThat(getLabelledCounterValue("count_too_big")).isEqualTo(0);
    assertThat(getLabelledCounterValue("start_slot_invalid")).isEqualTo(1);
    verify(recentChainData).getFinalizedEpoch();
  }

  private long getLabelledCounterValue(final String label) {
    return metricsSystem.getLabelledCounterValue(
        NETWORK, "rpc_execution_payload_envelopes_by_range_requests_total", label);
  }
}
