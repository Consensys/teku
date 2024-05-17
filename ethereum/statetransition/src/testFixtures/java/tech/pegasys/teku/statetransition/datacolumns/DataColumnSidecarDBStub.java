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
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnIdentifier;

public class DataColumnSidecarDBStub implements DataColumnSidecarDB {

  private Optional<UInt64> firstIncompleteSlot = Optional.empty();
  private Map<DataColumnIdentifier, DataColumnSidecar> db = new HashMap<>();
  private Map<UInt64, Set<DataColumnIdentifier>> slotIds = new HashMap<>();

  @Override
  public void setFirstIncompleteSlot(UInt64 slot) {
    this.firstIncompleteSlot = Optional.of(slot);
  }

  @Override
  public Optional<UInt64> getFirstIncompleteSlot() {
    return firstIncompleteSlot;
  }

  @Override
  public void addSidecar(DataColumnSidecar sidecar) {
    DataColumnIdentifier identifier = DataColumnIdentifier.createFromSidecar(sidecar);
    db.put(identifier, sidecar);
    slotIds.computeIfAbsent(sidecar.getSlot(), __ -> new HashSet<>()).add(identifier);
  }

  @Override
  public Optional<DataColumnSidecar> getSidecar(DataColumnIdentifier identifier) {
    return Optional.ofNullable(db.get(identifier));
  }

  @Override
  public Stream<DataColumnIdentifier> streamColumnIdentifiers(UInt64 slot) {
    return slotIds.getOrDefault(slot, Collections.emptySet()).stream();
  }

  @Override
  public void pruneAllSidecars(UInt64 tillSlot) {}
}
