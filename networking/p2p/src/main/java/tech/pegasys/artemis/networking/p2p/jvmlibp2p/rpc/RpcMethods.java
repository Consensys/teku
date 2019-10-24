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

package tech.pegasys.artemis.networking.p2p.jvmlibp2p.rpc;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import io.libp2p.core.Connection;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.BeaconBlocksMessageRequest;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.BeaconBlocksMessageResponse;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.GoodbyeMessage;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.HelloMessage;
import tech.pegasys.artemis.networking.p2p.jvmlibp2p.PeerLookup;
import tech.pegasys.artemis.util.sos.SimpleOffsetSerializable;

public class RpcMethods {

  private final Map<RpcMethod<?, ?>, RPCMessageHandler<?, ?>> methods;

  public RpcMethods(
      PeerLookup peerLookup,
      LocalMessageHandler<HelloMessage, HelloMessage> helloHandler,
      LocalMessageHandler<GoodbyeMessage, Void> goodbyeHandler,
      LocalMessageHandler<BeaconBlocksMessageRequest, BeaconBlocksMessageResponse>
          beaconBlocksHandler) {

    this.methods =
        createMethodMap(
            new RPCMessageHandler<>(RpcMethod.HELLO, peerLookup, helloHandler),
            new RPCMessageHandler<>(RpcMethod.GOODBYE, peerLookup, goodbyeHandler)
                .setCloseNotification(),
            new RPCMessageHandler<>(RpcMethod.BEACON_BLOCKS, peerLookup, beaconBlocksHandler));
  }

  private Map<RpcMethod<?, ?>, RPCMessageHandler<?, ?>> createMethodMap(
      final RPCMessageHandler<?, ?>... handlers) {
    final Builder<RpcMethod<?, ?>, RPCMessageHandler<?, ?>> builder = ImmutableMap.builder();
    Stream.of(handlers).forEach(handler -> builder.put(handler.getMethod(), handler));
    return builder.build();
  }

  public <I extends SimpleOffsetSerializable, O> CompletableFuture<O> invoke(
      final RpcMethod<I, O> method, final Connection connection, final I request) {
    return getHandler(method).invokeRemote(connection, request);
  }

  public Collection<RPCMessageHandler<?, ?>> all() {
    return Collections.unmodifiableCollection(methods.values());
  }

  @SuppressWarnings("unchecked")
  private <I extends SimpleOffsetSerializable, O> RPCMessageHandler<I, O> getHandler(
      final RpcMethod<I, O> method) {
    return (RPCMessageHandler<I, O>) methods.get(method);
  }
}
