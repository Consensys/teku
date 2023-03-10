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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseCallback;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.networking.eth2.rpc.core.methods.Eth2RpcMethod;
import tech.pegasys.teku.networking.p2p.mock.MockNodeIdGenerator;
import tech.pegasys.teku.networking.p2p.network.PeerAddress;
import tech.pegasys.teku.networking.p2p.peer.DisconnectReason;
import tech.pegasys.teku.networking.p2p.peer.DisconnectRequestHandler;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.networking.p2p.peer.PeerDisconnectedSubscriber;
import tech.pegasys.teku.networking.p2p.reputation.ReputationAdjustment;
import tech.pegasys.teku.networking.p2p.rpc.RpcMethod;
import tech.pegasys.teku.networking.p2p.rpc.RpcRequestHandler;
import tech.pegasys.teku.networking.p2p.rpc.RpcResponseHandler;
import tech.pegasys.teku.networking.p2p.rpc.RpcResponseListener;
import tech.pegasys.teku.networking.p2p.rpc.RpcStreamController;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobIdentifier;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.RpcRequest;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.MetadataMessage;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.generator.ChainBuilder;

public class RespondingEth2Peer implements Eth2Peer {
  private static final MockNodeIdGenerator ID_GENERATOR = new MockNodeIdGenerator();
  private static final Bytes4 FORK_DIGEST = Bytes4.fromHexString("0x11223344");

  private final Spec spec;
  private final ChainBuilder chain;
  private final List<ChainBuilder> forks;

  private final NodeId nodeId;
  private final Subscribers<PeerStatusSubscriber> statusSubscribers = Subscribers.create(false);
  private final Subscribers<PeerDisconnectedSubscriber> disconnectSubscribers =
      Subscribers.create(false);

  private PeerStatus status;
  private boolean disconnected = false;

  private final List<PendingRequest<?, ?>> pendingRequests = new ArrayList<>();
  private Function<List<SignedBeaconBlock>, List<SignedBeaconBlock>> blockRequestFilter =
      Function.identity();

  private RespondingEth2Peer(
      final Spec spec,
      final ChainBuilder chain,
      final List<ChainBuilder> forks,
      final PeerStatus status) {
    this.spec = spec;
    this.chain = chain;
    this.forks = forks;
    this.status = status;

    this.nodeId = ID_GENERATOR.next();
  }

  public static RespondingEth2Peer create(
      final Spec spec, final ChainBuilder chain, final ChainBuilder... forkChains) {
    return new RespondingEth2Peer(
        spec, chain, Arrays.asList(forkChains), createStatus(chain.getLatestBlockAndState()));
  }

  private static PeerStatus createStatus(final StateAndBlockSummary head) {
    final Checkpoint finalizedCheckpoint = head.getState().getFinalizedCheckpoint();
    return new PeerStatus(
        FORK_DIGEST,
        finalizedCheckpoint.getRoot(),
        finalizedCheckpoint.getEpoch(),
        head.getRoot(),
        head.getSlot());
  }

  private PeerStatus createStatus(final Checkpoint head, final Checkpoint finalized) {
    return new PeerStatus(
        FORK_DIGEST,
        finalized.getRoot(),
        finalized.getEpoch(),
        head.getRoot(),
        head.getEpochStartSlot(spec));
  }

  public void updateStatus(final Checkpoint head, final Checkpoint finalized) {
    updateStatus(createStatus(head, finalized));
  }

  @Override
  public int getOutstandingRequests() {
    return pendingRequests.size();
  }

  public void completePendingRequests() {
    final List<PendingRequest<?, ?>> requests = new ArrayList<>(pendingRequests);

    for (PendingRequest<?, ?> request : requests) {
      request.complete();
      pendingRequests.remove(request);
    }
  }

  public void setBlockRequestFilter(
      Function<List<SignedBeaconBlock>, List<SignedBeaconBlock>> filter) {
    this.blockRequestFilter = filter;
  }

  @Override
  public void updateStatus(final PeerStatus status) {
    this.status = status;
    statusSubscribers.deliver(PeerStatusSubscriber::onPeerStatus, status);
  }

  @Override
  public void updateMetadataSeqNumber(final UInt64 seqNumber) {}

  @Override
  public void subscribeInitialStatus(final PeerStatusSubscriber subscriber) {
    subscriber.onPeerStatus(status);
  }

  @Override
  public void subscribeStatusUpdates(final PeerStatusSubscriber subscriber) {
    statusSubscribers.subscribe(subscriber);
  }

  @Override
  public PeerStatus getStatus() {
    return status;
  }

  @Override
  public Optional<SszBitvector> getRemoteAttestationSubnets() {
    return Optional.empty();
  }

  @Override
  public UInt64 finalizedEpoch() {
    return status.getFinalizedEpoch();
  }

  @Override
  public Checkpoint finalizedCheckpoint() {
    return status.getFinalizedCheckpoint();
  }

  @Override
  public boolean hasStatus() {
    return true;
  }

  @Override
  public SafeFuture<PeerStatus> sendStatus() {
    return SafeFuture.completedFuture(status);
  }

  @Override
  public SafeFuture<Void> sendGoodbye(final UInt64 reason) {
    return SafeFuture.COMPLETE;
  }

  @Override
  public SafeFuture<Void> requestBlocksByRange(
      final UInt64 startSlot,
      final UInt64 count,
      final RpcResponseListener<SignedBeaconBlock> listener) {
    final long lastSlotExclusive = startSlot.longValue() + count.longValue();

    final PendingRequestHandler<Void, SignedBeaconBlock> handler =
        PendingRequestHandler.createForBatchBlockRequest(
            listener,
            () ->
                chain
                    .streamBlocksAndStates(startSlot.longValue(), lastSlotExclusive + 1)
                    .map(SignedBlockAndState::getBlock)
                    .collect(Collectors.toList()));
    return createPendingBlockRequest(handler);
  }

  @Override
  public SafeFuture<Void> requestBlobSidecarsByRange(
      final UInt64 startSlot, final UInt64 count, final RpcResponseListener<BlobSidecar> listener) {
    final long lastSlotExclusive = startSlot.longValue() + count.longValue();

    final PendingRequestHandler<Void, BlobSidecar> handler =
        PendingRequestHandler.createForBatchBlobSidecarRequest(
            listener,
            () ->
                chain
                    .streamBlobSidecars(startSlot.longValue(), lastSlotExclusive + 1)
                    .collect(Collectors.toList()));
    return createPendingBlobSidecarRequest(handler);
  }

  @Override
  public SafeFuture<Void> requestBlocksByRoot(
      final List<Bytes32> blockRoots, final RpcResponseListener<SignedBeaconBlock> listener) {
    final PendingRequestHandler<Void, SignedBeaconBlock> handler =
        PendingRequestHandler.createForBatchBlockRequest(
            listener,
            () ->
                blockRoots.stream()
                    .map(this::findBlockByRoot)
                    .flatMap(Optional::stream)
                    .collect(Collectors.toList()));

    return createPendingBlockRequest(handler);
  }

  @Override
  public SafeFuture<Void> requestBlobSidecarsByRoot(
      final List<BlobIdentifier> blobIdentifiers, final RpcResponseListener<BlobSidecar> listener)
      throws RpcException {
    throw new UnsupportedOperationException("Not yet implemented");
  }

  @Override
  public SafeFuture<Optional<SignedBeaconBlock>> requestBlockBySlot(final UInt64 slot) {
    final PendingRequestHandler<Optional<SignedBeaconBlock>, SignedBeaconBlock> handler =
        PendingRequestHandler.createForSingleBlockRequest(
            () -> Optional.ofNullable(chain.getBlockAtSlot(slot)));

    return createPendingBlockRequest(handler);
  }

  @Override
  public SafeFuture<Optional<SignedBeaconBlock>> requestBlockByRoot(final Bytes32 blockRoot) {
    final PendingRequestHandler<Optional<SignedBeaconBlock>, SignedBeaconBlock> handler =
        PendingRequestHandler.createForSingleBlockRequest(() -> findBlockByRoot(blockRoot));

    return createPendingBlockRequest(handler);
  }

  @Override
  public SafeFuture<Optional<BlobSidecar>> requestBlobSidecarByRoot(
      final BlobIdentifier blobIdentifier) {
    throw new UnsupportedOperationException("Not yet implemented");
  }

  private <T> SafeFuture<T> createPendingBlockRequest(
      final PendingRequestHandler<T, SignedBeaconBlock> handler) {
    final PendingRequestHandler<T, SignedBeaconBlock> filteredHandler =
        PendingRequestHandler.filterRequest(handler, blockRequestFilter);
    final PendingRequest<T, SignedBeaconBlock> request = new PendingRequest<>(filteredHandler);

    pendingRequests.add(request);
    return request.getFuture();
  }

  private <T> SafeFuture<T> createPendingBlobSidecarRequest(
      final PendingRequestHandler<T, BlobSidecar> handler) {
    final PendingRequest<T, BlobSidecar> request = new PendingRequest<>(handler);
    pendingRequests.add(request);
    return request.getFuture();
  }

  @Override
  public SafeFuture<MetadataMessage> requestMetadata() {
    final MetadataMessage defaultMetadata =
        spec.getGenesisSchemaDefinitions().getMetadataMessageSchema().createDefault();
    return SafeFuture.completedFuture(defaultMetadata);
  }

  @Override
  public <I extends RpcRequest, O extends SszData> SafeFuture<O> requestSingleItem(
      final Eth2RpcMethod<I, O> method, final I request) {
    return SafeFuture.failedFuture(new UnsupportedOperationException());
  }

  @Override
  public boolean wantToReceiveBlocks(
      final ResponseCallback<SignedBeaconBlock> callback, final long blocksCount) {
    return true;
  }

  @Override
  public boolean wantToReceiveBlobSidecars(
      final ResponseCallback<BlobSidecar> callback, final long blobSidecarsCount) {
    return true;
  }

  @Override
  public boolean wantToMakeRequest() {
    return true;
  }

  @Override
  public SafeFuture<UInt64> sendPing() {
    return SafeFuture.completedFuture(ONE);
  }

  @Override
  public int getUnansweredPingCount() {
    return 0;
  }

  @Override
  public NodeId getId() {
    return nodeId;
  }

  @Override
  public PeerAddress getAddress() {
    return new PeerAddress(nodeId);
  }

  @Override
  public Double getGossipScore() {
    return 0d;
  }

  @Override
  public boolean isConnected() {
    return !disconnected;
  }

  @Override
  public void disconnectImmediately(
      final Optional<DisconnectReason> reason, final boolean locallyInitiated) {
    disconnect(reason, locallyInitiated);
  }

  @Override
  public SafeFuture<Void> disconnectCleanly(final DisconnectReason reason) {
    disconnect(Optional.of(reason), true);
    return SafeFuture.COMPLETE;
  }

  private void disconnect(Optional<DisconnectReason> reason, boolean locallyInitiated) {
    disconnected = true;
    disconnectSubscribers.forEach(s -> s.onDisconnected(reason, locallyInitiated));
  }

  @Override
  public void setDisconnectRequestHandler(final DisconnectRequestHandler handler) {}

  @Override
  public void subscribeDisconnect(final PeerDisconnectedSubscriber subscriber) {
    disconnectSubscribers.subscribe(subscriber);
  }

  @Override
  public <
          TOutgoingHandler extends RpcRequestHandler,
          TRequest,
          RespHandler extends RpcResponseHandler<?>>
      SafeFuture<RpcStreamController<TOutgoingHandler>> sendRequest(
          final RpcMethod<TOutgoingHandler, TRequest, RespHandler> rpcMethod,
          final TRequest tRequest,
          final RespHandler responseHandler) {
    return null;
  }

  @Override
  public boolean connectionInitiatedLocally() {
    return true;
  }

  @Override
  public boolean connectionInitiatedRemotely() {
    return false;
  }

  @Override
  public boolean idMatches(final Peer other) {
    return other.getId().equals(nodeId);
  }

  @Override
  public void adjustReputation(final ReputationAdjustment adjustment) {}

  private <T> Optional<T> findObjectByRoot(
      final Bytes32 root, final BiFunction<ChainBuilder, Bytes32, Optional<T>> findMethod) {
    Optional<T> object = findMethod.apply(chain, root);
    for (ChainBuilder fork : forks) {
      if (object.isPresent()) {
        break;
      }
      object = findMethod.apply(fork, root);
    }
    return object;
  }

  private Optional<SignedBeaconBlock> findBlockByRoot(final Bytes32 root) {
    return findObjectByRoot(root, ChainBuilder::getBlock);
  }

  public static class PendingRequest<ResponseT, HandlerT> {
    private final SafeFuture<ResponseT> future = new SafeFuture<>();
    private final PendingRequestHandler<ResponseT, HandlerT> requestHandler;

    public PendingRequest(final PendingRequestHandler<ResponseT, HandlerT> requestHandler) {
      this.requestHandler = requestHandler;
    }

    public SafeFuture<ResponseT> getFuture() {
      return future;
    }

    public void complete() {
      if (!future.isDone()) {
        try {
          List<HandlerT> objects = requestHandler.getObjectsToReturn();
          requestHandler.handle(future, objects);
        } catch (Exception e) {
          future.completeExceptionally(e);
        }

        if (!future.isDone()) {
          throw new IllegalStateException(
              "Attempted to complete request but failed to fulfill future");
        }
      }
    }
  }

  private interface PendingRequestHandler<ResponseT, HandlerT> {

    List<HandlerT> getObjectsToReturn();

    void handle(SafeFuture<ResponseT> responseFuture, List<HandlerT> objects);

    static <ResponseT, HandlerT> PendingRequestHandler<ResponseT, HandlerT> filterRequest(
        final PendingRequestHandler<ResponseT, HandlerT> originalRequest,
        final Function<List<HandlerT>, List<HandlerT>> filter) {
      return new PendingRequestHandler<>() {

        @Override
        public List<HandlerT> getObjectsToReturn() {
          return filter.apply(originalRequest.getObjectsToReturn());
        }

        @Override
        public void handle(
            final SafeFuture<ResponseT> responseFuture, final List<HandlerT> objects) {
          originalRequest.handle(responseFuture, objects);
        }
      };
    }

    static <T> PendingRequestHandler<Optional<T>, T> createForSingleRequest(
        final Supplier<Optional<T>> objectSupplier) {
      return new PendingRequestHandler<>() {

        @Override
        public List<T> getObjectsToReturn() {
          return objectSupplier.get().map(List::of).orElse(Collections.emptyList());
        }

        @Override
        public void handle(final SafeFuture<Optional<T>> responseFuture, final List<T> objects) {
          final Optional<T> object =
              objects.isEmpty() ? Optional.empty() : Optional.of(objects.get(0));
          responseFuture.complete(object);
        }
      };
    }

    static PendingRequestHandler<Optional<SignedBeaconBlock>, SignedBeaconBlock>
        createForSingleBlockRequest(final Supplier<Optional<SignedBeaconBlock>> blockSupplier) {
      return createForSingleRequest(blockSupplier);
    }

    static <T> PendingRequestHandler<Void, T> createForBatchRequest(
        final RpcResponseListener<T> listener, final Supplier<List<T>> objectsSupplier) {
      return new PendingRequestHandler<>() {

        @Override
        public List<T> getObjectsToReturn() {
          return objectsSupplier.get();
        }

        @Override
        public void handle(final SafeFuture<Void> responseFuture, final List<T> objects) {
          SafeFuture<?> future = SafeFuture.COMPLETE;
          for (T object : objects) {
            future = future.thenCompose(__ -> listener.onResponse(object));
          }
          future.finish(() -> responseFuture.complete(null), responseFuture::completeExceptionally);
        }
      };
    }

    static PendingRequestHandler<Void, SignedBeaconBlock> createForBatchBlockRequest(
        final RpcResponseListener<SignedBeaconBlock> listener,
        final Supplier<List<SignedBeaconBlock>> blocksSupplier) {
      return createForBatchRequest(listener, blocksSupplier);
    }

    static PendingRequestHandler<Void, BlobSidecar> createForBatchBlobSidecarRequest(
        final RpcResponseListener<BlobSidecar> listener,
        final Supplier<List<BlobSidecar>> blobSidecarsSupplier) {
      return createForBatchRequest(listener, blobSidecarsSupplier);
    }
  }
}
