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
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.RpcRequest;
import tech.pegasys.artemis.networking.p2p.peer.NodeId;
import tech.pegasys.artemis.networking.p2p.rpc.RpcRequestHandler;
import tech.pegasys.artemis.networking.p2p.rpc.RpcStream;

public class Eth2OutgoingRequestHandler<TRequest extends RpcRequest, TResponse>
    implements RpcRequestHandler {
  private static final Logger LOG = LogManager.getLogger();

  private final Eth2RpcMethod<TRequest, TResponse> method;
  private final int maximumResponseChunks;
  private final ResponseStreamImpl<TResponse> responseStream = new ResponseStreamImpl<>();

  private ResponseRpcDecoder<TResponse> responseHandler;

  public Eth2OutgoingRequestHandler(
      final Eth2RpcMethod<TRequest, TResponse> method, final int maximumResponseChunks) {
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
      STDOUT.log(Level.TRACE, "Requester received " + bytes.capacity() + " bytes.");
      responseHandler.onDataReceived(bytes);
      if (responseStream.getResponseChunkCount() == maximumResponseChunks) {
        rpcStream.close().reportExceptions();
        responseHandler.close();
        responseStream.completeSuccessfully();
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

  public void handleInitialPayloadSent(RpcStream stream) {
    if (method.shouldReceiveResponse()) {
      // Close the write side of the stream
      stream.closeWriteStream().reportExceptions();
    } else {
      // If we're not expecting any response, close the stream altogether
      stream.close().reportExceptions();
      responseStream.completeSuccessfully();
    }
  }

  public ResponseStreamImpl<TResponse> getResponseStream() {
    return responseStream;
  }
}
