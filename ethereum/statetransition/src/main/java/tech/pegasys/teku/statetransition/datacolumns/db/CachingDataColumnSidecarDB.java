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

package tech.pegasys.teku.statetransition.datacolumns.db;

import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.LimitedMap;
import tech.pegasys.teku.infrastructure.collections.LimitedSet;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;

class CachingDataColumnSidecarDB implements DataColumnSidecarDB {
  private static final Logger LOG = LogManager.getLogger();

  private final DataColumnSidecarDB delegateDb;
  private final Function<UInt64, Integer> slotToNumberOfColumns;

  private final Map<UInt64, SlotCache> readSlotCaches;
  private final Set<DataColumnSlotAndIdentifier> latestAdded;

  private final Map<DataColumnSlotAndIdentifier, InflightEntry> inflightColumns =
      new ConcurrentHashMap<>();

  public CachingDataColumnSidecarDB(
      final DataColumnSidecarDB delegateDb,
      final Function<UInt64, Integer> slotToNumberOfColumns,
      final int slotReadCacheSize,
      final int sidecarsWriteCacheSize) {
    this.delegateDb = delegateDb;
    this.slotToNumberOfColumns = slotToNumberOfColumns;
    this.readSlotCaches = LimitedMap.createSynchronizedLRU(slotReadCacheSize);
    this.latestAdded = LimitedSet.createSynchronized(sidecarsWriteCacheSize);
  }

  private SlotCache getOrCreateSlotCache(final UInt64 slot) {
    return readSlotCaches.computeIfAbsent(
        slot,
        __ ->
            new SlotCache(
                delegateDb.getColumnIdentifiers(slot), slotToNumberOfColumns.apply(slot)));
  }

  private void invalidateSlotCache(final UInt64 slot) {
    readSlotCaches.remove(slot);
  }

  @Override
  public SafeFuture<List<DataColumnSlotAndIdentifier>> getColumnIdentifiers(final UInt64 slot) {
    return getOrCreateSlotCache(slot)
        .generateColumnIdentifiers(slot)
        .thenApply(
            dbIdentifiers -> {
              // Get inflight column identifiers at merge time to avoid race condition
              final List<DataColumnSlotAndIdentifier> inflightIdentifiers =
                  inflightColumns.keySet().stream().filter(id -> id.slot().equals(slot)).toList();

              if (inflightIdentifiers.isEmpty()) {
                return dbIdentifiers;
              }
              // Merge DB identifiers with inflight, avoiding duplicates
              final Set<DataColumnSlotAndIdentifier> merged = new LinkedHashSet<>(dbIdentifiers);
              merged.addAll(inflightIdentifiers);
              LOG.debug(
                  "Merged {} inflight columns with {} DB columns for slot {}",
                  inflightIdentifiers.size(),
                  dbIdentifiers.size(),
                  slot);
              return List.copyOf(merged);
            });
  }

  @Override
  public SafeFuture<Void> addSidecar(final DataColumnSidecar sidecar) {
    final DataColumnSlotAndIdentifier dataColumnSlotAndIdentifier =
        DataColumnSlotAndIdentifier.fromDataColumn(sidecar);

    if (!latestAdded.add(dataColumnSlotAndIdentifier)) {
      LOG.debug("Skipping duplicate sidecar for {}", dataColumnSlotAndIdentifier);
      return SafeFuture.COMPLETE;
    }
    invalidateSlotCache(sidecar.getSlot());

    final InflightEntry entry =
        inflightColumns.computeIfAbsent(
            dataColumnSlotAndIdentifier,
            key -> {
              final SafeFuture<Void> writeFuture =
                  delegateDb
                      .addSidecar(sidecar)
                      .alwaysRun(() -> inflightColumns.remove(dataColumnSlotAndIdentifier));
              return new InflightEntry(sidecar, writeFuture);
            });

    return entry.future;
  }

  private static class InflightEntry {
    final DataColumnSidecar sidecar;
    final SafeFuture<Void> future;

    InflightEntry(final DataColumnSidecar sidecar, final SafeFuture<Void> future) {
      this.sidecar = sidecar;
      this.future = future;
    }
  }

  private static class SlotCache {
    private final SafeFuture<Map<Bytes32, BitSet>> compactCacheFuture;

    public SlotCache(
        final SafeFuture<List<DataColumnSlotAndIdentifier>> dbResponseFuture,
        final int numberOfColumns) {
      this.compactCacheFuture =
          dbResponseFuture.thenApply(slotColumns -> toCompactCache(slotColumns, numberOfColumns));
    }

    public SafeFuture<List<DataColumnSlotAndIdentifier>> generateColumnIdentifiers(
        final UInt64 slot) {
      return compactCacheFuture.thenApply(compactCache -> toColumnIdentifiers(slot, compactCache));
    }

    private static Map<Bytes32, BitSet> toCompactCache(
        final List<DataColumnSlotAndIdentifier> slotColumns, final int numberOfColumns) {
      final Map<Bytes32, BitSet> compactCache = new HashMap<>();
      slotColumns.forEach(
          colId ->
              compactCache
                  .computeIfAbsent(colId.blockRoot(), blockRoot -> new BitSet(numberOfColumns))
                  .set(colId.columnIndex().intValue()));
      return compactCache;
    }

    private static List<DataColumnSlotAndIdentifier> toColumnIdentifiers(
        final UInt64 slot, final Map<Bytes32, BitSet> compactCache) {
      return compactCache.entrySet().stream()
          .flatMap(
              entry ->
                  entry.getValue().stream()
                      .mapToObj(
                          colIndex ->
                              new DataColumnSlotAndIdentifier(
                                  slot, entry.getKey(), UInt64.valueOf(colIndex))))
          .toList();
    }
  }

  @Override
  public SafeFuture<Optional<UInt64>> getFirstCustodyIncompleteSlot() {
    return delegateDb.getFirstCustodyIncompleteSlot();
  }

  @Override
  public SafeFuture<Optional<DataColumnSidecar>> getSidecar(
      final DataColumnSlotAndIdentifier identifier) {
    final Optional<DataColumnSidecar> maybeInflightSidecar =
        Optional.ofNullable(inflightColumns.get(identifier)).map(entry -> entry.sidecar);

    if (maybeInflightSidecar.isPresent()) {
      LOG.debug("Returning inflight sidecar for {}", identifier);
      return SafeFuture.completedFuture(maybeInflightSidecar);
    }

    return delegateDb.getSidecar(identifier);
  }

  @Override
  public SafeFuture<Void> setFirstCustodyIncompleteSlot(final UInt64 slot) {
    return delegateDb.setFirstCustodyIncompleteSlot(slot);
  }

  @Override
  public SafeFuture<Optional<UInt64>> getEarliestAvailableDataColumnSlot() {
    return delegateDb.getEarliestAvailableDataColumnSlot();
  }

  @Override
  public SafeFuture<Void> setEarliestAvailableDataColumnSlot(final UInt64 slot) {
    return delegateDb.setEarliestAvailableDataColumnSlot(slot);
  }
}
