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
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.BeaconChainMethods;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseStream.ResponseListener;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.teku.util.Waiter;
import tech.pegasys.teku.util.async.SafeFuture;
import tech.pegasys.teku.util.async.StubAsyncRunner;

public abstract class Eth2OutgoingRequestHandlerTest
    extends AbstractRequestHandlerTest<
        Eth2OutgoingRequestHandler<BeaconBlocksByRangeRequestMessage, SignedBeaconBlock>> {

  private final StubAsyncRunner asyncRequestRunner = new StubAsyncRunner();
  private final StubAsyncRunner timeoutRunner = new StubAsyncRunner();

  private final List<SignedBeaconBlock> blocks = new ArrayList<>();
  private final AtomicReference<ResponseListener<SignedBeaconBlock>> responseListener =
      new AtomicReference<>(blocks::add);
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

  @Test
  public void processAllExpectedChunks() throws Exception {
    sendInitialPayload();
    verify(rpcStream).closeWriteStream();

    for (int i = 0; i < maxChunks; i++) {
      deliverChunk(i);
      assertThat(finishedProcessingFuture).isNotDone();
    }
    complete();

    asyncRequestRunner.waitForExactly(maxChunks - 1);
    assertThat(finishedProcessingFuture).isNotDone();

    asyncRequestRunner.waitForExactly(1);
    timeoutRunner.executeUntilDone();
    Waiter.waitFor(() -> assertThat(finishedProcessingFuture).isDone());

    assertThat(finishedProcessingFuture).isCompletedWithValue(null);
    verify(rpcStream).close();
    assertThat(blocks.size()).isEqualTo(3);
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
    verify(rpcStream, atLeastOnce()).close();
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
        deliverBytes(lastChunkWithExtraChunk);
      } else {
        deliverChunk(i);
      }
    }
    complete();

    asyncRequestRunner.waitForExactly(maxChunks - 1);
    timeoutRunner.executeUntilDone();
    Waiter.waitFor(() -> assertThat(finishedProcessingFuture).isDone());

    verify(rpcStream).close();
    assertThat(blocks.size()).isEqualTo(2);
    assertThat(finishedProcessingFuture).isCompletedExceptionally();
    assertThatThrownBy(finishedProcessingFuture::get)
        .hasRootCause(RpcException.EXTRA_DATA_APPENDED);
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

  private void deliverError() throws IOException {
    final Bytes errorChunk = rpcEncoder.encodeErrorResponse(RpcException.SERVER_ERROR);
    deliverBytes(errorChunk);
  }

  private void deliverChunk(final int chunk) throws IOException {
    final Bytes chunkBytes = chunks.get(chunk);
    deliverBytes(chunkBytes);
  }

  private void complete() {
    reqHandler.complete(nodeId, rpcStream);
  }

  public static class Eth2OutgoingRequestHandlerTest_ssz extends Eth2OutgoingRequestHandlerTest {

    @Override
    protected RpcEncoding getRpcEncoding() {
      return RpcEncoding.SSZ;
    }
  }

  public static class Eth2OutgoingRequestHandlerTest_sszSnappy
      extends Eth2OutgoingRequestHandlerTest {

    @Override
    protected RpcEncoding getRpcEncoding() {
      return RpcEncoding.SSZ_SNAPPY;
    }
  }
}
