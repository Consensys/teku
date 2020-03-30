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

package tech.pegasys.artemis.networking.eth2.rpc.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.google.common.primitives.UnsignedLong;
import io.netty.buffer.Unpooled;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.BeaconBlocksByRangeRequestMessage;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.networking.eth2.peers.Eth2Peer;
import tech.pegasys.artemis.networking.eth2.peers.PeerLookup;
import tech.pegasys.artemis.networking.eth2.rpc.beaconchain.BeaconChainMethods;
import tech.pegasys.artemis.networking.eth2.rpc.beaconchain.methods.StatusMessageFactory;
import tech.pegasys.artemis.networking.p2p.mock.MockNodeId;
import tech.pegasys.artemis.networking.p2p.peer.NodeId;
import tech.pegasys.artemis.networking.p2p.rpc.RpcStream;
import tech.pegasys.artemis.storage.CombinedChainDataClient;
import tech.pegasys.artemis.storage.RecentChainData;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.async.StubAsyncRunner;

public class Eth2IncomingRequestHandlerTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private final PeerLookup peerLookup = mock(PeerLookup.class);
  private final CombinedChainDataClient combinedChainDataClient =
      mock(CombinedChainDataClient.class);
  private final RecentChainData recentChainData = mock(RecentChainData.class);

  private final BeaconChainMethods beaconChainMethods =
      BeaconChainMethods.create(
          asyncRunner,
          peerLookup,
          combinedChainDataClient,
          recentChainData,
          new NoOpMetricsSystem(),
          new StatusMessageFactory(recentChainData));

  private final Eth2RpcMethod<BeaconBlocksByRangeRequestMessage, SignedBeaconBlock>
      blocksByRangeMethod = beaconChainMethods.beaconBlocksByRange();

  private final Eth2IncomingRequestHandler<BeaconBlocksByRangeRequestMessage, SignedBeaconBlock>
      reqHandler = blocksByRangeMethod.createIncomingRequestHandler();
  private final RpcStream rpcStream = mock(RpcStream.class);

  private final NodeId nodeId = new MockNodeId();
  private final Eth2Peer peer = mock(Eth2Peer.class);
  private final BeaconState state = mock(BeaconState.class);
  private final BeaconBlocksByRangeRequestMessage request =
      new BeaconBlocksByRangeRequestMessage(
          dataStructureUtil.randomBytes32(), UnsignedLong.ONE, UnsignedLong.ONE, UnsignedLong.ONE);
  private final Bytes requestData = blocksByRangeMethod.encodeRequest(request);

  @BeforeEach
  public void setup() {
    lenient().when(rpcStream.close()).thenReturn(SafeFuture.COMPLETE);
    lenient().when(rpcStream.closeWriteStream()).thenReturn(SafeFuture.COMPLETE);
    lenient().when(peerLookup.getConnectedPeer(nodeId)).thenReturn(peer);

    lenient().when(state.getSlot()).thenReturn(UnsignedLong.ONE);
    lenient()
        .when(combinedChainDataClient.getNonfinalizedBlockState(any()))
        .thenReturn(Optional.of(state));
    lenient()
        .when(combinedChainDataClient.getBlockAtSlotExact(any(), any()))
        .thenAnswer(i -> getBlockAtSlot(i.getArgument(0)));
  }

  private SafeFuture<Optional<SignedBeaconBlock>> getBlockAtSlot(final UnsignedLong slot) {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(slot.longValue());
    return SafeFuture.completedFuture(Optional.of(block));
  }

  @Test
  public void shouldCloseStreamIfRequestNotReceivedInTime() {
    reqHandler.onActivation(rpcStream);
    verify(rpcStream, never()).close();
    verify(rpcStream, never()).closeWriteStream();

    // When timeout completes, we should close stream
    assertThat(asyncRunner.countDelayedActions()).isEqualTo(1);
    asyncRunner.executeQueuedActions();

    verify(rpcStream).close();
    verify(rpcStream, never()).closeWriteStream();
  }

  @Test
  public void shouldNotCloseStreamIfRequestReceivedInTime() {
    reqHandler.onActivation(rpcStream);
    verify(rpcStream, never()).close();
    verify(rpcStream, never()).closeWriteStream();

    reqHandler.onData(nodeId, rpcStream, Unpooled.wrappedBuffer(requestData.toArrayUnsafe()));

    // When timeout completes, we should not close stream
    assertThat(asyncRunner.countDelayedActions()).isEqualTo(1);
    asyncRunner.executeQueuedActions();
    asyncRunner.executeQueuedActions();

    verify(rpcStream, never()).close();
    verify(rpcStream, never()).closeWriteStream();
  }
}
