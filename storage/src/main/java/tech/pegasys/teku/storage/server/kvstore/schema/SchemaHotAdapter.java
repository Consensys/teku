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
import java.util.Collections;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.pow.api.DepositTreeSnapshot;
import tech.pegasys.teku.ethereum.pow.api.DepositsFromBlockEvent;
import tech.pegasys.teku.ethereum.pow.api.MinGenesisTimeBlockEvent;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.util.SlotAndBlockRootAndBlobIndex;

public class SchemaHotAdapter implements Schema {
  private final SchemaCombined delegate;

  public SchemaHotAdapter(final SchemaCombined delegate) {
    this.delegate = delegate;
  }

  public KvStoreColumn<Bytes32, SignedBeaconBlock> getColumnHotBlocksByRoot() {
    return delegate.getColumnHotBlocksByRoot();
  }

  public KvStoreColumn<Bytes32, BlockCheckpoints> getColumnHotBlockCheckpointEpochsByRoot() {
    return delegate.getColumnHotBlockCheckpointEpochsByRoot();
  }

  public KvStoreColumn<Checkpoint, BeaconState> getColumnCheckpointStates() {
    return delegate.getColumnCheckpointStates();
  }

  public KvStoreColumn<UInt64, VoteTracker> getColumnVotes() {
    return delegate.getColumnVotes();
  }

  public KvStoreColumn<UInt64, DepositsFromBlockEvent> getColumnDepositsFromBlockEvents() {
    return delegate.getColumnDepositsFromBlockEvents();
  }

  public KvStoreColumn<Bytes32, SlotAndBlockRoot> getColumnStateRootToSlotAndBlockRoot() {
    return delegate.getColumnStateRootToSlotAndBlockRoot();
  }

  public KvStoreColumn<Bytes32, BeaconState> getColumnHotStatesByRoot() {
    return delegate.getColumnHotStatesByRoot();
  }

  public KvStoreColumn<SlotAndBlockRootAndBlobIndex, Bytes>
      getColumnBlobSidecarBySlotRootBlobIndex() {
    return delegate.getColumnBlobSidecarBySlotRootBlobIndex();
  }

  public KvStoreVariable<UInt64> getVariableGenesisTime() {
    return delegate.getVariableGenesisTime();
  }

  public KvStoreVariable<Checkpoint> getVariableJustifiedCheckpoint() {
    return delegate.getVariableJustifiedCheckpoint();
  }

  public KvStoreVariable<Checkpoint> getVariableBestJustifiedCheckpoint() {
    return delegate.getVariableBestJustifiedCheckpoint();
  }

  public KvStoreVariable<Checkpoint> getVariableFinalizedCheckpoint() {
    return delegate.getVariableFinalizedCheckpoint();
  }

  public KvStoreVariable<BeaconState> getVariableLatestFinalizedState() {
    return delegate.getVariableLatestFinalizedState();
  }

  public KvStoreVariable<MinGenesisTimeBlockEvent> getVariableMinGenesisTimeBlock() {
    return delegate.getVariableMinGenesisTimeBlock();
  }

  public KvStoreVariable<Checkpoint> getVariableWeakSubjectivityCheckpoint() {
    return delegate.getVariableWeakSubjectivityCheckpoint();
  }

  public KvStoreVariable<Checkpoint> getVariableAnchorCheckpoint() {
    return delegate.getVariableAnchorCheckpoint();
  }

  public KvStoreVariable<DepositTreeSnapshot> getVariableFinalizedDepositSnapshot() {
    return delegate.getVariableFinalizedDepositSnapshot();
  }

  public Map<String, KvStoreColumn<?, ?>> getColumnMap() {
    return Map.of(
        "HOT_BLOCKS_BY_ROOT",
        getColumnHotBlocksByRoot(),
        "CHECKPOINT_STATES",
        getColumnCheckpointStates(),
        "VOTES",
        getColumnVotes(),
        "DEPOSITS_FROM_BLOCK_EVENTS",
        getColumnDepositsFromBlockEvents(),
        "STATE_ROOT_TO_SLOT_AND_BLOCK_ROOT",
        getColumnStateRootToSlotAndBlockRoot(),
        "HOT_STATES_BY_ROOT",
        getColumnHotStatesByRoot(),
        "HOT_BLOCK_CHECKPOINT_EPOCHS_BY_ROOT",
        getColumnHotBlockCheckpointEpochsByRoot(),
        "BLOB_SIDECAR_BY_SLOT_AND_BLOCK_ROOT_AND_BLOB_INDEX",
        getColumnBlobSidecarBySlotRootBlobIndex());
  }

  public Map<String, KvStoreVariable<?>> getVariableMap() {
    return Map.of(
        "GENESIS_TIME",
        getVariableGenesisTime(),
        "JUSTIFIED_CHECKPOINT",
        getVariableJustifiedCheckpoint(),
        "BEST_JUSTIFIED_CHECKPOINT",
        getVariableBestJustifiedCheckpoint(),
        "FINALIZED_CHECKPOINT",
        getVariableFinalizedCheckpoint(),
        "LATEST_FINALIZED_STATE",
        getVariableLatestFinalizedState(),
        "MIN_GENESIS_TIME_BLOCK",
        getVariableMinGenesisTimeBlock(),
        "WEAK_SUBJECTIVITY_CHECKPOINT",
        getVariableWeakSubjectivityCheckpoint(),
        "ANCHOR_CHECKPOINT",
        getVariableAnchorCheckpoint(),
        "FINALIZED_DEPOSIT_SNAPSHOT",
        getVariableFinalizedDepositSnapshot());
  }

  @Override
  public Collection<KvStoreColumn<?, ?>> getAllColumns() {
    return getColumnMap().values();
  }

  @Override
  public Collection<KvStoreVariable<?>> getAllVariables() {
    return getVariableMap().values();
  }

  @Override
  public Collection<Bytes> getDeletedColumnIds() {
    // No hot db columns have been removed currently
    return Collections.emptyList();
  }
}
