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

package tech.pegasys.teku.networking.p2p.peer;

import java.util.Objects;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.p2p.network.PeerAddress;
import tech.pegasys.teku.networking.p2p.reputation.ReputationAdjustment;
import tech.pegasys.teku.networking.p2p.rpc.RpcMethod;
import tech.pegasys.teku.networking.p2p.rpc.RpcRequestHandler;
import tech.pegasys.teku.networking.p2p.rpc.RpcStream;

public interface Peer {

  default NodeId getId() {
    return getAddress().getId();
  }

  PeerAddress getAddress();

  boolean isConnected();

  void disconnectImmediately(Optional<DisconnectReason> reason, boolean locallyInitiated);

  SafeFuture<Void> disconnectCleanly(DisconnectReason reason);

  void setDisconnectRequestHandler(DisconnectRequestHandler handler);

  void subscribeDisconnect(PeerDisconnectedSubscriber subscriber);

  SafeFuture<RpcStream> sendRequest(
      RpcMethod rpcMethod, Bytes initialPayload, RpcRequestHandler handler);

  boolean connectionInitiatedLocally();

  boolean connectionInitiatedRemotely();

  default boolean idMatches(final Peer other) {
    return other != null && Objects.equals(getId(), other.getId());
  }

  void adjustReputation(final ReputationAdjustment adjustment);
}
