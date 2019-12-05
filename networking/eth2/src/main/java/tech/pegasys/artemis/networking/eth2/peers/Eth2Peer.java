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

package tech.pegasys.artemis.networking.eth2.peers;

import com.google.common.base.MoreObjects;
import com.google.common.primitives.UnsignedLong;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.BeaconBlocksByRangeRequestMessage;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.BeaconBlocksByRootRequestMessage;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.GoodbyeMessage;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.RpcRequest;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.StatusMessage;
import tech.pegasys.artemis.networking.eth2.rpc.beaconchain.BeaconChainMethods;
import tech.pegasys.artemis.networking.eth2.rpc.beaconchain.methods.StatusMessageFactory;
import tech.pegasys.artemis.networking.eth2.rpc.core.ResponseStream;
import tech.pegasys.artemis.networking.eth2.rpc.core.ResponseStream.ResponseListener;
import tech.pegasys.artemis.networking.eth2.rpc.core.RpcMethod;
import tech.pegasys.artemis.networking.eth2.rpc.core.RpcMethods;
import tech.pegasys.artemis.networking.p2p.peer.DelegatingPeer;
import tech.pegasys.artemis.networking.p2p.peer.Peer;
import tech.pegasys.artemis.util.SSZTypes.Bytes4;

public class Eth2Peer extends DelegatingPeer implements Peer {
  private final RpcMethods rpcMethods;
  private final StatusMessageFactory statusMessageFactory;
  private volatile Optional<StatusData> remoteStatus = Optional.empty();

  public Eth2Peer(
      final Peer peer,
      final RpcMethods rpcMethods,
      final StatusMessageFactory statusMessageFactory) {
    super(peer);
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

  public CompletableFuture<StatusData> sendStatus() {
    return sendRequest(BeaconChainMethods.STATUS, statusMessageFactory.createStatusMessage())
        .thenCompose(ResponseStream::expectSingleResponse)
        .thenApply(
            remoteStatus -> {
              updateStatus(remoteStatus);
              return getStatus();
            });
  }

  public CompletableFuture<Void> sendGoodbye(final UnsignedLong reason) {
    return sendRequest(BeaconChainMethods.GOODBYE, new GoodbyeMessage(reason))
        .thenCompose(ResponseStream::expectNoResponse);
  }

  public CompletableFuture<Void> requestBlocksByRoot(
      final List<Bytes32> blockRoots, final ResponseListener<BeaconBlock> listener) {
    return requestStream(
        BeaconChainMethods.BEACON_BLOCKS_BY_ROOT,
        new BeaconBlocksByRootRequestMessage(blockRoots),
        listener);
  }

  public CompletableFuture<Void> requestBlocksByRange(
      final Bytes32 headBlockRoot,
      final UnsignedLong startSlot,
      final UnsignedLong count,
      final UnsignedLong step,
      final ResponseListener<BeaconBlock> listener) {
    return requestStream(
        BeaconChainMethods.BEACON_BLOCKS_BY_RANGE,
        new BeaconBlocksByRangeRequestMessage(headBlockRoot, startSlot, count, step),
        listener);
  }

  private <I extends RpcRequest, O> CompletableFuture<Void> requestStream(
      final RpcMethod<I, O> method, I request, final ResponseStream.ResponseListener<O> listener) {
    return sendRequest(method, request)
        .thenCompose(responseStream -> responseStream.expectMultipleResponses(listener));
  }

  public <I extends RpcRequest, O> CompletableFuture<ResponseStream<O>> sendRequest(
      final RpcMethod<I, O> method, I request) {
    return rpcMethods.invoke(method, this.getConnection(), request);
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
