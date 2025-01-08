/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.networking.p2p.libp2p.rpc;

import io.libp2p.core.Connection;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.ThrottlingTaskQueue;
import tech.pegasys.teku.networking.p2p.rpc.RpcRequestHandler;
import tech.pegasys.teku.networking.p2p.rpc.RpcResponseHandler;
import tech.pegasys.teku.networking.p2p.rpc.RpcStreamController;
import tech.pegasys.teku.spec.constants.NetworkConstants;

public class ThrottlingRpcHandler<
    TOutgoingHandler extends RpcRequestHandler,
    TRequest,
    TRespHandler extends RpcResponseHandler<?>> {

  private final RpcHandler<TOutgoingHandler, TRequest, TRespHandler> delegate;
  private final ThrottlingTaskQueue requestsQueue =
      ThrottlingTaskQueue.create(NetworkConstants.MAX_CONCURRENT_REQUESTS);

  public ThrottlingRpcHandler(final RpcHandler<TOutgoingHandler, TRequest, TRespHandler> delegate) {
    this.delegate = delegate;
  }

  public SafeFuture<RpcStreamController<TOutgoingHandler>> sendRequest(
      final Connection connection, final TRequest request, final TRespHandler responseHandler) {
    return requestsQueue.queueTask(
        () -> delegate.sendRequest(connection, request, responseHandler));
  }
}
