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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import com.google.common.collect.ImmutableSortedMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;
import org.assertj.core.api.AssertionsForInterfaceTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.peers.RequestApproval;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.BeaconChainMethodIds;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseCallback;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobSidecarsByRangeRequestMessage;
import tech.pegasys.teku.spec.datastructures.util.SlotAndBlockRootAndBlobIndex;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.store.UpdatableStore;

public class BlobSidecarsByRangeFuluDeprecationTest {
  private final UInt64 genesisTime = UInt64.valueOf(1982239L);
  private final UInt64 fuluForkEpoch = UInt64.valueOf(2);
  private final Optional<RequestApproval> allowedObjectsRequest =
      Optional.of(
          new RequestApproval.RequestApprovalBuilder().objectsCount(100).timeSeconds(ZERO).build());

  @SuppressWarnings("unchecked")
  private final ResponseCallback<BlobSidecar> callback = mock(ResponseCallback.class);

  private final CombinedChainDataClient combinedChainDataClient =
      mock(CombinedChainDataClient.class);
  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private final UpdatableStore store = mock(UpdatableStore.class);
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();
  private String protocolId;
  private UInt64 fuluForkFirstSlot;
  private DataStructureUtil dataStructureUtil;
  private Spec spec;
  private int maxBlobsPerBlock;
  Eth2Peer peer = mock(Eth2Peer.class);

  @BeforeEach
  public void setup() {
    spec =
        TestSpecFactory.createMinimalWithCapellaDenebElectraAndFuluForkEpoch(
            UInt64.ZERO, UInt64.ZERO, UInt64.ONE, fuluForkEpoch);
    dataStructureUtil = new DataStructureUtil(spec);
    fuluForkFirstSlot = spec.computeStartSlotAtEpoch(fuluForkEpoch);
    final RpcEncoding rpcEncoding =
        RpcEncoding.createSszSnappyEncoding(spec.getNetworkingConfig().getMaxPayloadSize());
    protocolId = BeaconChainMethodIds.getBlobSidecarsByRootMethodId(1, rpcEncoding);
    maxBlobsPerBlock =
        SpecConfigDeneb.required(spec.forMilestone(SpecMilestone.ELECTRA).getConfig())
            .getMaxBlobsPerBlock();

    when(peer.approveRequest()).thenReturn(true);
    when(peer.approveBlobSidecarsRequest(eq(callback), anyLong()))
        .thenReturn(allowedObjectsRequest);
    reset(combinedChainDataClient);
    when(combinedChainDataClient.getBlockByBlockRoot(any()))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(dataStructureUtil.randomSignedBeaconBlock(fuluForkFirstSlot))));
    // epoch 1 = electra is finalized, epochs 2+ not
    when(combinedChainDataClient.getFinalizedBlock())
        .thenReturn(
            Optional.of(
                dataStructureUtil.randomSignedBeaconBlock(
                    spec.computeStartSlotAtEpoch(UInt64.ZERO))));
    when(combinedChainDataClient.getStore()).thenReturn(store);
    when(combinedChainDataClient.getRecentChainData()).thenReturn(recentChainData);
    when(callback.respond(any())).thenReturn(SafeFuture.COMPLETE);

    // mock store
    when(store.getGenesisTime()).thenReturn(genesisTime);
    // current epoch is deneb fork epoch + 1
    when(store.getTimeSeconds())
        .thenReturn(
            spec.getSlotStartTime(
                fuluForkEpoch.increment().times(spec.getSlotsPerEpoch(ZERO)), genesisTime));
  }

  @Test
  public void byRangeShouldSendToPeerPreFuluBlobSidecarsOnly() {
    final BlobSidecarsByRangeMessageHandler handler =
        new BlobSidecarsByRangeMessageHandler(spec, metricsSystem, combinedChainDataClient);
    final UInt64 slotsPerEpoch = UInt64.valueOf(spec.getSlotsPerEpoch(ZERO));
    // request for over 2 epochs from epoch 1 2nd slot to end of epoch 2. On epoch 2, the first slot
    // is where fulu is activated. Expect replies limited up to (not including) fulu activation slot
    final UInt64 startSlot = spec.computeStartSlotAtEpoch(UInt64.ONE).increment();
    final UInt64 count = slotsPerEpoch.times(2).plus(5);
    final UInt64 latestFinalizedSlot = spec.computeEpochAtSlot(UInt64.valueOf(3));
    when(combinedChainDataClient.getFinalizedBlockSlot())
        .thenReturn(Optional.of(latestFinalizedSlot));
    when(combinedChainDataClient.getEarliestAvailableBlobSidecarSlot())
        .thenReturn(SafeFuture.completedFuture(Optional.of(ZERO)));
    final BlobSidecarsByRangeRequestMessage request =
        new BlobSidecarsByRangeRequestMessage(startSlot, count, maxBlobsPerBlock);
    final List<BlobSidecar> allAvailableBlobs =
        setUpBlobSidecarsData(startSlot, fuluForkFirstSlot.minus(1));

    final SlotAndBlockRoot canonicalSlotAndBlockRoot =
        allAvailableBlobs.getLast().getSlotAndBlockRoot();

    final List<BlobSidecar> expectedSent =
        allAvailableBlobs.stream()
            .filter(
                blobSidecar ->
                    blobSidecar
                            .getSlot()
                            .isLessThanOrEqualTo(latestFinalizedSlot) // include finalized
                        || blobSidecar
                            .getSlotAndBlockRoot()
                            .equals(canonicalSlotAndBlockRoot) // include canonical
                )
            .toList();

    when(combinedChainDataClient.getAncestorRoots(eq(startSlot), eq(ONE), any()))
        .thenReturn(
            ImmutableSortedMap.of(
                canonicalSlotAndBlockRoot.getSlot(), canonicalSlotAndBlockRoot.getBlockRoot()));

    handler.onIncomingMessage(protocolId, peer, request, callback);

    verify(peer, times(1))
        .approveBlobSidecarsRequest(any(), eq(count.times(maxBlobsPerBlock).longValue()));
    verify(peer, times(1))
        .adjustBlobSidecarsRequest(
            eq(allowedObjectsRequest.get()), eq(Long.valueOf(expectedSent.size())));

    final ArgumentCaptor<BlobSidecar> argumentCaptor = ArgumentCaptor.forClass(BlobSidecar.class);

    verify(callback, times(expectedSent.size())).respond(argumentCaptor.capture());

    final List<BlobSidecar> actualSent = argumentCaptor.getAllValues();

    verify(callback).completeSuccessfully();

    AssertionsForInterfaceTypes.assertThat(actualSent).containsExactlyElementsOf(expectedSent);
  }

  private List<Pair<SignedBeaconBlockHeader, SlotAndBlockRootAndBlobIndex>> setupKeyAndHeaderList(
      final UInt64 startSlot, final UInt64 maxSlot) {
    final List<Pair<SignedBeaconBlockHeader, SlotAndBlockRootAndBlobIndex>> headerAndKeys =
        new ArrayList<>();
    UInt64.rangeClosed(startSlot, maxSlot)
        .forEach(
            slot -> {
              final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(slot);
              UInt64.rangeClosed(
                      ZERO,
                      dataStructureUtil
                          .randomUInt64(
                              spec.forMilestone(SpecMilestone.ELECTRA)
                                  .miscHelpers()
                                  .getBlobKzgCommitmentsCount(block))
                          .minusMinZero(1))
                  .forEach(
                      index ->
                          headerAndKeys.add(
                              Pair.of(
                                  block.asHeader(),
                                  new SlotAndBlockRootAndBlobIndex(slot, block.getRoot(), index))));
            });
    return headerAndKeys;
  }

  private List<BlobSidecar> setUpBlobSidecarsData(final UInt64 startSlot, final UInt64 maxSlot) {
    final List<Pair<SignedBeaconBlockHeader, SlotAndBlockRootAndBlobIndex>> headerAndKeys =
        setupKeyAndHeaderList(startSlot, maxSlot);
    when(combinedChainDataClient.getBlobSidecarKeys(eq(startSlot), eq(maxSlot), anyLong()))
        .thenAnswer(
            args ->
                SafeFuture.completedFuture(
                    headerAndKeys
                        .subList(
                            0, Math.min(headerAndKeys.size(), Math.toIntExact(args.getArgument(2))))
                        .stream()
                        .map(Pair::getValue)
                        .toList()));
    return headerAndKeys.stream()
        .map(this::setUpBlobSidecarDataForKey)
        .collect(Collectors.toList());
  }

  private BlobSidecar setUpBlobSidecarDataForKey(
      final Pair<SignedBeaconBlockHeader, SlotAndBlockRootAndBlobIndex> keyAndHeaders) {
    final BlobSidecar blobSidecar =
        dataStructureUtil
            .createRandomBlobSidecarBuilder()
            .signedBeaconBlockHeader(keyAndHeaders.getLeft())
            .index(keyAndHeaders.getValue().getBlobIndex())
            .build();
    when(combinedChainDataClient.getBlobSidecarByKey(keyAndHeaders.getValue()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(blobSidecar)));
    return blobSidecar;
  }
}
