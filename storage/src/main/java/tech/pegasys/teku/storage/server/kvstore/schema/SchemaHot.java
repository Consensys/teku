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

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.pow.event.DepositsFromBlockEvent;
import tech.pegasys.teku.pow.event.MinGenesisTimeBlockEvent;
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
}
