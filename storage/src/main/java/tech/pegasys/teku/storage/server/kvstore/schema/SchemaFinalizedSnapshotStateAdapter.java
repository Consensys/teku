/*
 * Copyright 2022 ConsenSys AG.
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

import java.util.Collection;
import java.util.Map;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class SchemaFinalizedSnapshotStateAdapter extends SchemaFinalizedAdapter
    implements SchemaFinalizedSnapshotState {

  private final SchemaCombinedSnapshotState snapshotDelegate;

  public SchemaFinalizedSnapshotStateAdapter(final SchemaCombinedSnapshotState delegate) {
    super(delegate);
    this.snapshotDelegate = delegate;
  }

  @Override
  public KvStoreColumn<UInt64, BeaconState> getColumnFinalizedStatesBySlot() {
    return snapshotDelegate.getColumnFinalizedStatesBySlot();
  }

  @Override
  public Map<String, KvStoreColumn<?, ?>> getColumnMap() {
    return Map.of(
        "SLOTS_BY_FINALIZED_ROOT", getColumnSlotsByFinalizedRoot(),
        "FINALIZED_BLOCKS_BY_SLOT", getColumnFinalizedBlocksBySlot(),
        "FINALIZED_STATES_BY_SLOT", getColumnFinalizedStatesBySlot(),
        "SLOTS_BY_FINALIZED_STATE_ROOT", getColumnSlotsByFinalizedStateRoot(),
        "NON_CANONICAL_BLOCKS_BY_ROOT", getColumnNonCanonicalBlocksByRoot(),
        "NON_CANONICAL_BLOCK_ROOTS_BY_SLOT", getColumnNonCanonicalRootsBySlot());
  }

  @Override
  public Collection<KvStoreColumn<?, ?>> getAllColumns() {
    return getColumnMap().values();
  }

  @Override
  public Collection<KvStoreVariable<?>> getAllVariables() {
    return getVariableMap().values();
  }
}
