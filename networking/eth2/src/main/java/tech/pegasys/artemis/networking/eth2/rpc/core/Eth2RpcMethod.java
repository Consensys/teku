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

import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.RpcRequest;
import tech.pegasys.artemis.networking.eth2.peers.PeerLookup;
import tech.pegasys.artemis.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.artemis.networking.p2p.rpc.RpcMethod;
import tech.pegasys.artemis.networking.p2p.rpc.RpcRequestHandler;

public class Eth2RpcMethod<TRequest extends RpcRequest, TResponse> implements RpcMethod {

  private final String methodMultistreamId;
  private final RpcEncoding encoding;
  private final Class<TRequest> requestType;
  private final Class<TResponse> responseType;
  private final boolean closeNotification;

  private final LocalMessageHandler<TRequest, TResponse> localMessageHandler;
  private final PeerLookup peerLookup;

  private final RpcEncoder rpcEncoder;

  public Eth2RpcMethod(
      final String methodMultistreamId,
      final RpcEncoding encoding,
      final Class<TRequest> requestType,
      final Class<TResponse> responseType,
      final boolean closeNotification,
      final LocalMessageHandler<TRequest, TResponse> localMessageHandler,
      final PeerLookup peerLookup) {
    this.closeNotification = closeNotification;
    this.methodMultistreamId = methodMultistreamId + "/" + encoding.getName();
    this.encoding = encoding;
    this.requestType = requestType;
    this.responseType = responseType;
    this.localMessageHandler = localMessageHandler;
    this.peerLookup = peerLookup;

    this.rpcEncoder = new RpcEncoder(encoding);
  }

  public String getMultistreamId() {
    return methodMultistreamId;
  }

  public Class<TRequest> getRequestType() {
    return requestType;
  }

  public Class<TResponse> getResponseType() {
    return responseType;
  }

  public RpcEncoding getEncoding() {
    return encoding;
  }

  public Bytes encodeRequest(TRequest request) {
    return rpcEncoder.encodeRequest(request);
  }

  public RequestRpcDecoder<TRequest> createRequestDecoder() {
    return new RequestRpcDecoder<>(requestType, encoding);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final Eth2RpcMethod<?, ?> rpcMethod = (Eth2RpcMethod<?, ?>) o;
    return methodMultistreamId.equals(rpcMethod.methodMultistreamId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(methodMultistreamId);
  }

  @Override
  public String getId() {
    return methodMultistreamId;
  }

  public boolean getCloseNotification() {
    return closeNotification;
  }

  @Override
  public RpcRequestHandler createIncomingRequestHandler() {
    return new Eth2IncomingRequestHandler<>(this, peerLookup, localMessageHandler);
  }

  public Eth2OutgoingRequestHandler<TRequest, TResponse> createOutgoingRequestHandler(
      final int maximumResponseChunks) {
    return new Eth2OutgoingRequestHandler<>(this, maximumResponseChunks);
  }
}
