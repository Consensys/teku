/*
 * Copyright 2020 ConsenSys AG.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.core.ChainBuilder;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.MetadataMessage;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.RpcRequest;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.rpc.core.Eth2RpcMethod;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseCallback;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseStreamListener;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
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
import tech.pegasys.teku.networking.p2p.rpc.RpcStream;
import tech.pegasys.teku.ssz.SSZTypes.Bitvector;
import tech.pegasys.teku.ssz.SSZTypes.Bytes4;

public class RespondingEth2Peer implements Eth2Peer {
  private static final MockNodeIdGenerator idGenerator = new MockNodeIdGenerator();
  private static final Bytes4 forkDigest = Bytes4.fromHexString("0x11223344");

  private final ChainBuilder chain;
  private final List<ChainBuilder> forks;

  private final NodeId nodeId;
  private final Subscribers<PeerStatusSubscriber> statusSubscribers = Subscribers.create(false);
  private final Subscribers<PeerDisconnectedSubscriber> disconnectSubscribers =
      Subscribers.create(false);

  private PeerStatus status;
  private boolean disconnected = false;

  private final List<PendingBlockRequest<?>> pendingRequests = new ArrayList<>();
  private Function<List<SignedBeaconBlock>, List<SignedBeaconBlock>> blockRequestFilter =
      Function.identity();

  private RespondingEth2Peer(
      final ChainBuilder chain, final List<ChainBuilder> forks, final PeerStatus status) {
    this.chain = chain;
    this.forks = forks;
    this.status = status;

    this.nodeId = idGenerator.next();
  }

  public static RespondingEth2Peer create(
      final ChainBuilder chain, final ChainBuilder... forkChains) {
    return new RespondingEth2Peer(
        chain, Arrays.asList(forkChains), createStatus(chain.getLatestBlockAndState()));
  }

  private static PeerStatus createStatus(final StateAndBlockSummary head) {
    final Checkpoint finalizedCheckpoint = head.getState().getFinalized_checkpoint();
    return new PeerStatus(
        forkDigest,
        finalizedCheckpoint.getRoot(),
        finalizedCheckpoint.getEpoch(),
        head.getRoot(),
        head.getSlot());
  }

  private static PeerStatus createStatus(final Checkpoint head, final Checkpoint finalized) {
    return new PeerStatus(
        forkDigest,
        finalized.getRoot(),
        finalized.getEpoch(),
        head.getRoot(),
        head.getEpochStartSlot());
  }

  public void updateStatus(final Checkpoint head, final Checkpoint finalized) {
    updateStatus(createStatus(head, finalized));
  }

  @Override
  public int getOutstandingRequests() {
    return pendingRequests.size();
  }

  public void completePendingRequests() {
    final List<PendingBlockRequest<?>> requests = new ArrayList<>(pendingRequests);

    for (PendingBlockRequest<?> request : requests) {
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
  public Optional<Bitvector> getRemoteAttestationSubnets() {
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
      final UInt64 step,
      final ResponseStreamListener<SignedBeaconBlock> listener) {
    final long lastSlotExclusive = startSlot.longValue() + count.longValue() * step.longValue();

    final PendingBlockRequestHandler<Void> handler =
        PendingBlockRequestHandler.createForBatchRequest(
            listener,
            () ->
                chain
                    .streamBlocksAndStates(startSlot.longValue(), lastSlotExclusive + 1)
                    .filter(b -> b.getSlot().minus(startSlot).mod(step).equals(UInt64.ZERO))
                    .map(SignedBlockAndState::getBlock)
                    .collect(Collectors.toList()));
    return createPendingRequest(handler);
  }

  @Override
  public SafeFuture<Void> requestBlocksByRoot(
      final List<Bytes32> blockRoots, final ResponseStreamListener<SignedBeaconBlock> listener)
      throws RpcException {
    final PendingBlockRequestHandler<Void> handler =
        PendingBlockRequestHandler.createForBatchRequest(
            listener,
            () ->
                blockRoots.stream()
                    .map(this::findBlockByRoot)
                    .flatMap(Optional::stream)
                    .collect(Collectors.toList()));

    return createPendingRequest(handler);
  }

  @Override
  public SafeFuture<Optional<SignedBeaconBlock>> requestBlockBySlot(final UInt64 slot) {
    final PendingBlockRequestHandler<Optional<SignedBeaconBlock>> handler =
        PendingBlockRequestHandler.createForSingleBlockRequest(
            () -> Optional.ofNullable(chain.getBlockAtSlot(slot)));

    return createPendingRequest(handler);
  }

  @Override
  public SafeFuture<Optional<SignedBeaconBlock>> requestBlockByRoot(final Bytes32 blockRoot) {
    final PendingBlockRequestHandler<Optional<SignedBeaconBlock>> handler =
        PendingBlockRequestHandler.createForSingleBlockRequest(() -> findBlockByRoot(blockRoot));

    return createPendingRequest(handler);
  }

  private <T> SafeFuture<T> createPendingRequest(PendingBlockRequestHandler<T> handler) {
    final PendingBlockRequestHandler<T> filteredHandler =
        PendingBlockRequestHandler.filterRequest(handler, blockRequestFilter);
    final PendingBlockRequest<T> request = new PendingBlockRequest<>(filteredHandler);

    pendingRequests.add(request);
    return request.getFuture();
  }

  @Override
  public SafeFuture<MetadataMessage> requestMetadata() {
    return SafeFuture.completedFuture(MetadataMessage.createDefault());
  }

  @Override
  public <I extends RpcRequest, O> SafeFuture<O> requestSingleItem(
      final Eth2RpcMethod<I, O> method, final I request) {
    return SafeFuture.failedFuture(new UnsupportedOperationException());
  }

  @Override
  public boolean wantToReceiveObjects(
      final ResponseCallback<SignedBeaconBlock> callback, final long objectCount) {
    return true;
  }

  @Override
  public boolean wantToMakeRequest() {
    return true;
  }

  @Override
  public SafeFuture<UInt64> sendPing() {
    return SafeFuture.completedFuture(UInt64.ONE);
  }

  @Override
  public int getOutstandingPings() {
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
  public SafeFuture<RpcStream> sendRequest(
      final RpcMethod rpcMethod, final Bytes initialPayload, final RpcRequestHandler handler) {
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

  private Optional<SignedBeaconBlock> findBlockByRoot(final Bytes32 root) {
    Optional<SignedBeaconBlock> block = chain.getBlock(root);
    for (ChainBuilder fork : forks) {
      if (block.isPresent()) {
        break;
      }
      block = fork.getBlock(root);
    }
    return block;
  }

  public static class PendingBlockRequest<T> {
    private final SafeFuture<T> future = new SafeFuture<>();
    private final PendingBlockRequestHandler<T> requestHandler;

    public PendingBlockRequest(final PendingBlockRequestHandler<T> requestHandler) {
      this.requestHandler = requestHandler;
    }

    public SafeFuture<T> getFuture() {
      return future;
    }

    public void complete() {
      if (!future.isDone()) {
        try {
          List<SignedBeaconBlock> blocks = requestHandler.getBlocksToReturn();
          requestHandler.handle(future, blocks);
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

  private interface PendingBlockRequestHandler<T> {

    List<SignedBeaconBlock> getBlocksToReturn();

    void handle(SafeFuture<T> responseFuture, List<SignedBeaconBlock> blocks);

    static <T> PendingBlockRequestHandler<T> filterRequest(
        PendingBlockRequestHandler<T> originalRequest,
        final Function<List<SignedBeaconBlock>, List<SignedBeaconBlock>> filter) {
      return new PendingBlockRequestHandler<T>() {
        @Override
        public List<SignedBeaconBlock> getBlocksToReturn() {
          return filter.apply(originalRequest.getBlocksToReturn());
        }

        @Override
        public void handle(
            final SafeFuture<T> responseFuture, final List<SignedBeaconBlock> blocks) {
          originalRequest.handle(responseFuture, blocks);
        }
      };
    }

    static PendingBlockRequestHandler<Optional<SignedBeaconBlock>> createForSingleBlockRequest(
        Supplier<Optional<SignedBeaconBlock>> blockSupplier) {
      return new PendingBlockRequestHandler<Optional<SignedBeaconBlock>>() {
        @Override
        public List<SignedBeaconBlock> getBlocksToReturn() {
          return blockSupplier.get().map(List::of).orElse(Collections.emptyList());
        }

        @Override
        public void handle(
            final SafeFuture<Optional<SignedBeaconBlock>> responseFuture,
            final List<SignedBeaconBlock> blocks) {
          final Optional<SignedBeaconBlock> block =
              blocks.isEmpty() ? Optional.empty() : Optional.of(blocks.get(0));
          responseFuture.complete(block);
        }
      };
    }

    static PendingBlockRequestHandler<Void> createForBatchRequest(
        final ResponseStreamListener<SignedBeaconBlock> listener,
        final Supplier<List<SignedBeaconBlock>> blocksSupplier) {
      return new PendingBlockRequestHandler<Void>() {
        @Override
        public List<SignedBeaconBlock> getBlocksToReturn() {
          return blocksSupplier.get();
        }

        @Override
        public void handle(
            final SafeFuture<Void> responseFuture, final List<SignedBeaconBlock> blocks) {
          SafeFuture<?> future = SafeFuture.COMPLETE;
          for (SignedBeaconBlock block : blocks) {
            future = future.thenCompose(__ -> listener.onResponse(block));
          }
          future.finish(() -> responseFuture.complete(null), responseFuture::completeExceptionally);
        }
      };
    }
  }
}
