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

package tech.pegasys.teku.networking.eth2.rpc.core;

import static tech.pegasys.teku.networking.eth2.rpc.core.Eth2OutgoingRequestHandler.State.ABORTED;
import static tech.pegasys.teku.networking.eth2.rpc.core.Eth2OutgoingRequestHandler.State.CLOSED;
import static tech.pegasys.teku.networking.eth2.rpc.core.Eth2OutgoingRequestHandler.State.DATA_COMPLETED;
import static tech.pegasys.teku.networking.eth2.rpc.core.Eth2OutgoingRequestHandler.State.EXPECT_DATA;
import static tech.pegasys.teku.networking.eth2.rpc.core.Eth2OutgoingRequestHandler.State.READ_COMPLETE;

import com.google.common.annotations.VisibleForTesting;
import io.netty.buffer.ByteBuf;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.ExtraDataAppendedException;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.networking.p2p.rpc.RpcRequestHandler;
import tech.pegasys.teku.networking.p2p.rpc.RpcStream;
import tech.pegasys.teku.spec.config.NetworkingSpecConfig;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.RpcRequest;

public class Eth2OutgoingRequestHandler<
        TRequest extends RpcRequest & SszData, TResponse extends SszData>
    implements RpcRequestHandler {

  @VisibleForTesting
  enum State {
    EXPECT_DATA,
    DATA_COMPLETED,
    READ_COMPLETE,
    CLOSED,
    ABORTED
  }

  private static final Logger LOG = LogManager.getLogger();

  private final AsyncRunner asyncRunner;
  private final int maximumResponseChunks;
  private final Eth2RpcResponseHandler<TResponse, ?> responseHandler;
  private final ResponseStream<TResponse> responseStream;

  private final AsyncRunner timeoutRunner;
  private final AtomicBoolean hasReceivedInitialBytes = new AtomicBoolean(false);
  private final AtomicInteger currentChunkCount = new AtomicInteger(0);
  private final AtomicReference<State> state;
  private final AtomicReference<AsyncResponseProcessor<TResponse>> responseProcessor =
      new AtomicReference<>();

  private final String protocolId;
  private final RpcResponseDecoder<TResponse, ?> responseDecoder;
  private final boolean shouldReceiveResponse;
  private final Duration ttbfTimeout;
  private final Duration respTimeout;

  public Eth2OutgoingRequestHandler(
      final AsyncRunner asyncRunner,
      final AsyncRunner timeoutRunner,
      final String protocolId,
      final RpcResponseDecoder<TResponse, ?> responseDecoder,
      final boolean shouldReceiveResponse,
      final TRequest request,
      final Eth2RpcResponseHandler<TResponse, ?> responseHandler,
      final NetworkingSpecConfig networkingConfig) {
    this.asyncRunner = asyncRunner;
    this.timeoutRunner = timeoutRunner;
    this.maximumResponseChunks = request.getMaximumResponseChunks();

    this.responseHandler = responseHandler;
    responseStream = new ResponseStream<>(responseHandler);
    this.responseDecoder = responseDecoder;
    this.shouldReceiveResponse = shouldReceiveResponse;
    this.protocolId = protocolId;
    this.ttbfTimeout = Duration.of(networkingConfig.getTtfbTimeout(), ChronoUnit.SECONDS);
    this.respTimeout = Duration.of(networkingConfig.getRespTimeout(), ChronoUnit.SECONDS);
    this.state = new AtomicReference<>(shouldReceiveResponse ? EXPECT_DATA : DATA_COMPLETED);
  }

  public void handleInitialPayloadSent(final RpcStream stream) {
    // Close the write side of the stream
    stream.closeWriteStream().ifExceptionGetsHereRaiseABug();

    if (shouldReceiveResponse) {
      // Start timer for first bytes
      ensureFirstBytesArriveWithinTimeLimit(stream);
    } else {
      ensureReadCompleteArrivesInTime(stream);
    }
  }

  @Override
  public void active(NodeId nodeId, RpcStream rpcStream) {}

  @Override
  public void processData(final NodeId nodeId, final RpcStream rpcStream, final ByteBuf data) {
    if (!data.isReadable()) {
      return;
    }

    try {
      if (state.get() != EXPECT_DATA) {
        throw new RpcException.ExtraDataAppendedException(" extra data: " + bufToString(data));
      }

      onFirstByteReceived(rpcStream);

      List<TResponse> maybeResponses = responseDecoder.decodeNextResponses(data);
      final int chunksReceived = currentChunkCount.addAndGet(maybeResponses.size());

      if (chunksReceived > maximumResponseChunks) {
        throw new ExtraDataAppendedException();
      }

      for (TResponse maybeResponse : maybeResponses) {
        getResponseProcessor(rpcStream).processResponse(maybeResponse);
      }
      if (chunksReceived < maximumResponseChunks) {
        if (!maybeResponses.isEmpty()) {
          ensureNextResponseArrivesInTime(rpcStream, chunksReceived, currentChunkCount);
        }
      } else {
        if (!transferToState(DATA_COMPLETED, List.of(EXPECT_DATA))) {
          abortRequest(rpcStream, new IllegalStateException("Unexpected state: " + state));
          return;
        }
        ensureReadCompleteArrivesInTime(rpcStream);
      }
    } catch (final RpcException e) {
      abortRequest(rpcStream, e);
    } catch (final Throwable t) {
      LOG.error("Encountered error while processing response", t);
      abortRequest(rpcStream, t);
    }
  }

  private AsyncResponseProcessor<TResponse> getResponseProcessor(final RpcStream rpcStream) {
    return responseProcessor.updateAndGet(
        oldVal -> {
          if (oldVal == null) {
            return new AsyncResponseProcessor<>(
                asyncRunner, responseStream, throwable -> abortRequest(rpcStream, throwable));
          } else {
            return oldVal;
          }
        });
  }

  private String bufToString(ByteBuf buf) {
    final int contentSize = Integer.min(buf.readableBytes(), 1024);
    String bufContent = "";
    if (contentSize > 0) {
      ByteBuf bufSlice = buf.slice(0, contentSize);
      byte[] bytes = new byte[bufSlice.readableBytes()];
      bufSlice.getBytes(0, bytes);
      bufContent += Bytes.wrap(bytes);
      if (contentSize < buf.readableBytes()) {
        bufContent += "...";
      }
    }
    return "ByteBuf{" + buf + ", content: " + bufContent + "}";
  }

  @Override
  public void readComplete(NodeId nodeId, RpcStream rpcStream) {
    if (!transferToState(READ_COMPLETE, List.of(DATA_COMPLETED, EXPECT_DATA))) {
      abortRequest(rpcStream, new IllegalStateException("Unexpected state: " + state));
      return;
    }

    try {
      responseDecoder.complete();
      completeRequest(rpcStream);
    } catch (RpcException e) {
      abortRequest(rpcStream, e);
    }
  }

  @Override
  public void closed(NodeId nodeId, RpcStream rpcStream) {
    if (!transferToState(CLOSED, List.of(READ_COMPLETE))) {
      abortRequest(rpcStream, new IllegalStateException("Unexpected state: " + state));
    }
  }

  public SafeFuture<Void> getCompletedFuture() {
    return responseHandler.getCompletedFuture();
  }

  private boolean transferToState(State toState, Collection<State> fromStates) {
    for (State fromState : fromStates) {
      if (state.compareAndSet(fromState, toState)) {
        return true;
      }
    }
    return false;
  }

  private void onFirstByteReceived(final RpcStream rpcStream) {
    if (hasReceivedInitialBytes.compareAndSet(false, true)) {
      // Setup initial chunk timeout
      ensureNextResponseArrivesInTime(rpcStream, currentChunkCount.get(), currentChunkCount);
    }
  }

  private void completeRequest(final RpcStream rpcStream) {
    getResponseProcessor(rpcStream)
        .finishProcessing()
        .thenAccept(
            (__) -> {
              try {
                responseStream.completeSuccessfully();
                LOG.trace("Complete request");
              } catch (final Throwable t) {
                LOG.error("Encountered error while completing outgoing request", t);
                responseStream.completeWithError(t);
              }
            })
        .exceptionally(
            (err) -> {
              abortRequest(rpcStream, err, true);
              return null;
            })
        .ifExceptionGetsHereRaiseABug();
  }

  private void abortRequest(final RpcStream rpcStream, Throwable error) {
    abortRequest(rpcStream, error, false);
  }

  private void abortRequest(final RpcStream rpcStream, Throwable error, final boolean force) {
    if (!transferToState(ABORTED, List.of(EXPECT_DATA, DATA_COMPLETED, READ_COMPLETE)) && !force) {
      return;
    }

    LOG.trace("Abort request: {}", error.getMessage());

    // releasing any resources
    try {
      responseDecoder.close();
      rpcStream.closeAbruptly().ifExceptionGetsHereRaiseABug();
    } finally {
      getResponseProcessor(rpcStream)
          .finishProcessing()
          .always(() -> responseStream.completeWithError(error));
    }
  }

  private void ensureFirstBytesArriveWithinTimeLimit(final RpcStream stream) {
    timeoutRunner
        .getDelayedFuture(ttbfTimeout)
        .thenAccept(
            (__) -> {
              if (!hasReceivedInitialBytes.get()) {
                abortRequest(
                    stream,
                    new RpcTimeoutException("Timed out waiting for initial response", ttbfTimeout));
              }
            })
        .ifExceptionGetsHereRaiseABug();
  }

  private void ensureNextResponseArrivesInTime(
      final RpcStream stream,
      final int previousResponseCount,
      final AtomicInteger currentResponseCount) {
    final Duration timeout = respTimeout;
    timeoutRunner
        .getDelayedFuture(timeout)
        .thenAccept(
            (__) -> {
              if (previousResponseCount == currentResponseCount.get()) {
                abortRequest(
                    stream,
                    new RpcTimeoutException(
                        "Timed out waiting for response chunk " + previousResponseCount, timeout));
              }
            })
        .ifExceptionGetsHereRaiseABug();
  }

  private void ensureReadCompleteArrivesInTime(final RpcStream stream) {
    final Duration timeout = respTimeout;
    timeoutRunner
        .getDelayedFuture(timeout)
        .thenAccept(
            (__) -> {
              if (!(state.get() == READ_COMPLETE || state.get() == CLOSED)) {
                abortRequest(
                    stream,
                    new RpcTimeoutException("Timed out waiting for read channel close", timeout));
              }
            })
        .ifExceptionGetsHereRaiseABug();
  }

  @VisibleForTesting
  State getState() {
    return state.get();
  }

  @Override
  public String toString() {
    return "Eth2OutgoingRequestHandler{" + protocolId + '}';
  }
}
