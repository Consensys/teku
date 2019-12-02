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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.BeaconBlocksByRootRequestMessage;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.GoodbyeMessage;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.RpcRequest;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.StatusMessage;
import tech.pegasys.artemis.networking.p2p.jvmlibp2p.rpc.ResponseStream;
import tech.pegasys.artemis.networking.p2p.jvmlibp2p.rpc.ResponseStream.ResponseListener;
import tech.pegasys.artemis.networking.p2p.jvmlibp2p.rpc.RpcMethod;
import tech.pegasys.artemis.networking.p2p.jvmlibp2p.rpc.RpcMethods;
import tech.pegasys.artemis.networking.p2p.jvmlibp2p.rpc.methods.StatusMessageFactory;
import tech.pegasys.artemis.util.SSZTypes.Bytes4;
import tech.pegasys.artemis.util.sos.SimpleOffsetSerializable;

public class Peer {
  private final Connection connection;
  private final RpcMethods rpcMethods;
  private final StatusMessageFactory statusMessageFactory;
  private volatile Optional<StatusData> remoteStatus = Optional.empty();

  public Peer(
      final Connection connection,
      final RpcMethods rpcMethods,
      final StatusMessageFactory statusMessageFactory) {
    this.connection = connection;
    this.rpcMethods = rpcMethods;
    this.statusMessageFactory = statusMessageFactory;
  }

  public void updateStatus(final StatusMessage message) {
    remoteStatus = Optional.of(StatusData.fromStatusMessage(message));
  }

  public StatusData getStatus() {
    return remoteStatus.orElseThrow();
  }

  public boolean hasStatus() {
    return remoteStatus.isPresent();
  }

  public PeerId getPeerId() {
    return connection.getSecureSession().getRemoteId();
  }

  public boolean isConnected() {
    return connection.getNettyChannel().isOpen();
  }

  public CompletableFuture<StatusData> sendStatus() {
    return invoke(RpcMethod.STATUS, statusMessageFactory.createStatusMessage())
        .thenCompose(ResponseStream::expectSingleResponse)
        .thenApply(
            remoteStatus -> {
              updateStatus(remoteStatus);
              return getStatus();
            });
  }

  public CompletableFuture<Void> sendGoodbye(final UnsignedLong reason) {
    return invoke(RpcMethod.GOODBYE, new GoodbyeMessage(reason))
        .thenCompose(ResponseStream::expectNoResponse);
  }

  public CompletableFuture<Void> requestBlocksByRoot(
      final List<Bytes32> blockRoots, final ResponseListener<BeaconBlock> listener) {
    return requestStream(
        RpcMethod.BEACON_BLOCKS_BY_ROOT,
        new BeaconBlocksByRootRequestMessage(blockRoots),
        listener);
  }

  private <I extends RpcRequest, O extends SimpleOffsetSerializable>
      CompletableFuture<Void> requestStream(
          final RpcMethod<I, O> method,
          I request,
          final ResponseStream.ResponseListener<O> listener) {
    return invoke(method, request)
        .thenCompose(responseStream -> responseStream.expectMultipleResponses(listener));
  }

  <I extends RpcRequest, O extends SimpleOffsetSerializable>
      CompletableFuture<ResponseStream<O>> invoke(final RpcMethod<I, O> method, I request) {
    return rpcMethods.invoke(method, connection, request);
  }

  public static class StatusData {
    private final Bytes4 headForkVersion;
    private final Bytes32 finalizedRoot;
    private final UnsignedLong finalizedEpoch;
    private final Bytes32 headRoot;
    private final UnsignedLong headSlot;

    public static StatusData fromStatusMessage(final StatusMessage message) {
      return new StatusData(
          message.getHeadForkVersion().copy(),
          message.getFinalizedRoot().copy(),
          message.getFinalizedEpoch(),
          message.getHeadRoot().copy(),
          message.getHeadSlot());
    }

    private StatusData(
        final Bytes4 headForkVersion,
        final Bytes32 finalizedRoot,
        final UnsignedLong finalizedEpoch,
        final Bytes32 headRoot,
        final UnsignedLong headSlot) {
      this.headForkVersion = headForkVersion;
      this.finalizedRoot = finalizedRoot;
      this.finalizedEpoch = finalizedEpoch;
      this.headRoot = headRoot;
      this.headSlot = headSlot;
    }

    public Bytes4 getHeadForkVersion() {
      return headForkVersion;
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
          .add("currentFork", headForkVersion)
          .add("finalizedRoot", finalizedRoot)
          .add("finalizedEpoch", finalizedEpoch)
          .add("headRoot", headRoot)
          .add("headSlot", headSlot)
          .toString();
    }
  }
}
