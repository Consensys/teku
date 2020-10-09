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
  RocksDbColumn<Bytes32, SignedBeaconBlock> column_HOT_BLOCKS_BY_ROOT();
  // Checkpoint states are no longer stored, keeping only for backwards compatibility.
  RocksDbColumn<Checkpoint, BeaconState> column_CHECKPOINT_STATES();
  RocksDbColumn<UInt64, VoteTracker> column_VOTES();
  RocksDbColumn<UInt64, DepositsFromBlockEvent> column_DEPOSITS_FROM_BLOCK_EVENTS();
  RocksDbColumn<Bytes32, SlotAndBlockRoot> column_STATE_ROOT_TO_SLOT_AND_BLOCK_ROOT();
  RocksDbColumn<Bytes32, BeaconState> column_HOT_STATES_BY_ROOT();

  // Variables
  RocksDbVariable<UInt64> variable_GENESIS_TIME();
  RocksDbVariable<Checkpoint> variable_JUSTIFIED_CHECKPOINT();
  RocksDbVariable<Checkpoint> variable_BEST_JUSTIFIED_CHECKPOINT();
  RocksDbVariable<Checkpoint> variable_FINALIZED_CHECKPOINT();
  RocksDbVariable<BeaconState> variable_LATEST_FINALIZED_STATE();
  RocksDbVariable<MinGenesisTimeBlockEvent> variable_MIN_GENESIS_TIME_BLOCK();
  RocksDbVariable<ProtoArraySnapshot> variable_PROTO_ARRAY_SNAPSHOT();
  RocksDbVariable<Checkpoint> variable_WEAK_SUBJECTIVITY_CHECKPOINT();
}
