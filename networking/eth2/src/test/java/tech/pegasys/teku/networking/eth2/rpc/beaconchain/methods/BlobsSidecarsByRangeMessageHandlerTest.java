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

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.spec.config.Constants.MAX_CHUNK_SIZE;

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
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.ResourceUnavailableException;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip4844.BlobsSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobsSidecarsByRangeRequestMessage;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

public class BlobsSidecarsByRangeMessageHandlerTest {

  private static final RpcEncoding RPC_ENCODING =
      RpcEncoding.createSszSnappyEncoding(MAX_CHUNK_SIZE);

  private final Spec spec = TestSpecFactory.createMinimalEip4844();

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private final UInt64 eip4844ForkEpoch = UInt64.valueOf(1);

  private final int slotsPerEpoch = spec.getSlotsPerEpoch(ZERO);

  private final UInt64 startSlot = eip4844ForkEpoch.increment().times(slotsPerEpoch);

  private final Bytes32 headBlockRoot = dataStructureUtil.randomBytes32();

  private final UInt64 count = UInt64.valueOf(5);

  private final UInt64 maxRequestSize = UInt64.valueOf(8);

  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();

  private final Eth2Peer peer = mock(Eth2Peer.class);

  @SuppressWarnings("unchecked")
  private final ResponseCallback<BlobsSidecar> listener = mock(ResponseCallback.class);

  private final CombinedChainDataClient combinedChainDataClient =
      mock(CombinedChainDataClient.class);

  private final String protocolId =
      BeaconChainMethodIds.getBlobsSidecarsByRangeMethodId(1, RPC_ENCODING);

  private final BlobsSidecarsByRangeMessageHandler handler =
      new BlobsSidecarsByRangeMessageHandler(
          spec, eip4844ForkEpoch, metricsSystem, combinedChainDataClient, maxRequestSize);

  @BeforeEach
  public void setUp() {
    when(peer.wantToMakeRequest()).thenReturn(true);
    when(peer.wantToReceiveBlobsSidecars(listener, count.longValue())).thenReturn(true);
    when(combinedChainDataClient.getEarliestAvailableBlobsSidecarEpoch())
        .thenReturn(SafeFuture.completedFuture(Optional.of(ZERO)));
    when(combinedChainDataClient.getCurrentEpoch()).thenReturn(eip4844ForkEpoch.increment());
    when(combinedChainDataClient.getBestBlockRoot()).thenReturn(Optional.of(headBlockRoot));
    when(listener.respond(any())).thenReturn(SafeFuture.COMPLETE);
  }

  @Test
  public void validateRequest_validRequest() {
    final Optional<RpcException> result =
        handler.validateRequest(protocolId, new BlobsSidecarsByRangeRequestMessage(startSlot, ONE));
    assertThat(result).isEmpty();
  }

  @Test
  public void shouldNotSendBlobsSidecarsIfPeerIsRateLimited() {

    when(peer.wantToReceiveBlobsSidecars(listener, 5)).thenReturn(false);

    final BlobsSidecarsByRangeRequestMessage request =
        new BlobsSidecarsByRangeRequestMessage(startSlot, count);

    handler.onIncomingMessage(protocolId, peer, request, listener);

    final long rateLimitedCount =
        metricsSystem
            .getCounter(TekuMetricCategory.NETWORK, "rpc_blobs_sidecars_by_range_requests_total")
            .getValue("rate_limited");

    assertThat(rateLimitedCount).isOne();

    verifyNoInteractions(listener);
  }

  @Test
  public void shouldSendResourceUnavailableIfBlobsSidecarsAreNotAvailable() {

    // current epoch is 5020
    when(combinedChainDataClient.getCurrentEpoch()).thenReturn(UInt64.valueOf(5020));

    // earliest available sidecar epoch - 5010
    when(combinedChainDataClient.getEarliestAvailableBlobsSidecarEpoch())
        .thenReturn(SafeFuture.completedFuture(Optional.of(eip4844ForkEpoch.plus(5009))));

    // start slot in epoch 5000 within MIN_EPOCHS_FOR_BLOBS_SIDECARS_REQUESTS range
    final BlobsSidecarsByRangeRequestMessage request =
        new BlobsSidecarsByRangeRequestMessage(UInt64.valueOf(5000).times(slotsPerEpoch), count);

    handler.onIncomingMessage(protocolId, peer, request, listener);

    // blobs sidecars should be available from epoch 5000, but they are
    // available from epoch 5010
    verify(listener)
        .completeWithErrorResponse(
            new ResourceUnavailableException("Blobs sidecars are not available."));
  }

  @Test
  public void shouldCompleteSuccessfullyIfRequestNotWithinRange() {
    when(combinedChainDataClient.getBlockAtSlotExact(any(), eq(headBlockRoot)))
        .thenReturn(
            SafeFuture.completedFuture(Optional.of(dataStructureUtil.randomSignedBeaconBlock())));
    // no sidecars in database
    when(combinedChainDataClient.getBlobsSidecarBySlotAndBlockRoot(any(), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    // request not within the MIN_EPOCHS_FOR_BLOBS_SIDECARS_REQUESTS range [1,2] so available is
    // assumed
    final BlobsSidecarsByRangeRequestMessage request =
        new BlobsSidecarsByRangeRequestMessage(ZERO, count);

    handler.onIncomingMessage(protocolId, peer, request, listener);

    verify(combinedChainDataClient, times(count.intValue()))
        .getBlobsSidecarBySlotAndBlockRoot(any(), any());

    verify(listener).completeSuccessfully();
  }

  @Test
  public void shouldSendToPeerRequestedNumberOfBlobsSidecars() {

    final BlobsSidecarsByRangeRequestMessage request =
        new BlobsSidecarsByRangeRequestMessage(startSlot, count);

    final List<BlobsSidecar> expectedSent =
        setUpBlobsSidecarData(startSlot, request.getMaxSlot(), headBlockRoot);

    handler.onIncomingMessage(protocolId, peer, request, listener);

    final ArgumentCaptor<BlobsSidecar> argumentCaptor = ArgumentCaptor.forClass(BlobsSidecar.class);

    verify(listener, times(count.intValue())).respond(argumentCaptor.capture());

    final List<BlobsSidecar> actualSent = argumentCaptor.getAllValues();

    verify(listener).completeSuccessfully();

    assertThat(actualSent).containsExactlyElementsOf(expectedSent);
  }

  private List<BlobsSidecar> setUpBlobsSidecarData(
      final UInt64 startSlot, final UInt64 maxSlot, final Bytes32 headBlockRoot) {
    return UInt64.rangeClosed(startSlot, maxSlot)
        .map(
            slot -> {
              final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(slot);
              when(combinedChainDataClient.getBlockAtSlotExact(slot, headBlockRoot))
                  .thenReturn(SafeFuture.completedFuture(Optional.of(block)));
              final BlobsSidecar blobsSidecar =
                  dataStructureUtil.randomBlobsSidecar(headBlockRoot, slot);
              when(combinedChainDataClient.getBlobsSidecarBySlotAndBlockRoot(slot, block.getRoot()))
                  .thenReturn(SafeFuture.completedFuture(Optional.of(blobsSidecar)));
              return blobsSidecar;
            })
        .collect(Collectors.toList());
  }
}
