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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
 * SimpleSidecarRetriever}. It achieves so by either reconstructing (if we have over 50% of data
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

  private final Set<DataColumnSidecarPromiseWithTimestamp> pendingPromises =
      ConcurrentHashMap.newKeySet();
  private final Map<UInt64, RecoveryEntry> recoveryBySlot = new ConcurrentHashMap<>();

  private Cancellable cancellable;

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
    // reconstruction of all columns is possible with more than 50% of the column data
    this.numberOfColumnsRequiredToReconstruct = numberOfColumns / 2;
  }

  public synchronized void start() {
    if (cancellable != null) {
      return;
    }
    cancellable =
        asyncRunner.runWithFixedDelay(
            this::checkPendingPromises,
            recoveryInitiationCheckInterval,
            recoveryInitiationCheckInterval,
            error -> LOG.error("Failed to check pending data column sidecar retrievals", error));
  }

  public synchronized void stop() {
    if (cancellable == null) {
      return;
    }
    cancellable.cancel();
    cancellable = null;
  }

  private record DataColumnSidecarPromiseWithTimestamp(
      DataColumnSlotAndIdentifier dataColumnSlotAndIdentifier,
      SafeFuture<DataColumnSidecar> promise,
      UInt64 timestamp) {}

  @Override
  public SafeFuture<DataColumnSidecar> retrieve(final DataColumnSlotAndIdentifier columnId) {
    final SafeFuture<DataColumnSidecar> promise = delegate.retrieve(columnId);
    final DataColumnSidecarPromiseWithTimestamp pendingPromiseWithTimestamp =
        new DataColumnSidecarPromiseWithTimestamp(
            columnId, promise, timeProvider.getTimeInMillis());
    pendingPromises.add(pendingPromiseWithTimestamp);

    // remove it from pending as soon as the promise is done
    promise.always(() -> pendingPromises.remove(pendingPromiseWithTimestamp));

    return promise;
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
  int pendingPromisesCount() {
    return pendingPromises.size();
  }

  private void checkPendingPromises() {
    final UInt64 currentTime = timeProvider.getTimeInMillis();

    pendingPromises.removeIf(
        promiseWithTimestamp -> {
          if (promiseWithTimestamp.promise.isDone()) {
            // If the promise is already done, we can remove it
            return true;
          }
          // If the promise is not done, we check if it has timed out
          // TODO-fulu we probably need a better heuristics to submit requests for recovery
          // (https://github.com/Consensys/teku/issues/9465)
          if (promiseWithTimestamp
              .timestamp
              .plus(recoveryInitiationTimeout.toMillis())
              .isGreaterThan(currentTime)) {
            // If the promise is not timed out, we keep it
            return false;
          }

          // If the promise is timed out, we initiate recovery and remove it
          maybeInitiateRecovery(
              promiseWithTimestamp.dataColumnSlotAndIdentifier, promiseWithTimestamp.promise);

          return true;
        });
  }

  private void maybeInitiateRecovery(
      final DataColumnSlotAndIdentifier columnId, final SafeFuture<DataColumnSidecar> promise) {
    blockResolver
        .getBlockAtSlot(columnId.slot())
        .thenPeek(
            maybeBlock -> {
              if (!maybeBlock.map(b -> b.getRoot().equals(columnId.blockRoot())).orElse(false)) {
                LOG.trace("Recovery: CAN'T initiate recovery for " + columnId);
                promise.completeExceptionally(
                    new NotOnCanonicalChainException(columnId, maybeBlock));
              } else {
                final BeaconBlock block = maybeBlock.orElseThrow();
                LOG.trace("Recovery: initiating recovery for " + columnId);
                final RecoveryEntry recovery = addRecovery(columnId, block);
                recovery.addRequest(columnId.columnIndex(), promise);
              }
            })
        .ifExceptionGetsHereRaiseABug();
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
    sidecarDB
        .getColumnIdentifiers(block.getSlotAndBlockRoot())
        .thenCompose(
            dataColumnIdentifiers ->
                SafeFuture.collectAll(
                    dataColumnIdentifiers.stream()
                        .limit(numberOfColumnsRequiredToReconstruct)
                        .map(sidecarDB::getSidecar)))
        .thenAccept(
            dataColumnSidecars -> {
              dataColumnSidecars.forEach(
                  maybeDataColumnSidecar ->
                      maybeDataColumnSidecar.ifPresent(recoveryEntry::addSidecar));
              // don't try to recover from peers if reconstruction is in progress or reconstruction
              // is done
              if (recoveryEntry.isReconstructionInProgress()
                  || recoveryEntry.isReconstructionDone()) {
                return;
              }
              recoveryEntry.initRecoveryRequests();
            })
        .ifExceptionGetsHereRaiseABug();

    return recoveryEntry;
  }

  private class RecoveryEntry {
    private final BeaconBlock block;
    private final KZG kzg;
    private final MiscHelpersFulu specHelpers;

    private final Map<UInt64, DataColumnSidecar> existingSidecarsByColIdx =
        new ConcurrentHashMap<>();
    private final Map<UInt64, List<SafeFuture<DataColumnSidecar>>> promisesByColIdx =
        new ConcurrentHashMap<>();

    private volatile SafeFuture<Void> reconstructionInProgress;
    private volatile List<SafeFuture<DataColumnSidecar>> recoveryRequests;
    private volatile boolean cancelled = false;

    public RecoveryEntry(
        final BeaconBlock block, final KZG kzg, final MiscHelpersFulu specHelpers) {
      this.block = block;
      this.kzg = kzg;
      this.specHelpers = specHelpers;
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    public void addRequest(final UInt64 columnIndex, final SafeFuture<DataColumnSidecar> promise) {
      if (isReconstructionDone()) {
        promise.completeAsync(existingSidecarsByColIdx.get(columnIndex), asyncRunner);
      } else if (isReconstructionInProgress()) {
        // wait for the reconstruction to finish before completing the request
        reconstructionInProgress
            .whenException(__ -> addToPendingPromises(columnIndex, promise))
            .thenRun(
                () ->
                    promise.completeAsync(existingSidecarsByColIdx.get(columnIndex), asyncRunner));
      } else {
        addToPendingPromises(columnIndex, promise);
      }
    }

    private void addToPendingPromises(
        final UInt64 columnIndex, final SafeFuture<DataColumnSidecar> promise) {
      promisesByColIdx.computeIfAbsent(columnIndex, __ -> new ArrayList<>()).add(promise);
      handleRequestCancel(columnIndex, promise);
    }

    public boolean isReconstructionInProgress() {
      return reconstructionInProgress != null && !reconstructionInProgress.isDone();
    }

    private boolean isReconstructionDone() {
      return reconstructionInProgress != null && reconstructionInProgress.isCompletedNormally();
    }

    private void handleRequestCancel(
        final UInt64 columnIndex, final SafeFuture<DataColumnSidecar> request) {
      request.finish(
          __ -> {
            if (request.isCancelled()) {
              onRequestCancel(columnIndex);
            }
          });
    }

    private synchronized void onRequestCancel(final UInt64 columnIndex) {
      final List<SafeFuture<DataColumnSidecar>> promises = promisesByColIdx.remove(columnIndex);
      promises.stream().filter(p -> !p.isDone()).forEach(promise -> promise.cancel(true));
      if (promisesByColIdx.isEmpty()) {
        cancel();
      }
    }

    public synchronized void addSidecar(final DataColumnSidecar sidecar) {
      if (sidecar.getBlockRoot().equals(block.getRoot()) && !isReconstructionDone() && !cancelled) {
        existingSidecarsByColIdx.put(sidecar.getIndex(), sidecar);
        if (existingSidecarsByColIdx.size() >= numberOfColumnsRequiredToReconstruct
            && reconstructionInProgress == null) {
          reconstructionInProgress =
              asyncRunner
                  .runAsync(
                      () -> {
                        reconstruct();
                        reconstructionComplete();
                      })
                  // in case of reconstruction failures, attempt recovery as a backup
                  .whenException(__ -> initRecoveryRequests());
        }
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
      promisesByColIdx.forEach(
          (key, value) -> {
            final DataColumnSidecar columnSidecar = existingSidecarsByColIdx.get(key);
            value.forEach(promise -> promise.completeAsync(columnSidecar, asyncRunner));
          });
      // cancel all pending recovery requests
      if (recoveryRequests != null) {
        recoveryRequests.forEach(r -> r.cancel(true));
        recoveryRequests = null;
      }
    }

    public synchronized void initRecoveryRequests() {
      if (!cancelled) {
        LOG.trace("Initialising recovery requests to peers for slot {}", block.getSlot());
        recoveryRequests =
            IntStream.range(0, numberOfColumns)
                .mapToObj(UInt64::valueOf)
                .filter(idx -> !existingSidecarsByColIdx.containsKey(idx))
                .map(
                    columnIdx ->
                        delegate.retrieve(
                            new DataColumnSlotAndIdentifier(
                                block.getSlot(), block.getRoot(), columnIdx)))
                .peek(
                    promise ->
                        promise
                            .thenPeek(this::addSidecar)
                            .ignoreCancelException()
                            .ifExceptionGetsHereRaiseABug())
                .toList();
      }
    }

    public synchronized void cancel() {
      cancelled = true;
      promisesByColIdx.values().stream()
          .flatMap(Collection::stream)
          .forEach(
              promise ->
                  asyncRunner.runAsync(() -> promise.cancel(true)).ifExceptionGetsHereRaiseABug());
      if (recoveryRequests != null) {
        recoveryRequests.forEach(rr -> rr.cancel(true));
      }
      if (reconstructionInProgress != null) {
        reconstructionInProgress.cancel(true);
      }
    }
  }
}
