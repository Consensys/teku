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

package tech.pegasys.teku.networking.eth2.peers;

import com.google.common.base.MoreObjects;
import com.google.common.primitives.UnsignedLong;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.BeaconBlocksByRangeRequestMessage;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.BeaconBlocksByRootRequestMessage;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.EmptyMessage;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.GoodbyeMessage;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.MetadataMessage;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.PingMessage;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.RpcRequest;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.StatusMessage;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.BeaconChainMethods;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.MetadataMessagesFactory;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.StatusMessageFactory;
import tech.pegasys.teku.networking.eth2.rpc.core.Eth2OutgoingRequestHandler;
import tech.pegasys.teku.networking.eth2.rpc.core.Eth2RpcMethod;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseStream;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseStream.ResponseListener;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseStreamImpl;
import tech.pegasys.teku.networking.p2p.peer.DelegatingPeer;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.ssz.SSZTypes.Bitvector;
import tech.pegasys.teku.util.async.SafeFuture;

public class Eth2Peer extends DelegatingPeer implements Peer {
  private static final Logger LOG = LogManager.getLogger();

  private final BeaconChainMethods rpcMethods;
  private final StatusMessageFactory statusMessageFactory;
  private final MetadataMessagesFactory metadataMessagesFactory;
  private volatile Optional<PeerStatus> remoteStatus = Optional.empty();
  private volatile Optional<UnsignedLong> remoteMetadataSeqNumber = Optional.empty();
  private volatile Optional<Bitvector> remoteAttSubnets = Optional.empty();
  private final SafeFuture<PeerStatus> initialStatus = new SafeFuture<>();
  private final AtomicBoolean chainValidated = new AtomicBoolean(false);
  private final AtomicInteger outstandingRequests = new AtomicInteger(0);
  private final AtomicInteger outstandingPings = new AtomicInteger();

  public Eth2Peer(
      final Peer peer,
      final BeaconChainMethods rpcMethods,
      final StatusMessageFactory statusMessageFactory,
      final MetadataMessagesFactory metadataMessagesFactory) {
    super(peer);
    this.rpcMethods = rpcMethods;
    this.statusMessageFactory = statusMessageFactory;
    this.metadataMessagesFactory = metadataMessagesFactory;
  }

  public void updateStatus(final PeerStatus status) {
    remoteStatus = Optional.of(status);
    initialStatus.complete(status);
  }

  public void updateMetadataSeqNumber(final UnsignedLong seqNumber) {
    Optional<UnsignedLong> curValue = this.remoteMetadataSeqNumber;
    remoteMetadataSeqNumber = Optional.of(seqNumber);
    if (curValue.isEmpty() || seqNumber.compareTo(curValue.get()) > 0) {
      requestMetadata()
          .finish(
              this::updateMetadata, error -> LOG.debug("Failed to retrieve peer metadata", error));
    }
  }

  private void updateMetadata(final MetadataMessage metadataMessage) {
    remoteMetadataSeqNumber = Optional.of(metadataMessage.getSeqNumber());
    remoteAttSubnets = Optional.of(metadataMessage.getAttnets());
  }

  public void subscribeInitialStatus(final InitialStatusSubscriber subscriber) {
    initialStatus.finish(
        subscriber::onInitialStatus,
        error -> LOG.debug("Failed to retrieve initial status", error));
  }

  public PeerStatus getStatus() {
    return remoteStatus.orElseThrow();
  }

  public Optional<Bitvector> getRemoteAttestationSubnets() {
    return remoteAttSubnets;
  }

  public UnsignedLong finalizedEpoch() {
    return getStatus().getFinalizedEpoch();
  }

  public int getOutstandingRequests() {
    return outstandingRequests.get();
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
    final Optional<StatusMessage> statusMessage = statusMessageFactory.createStatusMessage();
    if (statusMessage.isEmpty()) {
      final Exception error =
          new IllegalStateException("Unable to generate local status message.  Node is not ready.");
      return SafeFuture.failedFuture(error);
    }

    return requestSingleItem(rpcMethods.status(), statusMessage.get())
        .thenApply(PeerStatus::fromStatusMessage)
        .thenPeek(this::updateStatus);
  }

  SafeFuture<Void> sendGoodbye(final UnsignedLong reason) {
    final Eth2RpcMethod<GoodbyeMessage, GoodbyeMessage> goodByeMethod = rpcMethods.goodBye();
    return sendMessage(goodByeMethod, new GoodbyeMessage(reason));
  }

  public SafeFuture<Void> requestBlocksByRoot(
      final List<Bytes32> blockRoots, final ResponseListener<SignedBeaconBlock> listener) {
    final Eth2RpcMethod<BeaconBlocksByRootRequestMessage, SignedBeaconBlock> blockByRoot =
        rpcMethods.beaconBlocksByRoot();
    return requestStream(blockByRoot, new BeaconBlocksByRootRequestMessage(blockRoots), listener);
  }

  public SafeFuture<SignedBeaconBlock> requestBlockBySlot(final UnsignedLong slot) {
    final Eth2RpcMethod<BeaconBlocksByRangeRequestMessage, SignedBeaconBlock> blocksByRange =
        rpcMethods.beaconBlocksByRange();
    final BeaconBlocksByRangeRequestMessage request =
        new BeaconBlocksByRangeRequestMessage(slot, UnsignedLong.ONE, UnsignedLong.ONE);
    return requestSingleItem(blocksByRange, request);
  }

  public SafeFuture<SignedBeaconBlock> requestBlockByRoot(final Bytes32 blockRoot) {
    final Eth2RpcMethod<BeaconBlocksByRootRequestMessage, SignedBeaconBlock> blockByRoot =
        rpcMethods.beaconBlocksByRoot();
    return requestSingleItem(blockByRoot, new BeaconBlocksByRootRequestMessage(List.of(blockRoot)));
  }

  public SafeFuture<Void> requestBlocksByRange(
      final UnsignedLong startSlot,
      final UnsignedLong count,
      final UnsignedLong step,
      final ResponseListener<SignedBeaconBlock> listener) {
    final Eth2RpcMethod<BeaconBlocksByRangeRequestMessage, SignedBeaconBlock> blocksByRange =
        rpcMethods.beaconBlocksByRange();
    return requestStream(
        blocksByRange, new BeaconBlocksByRangeRequestMessage(startSlot, count, step), listener);
  }

  public SafeFuture<MetadataMessage> requestMetadata() {
    return requestSingleItem(rpcMethods.getMetadata(), EmptyMessage.EMPTY_MESSAGE);
  }

  public SafeFuture<UnsignedLong> sendPing() {
    outstandingPings.getAndIncrement();
    return requestSingleItem(rpcMethods.ping(), metadataMessagesFactory.createPingMessage())
        .thenApply(PingMessage::getSeqNumber)
        .thenPeek(__ -> outstandingPings.set(0))
        .thenPeek(this::updateMetadataSeqNumber);
  }

  public int getOutstandingPings() {
    return outstandingPings.get();
  }

  private <I extends RpcRequest, O> SafeFuture<Void> sendMessage(
      final Eth2RpcMethod<I, O> method, final I request) {
    final Eth2OutgoingRequestHandler<I, O> handler =
        method.createOutgoingRequestHandler(request.getMaximumRequestChunks());
    SafeFuture<Void> respFuture = handler.getResponseStream().expectNoResponse();
    return sendRequest(method, request, handler).thenCompose(__ -> respFuture);
  }

  public <I extends RpcRequest, O> SafeFuture<O> requestSingleItem(
      final Eth2RpcMethod<I, O> method, final I request) {
    final Eth2OutgoingRequestHandler<I, O> handler =
        method.createOutgoingRequestHandler(request.getMaximumRequestChunks());
    SafeFuture<O> respFuture = handler.getResponseStream().expectSingleResponse();
    return sendRequest(method, request, handler).thenCompose(__ -> respFuture);
  }

  private <I extends RpcRequest, O> SafeFuture<Void> requestStream(
      final Eth2RpcMethod<I, O> method,
      final I request,
      final ResponseStream.ResponseListener<O> listener) {
    final Eth2OutgoingRequestHandler<I, O> handler =
        method.createOutgoingRequestHandler(request.getMaximumRequestChunks());
    SafeFuture<Void> respFuture = handler.getResponseStream().expectMultipleResponses(listener);
    return sendRequest(method, request, handler).thenCompose(__ -> respFuture);
  }

  private <I extends RpcRequest, O> SafeFuture<ResponseStream<O>> sendRequest(
      final Eth2RpcMethod<I, O> method, final I request, Eth2OutgoingRequestHandler<I, O> handler) {
    Bytes payload = method.encodeRequest(request);
    return this.sendRequest(method, payload, handler)
        .thenAccept(handler::handleInitialPayloadSent)
        .thenApply(
            res -> {
              final ResponseStreamImpl<O> stream = handler.getResponseStream();
              outstandingRequests.incrementAndGet();
              stream.subscribeCompleted((__) -> outstandingRequests.decrementAndGet());
              return stream;
            });
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

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("id", getId())
        .add("remoteStatus", remoteStatus)
        .toString();
  }

  public interface InitialStatusSubscriber {
    void onInitialStatus(final PeerStatus initialStatus);
  }
}
