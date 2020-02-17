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
  private int currentChunkCount = 0;

  private ResponseRpcDecoder<TResponse> responseHandler;

  public Eth2OutgoingRequestHandler(
      final AsyncRunner asyncRunner,
      final Eth2RpcMethod<TRequest, TResponse> method,
      final int maximumResponseChunks) {
    this.asyncRunner = asyncRunner;
    this.method = method;
    this.maximumResponseChunks = maximumResponseChunks;

    responseHandler = new ResponseRpcDecoder<>(responseStream::respond, this.method);
  }

  @Override
  public void onData(final NodeId nodeId, final RpcStream rpcStream, final ByteBuf bytes) {
    if (responseHandler == null) {
      STDOUT.log(
          Level.WARN, "Received " + bytes.capacity() + " bytes of data before requesting it.");
      throw new IllegalArgumentException("Some data received prior to request: " + bytes);
    }
    try {
      if (hasReceivedInitialBytes.compareAndSet(false, true)) {
        // Setup initial chunk timeout
        ensureNextResponseArrivesInTime(currentChunkCount, rpcStream);
      }
      STDOUT.log(Level.TRACE, "Requester received " + bytes.capacity() + " bytes.");
      responseHandler.onDataReceived(bytes);
      if (responseStream.getResponseChunkCount() >= maximumResponseChunks) {
        rpcStream.close().reportExceptions();
        responseHandler.close();
        responseStream.completeSuccessfully();
      } else if (responseStream.getResponseChunkCount() > currentChunkCount) {
        currentChunkCount = responseStream.getResponseChunkCount();
        ensureNextResponseArrivesInTime(currentChunkCount, rpcStream);
      }
    } catch (final InvalidResponseException e) {
      LOG.debug("Peer responded with invalid data", e);
      responseStream.completeWithError(e);
    } catch (final RpcException e) {
      LOG.debug("Request returned an error {}", e.getErrorMessage());
      responseStream.completeWithError(e);
    } catch (final Throwable t) {
      LOG.error("Failed to handle response", t);
      responseStream.completeWithError(t);
    }
  }

  @Override
  public void onRequestComplete() {
    try {
      responseHandler.close();
      responseStream.completeSuccessfully();
    } catch (final RpcException e) {
      LOG.debug("Request returned an error {}", e.getErrorMessage());
      responseStream.completeWithError(e);
    } catch (final Throwable t) {
      LOG.error("Failed to handle response", t);
      responseStream.completeWithError(t);
    }
  }

  public void handleInitialPayloadSent(final RpcStream stream) {
    if (method.shouldReceiveResponse()) {
      // Close the write side of the stream
      stream.closeWriteStream().reportExceptions();
      // Start timer for first bytes
      ensureFirstBytesArriveWithinTimeLimit(stream);
    } else {
      // If we're not expecting any response, close the stream altogether
      stream.close().reportExceptions();
      responseStream.completeSuccessfully();
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
                stream.close().reportExceptions();
                responseStream.completeWithError(
                    new RpcTimeoutException("Timed out waiting for initial response", timeout));
                responseHandler.closeSilently();
              }
            })
        .reportExceptions();
  }

  private void ensureNextResponseArrivesInTime(
      final int currentResponseCount, final RpcStream stream) {
    final Duration timeout = RpcTimeouts.RESP_TIMEOUT;
    asyncRunner
        .getDelayedFuture(timeout.toMillis(), TimeUnit.MILLISECONDS)
        .thenAccept(
            (__) -> {
              if (responseStream.getResponseChunkCount() == currentResponseCount) {
                LOG.debug(
                    "Failed to receive response chunk {} within {} sec. Close stream.",
                    currentResponseCount,
                    timeout.getSeconds());
                stream.close().reportExceptions();
                responseStream.completeWithError(
                    new RpcTimeoutException(
                        "Timed out waiting for response chunk " + currentResponseCount, timeout));
                responseHandler.closeSilently();
              }
            })
        .reportExceptions();
  }

  public ResponseStreamImpl<TResponse> getResponseStream() {
    return responseStream;
  }
}
