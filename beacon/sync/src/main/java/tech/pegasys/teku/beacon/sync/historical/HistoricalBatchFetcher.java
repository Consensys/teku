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

package tech.pegasys.teku.beacon.sync.historical;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.exceptions.ExceptionUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.rpc.core.InvalidResponseException;
import tech.pegasys.teku.networking.p2p.peer.DisconnectReason;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSummary;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobIdentifier;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.util.AsyncBLSSignatureVerifier;
import tech.pegasys.teku.spec.logic.versions.deneb.blobs.BlobSidecarsAndValidationResult;
import tech.pegasys.teku.statetransition.blobs.BlobSidecarManager;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

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

  private final Spec spec;
  private final BlobSidecarManager blobSidecarManager;
  private final SafeFuture<BeaconBlockSummary> future = new SafeFuture<>();
  private final Deque<SignedBeaconBlock> blocksToImport = new ConcurrentLinkedDeque<>();
  private final Map<UInt64, List<BlobSidecar>> blobSidecarsBySlotToImport =
      new ConcurrentHashMap<>();
  private final AtomicInteger requestCount = new AtomicInteger(0);
  private final AsyncBLSSignatureVerifier signatureVerificationService;
  private final CombinedChainDataClient chainDataClient;

  /**
   * @param storageUpdateChannel The storage channel where finalized blocks will be imported
   * @param peer The peer to request blocks from
   * @param maxSlot The maxSlot to pull
   * @param lastBlockRoot The block root that defines the last block in our batch
   * @param batchSize The number of blocks to sync (assuming all slots are filled)
   */
  public HistoricalBatchFetcher(
      final StorageUpdateChannel storageUpdateChannel,
      final AsyncBLSSignatureVerifier signatureVerifier,
      final CombinedChainDataClient chainDataClient,
      final Spec spec,
      final BlobSidecarManager blobSidecarManager,
      final Eth2Peer peer,
      final UInt64 maxSlot,
      final Bytes32 lastBlockRoot,
      final UInt64 batchSize) {
    this(
        storageUpdateChannel,
        signatureVerifier,
        chainDataClient,
        spec,
        blobSidecarManager,
        peer,
        maxSlot,
        lastBlockRoot,
        batchSize,
        MAX_REQUESTS);
  }

  @VisibleForTesting
  HistoricalBatchFetcher(
      final StorageUpdateChannel storageUpdateChannel,
      final AsyncBLSSignatureVerifier signatureVerifier,
      final CombinedChainDataClient chainDataClient,
      final Spec spec,
      final BlobSidecarManager blobSidecarManager,
      final Eth2Peer peer,
      final UInt64 maxSlot,
      final Bytes32 lastBlockRoot,
      final UInt64 batchSize,
      final int maxRequests) {
    this.storageUpdateChannel = storageUpdateChannel;
    this.signatureVerificationService = signatureVerifier;
    this.chainDataClient = chainDataClient;
    this.spec = spec;
    this.blobSidecarManager = blobSidecarManager;
    this.peer = peer;
    this.maxSlot = maxSlot;
    this.lastBlockRoot = lastBlockRoot;
    this.batchSize = batchSize;
    this.maxRequests = maxRequests;
  }

  /**
   * Fetch the batch of blocks (and blob sidecars if required) up to {@link #maxSlot}, save them to
   * the database, and return the new value for the earliest block.
   *
   * @return A future that resolves with the earliest block pulled and saved.
   */
  public SafeFuture<BeaconBlockSummary> run() {
    SafeFuture.asyncDoWhile(this::requestBlocksAndBlobSidecarsByRange)
        .thenCompose(
            __ -> {
              if (blocksToImport.isEmpty()) {
                // If we've received no blocks, this range of blocks may be empty
                // Try to look up the next block by root
                return requestBlockByRoot();
              } else {
                return SafeFuture.COMPLETE;
              }
            })
        .thenCompose(__ -> complete())
        .finish(this::handleRequestError);

    return future;
  }

  private SafeFuture<Void> complete() {
    final Optional<SignedBeaconBlock> latestBlock = getLatestReceivedBlock();

    if (latestBlockCompletesBatch(latestBlock)) {
      LOG.trace("Import batch of {} blocks", blocksToImport.size());
      return importBatch();
    } else if (latestBlockShouldCompleteBatch(latestBlock)) {
      // Nothing left to request but the batch is incomplete
      // It appears our peer is on a different chain
      LOG.warn("Received invalid blocks from a different chain. Disconnecting peer: " + peer);
      peer.disconnectCleanly(DisconnectReason.IRRELEVANT_NETWORK).ifExceptionGetsHereRaiseABug();
      throw new InvalidResponseException("Received invalid blocks from a different chain");
    } else {
      // We haven't completed the batch, and we've hit our request limit
      throw new InvalidResponseException("Failed to deliver full batch");
    }
  }

  private void handleRequestError(final Throwable throwable) {
    final Throwable rootCause = Throwables.getRootCause(throwable);
    if (rootCause instanceof InvalidResponseException) {
      // Disconnect misbehaving peer
      LOG.debug("Received invalid response from peer. Disconnecting: " + peer, throwable);
      peer.disconnectCleanly(DisconnectReason.REMOTE_FAULT).ifExceptionGetsHereRaiseABug();
      future.completeExceptionally(throwable);
    } else {
      future.completeExceptionally(throwable);
    }
  }

  private boolean batchIsComplete() {
    return getLatestReceivedBlock()
        .map(SignedBeaconBlock::getRoot)
        .map(r -> r.equals(lastBlockRoot))
        .orElse(false);
  }

  private SafeFuture<Boolean> requestBlocksAndBlobSidecarsByRange() {
    final RequestParameters requestParams = calculateRequestParams();
    if (requestParams.getCount().equals(UInt64.ZERO)) {
      // Nothing left to request
      return SafeFuture.completedFuture(false);
    }

    final UInt64 endSlot = requestParams.getEndSlot();

    final RequestManager requestManager =
        new RequestManager(
            lastBlockRoot,
            getLatestReceivedBlock(),
            blocksToImport::addLast,
            this::processBlobSidecar);

    LOG.trace(
        "Request {} blocks from {} to {}",
        requestParams.getCount(),
        requestParams.getStartSlot(),
        endSlot);

    final SafeFuture<Void> blocksRequest =
        peer.requestBlocksByRange(
            requestParams.getStartSlot(), requestParams.getCount(), requestManager::processBlock);

    final SafeFuture<Void> blobSidecarsRequest;
    if (blobSidecarManager.isAvailabilityRequiredAtSlot(endSlot)) {
      LOG.trace(
          "Request {} blob sidecars from {} to {}",
          requestParams.getCount(),
          requestParams.getStartSlot(),
          endSlot);
      fillBlobSidecarsBySlotToImport(requestParams.getStartSlot(), endSlot);
      blobSidecarsRequest =
          peer.requestBlobSidecarsByRange(
              requestParams.getStartSlot(),
              requestParams.getCount(),
              requestManager::processBlobSidecar);
    } else {
      blobSidecarsRequest = SafeFuture.COMPLETE;
    }

    return SafeFuture.allOfFailFast(blocksRequest, blobSidecarsRequest)
        .thenApply(__ -> shouldRetryBlocksAndBlobSidecarsByRangeRequest());
  }

  // We explicitly mark post-Deneb slots in availability range as some could contain no BlobSidecars
  private void fillBlobSidecarsBySlotToImport(final UInt64 startSlot, final UInt64 endSlot) {
    // Whole range is in availability period
    if (blobSidecarManager.isAvailabilityRequiredAtSlot(startSlot)) {
      for (UInt64 currentSlot = startSlot;
          currentSlot.isLessThanOrEqualTo(endSlot);
          currentSlot = currentSlot.plus(1)) {
        blobSidecarsBySlotToImport.put(currentSlot, new ArrayList<>());
      }
    } else {
      for (UInt64 currentSlot = startSlot;
          currentSlot.isLessThanOrEqualTo(endSlot);
          currentSlot = currentSlot.plus(1)) {
        if (blobSidecarManager.isAvailabilityRequiredAtSlot(currentSlot)) {
          blobSidecarsBySlotToImport.put(currentSlot, new ArrayList<>());
        }
      }
    }
  }

  private void processBlobSidecar(final BlobSidecar blobSidecar) {
    blobSidecarsBySlotToImport.get(blobSidecar.getSlot()).add(blobSidecar);
  }

  private boolean shouldRetryBlocksAndBlobSidecarsByRangeRequest() {
    return !batchIsComplete() && requestCount.incrementAndGet() < maxRequests;
  }

  private SafeFuture<Void> requestBlockByRoot() {
    LOG.trace("Request next historical block directly by root {}", lastBlockRoot);
    return peer.requestBlockByRoot(lastBlockRoot)
        .thenCompose(
            maybeBlock -> {
              if (maybeBlock.isPresent()) {
                return processReceivedBlockByRoot(maybeBlock.get());
              }
              return SafeFuture.COMPLETE;
            });
  }

  private SafeFuture<Void> processReceivedBlockByRoot(final SignedBeaconBlock block) {
    blocksToImport.add(block);
    if (blobSidecarManager.isAvailabilityRequiredAtSlot(block.getSlot())) {
      final int numberOfKzgCommitments =
          block
              .getMessage()
              .getBody()
              .toVersionDeneb()
              .orElseThrow()
              .getBlobKzgCommitments()
              .size();
      if (numberOfKzgCommitments == 0) {
        LOG.trace(
            "Requesting blob sidecars for block with root {} is not necessary because there are no kzg commitments in the block",
            block.getRoot());
        return SafeFuture.COMPLETE;
      }
      return requestBlobSidecarsByRoot(block.getRoot(), numberOfKzgCommitments);
    }
    return SafeFuture.COMPLETE;
  }

  private SafeFuture<Void> requestBlobSidecarsByRoot(
      final Bytes32 blockRoot, final int requiredBlobSidecars) {
    LOG.trace(
        "Request {} associated blob sidecars for historical block with root {}",
        requiredBlobSidecars,
        blockRoot);
    final List<BlobIdentifier> blobIdentifiers =
        IntStream.range(0, requiredBlobSidecars)
            .mapToObj(index -> new BlobIdentifier(blockRoot, UInt64.valueOf(index)))
            .collect(Collectors.toList());
    return peer.requestBlobSidecarsByRoot(
        blobIdentifiers,
        blobSidecar -> {
          processBlobSidecar(blobSidecar);
          return SafeFuture.COMPLETE;
        });
  }

  private SafeFuture<Void> importBatch() {
    // send to signature verification and blob sidecars validation and only store blocks and blob
    // sidecars if all checks pass, or if one fails we reject the entire response
    return batchVerifyHistoricalBlockSignatures(blocksToImport)
        .thenCompose(
            __ -> {
              final UInt64 latestSlotInBatch = blocksToImport.getLast().getSlot();
              validateBlobSidecars(latestSlotInBatch, blocksToImport);

              final SignedBeaconBlock newEarliestBlock = blocksToImport.getFirst();
              return storageUpdateChannel
                  .onFinalizedBlocks(blocksToImport, new HashMap<>(blobSidecarsBySlotToImport))
                  .thenRun(
                      () -> {
                        LOG.trace("Earliest block is now from slot {}", newEarliestBlock.getSlot());
                        future.complete(newEarliestBlock);
                      });
            })
        // always clear the blob sidecars cache
        .alwaysRun(blobSidecarsBySlotToImport::clear);
  }

  SafeFuture<Void> batchVerifyHistoricalBlockSignatures(
      final Collection<SignedBeaconBlock> blocks) {

    return chainDataClient
        .getBestState()
        .orElseThrow()
        .thenCompose(bestState -> batchVerifyHistoricalBlockSignature(blocks, bestState));
  }

  private SafeFuture<Void> batchVerifyHistoricalBlockSignature(
      final Collection<SignedBeaconBlock> blocks, final BeaconState bestState) {
    final List<BLSSignature> signatures = new ArrayList<>();
    final List<Bytes> signingRoots = new ArrayList<>();
    final List<List<BLSPublicKey>> proposerPublicKeys = new ArrayList<>();

    final Bytes32 genesisValidatorsRoot = bestState.getForkInfo().getGenesisValidatorsRoot();

    blocks.forEach(
        signedBlock -> {
          final BeaconBlock block = signedBlock.getMessage();
          if (block.getSlot().isGreaterThan(SpecConfig.GENESIS_SLOT)) {
            final UInt64 epoch = spec.computeEpochAtSlot(block.getSlot());
            final Fork fork = spec.fork(epoch);
            final Bytes32 domain =
                spec.getDomain(Domain.BEACON_PROPOSER, epoch, fork, genesisValidatorsRoot);
            signatures.add(signedBlock.getSignature());
            signingRoots.add(spec.computeSigningRoot(block, domain));
            final BLSPublicKey proposerPublicKey =
                spec.getValidatorPubKey(bestState, block.getProposerIndex())
                    .orElseThrow(
                        () ->
                            new IllegalStateException(
                                "Proposer has to be in the state since state is more recent than the block proposed"));
            proposerPublicKeys.add(List.of(proposerPublicKey));
          }
        });

    return signatureVerificationService
        .verify(proposerPublicKeys, signingRoots, signatures)
        .thenAccept(
            signaturesValid -> {
              if (!signaturesValid) {
                throw new IllegalArgumentException("Batch signature verification failed");
              }
            });
  }

  private void validateBlobSidecars(
      final UInt64 latestSlotInBatch, final Collection<SignedBeaconBlock> blocks) {
    if (!blobSidecarManager.isAvailabilityRequiredAtSlot(latestSlotInBatch)) {
      return;
    }
    LOG.trace("Validating blob sidecars for a batch");
    blocks.forEach(this::validateBlobSidecars);
  }

  private void validateBlobSidecars(final SignedBeaconBlock block) {
    // We are in availability window, but lastBlock could be not pre-filled with empty list
    blobSidecarsBySlotToImport.putIfAbsent(block.getSlot(), Collections.emptyList());
    final List<BlobSidecar> blobSidecars = blobSidecarsBySlotToImport.get(block.getSlot());
    LOG.trace("Validating {} blob sidecars for block {}", blobSidecars.size(), block.getRoot());
    final BlobSidecarsAndValidationResult validationResult =
        blobSidecarManager.createAvailabilityChecker(block).validateImmediately(blobSidecars);

    if (validationResult.isFailure()) {
      final String causeMessage =
          validationResult
              .getCause()
              .map(cause -> " (" + ExceptionUtil.getRootCauseMessage(cause) + ")")
              .orElse("");
      throw new IllegalArgumentException(
          String.format(
              "Blob sidecars validation for block %s failed: %s%s",
              block.getRoot(), validationResult.getValidationResult(), causeMessage));
    }
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

  private boolean latestBlockCompletesBatch(final Optional<SignedBeaconBlock> latestBlock) {
    return latestBlock
        .map(SignedBeaconBlock::getRoot)
        .map(r -> r.equals(lastBlockRoot))
        .orElse(false);
  }

  private boolean latestBlockShouldCompleteBatch(final Optional<SignedBeaconBlock> latestBlock) {
    return latestBlock
        .map(SignedBeaconBlock::getSlot)
        .map(s -> s.isGreaterThanOrEqualTo(maxSlot))
        .orElse(false);
  }

  private static class RequestManager {
    private final Bytes32 lastBlockRoot;
    private final Optional<SignedBeaconBlock> previousBlock;
    private final Consumer<SignedBeaconBlock> blockProcessor;
    private final Consumer<BlobSidecar> blobSidecarProcessor;

    private final AtomicInteger blocksReceived = new AtomicInteger(0);
    private final AtomicBoolean foundLastBlock = new AtomicBoolean(false);

    private RequestManager(
        final Bytes32 lastBlockRoot,
        final Optional<SignedBeaconBlock> previousBlock,
        final Consumer<SignedBeaconBlock> blockProcessor,
        final Consumer<BlobSidecar> blobSidecarProcessor) {
      this.lastBlockRoot = lastBlockRoot;
      this.previousBlock = previousBlock;
      this.blockProcessor = blockProcessor;
      this.blobSidecarProcessor = blobSidecarProcessor;
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

    private SafeFuture<?> processBlobSidecar(final BlobSidecar blobSidecar) {
      blobSidecarProcessor.accept(blobSidecar);
      return SafeFuture.COMPLETE;
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
