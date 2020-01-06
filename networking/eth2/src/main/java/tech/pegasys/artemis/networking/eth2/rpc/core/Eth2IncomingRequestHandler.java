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

package tech.pegasys.artemis.networking.eth2.rpc.core;

import io.netty.buffer.ByteBuf;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.RpcRequest;
import tech.pegasys.artemis.networking.eth2.peers.Eth2Peer;
import tech.pegasys.artemis.networking.eth2.peers.PeerLookup;
import tech.pegasys.artemis.networking.p2p.peer.NodeId;
import tech.pegasys.artemis.networking.p2p.rpc.RpcDataHandler;
import tech.pegasys.artemis.networking.p2p.rpc.RpcStream;

public class Eth2IncomingRequestHandler<TRequest extends RpcRequest, TResponse>
    implements RpcDataHandler {
  private static final Logger LOG = LogManager.getLogger();

  private final Eth2RpcMethod<TRequest, TResponse> method;
  private final PeerLookup peerLookup;
  private final LocalMessageHandler<TRequest, TResponse> localMessageHandler;
  private final RpcEncoder rpcEncoder;
  private boolean closeNotification;

  private final RequestRpcDecoder<TRequest> requestReader;
  private ResponseCallback<TResponse> callback;

  public Eth2IncomingRequestHandler(
      Eth2RpcMethod<TRequest, TResponse> method,
      PeerLookup peerLookup,
      LocalMessageHandler<TRequest, TResponse> localMessageHandler) {
    this.method = method;
    this.peerLookup = peerLookup;
    this.localMessageHandler = localMessageHandler;
    this.rpcEncoder = new RpcEncoder(method.getEncoding());

    requestReader = method.createRequestDecoder();
    closeNotification = method.getCloseNotification();
  }

  @Override
  public void onData(final NodeId nodeId, final RpcStream rpcStream, final ByteBuf bytes) {
    final Eth2Peer peer = peerLookup.getConnectedPeer(nodeId);
    if (callback == null) {
      callback = new RpcResponseCallback<>(rpcStream, rpcEncoder, closeNotification);
    }
    try {
      requestReader
          .onDataReceived(bytes)
          .ifPresent(request -> invokeHandler(peer, request, callback));
    } catch (final RpcException e) {
      callback.completeWithError(e);
    }
  }

  private void invokeHandler(
      Eth2Peer peer, TRequest request, ResponseCallback<TResponse> callback) {
    try {
      localMessageHandler.onIncomingMessage(peer, request, callback);
    } catch (final Throwable t) {
      LOG.error("Unhandled error while processing request " + method.getMultistreamId(), t);
      callback.completeWithError(RpcException.SERVER_ERROR);
    }
  }
}
