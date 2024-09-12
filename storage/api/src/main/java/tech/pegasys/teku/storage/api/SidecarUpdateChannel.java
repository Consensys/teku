/*
 * Copyright Consensys Software Inc., 2022
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

import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.events.ChannelInterface;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;

public interface SidecarUpdateChannel extends ChannelInterface {

  // TODO: as it's pushed separately from sidecars, an eventual consistency could occur.
  //  Clarify that it's safe
  SafeFuture<Void> onFirstCustodyIncompleteSlot(UInt64 slot);

  SafeFuture<Void> onFirstSamplerIncompleteSlot(UInt64 slot);

  SafeFuture<Void> onNewSidecar(DataColumnSidecar sidecar);

  // TODO: Make a dedicated pruner instead
  SafeFuture<Void> onSidecarsAvailabilitySlot(UInt64 earliestSlotRequired);
}
