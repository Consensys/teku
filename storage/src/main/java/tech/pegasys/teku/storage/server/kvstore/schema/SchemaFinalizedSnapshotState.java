/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.storage.server.kvstore.schema;

import java.util.Map;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public interface SchemaFinalizedSnapshotState extends SchemaFinalized {

  KvStoreColumn<UInt64, BeaconState> getColumnFinalizedStatesBySlot();

  @Override
  default Map<String, KvStoreColumn<?, ?>> getColumnMap() {
    return Map.of(
        "SLOTS_BY_FINALIZED_ROOT", getColumnSlotsByFinalizedRoot(),
        "FINALIZED_BLOCKS_BY_SLOT", getColumnFinalizedBlocksBySlot(),
        "FINALIZED_STATES_BY_SLOT", getColumnFinalizedStatesBySlot(),
        "SLOTS_BY_FINALIZED_STATE_ROOT", getColumnSlotsByFinalizedStateRoot(),
        "NON_CANONICAL_BLOCKS_BY_ROOT", getColumnNonCanonicalBlocksByRoot(),
        "NON_CANONICAL_BLOCK_ROOTS_BY_SLOT", getColumnNonCanonicalRootsBySlot());
  }
}
