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
import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;
import static tech.pegasys.teku.networking.eth2.rpc.core.RpcResponseStatus.INVALID_REQUEST_CODE;
import static tech.pegasys.teku.util.config.Constants.MAX_REQUEST_BLOCKS;
import static tech.pegasys.teku.util.unsignedlong.UnsignedLongMath.min;

import com.google.common.base.Throwables;
import com.google.common.primitives.UnsignedLong;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import org.apache.logging.log4j.LogManager;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.BeaconBlocksByRangeRequestMessage;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.rpc.core.PeerRequiredLocalMessageHandler;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseCallback;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.networking.p2p.rpc.StreamClosedException;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

public class BeaconBlocksByRangeMessageHandler
    extends PeerRequiredLocalMessageHandler<BeaconBlocksByRangeRequestMessage, SignedBeaconBlock> {
  private static final org.apache.logging.log4j.Logger LOG = LogManager.getLogger();

  private final CombinedChainDataClient combinedChainDataClient;
  private final UnsignedLong maxRequestSize;

  public BeaconBlocksByRangeMessageHandler(
      final CombinedChainDataClient combinedChainDataClient, final UnsignedLong maxRequestSize) {
    this.combinedChainDataClient = combinedChainDataClient;
    this.maxRequestSize = maxRequestSize;
  }

  @Override
  public void onIncomingMessage(
      final Eth2Peer peer,
      final BeaconBlocksByRangeRequestMessage message,
      final ResponseCallback<SignedBeaconBlock> callback) {
    LOG.trace(
        "Peer {} requested {} BeaconBlocks starting at slot {} with step {}",
        peer.getId(),
        message.getCount(),
        message.getStartSlot(),
        message.getStep());
    if (message.getStep().compareTo(ONE) < 0) {
      callback.completeWithErrorResponse(
          new RpcException(INVALID_REQUEST_CODE, "Step must be greater than zero"));
      return;
    }
    if (message.getCount().compareTo(UnsignedLong.valueOf(MAX_REQUEST_BLOCKS)) > 0) {
      callback.completeWithErrorResponse(
          new RpcException(
              INVALID_REQUEST_CODE,
              "Only a maximum of " + MAX_REQUEST_BLOCKS + " blocks can be requested per request"));
      return;
    }
    if (!peer.wantToReceiveObjects(callback, min(maxRequestSize, message.getCount()).longValue())) {
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
    final UnsignedLong count = min(maxRequestSize, message.getCount());
    final UnsignedLong endSlot =
        message.getStartSlot().plus(message.getStep().times(count)).minus(ONE);

    final UnsignedLong headBlockSlot =
        combinedChainDataClient.getBestBlock().map(SignedBeaconBlock::getSlot).orElse(ZERO);
    final NavigableMap<UnsignedLong, Bytes32> hotRoots;
    if (combinedChainDataClient.isFinalized(endSlot)) {
      // All blocks are finalized so skip scanning the protoarray
      hotRoots = new TreeMap<>();
    } else {
      hotRoots =
          combinedChainDataClient.getAncestorRoots(
              message.getStartSlot(), message.getStep(), count);
    }
    // Don't send anything past the last slot found in protoarray to ensure blocks are consistent
    // If we didn't find any blocks in protoarray, every block in the range must be finalized
    // so we don't need to worry about inconsistent blocks
    final UnsignedLong headSlot = hotRoots.isEmpty() ? headBlockSlot : hotRoots.lastKey();
    return sendNextBlock(
        new RequestState(
            message.getStartSlot(), message.getStep(), count, headSlot, hotRoots, callback));
  }

  private SafeFuture<RequestState> sendNextBlock(final RequestState requestState) {
    SafeFuture<Boolean> blockFuture = processNextBlock(requestState);
    // Avoid risk of StackOverflowException by iterating when the block future is already complete
    // Using thenCompose on the completed future would execute immediately and recurse back into
    // this method to send the next block.  When not already complete, thenCompose is executed
    // on a separate thread so doesn't recurse on the same stack.
    while (blockFuture.isDone() && !blockFuture.isCompletedExceptionally()) {
      if (blockFuture.join()) {
        return completedFuture(requestState);
      }
      blockFuture = processNextBlock(requestState);
    }
    return blockFuture.thenCompose(
        complete -> complete ? completedFuture(requestState) : sendNextBlock(requestState));
  }

  private SafeFuture<Boolean> processNextBlock(final RequestState requestState) {
    // Ensure blocks are loaded off of the event thread
    return requestState
        .loadNextBlock()
        .thenCompose(block -> handleLoadedBlock(requestState, block));
  }

  /** Sends the block and returns true if the request is now complete. */
  private SafeFuture<Boolean> handleLoadedBlock(
      final RequestState requestState, final Optional<SignedBeaconBlock> block) {
    return block
        .map(requestState::sendBlock)
        .orElse(SafeFuture.COMPLETE)
        .thenApply(
            __ -> {
              if (requestState.isComplete()) {
                return true;
              } else {
                requestState.incrementCurrentSlot();
                return false;
              }
            });
  }

  private class RequestState {
    private final UnsignedLong headSlot;
    private final ResponseCallback<SignedBeaconBlock> callback;
    private final UnsignedLong step;
    private final NavigableMap<UnsignedLong, Bytes32> knownBlockRoots;
    private UnsignedLong currentSlot;
    private UnsignedLong remainingBlocks;

    RequestState(
        final UnsignedLong startSlot,
        final UnsignedLong step,
        final UnsignedLong count,
        final UnsignedLong headSlot,
        final NavigableMap<UnsignedLong, Bytes32> knownBlockRoots,
        final ResponseCallback<SignedBeaconBlock> callback) {
      this.currentSlot = startSlot;
      this.knownBlockRoots = knownBlockRoots;
      // Minus 1 to account for sending the block at startSlot.
      // We only decrement this when moving to the next slot but we're already at the first slot
      this.remainingBlocks = count.minus(ONE);
      this.step = step;
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

    SafeFuture<Void> sendBlock(final SignedBeaconBlock block) {
      return callback.respond(block);
    }

    void incrementCurrentSlot() {
      remainingBlocks = remainingBlocks.minus(ONE);
      currentSlot = currentSlot.plus(step);
    }

    SafeFuture<Optional<SignedBeaconBlock>> loadNextBlock() {
      final UnsignedLong slot = this.currentSlot;
      final Bytes32 knownBlockRoot = knownBlockRoots.get(slot);
      if (knownBlockRoot != null) {
        // Known root so lookup by root
        return combinedChainDataClient
            .getBlockByBlockRoot(knownBlockRoot)
            .thenApply(maybeBlock -> maybeBlock.filter(block -> block.getSlot().equals(slot)));
      } else if ((!knownBlockRoots.isEmpty() && slot.compareTo(knownBlockRoots.firstKey()) >= 0)
          || slot.compareTo(headSlot) > 0) {
        // Unknown root but not finalized means this is an empty slot
        // Could also be because the first block requested is above our head slot
        return SafeFuture.completedFuture(Optional.empty());
      } else {
        // Must be a finalized block so lookup by slot
        return combinedChainDataClient.getBlockAtSlotExact(slot);
      }
    }
  }
}
