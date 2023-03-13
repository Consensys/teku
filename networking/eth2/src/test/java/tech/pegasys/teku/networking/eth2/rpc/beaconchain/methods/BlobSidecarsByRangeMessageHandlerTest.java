/*
 * Copyright ConsenSys Software Inc., 2023
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.networking.eth2.rpc.core.RpcResponseStatus.INVALID_REQUEST_CODE;
import static tech.pegasys.teku.spec.config.Constants.MAX_CHUNK_SIZE;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import org.assertj.core.api.AssertionsForInterfaceTypes;
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
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobSidecarsByRangeRequestMessage;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

public class BlobSidecarsByRangeMessageHandlerTest {

  private static final RpcEncoding RPC_ENCODING =
      RpcEncoding.createSszSnappyEncoding(MAX_CHUNK_SIZE);

  private final Spec spec = TestSpecFactory.createMinimalDeneb();

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private final UInt64 denebForkEpoch = UInt64.valueOf(1);

  private final SpecConfigDeneb specConfig =
      SpecConfigDeneb.required(spec.atEpoch(denebForkEpoch).getConfig());

  private final int slotsPerEpoch = spec.getSlotsPerEpoch(ZERO);

  private final UInt64 startSlot = denebForkEpoch.increment().times(slotsPerEpoch);

  private final Bytes32 headBlockRoot = dataStructureUtil.randomBytes32();

  private final UInt64 count = UInt64.valueOf(5);

  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();

  private final Eth2Peer peer = mock(Eth2Peer.class);

  @SuppressWarnings("unchecked")
  private final ResponseCallback<BlobSidecar> listener = mock(ResponseCallback.class);

  private final CombinedChainDataClient combinedChainDataClient =
      mock(CombinedChainDataClient.class);

  private final String protocolId =
      BeaconChainMethodIds.getBlobSidecarsByRangeMethodId(1, RPC_ENCODING);

  private final BlobSidecarsByRangeMessageHandler handler =
      new BlobSidecarsByRangeMessageHandler(
          spec, denebForkEpoch, metricsSystem, combinedChainDataClient);

  private final UInt64 maxBlobsPerBlock = UInt64.valueOf(specConfig.getMaxBlobsPerBlock());

  @BeforeEach
  public void setUp() {
    when(peer.popRequest()).thenReturn(true);
    when(peer.popBlobSidecarRequests(listener, count.times(maxBlobsPerBlock).longValue()))
        .thenReturn(true);
    when(combinedChainDataClient.getEarliestAvailableBlobSidecarEpoch())
        .thenReturn(SafeFuture.completedFuture(Optional.of(ZERO)));
    when(combinedChainDataClient.getCurrentEpoch()).thenReturn(denebForkEpoch.increment());
    when(combinedChainDataClient.getBestBlockRoot()).thenReturn(Optional.of(headBlockRoot));
    when(listener.respond(any())).thenReturn(SafeFuture.COMPLETE);
  }

  @Test
  public void validateRequest_validRequest() {
    final Optional<RpcException> result =
        handler.validateRequest(protocolId, new BlobSidecarsByRangeRequestMessage(startSlot, ONE));
    assertThat(result).isEmpty();
  }

  @Test
  public void shouldNotSendBlobSidecarsIfCountIsTooBig() {
    final UInt64 maxRequestBlobSidecars = specConfig.getMaxRequestBlobSidecars();
    final BlobSidecarsByRangeRequestMessage request =
        new BlobSidecarsByRangeRequestMessage(startSlot, maxRequestBlobSidecars.increment());

    handler.onIncomingMessage(protocolId, peer, request, listener);

    final long rateLimitedCount =
        metricsSystem
            .getCounter(TekuMetricCategory.NETWORK, "rpc_blob_sidecars_by_range_requests_total")
            .getValue("count_too_big");

    assertThat(rateLimitedCount).isOne();

    verify(listener)
        .completeWithErrorResponse(
            new RpcException(
                INVALID_REQUEST_CODE,
                String.format(
                    "Only a maximum of %s blob sidecars can be requested per request",
                    maxRequestBlobSidecars)));
  }

  @Test
  public void shouldNotSendBlobSidecarsIfPeerIsRateLimited() {

    when(peer.popBlobSidecarRequests(listener, 20)).thenReturn(false);

    final BlobSidecarsByRangeRequestMessage request =
        new BlobSidecarsByRangeRequestMessage(startSlot, count);

    handler.onIncomingMessage(protocolId, peer, request, listener);

    final long rateLimitedCount =
        metricsSystem
            .getCounter(TekuMetricCategory.NETWORK, "rpc_blob_sidecars_by_range_requests_total")
            .getValue("rate_limited");

    assertThat(rateLimitedCount).isOne();

    verifyNoInteractions(listener);
  }

  @Test
  public void shouldSendResourceUnavailableIfBlobSidecarsAreNotAvailable() {

    // current epoch is 5020
    when(combinedChainDataClient.getCurrentEpoch()).thenReturn(UInt64.valueOf(5020));

    // earliest available sidecar epoch - 5010
    when(combinedChainDataClient.getEarliestAvailableBlobSidecarEpoch())
        .thenReturn(SafeFuture.completedFuture(Optional.of(denebForkEpoch.plus(5009))));

    // start slot in epoch 5000 within MIN_EPOCHS_FOR_BLOB_SIDECARS_REQUESTS range
    final BlobSidecarsByRangeRequestMessage request =
        new BlobSidecarsByRangeRequestMessage(UInt64.valueOf(5000).times(slotsPerEpoch), count);

    handler.onIncomingMessage(protocolId, peer, request, listener);

    // blob sidecars should be available from epoch 5000, but they are
    // available from epoch 5010
    verify(listener)
        .completeWithErrorResponse(
            new RpcException.ResourceUnavailableException(
                "Requested blob sidecars are not available."));
  }

  @Test
  public void shouldCompleteSuccessfullyIfRequestNotWithinRange() {
    when(combinedChainDataClient.getBlockAtSlotExact(any(), eq(headBlockRoot)))
        .thenReturn(
            SafeFuture.completedFuture(Optional.of(dataStructureUtil.randomSignedBeaconBlock())));

    final BlobSidecarsByRangeRequestMessage request =
        new BlobSidecarsByRangeRequestMessage(ZERO, count);

    handler.onIncomingMessage(protocolId, peer, request, listener);

    verify(combinedChainDataClient, times(count.times(maxBlobsPerBlock).intValue()))
        .getBlobSidecarByBlockRootAndIndex(any(), any());

    verify(listener, never()).respond(any());

    verify(listener).completeSuccessfully();
  }

  @Test
  public void shouldSendToPeerRequestedNumberOfBlobSidecars() {

    final BlobSidecarsByRangeRequestMessage request =
        new BlobSidecarsByRangeRequestMessage(startSlot, count);

    final List<BlobSidecar> expectedSent =
        setUpBlobSidecarsData(startSlot, request.getMaxSlot(), headBlockRoot);

    handler.onIncomingMessage(protocolId, peer, request, listener);

    final ArgumentCaptor<BlobSidecar> argumentCaptor = ArgumentCaptor.forClass(BlobSidecar.class);

    verify(listener, times(count.times(maxBlobsPerBlock).intValue()))
        .respond(argumentCaptor.capture());

    verify(combinedChainDataClient, times(count.intValue())).getBlockAtSlotExact(any(), any());

    final List<BlobSidecar> actualSent = argumentCaptor.getAllValues();

    verify(listener).completeSuccessfully();

    AssertionsForInterfaceTypes.assertThat(actualSent).containsExactlyElementsOf(expectedSent);
  }

  @Test
  public void shouldIgnoreRequestWhenCountIsZero() {

    final BlobSidecarsByRangeRequestMessage request =
        new BlobSidecarsByRangeRequestMessage(startSlot, ZERO);

    when(peer.popBlobSidecarRequests(listener, 0)).thenReturn(true);

    handler.onIncomingMessage(protocolId, peer, request, listener);

    final ArgumentCaptor<BlobSidecar> argumentCaptor = ArgumentCaptor.forClass(BlobSidecar.class);

    verify(listener, never()).respond(argumentCaptor.capture());

    final List<BlobSidecar> actualSent = argumentCaptor.getAllValues();

    verify(listener).completeSuccessfully();

    AssertionsForInterfaceTypes.assertThat(actualSent).isEmpty();
  }

  @Test
  public void shouldIterateThroughIndicesAndSlots() {

    UInt64 count = UInt64.valueOf(5);
    UInt64 currentSlot = ZERO;
    BlobSidecarsByRangeMessageHandler.RequestState request =
        handler
        .new RequestState(
            listener,
            maxBlobsPerBlock,
            specConfig.getMaxRequestBlobSidecars(),
            headBlockRoot,
            currentSlot,
            count);

    for (int slot = currentSlot.intValue(); slot <= count.intValue(); slot++) {
      for (int index = 0; index < maxBlobsPerBlock.intValue(); index++) {
        assertThat(request.isComplete()).isFalse();
        assertThat(request.getCurrentSlot()).isEqualTo(UInt64.valueOf(slot));
        assertThat(request.getCurrentIndex()).isEqualTo(UInt64.valueOf(index));
        request.incrementSlotAndIndex();
      }
    }

    assertThat(request.isComplete()).isTrue();
  }

  private List<BlobSidecar> setUpBlobSidecarsData(
      final UInt64 startSlot, final UInt64 maxSlot, final Bytes32 headBlockRoot) {
    return UInt64.rangeClosed(startSlot, maxSlot)
        .flatMap(
            slot -> {
              final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(slot);
              when(combinedChainDataClient.getBlockAtSlotExact(slot, headBlockRoot))
                  .thenReturn(SafeFuture.completedFuture(Optional.of(block)));
              return setUpBlobSidecarsDataForSlot(slot, block).stream();
            })
        .collect(Collectors.toList());
  }

  private List<BlobSidecar> setUpBlobSidecarsDataForSlot(
      final UInt64 slot, final SignedBeaconBlock block) {
    return UInt64.range(ZERO, maxBlobsPerBlock)
        .map(
            index -> {
              final BlobSidecar blobSidecar =
                  dataStructureUtil
                      .createRandomBlobSidecarBuilder()
                      .blockRoot(block.getRoot())
                      .slot(slot)
                      .index(index)
                      .blockParentRoot(block.getParentRoot())
                      .build();
              when(combinedChainDataClient.getBlobSidecarByBlockRootAndIndex(
                      block.getRoot(), index))
                  .thenReturn(SafeFuture.completedFuture(Optional.of(blobSidecar)));
              return blobSidecar;
            })
        .collect(Collectors.toList());
  }
}
