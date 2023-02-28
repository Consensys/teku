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
import static tech.pegasys.teku.networking.eth2.rpc.core.RpcResponseStatus.INVALID_REQUEST_CODE;
import static tech.pegasys.teku.networking.eth2.rpc.core.RpcResponseStatus.RESOURCE_UNAVAILABLE;
import static tech.pegasys.teku.spec.config.Constants.MAX_CHUNK_SIZE_BELLATRIX;
import static tech.pegasys.teku.spec.config.Constants.MAX_REQUEST_BLOCKS_DENEB;
import static tech.pegasys.teku.spec.config.Constants.SYNC_BLOB_SIDECARS_SIZE;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
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
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobIdentifier;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobSidecarsByRootRequestMessage;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

public class BlobSidecarsByRootMessageHandlerTest {

  private static final RpcEncoding RPC_ENCODING =
      RpcEncoding.createSszSnappyEncoding(MAX_CHUNK_SIZE_BELLATRIX);

  private final String protocolId =
      BeaconChainMethodIds.getBlobSidecarsByRootMethodId(1, RPC_ENCODING);

  private final UInt64 denebForkEpoch = UInt64.valueOf(1);

  private final Spec spec = TestSpecFactory.createMinimalWithDenebForkEpoch(denebForkEpoch);

  private final UInt64 denebFirstSlot = spec.computeStartSlotAtEpoch(denebForkEpoch);

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private final ArgumentCaptor<BlobSidecar> blobSidecarCaptor =
      ArgumentCaptor.forClass(BlobSidecar.class);

  private final ArgumentCaptor<RpcException> rpcExceptionCaptor =
      ArgumentCaptor.forClass(RpcException.class);

  @SuppressWarnings("unchecked")
  private final ResponseCallback<BlobSidecar> callback = mock(ResponseCallback.class);

  private final CombinedChainDataClient combinedChainDataClient =
      mock(CombinedChainDataClient.class);

  private final Eth2Peer peer = mock(Eth2Peer.class);

  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();

  private final BlobSidecarsByRootMessageHandler handler =
      new BlobSidecarsByRootMessageHandler(
          spec, metricsSystem, denebForkEpoch, combinedChainDataClient);

  @BeforeEach
  public void setup() {
    when(peer.wantToMakeRequest()).thenReturn(true);
    when(peer.wantToReceiveBlobSidecars(eq(callback), anyLong())).thenReturn(true);
    when(combinedChainDataClient.getBlockByBlockRoot(any()))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(dataStructureUtil.randomSignedBeaconBlock(denebFirstSlot))));
    // deneb fork epoch is finalized
    when(combinedChainDataClient.getFinalizedBlock())
        .thenReturn(Optional.of(dataStructureUtil.randomSignedBeaconBlock(denebFirstSlot)));
    // current epoch is deneb fork epoch + 1
    when(combinedChainDataClient.getCurrentEpoch()).thenReturn(denebForkEpoch.plus(1));
    // mock the blob sidecars storage
    when(combinedChainDataClient.getBlobSidecarByBlockRootAndIndex(any(), any()))
        .thenAnswer(
            i -> {
              final Bytes32 blockRoot = i.getArgument(0);
              final UInt64 index = i.getArgument(1);
              return SafeFuture.completedFuture(
                  Optional.of(dataStructureUtil.randomBlobSidecar(blockRoot, index)));
            });
    when(callback.respond(any())).thenReturn(SafeFuture.COMPLETE);
  }

  @Test
  public void validateRequest_shouldNotAllowRequestLargerThanMaximumAllowed() {
    final int maxRequestBlobSidecars =
        calculateMaxRequestBlobSidecars(spec, SpecMilestone.DENEB).intValue();
    final BlobSidecarsByRootRequestMessage request =
        new BlobSidecarsByRootRequestMessage(
            dataStructureUtil.randomBlobIdentifiers(maxRequestBlobSidecars + 1));

    final Optional<RpcException> result = handler.validateRequest(protocolId, request);

    assertThat(result)
        .hasValueSatisfying(
            rpcException -> {
              assertThat(rpcException.getResponseCode()).isEqualTo(INVALID_REQUEST_CODE);
              assertThat(rpcException.getErrorMessageString())
                  .isEqualTo(
                      "Only a maximum of %d blob sidecars can be requested per request",
                      maxRequestBlobSidecars);
            });

    final long countTooBigCount =
        metricsSystem
            .getCounter(TekuMetricCategory.NETWORK, "rpc_blob_sidecars_by_root_requests_total")
            .getValue("count_too_big");

    assertThat(countTooBigCount).isOne();
  }

  @Test
  public void shouldNotSendBlobSidecarsIfPeerIsRateLimited() {

    when(peer.wantToReceiveBlobSidecars(callback, 5)).thenReturn(false);

    final BlobSidecarsByRootRequestMessage request =
        new BlobSidecarsByRootRequestMessage(dataStructureUtil.randomBlobIdentifiers(5));

    handler.onIncomingMessage(protocolId, peer, request, callback);

    final long rateLimitedCount =
        metricsSystem
            .getCounter(TekuMetricCategory.NETWORK, "rpc_blob_sidecars_by_root_requests_total")
            .getValue("rate_limited");

    assertThat(rateLimitedCount).isOne();

    verifyNoInteractions(callback);
  }

  @Test
  public void shouldSendResourceUnavailableIfBlockForBlockRootIsNotAvailable() {
    final List<BlobIdentifier> blobIdentifiers = dataStructureUtil.randomBlobIdentifiers(4);

    // the second block root can't be found in the database
    final Bytes32 secondBlockRoot = blobIdentifiers.get(1).getBlockRoot();
    when(combinedChainDataClient.getBlockByBlockRoot(secondBlockRoot))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    handler.onIncomingMessage(
        protocolId, peer, new BlobSidecarsByRootRequestMessage(blobIdentifiers), callback);

    verify(callback, times(1)).respond(blobSidecarCaptor.capture());
    verify(callback).completeWithErrorResponse(rpcExceptionCaptor.capture());

    // verify we responded with the first sidecar
    assertThat(blobSidecarCaptor.getValue().getBlockRoot())
        .isEqualTo(blobIdentifiers.get(0).getBlockRoot());

    final RpcException rpcException = rpcExceptionCaptor.getValue();

    assertThat(rpcException.getResponseCode()).isEqualTo(RESOURCE_UNAVAILABLE);
    assertThat(rpcException.getErrorMessageString())
        .isEqualTo("Block for block root (%s) couldn't be retrieved", secondBlockRoot);
  }

  @Test
  public void
      shouldSendResourceUnavailableIfBlockRootReferencesBlockEarlierThanTheMinimumRequestEpoch() {
    final List<BlobIdentifier> blobIdentifiers = dataStructureUtil.randomBlobIdentifiers(3);

    // first slot will be earlier than the minimum_request_epoch (for this test it is
    // denebForkEpoch)
    when(combinedChainDataClient.getBlockByBlockRoot(any()))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(dataStructureUtil.randomSignedBeaconBlock(UInt64.ONE))));

    handler.onIncomingMessage(
        protocolId, peer, new BlobSidecarsByRootRequestMessage(blobIdentifiers), callback);

    verify(callback, never()).respond(any());
    verify(callback).completeWithErrorResponse(rpcExceptionCaptor.capture());

    final RpcException rpcException = rpcExceptionCaptor.getValue();

    assertThat(rpcException.getResponseCode()).isEqualTo(INVALID_REQUEST_CODE);
    assertThat(rpcException.getErrorMessageString())
        .isEqualTo(
            "Block root (%s) references a block earlier than the minimum_request_epoch (%s)",
            blobIdentifiers.get(0).getBlockRoot(), denebForkEpoch);
  }

  @Test
  public void shouldSendResourceUnavailableIfBlobSidecarIsNotAvailable() {
    final List<BlobIdentifier> blobIdentifiers = dataStructureUtil.randomBlobIdentifiers(3);

    final BlobIdentifier secondBlobIdentifier = blobIdentifiers.get(1);

    // the second blob sidecar can't be found in the database
    when(combinedChainDataClient.getBlobSidecarByBlockRootAndIndex(
            secondBlobIdentifier.getBlockRoot(), secondBlobIdentifier.getIndex()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    handler.onIncomingMessage(
        protocolId, peer, new BlobSidecarsByRootRequestMessage(blobIdentifiers), callback);

    verify(callback, times(1)).respond(blobSidecarCaptor.capture());
    verify(callback).completeWithErrorResponse(rpcExceptionCaptor.capture());

    // verify we responded with the first sidecar
    assertThat(blobSidecarCaptor.getValue().getBlockRoot())
        .isEqualTo(blobIdentifiers.get(0).getBlockRoot());

    final RpcException rpcException = rpcExceptionCaptor.getValue();

    assertThat(rpcException.getResponseCode()).isEqualTo(RESOURCE_UNAVAILABLE);
    assertThat(rpcException.getErrorMessageString())
        .isEqualTo("Blob sidecar for blob identifier (%s) was not available", secondBlobIdentifier);
  }

  @Test
  public void shouldSendToPeerRequestedBlobSidecars() {
    final List<BlobIdentifier> blobIdentifiers = dataStructureUtil.randomBlobIdentifiers(5);

    handler.onIncomingMessage(
        protocolId, peer, new BlobSidecarsByRootRequestMessage(blobIdentifiers), callback);

    verify(callback, times(5)).respond(blobSidecarCaptor.capture());

    final List<BlobSidecar> sentBlobSidecars = blobSidecarCaptor.getAllValues();

    // verify sent blob sidecars
    IntStream.range(0, 5)
        .forEach(
            index -> {
              final BlobIdentifier identifier = blobIdentifiers.get(index);
              final BlobSidecar blobSidecar = sentBlobSidecars.get(index);
              assertThat(blobSidecar.getBlockRoot()).isEqualTo(identifier.getBlockRoot());
              assertThat(blobSidecar.getIndex()).isEqualTo(identifier.getIndex());
            });

    verify(callback).completeSuccessfully();
  }

  @Test
  public void
      verifySyncBlobSidecarsSizeIsNotLargerThanMaxRequestBlobSidecarsForShardingMilestones() {
    final List<SpecMilestone> shardingMilestones =
        SpecMilestone.getAllFutureMilestones(SpecMilestone.CAPELLA);

    shardingMilestones.forEach(
        milestone -> {
          final Spec spec = TestSpecFactory.createMainnet(milestone);
          final UInt64 maxRequestBlobSidecars = calculateMaxRequestBlobSidecars(spec, milestone);
          assertThat(SYNC_BLOB_SIDECARS_SIZE.isLessThanOrEqualTo(maxRequestBlobSidecars)).isTrue();
        });
  }

  private UInt64 calculateMaxRequestBlobSidecars(final Spec spec, final SpecMilestone milestone) {
    return MAX_REQUEST_BLOCKS_DENEB.times(
        SpecConfigDeneb.required(spec.forMilestone(milestone).getConfig()).getMaxBlobsPerBlock());
  }
}
