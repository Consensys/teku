/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.statetransition.datacolumns.retriever;

import com.google.common.annotations.VisibleForTesting;
import java.time.Duration;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.Cancellable;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;
import tech.pegasys.teku.statetransition.datacolumns.CanonicalBlockResolver;
import tech.pegasys.teku.statetransition.datacolumns.db.DataColumnSidecarDbAccessor;

/**
 * This class helps to recover sidecars which took a consider amount of time to retrieve by {@link
 * SimpleSidecarRetriever}. It achieves so by either reconstructing (if we have >= 50% of data
 * columns) or asking peers for sidecars until the 50% threshold is met in which case reconstruction
 * happens
 */
public class RecoveringSidecarRetriever implements DataColumnSidecarRetriever {
  private static final Logger LOG = LogManager.getLogger();

  private final DataColumnSidecarRetriever delegate;
  private final KZG kzg;
  private final MiscHelpersFulu specHelpers;
  private final CanonicalBlockResolver blockResolver;
  private final DataColumnSidecarDbAccessor sidecarDB;
  private final AsyncRunner asyncRunner;
  private final TimeProvider timeProvider;
  private final Duration recoveryInitiationTimeout;
  private final Duration recoveryInitiationCheckInterval;
  private final int numberOfColumns;
  private final int numberOfColumnsRequiredToReconstruct;

  private final Set<DataColumnSidecarRequestWithTimestamp> pendingRequests =
      new ConcurrentSkipListSet<>(
          // prioritise the earliest requests when checking if recovery needs to be initiated
          Comparator.comparing(DataColumnSidecarRequestWithTimestamp::timestamp)
              .thenComparing(DataColumnSidecarRequestWithTimestamp::dataColumnSlotAndIdentifier));
  private final Map<UInt64, RecoveryEntry> recoveryBySlot = new ConcurrentHashMap<>();

  private Cancellable cancellable;

  private record DataColumnSidecarRequestWithTimestamp(
      DataColumnSlotAndIdentifier dataColumnSlotAndIdentifier,
      SafeFuture<DataColumnSidecar> response,
      UInt64 timestamp) {}

  public RecoveringSidecarRetriever(
      final DataColumnSidecarRetriever delegate,
      final KZG kzg,
      final MiscHelpersFulu specHelpers,
      final CanonicalBlockResolver blockResolver,
      final DataColumnSidecarDbAccessor sidecarDB,
      final AsyncRunner asyncRunner,
      final Duration recoveryInitiationTimeout,
      final Duration recoveryInitiationCheckInterval,
      final TimeProvider timeProvider,
      final int numberOfColumns) {
    this.delegate = delegate;
    this.kzg = kzg;
    this.specHelpers = specHelpers;
    this.blockResolver = blockResolver;
    this.sidecarDB = sidecarDB;
    this.asyncRunner = asyncRunner;
    this.recoveryInitiationTimeout = recoveryInitiationTimeout;
    this.recoveryInitiationCheckInterval = recoveryInitiationCheckInterval;
    this.timeProvider = timeProvider;
    this.numberOfColumns = numberOfColumns;
    // reconstruction of all columns is possible with >= 50% of the column data
    this.numberOfColumnsRequiredToReconstruct = Math.ceilDiv(numberOfColumns, 2);
  }

  public synchronized void start() {
    if (cancellable != null) {
      return;
    }
    cancellable =
        asyncRunner.runWithFixedDelay(
            this::checkPendingRequests,
            recoveryInitiationCheckInterval,
            recoveryInitiationCheckInterval,
            error ->
                LOG.error(
                    "Failed to check if {} pending data column sidecars requests require recovery to be ran",
                    pendingRequests.size(),
                    error));
  }

  public synchronized void stop() {
    if (cancellable == null) {
      return;
    }
    cancellable.cancel();
    cancellable = null;
  }

  @Override
  public SafeFuture<DataColumnSidecar> retrieve(final DataColumnSlotAndIdentifier columnId) {
    final SafeFuture<DataColumnSidecar> response = delegate.retrieve(columnId);
    final DataColumnSidecarRequestWithTimestamp pendingRequest =
        new DataColumnSidecarRequestWithTimestamp(
            columnId, response, timeProvider.getTimeInMillis());
    pendingRequests.add(pendingRequest);

    // remove it from pending as soon as the response is done
    response.always(() -> pendingRequests.remove(pendingRequest));

    return response;
  }

  @Override
  public void flush() {
    delegate.flush();
  }

  @Override
  public void onNewValidatedSidecar(final DataColumnSidecar sidecar) {
    delegate.onNewValidatedSidecar(sidecar);
  }

  @VisibleForTesting
  int pendingRequestsCount() {
    return pendingRequests.size();
  }

  private void checkPendingRequests() {
    final UInt64 currentTime = timeProvider.getTimeInMillis();

    pendingRequests.removeIf(
        requestWithTimestamp -> {
          if (requestWithTimestamp.response.isDone()) {
            // If the response is already done, we can remove it
            return true;
          }
          // If the promise is not done, we check if it has timed out
          // TODO-fulu we probably need a better heuristics to submit requests for recovery
          // (https://github.com/Consensys/teku/issues/9465)
          if (requestWithTimestamp
              .timestamp
              .plus(recoveryInitiationTimeout.toMillis())
              .isGreaterThan(currentTime)) {
            // If the response is not timed out, we keep it
            return false;
          }
          // If the response is timed out, we maybe initiate recovery and remove it
          maybeInitiateRecovery(
              requestWithTimestamp.dataColumnSlotAndIdentifier, requestWithTimestamp.response);
          return true;
        });
  }

  private void maybeInitiateRecovery(
      final DataColumnSlotAndIdentifier columnId, final SafeFuture<DataColumnSidecar> response) {
    blockResolver
        .getBlockAtSlot(columnId.slot())
        .thenPeek(
            maybeBlock -> {
              if (!maybeBlock.map(b -> b.getRoot().equals(columnId.blockRoot())).orElse(false)) {
                LOG.trace("Recovery: CAN'T initiate recovery for {}", columnId);
                response.completeExceptionally(
                    new NotOnCanonicalChainException(columnId, maybeBlock));
              } else {
                final BeaconBlock block = maybeBlock.orElseThrow();
                LOG.trace("Recovery: initiating recovery for {}", columnId);
                final RecoveryEntry recovery = addRecovery(columnId, block);
                recovery.addRequest(columnId.columnIndex(), response);
              }
            })
        .finishStackTrace();
  }

  private RecoveryEntry addRecovery(
      final DataColumnSlotAndIdentifier columnId, final BeaconBlock block) {
    return recoveryBySlot.compute(
        columnId.slot(),
        (slot, existingRecovery) -> {
          if (existingRecovery != null
              && !existingRecovery.block.getRoot().equals(block.getRoot())) {
            // we are recovering obsolete column which is no more on our canonical chain
            existingRecovery.cancel();
          }
          if (existingRecovery == null || existingRecovery.cancelled) {
            return createNewRecovery(block);
          } else {
            return existingRecovery;
          }
        });
  }

  private RecoveryEntry createNewRecovery(final BeaconBlock block) {
    final RecoveryEntry recoveryEntry = new RecoveryEntry(block, kzg, specHelpers);
    LOG.trace(
        "Recovery: new RecoveryEntry for slot {} and block {} ",
        recoveryEntry.block.getSlot(),
        recoveryEntry.block.getRoot());
    // check existing sidecars in DB as a start
    sidecarDB
        .getColumnIdentifiers(block.getSlotAndBlockRoot())
        .thenAccept(
            dataColumnIdentifiers -> processSidecarsFromDb(dataColumnIdentifiers, recoveryEntry))
        .finish(
            error ->
                LOG.error(
                    "Exception occurred while retrieving existing data column sidecars from database for slot {} and block {}",
                    block.getSlot(),
                    block.getRoot(),
                    error));

    return recoveryEntry;
  }

  private void processSidecarsFromDb(
      final List<DataColumnSlotAndIdentifier> columnIdentifiers,
      final RecoveryEntry recoveryEntry) {
    SafeFuture.collectAll(
            columnIdentifiers.stream()
                .limit(numberOfColumnsRequiredToReconstruct)
                .map(
                    columnId ->
                        sidecarDB
                            .getSidecar(columnId)
                            .thenAccept(
                                maybeSidecar -> maybeSidecar.ifPresent(recoveryEntry::addSidecar))))
        .always(
            () -> {
              // if no reconstruction has been started after DB retrieval, we attempt recovery via
              // peers
              if (!recoveryEntry.maybeStartReconstruction()) {
                recoveryEntry.attemptRecoveryViaPeers();
              }
            });
  }

  private class RecoveryEntry {
    private final BeaconBlock block;
    private final KZG kzg;
    private final MiscHelpersFulu specHelpers;

    private final Map<UInt64, DataColumnSidecar> existingSidecarsByColIdx =
        new ConcurrentHashMap<>();
    private final Map<UInt64, List<SafeFuture<DataColumnSidecar>>> responsesByColIdx =
        new ConcurrentHashMap<>();

    private SafeFuture<Void> reconstructionInProgress;
    private List<SafeFuture<DataColumnSidecar>> recoveryPeerRequests;

    private boolean cancelled = false;

    RecoveryEntry(final BeaconBlock block, final KZG kzg, final MiscHelpersFulu specHelpers) {
      this.block = block;
      this.kzg = kzg;
      this.specHelpers = specHelpers;
    }

    void addRequest(final UInt64 columnIndex, final SafeFuture<DataColumnSidecar> response) {
      if (existingSidecarsByColIdx.containsKey(columnIndex)) {
        response.complete(existingSidecarsByColIdx.get(columnIndex));
      } else {
        addToPendingRequests(columnIndex, response);
      }
    }

    void addSidecar(final DataColumnSidecar sidecar) {
      if (!cancelled && sidecar.getBlockRoot().equals(block.getRoot())) {
        existingSidecarsByColIdx.put(sidecar.getIndex(), sidecar);
        // attempt to complete any pending requests immediately
        final List<SafeFuture<DataColumnSidecar>> responses =
            responsesByColIdx.remove(sidecar.getIndex());
        if (responses != null) {
          responses.forEach(response -> response.complete(sidecar));
        }
      }
    }

    /** Start reconstruction if we have enough available columns */
    synchronized boolean maybeStartReconstruction() {
      if (!cancelled
          && existingSidecarsByColIdx.size() >= numberOfColumnsRequiredToReconstruct
          && (reconstructionInProgress == null
              || reconstructionInProgress.isCompletedExceptionally())) {
        reconstructionInProgress =
            asyncRunner
                .runAsync(
                    () -> {
                      reconstruct();
                      reconstructionComplete();
                    })
                .ignoreCancelException()
                // in case of reconstruction failures, attempt recovery via peers as a backup
                .whenException(__ -> attemptRecoveryViaPeers());
        return true;
      } else {
        return false;
      }
    }

    synchronized void attemptRecoveryViaPeers() {
      if (!cancelled && recoveryPeerRequests == null) {
        LOG.trace("Initialising peer recovery requests for slot {}", block.getSlot());
        recoveryPeerRequests =
            IntStream.range(0, numberOfColumns)
                .mapToObj(UInt64::valueOf)
                .filter(idx -> !existingSidecarsByColIdx.containsKey(idx))
                .map(
                    columnIdx -> {
                      final DataColumnSlotAndIdentifier columnId =
                          new DataColumnSlotAndIdentifier(
                              block.getSlot(), block.getRoot(), columnIdx);
                      final SafeFuture<DataColumnSidecar> sidecarFuture =
                          delegate.retrieve(columnId);
                      sidecarFuture
                          .thenPeek(
                              sidecar -> {
                                addSidecar(sidecar);
                                // on each recovered sidecar from peers, check if
                                // reconstruction can be started
                                maybeStartReconstruction();
                              })
                          .ignoreCancelException()
                          .finish(
                              __ ->
                                  LOG.error(
                                      "Exception occurred while retrieving data column sidecar with columnId {} from peer",
                                      columnId));
                      return sidecarFuture;
                    })
                .toList();
      }
    }

    synchronized void cancel() {
      cancelled = true;
      responsesByColIdx.values().stream()
          .flatMap(Collection::stream)
          .forEach(response -> response.cancel(true));
      if (recoveryPeerRequests != null) {
        recoveryPeerRequests.forEach(request -> request.cancel(true));
      }
      if (reconstructionInProgress != null) {
        reconstructionInProgress.cancel(true);
      }
    }

    private void addToPendingRequests(
        final UInt64 columnIndex, final SafeFuture<DataColumnSidecar> response) {
      responsesByColIdx
          .computeIfAbsent(columnIndex, __ -> new CopyOnWriteArrayList<>())
          .add(response);
      handleRequestCancel(columnIndex, response);
    }

    private void handleRequestCancel(
        final UInt64 columnIndex, final SafeFuture<DataColumnSidecar> response) {
      response.finish(
          __ -> {
            if (response.isCancelled()) {
              // cancel all responses for a given colIdx
              onRequestCancel(columnIndex);
            }
          });
    }

    private void onRequestCancel(final UInt64 columnIndex) {
      final List<SafeFuture<DataColumnSidecar>> responses = responsesByColIdx.remove(columnIndex);
      if (responses != null) {
        responses.forEach(
            response -> {
              if (!response.isDone()) {
                response.cancel(true);
              }
            });
      }
      if (responsesByColIdx.isEmpty()) {
        cancel();
      }
    }

    private void reconstruct() {
      LOG.trace(
          "Reconstruction started for slot {} ({} existing data column sidecars)",
          block.getSlot(),
          existingSidecarsByColIdx.size());
      final Map<UInt64, DataColumnSidecar> reconstructedSidecars =
          specHelpers
              .reconstructAllDataColumnSidecars(existingSidecarsByColIdx.values(), kzg)
              .stream()
              .collect(
                  Collectors.toUnmodifiableMap(DataColumnSidecar::getIndex, Function.identity()));
      existingSidecarsByColIdx.putAll(reconstructedSidecars);
    }

    private void reconstructionComplete() {
      LOG.trace("Reconstruction completed for slot {}", block.getSlot());
      // complete all retrieval requests
      responsesByColIdx.forEach(
          (colIdx, responses) -> {
            final DataColumnSidecar columnSidecar = existingSidecarsByColIdx.get(colIdx);
            responses.forEach(response -> response.completeAsync(columnSidecar, asyncRunner));
          });
      responsesByColIdx.clear();
      // cancel all pending recovery peer requests
      if (recoveryPeerRequests != null) {
        recoveryPeerRequests.forEach(request -> request.cancel(true));
      }
    }
  }
}
