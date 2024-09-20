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

import static tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier.minimalComparableForSlot;

import java.util.List;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.statetransition.datacolumns.db.DataColumnSidecarDB;

public class DataColumnSidecarDBStub implements DataColumnSidecarDB {

  private Optional<UInt64> firstCustodyIncompleteSlot = Optional.empty();
  private Optional<UInt64> firstSamplerIncompleteSlot = Optional.empty();
  private final NavigableMap<DataColumnSlotAndIdentifier, DataColumnSidecar> db = new TreeMap<>();
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
    DataColumnSlotAndIdentifier identifier = DataColumnSlotAndIdentifier.fromDataColumn(sidecar);
    db.put(identifier, sidecar);
    return SafeFuture.COMPLETE;
  }

  @Override
  public SafeFuture<Optional<DataColumnSidecar>> getSidecar(
      DataColumnSlotAndIdentifier identifier) {
    dbReadCounter.incrementAndGet();
    return SafeFuture.completedFuture(Optional.ofNullable(db.get(identifier)));
  }

  @Override
  public SafeFuture<List<DataColumnSlotAndIdentifier>> getColumnIdentifiers(UInt64 slot) {
    dbReadCounter.incrementAndGet();
    return SafeFuture.completedFuture(
        db
            .subMap(minimalComparableForSlot(slot), minimalComparableForSlot(slot.increment()))
            .keySet()
            .stream()
            .sorted()
            .toList());
  }

  @Override
  public SafeFuture<Void> pruneAllSidecars(UInt64 tillSlot) {
    dbWriteCounter.incrementAndGet();
    db.headMap(minimalComparableForSlot(tillSlot)).clear();
    return SafeFuture.COMPLETE;
  }

  public AtomicLong getDbReadCounter() {
    return dbReadCounter;
  }

  public AtomicLong getDbWriteCounter() {
    return dbWriteCounter;
  }
}
