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

package tech.pegasys.teku.sync.historical;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import java.util.Deque;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockSummary;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.rpc.core.InvalidResponseException;
import tech.pegasys.teku.networking.p2p.peer.DisconnectReason;
import tech.pegasys.teku.networking.p2p.peer.PeerDisconnectedException;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;

/** Fetches a target batch of blocks from a peer. */
public class HistoricalBatchFetcher {
  private static final Logger LOG = LogManager.getLogger();

  private static final int MAX_REQUESTS = 2;

  private final StorageUpdateChannel storageUpdateChannel;
  private final Eth2Peer peer;
  private final UInt64 maxSlot;
  private final Bytes32 lastBlockRoot;
  private final UInt64 batchSize;
  private final int maxRequests;

  private final SafeFuture<BeaconBlockSummary> future = new SafeFuture<>();
  private final Deque<SignedBeaconBlock> blocksToImport = new ConcurrentLinkedDeque<>();
  private final AtomicInteger requestCount = new AtomicInteger(0);
  private volatile boolean requestedBlockDirectly = false;

  /**
   * @param storageUpdateChannel The storage channel where finalized blocks will be imported
   * @param peer The peer to request blocks from
   * @param maxSlot The maxSlot to pull
   * @param lastBlockRoot The block root that defines the last block in our batch
   * @param batchSize The number of blocks to sync (assuming all slots are filled)
   * @param maxRequests The number of blocksByRange requests allowed to pull this batch
   */
  @VisibleForTesting
  HistoricalBatchFetcher(
      final StorageUpdateChannel storageUpdateChannel,
      final Eth2Peer peer,
      final UInt64 maxSlot,
      final Bytes32 lastBlockRoot,
      final UInt64 batchSize,
      final int maxRequests) {
    this.storageUpdateChannel = storageUpdateChannel;
    this.peer = peer;
    this.maxSlot = maxSlot;
    this.lastBlockRoot = lastBlockRoot;
    this.batchSize = batchSize;
    this.maxRequests = maxRequests;
  }

  public static HistoricalBatchFetcher create(
      final StorageUpdateChannel storageUpdateChannel,
      final Eth2Peer peer,
      final UInt64 maxSlot,
      final Bytes32 lastBlockRoot,
      final UInt64 batchSize) {
    return new HistoricalBatchFetcher(
        storageUpdateChannel, peer, maxSlot, lastBlockRoot, batchSize, MAX_REQUESTS);
  }

  /**
   * Fetch the batch of blocks up to {@link #maxSlot}, save them to the database, and return the new
   * value for the earliest block.
   *
   * @return A future that resolves with the earliest block pulled and saved.
   */
  public SafeFuture<BeaconBlockSummary> run() {
    requestBlocks();
    return future;
  }

  private void requestBlocks() {
    requestBlocks(calculateRequestParams());
  }

  private void requestBlocks(RequestParameters requestParams) {
    LOG.trace(
        "Request {} blocks from {} to {}",
        requestParams.getCount(),
        requestParams.getStartSlot(),
        requestParams.getEndSlot());
    final RequestManager requestManager =
        new RequestManager(lastBlockRoot, getLatestReceivedBlock(), blocksToImport::addLast);
    peer.requestBlocksByRange(
            requestParams.getStartSlot(),
            requestParams.getCount(),
            UInt64.ONE,
            requestManager::processBlock)
        .finish(__ -> this.handleCompletedRequest(), this::handleRequestError);
  }

  private void handleRequestError(final Throwable throwable) {
    final Throwable rootCause = Throwables.getRootCause(throwable);
    if (rootCause instanceof PeerDisconnectedException) {
      complete();
    }
    if (rootCause instanceof InvalidResponseException) {
      // Disconnect misbehaving peer
      LOG.debug("Received invalid response from peer. Disconnecting: " + peer, throwable);
      peer.disconnectCleanly(DisconnectReason.REMOTE_FAULT).reportExceptions();
      future.completeExceptionally(throwable);
    } else {
      future.completeExceptionally(throwable);
    }
  }

  private void handleCompletedRequest() {
    processResponses(true);
  }

  private void complete() {
    processResponses(false);
  }

  private void processResponses(final boolean allowAdditionalRequests) {
    final Optional<SignedBeaconBlock> latestBlock = getLatestReceivedBlock();
    final boolean latestBlockCompletesBatch =
        latestBlock.map(SignedBeaconBlock::getRoot).map(r -> r.equals(lastBlockRoot)).orElse(false);
    if (latestBlockCompletesBatch) {
      LOG.trace("Import batch of {} blocks", blocksToImport.size());
      importBatch();
    } else if (allowAdditionalRequests && requestCount.incrementAndGet() < maxRequests) {
      final RequestParameters requestParameters = calculateRequestParams();
      if (requestParameters.getCount().equals(UInt64.ZERO)) {
        // Nothing left to request but the batch is incomplete
        // It appears our peer is on a different chain
        LOG.warn("Received invalid blocks from a different chain. Disconnecting peer: " + peer);
        peer.disconnectCleanly(DisconnectReason.IRRELEVANT_NETWORK).reportExceptions();
        future.completeExceptionally(
            new InvalidResponseException("Received invalid blocks from a different chain"));
      } else {
        requestBlocks();
      }
    } else if (blocksToImport.size() == 0 && !requestedBlockDirectly) {
      // If we've received no blocks in the range we want, this range could be empty - request the
      // next block directly
      requestTargetBlockDirectly();
    } else {
      // We haven't completed the batch and we've hit our request limit
      future.completeExceptionally(new InvalidResponseException("Failed to deliver full batch"));
    }
  }

  private void requestTargetBlockDirectly() {
    LOG.trace("Request next historical block directly by hash {}", lastBlockRoot);
    requestedBlockDirectly = true;
    peer.requestBlockByRoot(lastBlockRoot)
        .thenAccept(maybeBlock -> maybeBlock.ifPresent(blocksToImport::add))
        .finish(this::complete, this::handleRequestError);
  }

  private void importBatch() {
    final SignedBeaconBlock newEarliestBlock = blocksToImport.getFirst();
    storageUpdateChannel
        .onFinalizedBlocks(blocksToImport)
        .finish(() -> future.complete(newEarliestBlock), future::completeExceptionally);
  }

  private RequestParameters calculateRequestParams() {
    final UInt64 startSlot = getStartSlot();
    final UInt64 count = maxSlot.plus(1).minus(startSlot);
    return new RequestParameters(startSlot, count);
  }

  private UInt64 getStartSlot() {
    if (blocksToImport.isEmpty()) {
      return maxSlot.plus(1).safeMinus(batchSize).orElse(UInt64.ZERO);
    }

    return blocksToImport.getLast().getSlot().plus(1);
  }

  private Optional<SignedBeaconBlock> getLatestReceivedBlock() {
    return Optional.ofNullable(blocksToImport.peekLast());
  }

  private static class RequestManager {
    private final Bytes32 lastBlockRoot;
    private final Optional<SignedBeaconBlock> previousBlock;
    private final Consumer<SignedBeaconBlock> blockProcessor;

    private final AtomicInteger blocksReceived = new AtomicInteger(0);
    private final AtomicBoolean foundLastBlock = new AtomicBoolean(false);

    private RequestManager(
        final Bytes32 lastBlockRoot,
        final Optional<SignedBeaconBlock> previousBlock,
        final Consumer<SignedBeaconBlock> blockProcessor) {
      this.lastBlockRoot = lastBlockRoot;
      this.previousBlock = previousBlock;
      this.blockProcessor = blockProcessor;
    }

    private SafeFuture<?> processBlock(final SignedBeaconBlock block) {
      return SafeFuture.of(
          () -> {
            if (blocksReceived.incrementAndGet() == 1 && previousBlock.isPresent()) {
              if (!block.getParentRoot().equals(previousBlock.get().getRoot())) {
                throw new InvalidResponseException(
                    "Expected first block to descend from last received block.");
              }
            }

            // Only process blocks up to the last block - ignore any extra blocks
            if (!foundLastBlock.get()) {
              blockProcessor.accept(block);
            }
            if (block.getRoot().equals(lastBlockRoot)) {
              foundLastBlock.set(true);
            }

            return SafeFuture.COMPLETE;
          });
    }
  }

  private static class RequestParameters {
    private final UInt64 startSlot;
    private final UInt64 count;

    private RequestParameters(final UInt64 startSlot, final UInt64 count) {
      this.startSlot = startSlot;
      this.count = count;
    }

    public UInt64 getStartSlot() {
      return startSlot;
    }

    public UInt64 getCount() {
      return count;
    }

    public UInt64 getEndSlot() {
      return startSlot.plus(count).safeMinus(1).orElse(startSlot);
    }
  }
}
