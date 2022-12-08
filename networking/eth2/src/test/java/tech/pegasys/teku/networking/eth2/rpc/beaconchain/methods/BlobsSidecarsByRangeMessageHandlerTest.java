/*
 * Copyright ConsenSys Software Inc., 2022
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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.spec.config.Constants.MAX_CHUNK_SIZE;

import java.util.Optional;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.BeaconChainMethodIds;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseCallback;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip4844.BlobsSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobsSidecarsByRangeRequestMessage;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

class BlobsSidecarsByRangeMessageHandlerTest {

  private static final RpcEncoding RPC_ENCODING =
      RpcEncoding.createSszSnappyEncoding(MAX_CHUNK_SIZE);

  private final Spec spec = TestSpecFactory.createMinimalEip4844();

  private final UInt64 maxRequestSize = UInt64.valueOf(8);

  private final MetricsSystem metricsSystem = new NoOpMetricsSystem();

  private final Eth2Peer peer = mock(Eth2Peer.class);

  @SuppressWarnings("unchecked")
  private final ResponseCallback<BlobsSidecar> listener = mock(ResponseCallback.class);

  private final CombinedChainDataClient combinedChainDataClient =
      mock(CombinedChainDataClient.class);

  private final String protocolId =
      BeaconChainMethodIds.getBlobsSidecarsByRangeMethodId(1, RPC_ENCODING);

  private final BlobsSidecarsByRangeMessageHandler handler =
      new BlobsSidecarsByRangeMessageHandler(
          spec, metricsSystem, combinedChainDataClient, maxRequestSize);

  @Test
  public void validateRequest() {
    // TODO: implement
    final Optional<RpcException> result =
        handler.validateRequest(protocolId, new BlobsSidecarsByRangeRequestMessage(ZERO, ONE));
    assertThat(result).isEmpty();
  }

  @Test
  public void shouldReturnRequestedNumberOfBlobsSidecars() {
    // TODO: implement
    final int startSlot = 1;
    final int count = 5;
    handler.onIncomingMessage(
        protocolId,
        peer,
        new BlobsSidecarsByRangeRequestMessage(UInt64.valueOf(startSlot), UInt64.valueOf(count)),
        listener);

    verify(listener).completeWithUnexpectedError(any());
  }
}
