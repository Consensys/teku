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

import io.netty.buffer.ByteBuf;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.RpcRequest;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcTimeouts.RpcTimeoutException;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.networking.p2p.rpc.RpcRequestHandler;
import tech.pegasys.teku.networking.p2p.rpc.RpcStream;
import tech.pegasys.teku.util.async.AsyncRunner;

public class Eth2OutgoingRequestHandler<TRequest extends RpcRequest, TResponse>
    implements RpcRequestHandler {

  private static final Logger LOG = LogManager.getLogger();

  private final Eth2RpcMethod<TRequest, TResponse> method;
  private final int maximumResponseChunks;
  private final ResponseStreamImpl<TResponse> responseStream = new ResponseStreamImpl<>();

  private final AsyncRunner timeoutRunner;
  private final AtomicBoolean hasReceivedInitialBytes = new AtomicBoolean(false);
  private final AtomicInteger currentChunkCount = new AtomicInteger(0);
  private final AtomicBoolean isClosed = new AtomicBoolean(false);

  private final ResponseRpcDecoder<TResponse> responseHandler;
  private final AsyncResponseProcessor<TResponse> responseProcessor;

  private volatile RpcStream rpcStream;

  public Eth2OutgoingRequestHandler(
      final AsyncRunner asyncRunner,
      final AsyncRunner timeoutRunner,
      final Eth2RpcMethod<TRequest, TResponse> method,
      final int maximumResponseChunks) {
    this.timeoutRunner = timeoutRunner;
    this.method = method;
    this.maximumResponseChunks = maximumResponseChunks;

    responseProcessor =
        new AsyncResponseProcessor<>(asyncRunner, responseStream, this::onAsyncProcessorError);
    responseHandler = new ResponseRpcDecoder<>(responseProcessor::processResponse, this.method);
  }

  @Override
  public void onActivation(final RpcStream rpcStream) {
    this.rpcStream = rpcStream;
  }

  @Override
  public void onData(final NodeId nodeId, final RpcStream rpcStream, final ByteBuf bytes) {
    try {
      if (hasReceivedInitialBytes.compareAndSet(false, true)) {
        // Setup initial chunk timeout
        ensureNextResponseArrivesInTime(rpcStream, currentChunkCount.get(), currentChunkCount);
      }
      LOG.trace("Requester received {} bytes.", bytes.capacity());
      responseHandler.onDataReceived(bytes);

      final int previousResponseCount = currentChunkCount.get();
      currentChunkCount.set(responseProcessor.getResponseCount());
      if (currentChunkCount.get() >= maximumResponseChunks) {
        completeRequest(rpcStream);
      } else if (currentChunkCount.get() > previousResponseCount) {
        ensureNextResponseArrivesInTime(rpcStream, currentChunkCount.get(), currentChunkCount);
      }
    } catch (final Throwable t) {
      LOG.error("Encountered error while processing response", t);
      cancelRequest(rpcStream, t);
    }
  }

  @Override
  public void onRequestComplete() {
    completeRequest(this.rpcStream);
  }

  private void onAsyncProcessorError(final Throwable throwable) {
    cancelRequest(this.rpcStream, throwable);
  }

  public void handleInitialPayloadSent(final RpcStream stream) {
    if (method.shouldReceiveResponse()) {
      // Close the write side of the stream
      stream.closeWriteStream().reportExceptions();
      // Start timer for first bytes
      ensureFirstBytesArriveWithinTimeLimit(stream);
    } else {
      // If we're not expecting any response, complete the request
      completeRequest(stream);
    }
  }

  private void completeRequest(final RpcStream rpcStream) {
    if (!isClosed.compareAndSet(false, true)) {
      return;
    }
    if (rpcStream != null) {
      rpcStream.close().reportExceptions();
    }
    responseProcessor
        .finishProcessing()
        .thenAccept(
            (__) -> {
              try {
                responseHandler.close();
                responseStream.completeSuccessfully();
                LOG.trace("Complete request");
              } catch (final RpcException e) {
                LOG.debug(
                    "Encountered unconsumed data when completing outgoing request: {}",
                    e.getErrorMessage());
                responseStream.completeWithError(e);
              } catch (final Throwable t) {
                LOG.error("Encountered error while completing outgoing request", t);
                responseStream.completeWithError(t);
              }
            })
        .exceptionally(
            (err) -> {
              cancelRequest(rpcStream, err, true);
              return null;
            })
        .reportExceptions();
  }

  private void cancelRequest(final RpcStream rpcStream, Throwable error) {
    cancelRequest(rpcStream, error, false);
  }

  private void cancelRequest(
      final RpcStream rpcStream, Throwable error, final boolean forceCancel) {
    if (!isClosed.compareAndSet(false, true) && !forceCancel) {
      return;
    }

    LOG.debug("Cancel request: {}", error.getMessage());
    rpcStream.close().reportExceptions();
    responseHandler.closeSilently();
    responseProcessor.finishProcessing().always(() -> responseStream.completeWithError(error));
  }

  private void ensureFirstBytesArriveWithinTimeLimit(final RpcStream stream) {
    final Duration timeout = RpcTimeouts.TTFB_TIMEOUT;
    timeoutRunner
        .getDelayedFuture(timeout.toMillis(), TimeUnit.MILLISECONDS)
        .thenAccept(
            (__) -> {
              if (!hasReceivedInitialBytes.get()) {
                cancelRequest(
                    stream,
                    new RpcTimeoutException("Timed out waiting for initial response", timeout));
              }
            })
        .reportExceptions();
  }

  private void ensureNextResponseArrivesInTime(
      final RpcStream stream,
      final int previousResponseCount,
      final AtomicInteger currentResponseCount) {
    final Duration timeout = RpcTimeouts.RESP_TIMEOUT;
    timeoutRunner
        .getDelayedFuture(timeout.toMillis(), TimeUnit.MILLISECONDS)
        .thenAccept(
            (__) -> {
              if (previousResponseCount == currentResponseCount.get()) {
                cancelRequest(
                    stream,
                    new RpcTimeoutException(
                        "Timed out waiting for response chunk " + previousResponseCount, timeout));
              }
            })
        .reportExceptions();
  }

  public ResponseStreamImpl<TResponse> getResponseStream() {
    return responseStream;
  }
}
