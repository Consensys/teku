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

import com.google.common.primitives.UnsignedLong;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.BeaconBlocksByRangeRequestMessage;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.BeaconBlocksByRootRequestMessage;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.GoodbyeMessage;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.RpcRequest;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.StatusMessage;
import tech.pegasys.artemis.networking.eth2.rpc.beaconchain.BeaconChainMethods;
import tech.pegasys.artemis.networking.eth2.rpc.beaconchain.methods.StatusMessageFactory;
import tech.pegasys.artemis.networking.eth2.rpc.core.Eth2OutgoingRequestHandler;
import tech.pegasys.artemis.networking.eth2.rpc.core.Eth2RpcMethod;
import tech.pegasys.artemis.networking.eth2.rpc.core.ResponseStream;
import tech.pegasys.artemis.networking.eth2.rpc.core.ResponseStream.ResponseListener;
import tech.pegasys.artemis.networking.p2p.peer.DelegatingPeer;
import tech.pegasys.artemis.networking.p2p.peer.Peer;
import tech.pegasys.artemis.util.async.SafeFuture;

public class Eth2Peer extends DelegatingPeer implements Peer {
  private final BeaconChainMethods rpcMethods;
  private final StatusMessageFactory statusMessageFactory;
  private volatile Optional<PeerStatus> remoteStatus = Optional.empty();
  private final SafeFuture<PeerStatus> initialStatus = new SafeFuture<>();
  private AtomicBoolean chainValidated = new AtomicBoolean(false);

  public Eth2Peer(
      final Peer peer,
      final BeaconChainMethods rpcMethods,
      final StatusMessageFactory statusMessageFactory) {
    super(peer);
    this.rpcMethods = rpcMethods;
    this.statusMessageFactory = statusMessageFactory;
  }

  public void updateStatus(final PeerStatus status) {
    remoteStatus = Optional.of(status);
    initialStatus.complete(status);
  }

  public void subscribeInitialStatus(final InitialStatusSubscriber subscriber) {
    initialStatus.finish(subscriber::onInitialStatus);
  }

  public PeerStatus getStatus() {
    return remoteStatus.orElseThrow();
  }

  public boolean hasStatus() {
    return remoteStatus.isPresent();
  }

  boolean isChainValidated() {
    return chainValidated.get();
  }

  void markChainValidated() {
    chainValidated.set(true);
  }

  public SafeFuture<PeerStatus> sendStatus() {
    final Eth2RpcMethod<StatusMessage, StatusMessage> statusMethod = rpcMethods.status();
    return sendRequest(statusMethod, statusMessageFactory.createStatusMessage())
        .thenCompose(ResponseStream::expectSingleResponse)
        .thenApply(
            remoteStatus -> {
              final PeerStatus status = PeerStatus.fromStatusMessage(remoteStatus);
              updateStatus(status);
              return getStatus();
            });
  }

  public SafeFuture<Void> sendGoodbye(final UnsignedLong reason) {
    final Eth2RpcMethod<GoodbyeMessage, GoodbyeMessage> goodByeMethod = rpcMethods.goodBye();
    return sendRequest(goodByeMethod, new GoodbyeMessage(reason))
        .thenCompose(ResponseStream::expectNoResponse);
  }

  public SafeFuture<Void> requestBlocksByRoot(
      final List<Bytes32> blockRoots, final ResponseListener<BeaconBlock> listener) {
    final Eth2RpcMethod<BeaconBlocksByRootRequestMessage, BeaconBlock> blockByRoot =
        rpcMethods.beaconBlocksByRoot();
    return requestStream(blockByRoot, new BeaconBlocksByRootRequestMessage(blockRoots), listener);
  }

  public SafeFuture<BeaconBlock> requestBlockBySlot(
      final Bytes32 headBlockRoot, final UnsignedLong slot) {
    final Eth2RpcMethod<BeaconBlocksByRangeRequestMessage, BeaconBlock> blocksByRange =
        rpcMethods.beaconBlocksByRange();
    final BeaconBlocksByRangeRequestMessage request =
        new BeaconBlocksByRangeRequestMessage(
            headBlockRoot, slot, UnsignedLong.ONE, UnsignedLong.ONE);
    return sendRequest(blocksByRange, request).thenCompose(ResponseStream::expectSingleResponse);
  }

  public SafeFuture<Void> requestBlocksByRange(
      final Bytes32 headBlockRoot,
      final UnsignedLong startSlot,
      final UnsignedLong count,
      final UnsignedLong step,
      final ResponseListener<BeaconBlock> listener) {
    final Eth2RpcMethod<BeaconBlocksByRangeRequestMessage, BeaconBlock> blocksByRange =
        rpcMethods.beaconBlocksByRange();
    return requestStream(
        blocksByRange,
        new BeaconBlocksByRangeRequestMessage(headBlockRoot, startSlot, count, step),
        listener);
  }

  private <I extends RpcRequest, O> SafeFuture<Void> requestStream(
      final Eth2RpcMethod<I, O> method,
      I request,
      final ResponseStream.ResponseListener<O> listener) {
    return sendRequest(method, request)
        .thenCompose(responseStream -> responseStream.expectMultipleResponses(listener));
  }

  public <I extends RpcRequest, O> SafeFuture<ResponseStream<O>> sendRequest(
      final Eth2RpcMethod<I, O> method, I request) {
    Bytes payload = method.encodeRequest(request);
    final Eth2OutgoingRequestHandler<I, O> handler =
        method.createOutgoingRequestHandler(request.getMaximumRequestChunks());
    return this.sendRequest(method, payload, handler)
        .thenAccept(handler::handleInitialPayloadSent)
        .thenApply(res -> handler.getResponseStream());
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof Eth2Peer)) {
      return false;
    }
    final Eth2Peer eth2Peer = (Eth2Peer) o;
    return Objects.equals(rpcMethods, eth2Peer.rpcMethods) && super.equals(o);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), rpcMethods);
  }

  public interface InitialStatusSubscriber {
    void onInitialStatus(final PeerStatus initialStatus);
  }
}
