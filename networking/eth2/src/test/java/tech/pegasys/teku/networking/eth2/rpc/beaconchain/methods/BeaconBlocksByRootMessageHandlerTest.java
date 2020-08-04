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

package tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.core.ChainBuilder;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.BeaconBlocksByRootRequestMessage;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseCallback;
import tech.pegasys.teku.networking.p2p.rpc.StreamClosedException;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.store.UpdatableStore;

public class BeaconBlocksByRootMessageHandlerTest {
  private final ChainBuilder chainBuilder = ChainBuilder.createDefault();
  final UpdatableStore store = mock(UpdatableStore.class);
  final RecentChainData recentChainData = mock(RecentChainData.class);
  final BeaconBlocksByRootMessageHandler handler =
      new BeaconBlocksByRootMessageHandler(recentChainData);
  final Eth2Peer peer = mock(Eth2Peer.class);

  @SuppressWarnings("unchecked")
  final ResponseCallback<SignedBeaconBlock> callback = mock(ResponseCallback.class);

  @BeforeEach
  public void setup() {
    when(peer.wantToMakeRequest()).thenReturn(true);
    when(peer.wantToReceiveObjects(any(), anyLong())).thenReturn(true);
    when(recentChainData.getStore()).thenReturn(store);
  }

  @Test
  public void onIncomingMessage_respondsWithAllBlocks() {
    final List<SignedBeaconBlock> blocks = mockChain(5);

    final BeaconBlocksByRootRequestMessage message = createRequest(blocks);
    handler.onIncomingMessage(peer, message, callback);

    for (SignedBeaconBlock block : blocks) {
      verify(store).retrieveSignedBlock(block.getRoot());
      verify(callback).respond(block);
    }
  }

  @Test
  public void onIncomingMessage_interruptedByClosedStream() {
    final List<SignedBeaconBlock> blocks = mockChain(5);

    // Mock callback to appear to be closed
    doThrow(new StreamClosedException()).when(callback).respond(any());

    final BeaconBlocksByRootRequestMessage message = createRequest(blocks);
    handler.onIncomingMessage(peer, message, callback);

    // Check that we only asked for the first block
    verify(store, times(1)).retrieveSignedBlock(any());
    verify(callback, times(1)).respond(any());
  }

  private BeaconBlocksByRootRequestMessage createRequest(final List<SignedBeaconBlock> forBlocks) {
    final List<Bytes32> blockHashes =
        forBlocks.stream().map(SignedBeaconBlock::getRoot).collect(Collectors.toList());
    return new BeaconBlocksByRootRequestMessage(blockHashes);
  }

  private List<SignedBeaconBlock> mockChain(final int chainSize) {
    // Create some blocks to request
    chainBuilder.generateGenesis();
    final List<SignedBeaconBlock> blocks =
        chainBuilder.generateBlocksUpToSlot(chainSize).stream()
            .map(SignedBlockAndState::getBlock)
            .collect(Collectors.toList());

    // Setup store to return blocks
    for (SignedBeaconBlock block : blocks) {
      when(store.retrieveSignedBlock(block.getRoot()))
          .thenReturn(SafeFuture.completedFuture(Optional.of(block)));
    }

    return blocks;
  }
}
