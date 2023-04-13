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

package tech.pegasys.teku.beacon.sync.forward.singlepeer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.peers.PeerStatus;
import tech.pegasys.teku.networking.p2p.rpc.RpcResponseListener;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.StatusMessage;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.blobs.BlobSidecarManager;
import tech.pegasys.teku.statetransition.block.BlockImporter;
import tech.pegasys.teku.storage.client.RecentChainData;

public abstract class AbstractSyncTest {

  protected final UInt64 denebForkEpoch = UInt64.valueOf(100);
  protected final Spec spec = TestSpecFactory.createMinimalWithDenebForkEpoch(denebForkEpoch);
  protected final UInt64 denebFirstSlot = spec.computeStartSlotAtEpoch(denebForkEpoch);
  protected final Eth2Peer peer = mock(Eth2Peer.class);
  protected final BlockImporter blockImporter = mock(BlockImporter.class);
  protected final BlobSidecarManager blobsSidecarManager = mock(BlobSidecarManager.class);
  protected final RecentChainData recentChainData = mock(RecentChainData.class);

  protected final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  protected final StubAsyncRunner asyncRunner = new StubAsyncRunner();

  @BeforeEach
  public void setup() {
    when(recentChainData.getSpec()).thenReturn(spec);
  }

  @SuppressWarnings("unchecked")
  protected final ArgumentCaptor<RpcResponseListener<SignedBeaconBlock>>
      blockResponseListenerArgumentCaptor = ArgumentCaptor.forClass(RpcResponseListener.class);

  @SuppressWarnings("unchecked")
  protected final ArgumentCaptor<RpcResponseListener<BlobSidecar>>
      blobSidecarResponseListenerArgumentCaptor =
          ArgumentCaptor.forClass(RpcResponseListener.class);

  protected void completeRequestWithBlockAtSlot(final SafeFuture<Void> request, final UInt64 slot) {
    completeRequestWithBlocksAtSlots(request, slot, UInt64.ONE);
  }

  protected void completeRequestWithBlocksAtSlots(
      final SafeFuture<Void> request, final UInt64 startSlot, final UInt64 count) {
    // Capture latest response listener
    verify(peer, atLeastOnce())
        .requestBlocksByRange(any(), any(), blockResponseListenerArgumentCaptor.capture());
    final RpcResponseListener<SignedBeaconBlock> responseListener =
        blockResponseListenerArgumentCaptor.getValue();
    final List<SignedBeaconBlock> blocks =
        respondWithBlocksAtSlots(request, responseListener, startSlot, count);
    for (final SignedBeaconBlock block : blocks) {
      verify(blockImporter).importBlock(block);
    }
    request.complete(null);
    asyncRunner.executeQueuedActions();
  }

  protected void completeRequestWithBlobSidecarsAtSlots(
      final SafeFuture<Void> request, final UInt64 startSlot, final UInt64 count) {
    // Capture latest response listener
    verify(peer, atLeastOnce())
        .requestBlobSidecarsByRange(
            any(), any(), blobSidecarResponseListenerArgumentCaptor.capture());
    final RpcResponseListener<BlobSidecar> responseListener =
        blobSidecarResponseListenerArgumentCaptor.getValue();
    final List<BlobSidecar> blobSidecars =
        respondWithBlobSidecarsAtSlots(request, responseListener, startSlot, count);
    for (final BlobSidecar blobSidecar : blobSidecars) {
      verify(blobsSidecarManager).importBlobSidecar(blobSidecar);
    }
    request.complete(null);
    asyncRunner.executeQueuedActions();
  }

  protected List<SignedBeaconBlock> respondWithBlocksAtSlots(
      final SafeFuture<Void> request,
      final RpcResponseListener<SignedBeaconBlock> responseListener,
      final UInt64 startSlot,
      final UInt64 count) {
    return respondWithBlocksAtSlots(request, responseListener, getSlotsRange(startSlot, count));
  }

  protected List<SignedBeaconBlock> respondWithBlocksAtSlots(
      final SafeFuture<Void> request,
      final RpcResponseListener<SignedBeaconBlock> responseListener,
      final UInt64... slots) {
    final List<SignedBeaconBlock> blocks = new ArrayList<>();
    for (final UInt64 slot : slots) {
      final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(slot);
      blocks.add(block);
      responseListener.onResponse(block).propagateExceptionTo(request);
    }
    return blocks;
  }

  protected List<BlobSidecar> respondWithBlobSidecarsAtSlots(
      final SafeFuture<Void> request,
      final RpcResponseListener<BlobSidecar> responseListener,
      final UInt64 startSlot,
      final UInt64 count) {
    return respondWithBlobSidecarsAtSlots(
        request, responseListener, getSlotsRange(startSlot, count));
  }

  protected List<BlobSidecar> respondWithBlobSidecarsAtSlots(
      final SafeFuture<Void> request,
      final RpcResponseListener<BlobSidecar> responseListener,
      final UInt64... slots) {
    final List<BlobSidecar> blobSidecars = new ArrayList<>();
    for (final UInt64 slot : slots) {
      final BlobSidecar blobSidecar =
          dataStructureUtil.createRandomBlobSidecarBuilder().slot(slot).build();
      blobSidecars.add(blobSidecar);
      responseListener.onResponse(blobSidecar).propagateExceptionTo(request);
    }
    return blobSidecars;
  }

  protected PeerStatus withPeerHeadSlot(
      final UInt64 peerHeadSlot, final UInt64 peerFinalizedEpoch, final Bytes32 peerHeadBlockRoot) {
    final PeerStatus peerStatus =
        PeerStatus.fromStatusMessage(
            new StatusMessage(
                Bytes4.leftPad(Bytes.EMPTY),
                Bytes32.ZERO,
                peerFinalizedEpoch,
                peerHeadBlockRoot,
                peerHeadSlot));

    when(peer.getStatus()).thenReturn(peerStatus);
    return peerStatus;
  }

  private UInt64[] getSlotsRange(final UInt64 startSlot, final UInt64 count) {
    return UInt64.range(startSlot, startSlot.plus(count)).toArray(UInt64[]::new);
  }
}
