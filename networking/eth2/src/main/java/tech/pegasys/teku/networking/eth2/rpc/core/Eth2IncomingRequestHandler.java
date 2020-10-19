/*
 * Copyright 2019 ConsenSys AG.
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

import com.google.common.annotations.VisibleForTesting;
import io.netty.buffer.ByteBuf;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.RpcRequest;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.peers.PeerLookup;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.networking.p2p.rpc.RpcRequestHandler;
import tech.pegasys.teku.networking.p2p.rpc.RpcStream;
import tech.pegasys.teku.networking.p2p.rpc.StreamClosedException;

public class Eth2IncomingRequestHandler<TRequest extends RpcRequest, TResponse>
    implements RpcRequestHandler {
  private static final Logger LOG = LogManager.getLogger();

  private final Eth2RpcMethod<TRequest, TResponse> method;
  private final PeerLookup peerLookup;
  private final LocalMessageHandler<TRequest, TResponse> localMessageHandler;
  private final RpcEncoder rpcEncoder;

  private final RpcRequestDecoder<TRequest> requestDecoder;

  private final AsyncRunner asyncRunner;
  private final AtomicBoolean requestHandled = new AtomicBoolean(false);

  public Eth2IncomingRequestHandler(
      final AsyncRunner asyncRunner,
      final Eth2RpcMethod<TRequest, TResponse> method,
      final PeerLookup peerLookup,
      final LocalMessageHandler<TRequest, TResponse> localMessageHandler) {
    this.asyncRunner = asyncRunner;
    this.method = method;
    this.peerLookup = peerLookup;
    this.localMessageHandler = localMessageHandler;
    this.rpcEncoder = new RpcEncoder(method.getEncoding());

    requestDecoder = method.createRequestDecoder();
  }

  @Override
  public void active(NodeId nodeId, RpcStream rpcStream) {
    ensureRequestReceivedWithinTimeLimit(rpcStream);
  }

  @Override
  public void processData(final NodeId nodeId, final RpcStream rpcStream, final ByteBuf data) {
    try {
      Optional<Eth2Peer> peer = peerLookup.getConnectedPeer(nodeId);
      requestDecoder
          .decodeRequest(data)
          .ifPresent(
              request ->
                  handleRequest(peer, request, new RpcResponseCallback<>(rpcStream, rpcEncoder)));
    } catch (final RpcException e) {
      requestHandled.set(true);
      new RpcResponseCallback<>(rpcStream, rpcEncoder).completeWithErrorResponse(e);
    }
  }

  @Override
  public void readComplete(NodeId nodeId, RpcStream rpcStream) {
    try {
      Optional<Eth2Peer> peer = peerLookup.getConnectedPeer(nodeId);
      requestDecoder
          .complete()
          .ifPresent(
              request ->
                  handleRequest(peer, request, new RpcResponseCallback<>(rpcStream, rpcEncoder)));
    } catch (RpcException e) {
      new RpcResponseCallback<>(rpcStream, rpcEncoder).completeWithErrorResponse(e);
      LOG.debug("RPC Request stream closed prematurely", e);
    }
  }

  @Override
  public void closed(NodeId nodeId, RpcStream rpcStream) {}

  private void handleRequest(
      Optional<Eth2Peer> peer, TRequest request, ResponseCallback<TResponse> callback) {
    try {
      requestHandled.set(true);
      localMessageHandler.onIncomingMessage(peer, request, callback);
    } catch (final StreamClosedException e) {
      LOG.trace("Stream closed before response sent for request {}", method.getMultistreamId(), e);
      callback.completeWithUnexpectedError(e);
    } catch (final Throwable t) {
      LOG.error("Unhandled error while processing request {}", method.getMultistreamId(), t);
      callback.completeWithUnexpectedError(t);
    }
  }

  private void ensureRequestReceivedWithinTimeLimit(final RpcStream stream) {
    final Duration timeout = RpcTimeouts.RESP_TIMEOUT;
    asyncRunner
        .getDelayedFuture(timeout.toMillis(), TimeUnit.MILLISECONDS)
        .thenAccept(
            (__) -> {
              if (!requestHandled.get()) {
                LOG.debug(
                    "Failed to receive incoming request data within {} sec for method {}. Close stream.",
                    timeout.getSeconds(),
                    method);
                stream.closeAbruptly().reportExceptions();
              }
            })
        .reportExceptions();
  }

  @VisibleForTesting
  boolean hasRequestBeenReceived() {
    return requestHandled.get();
  }

  @Override
  public String toString() {
    return "Eth2IncomingRequestHandler{" + "method=" + method + '}';
  }
}
