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

package tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods;

import static com.google.common.primitives.UnsignedLong.ONE;
import static com.google.common.primitives.UnsignedLong.ZERO;
import static tech.pegasys.teku.networking.eth2.rpc.core.RpcResponseStatus.INVALID_REQUEST_CODE;
import static tech.pegasys.teku.util.async.SafeFuture.completedFuture;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import com.google.common.primitives.UnsignedLong;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.apache.logging.log4j.LogManager;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.BeaconBlocksByRangeRequestMessage;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.rpc.core.PeerRequiredLocalMessageHandler;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseCallback;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.networking.p2p.rpc.StreamClosedException;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.util.async.SafeFuture;

public class BeaconBlocksByRangeMessageHandler
    extends PeerRequiredLocalMessageHandler<BeaconBlocksByRangeRequestMessage, SignedBeaconBlock> {
  private static final org.apache.logging.log4j.Logger LOG = LogManager.getLogger();

  @VisibleForTesting
  static final RpcException INVALID_STEP =
      new RpcException(INVALID_REQUEST_CODE, "Step must be greater than zero");

  private final CombinedChainDataClient combinedChainDataClient;

  public BeaconBlocksByRangeMessageHandler(final CombinedChainDataClient combinedChainDataClient) {
    this.combinedChainDataClient = combinedChainDataClient;
  }

  @Override
  public void onIncomingMessage(
      final Eth2Peer peer,
      final BeaconBlocksByRangeRequestMessage message,
      final ResponseCallback<SignedBeaconBlock> callback) {
    LOG.trace(
        "Peer {} requested {} BeaconBlocks starting at slot {} with step {}",
        peer.getId(),
        message.getStartSlot(),
        message.getCount(),
        message.getStep());
    if (message.getStep().compareTo(ONE) < 0) {
      callback.completeWithErrorResponse(INVALID_STEP);
      return;
    }
    sendMatchingBlocks(message, callback)
        .finish(
            callback::completeSuccessfully,
            error -> {
              final Throwable rootCause = Throwables.getRootCause(error);
              if (rootCause instanceof RpcException) {
                LOG.trace("Rejecting beacon blocks by range request", error); // Keep full context
                callback.completeWithErrorResponse((RpcException) rootCause);
              } else {
                if (rootCause instanceof StreamClosedException) {
                  LOG.trace("Stream closed while sending requested blocks", error);
                } else {
                  LOG.error("Failed to process blocks by range request", error);
                }
                callback.completeWithUnexpectedError(error);
              }
            });
  }

  private SafeFuture<?> sendMatchingBlocks(
      final BeaconBlocksByRangeRequestMessage message,
      final ResponseCallback<SignedBeaconBlock> callback) {
    Optional<Bytes32> maybeBestRoot = combinedChainDataClient.getBestBlockRoot();
    return maybeBestRoot
        .map(combinedChainDataClient::getStateByBlockRoot)
        .flatMap(CompletableFuture::join)
        .map(BeaconState::getSlot)
        .map(slot -> sendNextBlock(new RequestState(message, slot, maybeBestRoot.get(), callback)))
        .orElseGet(() -> completedFuture(null));
  }

  private SafeFuture<RequestState> sendNextBlock(final RequestState requestState) {
    SafeFuture<Optional<SignedBeaconBlock>> blockFuture = loadNextBlock(requestState);
    // Avoid risk of
    while (blockFuture.isDone() && !blockFuture.isCompletedExceptionally()) {
      final boolean complete = handleLoadedBlock(requestState, blockFuture.join());
      if (complete) {
        return completedFuture(requestState);
      }
      blockFuture = loadNextBlock(requestState);
    }
    return blockFuture.thenCompose(
        maybeBlock ->
            handleLoadedBlock(requestState, maybeBlock)
                ? completedFuture(requestState)
                : sendNextBlock(requestState));
  }

  /** Sends the block and returns true if the request is now complete. */
  private boolean handleLoadedBlock(
      final RequestState requestState, final Optional<SignedBeaconBlock> block) {
    block.ifPresent(requestState::sendBlock);
    if (requestState.isComplete()) {
      return true;
    } else {
      requestState.incrementCurrentSlot();
      return false;
    }
  }

  private SafeFuture<Optional<SignedBeaconBlock>> loadNextBlock(final RequestState requestState) {
    return combinedChainDataClient.getBlockAtSlotExact(
        requestState.currentSlot, requestState.headBlockRoot);
  }

  private static class RequestState {
    private final UnsignedLong headSlot;
    private final ResponseCallback<SignedBeaconBlock> callback;
    private final Bytes32 headBlockRoot;
    private final UnsignedLong step;
    private UnsignedLong currentSlot;
    private UnsignedLong remainingBlocks;

    public RequestState(
        final BeaconBlocksByRangeRequestMessage message,
        final UnsignedLong headSlot,
        final Bytes32 headBlockRoot,
        final ResponseCallback<SignedBeaconBlock> callback) {
      this.currentSlot = message.getStartSlot();
      this.headBlockRoot = headBlockRoot;
      // Minus 1 to account for sending the block at startSlot.
      // We only decrement this when moving to the next slot but we're already at the first slot
      this.remainingBlocks = message.getCount().minus(ONE);
      this.step = message.getStep();
      this.headSlot = headSlot;
      this.callback = callback;
    }

    private boolean needsMoreBlocks() {
      return !remainingBlocks.equals(ZERO);
    }

    private boolean hasReachedHeadSlot() {
      return currentSlot.compareTo(headSlot) >= 0;
    }

    boolean isComplete() {
      return !needsMoreBlocks() || hasReachedHeadSlot();
    }

    void sendBlock(final SignedBeaconBlock block) {
      callback.respond(block);
    }

    void incrementCurrentSlot() {
      remainingBlocks = remainingBlocks.minus(ONE);
      currentSlot = currentSlot.plus(step);
    }
  }
}
