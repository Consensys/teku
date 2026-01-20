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

package tech.pegasys.teku.storage.archive;

import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;

public interface DataColumnSidecarsArchiver {

  DataColumnSidecarsArchiver NOOP =
      new DataColumnSidecarsArchiver() {
        @Override
        public void archive(
            final DataColumnSlotAndIdentifier identifier, final DataColumnSidecar sidecar) {}

        @Override
        public Optional<DataColumnSidecar> retrieve(final DataColumnSlotAndIdentifier identifier) {
          return Optional.empty();
        }

        @Override
        public List<DataColumnSidecar> retrieveForSlot(final UInt64 slot) {
          return List.of();
        }

        @Override
        public List<DataColumnSidecar> retrieveForBlock(final SlotAndBlockRoot slotAndBlockRoot) {
          return List.of();
        }

        @Override
        public void pruneEpoch(final UInt64 epoch) {}
      };

  void archive(DataColumnSlotAndIdentifier identifier, DataColumnSidecar sidecar);

  Optional<DataColumnSidecar> retrieve(DataColumnSlotAndIdentifier identifier);

  List<DataColumnSidecar> retrieveForSlot(UInt64 slot);

  List<DataColumnSidecar> retrieveForBlock(SlotAndBlockRoot slotAndBlockRoot);

  void pruneEpoch(UInt64 epoch);
}
