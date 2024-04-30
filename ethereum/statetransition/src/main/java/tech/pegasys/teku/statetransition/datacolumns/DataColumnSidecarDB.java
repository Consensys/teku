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
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnIdentifier;

public interface DataColumnSidecarDB {

  // read

  Optional<UInt64> getFirstIncompleteSlot();

  Optional<DataColumnSidecar> getSidecar(DataColumnIdentifier identifier);

  Stream<DataColumnIdentifier> streamColumnIdentifiers(UInt64 slot);

  // update

  void setFirstIncompleteSlot(UInt64 slot);

  void addSidecar(DataColumnSidecar sidecar);

  void pruneAllSidecars(UInt64 tillSlot);
}
