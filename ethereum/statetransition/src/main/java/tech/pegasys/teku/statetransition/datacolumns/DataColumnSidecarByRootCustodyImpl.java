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
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnIdentifier;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

public class DataColumnSidecarByRootCustodyImpl
    implements DataColumnSidecarByRootCustody, UpdatableDataColumnSidecarCustody {

  public static final int DEFAULT_MAX_CACHE_SIZE_EPOCHS = 1024;

  private final UpdatableDataColumnSidecarCustody custody;
  private final CombinedChainDataClient combinedChainDataClient;
  private final UInt64 maxCacheSizeInSlots;

  private final ColumnSlotCache cache = new ColumnSlotCache();

  public DataColumnSidecarByRootCustodyImpl(
      UpdatableDataColumnSidecarCustody custody,
      CombinedChainDataClient combinedChainDataClient,
      UInt64 maxCacheSizeInSlots) {
    this.custody = custody;
    this.combinedChainDataClient = combinedChainDataClient;
    this.maxCacheSizeInSlots = maxCacheSizeInSlots;
  }

  @Override
  public SafeFuture<Optional<DataColumnSidecar>> getCustodyDataColumnSidecarByRoot(
      DataColumnIdentifier columnId) {

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
  public SafeFuture<Void> onNewValidatedDataColumnSidecar(DataColumnSidecar dataColumnSidecar) {
    cache.pruneCaches(dataColumnSidecar.getSlot().minusMinZero(maxCacheSizeInSlots));
    cache.addColumnSlotIdFromSidecar(dataColumnSidecar);
    return custody.onNewValidatedDataColumnSidecar(dataColumnSidecar);
  }

  @Override
  public AsyncStream<DataColumnSlotAndIdentifier> retrieveMissingColumns() {
    return custody.retrieveMissingColumns();
  }

  @Override
  public SafeFuture<Optional<DataColumnSidecar>> getCustodyDataColumnSidecar(
      DataColumnSlotAndIdentifier columnId) {
    return custody.getCustodyDataColumnSidecar(columnId);
  }

  private static class ColumnSlotCache {
    private final Map<Bytes32, UInt64> blockRootToSlot = new HashMap<>();
    private final NavigableMap<UInt64, Bytes32> slotToBlockRoot = new TreeMap<>();

    public synchronized Optional<DataColumnSlotAndIdentifier> get(
        DataColumnIdentifier dataColumnIdentifier) {
      return Optional.ofNullable(blockRootToSlot.get(dataColumnIdentifier.getBlockRoot()))
          .map(slot -> new DataColumnSlotAndIdentifier(slot, dataColumnIdentifier));
    }

    public SafeFuture<Optional<DataColumnSlotAndIdentifier>> getOrComputeAsync(
        DataColumnIdentifier dataColumnIdentifier,
        Function<Bytes32, SafeFuture<Optional<UInt64>>> asyncRootToSlotSupplier) {
      return get(dataColumnIdentifier)
          .map(id -> SafeFuture.completedFuture(Optional.of(id)))
          .orElseGet(
              () ->
                  asyncRootToSlotSupplier
                      .apply(dataColumnIdentifier.getBlockRoot())
                      .thenPeek(
                          maybeSlot ->
                              maybeSlot.ifPresent(
                                  slot ->
                                      addBlockRootToSlot(
                                          dataColumnIdentifier.getBlockRoot(), slot)))
                      .thenApply(
                          maybeSlot ->
                              maybeSlot.map(
                                  slot ->
                                      new DataColumnSlotAndIdentifier(
                                          slot, dataColumnIdentifier))));
    }

    public void addColumnSlotIdFromSidecar(DataColumnSidecar sidecar) {
      addBlockRootToSlot(sidecar.getBlockRoot(), sidecar.getSlot());
    }

    public synchronized void addBlockRootToSlot(Bytes32 blockRoot, UInt64 slot) {
      blockRootToSlot.put(blockRoot, slot);
      slotToBlockRoot.put(slot, blockRoot);
    }

    public synchronized void pruneCaches(UInt64 tillSlotExclusive) {
      SortedMap<UInt64, Bytes32> toPrune = slotToBlockRoot.headMap(tillSlotExclusive);
      toPrune.values().forEach(blockRootToSlot::remove);
      toPrune.clear();
    }
  }
}
