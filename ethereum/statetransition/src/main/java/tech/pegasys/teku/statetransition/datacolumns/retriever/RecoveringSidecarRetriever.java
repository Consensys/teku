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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;
import tech.pegasys.teku.statetransition.datacolumns.CanonicalBlockResolver;
import tech.pegasys.teku.statetransition.datacolumns.db.DataColumnSidecarDbAccessor;

public class RecoveringSidecarRetriever implements DataColumnSidecarRetriever {
  private static final Logger LOG = LogManager.getLogger();

  private final DataColumnSidecarRetriever delegate;
  private final KZG kzg;
  private final MiscHelpersFulu specHelpers;
  private final CanonicalBlockResolver blockResolver;
  private final DataColumnSidecarDbAccessor sidecarDB;
  private final AsyncRunner asyncRunner;
  private final Duration recoverInitiationTimeout;
  private final int columnCount;
  private final int recoverColumnCount;

  private final Map<UInt64, RecoveryEntry> recoveryBySlot = new ConcurrentHashMap<>();

  public RecoveringSidecarRetriever(
      final DataColumnSidecarRetriever delegate,
      final KZG kzg,
      final MiscHelpersFulu specHelpers,
      final CanonicalBlockResolver blockResolver,
      final DataColumnSidecarDbAccessor sidecarDB,
      final AsyncRunner asyncRunner,
      final Duration recoverInitiationTimeout,
      final int columnCount) {
    this.delegate = delegate;
    this.kzg = kzg;
    this.specHelpers = specHelpers;
    this.blockResolver = blockResolver;
    this.sidecarDB = sidecarDB;
    this.asyncRunner = asyncRunner;
    this.recoverInitiationTimeout = recoverInitiationTimeout;
    this.columnCount = columnCount;
    this.recoverColumnCount = columnCount / 2;
  }

  @Override
  public SafeFuture<DataColumnSidecar> retrieve(final DataColumnSlotAndIdentifier columnId) {
    final SafeFuture<DataColumnSidecar> promise = delegate.retrieve(columnId);
    // TODO-fulu we probably need a better heuristics to submit requests for recovery
    // (https://github.com/Consensys/teku/issues/9465)
    asyncRunner
        .runAfterDelay(
            () -> {
              if (!promise.isDone()) {
                maybeInitiateRecovery(columnId, promise);
              }
            },
            recoverInitiationTimeout)
        .ifExceptionGetsHereRaiseABug();
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
  void maybeInitiateRecovery(
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

  private synchronized RecoveryEntry addRecovery(
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
                        .limit(recoverColumnCount)
                        .map(sidecarDB::getSidecar)))
        .thenPeek(
            maybeDataColumnSidecars -> {
              maybeDataColumnSidecars.forEach(
                  maybeDataColumnSidecar ->
                      maybeDataColumnSidecar.ifPresent(recoveryEntry::addSidecar));
              recoveryEntry.initRecoveryRequests();
            })
        .ifExceptionGetsHereRaiseABug();

    return recoveryEntry;
  }

  private synchronized void recoveryComplete(final RecoveryEntry entry) {
    LOG.trace("Recovery complete for entry {}", entry);
  }

  private class RecoveryEntry {
    private final BeaconBlock block;
    private final KZG kzg;
    private final MiscHelpersFulu specHelpers;

    private final Map<UInt64, DataColumnSidecar> existingSidecarsByColIdx = new HashMap<>();
    private final Map<UInt64, List<SafeFuture<DataColumnSidecar>>> promisesByColIdx =
        new HashMap<>();
    private List<SafeFuture<DataColumnSidecar>> recoveryRequests;
    private boolean recovered = false;
    private boolean cancelled = false;

    public RecoveryEntry(
        final BeaconBlock block, final KZG kzg, final MiscHelpersFulu specHelpers) {
      this.block = block;
      this.kzg = kzg;
      this.specHelpers = specHelpers;
    }

    public synchronized void addRequest(
        final UInt64 columnIndex, final SafeFuture<DataColumnSidecar> promise) {
      if (recovered) {
        promise.completeAsync(existingSidecarsByColIdx.get(columnIndex), asyncRunner);
      } else {
        promisesByColIdx.computeIfAbsent(columnIndex, __ -> new ArrayList<>()).add(promise);
        handleRequestCancel(columnIndex, promise);
      }
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
      if (!recovered && sidecar.getBlockRoot().equals(block.getRoot())) {
        existingSidecarsByColIdx.put(sidecar.getIndex(), sidecar);
        if (existingSidecarsByColIdx.size() >= recoverColumnCount) {
          // TODO-fulu: Make it asynchronously as it's heavy CPU operation
          // (https://github.com/Consensys/teku/issues/9466)
          recover();
          recoveryComplete();
        }
      }
    }

    private void recoveryComplete() {
      recovered = true;
      LOG.trace(
          "Recovery: completed for the slot {}, requests complete: {}",
          block.getSlot(),
          promisesByColIdx.values().stream().mapToInt(List::size).sum());

      promisesByColIdx.forEach(
          (key, value) -> {
            DataColumnSidecar columnSidecar = existingSidecarsByColIdx.get(key);
            value.forEach(promise -> promise.completeAsync(columnSidecar, asyncRunner));
          });
      promisesByColIdx.clear();
      RecoveringSidecarRetriever.this.recoveryComplete(this);
      if (recoveryRequests != null) {
        recoveryRequests.forEach(r -> r.cancel(true));
        recoveryRequests = null;
      }
    }

    public synchronized void initRecoveryRequests() {
      if (!recovered && !cancelled) {
        recoveryRequests =
            IntStream.range(0, columnCount)
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
    }

    private void recover() {
      final List<DataColumnSidecar> recoveredSidecars =
          specHelpers.reconstructAllDataColumnSidecars(existingSidecarsByColIdx.values(), kzg);
      final Map<UInt64, DataColumnSidecar> recoveredSidecarsAsMap =
          recoveredSidecars.stream()
              .collect(Collectors.toUnmodifiableMap(DataColumnSidecar::getIndex, i -> i));
      existingSidecarsByColIdx.putAll(recoveredSidecarsAsMap);
    }
  }
}
