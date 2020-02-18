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

import static tech.pegasys.artemis.util.alogger.ALogger.STDOUT;

import io.netty.buffer.ByteBuf;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.RpcRequest;
import tech.pegasys.artemis.networking.eth2.rpc.core.RpcTimeouts.RpcTimeoutException;
import tech.pegasys.artemis.networking.p2p.peer.NodeId;
import tech.pegasys.artemis.networking.p2p.rpc.RpcRequestHandler;
import tech.pegasys.artemis.networking.p2p.rpc.RpcStream;
import tech.pegasys.artemis.util.async.AsyncRunner;

public class Eth2OutgoingRequestHandler<TRequest extends RpcRequest, TResponse>
    implements RpcRequestHandler {
  private static final Logger LOG = LogManager.getLogger();

  private final Eth2RpcMethod<TRequest, TResponse> method;
  private final int maximumResponseChunks;
  private final ResponseStreamImpl<TResponse> responseStream = new ResponseStreamImpl<>();

  private final AsyncRunner asyncRunner;
  private final AtomicBoolean hasReceivedInitialBytes = new AtomicBoolean(false);
  private AtomicInteger currentChunkCount = new AtomicInteger(0);

  private final ResponseRpcDecoder<TResponse> responseHandler;
  private final AsyncResponseProcessor<TResponse> responseProcessor;

  private RpcStream rpcStream;

  public Eth2OutgoingRequestHandler(
      final AsyncRunner asyncRunner,
      final AsyncRunner timeoutRunner,
      final Eth2RpcMethod<TRequest, TResponse> method,
      final int maximumResponseChunks) {
    this.asyncRunner = timeoutRunner;
    this.method = method;
    this.maximumResponseChunks = maximumResponseChunks;

    responseProcessor = new AsyncResponseProcessor<>(asyncRunner, responseStream, this::onError);
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
      STDOUT.log(Level.TRACE, "Requester received " + bytes.capacity() + " bytes.");
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
      cancelRequest(rpcStream, true, t);
    }
  }

  @Override
  public void onRequestComplete() {
    completeRequest(this.rpcStream);
  }

  private void onError(final Throwable throwable) {
    cancelRequest(this.rpcStream, true, throwable);
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
        .reportExceptions();
  }

  private void cancelRequest(
      final RpcStream rpcStream, final boolean dropPendingResponses, Throwable error) {
    LOG.debug(
        "Cancel request (drop pending responses: {}): {}",
        dropPendingResponses,
        error.getMessage());
    rpcStream.close().reportExceptions();
    responseHandler.closeSilently();
    if (dropPendingResponses) {
      responseProcessor.cancel();
      responseStream.completeWithError(error);
    } else {
      responseProcessor
          .finishProcessing()
          .thenAccept(__ -> responseStream.completeWithError(error))
          .reportExceptions();
    }
  }

  private void ensureFirstBytesArriveWithinTimeLimit(final RpcStream stream) {
    final Duration timeout = RpcTimeouts.TTFB_TIMEOUT;
    asyncRunner
        .getDelayedFuture(timeout.toMillis(), TimeUnit.MILLISECONDS)
        .thenAccept(
            (__) -> {
              if (!hasReceivedInitialBytes.get()) {
                LOG.debug(
                    "Failed to receive initial response within {} sec. Close stream.",
                    timeout.getSeconds());
                cancelRequest(
                    stream,
                    false,
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
    asyncRunner
        .getDelayedFuture(timeout.toMillis(), TimeUnit.MILLISECONDS)
        .thenAccept(
            (__) -> {
              if (previousResponseCount == currentResponseCount.get()) {
                LOG.debug(
                    "Failed to receive response chunk {} within {} sec. Close stream.",
                    previousResponseCount,
                    timeout.getSeconds());
                cancelRequest(
                    stream,
                    false,
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
