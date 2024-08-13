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
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.MatrixEntry;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnIdentifier;
import tech.pegasys.teku.spec.logic.versions.eip7594.helpers.MiscHelpersEip7594;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsEip7594;
import tech.pegasys.teku.statetransition.datacolumns.CanonicalBlockResolver;
import tech.pegasys.teku.statetransition.datacolumns.ColumnSlotAndIdentifier;
import tech.pegasys.teku.statetransition.datacolumns.DataColumnSidecarDB;

public class RecoveringSidecarRetriever implements DataColumnSidecarRetriever {
  private static final Logger LOG = LogManager.getLogger("das-nyota");

  private final DataColumnSidecarRetriever delegate;
  private final KZG kzg;
  private final MiscHelpersEip7594 specHelpers;
  private final SchemaDefinitionsEip7594 schemaDefinitions;
  private final CanonicalBlockResolver blockResolver;
  private final DataColumnSidecarDB sidecarDB;
  private final AsyncRunner asyncRunner;
  private final Duration recoverInitiationTimeout;
  private final int columnCount;
  private final int recoverColumnCount;

  private final Map<UInt64, RecoveryEntry> recoveryBySlot = new ConcurrentHashMap<>();

  public RecoveringSidecarRetriever(
      DataColumnSidecarRetriever delegate,
      KZG kzg,
      MiscHelpersEip7594 specHelpers,
      SchemaDefinitionsEip7594 schemaDefinitionsEip7594,
      CanonicalBlockResolver blockResolver,
      DataColumnSidecarDB sidecarDB,
      AsyncRunner asyncRunner,
      Duration recoverInitiationTimeout,
      int columnCount) {
    this.delegate = delegate;
    this.kzg = kzg;
    this.specHelpers = specHelpers;
    this.schemaDefinitions = schemaDefinitionsEip7594;
    this.blockResolver = blockResolver;
    this.sidecarDB = sidecarDB;
    this.asyncRunner = asyncRunner;
    this.recoverInitiationTimeout = recoverInitiationTimeout;
    this.columnCount = columnCount;
    this.recoverColumnCount = columnCount / 2;
  }

  @Override
  public SafeFuture<DataColumnSidecar> retrieve(ColumnSlotAndIdentifier columnId) {
    SafeFuture<DataColumnSidecar> promise = delegate.retrieve(columnId);
    // TODO we probably need a better heuristics to submit requests for recovery
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

  @VisibleForTesting
  void maybeInitiateRecovery(
      ColumnSlotAndIdentifier columnId, SafeFuture<DataColumnSidecar> promise) {
    blockResolver
        .getBlockAtSlot(columnId.slot())
        .thenPeek(
            maybeBlock -> {
              if (!maybeBlock
                  .map(b -> b.getRoot().equals(columnId.identifier().getBlockRoot()))
                  .orElse(false)) {
                LOG.info("[nyota] Recovery: CAN'T initiate recovery for " + columnId);
                promise.completeExceptionally(
                    new NotOnCanonicalChainException(columnId, maybeBlock));
              } else {
                final BeaconBlock block = maybeBlock.orElseThrow();
                LOG.info("[nyota] Recovery: initiating recovery for " + columnId);
                final RecoveryEntry recovery = addRecovery(columnId, block);
                recovery.addRequest(columnId.identifier().getIndex(), promise);
              }
            })
        .ifExceptionGetsHereRaiseABug();
  }

  private synchronized RecoveryEntry addRecovery(
      final ColumnSlotAndIdentifier columnId, final BeaconBlock block) {
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
    LOG.info(
        "[nyota] Recovery: new RecoveryEntry for slot {} and block {} ",
        recoveryEntry.block.getSlot(),
        recoveryEntry.block.getRoot());
    sidecarDB
        .streamColumnIdentifiers(block.getSlot())
        .thenCompose(
            dataColumnIdentifiers ->
                SafeFuture.collectAll(
                    dataColumnIdentifiers.limit(recoverColumnCount).map(sidecarDB::getSidecar)))
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

  private synchronized void recoveryComplete(RecoveryEntry entry) {
    LOG.trace("Recovery complete for entry {}", entry);
  }

  private class RecoveryEntry {
    private final BeaconBlock block;
    private final KZG kzg;
    private final MiscHelpersEip7594 specHelpers;

    private final Map<UInt64, DataColumnSidecar> existingSidecarsByColIdx = new HashMap<>();
    private final Map<UInt64, List<SafeFuture<DataColumnSidecar>>> promisesByColIdx =
        new HashMap<>();
    private List<SafeFuture<DataColumnSidecar>> recoveryRequests;
    private boolean recovered = false;
    private boolean cancelled = false;

    public RecoveryEntry(BeaconBlock block, KZG kzg, MiscHelpersEip7594 specHelpers) {
      this.block = block;
      this.kzg = kzg;
      this.specHelpers = specHelpers;
    }

    public synchronized void addRequest(UInt64 columnIndex, SafeFuture<DataColumnSidecar> promise) {
      if (recovered) {
        promise.completeAsync(existingSidecarsByColIdx.get(columnIndex), asyncRunner);
      } else {
        promisesByColIdx.computeIfAbsent(columnIndex, __ -> new ArrayList<>()).add(promise);
      }
    }

    public synchronized void addSidecar(DataColumnSidecar sidecar) {
      if (!recovered && sidecar.getBlockRoot().equals(block.getRoot())) {
        existingSidecarsByColIdx.put(sidecar.getIndex(), sidecar);
        if (existingSidecarsByColIdx.size() >= recoverColumnCount) {
          // TODO: Make it asynchronously as it's heavy CPU operation
          recover();
          recoveryComplete();
        }
      }
    }

    private void recoveryComplete() {
      recovered = true;
      LOG.info(
          "[nyota] Recovery: completed for the slot {}, requests complete: {}",
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
                            new ColumnSlotAndIdentifier(
                                block.getSlot(),
                                new DataColumnIdentifier(block.getRoot(), columnIdx))))
                .peek(promise -> promise.thenPeek(this::addSidecar).ifExceptionGetsHereRaiseABug())
                .toList();
      }
    }

    public synchronized void cancel() {
      cancelled = true;
      promisesByColIdx.values().stream()
          .flatMap(Collection::stream)
          .forEach(
              promise ->
                  promise.completeExceptionallyAsync(
                      new NotOnCanonicalChainException("Canonical block changed"), asyncRunner));
    }

    private void recover() {
      List<List<MatrixEntry>> columnBlobEntries =
          existingSidecarsByColIdx.values().stream()
              .map(
                  sideCar ->
                      IntStream.range(0, sideCar.getDataColumn().size())
                          .mapToObj(
                              rowIndex ->
                                  schemaDefinitions
                                      .getMatrixEntrySchema()
                                      .create(
                                          sideCar.getDataColumn().get(rowIndex),
                                          sideCar.getSszKZGProofs().get(rowIndex).getKZGProof(),
                                          sideCar.getIndex(),
                                          UInt64.valueOf(rowIndex)))
                          .toList())
              .toList();
      List<List<MatrixEntry>> blobColumnEntries = transpose(columnBlobEntries);
      List<List<MatrixEntry>> extendedMatrix = specHelpers.recoverMatrix(blobColumnEntries, kzg);
      DataColumnSidecar anyExistingSidecar =
          existingSidecarsByColIdx.values().stream().findFirst().orElseThrow();
      SignedBeaconBlockHeader signedBeaconBlockHeader =
          anyExistingSidecar.getSignedBeaconBlockHeader();
      List<DataColumnSidecar> recoveredSidecars =
          specHelpers.constructDataColumnSidecars(block, signedBeaconBlockHeader, extendedMatrix);
      Map<UInt64, DataColumnSidecar> recoveredSidecarsAsMap =
          recoveredSidecars.stream()
              .collect(Collectors.toUnmodifiableMap(DataColumnSidecar::getIndex, i -> i));
      existingSidecarsByColIdx.putAll(recoveredSidecarsAsMap);
    }
  }

  private static <T> List<List<T>> transpose(List<List<T>> matrix) {
    int rowCount = matrix.size();
    int colCount = matrix.get(0).size();
    List<List<T>> ret =
        Stream.generate(() -> (List<T>) new ArrayList<T>(rowCount)).limit(colCount).toList();

    for (int row = 0; row < rowCount; row++) {
      if (matrix.get(row).size() != colCount) {
        throw new IllegalArgumentException("Different number columns in the matrix");
      }
      for (int col = 0; col < colCount; col++) {
        T val = matrix.get(row).get(col);
        ret.get(col).add(row, val);
      }
    }
    return ret;
  }
}
