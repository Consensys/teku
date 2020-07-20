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

import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.RpcRequest;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.networking.eth2.peers.PeerLookup;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.teku.networking.p2p.rpc.RpcMethod;

public class Eth2RpcMethod<TRequest extends RpcRequest, TResponse> implements RpcMethod {

  private final AsyncRunner asyncRunner;

  private final String methodMultistreamId;
  private final RpcEncoding encoding;
  private final Class<TRequest> requestType;
  private final Class<TResponse> responseType;
  private final boolean expectResponseToRequest;

  private final LocalMessageHandler<TRequest, TResponse> localMessageHandler;
  private final PeerLookup peerLookup;

  private final RpcEncoder rpcEncoder;

  public Eth2RpcMethod(
      final AsyncRunner asyncRunner,
      final String methodMultistreamId,
      final RpcEncoding encoding,
      final Class<TRequest> requestType,
      final Class<TResponse> responseType,
      final boolean expectResponseToRequest,
      final LocalMessageHandler<TRequest, TResponse> localMessageHandler,
      final PeerLookup peerLookup) {
    this.asyncRunner = asyncRunner;
    this.expectResponseToRequest = expectResponseToRequest;
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

  public RpcEncoder getRpcEncoder() {
    return rpcEncoder;
  }

  public Bytes encodeRequest(TRequest request) {
    return rpcEncoder.encodeRequest(request);
  }

  public RpcRequestDecoder<TRequest> createRequestDecoder() {
    return new RpcRequestDecoder<>(requestType, encoding);
  }

  public RpcResponseDecoder<TResponse> createResponseDecoder() {
    return new RpcResponseDecoder<>(responseType, encoding);
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

  public boolean shouldReceiveResponse() {
    return expectResponseToRequest;
  }

  @Override
  public Eth2IncomingRequestHandler<TRequest, TResponse> createIncomingRequestHandler() {
    return new Eth2IncomingRequestHandler<>(asyncRunner, this, peerLookup, localMessageHandler);
  }

  public Eth2OutgoingRequestHandler<TRequest, TResponse> createOutgoingRequestHandler(
      final int maximumResponseChunks) {
    return new Eth2OutgoingRequestHandler<>(asyncRunner, asyncRunner, this, maximumResponseChunks);
  }

  @Override
  public String toString() {
    return "Eth2RpcMethod{" + "id='" + methodMultistreamId + '\'' + '}';
  }
}
