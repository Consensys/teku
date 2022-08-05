/*
 * Copyright ConsenSys Software Inc., 2022
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
import java.util.Set;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class SchemaFinalizedSnapshotStateAdapter implements SchemaFinalizedSnapshotState {

  private final SchemaCombinedSnapshotState snapshotDelegate;
  private final SchemaCombined delegate;

  public SchemaFinalizedSnapshotStateAdapter(final SchemaCombinedSnapshotState delegate) {
    this.delegate = delegate;
    this.snapshotDelegate = delegate;
  }

  @Override
  public KvStoreColumn<UInt64, BeaconState> getColumnFinalizedStatesBySlot() {
    return snapshotDelegate.getColumnFinalizedStatesBySlot();
  }

  public KvStoreColumn<Bytes32, SignedBeaconBlock> getColumnBlindedBlocksByRoot() {
    return delegate.getColumnBlindedBlocksByRoot();
  }

  public KvStoreColumn<UInt64, Bytes32> getColumnFinalizedBlockRootBySlot() {
    return delegate.getColumnFinalizedBlockRootBySlot();
  }

  public KvStoreColumn<Bytes32, Bytes> getColumnExecutionPayloadByBlockRoot() {
    return delegate.getColumnExecutionPayloadByBlockRoot();
  }

  public Map<String, KvStoreColumn<?, ?>> getColumnMap() {
    return Map.of(
        "SLOTS_BY_FINALIZED_ROOT", getColumnSlotsByFinalizedRoot(),
        "FINALIZED_BLOCKS_BY_SLOT", getColumnFinalizedBlocksBySlot(),
        "FINALIZED_STATES_BY_SLOT", getColumnFinalizedStatesBySlot(),
        "SLOTS_BY_FINALIZED_STATE_ROOT", getColumnSlotsByFinalizedStateRoot(),
        "NON_CANONICAL_BLOCKS_BY_ROOT", getColumnNonCanonicalBlocksByRoot(),
        "NON_CANONICAL_BLOCK_ROOTS_BY_SLOT", getColumnNonCanonicalRootsBySlot(),
        "BLINDED_BLOCKS_BY_ROOT", getColumnBlindedBlocksByRoot(),
        "EXECUTION_PAYLOAD_BY_BLOCK_ROOT", getColumnExecutionPayloadByBlockRoot(),
        "FINALIZED_BLOCK_ROOT_BY_SLOT", getColumnFinalizedBlockRootBySlot());
  }

  public Collection<KvStoreColumn<?, ?>> getAllColumns() {
    return getColumnMap().values();
  }

  public Collection<KvStoreVariable<?>> getAllVariables() {
    return getVariableMap().values();
  }

  public KvStoreColumn<Bytes32, UInt64> getColumnSlotsByFinalizedRoot() {
    return delegate.getColumnSlotsByFinalizedRoot();
  }

  public KvStoreColumn<UInt64, SignedBeaconBlock> getColumnFinalizedBlocksBySlot() {
    return delegate.getColumnFinalizedBlocksBySlot();
  }

  public KvStoreColumn<Bytes32, UInt64> getColumnSlotsByFinalizedStateRoot() {
    return delegate.getColumnSlotsByFinalizedStateRoot();
  }

  public KvStoreColumn<Bytes32, SignedBeaconBlock> getColumnNonCanonicalBlocksByRoot() {
    return delegate.getColumnNonCanonicalBlocksByRoot();
  }

  public KvStoreColumn<UInt64, Set<Bytes32>> getColumnNonCanonicalRootsBySlot() {
    return delegate.getColumnNonCanonicalRootsBySlot();
  }

  public KvStoreVariable<UInt64> getOptimisticTransitionBlockSlot() {
    return delegate.getOptimisticTransitionBlockSlot();
  }

  public Map<String, KvStoreVariable<?>> getVariableMap() {
    return Map.of("OPTIMISTIC_TRANSITION_BLOCK_SLOT", getOptimisticTransitionBlockSlot());
  }
}
