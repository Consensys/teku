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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.spec.config.Constants.MAX_CHUNK_SIZE;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.BeaconChainMethodIds;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseCallback;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcResponseStatus;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.eip4844.SignedBeaconBlockAndBlobsSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BeaconBlockAndBlobsSidecarByRootRequestMessage;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.store.UpdatableStore;

public class BeaconBlockAndBlobsSidecarByRootMessageHandlerTest {

  private static final RpcEncoding RPC_ENCODING =
      RpcEncoding.createSszSnappyEncoding(MAX_CHUNK_SIZE);

  private final UInt64 eip4844ForkEpoch = UInt64.ONE;

  private final UInt64 finalizedEpoch = UInt64.valueOf(3);

  private final Spec spec = TestSpecFactory.createMinimalEip4844();

  private final int slotsPerEpoch = spec.getSlotsPerEpoch(UInt64.ONE);

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();

  private final String protocolId =
      BeaconChainMethodIds.getBeaconBlockAndBlobsSidecarByRoot(1, RPC_ENCODING);

  private final RecentChainData recentChainData = mock(RecentChainData.class);

  private final Eth2Peer peer = mock(Eth2Peer.class);

  private final UpdatableStore store = mock(UpdatableStore.class);

  @BeforeEach
  public void setUp() {
    when(peer.wantToMakeRequest()).thenReturn(true);
    when(peer.wantToReceiveBlockAndBlobsSidecars(any(), anyLong())).thenReturn(true);
    when(recentChainData.getStore()).thenReturn(store);
  }

  @SuppressWarnings("unchecked")
  private final ResponseCallback<SignedBeaconBlockAndBlobsSidecar> callback =
      mock(ResponseCallback.class);

  final BeaconBlockAndBlobsSidecarByRootMessageHandler handler =
      new BeaconBlockAndBlobsSidecarByRootMessageHandler(
          spec, eip4844ForkEpoch, metricsSystem, recentChainData);

  @Test
  public void validateRequest_resourceNotAvailableWhenNotInSupportedRange() {
    when(recentChainData.getFinalizedEpoch()).thenReturn(finalizedEpoch);
    // 4098 - 4096 = 2
    when(recentChainData.getCurrentEpoch()).thenReturn(Optional.of(UInt64.valueOf(4098)));

    final Bytes32 blockRoot1 = dataStructureUtil.randomBytes32();
    final Bytes32 blockRoot2 = dataStructureUtil.randomBytes32();

    when(recentChainData.getSlotForBlockRoot(any()))
        .thenReturn(Optional.of(finalizedEpoch.plus(1)));
    // requested epoch for blockRoot1 is earlier than the `minimum_request_epoch`
    when(recentChainData.getSlotForBlockRoot(blockRoot1)).thenReturn(Optional.of(UInt64.ONE));

    final BeaconBlockAndBlobsSidecarByRootRequestMessage request =
        new BeaconBlockAndBlobsSidecarByRootRequestMessage(List.of(blockRoot1, blockRoot2));

    final Optional<RpcException> result = handler.validateRequest(protocolId, request);

    assertThat(result)
        .hasValueSatisfying(
            rpcException -> {
              assertThat(rpcException.getResponseCode())
                  .isEqualTo(RpcResponseStatus.RESOURCE_UNAVAILABLE);
              assertThat(rpcException.getErrorMessageString())
                  .isEqualTo("Can't request block and blobs sidecar earlier than epoch 3");
            });

    verifyRequestsMetric("resource_unavailable", 1);
  }

  @Test
  public void onIncomingMessage_rateLimitedIfPeerDoesNotWantToMakeRequest() {
    when(peer.wantToMakeRequest()).thenReturn(false);

    handler.onIncomingMessage(protocolId, peer, createRandomRequest(), callback);
    verifyRequestsMetric("rate_limited", 1);
    verifyNoInteractions(recentChainData);
  }

  @Test
  public void onIncomingMessage_rateLimitedIfPeerDoesNotWantToReceiveBlockAndBlobsSidecar() {
    when(peer.wantToReceiveBlockAndBlobsSidecars(eq(callback), anyLong())).thenReturn(false);

    handler.onIncomingMessage(protocolId, peer, createRandomRequest(), callback);
    verifyRequestsMetric("rate_limited", 1);
    verifyNoInteractions(recentChainData);
  }

  @Test
  public void onIncomingMessage_requestBlockAndBlobsSidecars() {
    when(recentChainData.getFinalizedEpoch()).thenReturn(finalizedEpoch);
    when(recentChainData.getCurrentEpoch()).thenReturn(Optional.of(finalizedEpoch.plus(1)));
    when(recentChainData.getSlotForBlockRoot(any()))
        .thenReturn(Optional.of(finalizedEpoch.times(slotsPerEpoch)));

    final Bytes32 blockRoot1 = dataStructureUtil.randomBytes32();
    final Bytes32 blockRoot2 = dataStructureUtil.randomBytes32();
    final Bytes32 blockRoot3 = dataStructureUtil.randomBytes32();

    final List<SignedBeaconBlockAndBlobsSidecar> expectedSent =
        mockBlocksAndBlobsSidecars(blockRoot1, blockRoot2, blockRoot3);

    final BeaconBlockAndBlobsSidecarByRootRequestMessage request =
        new BeaconBlockAndBlobsSidecarByRootRequestMessage(
            List.of(blockRoot1, blockRoot2, blockRoot3));

    handler.onIncomingMessage(protocolId, peer, request, callback);

    final ArgumentCaptor<SignedBeaconBlockAndBlobsSidecar> argumentCaptor =
        ArgumentCaptor.forClass(SignedBeaconBlockAndBlobsSidecar.class);

    verify(callback, times(3)).respond(argumentCaptor.capture());

    final List<SignedBeaconBlockAndBlobsSidecar> actualSent = argumentCaptor.getAllValues();

    verify(callback).completeSuccessfully();

    assertThat(actualSent).containsExactlyElementsOf(expectedSent);

    verifyRequestsMetric("ok", 1);
    verifyRequestedCounter(3);
  }

  private BeaconBlockAndBlobsSidecarByRootRequestMessage createRandomRequest() {
    return new BeaconBlockAndBlobsSidecarByRootRequestMessage(
        List.of(dataStructureUtil.randomBytes32(), dataStructureUtil.randomBytes32()));
  }

  private void verifyRequestsMetric(final String status, final long expectedCount) {
    assertThat(
            metricsSystem
                .getCounter(
                    TekuMetricCategory.NETWORK,
                    "rpc_block_and_blobs_sidecar_by_root_requests_total")
                .getValue(status))
        .isEqualTo(expectedCount);
  }

  private void verifyRequestedCounter(final long expectedCount) {
    assertThat(
            metricsSystem
                .getCounter(
                    TekuMetricCategory.NETWORK,
                    "rpc_block_and_blobs_sidecar_by_root_requested_total")
                .getValue())
        .isEqualTo(expectedCount);
  }

  private List<SignedBeaconBlockAndBlobsSidecar> mockBlocksAndBlobsSidecars(
      final Bytes32... blockRoots) {
    return Arrays.stream(blockRoots)
        .map(
            blockRoot -> {
              final SignedBeaconBlockAndBlobsSidecar blockAndBlobsSidecar =
                  dataStructureUtil.randomSignedBeaconBlockAndBlobsSidecar();
              when(store.retrieveSignedBlockAndBlobsSidecar(blockRoot))
                  .thenReturn(SafeFuture.completedFuture(Optional.of(blockAndBlobsSidecar)));
              return blockAndBlobsSidecar;
            })
        .collect(Collectors.toList());
  }
}
