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

import static tech.pegasys.teku.storage.server.rocksdb.serialization.RocksDbSerializer.BYTES32_SERIALIZER;
import static tech.pegasys.teku.storage.server.rocksdb.serialization.RocksDbSerializer.CHECKPOINT_SERIALIZER;
import static tech.pegasys.teku.storage.server.rocksdb.serialization.RocksDbSerializer.DEPOSITS_FROM_BLOCK_EVENT_SERIALIZER;
import static tech.pegasys.teku.storage.server.rocksdb.serialization.RocksDbSerializer.MIN_GENESIS_TIME_BLOCK_EVENT_SERIALIZER;
import static tech.pegasys.teku.storage.server.rocksdb.serialization.RocksDbSerializer.PROTO_ARRAY_SNAPSHOT_SERIALIZER;
import static tech.pegasys.teku.storage.server.rocksdb.serialization.RocksDbSerializer.SIGNED_BLOCK_SERIALIZER;
import static tech.pegasys.teku.storage.server.rocksdb.serialization.RocksDbSerializer.SLOT_AND_BLOCK_ROOT_SERIALIZER;
import static tech.pegasys.teku.storage.server.rocksdb.serialization.RocksDbSerializer.STATE_SERIALIZER;
import static tech.pegasys.teku.storage.server.rocksdb.serialization.RocksDbSerializer.UINT64_SERIALIZER;
import static tech.pegasys.teku.storage.server.rocksdb.serialization.RocksDbSerializer.VOTES_SERIALIZER;

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

public interface V3Schema extends Schema {

  // Columns
  RocksDbColumn<UInt64, Bytes32> FINALIZED_ROOTS_BY_SLOT =
      RocksDbColumn.create(1, UINT64_SERIALIZER, BYTES32_SERIALIZER);
  RocksDbColumn<Bytes32, SignedBeaconBlock> FINALIZED_BLOCKS_BY_ROOT =
      RocksDbColumn.create(2, BYTES32_SERIALIZER, SIGNED_BLOCK_SERIALIZER);
  RocksDbColumn<Bytes32, BeaconState> FINALIZED_STATES_BY_ROOT =
      RocksDbColumn.create(3, BYTES32_SERIALIZER, STATE_SERIALIZER);
  RocksDbColumn<Bytes32, SignedBeaconBlock> HOT_BLOCKS_BY_ROOT =
      RocksDbColumn.create(4, BYTES32_SERIALIZER, SIGNED_BLOCK_SERIALIZER);
  // We no longer store checkpoint states, keeping only for backwards compatibility
  RocksDbColumn<Checkpoint, BeaconState> CHECKPOINT_STATES =
      RocksDbColumn.create(5, CHECKPOINT_SERIALIZER, STATE_SERIALIZER);
  RocksDbColumn<UInt64, VoteTracker> VOTES =
      RocksDbColumn.create(6, UINT64_SERIALIZER, VOTES_SERIALIZER);
  RocksDbColumn<UInt64, DepositsFromBlockEvent> DEPOSITS_FROM_BLOCK_EVENTS =
      RocksDbColumn.create(7, UINT64_SERIALIZER, DEPOSITS_FROM_BLOCK_EVENT_SERIALIZER);
  RocksDbColumn<Bytes32, SlotAndBlockRoot> STATE_ROOT_TO_SLOT_AND_BLOCK_ROOT =
      RocksDbColumn.create(8, BYTES32_SERIALIZER, SLOT_AND_BLOCK_ROOT_SERIALIZER);
  RocksDbColumn<Bytes32, UInt64> SLOTS_BY_FINALIZED_STATE_ROOT =
      RocksDbColumn.create(9, BYTES32_SERIALIZER, UINT64_SERIALIZER);

  // Variables
  RocksDbVariable<UInt64> GENESIS_TIME = RocksDbVariable.create(1, UINT64_SERIALIZER);
  RocksDbVariable<Checkpoint> JUSTIFIED_CHECKPOINT =
      RocksDbVariable.create(2, CHECKPOINT_SERIALIZER);
  RocksDbVariable<Checkpoint> BEST_JUSTIFIED_CHECKPOINT =
      RocksDbVariable.create(3, CHECKPOINT_SERIALIZER);
  RocksDbVariable<Checkpoint> FINALIZED_CHECKPOINT =
      RocksDbVariable.create(4, CHECKPOINT_SERIALIZER);
  RocksDbVariable<BeaconState> LATEST_FINALIZED_STATE = RocksDbVariable.create(5, STATE_SERIALIZER);
  RocksDbVariable<MinGenesisTimeBlockEvent> MIN_GENESIS_TIME_BLOCK =
      RocksDbVariable.create(6, MIN_GENESIS_TIME_BLOCK_EVENT_SERIALIZER);
  RocksDbVariable<ProtoArraySnapshot> PROTO_ARRAY_SNAPSHOT =
      RocksDbVariable.create(7, PROTO_ARRAY_SNAPSHOT_SERIALIZER);
}
