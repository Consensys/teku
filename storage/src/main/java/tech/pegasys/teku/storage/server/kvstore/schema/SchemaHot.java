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

import java.util.Collection;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.pow.api.DepositsFromBlockEvent;
import tech.pegasys.teku.ethereum.pow.api.MinGenesisTimeBlockEvent;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
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
  default Collection<KvStoreColumn<?, ?>> getAllColumns() {
    return getColumnMap().values();
  }

  default Map<String, KvStoreColumn<?, ?>> getColumnMap() {
    return Map.of(
        "HOT_BLOCKS_BY_ROOT", getColumnHotBlocksByRoot(),
        "CHECKPOINT_STATES", getColumnCheckpointStates(),
        "VOTES", getColumnVotes(),
        "DEPOSITS_FROM_BLOCK_EVENTS", getColumnDepositsFromBlockEvents(),
        "STATE_ROOT_TO_SLOT_AND_BLOCK_ROOT", getColumnStateRootToSlotAndBlockRoot(),
        "HOT_STATES_BY_ROOT", getColumnHotStatesByRoot(),
        "HOT_BLOCK_CHECKPOINT_EPOCHS_BY_ROOT", getColumnHotBlockCheckpointEpochsByRoot());
  }

  // Variables
  KvStoreVariable<UInt64> getVariableGenesisTime();

  KvStoreVariable<Checkpoint> getVariableJustifiedCheckpoint();

  KvStoreVariable<Checkpoint> getVariableBestJustifiedCheckpoint();

  KvStoreVariable<Checkpoint> getVariableFinalizedCheckpoint();

  KvStoreVariable<BeaconState> getVariableLatestFinalizedState();

  KvStoreVariable<MinGenesisTimeBlockEvent> getVariableMinGenesisTimeBlock();

  KvStoreVariable<Checkpoint> getVariableWeakSubjectivityCheckpoint();

  KvStoreVariable<Checkpoint> getVariableAnchorCheckpoint();

  @Override
  default Collection<KvStoreVariable<?>> getAllVariables() {
    return getVariableMap().values();
  }

  default Map<String, KvStoreVariable<?>> getVariableMap() {
    return Map.of(
        "GENESIS_TIME", getVariableGenesisTime(),
        "JUSTIFIED_CHECKPOINT", getVariableJustifiedCheckpoint(),
        "BEST_JUSTIFIED_CHECKPOINT", getVariableBestJustifiedCheckpoint(),
        "FINALIZED_CHECKPOINT", getVariableFinalizedCheckpoint(),
        "LATEST_FINALIZED_STATE", getVariableLatestFinalizedState(),
        "MIN_GENESIS_TIME_BLOCK", getVariableMinGenesisTimeBlock(),
        "WEAK_SUBJECTIVITY_CHECKPOINT", getVariableWeakSubjectivityCheckpoint(),
        "ANCHOR_CHECKPOINT", getVariableAnchorCheckpoint());
  }
}
