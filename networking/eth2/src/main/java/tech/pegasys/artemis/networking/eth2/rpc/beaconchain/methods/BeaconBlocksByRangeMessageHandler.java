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

package tech.pegasys.artemis.networking.eth2.rpc.beaconchain.methods;

import static com.google.common.primitives.UnsignedLong.ONE;
import static com.google.common.primitives.UnsignedLong.ZERO;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;

import com.google.common.base.MoreObjects;
import com.google.common.primitives.UnsignedLong;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.apache.logging.log4j.LogManager;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.BeaconBlocksByRangeRequestMessage;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.networking.eth2.peers.Eth2Peer;
import tech.pegasys.artemis.networking.eth2.rpc.core.LocalMessageHandler;
import tech.pegasys.artemis.networking.eth2.rpc.core.ResponseCallback;
import tech.pegasys.artemis.networking.eth2.rpc.core.RpcException;
import tech.pegasys.artemis.storage.CombinedChainDataClient;

public class BeaconBlocksByRangeMessageHandler
    implements LocalMessageHandler<BeaconBlocksByRangeRequestMessage, BeaconBlock> {
  private static final org.apache.logging.log4j.Logger LOG = LogManager.getLogger();

  private final CombinedChainDataClient storageClient;

  public BeaconBlocksByRangeMessageHandler(final CombinedChainDataClient storageClient) {
    this.storageClient = storageClient;
  }

  @Override
  public void onIncomingMessage(
      final Eth2Peer peer,
      final BeaconBlocksByRangeRequestMessage message,
      final ResponseCallback<BeaconBlock> callback) {
    LOG.trace(
        "Peer {} requested {} BeaconBlocks from chain {} starting at slot {} with step {}",
        peer.getId(),
        message.getHeadBlockRoot(),
        message.getStartSlot(),
        message.getCount(),
        message.getStep());
    if (message.getStep().compareTo(ONE) < 0) {
      callback.completeWithError(RpcException.INVALID_STEP);
      return;
    }
    sendMatchingBlocks(message, callback)
        .thenAccept(success -> callback.completeSuccessfully())
        .exceptionally(
            error -> {
              if (error instanceof RpcException) {
                LOG.trace("Rejecting beacon blocks by range request", error);
                callback.completeWithError((RpcException) error);
              } else {
                LOG.error("Failed to process blocks by range request", error);
                callback.completeWithError(RpcException.SERVER_ERROR);
              }
              return null;
            });
  }

  private CompletableFuture<?> sendMatchingBlocks(
      final BeaconBlocksByRangeRequestMessage message,
      final ResponseCallback<BeaconBlock> callback) {
    final Optional<BeaconState> headState =
        storageClient.getNonfinalizedBlockState(message.getHeadBlockRoot());
    if (headState.isEmpty()) {
      LOG.trace("Not sending blocks by range because head block state is unknown");
      return completedFuture(null);
    }
    final UnsignedLong bestSlot = headState.get().getSlot();
    if (bestSlot == null) {
      LOG.error("No best slot present despite having at least one canonical block");
      return failedFuture(RpcException.SERVER_ERROR);
    }

    final RequestState requestState =
        new RequestState(
            message.getHeadBlockRoot(),
            message.getStartSlot(),
            message.getStep(),
            message.getCount(),
            bestSlot,
            callback);
    return sendNextBlock(requestState);
  }

  private CompletableFuture<RequestState> sendNextBlock(final RequestState requestState) {
    return storageClient
        .getBlockAtSlot(requestState.currentSlot, requestState.headBlockRoot)
        .thenCompose(
            maybeBlock -> {
              maybeBlock
                  .filter(block -> block.getSlot().equals(requestState.currentSlot))
                  .ifPresent(
                      block -> {
                        LOG.trace(
                            "Responding with block slot {} for slot {}. Remaining {}",
                            block.getSlot(),
                            requestState.currentSlot,
                            requestState.remainingBlocks);
                        requestState.decrementRemainingBlocks();
                        requestState.callback.respond(block);
                      });
              if (requestState.isComplete()) {
                return completedFuture(requestState);
              }
              requestState.incrementCurrentSlot();
              return sendNextBlock(requestState);
            });
  }

  private static class RequestState {
    private final UnsignedLong headSlot;
    private final ResponseCallback<BeaconBlock> callback;
    private final Bytes32 headBlockRoot;
    private final UnsignedLong step;
    private UnsignedLong currentSlot;
    private UnsignedLong remainingBlocks;

    public RequestState(
        final Bytes32 headBlockRoot,
        final UnsignedLong currentSlot,
        final UnsignedLong step,
        final UnsignedLong remainingBlocks,
        final UnsignedLong headSlot,
        final ResponseCallback<BeaconBlock> callback) {
      this.headBlockRoot = headBlockRoot;
      this.currentSlot = currentSlot;
      this.remainingBlocks = remainingBlocks;
      this.headSlot = headSlot;
      this.step = step;
      this.callback = callback;
    }

    boolean needsMoreBlocks() {
      return !remainingBlocks.equals(ZERO);
    }

    boolean hasReachedHeadSlot() {
      return currentSlot.compareTo(headSlot) >= 0;
    }

    boolean isComplete() {
      return !needsMoreBlocks() || hasReachedHeadSlot();
    }

    void decrementRemainingBlocks() {
      remainingBlocks = remainingBlocks.minus(ONE);
    }

    void incrementCurrentSlot() {
      currentSlot = currentSlot.plus(step);
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("headSlot", headSlot)
          .add("callback", callback)
          .add("headBlockRoot", headBlockRoot)
          .add("currentSlot", currentSlot)
          .add("remainingBlocks", remainingBlocks)
          .toString();
    }
  }
}
