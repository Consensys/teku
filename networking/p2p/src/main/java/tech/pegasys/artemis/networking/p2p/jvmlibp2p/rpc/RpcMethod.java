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

import java.util.Objects;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.BeaconBlocksMessageRequest;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.BeaconBlocksMessageResponse;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.GoodbyeMessage;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.StatusMessage;
import tech.pegasys.artemis.util.sos.SimpleOffsetSerializable;

public class RpcMethod<I extends SimpleOffsetSerializable, O> {

  public static final RpcMethod<StatusMessage, StatusMessage> STATUS =
      new RpcMethod<>(
          "/eth2/beacon_chain/req/status/1/ssz", StatusMessage.class, StatusMessage.class);
  public static final RpcMethod<GoodbyeMessage, Void> GOODBYE =
      new RpcMethod<>("/eth2/beacon_chain/req/goodbye/1/ssz", GoodbyeMessage.class, Void.class);
  public static final RpcMethod<BeaconBlocksMessageRequest, BeaconBlocksMessageResponse>
      BEACON_BLOCKS =
          new RpcMethod<>(
              "/eth2/beacon_chain/req/beacon_blocks/1/ssz",
              BeaconBlocksMessageRequest.class,
              BeaconBlocksMessageResponse.class);

  private final String methodMultistreamId;
  private final Class<I> requestType;
  private final Class<O> responseType;

  RpcMethod(
      final String methodMultistreamId, final Class<I> requestType, final Class<O> responseType) {
    this.methodMultistreamId = methodMultistreamId;
    this.requestType = requestType;
    this.responseType = responseType;
  }

  public String getMultistreamId() {
    return methodMultistreamId;
  }

  public Class<I> getRequestType() {
    return requestType;
  }

  public Class<O> getResponseType() {
    return responseType;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final RpcMethod<?, ?> rpcMethod = (RpcMethod<?, ?>) o;
    return methodMultistreamId.equals(rpcMethod.methodMultistreamId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(methodMultistreamId);
  }
}
