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

package tech.pegasys.artemis.networking.p2p.jvmlibp2p;

import com.google.common.base.MoreObjects;
import com.google.common.primitives.UnsignedLong;
import io.libp2p.core.Connection;
import io.libp2p.core.PeerId;
import io.libp2p.core.multiformats.Multiaddr;
import java.util.concurrent.CompletableFuture;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.StatusMessage;
import tech.pegasys.artemis.networking.p2p.jvmlibp2p.rpc.RpcMethod;
import tech.pegasys.artemis.networking.p2p.jvmlibp2p.rpc.RpcMethods;
import tech.pegasys.artemis.util.SSZTypes.Bytes4;
import tech.pegasys.artemis.util.sos.SimpleOffsetSerializable;

public class Peer {
  private final Connection connection;
  private final Multiaddr multiaddr;
  private final RpcMethods rpcMethods;
  private final PeerId peerId;
  private final CompletableFuture<StatusData> remoteStatus = new CompletableFuture<>();

  public Peer(Connection connection, RpcMethods rpcMethods) {
    this.connection = connection;
    this.peerId = connection.getSecureSession().getRemoteId();
    this.multiaddr =
        new Multiaddr(connection.remoteAddress().toString() + "/p2p/" + peerId.toString());
    this.rpcMethods = rpcMethods;
  }

  public void receivedStatusMessage(final StatusMessage message) {
    remoteStatus.complete(StatusData.fromStatusMessage(message));
  }

  public StatusData getStatus() {
    return remoteStatus.getNow(null);
  }

  public PeerId getPeerId() {
    return peerId;
  }

  public Multiaddr getPeerMultiaddr() {
    return multiaddr;
  }

  public PeerId getRemoteId() {
    return connection.getSecureSession().getRemoteId();
  }

  public boolean isInitiator() {
    return connection.isInitiator();
  }

  public <I extends SimpleOffsetSerializable, O extends SimpleOffsetSerializable>
      CompletableFuture<O> send(final RpcMethod<I, O> method, I request) {
    return rpcMethods.invoke(method, connection, request);
  }

  public boolean hasReceivedHello() {
    return remoteStatus.isDone() && !remoteStatus.isCompletedExceptionally();
  }

  public static class StatusData {
    private final Bytes4 currentFork;
    private final Bytes32 finalizedRoot;
    private final UnsignedLong finalizedEpoch;
    private final Bytes32 headRoot;
    private final UnsignedLong headSlot;

    public static StatusData fromStatusMessage(final StatusMessage message) {
      return new StatusData(
          message.getForkVersion().copy(),
          message.getFinalizedRoot().copy(),
          message.getFinalizedEpoch(),
          message.getHeadRoot().copy(),
          message.getHeadSlot());
    }

    private StatusData(
        final Bytes4 currentFork,
        final Bytes32 finalizedRoot,
        final UnsignedLong finalizedEpoch,
        final Bytes32 headRoot,
        final UnsignedLong headSlot) {
      this.currentFork = currentFork;
      this.finalizedRoot = finalizedRoot;
      this.finalizedEpoch = finalizedEpoch;
      this.headRoot = headRoot;
      this.headSlot = headSlot;
    }

    public Bytes4 getForkVersion() {
      return currentFork;
    }

    public Bytes32 getFinalizedRoot() {
      return finalizedRoot;
    }

    public UnsignedLong getFinalizedEpoch() {
      return finalizedEpoch;
    }

    public Bytes32 getHeadRoot() {
      return headRoot;
    }

    public UnsignedLong getHeadSlot() {
      return headSlot;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("currentFork", currentFork)
          .add("finalizedRoot", finalizedRoot)
          .add("finalizedEpoch", finalizedEpoch)
          .add("headRoot", headRoot)
          .add("headSlot", headSlot)
          .toString();
    }
  }
}
