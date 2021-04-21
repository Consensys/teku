/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.networking.eth2.rpc.core.methods;

import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.networking.eth2.rpc.core.Eth2IncomingRequestHandler;
import tech.pegasys.teku.networking.eth2.rpc.core.Eth2OutgoingRequestHandler;
import tech.pegasys.teku.networking.eth2.rpc.core.Eth2RpcResponseHandler;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcRequestDecoder;
import tech.pegasys.teku.networking.p2p.rpc.RpcMethod;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.RpcRequest;
import tech.pegasys.teku.ssz.SszData;

public interface Eth2RpcMethod<TRequest extends RpcRequest & SszData, TResponse extends SszData>
    extends RpcMethod<
        Eth2OutgoingRequestHandler<TRequest, TResponse>,
        TRequest,
        Eth2RpcResponseHandler<TResponse, ?>> {

  @Override
  Bytes encodeRequest(TRequest request);

  RpcRequestDecoder<TRequest> createRequestDecoder();

  @Override
  List<String> getIds();

  boolean shouldReceiveResponse();

  @Override
  Eth2IncomingRequestHandler<TRequest, TResponse> createIncomingRequestHandler(String protocolId);

  @Override
  Eth2OutgoingRequestHandler<TRequest, TResponse> createOutgoingRequestHandler(
      String protocolId,
      final TRequest request,
      Eth2RpcResponseHandler<TResponse, ?> responseHandler);
}
