/*
 * Copyright Consensys Software Inc., 2026
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

import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.stream.AsyncStream;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.util.DataColumnIdentifier;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.statetransition.blobs.RemoteOrigin;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

public class DataColumnSidecarByRootCustodyImpl
    implements DataColumnSidecarByRootCustody, DataColumnSidecarCustody {

  public static final int DEFAULT_MAX_CACHE_SIZE_EPOCHS = 1024;

  private final DataColumnSidecarCustody custody;
  private final CombinedChainDataClient combinedChainDataClient;
  private final UInt64 maxCacheSizeInSlots;

  private final ColumnSlotCache cache = new ColumnSlotCache();

  public DataColumnSidecarByRootCustodyImpl(
      final DataColumnSidecarCustody custody,
      final CombinedChainDataClient combinedChainDataClient,
      final UInt64 maxCacheSizeInSlots) {
    this.custody = custody;
    this.combinedChainDataClient = combinedChainDataClient;
    this.maxCacheSizeInSlots = maxCacheSizeInSlots;
  }

  @Override
  public SafeFuture<Optional<DataColumnSidecar>> getCustodyDataColumnSidecarByRoot(
      final DataColumnIdentifier columnId) {

    return cache
        .getOrComputeAsync(
            columnId,
            blockRoot ->
                combinedChainDataClient
                    .getBlockByBlockRoot(blockRoot)
                    .thenApply(block -> block.map(SignedBeaconBlock::getSlot)))
        .thenCompose(
            maybeId ->
                maybeId
                    .map(this::getCustodyDataColumnSidecar)
                    .orElse(SafeFuture.completedFuture(Optional.empty())));
  }

  @Override
  public SafeFuture<Void> onNewValidatedDataColumnSidecar(
      final DataColumnSidecar dataColumnSidecar, final RemoteOrigin remoteOrigin) {
    cache.pruneCaches(dataColumnSidecar.getSlot().minusMinZero(maxCacheSizeInSlots));
    cache.addColumnSlotIdFromSidecar(dataColumnSidecar);
    return custody.onNewValidatedDataColumnSidecar(dataColumnSidecar, remoteOrigin);
  }

  @Override
  public AsyncStream<DataColumnSlotAndIdentifier> retrieveMissingColumns() {
    return custody.retrieveMissingColumns();
  }

  @Override
  public SafeFuture<Optional<DataColumnSidecar>> getCustodyDataColumnSidecar(
      final DataColumnSlotAndIdentifier columnId) {
    return custody.getCustodyDataColumnSidecar(columnId);
  }

  @Override
  public SafeFuture<Boolean> hasCustodyDataColumnSidecar(
      final DataColumnSlotAndIdentifier columnId) {
    return custody.hasCustodyDataColumnSidecar(columnId);
  }

  private static class ColumnSlotCache {
    private final Map<Bytes32, UInt64> blockRootToSlot = new HashMap<>();
    private final NavigableMap<UInt64, Bytes32> slotToBlockRoot = new TreeMap<>();

    public synchronized Optional<DataColumnSlotAndIdentifier> get(
        final DataColumnIdentifier dataColumnIdentifier) {
      return Optional.ofNullable(blockRootToSlot.get(dataColumnIdentifier.blockRoot()))
          .map(slot -> new DataColumnSlotAndIdentifier(slot, dataColumnIdentifier));
    }

    public SafeFuture<Optional<DataColumnSlotAndIdentifier>> getOrComputeAsync(
        final DataColumnIdentifier dataColumnIdentifier,
        final Function<Bytes32, SafeFuture<Optional<UInt64>>> asyncRootToSlotSupplier) {
      return get(dataColumnIdentifier)
          .map(id -> SafeFuture.completedFuture(Optional.of(id)))
          .orElseGet(
              () ->
                  asyncRootToSlotSupplier
                      .apply(dataColumnIdentifier.blockRoot())
                      .thenPeek(
                          maybeSlot ->
                              maybeSlot.ifPresent(
                                  slot ->
                                      addBlockRootToSlot(dataColumnIdentifier.blockRoot(), slot)))
                      .thenApply(
                          maybeSlot ->
                              maybeSlot.map(
                                  slot ->
                                      new DataColumnSlotAndIdentifier(
                                          slot, dataColumnIdentifier))));
    }

    public void addColumnSlotIdFromSidecar(final DataColumnSidecar sidecar) {
      addBlockRootToSlot(sidecar.getBeaconBlockRoot(), sidecar.getSlot());
    }

    public synchronized void addBlockRootToSlot(final Bytes32 blockRoot, final UInt64 slot) {
      blockRootToSlot.put(blockRoot, slot);
      slotToBlockRoot.put(slot, blockRoot);
    }

    public synchronized void pruneCaches(final UInt64 tillSlotExclusive) {
      final SortedMap<UInt64, Bytes32> toPrune = slotToBlockRoot.headMap(tillSlotExclusive);
      toPrune.values().forEach(blockRootToSlot::remove);
      toPrune.clear();
    }
  }
}
