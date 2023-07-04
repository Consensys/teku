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
import static org.mockito.ArgumentMatchers.anyLong;
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

import com.google.common.collect.ImmutableSortedMap;
import java.util.ArrayList;
import java.util.Collections;
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
import tech.pegasys.teku.networking.eth2.peers.RequestApproval;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.BeaconChainMethodIds;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseCallback;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobSidecarsByRangeRequestMessage;
import tech.pegasys.teku.spec.datastructures.util.SlotAndBlockRootAndBlobIndex;
import tech.pegasys.teku.spec.logic.versions.deneb.helpers.MiscHelpersDeneb;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

public class BlobSidecarsByRangeMessageHandlerTest {

  private static final RpcEncoding RPC_ENCODING =
      RpcEncoding.createSszSnappyEncoding(
          TestSpecFactory.createDefault().getNetworkingConfig().getMaxChunkSize());

  private final UInt64 denebForkEpoch = UInt64.valueOf(1);

  private final Spec spec = TestSpecFactory.createMinimalWithDenebForkEpoch(denebForkEpoch);

  private final SpecConfigDeneb specConfigDeneb =
      SpecConfigDeneb.required(spec.forMilestone(SpecMilestone.DENEB).getConfig());

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private final int maxBlobsPerBlock = specConfigDeneb.getMaxBlobsPerBlock();

  private final int slotsPerEpoch = spec.getSlotsPerEpoch(ZERO);

  private final UInt64 startSlot = denebForkEpoch.increment().times(slotsPerEpoch);

  private final Bytes32 headBlockRoot = dataStructureUtil.randomBytes32();

  private final UInt64 count = UInt64.valueOf(5);

  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();

  private final Eth2Peer peer = mock(Eth2Peer.class);

  private final MiscHelpersDeneb miscHelpers =
      spec.forMilestone(SpecMilestone.DENEB).miscHelpers().toVersionDeneb().orElseThrow();

  @SuppressWarnings("unchecked")
  private final ResponseCallback<BlobSidecar> listener = mock(ResponseCallback.class);

  private final CombinedChainDataClient combinedChainDataClient =
      mock(CombinedChainDataClient.class);

  private final String protocolId =
      BeaconChainMethodIds.getBlobSidecarsByRangeMethodId(1, RPC_ENCODING);

  private final BlobSidecarsByRangeMessageHandler handler =
      new BlobSidecarsByRangeMessageHandler(
          spec, specConfigDeneb, metricsSystem, combinedChainDataClient);
  private final Optional<RequestApproval> allowedObjectsRequest =
      Optional.of(
          new RequestApproval.RequestApprovalBuilder().objectsCount(100).timeSeconds(ZERO).build());

  @BeforeEach
  public void setUp() {
    when(peer.approveRequest()).thenReturn(true);
    when(peer.approveBlobSidecarsRequest(eq(listener), anyLong()))
        .thenReturn(allowedObjectsRequest);
    when(combinedChainDataClient.getEarliestAvailableBlobSidecarSlot())
        .thenReturn(SafeFuture.completedFuture(Optional.of(ZERO)));
    when(combinedChainDataClient.getCurrentEpoch()).thenReturn(denebForkEpoch.increment());
    when(combinedChainDataClient.getBestBlockRoot()).thenReturn(Optional.of(headBlockRoot));
    // everything is finalized by default
    when(combinedChainDataClient.getFinalizedBlockSlot())
        .thenReturn(Optional.of(startSlot.plus(count)));
    when(listener.respond(any())).thenReturn(SafeFuture.COMPLETE);
  }

  @Test
  public void validateRequest_validRequest() {
    final Optional<RpcException> result =
        handler.validateRequest(
            protocolId, new BlobSidecarsByRangeRequestMessage(startSlot, ONE, maxBlobsPerBlock));
    assertThat(result).isEmpty();
  }

  @Test
  public void shouldNotSendBlobSidecarsIfCountIsTooBig() {
    final UInt64 maxRequestBlobSidecars =
        UInt64.valueOf(specConfigDeneb.getMaxRequestBlobSidecars());
    final BlobSidecarsByRangeRequestMessage request =
        new BlobSidecarsByRangeRequestMessage(
            startSlot, maxRequestBlobSidecars.increment(), maxBlobsPerBlock);

    handler.onIncomingMessage(protocolId, peer, request, listener);

    // Rate limiter not invoked
    verify(peer, never()).approveBlobSidecarsRequest(any(), anyLong());

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

    when(peer.approveBlobSidecarsRequest(listener, count.times(maxBlobsPerBlock).longValue()))
        .thenReturn(Optional.empty());

    final BlobSidecarsByRangeRequestMessage request =
        new BlobSidecarsByRangeRequestMessage(startSlot, count, maxBlobsPerBlock);

    handler.onIncomingMessage(protocolId, peer, request, listener);

    // Requesting 5 * maxBlobsPerBlock blob sidecars
    verify(peer, times(1))
        .approveBlobSidecarsRequest(any(), eq(count.times(maxBlobsPerBlock).longValue()));
    // No adjustment
    verify(peer, never()).adjustBlobSidecarsRequest(any(), anyLong());

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
    when(combinedChainDataClient.getEarliestAvailableBlobSidecarSlot())
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(denebForkEpoch.plus(5009).times(slotsPerEpoch))));

    // start slot in epoch 5000 within MIN_EPOCHS_FOR_BLOB_SIDECARS_REQUESTS range
    final BlobSidecarsByRangeRequestMessage request =
        new BlobSidecarsByRangeRequestMessage(
            UInt64.valueOf(5000).times(slotsPerEpoch), count, maxBlobsPerBlock);

    handler.onIncomingMessage(protocolId, peer, request, listener);

    // Requesting 5 * maxBlobsPerBlock blob sidecars
    verify(peer, times(1))
        .approveBlobSidecarsRequest(any(), eq(count.times(maxBlobsPerBlock).longValue()));
    // Request cancelled
    verify(peer, times(1))
        .adjustBlobSidecarsRequest(eq(allowedObjectsRequest.get()), eq(Long.valueOf(0)));

    // blob sidecars should be available from epoch 5000, but they are
    // available from epoch 5010
    verify(listener)
        .completeWithErrorResponse(
            new RpcException.ResourceUnavailableException(
                "Requested blob sidecars are not available."));
  }

  @Test
  public void shouldCompleteSuccessfullyIfNoBlobSidecarsInRange() {
    when(combinedChainDataClient.getBlobSidecarKeys(any(), any(), any()))
        .thenReturn(SafeFuture.completedFuture(Collections.emptyList()));
    final BlobSidecarsByRangeRequestMessage request =
        new BlobSidecarsByRangeRequestMessage(ZERO, count, maxBlobsPerBlock);

    handler.onIncomingMessage(protocolId, peer, request, listener);

    // Requesting 5 * maxBlobsPerBlock blob sidecars
    verify(peer, times(1))
        .approveBlobSidecarsRequest(any(), eq(count.times(maxBlobsPerBlock).longValue()));
    // Sending 0 blob sidecars
    verify(peer, times(1))
        .adjustBlobSidecarsRequest(eq(allowedObjectsRequest.get()), eq(Long.valueOf(0)));

    verify(combinedChainDataClient, never()).getBlobSidecarByKey(any());

    verify(listener, never()).respond(any());

    verify(listener).completeSuccessfully();
  }

  @Test
  public void shouldSendToPeerRequestedNumberOfFinalizedBlobSidecars() {

    final BlobSidecarsByRangeRequestMessage request =
        new BlobSidecarsByRangeRequestMessage(startSlot, count, maxBlobsPerBlock);

    final List<BlobSidecar> expectedSent = setUpBlobSidecarsData(startSlot, request.getMaxSlot());

    handler.onIncomingMessage(protocolId, peer, request, listener);

    // Requesting 5 * maxBlobsPerBlock blob sidecars
    verify(peer, times(1))
        .approveBlobSidecarsRequest(any(), eq(count.times(maxBlobsPerBlock).longValue()));
    // Sending expectedSent blob sidecars
    verify(peer, times(1))
        .adjustBlobSidecarsRequest(
            eq(allowedObjectsRequest.get()), eq(Long.valueOf(expectedSent.size())));

    final ArgumentCaptor<BlobSidecar> argumentCaptor = ArgumentCaptor.forClass(BlobSidecar.class);

    verify(listener, times(expectedSent.size())).respond(argumentCaptor.capture());

    final List<BlobSidecar> actualSent = argumentCaptor.getAllValues();

    verify(listener).completeSuccessfully();

    AssertionsForInterfaceTypes.assertThat(actualSent).containsExactlyElementsOf(expectedSent);
  }

  @Test
  public void shouldSendToPeerRequestedNumberOfCanonicalBlobSidecars() {

    final UInt64 latestFinalizedSlot = startSlot.plus(count).minus(3);
    when(combinedChainDataClient.getFinalizedBlockSlot())
        .thenReturn(Optional.of(latestFinalizedSlot));

    final BlobSidecarsByRangeRequestMessage request =
        new BlobSidecarsByRangeRequestMessage(startSlot, count, maxBlobsPerBlock);

    final List<BlobSidecar> allAvailableBlobs =
        setUpBlobSidecarsData(startSlot, request.getMaxSlot());

    // we simulate that the canonical non-finalized chain only contains blobs from last
    // slotAndBlockRoot
    final SlotAndBlockRoot canonicalSlotAndBlockRoot =
        allAvailableBlobs.get(allAvailableBlobs.size() - 1).getSlotAndBlockRoot();

    final List<BlobSidecar> expectedSent =
        allAvailableBlobs.stream()
            .filter(
                blobSidecar ->
                    blobSidecar
                            .getSlot()
                            .isLessThanOrEqualTo(latestFinalizedSlot) // include finalized
                        || blobSidecar
                            .getSlotAndBlockRoot()
                            .equals(canonicalSlotAndBlockRoot) // include non canonical
                )
            .collect(Collectors.toUnmodifiableList());

    // let return only canonical slot and block root as canonical
    when(combinedChainDataClient.getAncestorRoots(eq(startSlot), eq(ONE), any()))
        .thenReturn(
            ImmutableSortedMap.of(
                canonicalSlotAndBlockRoot.getSlot(), canonicalSlotAndBlockRoot.getBlockRoot()));

    handler.onIncomingMessage(protocolId, peer, request, listener);

    // Requesting 5 * maxBlobsPerBlock blob sidecars
    verify(peer, times(1))
        .approveBlobSidecarsRequest(any(), eq(count.times(maxBlobsPerBlock).longValue()));
    // Sending expectedSent blob sidecars
    verify(peer, times(1))
        .adjustBlobSidecarsRequest(
            eq(allowedObjectsRequest.get()), eq(Long.valueOf(expectedSent.size())));

    final ArgumentCaptor<BlobSidecar> argumentCaptor = ArgumentCaptor.forClass(BlobSidecar.class);

    verify(listener, times(expectedSent.size())).respond(argumentCaptor.capture());

    final List<BlobSidecar> actualSent = argumentCaptor.getAllValues();

    verify(listener).completeSuccessfully();

    AssertionsForInterfaceTypes.assertThat(actualSent).containsExactlyElementsOf(expectedSent);
  }

  @Test
  public void shouldIgnoreRequestWhenCountIsZero() {

    final BlobSidecarsByRangeRequestMessage request =
        new BlobSidecarsByRangeRequestMessage(startSlot, ZERO, maxBlobsPerBlock);

    final Optional<RequestApproval> zeroObjectRequests =
        Optional.of(
            new RequestApproval.RequestApprovalBuilder().timeSeconds(ZERO).objectsCount(0).build());

    when(peer.approveBlobSidecarsRequest(listener, 0)).thenReturn(zeroObjectRequests);

    handler.onIncomingMessage(protocolId, peer, request, listener);

    // Requesting 5 * maxBlobsPerBlock blob sidecars
    verify(peer, times(1)).approveBlobSidecarsRequest(any(), eq(Long.valueOf(0)));
    // no adjustment
    verify(peer, never()).adjustBlobSidecarsRequest(any(), anyLong());

    final ArgumentCaptor<BlobSidecar> argumentCaptor = ArgumentCaptor.forClass(BlobSidecar.class);

    verify(listener, never()).respond(argumentCaptor.capture());

    final List<BlobSidecar> actualSent = argumentCaptor.getAllValues();

    verify(listener).completeSuccessfully();

    AssertionsForInterfaceTypes.assertThat(actualSent).isEmpty();
  }

  private List<BlobSidecar> setUpBlobSidecarsData(final UInt64 startSlot, final UInt64 maxSlot) {
    final List<SlotAndBlockRootAndBlobIndex> keys = setupKeyList(startSlot, maxSlot);
    when(combinedChainDataClient.getBlobSidecarKeys(eq(startSlot), eq(maxSlot), any()))
        .thenAnswer(
            args ->
                SafeFuture.completedFuture(
                    keys.subList(
                        0, Math.min(keys.size(), ((UInt64) args.getArgument(2)).intValue()))));
    return keys.stream().map(this::setUpBlobSidecarDataForKey).collect(Collectors.toList());
  }

  private List<SlotAndBlockRootAndBlobIndex> setupKeyList(
      final UInt64 startSlot, final UInt64 maxSlot) {
    final List<SlotAndBlockRootAndBlobIndex> keys = new ArrayList<>();
    UInt64.rangeClosed(startSlot, maxSlot)
        .forEach(
            slot -> {
              final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(slot);
              UInt64.rangeClosed(
                      ZERO,
                      dataStructureUtil
                          .randomUInt64(miscHelpers.getBlobKzgCommitmentsCount(block))
                          .minusMinZero(1))
                  .forEach(
                      index ->
                          keys.add(new SlotAndBlockRootAndBlobIndex(slot, block.getRoot(), index)));
            });
    return keys;
  }

  private BlobSidecar setUpBlobSidecarDataForKey(final SlotAndBlockRootAndBlobIndex key) {
    final BlobSidecar blobSidecar =
        dataStructureUtil
            .createRandomBlobSidecarBuilder()
            .blockRoot(key.getBlockRoot())
            .slot(key.getSlot())
            .index(key.getBlobIndex())
            .blockParentRoot(dataStructureUtil.randomBytes32())
            .build();
    when(combinedChainDataClient.getBlobSidecarByKey(key))
        .thenReturn(SafeFuture.completedFuture(Optional.of(blobSidecar)));
    return blobSidecar;
  }
}
