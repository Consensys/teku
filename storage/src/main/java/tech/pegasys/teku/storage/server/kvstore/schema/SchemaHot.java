/*
 * Copyright 2020 ConsenSys AG.
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

import java.util.List;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.pow.api.DepositsFromBlockEvent;
import tech.pegasys.teku.ethereum.pow.api.MinGenesisTimeBlockEvent;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.protoarray.ProtoArraySnapshot;
import tech.pegasys.teku.spec.datastructures.blocks.CheckpointEpochs;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public interface SchemaHot extends Schema {
  KvStoreColumn<Bytes32, SignedBeaconBlock> getColumnHotBlocksByRoot();

  KvStoreColumn<Bytes32, CheckpointEpochs> getColumnHotBlockCheckpointEpochsByRoot();

  // Checkpoint states are no longer stored, keeping only for backwards compatibility.
  KvStoreColumn<Checkpoint, BeaconState> getColumnCheckpointStates();

  KvStoreColumn<UInt64, VoteTracker> getColumnVotes();

  KvStoreColumn<UInt64, DepositsFromBlockEvent> getColumnDepositsFromBlockEvents();

  KvStoreColumn<Bytes32, SlotAndBlockRoot> getColumnStateRootToSlotAndBlockRoot();

  KvStoreColumn<Bytes32, BeaconState> getColumnHotStatesByRoot();

  @Override
  default List<KvStoreColumn<?, ?>> getAllColumns() {
    return List.of(
        getColumnHotBlocksByRoot(),
        getColumnCheckpointStates(),
        getColumnVotes(),
        getColumnDepositsFromBlockEvents(),
        getColumnStateRootToSlotAndBlockRoot(),
        getColumnHotStatesByRoot(),
        getColumnHotBlockCheckpointEpochsByRoot());
  }

  default Map<String, KvStoreColumn<?, ?>> getColumnMap() {
    final List<KvStoreColumn<?, ?>> allColumns = getAllColumns();
    return Map.of(
        "HOT_BLOCKS_BY_ROOT", allColumns.get(0),
        "CHECKPOINT_STATES", allColumns.get(1),
        "VOTES", allColumns.get(2),
        "DEPOSITS_FROM_BLOCK_EVENTS", allColumns.get(3),
        "STATE_ROOT_TO_SLOT_AND_BLOCK_ROOT", allColumns.get(4),
        "HOT_STATES_BY_ROOT", allColumns.get(5),
        "HOT_BLOCK_CHECKPOINT_EPOCHS_BY_ROOT", allColumns.get(6));
  }

  // Variables
  KvStoreVariable<UInt64> getVariableGenesisTime();

  KvStoreVariable<Checkpoint> getVariableJustifiedCheckpoint();

  KvStoreVariable<Checkpoint> getVariableBestJustifiedCheckpoint();

  KvStoreVariable<Checkpoint> getVariableFinalizedCheckpoint();

  KvStoreVariable<BeaconState> getVariableLatestFinalizedState();

  KvStoreVariable<MinGenesisTimeBlockEvent> getVariableMinGenesisTimeBlock();

  KvStoreVariable<ProtoArraySnapshot> getVariableProtoArraySnapshot();

  KvStoreVariable<Checkpoint> getVariableWeakSubjectivityCheckpoint();

  KvStoreVariable<Checkpoint> getVariableAnchorCheckpoint();

  @Override
  default List<KvStoreVariable<?>> getAllVariables() {
    return List.of(
        getVariableGenesisTime(),
        getVariableJustifiedCheckpoint(),
        getVariableBestJustifiedCheckpoint(),
        getVariableFinalizedCheckpoint(),
        getVariableLatestFinalizedState(),
        getVariableMinGenesisTimeBlock(),
        getVariableProtoArraySnapshot(),
        getVariableWeakSubjectivityCheckpoint(),
        getVariableAnchorCheckpoint());
  }

  default Map<String, KvStoreVariable<?>> getVariableMap() {
    final List<KvStoreVariable<?>> allVariables = getAllVariables();
    return Map.of(
        "GENESIS_TIME", allVariables.get(0),
        "JUSTIFIED_CHECKPOINT", allVariables.get(1),
        "BEST_JUSTIFIED_CHECKPOINT", allVariables.get(2),
        "FINALIZED_CHECKPOINT", allVariables.get(3),
        "LATEST_FINALIZED_STATE", allVariables.get(4),
        "MIN_GENESIS_TIME_BLOCK", allVariables.get(5),
        "PROTO_ARRAY_SNAPSHOT", allVariables.get(6),
        "WEAK_SUBJECTIVITY_CHECKPOINT", allVariables.get(7),
        "ANCHOR_CHECKPOINT", allVariables.get(8));
  }
}
