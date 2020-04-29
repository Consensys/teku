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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.BeaconBlocksByRangeRequestMessage;
import tech.pegasys.artemis.networking.eth2.rpc.core.ResponseStream.ResponseListener;
import tech.pegasys.artemis.util.Waiter;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.async.StubAsyncRunner;

public class Eth2OutgoingRequestHandlerTest
    extends AbstractRequestHandlerTest<
        Eth2OutgoingRequestHandler<BeaconBlocksByRangeRequestMessage, SignedBeaconBlock>> {

  private final StubAsyncRunner asyncRequestRunner = new StubAsyncRunner();
  private final StubAsyncRunner timeoutRunner = new StubAsyncRunner();

  private final Eth2RpcMethod<BeaconBlocksByRangeRequestMessage, SignedBeaconBlock>
      blocksByRangeMethod = beaconChainMethods.beaconBlocksByRange();
  private final RpcEncoder rpcEncoder = blocksByRangeMethod.getRpcEncoder();
  private final List<Bytes> chunks = List.of(chunkBytes(0), chunkBytes(1), chunkBytes(2));

  private final int maxChunks = chunks.size();

  private final List<SignedBeaconBlock> blocks = new ArrayList<>();
  private SafeFuture<Void> finishedProcessingFuture;
  private final AtomicReference<ResponseListener<SignedBeaconBlock>> responseListener =
      new AtomicReference<>(blocks::add);

  @BeforeEach
  @Override
  public void setup() {
    super.setup();
    finishedProcessingFuture =
        reqHandler
            .getResponseStream()
            .expectMultipleResponses(b -> responseListener.get().onResponse(b));
  }

  @Override
  protected Eth2OutgoingRequestHandler<BeaconBlocksByRangeRequestMessage, SignedBeaconBlock>
      createRequestHandler() {
    return new Eth2OutgoingRequestHandler<>(
        asyncRequestRunner, timeoutRunner, blocksByRangeMethod, maxChunks);
  }

  @Test
  public void processAllExpectedChunks() throws Exception {
    sendInitialPayload();
    verify(rpcStream).closeWriteStream();

    for (int i = 0; i < maxChunks; i++) {
      deliverChunk(i);
      assertThat(finishedProcessingFuture).isNotDone();
    }
    inputStream.close();

    asyncRequestRunner.waitForExactly(maxChunks - 1);
    assertThat(finishedProcessingFuture).isNotDone();

    asyncRequestRunner.waitForExactly(1);
    timeoutRunner.executeUntilDone();
    verify(rpcStream).close();
    assertThat(blocks.size()).isEqualTo(3);
    assertThat(finishedProcessingFuture).isCompletedWithValue(null);
  }

  @Test
  public void receiveAllChunksThenEncounterErrorWhileProcessing() throws Exception {
    sendInitialPayload();
    verify(rpcStream).closeWriteStream();

    for (int i = 0; i < maxChunks; i++) {
      deliverChunk(i);
      assertThat(finishedProcessingFuture).isNotDone();
    }
    inputStream.close();

    asyncRequestRunner.waitForExactly(maxChunks - 1);
    assertThat(finishedProcessingFuture).isNotDone();

    // Fail to process the next block
    final RuntimeException error = new RuntimeException("whoops");
    responseListener.set(
        b -> {
          throw error;
        });

    asyncRequestRunner.waitForExactly(1);
    timeoutRunner.executeUntilDone();
    verify(rpcStream, atLeastOnce()).close();
    assertThat(blocks.size()).isEqualTo(maxChunks - 1);
    assertThat(finishedProcessingFuture).isCompletedExceptionally();
    assertThatThrownBy(finishedProcessingFuture::get).hasRootCause(error);
  }

  @Test
  public void processAllValidChunksWhenErrorIsEncountered() throws Exception {
    sendInitialPayload();
    verify(rpcStream).closeWriteStream();

    deliverChunk(0);
    assertThat(finishedProcessingFuture).isNotDone();
    deliverInvalidChunk();
    inputStream.close();

    asyncRequestRunner.waitForExactly(1);
    Waiter.waitFor(() -> assertThat(finishedProcessingFuture).isDone());
    verify(rpcStream).close();
    assertThat(blocks.size()).isEqualTo(1);
    assertThat(finishedProcessingFuture).isCompletedExceptionally();
  }

  @Test
  public void sendTooManyChunks() throws Exception {
    sendInitialPayload();
    verify(rpcStream).closeWriteStream();

    for (int i = 0; i < maxChunks; i++) {
      if (i == maxChunks - 1) {
        // Send 2 chunks in the last batch of data which should only contain 1 chunk
        final Bytes lastChunk = chunks.get(i);
        final Bytes lastChunkWithExtraChunk = Bytes.concatenate(lastChunk, lastChunk);
        deliverBytes(lastChunkWithExtraChunk, lastChunk.size());
      } else {
        deliverChunk(i);
      }
    }
    inputStream.close();

    asyncRequestRunner.waitForExactly(maxChunks);
    timeoutRunner.executeUntilDone();
    verify(rpcStream).close();
    assertThat(blocks.size()).isEqualTo(3);
    assertThat(finishedProcessingFuture).isCompletedExceptionally();
    assertThatThrownBy(finishedProcessingFuture::get)
        .hasRootCause(RpcException.EXTRA_DATA_APPENDED);
  }

  @Test
  public void disconnectsIfInitialBytesAreNotReceivedInTime() throws Exception {
    sendInitialPayload();
    verify(rpcStream).closeWriteStream();
    verify(rpcStream, never()).close();

    // Run async tasks
    assertThat(timeoutRunner.countDelayedActions()).isEqualTo(1);
    timeoutRunner.executeQueuedActions();
    verify(rpcStream).close();
  }

  @Test
  public void doesNotDisconnectIfInitialBytesAreReceivedInTime() throws Exception {
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
  public void disconnectsIfFirstChunkIsNotReceivedInTime() throws Exception {
    sendInitialPayload();

    deliverInitialBytes();

    // Run timeouts
    timeoutRunner.executeQueuedActions();
    verify(rpcStream).close();
  }

  @Test
  public void doNotDisconnectsIfFirstChunkReceivedInTime() throws Exception {
    sendInitialPayload();

    deliverChunk(0);

    // Run timeouts
    assertThat(timeoutRunner.countDelayedActions()).isEqualTo(3);
    timeoutRunner.executeQueuedActions(2);
    verify(rpcStream, never()).close();
  }

  @Test
  public void disconnectsIfSecondChunkNotReceivedInTime() throws Exception {
    sendInitialPayload();

    deliverChunk(0);

    // Run timeouts
    assertThat(timeoutRunner.countDelayedActions()).isEqualTo(3);
    asyncRequestRunner.waitForExactly(1);
    timeoutRunner.executeQueuedActions();
    assertThat(blocks.size()).isEqualTo(1);
    verify(rpcStream).close();
  }

  @Test
  public void doNotDisconnectsIfSecondChunkReceivedInTime() throws Exception {
    sendInitialPayload();

    deliverChunk(0);
    deliverChunk(1);

    // Run timeouts
    assertThat(timeoutRunner.countDelayedActions()).isEqualTo(4);
    asyncRequestRunner.waitForExactly(2);
    timeoutRunner.executeQueuedActions(3);
    assertThat(blocks.size()).isEqualTo(2);
    verify(rpcStream, never()).close();
  }

  private void sendInitialPayload() {
    reqHandler.handleInitialPayloadSent(rpcStream);
  }

  private void deliverInitialBytes() throws IOException {
    final Bytes firstByte = chunks.get(0).slice(0, 1);
    deliverBytes(firstByte);
  }

  private Bytes chunkBytes(final int chunk) {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(chunk);
    return rpcEncoder.encodeSuccessfulResponse(block);
  }

  private void deliverInvalidChunk() throws IOException {
    // Send a chunk with error code 1, message length 0
    final Bytes invalidChunk = Bytes.fromHexString("0x0100");
    deliverBytes(invalidChunk);
  }

  private void deliverChunk(final int chunk) throws IOException {
    final Bytes chunkBytes = chunks.get(chunk);
    deliverBytes(chunkBytes);
    if (chunk < maxChunks - 1) {
      // Make sure we finish processing this chunk, and loop back around to wait on the next
      Waiter.waitFor(() -> assertThat(inputStream.isWaitingOnNextByteToBeDelivered()).isTrue());
    }
  }
}
