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

package tech.pegasys.teku.networking.eth2;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.async.Waiter.waitFor;
import static tech.pegasys.teku.spec.SpecMilestone.GLOAS;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.p2p.rpc.RpcResponseListener;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.epbs.SignedExecutionPayloadAndState;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;

@TestSpecContext(milestone = {GLOAS})
public class ExecutionPayloadEnvelopesByRootIntegrationTest
    extends AbstractRpcMethodIntegrationTest {

  private Eth2Peer peer;

  @BeforeEach
  public void setUp(final TestSpecInvocationContextProvider.SpecContext specContext) {
    peer = createPeer(specContext.getSpec());
  }

  @TestTemplate
  public void requestExecutionPayloadEnvelopesByRoot_shouldReturnExecutionPayloadEnvelopes()
      throws ExecutionException, InterruptedException, TimeoutException {

    // up to slot 3
    final UInt64 targetSlot = UInt64.valueOf(3);
    final SignedBlockAndState block = peerStorage.chainUpdater().advanceChainUntil(targetSlot);
    peerStorage.chainUpdater().updateBestBlock(block);

    // grab expected execution payload envelopes from storage
    final List<SignedExecutionPayloadEnvelope> expectedExecutionPayloadEnvelopes =
        peerStorage
            .chainBuilder()
            .streamExecutionPayloadsAndStates(UInt64.ZERO)
            .map(SignedExecutionPayloadAndState::executionPayload)
            .toList();

    assertThat(expectedExecutionPayloadEnvelopes).hasSize(3);

    final List<Bytes32> beaconBlockRoots =
        expectedExecutionPayloadEnvelopes.stream()
            .map(SignedExecutionPayloadEnvelope::getMessage)
            .map(ExecutionPayloadEnvelope::getBeaconBlockRoot)
            .toList();

    final List<SignedExecutionPayloadEnvelope> executionPayloadEnvelopes =
        requestExecutionPayloadEnvelopesByRoot(peer, beaconBlockRoots);

    assertThat(executionPayloadEnvelopes)
        .containsExactlyInAnyOrderElementsOf(expectedExecutionPayloadEnvelopes);
  }

  private List<SignedExecutionPayloadEnvelope> requestExecutionPayloadEnvelopesByRoot(
      final Eth2Peer peer, final List<Bytes32> beaconBlockRoots)
      throws InterruptedException, ExecutionException, TimeoutException {
    final List<SignedExecutionPayloadEnvelope> executionPayloadEnvelopes = new ArrayList<>();
    waitFor(
        peer.requestExecutionPayloadEnvelopesByRoot(
            beaconBlockRoots, RpcResponseListener.from(executionPayloadEnvelopes::add)));
    assertThat(peer.getOutstandingRequests()).isEqualTo(0);
    return executionPayloadEnvelopes;
  }
}
