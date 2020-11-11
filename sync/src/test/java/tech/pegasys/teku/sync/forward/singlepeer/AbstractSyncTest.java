/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.sync.forward.singlepeer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.StatusMessage;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.peers.PeerStatus;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseStreamListener;
import tech.pegasys.teku.statetransition.block.BlockImporter;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.util.config.Constants;

public abstract class AbstractSyncTest {
  protected final Eth2Peer peer = mock(Eth2Peer.class);
  protected final BlockImporter blockImporter = mock(BlockImporter.class);
  protected final RecentChainData storageClient = mock(RecentChainData.class);

  protected final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  protected final StubAsyncRunner asyncRunner = new StubAsyncRunner();

  @SuppressWarnings("unchecked")
  protected final ArgumentCaptor<ResponseStreamListener<SignedBeaconBlock>>
      responseListenerArgumentCaptor = ArgumentCaptor.forClass(ResponseStreamListener.class);

  protected void completeRequestWithBlockAtSlot(
      final SafeFuture<Void> requestFuture1, final int lastBlockSlot) {
    completeRequestWithBlockAtSlot(requestFuture1, UInt64.valueOf(lastBlockSlot));
  }

  protected void completeRequestWithBlockAtSlot(
      final SafeFuture<Void> requestFuture1, final UInt64 lastBlockSlot) {
    // Capture latest response listener
    verify(peer, atLeastOnce())
        .requestBlocksByRange(any(), any(), any(), responseListenerArgumentCaptor.capture());
    final ResponseStreamListener<SignedBeaconBlock> responseListener =
        responseListenerArgumentCaptor.getValue();

    List<SignedBeaconBlock> blocks = respondWithBlocksAtSlots(responseListener, lastBlockSlot);
    for (SignedBeaconBlock block : blocks) {
      verify(blockImporter).importBlock(block);
    }
    requestFuture1.complete(null);
    asyncRunner.executeQueuedActions();
  }

  protected List<SignedBeaconBlock> respondWithBlocksAtSlots(
      final ResponseStreamListener<SignedBeaconBlock> responseListener, UInt64... slots) {
    List<SignedBeaconBlock> blocks = new ArrayList<>();
    for (UInt64 slot : slots) {
      final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(slot);
      blocks.add(block);
      responseListener.onResponse(block).join();
    }
    return blocks;
  }

  protected PeerStatus withPeerHeadSlot(
      final UInt64 peerHeadSlot, final UInt64 peerFinalizedEpoch, final Bytes32 peerHeadBlockRoot) {
    final PeerStatus peer_status =
        PeerStatus.fromStatusMessage(
            new StatusMessage(
                Constants.GENESIS_FORK_VERSION,
                Bytes32.ZERO,
                peerFinalizedEpoch,
                peerHeadBlockRoot,
                peerHeadSlot));

    when(peer.getStatus()).thenReturn(peer_status);
    return peer_status;
  }
}
