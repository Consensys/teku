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
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
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
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.statetransition.CustodyGroupCountChannel;
import tech.pegasys.teku.statetransition.blobs.RemoteOrigin;
import tech.pegasys.teku.statetransition.datacolumns.retriever.DataColumnSidecarRetriever;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;
import tech.pegasys.teku.storage.client.ChainHead;
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

  private volatile int currentSyncCustodyGroupCount;
  private volatile boolean requiresResyncDueToCustodyGroupCountChange;

  private final AtomicReference<BatchData> currentBatch = new AtomicReference<>(null);
  private final Map<DataColumnSlotAndIdentifier, PendingRequest> pendingRequests =
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
    scheduledBackfiller =
        Optional.of(
            asyncRunner.runWithFixedDelay(
                this::fillUp,
                Duration.ZERO,
                backfillCheckInterval,
                error -> LOG.error("Failed to run data column backfill", error)));
    return SafeFuture.COMPLETE;
  }

  @Override
  protected synchronized SafeFuture<?> doStop() {
    scheduledBackfiller.ifPresent(Cancellable::cancel);
    return SafeFuture.COMPLETE;
  }

  @Override
  public void onGroupCountUpdate(final int custodyGroupCount, final int samplingGroupCount) {
    if (custodyGroupCount > currentSyncCustodyGroupCount) {
      currentSyncCustodyGroupCount = custodyGroupCount;
      requiresResyncDueToCustodyGroupCountChange = true;
    }
  }

  @Override
  public void onCustodyGroupCountSynced(final int groupCount) {}

  private record BatchData(
      UInt64 fromSlot,
      UInt64 toSlot,
      List<UInt64> requiredColumnsInCustody,
      List<DataColumnSlotAndIdentifier> columnsInCustody,
      List<SlotAndBlockRootWithBlobsPresence> blocksInfo) {

    Optional<UInt64> oldestExistingBlockInBatch() {
      return blocksInfo.stream()
              .filter(s -> s.blockRoot.isPresent())
              .map(SlotAndBlockRootWithBlobsPresence::slot)
              .min(UInt64::compareTo);
    }

  }

  @Override
  public void onNewFinalizedCheckpoint(
      final Checkpoint checkpoint, final boolean fromOptimisticBlock) {}

  /*
   * TODO:
   *
   * - ottenere tutti i blockroot per blocchi che hanno blobs (getBlockRootWithBlobs)
   *    ( se lo slot è non finalized, usare getAllBlockRootsAtSlot)
   *
   * - con i dati (earliestSlotInBatch, latestSlotInBatch, List<BlockRoots>) accedere al db
   * via getColumnIdentifiers(earliestSlotInBatch, latestSlotInBatch) (to be implemented)
   *
   * - calcolare i missingcolums con il risultato della query
   *
   * - avviare RPC
   *
   * - onFinalization: gestire cancellazione di pending requests relative a columns che non sono più canonical
   *
   * - a completamento del sync, notificare custodyGroupCountSynced
   *
   * - se aumenta il custodyCount, resettare earliestAvailable to current head slot. (triggera fillup)
   *
   *
   * - quando
   *
   * optionals:
   * - batch size dinamico in base al custody count?
   *
   * -
   */

  //                      combinedChainDataClient.getBlockByBlockRoot()
  //
  //      combinedChainDataClient.getRecentChainData().getAllBlockRootsAtSlot()
  //
  //      combinedChainDataClient.getFinalizedBlockSlot()
  //
  //              // to be used when the batch has all empty slots.
  //                      // This is to cover the case of a sequence of empty slots
  // bigger than our batch size. So we can skip all empty slots and go directly to the
  // previous existing block (if there is no such block, it means we are still
  // backfilling them)
  //                      combinedChainDataClient.getBlockInEffectAtSlot()

  private SafeFuture<Void> eventuallyResetEarliestAvailableCustodySlot(final Optional<UInt64> maybeSlot) {
    return maybeSlot
            .map(
                    earliestAvailableCustodySlotWriter).orElse(SafeFuture.COMPLETE);
  }

  void fillUp() {
    LOG.info("Starting data column backfill. current pending requests: {}", pendingRequests.size());

        if (!backfilling.compareAndSet(false, true)) {
          //
          return;
        }

    //    recentChainData.retrieveBlockByRoot()
    //    recentChainData.getAllBlockRootsAtSlot()

    // combinedChainDataClient.getCurrentSlot()

    if (requiresResyncDueToCustodyGroupCountChange) {
      // move EarliestAvailableCustodySlot to current head to trigger a full backfill to do a fillup
      // of additional columns to custody
      eventuallyResetEarliestAvailableCustodySlot(Optional.of(combinedChainDataClient.getCurrentSlot())).always(this::checkBatchCompletion);
      requiresResyncDueToCustodyGroupCountChange = false;
      return;
    } else if(currentBatch.get() != null) {
      eventuallyResetEarliestAvailableCustodySlot(currentBatch.get().oldestExistingBlockInBatch()).always(this::checkBatchCompletion);;
      return;
    }

    earliestAvailableCustodySlotProvider
        .get()
        .thenCompose(
            earliestSlot -> {
              LOG.info("Earliest available checkpoint for {} slot", earliestSlot);

              if (earliestSlot.isEmpty()) {
                eventuallyResetEarliestAvailableCustodySlot(combinedChainDataClient.getChainHead().map(ChainHead::getSlot));
                return SafeFuture.completedFuture(Optional.<BatchData>empty());
              }

              if (earliestSlot
                  .get()
                  .isLessThanOrEqualTo(
                      minCustodyPeriodSlotCalculator.getMinCustodyPeriodSlot(
                          combinedChainDataClient.getCurrentSlot()))) {
                return SafeFuture.completedFuture(Optional.<BatchData>empty());
              }

              var latestFinalizedSlot = combinedChainDataClient.getFinalizedBlockSlot();
              if (latestFinalizedSlot.isEmpty()) {
                return SafeFuture.completedFuture(Optional.<BatchData>empty());
              }

              var latestSlotInBatch = earliestSlot.get();
              var oldestCustodySlot =
                  minCustodyPeriodSlotCalculator.getMinCustodyPeriodSlot(
                      combinedChainDataClient.getCurrentSlot());
              var earliestSlotInBatch =
                  latestSlotInBatch.minusMinZero(batchSizeInSlots).max(oldestCustodySlot);

              return combinedChainDataClient
                  .getDataColumnIdentifiers(
                      earliestSlotInBatch, latestSlotInBatch, UInt64.valueOf(100_000))
                  .thenCombine(
                      retrieveBlocksWithBlobsInRange(earliestSlotInBatch, latestSlotInBatch),
                      (dataColumnSlotAndIdentifiers, slotAndBlockRootWithBlobsPresences) ->
                          Optional.of(
                              new BatchData(
                                  earliestSlotInBatch,
                                  latestSlotInBatch,
                                      custodyGroupCountManager.getCustodyColumnIndices(),
                                  dataColumnSlotAndIdentifiers,
                                  slotAndBlockRootWithBlobsPresences)));
            })
        .thenAccept(
            maybeBatchData -> {
              if (maybeBatchData.isEmpty()) {
                return;
              }
              final BatchData batchData = maybeBatchData.get();
              currentBatch.set(batchData);

              Map<SlotAndBlockRoot, List<UInt64>> missingCustodyForKnownSlotAndRoot =
                      batchData.columnsInCustody.stream()
                          // only include columns related to block we have imported
                      .filter(
                          dataColumnSlotAndIdentifier ->
                                  batchData.blocksInfo.stream()
                                  .anyMatch(
                                      blocksInfo ->
                                          dataColumnSlotAndIdentifier.slot().equals(blocksInfo.slot)
                                              && blocksInfo.blockRoot().map(b -> b.equals(dataColumnSlotAndIdentifier.blockRoot())).orElse(false)))
                      .collect(
                          Collectors.groupingBy(DataColumnSlotAndIdentifier::getSlotAndBlockRoot))
                      .entrySet()
                      .stream()
                      .map(
                          entry -> {
                            var columnsInCustody = entry.getValue();

                            // remove columns we already custody
                            return Map.entry(
                                entry.getKey(),
                                    batchData.requiredColumnsInCustody.stream()
                                    .filter(
                                        id ->
                                            columnsInCustody.stream()
                                                .noneMatch(c -> c.columnIndex().equals(id)))
                                    .toList());
                          })
                      .collect(Collectors.toMap(Entry::getKey, Entry::getValue));

              // add columns for block which has blobs, but we have no columns for
              batchData.blocksInfo.stream()
                  .filter(SlotAndBlockRootWithBlobsPresence::hasBlobs)
                  .forEach(
                      bi ->
                          missingCustodyForKnownSlotAndRoot.putIfAbsent(
                              new SlotAndBlockRoot(bi.slot, bi.blockRoot.orElseThrow()),
                                  batchData.requiredColumnsInCustody));

              // prepare the columns to request
              final Set<DataColumnSlotAndIdentifier> missingColumnsToRequest =
                  missingCustodyForKnownSlotAndRoot.entrySet().stream()
                      .flatMap(
                          entry ->
                              entry.getValue().stream()
                                  .map(
                                      id ->
                                          new DataColumnSlotAndIdentifier(
                                              entry.getKey().getSlot(),
                                              entry.getKey().getBlockRoot(),
                                              id)))
                      .filter(columnSlotId -> !pendingRequests.containsKey(columnSlotId))
                      .collect(Collectors.toSet());

              updateEarliestAvailableCustodySlot(missingColumnsToRequest);

              for (final DataColumnSlotAndIdentifier missingColumn : missingColumnsToRequest) {
                if (missingColumn
                    .slot()
                    .isGreaterThan(
                        minCustodyPeriodSlotCalculator.getMinCustodyPeriodSlot(
                            combinedChainDataClient.getCurrentSlot()))) {
                  LOG.info("Adding missing column {} to custody", missingColumn);
                  addPendingRequest(missingColumn);
                } else {
                  LOG.info(
                      "Skipping column from slot {} as it is outside of the retention period now.",
                      missingColumn.getSlotAndBlockRoot().getSlot());
                }
              }
            })
            .alwaysRun(this::checkBatchCompletion)
            .finishStackTrace();

  }

  private void updateEarliestAvailableCustodySlot(final Set<DataColumnSlotAndIdentifier> missingColumnsToRequest) {
    // Check if we are done with the current batch (no columns left to request)
    if (missingColumnsToRequest.isEmpty()) {

      var oldestExistingBlockInBatch = currentBatch.get().oldestExistingBlockInBatch();

      if (oldestExistingBlockInBatch.isPresent()) {
        // OPTIMIZATION: The batch contained blocks.
        // We can simply move the cursor to the slot immediately prior to the oldest block found.
        // No database lookup (getBlockInEffectAtSlot) is required here.
        UInt64 nextSlot = oldestExistingBlockInBatch.get().minusMinZero(1);
        earliestAvailableCustodySlotWriter.apply(nextSlot).finishError(LOG);

      } else {
        // The batch was empty (gap).
        // Use getBlockInEffectAtSlot to efficiently skip the empty slots and find the
        // previous actual block, preventing a step-by-step walk through a long empty epoch.
        combinedChainDataClient
                .getBlockInEffectAtSlot(currentBatch.get().fromSlot().minusMinZero(1))
                .thenAccept(
                        maybeBlockInEffect ->
                                maybeBlockInEffect.ifPresent(
                                        blockInEffect ->
                                                earliestAvailableCustodySlotWriter
                                                        .apply(blockInEffect.getSlot())
                                                        .finishError(LOG)))
                .finishError(LOG);
      }
    }
  }

  private boolean checkBatchCompletion() {
    if(pendingRequests.isEmpty()) {
      final BatchData localBatch = currentBatch.getAndSet(null);
      if(localBatch!=null) {
        if(localBatch.fromSlot.isLessThanOrEqualTo(
                minCustodyPeriodSlotCalculator.getMinCustodyPeriodSlot(
                        combinedChainDataClient.getCurrentSlot()))) {
          custodyGroupCountManager.setCustodyGroupSyncedCount(localBatch.requiredColumnsInCustody().size());
        }
      }
      return backfilling.compareAndSet(true, false);
    }
    return false;
  }

  record SlotAndBlockRootWithBlobsPresence(
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

  private synchronized void addPendingRequest(final DataColumnSlotAndIdentifier missingColumn) {
    if (pendingRequests.containsKey(missingColumn)) {
      return;
    }
    final SafeFuture<DataColumnSidecar> future = retriever.retrieve(missingColumn);
    final PendingRequest request = new PendingRequest(missingColumn, future);
    pendingRequests.put(missingColumn, request);
    future.finish(
        response -> onRequestComplete(request, response), err -> onRequestException(request, err));
  }

  private void onRequestComplete(final PendingRequest request, final DataColumnSidecar response) {
    dataColumnSidecarCustody
        .onNewValidatedDataColumnSidecar(response, RemoteOrigin.RPC)
        .thenRun(
            () -> {
              removePendingRequest(request.columnId);
                // syncedColumnCount.incrementAndGet();
            })
        .finishStackTrace();
  }

  private boolean wasCancelledImplicitly(final Throwable exception) {
    return exception instanceof CancellationException
        || (exception instanceof CompletionException
            && exception.getCause() instanceof CancellationException);
  }

  private void removePendingRequest(final DataColumnSlotAndIdentifier missingColumn) {
    pendingRequests.remove(missingColumn);
    if(checkBatchCompletion()) {
      fillUp();
    }
  }

  private synchronized void onRequestException(
      final PendingRequest request, final Throwable exception) {
    if (wasCancelledImplicitly(exception)) {
      // request was cancelled explicitly here
      removePendingRequest(request.columnId);
    } else {
      LOG.warn("Unexpected exception for request " + request, exception);
    }
  }

  private record PendingRequest(
      DataColumnSlotAndIdentifier columnId, SafeFuture<DataColumnSidecar> columnPromise) {}
}
