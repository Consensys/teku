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

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import tech.pegasys.teku.networking.eth2.rpc.core.Eth2IncomingRequestHandler;
import tech.pegasys.teku.networking.eth2.rpc.core.Eth2OutgoingRequestHandler;
import tech.pegasys.teku.networking.eth2.rpc.core.Eth2RpcResponseHandler;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.RpcRequest;
import tech.pegasys.teku.ssz.SszData;
import tech.pegasys.teku.ssz.schema.SszSchema;

public class VersionedEth2RpcMethod<
        TRequest extends RpcRequest & SszData, TResponse extends SszData>
    extends AbstractEth2RpcMethod<TRequest, TResponse> {
  private final List<String> protocolIds;
  private final Map<String, SingleProtocolEth2RpcMethod<TRequest, TResponse>> protocolToMethod;

  private VersionedEth2RpcMethod(
      final RpcEncoding encoding,
      final SszSchema<TRequest> requestType,
      final boolean expectResponseToRequest,
      final List<String> protocolIds,
      final Map<String, SingleProtocolEth2RpcMethod<TRequest, TResponse>> protocolToMethod) {
    super(encoding, requestType, expectResponseToRequest);
    checkArgument(
        protocolToMethod.keySet().containsAll(protocolIds)
            && protocolIds.containsAll(protocolToMethod.keySet()),
        "Supplied protocolIds must match supplied methods");
    this.protocolIds = protocolIds;
    this.protocolToMethod = protocolToMethod;
  }

  /**
   * Creates a versioned RPC method with multiple protocols. Prioritizes later protocol versions
   * over earlier versions
   *
   * @param methodVersions The supported method versions
   * @param <TRequest> The rpc request type
   * @param <TResponse> The rpc response type
   * @return A versioned RPC method
   */
  public static <TRequest extends RpcRequest & SszData, TResponse extends SszData>
      VersionedEth2RpcMethod<TRequest, TResponse> create(
          final RpcEncoding encoding,
          final SszSchema<TRequest> requestType,
          final boolean expectResponseToRequest,
          final List<SingleProtocolEth2RpcMethod<TRequest, TResponse>> methodVersions) {
    // Prioritize methods by version, preferring later versions over earlier versions
    final List<String> sortedProtocolIds =
        methodVersions.stream()
            .sorted(
                Comparator.<SingleProtocolEth2RpcMethod<?, ?>>comparingInt(
                        SingleProtocolEth2RpcMethod::getProtocolVersion)
                    .reversed())
            .map(SingleProtocolEth2RpcMethod::getId)
            .collect(Collectors.toList());
    final Map<String, SingleProtocolEth2RpcMethod<TRequest, TResponse>> protocolIdToMethod =
        methodVersions.stream()
            .collect(Collectors.toMap(SingleProtocolEth2RpcMethod::getId, m -> m));
    return new VersionedEth2RpcMethod<>(
        encoding, requestType, expectResponseToRequest, sortedProtocolIds, protocolIdToMethod);
  }

  @Override
  public List<String> getIds() {
    return protocolIds;
  }

  @Override
  public Eth2IncomingRequestHandler<TRequest, TResponse> createIncomingRequestHandler(
      final String protocolId) {
    final Eth2RpcMethod<TRequest, TResponse> method = protocolToMethod.get(protocolId);
    return method.createIncomingRequestHandler(protocolId);
  }

  @Override
  public Eth2OutgoingRequestHandler<TRequest, TResponse> createOutgoingRequestHandler(
      String protocolId,
      final TRequest request,
      Eth2RpcResponseHandler<TResponse, ?> responseHandler) {
    final Eth2RpcMethod<TRequest, TResponse> method = protocolToMethod.get(protocolId);
    return method.createOutgoingRequestHandler(protocolId, request, responseHandler);
  }
}
