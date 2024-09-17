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

import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnIdentifier;
import tech.pegasys.teku.spec.datastructures.util.ColumnSlotAndIdentifier;
import tech.pegasys.teku.storage.api.SidecarUpdateChannel;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

public interface DataColumnSidecarDB extends DataColumnSidecarCoreDB {

  static DataColumnSidecarDB create(
      final CombinedChainDataClient combinedChainDataClient,
      final SidecarUpdateChannel sidecarUpdateChannel) {
    return new DataColumnSidecarDBImpl(combinedChainDataClient, sidecarUpdateChannel);
  }

  // read

  SafeFuture<Optional<UInt64>> getFirstCustodyIncompleteSlot();

  SafeFuture<Optional<UInt64>> getFirstSamplerIncompleteSlot();

  @Override
  SafeFuture<Optional<DataColumnSidecar>> getSidecar(DataColumnIdentifier identifier);

  SafeFuture<Optional<DataColumnSidecar>> getSidecar(ColumnSlotAndIdentifier identifier);

  @Override
  SafeFuture<List<DataColumnIdentifier>> getColumnIdentifiers(UInt64 slot);

  // update

  SafeFuture<Void> setFirstCustodyIncompleteSlot(UInt64 slot);

  SafeFuture<Void> setFirstSamplerIncompleteSlot(UInt64 slot);

  @Override
  SafeFuture<Void> addSidecar(DataColumnSidecar sidecar);

  SafeFuture<Void> pruneAllSidecars(UInt64 tillSlot);
}
