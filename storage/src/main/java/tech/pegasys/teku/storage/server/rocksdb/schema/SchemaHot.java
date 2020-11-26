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

package tech.pegasys.teku.storage.server.rocksdb.schema;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.CheckpointEpochs;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.pow.event.DepositsFromBlockEvent;
import tech.pegasys.teku.pow.event.MinGenesisTimeBlockEvent;
import tech.pegasys.teku.protoarray.ProtoArraySnapshot;

public interface SchemaHot extends Schema {
  RocksDbColumn<Bytes32, SignedBeaconBlock> getColumnHotBlocksByRoot();

  RocksDbColumn<Bytes32, CheckpointEpochs> getColumnHotBlockCheckpointEpochsByRoot();

  // Checkpoint states are no longer stored, keeping only for backwards compatibility.
  RocksDbColumn<Checkpoint, BeaconState> getColumnCheckpointStates();

  RocksDbColumn<UInt64, VoteTracker> getColumnVotes();

  RocksDbColumn<UInt64, DepositsFromBlockEvent> getColumnDepositsFromBlockEvents();

  RocksDbColumn<Bytes32, SlotAndBlockRoot> getColumnStateRootToSlotAndBlockRoot();

  RocksDbColumn<Bytes32, BeaconState> getColumnHotStatesByRoot();

  // Variables
  RocksDbVariable<UInt64> getVariableGenesisTime();

  RocksDbVariable<Checkpoint> getVariableJustifiedCheckpoint();

  RocksDbVariable<Checkpoint> getVariableBestJustifiedCheckpoint();

  RocksDbVariable<Checkpoint> getVariableFinalizedCheckpoint();

  RocksDbVariable<BeaconState> getVariableLatestFinalizedState();

  RocksDbVariable<MinGenesisTimeBlockEvent> getVariableMinGenesisTimeBlock();

  RocksDbVariable<ProtoArraySnapshot> getVariableProtoArraySnapshot();

  RocksDbVariable<Checkpoint> getVariableWeakSubjectivityCheckpoint();

  RocksDbVariable<Checkpoint> getVariableAnchorCheckpoint();
}
