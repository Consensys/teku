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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnIdentifier;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.statetransition.datacolumns.db.DataColumnSidecarDB;

public class DataColumnSidecarDBStub implements DataColumnSidecarDB {

  private Optional<UInt64> firstCustodyIncompleteSlot = Optional.empty();
  private Optional<UInt64> firstSamplerIncompleteSlot = Optional.empty();
  private final Map<DataColumnIdentifier, DataColumnSidecar> db = new HashMap<>();
  private final NavigableMap<UInt64, Set<DataColumnIdentifier>> slotIds = new TreeMap<>();
  private final AtomicLong dbReadCounter = new AtomicLong();
  private final AtomicLong dbWriteCounter = new AtomicLong();

  @Override
  public SafeFuture<Void> setFirstCustodyIncompleteSlot(UInt64 slot) {
    dbWriteCounter.incrementAndGet();
    this.firstCustodyIncompleteSlot = Optional.of(slot);
    return SafeFuture.COMPLETE;
  }

  @Override
  public SafeFuture<Optional<UInt64>> getFirstCustodyIncompleteSlot() {
    dbReadCounter.incrementAndGet();
    return SafeFuture.completedFuture(firstCustodyIncompleteSlot);
  }

  @Override
  public SafeFuture<Void> setFirstSamplerIncompleteSlot(UInt64 slot) {
    dbWriteCounter.incrementAndGet();
    this.firstSamplerIncompleteSlot = Optional.of(slot);
    return SafeFuture.COMPLETE;
  }

  @Override
  public SafeFuture<Optional<UInt64>> getFirstSamplerIncompleteSlot() {
    dbReadCounter.incrementAndGet();
    return SafeFuture.completedFuture(firstSamplerIncompleteSlot);
  }

  @Override
  public SafeFuture<Void> addSidecar(DataColumnSidecar sidecar) {
    dbWriteCounter.incrementAndGet();
    DataColumnIdentifier identifier = DataColumnIdentifier.createFromSidecar(sidecar);
    db.put(identifier, sidecar);
    slotIds.computeIfAbsent(sidecar.getSlot(), __ -> new HashSet<>()).add(identifier);
    return SafeFuture.COMPLETE;
  }

  @Override
  public SafeFuture<Optional<DataColumnSidecar>> getSidecar(
      DataColumnSlotAndIdentifier identifier) {
    return SafeFuture.completedFuture(Optional.ofNullable(db.get(identifier.identifier())));
  }

  @Override
  public SafeFuture<Optional<DataColumnSidecar>> getSidecar(DataColumnIdentifier identifier) {
    dbReadCounter.incrementAndGet();
    return SafeFuture.completedFuture(Optional.ofNullable(db.get(identifier)));
  }

  @Override
  public SafeFuture<List<DataColumnIdentifier>> getColumnIdentifiers(UInt64 slot) {
    dbReadCounter.incrementAndGet();
    return SafeFuture.completedFuture(
        new ArrayList<>(slotIds.getOrDefault(slot, Collections.emptySet())));
  }

  @Override
  public SafeFuture<Void> pruneAllSidecars(UInt64 tillSlot) {
    dbWriteCounter.incrementAndGet();
    SortedMap<UInt64, Set<DataColumnIdentifier>> slotsToPrune = slotIds.headMap(tillSlot);
    slotsToPrune.values().stream().flatMap(Collection::stream).forEach(db::remove);
    slotsToPrune.clear();
    return SafeFuture.COMPLETE;
  }

  public AtomicLong getDbReadCounter() {
    return dbReadCounter;
  }

  public AtomicLong getDbWriteCounter() {
    return dbWriteCounter;
  }
}
