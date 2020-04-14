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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
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
import tech.pegasys.artemis.networking.eth2.rpc.core.ResponseStream.ResponseListener;
import tech.pegasys.artemis.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.artemis.networking.p2p.mock.MockNodeId;
import tech.pegasys.artemis.networking.p2p.peer.NodeId;
import tech.pegasys.artemis.networking.p2p.rpc.RpcStream;
import tech.pegasys.artemis.storage.client.CombinedChainDataClient;
import tech.pegasys.artemis.storage.client.RecentChainData;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.async.StubAsyncRunner;

public class Eth2OutgoingRequestHandlerTest {

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final StubAsyncRunner asyncRequestRunner = new StubAsyncRunner();
  private final StubAsyncRunner timeoutRunner = new StubAsyncRunner();

  private final PeerLookup peerLookup = mock(PeerLookup.class);
  private final CombinedChainDataClient combinedChainDataClient =
      mock(CombinedChainDataClient.class);
  private final RecentChainData recentChainData = mock(RecentChainData.class);

  private final BeaconChainMethods beaconChainMethods =
      BeaconChainMethods.create(
          timeoutRunner,
          peerLookup,
          combinedChainDataClient,
          recentChainData,
          new NoOpMetricsSystem(),
          new StatusMessageFactory(recentChainData),
          RpcEncoding.SSZ);

  private final Eth2RpcMethod<BeaconBlocksByRangeRequestMessage, SignedBeaconBlock>
      blocksByRangeMethod = beaconChainMethods.beaconBlocksByRange();
  private final RpcEncoder rpcEncoder = blocksByRangeMethod.getRpcEncoder();
  private final List<Bytes> chunks = List.of(chunkBytes(0), chunkBytes(1), chunkBytes(2));

  private NodeId nodeId = new MockNodeId();

  private final int maxChunks = chunks.size();
  private final Eth2OutgoingRequestHandler<BeaconBlocksByRangeRequestMessage, SignedBeaconBlock>
      reqHandler =
          new Eth2OutgoingRequestHandler<>(
              asyncRequestRunner, timeoutRunner, blocksByRangeMethod, maxChunks);
  private final RpcStream rpcStream = mock(RpcStream.class);
  private final List<SignedBeaconBlock> blocks = new ArrayList<>();
  private SafeFuture<Void> finishedProcessingFuture;
  private final AtomicReference<ResponseListener<SignedBeaconBlock>> responseListener =
      new AtomicReference<>(blocks::add);

  @BeforeEach
  public void setup() {
    lenient().when(rpcStream.close()).thenReturn(SafeFuture.COMPLETE);
    lenient().when(rpcStream.closeWriteStream()).thenReturn(SafeFuture.COMPLETE);
    finishedProcessingFuture =
        reqHandler
            .getResponseStream()
            .expectMultipleResponses(b -> responseListener.get().onResponse(b));
  }

  @Test
  public void processAllExpectedChunks() {
    sendInitialPayload();
    verify(rpcStream).closeWriteStream();

    for (int i = 0; i < maxChunks; i++) {
      deliverChunk(i);
      assertThat(finishedProcessingFuture).isNotDone();
    }

    asyncRequestRunner.executeRepeatedly(maxChunks - 1);
    assertThat(finishedProcessingFuture).isNotDone();

    asyncRequestRunner.executeUntilDone();
    timeoutRunner.executeUntilDone();
    verify(rpcStream).close();
    assertThat(blocks.size()).isEqualTo(3);
    assertThat(finishedProcessingFuture).isCompletedWithValue(null);
  }

  @Test
  public void receiveAllChunksThenEncounterErrorWhileProcessing() {
    sendInitialPayload();
    verify(rpcStream).closeWriteStream();

    for (int i = 0; i < maxChunks; i++) {
      deliverChunk(i);
      assertThat(finishedProcessingFuture).isNotDone();
    }

    asyncRequestRunner.executeRepeatedly(maxChunks - 1);
    assertThat(finishedProcessingFuture).isNotDone();

    // Fail to process the next block
    final RuntimeException error = new RuntimeException("whoops");
    responseListener.set(
        b -> {
          throw error;
        });

    asyncRequestRunner.executeUntilDone();
    timeoutRunner.executeUntilDone();
    verify(rpcStream, atLeastOnce()).close();
    assertThat(blocks.size()).isEqualTo(maxChunks - 1);
    assertThat(finishedProcessingFuture).isCompletedExceptionally();
    assertThatThrownBy(finishedProcessingFuture::get).hasRootCause(error);
  }

  @Test
  public void processAllValidChunksWhenErrorIsEncountered() {
    sendInitialPayload();
    verify(rpcStream).closeWriteStream();

    deliverChunk(0);
    assertThat(finishedProcessingFuture).isNotDone();
    deliverInvalidChunk();

    asyncRequestRunner.executeUntilDone();
    verify(rpcStream).close();
    assertThat(blocks.size()).isEqualTo(1);
    assertThat(finishedProcessingFuture).isCompletedExceptionally();
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

    asyncRequestRunner.executeUntilDone();
    timeoutRunner.executeUntilDone();
    verify(rpcStream).close();
    // TODO - we should limit the number of chunks to be parsed based on maxChunks
    assertThat(blocks.size()).isEqualTo(4);
    assertThat(finishedProcessingFuture).isCompletedWithValue(null);
  }

  @Test
  public void disconnectsIfInitialBytesAreNotReceivedInTime() {
    sendInitialPayload();
    verify(rpcStream).closeWriteStream();
    verify(rpcStream, never()).close();

    // Run async tasks
    assertThat(timeoutRunner.countDelayedActions()).isEqualTo(1);
    timeoutRunner.executeQueuedActions();
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
    assertThat(timeoutRunner.countDelayedActions()).isEqualTo(2);
    timeoutRunner.executeQueuedActions(1);
    verify(rpcStream, never()).close();
  }

  @Test
  public void disconnectsIfFirstChunkIsNotReceivedInTime() {
    sendInitialPayload();

    deliverInitialBytes();

    // Run timeouts
    timeoutRunner.executeQueuedActions();
    verify(rpcStream).close();
  }

  @Test
  public void doNotDisconnectsIfFirstChunkReceivedInTime() {
    sendInitialPayload();

    deliverChunk(0);

    // Run timeouts
    assertThat(timeoutRunner.countDelayedActions()).isEqualTo(3);
    timeoutRunner.executeQueuedActions(2);
    verify(rpcStream, never()).close();
  }

  @Test
  public void disconnectsIfSecondChunkNotReceivedInTime() {
    sendInitialPayload();

    deliverChunk(0);

    // Run timeouts
    assertThat(timeoutRunner.countDelayedActions()).isEqualTo(3);
    asyncRequestRunner.executeQueuedActions();
    timeoutRunner.executeQueuedActions();
    assertThat(blocks.size()).isEqualTo(1);
    verify(rpcStream).close();
  }

  @Test
  public void doNotDisconnectsIfSecondChunkReceivedInTime() {
    sendInitialPayload();

    deliverChunk(0);
    deliverChunk(1);

    // Run timeouts
    assertThat(timeoutRunner.countDelayedActions()).isEqualTo(4);
    asyncRequestRunner.executeUntilDone();
    timeoutRunner.executeQueuedActions(3);
    assertThat(blocks.size()).isEqualTo(2);
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
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(chunk);
    return rpcEncoder.encodeSuccessfulResponse(block);
  }

  private void deliverInvalidChunk() {
    // Send a chunk with error code 1, message length 0
    final Bytes invalidChunk = Bytes.fromHexString("0x0100");
    final ByteBuf chunkBuffer = Unpooled.wrappedBuffer(invalidChunk.toArrayUnsafe());
    reqHandler.onData(nodeId, rpcStream, chunkBuffer);
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
