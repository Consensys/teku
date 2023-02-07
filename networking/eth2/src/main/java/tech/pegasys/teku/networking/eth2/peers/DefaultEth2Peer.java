/*
 * Copyright ConsenSys Software Inc., 2022
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

import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.networking.eth2.rpc.core.RpcResponseStatus.INVALID_REQUEST_CODE;
import static tech.pegasys.teku.spec.config.Constants.MAX_REQUEST_BLOCKS;

import com.google.common.base.MoreObjects;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.BeaconChainMethods;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.BlocksByRangeListenerWrapper;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.MetadataMessagesFactory;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.StatusMessageFactory;
import tech.pegasys.teku.networking.eth2.rpc.core.Eth2RpcResponseHandler;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseCallback;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.networking.eth2.rpc.core.methods.Eth2RpcMethod;
import tech.pegasys.teku.networking.p2p.peer.DelegatingPeer;
import tech.pegasys.teku.networking.p2p.peer.DisconnectReason;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.networking.p2p.rpc.RpcResponseListener;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.SpecConfigEip4844;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.eip4844.SignedBeaconBlockAndBlobsSidecar;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip4844.BlobsSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BeaconBlockAndBlobsSidecarByRootRequestMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BeaconBlocksByRangeRequestMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BeaconBlocksByRootRequestMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobsSidecarsByRangeRequestMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.EmptyMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.GoodbyeMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.PingMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.RpcRequest;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.StatusMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.MetadataMessage;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;

class DefaultEth2Peer extends DelegatingPeer implements Eth2Peer {
  private static final Logger LOG = LogManager.getLogger();

  private final BeaconChainMethods rpcMethods;
  private final StatusMessageFactory statusMessageFactory;
  private final MetadataMessagesFactory metadataMessagesFactory;
  private final PeerChainValidator peerChainValidator;
  private volatile Optional<PeerStatus> remoteStatus = Optional.empty();
  private volatile Optional<UInt64> remoteMetadataSeqNumber = Optional.empty();
  private volatile Optional<SszBitvector> remoteAttSubnets = Optional.empty();
  private final SafeFuture<PeerStatus> initialStatus = new SafeFuture<>();
  private final Subscribers<PeerStatusSubscriber> statusSubscribers = Subscribers.create(true);
  private final AtomicInteger outstandingRequests = new AtomicInteger(0);
  private final AtomicInteger unansweredPings = new AtomicInteger();
  private final RateTracker blockRequestTracker;
  private final RateTracker blobsSidecarsRequestTracker;
  private final RateTracker requestTracker;
  private final Supplier<UInt64> eip4844StartSlotSupplier;

  DefaultEth2Peer(
      final Spec spec,
      final Peer peer,
      final BeaconChainMethods rpcMethods,
      final StatusMessageFactory statusMessageFactory,
      final MetadataMessagesFactory metadataMessagesFactory,
      final PeerChainValidator peerChainValidator,
      final RateTracker blockRequestTracker,
      final RateTracker blobsSidecarsRequestTracker,
      final RateTracker requestTracker) {
    super(peer);
    this.rpcMethods = rpcMethods;
    this.statusMessageFactory = statusMessageFactory;
    this.metadataMessagesFactory = metadataMessagesFactory;
    this.peerChainValidator = peerChainValidator;
    this.blockRequestTracker = blockRequestTracker;
    this.blobsSidecarsRequestTracker = blobsSidecarsRequestTracker;
    this.requestTracker = requestTracker;
    this.eip4844StartSlotSupplier =
        Suppliers.memoize(
            () -> {
              final UInt64 eip4844ForkEpoch =
                  SpecConfigEip4844.required(spec.forMilestone(SpecMilestone.EIP4844).getConfig())
                      .getEip4844ForkEpoch();
              return spec.computeStartSlotAtEpoch(eip4844ForkEpoch);
            });
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
                checkPeerIdentity();
                statusSubscribers.deliver(PeerStatusSubscriber::onPeerStatus, status);
              } // Otherwise will have already been disconnected.
            },
            error -> {
              LOG.error("Failed to validate updated peer status", error);
              disconnectCleanly(DisconnectReason.UNABLE_TO_VERIFY_NETWORK)
                  .ifExceptionGetsHereRaiseABug();
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
  public Optional<SszBitvector> getRemoteAttestationSubnets() {
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
    LOG.trace("Sending status message {} to {}", statusMessage.get(), getAddress());

    return requestSingleItem(rpcMethods.status(), statusMessage.get())
        .thenApply(PeerStatus::fromStatusMessage)
        .thenPeek(this::updateStatus);
  }

  @Override
  public SafeFuture<Void> sendGoodbye(final UInt64 reason) {
    final Eth2RpcMethod<GoodbyeMessage, GoodbyeMessage> goodByeMethod = rpcMethods.goodBye();
    return requestOptionalItem(goodByeMethod, new GoodbyeMessage(reason)).toVoid();
  }

  @Override
  public SafeFuture<Void> requestBlocksByRoot(
      final List<Bytes32> blockRoots, final RpcResponseListener<SignedBeaconBlock> listener)
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
  public SafeFuture<Void> requestBlockAndBlobsSidecarByRoot(
      final List<Bytes32> blockRoots,
      final RpcResponseListener<SignedBeaconBlockAndBlobsSidecar> listener)
      throws RpcException {
    if (blockRoots.size() > MAX_REQUEST_BLOCKS) {
      throw new RpcException(
          INVALID_REQUEST_CODE, "Only a maximum of " + MAX_REQUEST_BLOCKS + " can per request");
    }
    return rpcMethods
        .beaconBlockAndBlobsSidecarByRoot()
        .map(
            method ->
                requestStream(
                    method,
                    new BeaconBlockAndBlobsSidecarByRootRequestMessage(blockRoots),
                    listener))
        .orElse(
            SafeFuture.failedFuture(
                new UnsupportedOperationException(
                    "BlockAndBlobsSidecarByRoot method is not available")));
  }

  @Override
  public SafeFuture<Optional<SignedBeaconBlock>> requestBlockBySlot(final UInt64 slot) {
    final Eth2RpcMethod<BeaconBlocksByRangeRequestMessage, SignedBeaconBlock> blocksByRange =
        rpcMethods.beaconBlocksByRange();
    final BeaconBlocksByRangeRequestMessage request =
        new BeaconBlocksByRangeRequestMessage(slot, ONE, ONE);
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
  public SafeFuture<Optional<SignedBeaconBlockAndBlobsSidecar>> requestBlockAndBlobsSidecarByRoot(
      final Bytes32 blockRoot) {
    return rpcMethods
        .beaconBlockAndBlobsSidecarByRoot()
        .map(
            method ->
                requestOptionalItem(
                    method, new BeaconBlockAndBlobsSidecarByRootRequestMessage(List.of(blockRoot))))
        .orElse(
            SafeFuture.failedFuture(
                new UnsupportedOperationException(
                    "BlockAndBlobsSidecarByRoot method is not available")));
  }

  @Override
  public SafeFuture<Void> requestBlocksByRange(
      final UInt64 startSlot,
      final UInt64 count,
      final RpcResponseListener<SignedBeaconBlock> listener) {
    final Eth2RpcMethod<BeaconBlocksByRangeRequestMessage, SignedBeaconBlock> blocksByRange =
        rpcMethods.beaconBlocksByRange();
    return requestStream(
        blocksByRange,
        new BeaconBlocksByRangeRequestMessage(startSlot, count, ONE),
        new BlocksByRangeListenerWrapper(this, listener, startSlot, count));
  }

  @Override
  public SafeFuture<Void> requestBlobsSidecarsByRange(
      final UInt64 startSlot,
      final UInt64 count,
      final RpcResponseListener<BlobsSidecar> listener) {
    return rpcMethods
        .blobsSidecarsByRange()
        .map(
            method -> {
              final UInt64 eip4844StartSlot = eip4844StartSlotSupplier.get();
              final BlobsSidecarsByRangeRequestMessage request;
              if (startSlot.isLessThan(eip4844StartSlot)) {
                // handle request spanning the EIP-4844 fork transition
                LOG.debug(
                    "Requesting blobs sidecars from slot {} instead of slot {} because the request is spanning the EIP-4844 fork transition",
                    eip4844StartSlot,
                    startSlot);
                final UInt64 updatedCount =
                    count.minusMinZero(eip4844StartSlot.minusMinZero(startSlot));
                request = new BlobsSidecarsByRangeRequestMessage(eip4844StartSlot, updatedCount);
              } else {
                request = new BlobsSidecarsByRangeRequestMessage(startSlot, count);
              }
              return requestStream(method, request, listener);
            })
        .orElse(
            SafeFuture.failedFuture(
                new UnsupportedOperationException("BlobsSidecarsByRange method is not available")));
  }

  @Override
  public SafeFuture<MetadataMessage> requestMetadata() {
    return requestSingleItem(rpcMethods.getMetadata(), EmptyMessage.EMPTY_MESSAGE);
  }

  @Override
  public boolean wantToReceiveBlocks(
      final ResponseCallback<SignedBeaconBlock> callback, final long blocksCount) {
    return wantToReceiveObjects("block", blockRequestTracker, callback, blocksCount);
  }

  @Override
  public boolean wantToReceiveBlockAndBlobsSidecars(
      final ResponseCallback<SignedBeaconBlockAndBlobsSidecar> callback, final long blocksCount) {
    return wantToReceiveObjects(
        "block and blobs sidecar", blockRequestTracker, callback, blocksCount);
  }

  @Override
  public boolean wantToReceiveBlobsSidecars(
      final ResponseCallback<BlobsSidecar> callback, final long blobsSidecarsCount) {
    return wantToReceiveObjects(
        "blobs sidecars", blobsSidecarsRequestTracker, callback, blobsSidecarsCount);
  }

  @Override
  public boolean wantToMakeRequest() {
    if (requestTracker.wantToRequestObjects(1L) == 0L) {
      LOG.debug("Peer {} disconnected due to request rate limits", getId());
      disconnectCleanly(DisconnectReason.RATE_LIMITING).ifExceptionGetsHereRaiseABug();
      return false;
    }
    return true;
  }

  @Override
  public SafeFuture<UInt64> sendPing() {
    unansweredPings.getAndIncrement();
    return requestSingleItem(rpcMethods.ping(), metadataMessagesFactory.createPingMessage())
        .thenApply(PingMessage::getSeqNumber)
        .thenPeek(__ -> unansweredPings.set(0))
        .thenPeek(this::updateMetadataSeqNumber);
  }

  @Override
  public int getUnansweredPingCount() {
    return unansweredPings.get();
  }

  @Override
  public <I extends RpcRequest, O extends SszData> SafeFuture<O> requestSingleItem(
      final Eth2RpcMethod<I, O> method, final I request) {
    final Eth2RpcResponseHandler<O, O> responseHandler =
        Eth2RpcResponseHandler.expectSingleResponse();
    return sendEth2Request(method, request, responseHandler)
        .thenCompose(__ -> responseHandler.getResult());
  }

  private <T> boolean wantToReceiveObjects(
      final String requestType,
      final RateTracker requestTracker,
      final ResponseCallback<T> callback,
      final long objectCount) {
    if (requestTracker.wantToRequestObjects(objectCount) == 0L) {
      LOG.debug("Peer {} disconnected due to {} rate limits", getId(), requestType);
      callback.completeWithErrorResponse(
          new RpcException(INVALID_REQUEST_CODE, "Peer has been rate limited"));
      disconnectCleanly(DisconnectReason.RATE_LIMITING).ifExceptionGetsHereRaiseABug();
      return false;
    }
    return true;
  }

  private <I extends RpcRequest, O extends SszData> SafeFuture<Optional<O>> requestOptionalItem(
      final Eth2RpcMethod<I, O> method, final I request) {
    final Eth2RpcResponseHandler<O, Optional<O>> responseHandler =
        Eth2RpcResponseHandler.expectOptionalResponse();
    return sendEth2Request(method, request, responseHandler)
        .thenCompose(__ -> responseHandler.getResult());
  }

  private <I extends RpcRequest, O extends SszData> SafeFuture<Void> requestStream(
      final Eth2RpcMethod<I, O> method, final I request, final RpcResponseListener<O> listener) {
    final Eth2RpcResponseHandler<O, Void> responseHandler =
        Eth2RpcResponseHandler.expectMultipleResponses(listener);
    return sendEth2Request(method, request, responseHandler)
        .thenCompose(__ -> responseHandler.getResult());
  }

  private <I extends RpcRequest, O extends SszData> SafeFuture<Void> sendEth2Request(
      final Eth2RpcMethod<I, O> method,
      final I request,
      Eth2RpcResponseHandler<O, ?> responseHandler) {
    outstandingRequests.incrementAndGet();

    return this.sendRequest(method, request, responseHandler)
        .thenPeek(
            ctrl ->
                ctrl.getRequiredOutgoingRequestHandler()
                    .handleInitialPayloadSent(ctrl.getRpcStream()))
        .thenCompose(ctrl -> ctrl.getRequiredOutgoingRequestHandler().getCompletedFuture())
        .alwaysRun(outstandingRequests::decrementAndGet);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
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
