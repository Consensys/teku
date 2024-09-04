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

import java.util.Optional;
import java.util.stream.Stream;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnIdentifier;
import tech.pegasys.teku.spec.datastructures.util.ColumnSlotAndIdentifier;

public interface DataColumnSidecarDB {

  // read

  SafeFuture<Optional<UInt64>> getFirstCustodyIncompleteSlot();

  SafeFuture<Optional<UInt64>> getFirstSamplerIncompleteSlot();

  SafeFuture<Optional<DataColumnSidecar>> getSidecar(DataColumnIdentifier identifier);

  SafeFuture<Optional<DataColumnSidecar>> getSidecar(ColumnSlotAndIdentifier identifier);

  SafeFuture<Stream<DataColumnIdentifier>> streamColumnIdentifiers(UInt64 slot);

  // update

  SafeFuture<Void> setFirstCustodyIncompleteSlot(UInt64 slot);

  SafeFuture<Void> setFirstSamplerIncompleteSlot(UInt64 slot);

  void addSidecar(DataColumnSidecar sidecar);

  void pruneAllSidecars(UInt64 tillSlot);
}
