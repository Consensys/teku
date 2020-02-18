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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.BeaconBlocksByRangeRequestMessage;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.networking.eth2.peers.PeerLookup;
import tech.pegasys.artemis.networking.eth2.rpc.beaconchain.BeaconChainMethods;
import tech.pegasys.artemis.networking.eth2.rpc.beaconchain.methods.StatusMessageFactory;
import tech.pegasys.artemis.networking.p2p.mock.MockNodeId;
import tech.pegasys.artemis.networking.p2p.peer.NodeId;
import tech.pegasys.artemis.networking.p2p.rpc.RpcStream;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.CombinedChainDataClient;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.async.StubAsyncRunner;

public class Eth2OutgoingRequestHandlerTest {

  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private final PeerLookup peerLookup = mock(PeerLookup.class);
  private final CombinedChainDataClient combinedChainDataClient =
      mock(CombinedChainDataClient.class);
  private final ChainStorageClient chainStorageClient = mock(ChainStorageClient.class);

  private final BeaconChainMethods beaconChainMethods =
      BeaconChainMethods.create(
          asyncRunner,
          peerLookup,
          combinedChainDataClient,
          chainStorageClient,
          new NoOpMetricsSystem(),
          new StatusMessageFactory(chainStorageClient));

  private final Eth2RpcMethod<BeaconBlocksByRangeRequestMessage, SignedBeaconBlock>
      blocksByRangeMethod = beaconChainMethods.beaconBlocksByRange();
  private final RpcEncoder rpcEncoder = blocksByRangeMethod.getRpcEncoder();
  private final List<Bytes> chunks = List.of(chunkBytes(0), chunkBytes(1), chunkBytes(2));

  private NodeId nodeId = new MockNodeId();

  private final int maxChunks = chunks.size();
  private final Eth2OutgoingRequestHandler<BeaconBlocksByRangeRequestMessage, SignedBeaconBlock>
      reqHandler = blocksByRangeMethod.createOutgoingRequestHandler(maxChunks);
  private final RpcStream rpcStream = mock(RpcStream.class);
  private final List<SignedBeaconBlock> blocks = new ArrayList<>();

  @BeforeEach
  public void setup() {
    lenient().when(rpcStream.close()).thenReturn(SafeFuture.COMPLETE);
    lenient().when(rpcStream.closeWriteStream()).thenReturn(SafeFuture.COMPLETE);
    reqHandler.getResponseStream().expectMultipleResponses(blocks::add).reportExceptions();
  }

  @Test
  public void processAllExpectedChunks() {
    sendInitialPayload();
    verify(rpcStream).closeWriteStream();

    for (int i = 0; i < maxChunks; i++) {
      deliverChunk(i);
    }

    asyncRunner.executeQueuedActions();
    verify(rpcStream).close();
    assertThat(blocks.size()).isEqualTo(3);
  }

  @Test
  public void processTooManyChunksOnFinalDataDelivery() {
    sendInitialPayload();
    verify(rpcStream).closeWriteStream();

    for (int i = 0; i < maxChunks; i++) {
      if (i == maxChunks - 1) {
        deliverChunks(i, i);
      } else {
        deliverChunk(i);
      }
    }

    asyncRunner.executeQueuedActions();
    verify(rpcStream).close();
  }

  @Test
  public void disconnectsIfInitialBytesAreNotReceivedInTime() {
    sendInitialPayload();
    verify(rpcStream).closeWriteStream();
    verify(rpcStream, never()).close();

    // Run async tasks
    assertThat(asyncRunner.countDelayedActions()).isEqualTo(1);
    asyncRunner.executeQueuedActions();
    verify(rpcStream).close();
  }

  @Test
  public void doesNotDisconnectIfInitialBytesAreReceivedInTime() {
    sendInitialPayload();
    verify(rpcStream).closeWriteStream();
    verify(rpcStream, never()).close();

    // Deliver some bytes
    deliverInitialBytes();

    // Run async tasks
    assertThat(asyncRunner.countDelayedActions()).isEqualTo(2);
    asyncRunner.executeQueuedActions(1);
    verify(rpcStream, never()).close();
  }

  @Test
  public void disconnectsIfFirstChunkIsNotReceivedInTime() {
    sendInitialPayload();

    deliverInitialBytes();

    // Run timeouts
    asyncRunner.executeQueuedActions();
    verify(rpcStream).close();
  }

  @Test
  public void doNotDisconnectsIfFirstChunkReceivedInTime() {
    sendInitialPayload();

    deliverChunk(0);

    // Run timeouts
    assertThat(asyncRunner.countDelayedActions()).isEqualTo(3);
    asyncRunner.executeQueuedActions(2);
    verify(rpcStream, never()).close();
  }

  @Test
  public void disconnectsIfSecondChunkNotReceivedInTime() {
    sendInitialPayload();

    deliverChunk(0);

    // Run timeouts
    assertThat(asyncRunner.countDelayedActions()).isEqualTo(3);
    asyncRunner.executeQueuedActions();
    verify(rpcStream).close();
  }

  @Test
  public void doNotDisconnectsIfSecondChunkReceivedInTime() {
    sendInitialPayload();

    deliverChunk(0);
    deliverChunk(1);

    // Run timeouts
    assertThat(asyncRunner.countDelayedActions()).isEqualTo(4);
    asyncRunner.executeQueuedActions(3);
    verify(rpcStream, never()).close();
  }

  private void sendInitialPayload() {
    reqHandler.handleInitialPayloadSent(rpcStream);
  }

  private void deliverInitialBytes() {
    final Bytes firstByte = chunks.get(0).slice(0, 1);
    final ByteBuf initialBytes = Unpooled.wrappedBuffer(firstByte.toArrayUnsafe());
    reqHandler.onData(nodeId, rpcStream, initialBytes);
  }

  private Bytes chunkBytes(final int chunk) {
    final SignedBeaconBlock block = DataStructureUtil.randomSignedBeaconBlock(chunk, chunk);
    return rpcEncoder.encodeSuccessfulResponse(block);
  }

  private void deliverChunk(final int chunk) {
    final Bytes chunkBytes = chunks.get(chunk);
    final ByteBuf chunkBuffer = Unpooled.wrappedBuffer(chunkBytes.toArrayUnsafe());
    reqHandler.onData(nodeId, rpcStream, chunkBuffer);
  }

  private void deliverChunks(final int... chunks) {
    Bytes chunkBytes = Bytes.wrap(new byte[0]);
    for (int chunk : chunks) {
      final Bytes currentBytes = this.chunks.get(chunk);
      chunkBytes = Bytes.concatenate(chunkBytes, currentBytes);
    }
    final ByteBuf chunkBuffer = Unpooled.wrappedBuffer(chunkBytes.toArrayUnsafe());
    reqHandler.onData(nodeId, rpcStream, chunkBuffer);
  }
}
