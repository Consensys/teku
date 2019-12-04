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

import com.google.common.collect.ImmutableMap;
import io.libp2p.core.Connection;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.RpcRequest;

public class RpcMethods {

  private final Map<RpcMethod<?, ?>, RpcMessageHandler<?, ?>> methods;

  public RpcMethods(final RpcMessageHandler<?, ?>... handlers) {

    this.methods = createMethodMap(handlers);
  }

  private Map<RpcMethod<?, ?>, RpcMessageHandler<?, ?>> createMethodMap(
      final RpcMessageHandler<?, ?>... handlers) {
    final ImmutableMap.Builder<RpcMethod<?, ?>, RpcMessageHandler<?, ?>> builder =
        ImmutableMap.builder();
    Stream.of(handlers).forEach(handler -> builder.put(handler.getMethod(), handler));
    return builder.build();
  }

  public <I extends RpcRequest, O> CompletableFuture<ResponseStream<O>> invoke(
      final RpcMethod<I, O> method, final Connection connection, final I request) {
    return getHandler(method).invokeRemote(connection, request);
  }

  public Collection<RpcMessageHandler<?, ?>> all() {
    return Collections.unmodifiableCollection(methods.values());
  }

  @SuppressWarnings("unchecked")
  private <I extends RpcRequest, O> RpcMessageHandler<I, O> getHandler(
      final RpcMethod<I, O> method) {
    return (RpcMessageHandler<I, O>) methods.get(method);
  }
}
