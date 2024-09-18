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

package tech.pegasys.teku.statetransition.datacolumns.db;

import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnIdentifier;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;

/**
 * The underlying DB primary key for a sidecar is {@link DataColumnSlotAndIdentifier}. When a
 * sidecar is requested by {@link DataColumnIdentifier} the slot for the block root needs to be
 * queried This class serves two purposes:
 *
 * <ul>
 *   <li>Optimizes extra call to the block DB when the slot is known from recent {@link
 *       #addSidecar(DataColumnSidecar)} call
 *   <li>Handles the case when a sidecar was stored optimistically without obtaining its
 *       corresponding block. In this case a subsequent sidecar query by {@link
 *       DataColumnIdentifier} would fail without this cache
 * </ul>
 */
class SlotCachingDasDb extends AbstractDelegatingDasDb implements DataColumnSidecarDB {
  private final DataColumnSidecarDB delegate;
  private final ColumnSlotCache columnSlotCache = new ColumnSlotCache();

  public SlotCachingDasDb(DataColumnSidecarDB delegate) {
    super(delegate);
    this.delegate = delegate;
  }

  @Override
  public SafeFuture<Optional<DataColumnSidecar>> getSidecar(
      DataColumnIdentifier dataColumnIdentifier) {
    return columnSlotCache
        .get(dataColumnIdentifier)
        .map(delegate::getSidecar)
        .orElseGet(() -> delegate.getSidecar(dataColumnIdentifier));
  }

  @Override
  public SafeFuture<Void> addSidecar(DataColumnSidecar sidecar) {
    columnSlotCache.addColumnSlotIdFromSidecar(sidecar);
    return delegate.addSidecar(sidecar);
  }

  @Override
  public SafeFuture<Void> setFirstCustodyIncompleteSlot(UInt64 slot) {
    columnSlotCache.pruneCaches(slot);
    return delegate.setFirstCustodyIncompleteSlot(slot);
  }

  // just delegate methods

  @Deprecated
  @Override
  public SafeFuture<Optional<DataColumnSidecar>> getSidecar(
      DataColumnSlotAndIdentifier identifier) {
    return delegate.getSidecar(identifier);
  }

  @Override
  public SafeFuture<Optional<UInt64>> getFirstCustodyIncompleteSlot() {
    return delegate.getFirstCustodyIncompleteSlot();
  }

  @Override
  public SafeFuture<Optional<UInt64>> getFirstSamplerIncompleteSlot() {
    return delegate.getFirstSamplerIncompleteSlot();
  }

  @Override
  public SafeFuture<Void> setFirstSamplerIncompleteSlot(UInt64 slot) {
    return delegate.setFirstSamplerIncompleteSlot(slot);
  }

  @Override
  public SafeFuture<Void> pruneAllSidecars(UInt64 tillSlot) {
    columnSlotCache.pruneCaches(tillSlot);
    return delegate.pruneAllSidecars(tillSlot);
  }

  private static class ColumnSlotCache {
    private final Map<Bytes32, UInt64> blockRootToSlot = new HashMap<>();
    private final NavigableMap<UInt64, Bytes32> slotToBlockRoot = new TreeMap<>();

    public synchronized Optional<DataColumnSlotAndIdentifier> get(
        DataColumnIdentifier dataColumnIdentifier) {
      return Optional.ofNullable(blockRootToSlot.get(dataColumnIdentifier.getBlockRoot()))
          .map(slot -> new DataColumnSlotAndIdentifier(slot, dataColumnIdentifier));
    }

    public synchronized void addColumnSlotIdFromSidecar(DataColumnSidecar sidecar) {
      Bytes32 blockRoot = sidecar.getBlockRoot();
      UInt64 slot = sidecar.getSlot();
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
