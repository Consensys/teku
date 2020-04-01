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
import static tech.pegasys.artemis.util.async.SafeFuture.completedFuture;

import com.google.common.base.Throwables;
import com.google.common.primitives.UnsignedLong;
import org.apache.logging.log4j.LogManager;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.BeaconBlocksByRangeRequestMessage;
import tech.pegasys.artemis.networking.eth2.peers.Eth2Peer;
import tech.pegasys.artemis.networking.eth2.rpc.core.LocalMessageHandler;
import tech.pegasys.artemis.networking.eth2.rpc.core.ResponseCallback;
import tech.pegasys.artemis.networking.eth2.rpc.core.RpcException;
import tech.pegasys.artemis.storage.client.CombinedChainDataClient;
import tech.pegasys.artemis.util.async.SafeFuture;

public class BeaconBlocksByRangeMessageHandler
    implements LocalMessageHandler<BeaconBlocksByRangeRequestMessage, SignedBeaconBlock> {
  private static final org.apache.logging.log4j.Logger LOG = LogManager.getLogger();

  private final CombinedChainDataClient storageClient;

  public BeaconBlocksByRangeMessageHandler(final CombinedChainDataClient storageClient) {
    this.storageClient = storageClient;
  }

  @Override
  public void onIncomingMessage(
      final Eth2Peer peer,
      final BeaconBlocksByRangeRequestMessage message,
      final ResponseCallback<SignedBeaconBlock> callback) {
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
        .finish(
            callback::completeSuccessfully,
            error -> {
              final Throwable rootCause = Throwables.getRootCause(error);
              if (rootCause instanceof RpcException) {
                LOG.trace("Rejecting beacon blocks by range request", error); // Keep full context
                callback.completeWithError((RpcException) rootCause);
              } else {
                LOG.error("Failed to process blocks by range request", error);
                callback.completeWithError(RpcException.SERVER_ERROR);
              }
            });
  }

  private SafeFuture<?> sendMatchingBlocks(
      final BeaconBlocksByRangeRequestMessage message,
      final ResponseCallback<SignedBeaconBlock> callback) {
    return storageClient
        .getNonfinalizedBlockState(message.getHeadBlockRoot())
        .map(headState -> sendNextBlock(new RequestState(message, headState.getSlot(), callback)))
        .orElseGet(() -> completedFuture(null));
  }

  private SafeFuture<RequestState> sendNextBlock(final RequestState requestState) {
    return storageClient
        .getBlockAtSlotExact(requestState.currentSlot, requestState.headBlockRoot)
        .thenCompose(
            maybeBlock -> {
              maybeBlock.ifPresent(requestState::sendBlock);
              if (requestState.isComplete()) {
                return completedFuture(requestState);
              }
              requestState.incrementCurrentSlot();
              return sendNextBlock(requestState);
            });
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
        final ResponseCallback<SignedBeaconBlock> callback) {
      this.headBlockRoot = message.getHeadBlockRoot();
      this.currentSlot = message.getStartSlot();
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
