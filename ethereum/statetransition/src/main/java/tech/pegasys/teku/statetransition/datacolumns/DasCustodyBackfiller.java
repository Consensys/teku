/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.statetransition.datacolumns;

import static com.google.common.base.Preconditions.checkArgument;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.Cancellable;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.stream.AsyncStream;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeData;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.statetransition.CustodyGroupCountChannel;
import tech.pegasys.teku.statetransition.blobs.RemoteOrigin;
import tech.pegasys.teku.statetransition.datacolumns.retriever.DataColumnSidecarRetriever;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

public class DasCustodyBackfiller extends Service
    implements CustodyGroupCountChannel, FinalizedCheckpointChannel {
  public static final int BATCH_SIZE_IN_SLOTS = 10;

  private static final Logger LOG = LogManager.getLogger();

  private final AsyncRunner asyncRunner;
  private final CombinedChainDataClient combinedChainDataClient;
  private final Duration backfillCheckInterval;
  private final DataColumnSidecarCustody dataColumnSidecarCustody;
  private final CustodyGroupCountManager custodyGroupCountManager;
  private final DataColumnSidecarRetriever retriever;
  private final MinCustodyPeriodSlotCalculator minCustodyPeriodSlotCalculator;

  private Optional<Cancellable> scheduledBackfiller = Optional.empty();

  private final Supplier<SafeFuture<Optional<UInt64>>> earliestAvailableCustodySlotProvider;
  private final Function<UInt64, SafeFuture<Void>> earliestAvailableCustodySlotWriter;

  private final int batchSizeInSlots;

  private final AtomicBoolean backfilling = new AtomicBoolean(false);
  private final AtomicReference<Checkpoint> lastFinalizedCheckpoint = new AtomicReference<>();

  private volatile int currentSyncCustodyGroupCount;
  private volatile boolean requiresResyncDueToCustodyGroupCountChange;
  private volatile boolean isFirstRound = true;

  private final Map<DataColumnSlotAndIdentifier, SafeFuture<DataColumnSidecar>> pendingRequests =
      new ConcurrentHashMap<>();

  public DasCustodyBackfiller(
      final CombinedChainDataClient combinedChainDataClient,
      final Duration backfillCheckInterval,
      final DataColumnSidecarCustody dataColumnSidecarCustody,
      final CustodyGroupCountManager custodyGroupCountManager,
      final DataColumnSidecarRetriever retriever,
      final MinCustodyPeriodSlotCalculator minCustodyPeriodSlotCalculator,
      final AsyncRunner asyncRunner,
      final Supplier<SafeFuture<Optional<UInt64>>> earliestAvailableCustodySlotProvider,
      final Function<UInt64, SafeFuture<Void>> earliestAvailableCustodySlotWriter,
      final int batchSizeInSlots) {
    this.combinedChainDataClient = combinedChainDataClient;
    this.asyncRunner = asyncRunner;
    this.backfillCheckInterval = backfillCheckInterval;
    this.dataColumnSidecarCustody = dataColumnSidecarCustody;
    this.custodyGroupCountManager = custodyGroupCountManager;
    this.currentSyncCustodyGroupCount = custodyGroupCountManager.getCustodyGroupSyncedCount();
    this.batchSizeInSlots = batchSizeInSlots;
    this.retriever = retriever;
    this.minCustodyPeriodSlotCalculator = minCustodyPeriodSlotCalculator;
    this.earliestAvailableCustodySlotProvider = earliestAvailableCustodySlotProvider;
    this.earliestAvailableCustodySlotWriter = earliestAvailableCustodySlotWriter;
  }

  @Override
  protected synchronized SafeFuture<?> doStart() {
    if (scheduledBackfiller.map(Cancellable::isCancelled).orElse(true)) {
      scheduledBackfiller =
          Optional.of(
              asyncRunner.runWithFixedDelay(
                  this::runBackfillCycle,
                  Duration.ZERO,
                  backfillCheckInterval,
                  error -> LOG.error("Failed to run data column backfill", error)));
    }
    return SafeFuture.COMPLETE;
  }

  @Override
  protected synchronized SafeFuture<?> doStop() {
    scheduledBackfiller.ifPresent(Cancellable::cancel);
    pendingRequests.values().forEach(f -> f.cancel(true));
    pendingRequests.clear();
    return SafeFuture.COMPLETE;
  }

  @Override
  public void onGroupCountUpdate(final int custodyGroupCount, final int samplingGroupCount) {
    if (custodyGroupCount > currentSyncCustodyGroupCount) {
      currentSyncCustodyGroupCount = custodyGroupCount;
      requiresResyncDueToCustodyGroupCountChange = true;
      LOG.info("DasCustodyBackfiller: custody increase detected");
    }
  }

  @Override
  public void onCustodyGroupCountSynced(final int groupCount) {}

  /**
   * We want to cancel pending requests related to blocks that did not become canonical on
   * finalization
   */
  @Override
  public void onNewFinalizedCheckpoint(
      final Checkpoint checkpoint, final boolean fromOptimisticBlock) {
    if (pendingRequests.isEmpty()) {
      lastFinalizedCheckpoint.set(checkpoint);
      return;
    }
    final Spec spec = combinedChainDataClient.getRecentChainData().getSpec();

    final UInt64 fromSlot =
        Optional.ofNullable(lastFinalizedCheckpoint.getAndSet(checkpoint))
            .map(c -> c.getEpochStartSlot(spec))
            .orElse(UInt64.ZERO);
    final UInt64 toSlot = checkpoint.getEpochStartSlot(spec);

    final Map<Bytes32, List<SafeFuture<DataColumnSidecar>>> requestsByBlockRoot = new HashMap<>();

    pendingRequests.forEach(
        (columnId, rpcRequestFuture) -> {
          final UInt64 slot = columnId.slot();

          if (slot.isGreaterThan(fromSlot) && slot.isLessThanOrEqualTo(toSlot)) {

            // let's check only requests falling into the range

            requestsByBlockRoot
                .computeIfAbsent(columnId.blockRoot(), __ -> new ArrayList<>())
                .add(rpcRequestFuture);
          }
        });

    requestsByBlockRoot.forEach(
        (blockRoot, futures) ->
            combinedChainDataClient
                .getFinalizedSlotByBlockRoot(blockRoot)
                .thenAccept(
                    finalizedSlot -> {
                      // If the slot is empty, this block root is NOT in the finalized chain.
                      if (finalizedSlot.isEmpty()) {
                        LOG.debug(
                            "Cancelling {} pending data column request for finalized non canonical block {}",
                            futures.size(),
                            blockRoot);
                        futures.forEach(f -> f.cancel(true));
                      }
                    })
                .finishStackTrace());
  }

  private SafeFuture<Void> eventuallyResetEarliestAvailableCustodySlot(
      final Optional<UInt64> maybeSlot) {
    return maybeSlot.map(earliestAvailableCustodySlotWriter).orElse(SafeFuture.COMPLETE);
  }

  private void runBackfillCycle() {
    if (!backfilling.compareAndSet(false, true)) {
      LOG.debug(
          "DasCustodyBackfiller: Backfilling is in progress. Pending requests: {}",
          pendingRequests.size());
      return;
    }

    attemptNextBackfillStep()
        .handleComposed(
            (shouldRerunImmediately, error) -> {
              backfilling.set(false);

              if (error != null) {
                LOG.error("DasCustodyBackfiller: Error during data column backfill", error);
                return SafeFuture.COMPLETE;
              }

              if (shouldRerunImmediately) {
                return asyncRunner.runAsync(this::runBackfillCycle);
              }

              return SafeFuture.COMPLETE;
            })
        .finishStackTrace();
  }

  /**
   * Returns TRUE if the batch was processed successfully and we made progress, indicating we should
   * try the next batch immediately. Returns FALSE if we should wait for the scheduled interval.
   */
  private SafeFuture<Boolean> attemptNextBackfillStep() {
    final Optional<UInt64> minCustodyPeriodSlot =
        minCustodyPeriodSlotCalculator.getMinCustodyPeriodSlot(
            combinedChainDataClient.getCurrentSlot());

    if (minCustodyPeriodSlot.isEmpty()) {
      LOG.debug("Fulu not yet enabled");
      return SafeFuture.completedFuture(false);
    }

    if (requiresResyncDueToCustodyGroupCountChange) {
      requiresResyncDueToCustodyGroupCountChange = false;
      isFirstRound = false; // we don't need to do the first round
      return eventuallyResetEarliestAvailableCustodySlot(
              Optional.of(combinedChainDataClient.getCurrentSlot()))
          .thenApply(__ -> true);
    }

    if (isFirstRound) {
      return prepareAndExecuteFirstRoundBatch(minCustodyPeriodSlot.get());
    }

    return earliestAvailableCustodySlotProvider
        .get()
        .thenCompose(
            earliestAvailableCustodySlot ->
                this.prepareAndExecuteBatch(
                    earliestAvailableCustodySlot, minCustodyPeriodSlot.get()));
  }

  /**
   * The first batch after startup is a special batch that checks that our latest slots (chain
   * heads) have all custody. This is required because we write blocks and columns not in the same
   * transaction, so it is possible that the node shuts down after a block is written on disk but
   * before all custody columns has been written.
   */
  private SafeFuture<Boolean> prepareAndExecuteFirstRoundBatch(final UInt64 minCustodyPeriodSlot) {
    return getLatestChainHeadSlot()
        .map(
            latestSlot ->
                prepareBatch(
                        calculateEarliestSlotForBatch(latestSlot, minCustodyPeriodSlot),
                        latestSlot,
                        minCustodyPeriodSlot,
                        true)
                    .thenCompose(this::executeBatch)
                    .thenPeek(__ -> isFirstRound = false))
        .orElse(SafeFuture.completedFuture(true));
  }

  private Optional<UInt64> getLatestChainHeadSlot() {
    return combinedChainDataClient.getRecentChainData().getChainHeads().stream()
        .map(ProtoNodeData::getSlot)
        .max(UInt64::compareTo);
  }

  private SafeFuture<Boolean> prepareAndExecuteBatch(
      final Optional<UInt64> earliestAvailableCustodySlot, final UInt64 minCustodyPeriodSlot) {
    if (earliestAvailableCustodySlot.isEmpty()) {
      // is the db variable is not initialized
      // we are most likely coming from a checkpoint sync with a fresh DB
      // let's choose the most recent slot head +1, so that we make sure we cover the head in the
      // first batch
      final Optional<UInt64> mostRecentHeadSlotPlus1 =
          getLatestChainHeadSlot().map(UInt64::increment);

      return eventuallyResetEarliestAvailableCustodySlot(mostRecentHeadSlotPlus1)
          .thenApply(__ -> false);
    }

    LOG.debug(
        "DasCustodyBackfiller: earliestAvailableCustodySlot {}", earliestAvailableCustodySlot);

    if (earliestAvailableCustodySlot.get().isLessThanOrEqualTo(minCustodyPeriodSlot)) {

      return SafeFuture.completedFuture(false);
    }

    var latestSlotInBatch = earliestAvailableCustodySlot.get().minusMinZero(1);
    var earliestSlotInBatch =
        calculateEarliestSlotForBatch(latestSlotInBatch, earliestAvailableCustodySlot.get());

    return prepareBatch(earliestSlotInBatch, latestSlotInBatch, minCustodyPeriodSlot, false)
        .thenCompose(this::executeBatch);
  }

  private UInt64 calculateEarliestSlotForBatch(
      final UInt64 earliestSlot, final UInt64 minCustodyPeriodSlot) {
    return earliestSlot.minusMinZero(batchSizeInSlots - 1).max(minCustodyPeriodSlot);
  }

  private SafeFuture<BatchData> prepareBatch(
      final UInt64 fromSlot,
      final UInt64 toSlot,
      final UInt64 minCustodyPeriodSlot,
      final boolean isHeadsCustodyCheckBatch) {
    return combinedChainDataClient
        .getDataColumnIdentifiers(fromSlot, toSlot, UInt64.valueOf(100_000))
        .thenCombine(
            retrieveBlocksWithBlobsInRange(fromSlot, toSlot),
            (dataColumnSlotAndIdentifiers, slotAndBlockRootWithBlobsPresences) ->
                new BatchData(
                    fromSlot,
                    toSlot,
                    minCustodyPeriodSlot,
                    custodyGroupCountManager.getCustodyColumnIndices(),
                    dataColumnSlotAndIdentifiers,
                    slotAndBlockRootWithBlobsPresences,
                    isHeadsCustodyCheckBatch));
  }

  private SafeFuture<Boolean> executeBatch(final BatchData batchData) {
    LOG.info("DasCustodyBackfiller: Executing batch {}", batchData);

    final Map<SlotAndBlockRoot, List<UInt64>> missingCustodyForKnownSlotAndRoot =
        calculateMissingColumns(batchData);

    final Set<DataColumnSlotAndIdentifier> missingColumnsToRequest =
        missingCustodyForKnownSlotAndRoot.entrySet().stream()
            .flatMap(
                entry ->
                    entry.getValue().stream()
                        .map(
                            missingColumnIndex ->
                                new DataColumnSlotAndIdentifier(
                                    entry.getKey().getSlot(),
                                    entry.getKey().getBlockRoot(),
                                    missingColumnIndex)))
            .collect(Collectors.toSet());

    final List<SafeFuture<Void>> rpcFutures = new ArrayList<>();
    for (DataColumnSlotAndIdentifier missingColumn : missingColumnsToRequest) {
      if (missingColumn.slot().isGreaterThanOrEqualTo(batchData.minCustodyPeriodSlot)) {
        rpcFutures.add(requestColumnSidecar(missingColumn));
      }
    }

    // We wait for the DB variable update AND all RPCs to finish.
    return SafeFuture.allOf(SafeFuture.allOf(rpcFutures.toArray(SafeFuture[]::new)))
        .thenCompose(__ -> calculateAndUpdateEarliestAvailableCustodySlot(batchData))
        .thenPeek(
            cursorUpdated -> {
              LOG.info("DasCustodyBackfiller: Batch completed: {}", batchData);
              if (!cursorUpdated) {
                LOG.info(
                    "DasCustodyBackfiller: No progress, probably still back filling blocks. Waiting next round.");
              }
            });
  }

  private Map<SlotAndBlockRoot, List<UInt64>> calculateMissingColumns(final BatchData batchData) {
    final Map<SlotAndBlockRoot, List<UInt64>> missingCustody =
        batchData.columnsInCustody.stream()
            .filter(
                columnId ->
                    batchData.blocksInfo.stream()
                        .anyMatch(
                            info ->
                                columnId.slot().equals(info.slot)
                                    && info.blockRoot()
                                        .map(b -> b.equals(columnId.blockRoot()))
                                        .orElse(false)))
            .collect(Collectors.groupingBy(DataColumnSlotAndIdentifier::getSlotAndBlockRoot))
            .entrySet()
            .stream()
            .map(
                entry -> {
                  var existingCols = entry.getValue();
                  return Map.entry(
                      entry.getKey(),
                      batchData.requiredColumnsInCustody.stream()
                          .filter(
                              id ->
                                  existingCols.stream().noneMatch(c -> c.columnIndex().equals(id)))
                          .toList());
                })
            .collect(Collectors.toMap(Entry::getKey, Entry::getValue));

    batchData.blocksInfo.stream()
        .filter(SlotAndBlockRootWithBlobsPresence::hasBlobs)
        .forEach(
            bi ->
                missingCustody.putIfAbsent(
                    new SlotAndBlockRoot(bi.slot, bi.blockRoot.orElseThrow()),
                    batchData.requiredColumnsInCustody));
    return missingCustody;
  }

  /**
   * Moves the cursor backwards based on batch contents. Returns TRUE if cursor was updated, or if
   * it is a isHeadsCustodyCheckBatch batch Returns FALSE if no update (empty slots gap).
   */
  private SafeFuture<Boolean> calculateAndUpdateEarliestAvailableCustodySlot(
      final BatchData batchData) {
    if (batchData.isHeadsCustodyCheckBatch) {
      return SafeFuture.completedFuture(true);
    }

    var oldestExistingBlockInBatch = batchData.oldestExistingBlockInBatch();

    if (oldestExistingBlockInBatch.isPresent()) {
      // Batch has blocks, just move earliestAvailableCustodySlot back to last existing block in the
      // batch
      final UInt64 nextSlot = oldestExistingBlockInBatch.get();
      return updateEarliestAvailableCustodySlot(nextSlot, batchData);
    } else {
      // No blocks in batch, lookup most recent block from the oldest slot of the batch
      return combinedChainDataClient
          .getBlockInEffectAtSlot(batchData.fromSlot().minusMinZero(1))
          .thenCompose(
              maybeBlockInEffect ->
                  maybeBlockInEffect
                      .map(
                          block ->
                              updateEarliestAvailableCustodySlot(
                                  block.getSlot().increment().max(batchData.minCustodyPeriodSlot),
                                  batchData))

                      // there is no block in effect prior to our batch, still backfilling blocks
                      // so return false to wait for the next round
                      .orElseGet(() -> SafeFuture.completedFuture(false)));
    }
  }

  private SafeFuture<Boolean> updateEarliestAvailableCustodySlot(
      final UInt64 slot, final BatchData batchData) {
    return earliestAvailableCustodySlotWriter
        .apply(slot)
        .thenApply(
            __ -> {
              if (slot.isLessThanOrEqualTo(batchData.minCustodyPeriodSlot)) {
                custodyGroupCountManager.setCustodyGroupSyncedCount(
                    batchData.requiredColumnsInCustody.size());

                LOG.debug("DasCustodyBackfiller: Column custody backfill completed successfully.");
                return false;
              }

              return true;
            });
  }

  private SafeFuture<Void> requestColumnSidecar(final DataColumnSlotAndIdentifier colId) {
    if (pendingRequests.containsKey(colId)) {
      return SafeFuture.COMPLETE;
    }

    LOG.debug("DasCustodyBackfiller: Retrieving missing column {} to custody", colId);

    final SafeFuture<DataColumnSidecar> req = retriever.retrieve(colId);
    pendingRequests.put(colId, req);

    return req.thenPeek(
            __ -> LOG.debug("DasCustodyBackfiller: Data column sidecar {} retrieved.", colId))
        .thenCompose(
            sidecar ->
                dataColumnSidecarCustody.onNewValidatedDataColumnSidecar(sidecar, RemoteOrigin.RPC))
        .thenPeek(__ -> LOG.debug("DasCustodyBackfiller: Data column sidecar {} stored.", colId))
        .ignoreCancelException()
        .catchAndRethrow(
            err ->
                LOG.debug("DasCustodyBackfiller: Error retrieving missing column {}", colId, err))
        .alwaysRun(() -> pendingRequests.remove(colId));
  }

  private SafeFuture<List<SlotAndBlockRootWithBlobsPresence>> retrieveBlocksWithBlobsInRange(
      final UInt64 fromSlot, final UInt64 toSlot) {
    return AsyncStream.createUnsafe(UInt64.rangeClosed(fromSlot, toSlot).iterator())
        .flatMap(
            slot -> {
              if (combinedChainDataClient.isFinalized(slot)) {
                return AsyncStream.create(
                        combinedChainDataClient.getFinalizedBlockAtSlotExact(slot))
                    .map(block -> SlotAndBlockRootWithBlobsPresence.fromBlockAtSlot(block, slot));
              }
              return AsyncStream.createUnsafe(
                      combinedChainDataClient
                          .getRecentChainData()
                          .getAllBlockRootsAtSlot(slot)
                          .iterator())
                  .mapAsync(
                      blockRoot ->
                          combinedChainDataClient
                              .getRecentChainData()
                              .retrieveSignedBlockByRoot(blockRoot))
                  .map(block -> SlotAndBlockRootWithBlobsPresence.fromBlockAtSlot(block, slot));
            })
        .toList();
  }

  private record BatchData(
      UInt64 fromSlot,
      UInt64 toSlot,
      UInt64 minCustodyPeriodSlot,
      List<UInt64> requiredColumnsInCustody,
      List<DataColumnSlotAndIdentifier> columnsInCustody,
      List<SlotAndBlockRootWithBlobsPresence> blocksInfo,
      boolean isHeadsCustodyCheckBatch) {

    Optional<UInt64> oldestExistingBlockInBatch() {
      return blocksInfo.stream()
          .filter(s -> s.blockRoot.isPresent())
          .map(SlotAndBlockRootWithBlobsPresence::slot)
          .min(UInt64::compareTo);
    }
  }

  private record SlotAndBlockRootWithBlobsPresence(
      UInt64 slot, Optional<Bytes32> blockRoot, boolean hasBlobs) {
    static SlotAndBlockRootWithBlobsPresence fromBlockAtSlot(
        final Optional<SignedBeaconBlock> block, final UInt64 slot) {
      if (block.isEmpty()) {
        return new SlotAndBlockRootWithBlobsPresence(slot, Optional.empty(), false);
      }
      checkArgument(block.get().getSlot().equals(slot), "Inconsistent block slot");
      return new SlotAndBlockRootWithBlobsPresence(
          slot,
          block.map(SignedBeaconBlock::getRoot),
          block
              .get()
              .getMessage()
              .getBody()
              .getOptionalBlobKzgCommitments()
              .map(commitments -> !commitments.isEmpty())
              .orElse(false));
    }
  }
}
