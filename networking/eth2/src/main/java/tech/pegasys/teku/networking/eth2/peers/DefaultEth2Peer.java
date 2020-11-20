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

import static tech.pegasys.teku.networking.eth2.rpc.core.RpcResponseStatus.INVALID_REQUEST_CODE;
import static tech.pegasys.teku.util.config.Constants.MAX_REQUEST_BLOCKS;

import com.google.common.base.MoreObjects;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.BeaconChainMethods;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.BlocksByRangeListenerWrapper;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.MetadataMessagesFactory;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.StatusMessageFactory;
import tech.pegasys.teku.networking.eth2.rpc.core.Eth2OutgoingRequestHandler;
import tech.pegasys.teku.networking.eth2.rpc.core.Eth2RpcMethod;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseCallback;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseStream;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseStreamImpl;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseStreamListener;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.networking.p2p.peer.DelegatingPeer;
import tech.pegasys.teku.networking.p2p.peer.DisconnectReason;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.ssz.SSZTypes.Bitvector;

class DefaultEth2Peer extends DelegatingPeer implements Eth2Peer {
  private static final Logger LOG = LogManager.getLogger();

  private final BeaconChainMethods rpcMethods;
  private final StatusMessageFactory statusMessageFactory;
  private final MetadataMessagesFactory metadataMessagesFactory;
  private final PeerChainValidator peerChainValidator;
  private volatile Optional<PeerStatus> remoteStatus = Optional.empty();
  private volatile Optional<UInt64> remoteMetadataSeqNumber = Optional.empty();
  private volatile Optional<Bitvector> remoteAttSubnets = Optional.empty();
  private final SafeFuture<PeerStatus> initialStatus = new SafeFuture<>();
  private final Subscribers<PeerStatusSubscriber> statusSubscribers = Subscribers.create(true);
  private final AtomicInteger outstandingRequests = new AtomicInteger(0);
  private final AtomicInteger outstandingPings = new AtomicInteger();
  private final RateTracker blockRequestTracker;
  private final RateTracker requestTracker;

  DefaultEth2Peer(
      final Peer peer,
      final BeaconChainMethods rpcMethods,
      final StatusMessageFactory statusMessageFactory,
      final MetadataMessagesFactory metadataMessagesFactory,
      final PeerChainValidator peerChainValidator,
      final RateTracker blockRequestTracker,
      final RateTracker requestTracker) {
    super(peer);
    this.rpcMethods = rpcMethods;
    this.statusMessageFactory = statusMessageFactory;
    this.metadataMessagesFactory = metadataMessagesFactory;
    this.peerChainValidator = peerChainValidator;
    this.blockRequestTracker = blockRequestTracker;
    this.requestTracker = requestTracker;
  }

  @Override
  public void updateStatus(final PeerStatus status) {
    peerChainValidator
        .validate(this, status)
        .finish(
            valid -> {
              if (valid) {
                remoteStatus = Optional.of(status);
                initialStatus.complete(status);
                statusSubscribers.deliver(PeerStatusSubscriber::onPeerStatus, status);
              } // Otherwise will have already been disconnected.
            },
            error -> {
              LOG.error("Failed to validate updated peer status", error);
              disconnectCleanly(DisconnectReason.UNABLE_TO_VERIFY_NETWORK).reportExceptions();
            });
  }

  @Override
  public void updateMetadataSeqNumber(final UInt64 seqNumber) {
    Optional<UInt64> curValue = this.remoteMetadataSeqNumber;
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

  @Override
  public void subscribeInitialStatus(final PeerStatusSubscriber subscriber) {
    initialStatus.finish(
        subscriber::onPeerStatus, error -> LOG.debug("Failed to retrieve initial status", error));
  }

  @Override
  public void subscribeStatusUpdates(final PeerStatusSubscriber subscriber) {
    statusSubscribers.subscribe(subscriber);
  }

  @Override
  public PeerStatus getStatus() {
    return remoteStatus.orElseThrow();
  }

  @Override
  public Optional<Bitvector> getRemoteAttestationSubnets() {
    return remoteAttSubnets;
  }

  @Override
  public UInt64 finalizedEpoch() {
    return getStatus().getFinalizedEpoch();
  }

  @Override
  public Checkpoint finalizedCheckpoint() {
    return getStatus().getFinalizedCheckpoint();
  }

  @Override
  public int getOutstandingRequests() {
    return outstandingRequests.get();
  }

  @Override
  public boolean hasStatus() {
    return remoteStatus.isPresent();
  }

  @Override
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

  @Override
  public SafeFuture<Void> sendGoodbye(final UInt64 reason) {
    final Eth2RpcMethod<GoodbyeMessage, GoodbyeMessage> goodByeMethod = rpcMethods.goodBye();
    return sendMessage(goodByeMethod, new GoodbyeMessage(reason));
  }

  @Override
  public SafeFuture<Void> requestBlocksByRoot(
      final List<Bytes32> blockRoots, final ResponseStreamListener<SignedBeaconBlock> listener)
      throws RpcException {
    if (blockRoots.size() > MAX_REQUEST_BLOCKS) {
      throw new RpcException(
          INVALID_REQUEST_CODE, "Only a maximum of " + MAX_REQUEST_BLOCKS + " can per request");
    }
    final Eth2RpcMethod<BeaconBlocksByRootRequestMessage, SignedBeaconBlock> blockByRoot =
        rpcMethods.beaconBlocksByRoot();
    return requestStream(blockByRoot, new BeaconBlocksByRootRequestMessage(blockRoots), listener);
  }

  @Override
  public SafeFuture<Optional<SignedBeaconBlock>> requestBlockBySlot(final UInt64 slot) {
    final Eth2RpcMethod<BeaconBlocksByRangeRequestMessage, SignedBeaconBlock> blocksByRange =
        rpcMethods.beaconBlocksByRange();
    final BeaconBlocksByRangeRequestMessage request =
        new BeaconBlocksByRangeRequestMessage(slot, UInt64.ONE, UInt64.ONE);
    return requestOptionalItem(blocksByRange, request);
  }

  @Override
  public SafeFuture<Optional<SignedBeaconBlock>> requestBlockByRoot(final Bytes32 blockRoot) {
    final Eth2RpcMethod<BeaconBlocksByRootRequestMessage, SignedBeaconBlock> blockByRoot =
        rpcMethods.beaconBlocksByRoot();
    return requestOptionalItem(
        blockByRoot, new BeaconBlocksByRootRequestMessage(List.of(blockRoot)));
  }

  @Override
  public SafeFuture<Void> requestBlocksByRange(
      final UInt64 startSlot,
      final UInt64 count,
      final UInt64 step,
      final ResponseStreamListener<SignedBeaconBlock> listener) {
    final Eth2RpcMethod<BeaconBlocksByRangeRequestMessage, SignedBeaconBlock> blocksByRange =
        rpcMethods.beaconBlocksByRange();
    return requestStream(
        blocksByRange,
        new BeaconBlocksByRangeRequestMessage(startSlot, count, step),
        new BlocksByRangeListenerWrapper(this, listener, startSlot, count, step));
  }

  @Override
  public SafeFuture<MetadataMessage> requestMetadata() {
    return requestSingleItem(rpcMethods.getMetadata(), EmptyMessage.EMPTY_MESSAGE);
  }

  @Override
  public boolean wantToReceiveObjects(
      final ResponseCallback<SignedBeaconBlock> callback, final long objectCount) {
    if (blockRequestTracker.wantToRequestObjects(objectCount) == 0L) {
      LOG.debug("Peer {} disconnected due to block rate limits", getId());
      callback.completeWithErrorResponse(
          new RpcException(INVALID_REQUEST_CODE, "Peer has been rate limited"));
      disconnectCleanly(DisconnectReason.RATE_LIMITING).reportExceptions();
      return false;
    }
    return true;
  }

  @Override
  public boolean wantToMakeRequest() {
    if (requestTracker.wantToRequestObjects(1L) == 0L) {
      LOG.debug("Peer {} disconnected due to request rate limits", getId());
      disconnectCleanly(DisconnectReason.RATE_LIMITING).reportExceptions();
      return false;
    }
    return true;
  }

  @Override
  public SafeFuture<UInt64> sendPing() {
    outstandingPings.getAndIncrement();
    return requestSingleItem(rpcMethods.ping(), metadataMessagesFactory.createPingMessage())
        .thenApply(PingMessage::getSeqNumber)
        .thenPeek(__ -> outstandingPings.set(0))
        .thenPeek(this::updateMetadataSeqNumber);
  }

  @Override
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

  @Override
  public <I extends RpcRequest, O> SafeFuture<O> requestSingleItem(
      final Eth2RpcMethod<I, O> method, final I request) {
    final Eth2OutgoingRequestHandler<I, O> handler =
        method.createOutgoingRequestHandler(request.getMaximumRequestChunks());
    SafeFuture<O> respFuture = handler.getResponseStream().expectSingleResponse();
    return sendRequest(method, request, handler).thenCompose(__ -> respFuture);
  }

  private <I extends RpcRequest, O> SafeFuture<Optional<O>> requestOptionalItem(
      final Eth2RpcMethod<I, O> method, final I request) {
    final Eth2OutgoingRequestHandler<I, O> handler =
        method.createOutgoingRequestHandler(request.getMaximumRequestChunks());
    SafeFuture<Optional<O>> respFuture = handler.getResponseStream().expectOptionalResponse();
    return sendRequest(method, request, handler).thenCompose(__ -> respFuture);
  }

  private <I extends RpcRequest, O> SafeFuture<Void> requestStream(
      final Eth2RpcMethod<I, O> method, final I request, final ResponseStreamListener<O> listener) {
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
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    final DefaultEth2Peer that = (DefaultEth2Peer) o;
    return Objects.equals(rpcMethods, that.rpcMethods);
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
}
