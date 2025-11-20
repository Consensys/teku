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

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.Cancellable;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
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

  // private final AtomicBoolean backfilling = new AtomicBoolean(false);

  private volatile int currentSyncCustodyGroupCount;
  private volatile boolean requiresResyncDueToCustodyGroupCountChange;

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

  @Override
  public void onNewFinalizedCheckpoint(
      final Checkpoint checkpoint, final boolean fromOptimisticBlock) {}

  void fillUp() {
    if (!pendingRequests.isEmpty()) {
      return;
    }
    //    if (!backfilling.compareAndSet(false, true)) {
    //      //
    //      return;
    //    }

    //    recentChainData.retrieveBlockByRoot()
    //    recentChainData.getAllBlockRootsAtSlot()

    // combinedChainDataClient.getCurrentSlot()

    if (requiresResyncDueToCustodyGroupCountChange) {
      // move EarliestAvailableCustodySlot to current head to trigger a full backfill to do a fillup
      // of additional columns to custody
      combinedChainDataClient
          .getChainHead()
          .ifPresent(
              chainHead ->
                  earliestAvailableCustodySlotWriter.apply(chainHead.getSlot()).finishError(LOG));
      requiresResyncDueToCustodyGroupCountChange = false;
    }

    earliestAvailableCustodySlotProvider
        .get()
        .thenCompose(
            earliestSlot -> {
              var latestFinalizedSlot = combinedChainDataClient.getFinalizedBlockSlot();
              if (earliestSlot.isEmpty() || latestFinalizedSlot.isEmpty()) {
                return SafeFuture.completedFuture(List.of());
              }

              var latestSlotInBatch = earliestSlot.get();
              var oldestCustodySlot =
                  minCustodyPeriodSlotCalculator.getMinCustodyPeriodSlot(
                      combinedChainDataClient.getCurrentSlot());
              var earliestSlotInBatch =
                  latestSlotInBatch.minusMinZero(batchSizeInSlots).max(oldestCustodySlot);

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

              return combinedChainDataClient.getDataColumnIdentifiers(
                  earliestSlotInBatch, latestSlotInBatch, UInt64.MAX_VALUE);

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

            })
        .thenAccept(
            dataColumnSlotAndIdentifiers -> {
              var custodyRequirement = custodyGroupCountManager.getCustodyColumnIndices();

              // dataColumnSlotAndIdentifiers.stream().filter(c ->
              // custodyRequirement.contains(c.columnIndex()))

              Map<SlotAndBlockRoot, List<UInt64>> missingCustodyForKnownSlotAndRoot =
                  dataColumnSlotAndIdentifiers.stream()
                      .collect(
                          Collectors.groupingBy(DataColumnSlotAndIdentifier::getSlotAndBlockRoot))
                      .entrySet()
                      .stream()
                      .map(
                          entry -> {
                            var columnsInCustody = entry.getValue();

                            return Map.entry(
                                entry.getKey(),
                                custodyRequirement.stream()
                                    .filter(
                                        id ->
                                            columnsInCustody.stream()
                                                .noneMatch(c -> c.columnIndex().equals(id)))
                                    .toList());
                          })
                      .collect(Collectors.toUnmodifiableMap(Entry::getKey, Entry::getValue));

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

              for (final DataColumnSlotAndIdentifier missingColumn : missingColumnsToRequest) {
                if (missingColumn
                    .slot()
                    .isGreaterThan(
                        minCustodyPeriodSlotCalculator.getMinCustodyPeriodSlot(
                            combinedChainDataClient.getCurrentSlot()))) {
                  addPendingRequest(missingColumn);
                } else {
                  LOG.debug(
                      "Skipping column from slot {} as it is outside of the retention period now.",
                      missingColumn.getSlotAndBlockRoot().getSlot());
                }
              }
            })
        .finishError(LOG);

    //            .always(() -> backfilling.set(false));

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
              synchronized (this) {
                pendingRequests.remove(request.columnId);
                // syncedColumnCount.incrementAndGet();
                fillUp();
              }
            })
        .finishStackTrace();
  }

  private boolean wasCancelledImplicitly(final Throwable exception) {
    return exception instanceof CancellationException
        || (exception instanceof CompletionException
            && exception.getCause() instanceof CancellationException);
  }

  private synchronized void onRequestException(
      final PendingRequest request, final Throwable exception) {
    if (wasCancelledImplicitly(exception)) {
      // request was cancelled explicitly here
    } else {
      LOG.warn("Unexpected exception for request " + request, exception);
    }
  }

  private record PendingRequest(
      DataColumnSlotAndIdentifier columnId, SafeFuture<DataColumnSidecar> columnPromise) {}
}
