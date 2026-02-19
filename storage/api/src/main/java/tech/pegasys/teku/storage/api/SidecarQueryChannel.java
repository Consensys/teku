/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.storage.api;

import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.events.ChannelInterface;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;

/** Query channel for data column sidecar storage operations. */
public interface SidecarQueryChannel extends ChannelInterface {

  SafeFuture<Optional<UInt64>> getFirstCustodyIncompleteSlot();

  SafeFuture<Optional<UInt64>> getEarliestAvailableDataColumnSlot();

  SafeFuture<Optional<DataColumnSidecar>> getSidecar(DataColumnSlotAndIdentifier identifier);

  SafeFuture<Optional<DataColumnSidecar>> getNonCanonicalSidecar(
      DataColumnSlotAndIdentifier identifier);

  SafeFuture<List<DataColumnSlotAndIdentifier>> getDataColumnIdentifiers(UInt64 slot);

  SafeFuture<List<DataColumnSlotAndIdentifier>> getNonCanonicalDataColumnIdentifiers(UInt64 slot);

  SafeFuture<List<DataColumnSlotAndIdentifier>> getDataColumnIdentifiers(
      UInt64 startSlot, UInt64 endSlot, UInt64 limit);

  SafeFuture<Optional<UInt64>> getEarliestDataColumnSidecarSlot();

  SafeFuture<Optional<List<List<KZGProof>>>> getDataColumnSidecarsProofs(UInt64 slot);
}
