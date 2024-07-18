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

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnIdentifier;

public class DataColumnSidecarDBStub implements DataColumnSidecarDB {

  private Optional<UInt64> firstCustodyIncompleteSlot = Optional.empty();
  private Optional<UInt64> firstSamplerIncompleteSlot = Optional.empty();
  private Map<DataColumnIdentifier, DataColumnSidecar> db = new HashMap<>();
  private Map<UInt64, Set<DataColumnIdentifier>> slotIds = new HashMap<>();

  @Override
  public SafeFuture<Void> setFirstCustodyIncompleteSlot(UInt64 slot) {
    this.firstCustodyIncompleteSlot = Optional.of(slot);
    return SafeFuture.COMPLETE;
  }

  @Override
  public SafeFuture<Optional<UInt64>> getFirstCustodyIncompleteSlot() {
    return SafeFuture.completedFuture(firstCustodyIncompleteSlot);
  }

  @Override
  public SafeFuture<Void> setFirstSamplerIncompleteSlot(UInt64 slot) {
    this.firstSamplerIncompleteSlot = Optional.of(slot);
    return SafeFuture.COMPLETE;
  }

  @Override
  public SafeFuture<Optional<UInt64>> getFirstSamplerIncompleteSlot() {
    return SafeFuture.completedFuture(firstSamplerIncompleteSlot);
  }

  @Override
  public void addSidecar(DataColumnSidecar sidecar) {
    DataColumnIdentifier identifier = DataColumnIdentifier.createFromSidecar(sidecar);
    db.put(identifier, sidecar);
    slotIds.computeIfAbsent(sidecar.getSlot(), __ -> new HashSet<>()).add(identifier);
  }

  @Override
  public SafeFuture<Optional<DataColumnSidecar>> getSidecar(DataColumnIdentifier identifier) {
    return SafeFuture.completedFuture(Optional.ofNullable(db.get(identifier)));
  }

  @Override
  public SafeFuture<Stream<DataColumnIdentifier>> streamColumnIdentifiers(UInt64 slot) {
    return SafeFuture.completedFuture(slotIds.getOrDefault(slot, Collections.emptySet()).stream());
  }

  @Override
  public void pruneAllSidecars(UInt64 tillSlot) {}
}
