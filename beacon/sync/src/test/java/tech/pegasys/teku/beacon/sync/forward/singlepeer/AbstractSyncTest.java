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

import com.google.common.collect.Streams;
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
import tech.pegasys.teku.spec.datastructures.execution.versions.eip4844.BlobsSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.StatusMessage;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.blobs.BlobsSidecarManager;
import tech.pegasys.teku.statetransition.block.BlockImporter;
import tech.pegasys.teku.storage.client.RecentChainData;

public abstract class AbstractSyncTest {
  protected final UInt64 eip4844ForkEpoch = UInt64.valueOf(112260);
  protected final Spec spec = TestSpecFactory.createMinimalWithEip4844ForkEpoch(eip4844ForkEpoch);
  protected final Eth2Peer peer = mock(Eth2Peer.class);
  protected final BlockImporter blockImporter = mock(BlockImporter.class);
  protected final BlobsSidecarManager blobsSidecarManager = mock(BlobsSidecarManager.class);
  protected final RecentChainData storageClient = mock(RecentChainData.class);

  protected final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createMinimalEip4844());
  protected final StubAsyncRunner asyncRunner = new StubAsyncRunner();

  @BeforeEach
  public void setup() {
    when(storageClient.getSpec()).thenReturn(spec);
  }

  @SuppressWarnings("unchecked")
  protected final ArgumentCaptor<RpcResponseListener<SignedBeaconBlock>>
      blockResponseListenerArgumentCaptor = ArgumentCaptor.forClass(RpcResponseListener.class);

  @SuppressWarnings("unchecked")
  protected final ArgumentCaptor<RpcResponseListener<BlobsSidecar>>
      blobsSidecarResponseListenerArgumentCaptor =
          ArgumentCaptor.forClass(RpcResponseListener.class);

  protected void completeRequestWithBlockAtSlot(
      final SafeFuture<Void> request, final int lastBlockSlot) {
    completeRequestWithBlockAtSlot(request, UInt64.valueOf(lastBlockSlot));
  }

  protected void completeRequestWithBlockAtSlot(
      final SafeFuture<Void> request, final UInt64 lastBlockSlot) {
    // Capture latest response listener
    verify(peer, atLeastOnce())
        .requestBlocksByRange(any(), any(), blockResponseListenerArgumentCaptor.capture());
    final RpcResponseListener<SignedBeaconBlock> responseListener =
        blockResponseListenerArgumentCaptor.getValue();

    final List<SignedBeaconBlock> blocks =
        respondWithBlocksAtSlots(responseListener, lastBlockSlot);
    request.complete(null);
    for (SignedBeaconBlock block : blocks) {
      verify(blockImporter).importBlock(block);
    }
    asyncRunner.executeQueuedActions();
  }

  protected List<SignedBeaconBlock> respondWithBlocksAtSlots(
      final RpcResponseListener<SignedBeaconBlock> responseListener, UInt64... slots) {
    final List<SignedBeaconBlock> blocks = new ArrayList<>();
    for (final UInt64 slot : slots) {
      final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(slot);
      blocks.add(block);
      responseListener.onResponse(block).join();
    }
    return blocks;
  }

  protected List<BlobsSidecar> respondWithBlobsSidecarsAtSlots(
      final RpcResponseListener<BlobsSidecar> responseListener, UInt64... slots) {
    final List<BlobsSidecar> blobsSidecars = new ArrayList<>();
    for (final UInt64 slot : slots) {
      final BlobsSidecar blobsSidecar = dataStructureUtil.randomBlobsSidecar(slot);
      responseListener.onResponse(blobsSidecar).join();
    }
    return blobsSidecars;
  }

  protected void completeRequestsWithBlocksAndBlobsSidecars(
      final List<SafeFuture<Void>> blocksRequests,
      final List<SafeFuture<Void>> blobsSidecarsRequests) {

    Streams.forEachPair(
        blocksRequests.stream(),
        blobsSidecarsRequests.stream(),
        (blockRequest, blobsSidecarRequest) -> {
          final ArgumentCaptor<UInt64> startSlotArgumentCaptor =
              ArgumentCaptor.forClass(UInt64.class);
          final ArgumentCaptor<UInt64> countArgumentCaptor = ArgumentCaptor.forClass(UInt64.class);
          verify(peer, atLeastOnce())
              .requestBlocksByRange(
                  startSlotArgumentCaptor.capture(),
                  countArgumentCaptor.capture(),
                  blockResponseListenerArgumentCaptor.capture());
          verify(peer, atLeastOnce())
              .requestBlobsSidecarsByRange(
                  any(), any(), blobsSidecarResponseListenerArgumentCaptor.capture());
          // only responding with block and sidecars for the last slot of the request
          final UInt64 slotToRespond =
              startSlotArgumentCaptor.getValue().plus(countArgumentCaptor.getValue()).minus(1);
          final RpcResponseListener<SignedBeaconBlock> blockListener =
              blockResponseListenerArgumentCaptor.getValue();
          final RpcResponseListener<BlobsSidecar> blobsSidecarListener =
              blobsSidecarResponseListenerArgumentCaptor.getValue();
          final List<SignedBeaconBlock> blocks =
              respondWithBlocksAtSlots(blockListener, slotToRespond);
          final List<BlobsSidecar> blobsSidecars =
              respondWithBlobsSidecarsAtSlots(blobsSidecarListener, slotToRespond);
          blockRequest.complete(null);
          blobsSidecarRequest.complete(null);
          asyncRunner.executeQueuedActions();
          blocks.forEach(block -> verify(blockImporter).importBlock(block));
          blobsSidecars.forEach(
              sidecar -> verify(blobsSidecarManager).storeUnconfirmedBlobsSidecar(sidecar));
        });
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
}
