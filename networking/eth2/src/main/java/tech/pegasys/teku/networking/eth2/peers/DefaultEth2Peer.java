/*
 * Copyright Consensys Software Inc., 2022
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

import com.google.common.base.MoreObjects;
import com.google.common.base.Suppliers;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.BeaconChainMethods;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.BlobSidecarsByRangeListenerValidatingProxy;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.BlobSidecarsByRootListenerValidatingProxy;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.BlobSidecarsByRootValidator;
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
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.config.SpecConfigEip7732;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BeaconBlocksByRangeRequestMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BeaconBlocksByRootRequestMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobIdentifier;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobSidecarsByRangeRequestMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobSidecarsByRootRequestMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobSidecarsByRootRequestMessageSchema;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.EmptyMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.ExecutionPayloadEnvelopesByRangeRequestMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.ExecutionPayloadEnvelopesByRootRequestMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.ExecutionPayloadEnvelopesByRootRequestMessage.ExecutionPayloadEnvelopesByRootRequestMessageSchema;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.GoodbyeMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.PingMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.RpcRequest;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.StatusMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.MetadataMessage;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsEip7732;

class DefaultEth2Peer extends DelegatingPeer implements Eth2Peer {
  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final Optional<UInt256> discoveryNodeId;
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
  private final RateTracker blobSidecarsRequestTracker;
  private final RateTracker executionPayloadRequestTracker;
  private final RateTracker requestTracker;
  private final KZG kzg;
  private final Supplier<UInt64> firstSlotSupportingBlobSidecarsByRange;
  private final Supplier<BlobSidecarsByRootRequestMessageSchema>
      blobSidecarsByRootRequestMessageSchema;
  private final Supplier<ExecutionPayloadEnvelopesByRootRequestMessageSchema>
      executionPayloadEnvelopesByRootRequestMessageSchema;
  private final Supplier<UInt64> firstSlotSupportingExecutionPayloadEnvelopesByRange;

  DefaultEth2Peer(
      final Spec spec,
      final Peer peer,
      final Optional<UInt256> discoveryNodeId,
      final BeaconChainMethods rpcMethods,
      final StatusMessageFactory statusMessageFactory,
      final MetadataMessagesFactory metadataMessagesFactory,
      final PeerChainValidator peerChainValidator,
      final RateTracker blockRequestTracker,
      final RateTracker blobSidecarsRequestTracker,
      final RateTracker executionPayloadRequestTracker,
      final RateTracker requestTracker,
      final KZG kzg) {
    super(peer);
    this.spec = spec;
    this.discoveryNodeId = discoveryNodeId;
    this.rpcMethods = rpcMethods;
    this.statusMessageFactory = statusMessageFactory;
    this.metadataMessagesFactory = metadataMessagesFactory;
    this.peerChainValidator = peerChainValidator;
    this.blockRequestTracker = blockRequestTracker;
    this.blobSidecarsRequestTracker = blobSidecarsRequestTracker;
    this.executionPayloadRequestTracker = executionPayloadRequestTracker;
    this.requestTracker = requestTracker;
    this.kzg = kzg;
    this.firstSlotSupportingBlobSidecarsByRange =
        Suppliers.memoize(
            () -> {
              final UInt64 denebForkEpoch = getSpecConfigDeneb().getDenebForkEpoch();
              return spec.computeStartSlotAtEpoch(denebForkEpoch);
            });
    this.blobSidecarsByRootRequestMessageSchema =
        Suppliers.memoize(
            () ->
                SchemaDefinitionsDeneb.required(
                        spec.forMilestone(SpecMilestone.DENEB).getSchemaDefinitions())
                    .getBlobSidecarsByRootRequestMessageSchema());
    this.executionPayloadEnvelopesByRootRequestMessageSchema =
        Suppliers.memoize(
            () ->
                SchemaDefinitionsEip7732.required(
                        spec.forMilestone(SpecMilestone.EIP7732).getSchemaDefinitions())
                    .getExecutionPayloadEnvelopesByRootRequestMessageSchema());
    this.firstSlotSupportingExecutionPayloadEnvelopesByRange =
        Suppliers.memoize(
            () -> {
              final UInt64 eip7732ForkEpoch = getSpecConfigEip7732().getEip7732ForkEpoch();
              return spec.computeStartSlotAtEpoch(eip7732ForkEpoch);
            });
  }

  @Override
  public Optional<UInt256> getDiscoveryNodeId() {
    return discoveryNodeId;
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
          new IllegalStateException("Unable to generate local status message. Node is not ready.");
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
      final List<Bytes32> blockRoots, final RpcResponseListener<SignedBeaconBlock> listener) {
    final BeaconBlocksByRootRequestMessage.BeaconBlocksByRootRequestMessageSchema requestSchema =
        spec.getGenesisSchemaDefinitions().getBeaconBlocksByRootRequestMessageSchema();
    final Eth2RpcMethod<BeaconBlocksByRootRequestMessage, SignedBeaconBlock> blockByRoot =
        rpcMethods.beaconBlocksByRoot();
    return requestStream(
        blockByRoot, new BeaconBlocksByRootRequestMessage(requestSchema, blockRoots), listener);
  }

  @Override
  public SafeFuture<Void> requestBlobSidecarsByRoot(
      final List<BlobIdentifier> blobIdentifiers, final RpcResponseListener<BlobSidecar> listener) {
    return rpcMethods
        .blobSidecarsByRoot()
        .map(
            method ->
                requestStream(
                    method,
                    new BlobSidecarsByRootRequestMessage(
                        blobSidecarsByRootRequestMessageSchema.get(), blobIdentifiers),
                    new BlobSidecarsByRootListenerValidatingProxy(
                        this, spec, listener, kzg, blobIdentifiers)))
        .orElse(failWithUnsupportedMethodException("BlobSidecarsByRoot"));
  }

  @Override
  public SafeFuture<Void> requestExecutionPayloadEnvelopesByRoot(
      final List<Bytes32> blockRoots,
      final RpcResponseListener<SignedExecutionPayloadEnvelope> listener) {
    return rpcMethods
        .executionPayloadEnvelopesByRoot()
        .map(
            method ->
                requestStream(
                    method,
                    new ExecutionPayloadEnvelopesByRootRequestMessage(
                        executionPayloadEnvelopesByRootRequestMessageSchema.get(), blockRoots),
                    listener))
        .orElse(failWithUnsupportedMethodException("ExecutionPayloadEnvelopesByRoot"));
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
        blockByRoot,
        new BeaconBlocksByRootRequestMessage(
            spec.getGenesisSchemaDefinitions().getBeaconBlocksByRootRequestMessageSchema(),
            List.of(blockRoot)));
  }

  @Override
  public SafeFuture<Optional<BlobSidecar>> requestBlobSidecarByRoot(
      final BlobIdentifier blobIdentifier) {
    return rpcMethods
        .blobSidecarsByRoot()
        .map(
            method -> {
              final List<BlobIdentifier> blobIdentifiers =
                  Collections.singletonList(blobIdentifier);
              return requestOptionalItem(
                      method,
                      new BlobSidecarsByRootRequestMessage(
                          blobSidecarsByRootRequestMessageSchema.get(), blobIdentifiers))
                  .thenPeek(
                      maybeBlobSidecar ->
                          maybeBlobSidecar.ifPresent(
                              blobSidecar -> {
                                final BlobSidecarsByRootValidator validator =
                                    new BlobSidecarsByRootValidator(
                                        this, spec, kzg, blobIdentifiers);
                                validator.validate(blobSidecar);
                              }));
            })
        .orElse(failWithUnsupportedMethodException("BlobSidecarsByRoot"));
  }

  @Override
  public SafeFuture<Optional<SignedExecutionPayloadEnvelope>> requestExecutionPayloadEnvelopeByRoot(
      final Bytes32 blockRoot) {
    return rpcMethods
        .executionPayloadEnvelopesByRoot()
        .map(
            method ->
                requestOptionalItem(
                    method,
                    new ExecutionPayloadEnvelopesByRootRequestMessage(
                        executionPayloadEnvelopesByRootRequestMessageSchema.get(),
                        List.of(blockRoot))))
        .orElse(failWithUnsupportedMethodException("ExecutionPayloadEnvelopesByRoot"));
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
  public SafeFuture<Void> requestBlobSidecarsByRange(
      final UInt64 startSlot, final UInt64 count, final RpcResponseListener<BlobSidecar> listener) {
    return rpcMethods
        .blobSidecarsByRange()
        .map(
            method -> {
              final UInt64 firstSupportedSlot = firstSlotSupportingBlobSidecarsByRange.get();
              final BlobSidecarsByRangeRequestMessage request;
              final int maxBlobsPerBlock = calculateMaxBlobsPerBlock(startSlot.plus(count));

              if (startSlot.isLessThan(firstSupportedSlot)) {
                LOG.debug(
                    "Requesting blob sidecars from slot {} instead of slot {} because the request is spanning the Deneb fork transition",
                    firstSupportedSlot,
                    startSlot);
                final UInt64 updatedCount =
                    count.minusMinZero(firstSupportedSlot.minusMinZero(startSlot));
                if (updatedCount.isZero()) {
                  return SafeFuture.COMPLETE;
                }
                request =
                    new BlobSidecarsByRangeRequestMessage(
                        firstSupportedSlot, updatedCount, maxBlobsPerBlock);
              } else {
                request = new BlobSidecarsByRangeRequestMessage(startSlot, count, maxBlobsPerBlock);
              }
              return requestStream(
                  method,
                  request,
                  new BlobSidecarsByRangeListenerValidatingProxy(
                      spec,
                      this,
                      listener,
                      maxBlobsPerBlock,
                      kzg,
                      request.getStartSlot(),
                      request.getCount()));
            })
        .orElse(failWithUnsupportedMethodException("BlobSidecarsByRange"));
  }

  @Override
  public SafeFuture<Void> requestExecutionPayloadEnvelopesByRange(
      final UInt64 startSlot,
      final UInt64 count,
      final RpcResponseListener<SignedExecutionPayloadEnvelope> listener) {
    return rpcMethods
        .executionPayloadEnvelopesByRange()
        .map(
            method -> {
              final UInt64 firstSupportedSlot =
                  firstSlotSupportingExecutionPayloadEnvelopesByRange.get();
              final ExecutionPayloadEnvelopesByRangeRequestMessage request;

              if (startSlot.isLessThan(firstSupportedSlot)) {
                LOG.debug(
                    "Requesting execution payload envelopes from slot {} instead of slot {} because the request is spanning the EIP-7732 fork transition",
                    firstSupportedSlot,
                    startSlot);
                final UInt64 updatedCount =
                    count.minusMinZero(firstSupportedSlot.minusMinZero(startSlot));
                if (updatedCount.isZero()) {
                  return SafeFuture.COMPLETE;
                }
                request =
                    new ExecutionPayloadEnvelopesByRangeRequestMessage(
                        firstSupportedSlot, updatedCount);
              } else {
                request = new ExecutionPayloadEnvelopesByRangeRequestMessage(startSlot, count);
              }
              // EIP-7732 TODO: add a listener wrapper for additional verification (similar to
              // BlocksByRange)
              return requestStream(method, request, listener);
            })
        .orElse(failWithUnsupportedMethodException("ExecutionPayloadEnvelopesByRange"));
  }

  private int calculateMaxBlobsPerBlock(final UInt64 endSlot) {
    return SpecConfigDeneb.required(spec.atSlot(endSlot).getConfig()).getMaxBlobsPerBlock();
  }

  @Override
  public SafeFuture<MetadataMessage> requestMetadata() {
    return requestSingleItem(rpcMethods.getMetadata(), EmptyMessage.EMPTY_MESSAGE);
  }

  @Override
  public Optional<RequestApproval> approveBlocksRequest(
      final ResponseCallback<SignedBeaconBlock> callback, final long blocksCount) {
    return approveObjectsRequest("blocks", blockRequestTracker, blocksCount, callback);
  }

  @Override
  public void adjustBlocksRequest(
      final RequestApproval blocksRequest, final long returnedBlocksCount) {
    adjustObjectsRequest(blockRequestTracker, blocksRequest, returnedBlocksCount);
  }

  @Override
  public Optional<RequestApproval> approveBlobSidecarsRequest(
      final ResponseCallback<BlobSidecar> callback, final long blobSidecarsCount) {
    return approveObjectsRequest(
        "blob sidecars", blobSidecarsRequestTracker, blobSidecarsCount, callback);
  }

  @Override
  public void adjustBlobSidecarsRequest(
      final RequestApproval blobSidecarsRequest, final long returnedBlobSidecarsCount) {
    adjustObjectsRequest(
        blobSidecarsRequestTracker, blobSidecarsRequest, returnedBlobSidecarsCount);
  }

  @Override
  public Optional<RequestApproval> approveExecutionPayloadEnvelopesRequest(
      final ResponseCallback<SignedExecutionPayloadEnvelope> callback,
      final long executionPayloadEnvelopesCount) {
    return approveObjectsRequest(
        "execution payload envelopes",
        executionPayloadRequestTracker,
        executionPayloadEnvelopesCount,
        callback);
  }

  @Override
  public void adjustExecutionPayloadEnvelopesRequest(
      final RequestApproval executionPayloadEnvelopesRequest,
      final long returnedExecutionPayloadEnvelopesCount) {
    adjustObjectsRequest(
        executionPayloadRequestTracker,
        executionPayloadEnvelopesRequest,
        returnedExecutionPayloadEnvelopesCount);
  }

  @Override
  public boolean approveRequest() {
    if (requestTracker.approveObjectsRequest(1L).isEmpty()) {
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

  private void adjustObjectsRequest(
      final RateTracker requestTracker,
      final RequestApproval requestApproval,
      final long returnedObjectsCount) {
    requestTracker.adjustObjectsRequest(requestApproval, returnedObjectsCount);
  }

  private <T> Optional<RequestApproval> approveObjectsRequest(
      final String requestType,
      final RateTracker requestTracker,
      final long objectsCount,
      final ResponseCallback<T> callback) {
    final Optional<RequestApproval> requestApproval =
        requestTracker.approveObjectsRequest(objectsCount);
    if (requestApproval.isEmpty()) {
      LOG.debug("Peer {} disconnected due to {} rate limits", getId(), requestType);
      callback.completeWithErrorResponse(
          new RpcException(INVALID_REQUEST_CODE, "Peer has been rate limited"));
      disconnectCleanly(DisconnectReason.RATE_LIMITING).ifExceptionGetsHereRaiseABug();
    }
    return requestApproval;
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
      final Eth2RpcResponseHandler<O, ?> responseHandler) {
    outstandingRequests.incrementAndGet();

    return this.sendRequest(method, request, responseHandler)
        .thenPeek(
            ctrl ->
                ctrl.getRequiredOutgoingRequestHandler()
                    .handleInitialPayloadSent(ctrl.getRpcStream()))
        .thenCompose(ctrl -> ctrl.getRequiredOutgoingRequestHandler().getCompletedFuture())
        .alwaysRun(outstandingRequests::decrementAndGet);
  }

  private SpecConfigDeneb getSpecConfigDeneb() {
    return SpecConfigDeneb.required(spec.forMilestone(SpecMilestone.DENEB).getConfig());
  }

  private SpecConfigEip7732 getSpecConfigEip7732() {
    return SpecConfigEip7732.required(spec.forMilestone(SpecMilestone.EIP7732).getConfig());
  }

  private <T> SafeFuture<T> failWithUnsupportedMethodException(final String method) {
    return SafeFuture.failedFuture(
        new UnsupportedOperationException(method + " method is not supported"));
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
