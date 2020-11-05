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

package tech.pegasys.teku.networking.eth2.rpc.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.BeaconBlocksByRangeRequestMessage;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.async.Waiter;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.BeaconChainMethods;
import tech.pegasys.teku.networking.eth2.rpc.core.Eth2OutgoingRequestHandler.State;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.ExtraDataAppendedException;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.ServerErrorException;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;

public class Eth2OutgoingRequestHandlerTest
    extends AbstractRequestHandlerTest<
        Eth2OutgoingRequestHandler<BeaconBlocksByRangeRequestMessage, SignedBeaconBlock>> {

  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(0);
  private final StubAsyncRunner asyncRequestRunner = new StubAsyncRunner(timeProvider);
  private final StubAsyncRunner timeoutRunner = new StubAsyncRunner(timeProvider);

  private final List<SignedBeaconBlock> blocks = new ArrayList<>();
  private final AtomicReference<ResponseStreamListener<SignedBeaconBlock>> responseListener =
      new AtomicReference<>(ResponseStreamListener.from(blocks::add));
  private final int maxChunks = 3;

  private RpcEncoder rpcEncoder;
  private List<Bytes> chunks;
  private SafeFuture<Void> finishedProcessingFuture;

  @BeforeEach
  @Override
  public void setup() {
    super.setup();
    rpcEncoder = beaconChainMethods.beaconBlocksByRange().getRpcEncoder();
    chunks = IntStream.range(0, maxChunks).mapToObj(this::chunkBytes).collect(Collectors.toList());

    finishedProcessingFuture =
        reqHandler
            .getResponseStream()
            .expectMultipleResponses(b -> responseListener.get().onResponse(b));
  }

  @Override
  protected Eth2OutgoingRequestHandler<BeaconBlocksByRangeRequestMessage, SignedBeaconBlock>
      createRequestHandler(final BeaconChainMethods beaconChainMethods) {
    return new Eth2OutgoingRequestHandler<>(
        asyncRequestRunner, timeoutRunner, beaconChainMethods.beaconBlocksByRange(), maxChunks);
  }

  @Override
  protected RpcEncoding getRpcEncoding() {
    return RpcEncoding.SSZ_SNAPPY;
  }

  @Test
  public void processAllExpectedChunks() throws Exception {
    sendInitialPayload();
    verify(rpcStream).closeWriteStream();

    for (int i = 0; i < maxChunks; i++) {
      deliverChunk(i);
      assertThat(finishedProcessingFuture).isNotDone();
    }
    complete();
    close();

    asyncRequestRunner.waitForExactly(maxChunks - 1);
    assertThat(finishedProcessingFuture).isNotDone();

    asyncRequestRunner.waitForExactly(1);
    timeoutRunner.executeUntilDone();
    Waiter.waitFor(() -> assertThat(finishedProcessingFuture).isDone());

    assertThat(finishedProcessingFuture).isCompletedWithValue(null);
    assertThat(reqHandler.getState()).isIn(State.CLOSED, State.READ_COMPLETE);
    assertThat(blocks.size()).isEqualTo(3);
    verify(rpcStream, never()).closeAbruptly();
  }

  @Test
  public void receiveAllChunksThenEncounterErrorWhileProcessing() throws Exception {
    sendInitialPayload();
    verify(rpcStream).closeWriteStream();

    for (int i = 0; i < maxChunks; i++) {
      deliverChunk(i);
      assertThat(finishedProcessingFuture).isNotDone();
    }
    complete();
    close();

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
    Waiter.waitFor(() -> assertThat(finishedProcessingFuture).isDone());

    assertThat(finishedProcessingFuture).isCompletedExceptionally();
    verify(rpcStream, atLeastOnce()).closeAbruptly();
    assertThat(blocks.size()).isEqualTo(maxChunks - 1);
    assertThatThrownBy(finishedProcessingFuture::get).hasRootCause(error);
  }

  @Test
  public void processAllValidChunksWhenErrorIsEncountered() throws Exception {
    sendInitialPayload();
    verify(rpcStream).closeWriteStream();

    deliverChunk(0);
    assertThat(finishedProcessingFuture).isNotDone();
    deliverError();
    complete();
    close();

    asyncRequestRunner.waitForExactly(1);
    Waiter.waitFor(() -> assertThat(finishedProcessingFuture).isDone());

    verify(rpcStream).closeAbruptly();
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
        deliverBytes(lastChunkWithExtraChunk);
      } else {
        deliverChunk(i);
      }
    }
    complete();
    close();

    asyncRequestRunner.waitForExactly(maxChunks - 1);
    timeoutRunner.executeUntilDone();
    Waiter.waitFor(() -> assertThat(finishedProcessingFuture).isDone());

    verify(rpcStream).closeAbruptly();
    assertThat(blocks.size()).isEqualTo(2);
    assertThat(finishedProcessingFuture).isCompletedExceptionally();
    assertThatThrownBy(finishedProcessingFuture::get)
        .hasRootCause(new ExtraDataAppendedException());
  }

  @Test
  public void disconnectsIfInitialBytesAreNotReceivedInTime() {
    sendInitialPayload();
    verify(rpcStream).closeWriteStream();
    verify(rpcStream, never()).closeAbruptly();

    // Run async tasks
    timeProvider.advanceTimeByMillis(RpcTimeouts.TTFB_TIMEOUT.toMillis());
    timeoutRunner.executeDueActions();
    verify(rpcStream).closeAbruptly();
  }

  @Test
  public void doesNotDisconnectIfInitialBytesAreReceivedInTime() throws Exception {
    sendInitialPayload();
    verify(rpcStream).closeWriteStream();
    verify(rpcStream, never()).closeAbruptly();

    // Deliver some bytes just in time
    timeProvider.advanceTimeByMillis(RpcTimeouts.TTFB_TIMEOUT.toMillis() - 1);
    timeoutRunner.executeDueActions();
    deliverInitialBytes();

    // Go past the time the first bytes should have been received and check it doesn't timeout
    timeProvider.advanceTimeByMillis(10);
    timeoutRunner.executeDueActions();
    verify(rpcStream, never()).closeAbruptly();
  }

  @Test
  public void disconnectsIfFirstChunkIsNotReceivedInTime() throws Exception {
    sendInitialPayload();

    deliverInitialBytes();

    // Run timeouts
    timeProvider.advanceTimeByMillis(RpcTimeouts.RESP_TIMEOUT.toMillis());
    timeoutRunner.executeDueActions();
    verify(rpcStream).closeAbruptly();
  }

  @Test
  public void doNotDisconnectsIfFirstChunkReceivedInTime() throws Exception {
    sendInitialPayload();

    // First byte is received just in time
    timeProvider.advanceTimeByMillis(RpcTimeouts.TTFB_TIMEOUT.toMillis() - 1);
    deliverChunk(0);

    // Go past the time the first chunk would have timed out but not enough to trigger timeout on
    // the second chunk and ensure the timeout never fires.
    timeProvider.advanceTimeByMillis(RpcTimeouts.RESP_TIMEOUT.toMillis() - 1);
    timeoutRunner.executeDueActions();
    verify(rpcStream, never()).closeAbruptly();
  }

  @Test
  public void disconnectsIfSecondChunkNotReceivedInTime() throws Exception {
    sendInitialPayload();

    timeProvider.advanceTimeByMillis(100);
    deliverChunk(0);
    asyncRequestRunner.executeQueuedActions();
    assertThat(blocks.size()).isEqualTo(1);

    // Run timeouts
    timeProvider.advanceTimeByMillis(RpcTimeouts.RESP_TIMEOUT.toMillis());
    timeoutRunner.executeDueActions();
    verify(rpcStream).closeAbruptly();
  }

  @Test
  public void abortsWhenNoReadComplete() throws Exception {
    sendInitialPayload();

    timeProvider.advanceTimeByMillis(100);
    for (int i = 0; i < maxChunks; i++) {
      deliverChunk(i);
    }

    asyncRequestRunner.executeQueuedActions();

    // Run timeouts
    timeProvider.advanceTimeByMillis(RpcTimeouts.RESP_TIMEOUT.toMillis());
    timeoutRunner.executeDueActions();
    verify(rpcStream).closeAbruptly();
  }

  @Test
  public void shouldCompleteExceptionallyWhenClosedWithTruncatedMessage() {
    sendInitialPayload();

    timeProvider.advanceTimeByMillis(100);
    final Bytes chunkBytes = chunks.get(0);
    deliverBytes(chunkBytes.slice(0, chunkBytes.size() - 1));

    asyncRequestRunner.executeQueuedActions();

    complete();

    assertThat(finishedProcessingFuture).isCompletedExceptionally();
  }

  @Test
  public void doNotDisconnectsIfSecondChunkReceivedInTime() throws Exception {
    sendInitialPayload();

    timeProvider.advanceTimeByMillis(100);
    deliverChunk(0);
    asyncRequestRunner.executeQueuedActions();
    assertThat(blocks.size()).isEqualTo(1);

    // Second chunk is received just in time
    timeProvider.advanceTimeByMillis(RpcTimeouts.RESP_TIMEOUT.toMillis() - 1);
    timeoutRunner.executeDueActions();
    deliverChunk(1);
    asyncRequestRunner.executeQueuedActions();

    // Go past the time the second chunk would have timed out but not enough to trigger timeout on
    // the third chunk and ensure the timeout never fires.
    timeProvider.advanceTimeByMillis(RpcTimeouts.RESP_TIMEOUT.toMillis() - 1);
    timeoutRunner.executeDueActions();
    verify(rpcStream, never()).closeAbruptly();
    assertThat(blocks.size()).isEqualTo(2);
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

  private void deliverError() {
    final Bytes errorChunk = rpcEncoder.encodeErrorResponse(new ServerErrorException());
    deliverBytes(errorChunk);
  }

  private void deliverChunk(final int chunk) {
    final Bytes chunkBytes = chunks.get(chunk);
    deliverBytes(chunkBytes);
  }

  private void complete() {
    reqHandler.readComplete(nodeId, rpcStream);
  }

  private void close() {
    reqHandler.closed(nodeId, rpcStream);
  }
}
