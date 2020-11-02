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
import static tech.pegasys.teku.storage.server.rocksdb.serialization.RocksDbSerializer.CHECKPOINT_EPOCHS_SERIALIZER;
import static tech.pegasys.teku.storage.server.rocksdb.serialization.RocksDbSerializer.CHECKPOINT_SERIALIZER;
import static tech.pegasys.teku.storage.server.rocksdb.serialization.RocksDbSerializer.DEPOSITS_FROM_BLOCK_EVENT_SERIALIZER;
import static tech.pegasys.teku.storage.server.rocksdb.serialization.RocksDbSerializer.MIN_GENESIS_TIME_BLOCK_EVENT_SERIALIZER;
import static tech.pegasys.teku.storage.server.rocksdb.serialization.RocksDbSerializer.PROTO_ARRAY_SNAPSHOT_SERIALIZER;
import static tech.pegasys.teku.storage.server.rocksdb.serialization.RocksDbSerializer.SIGNED_BLOCK_SERIALIZER;
import static tech.pegasys.teku.storage.server.rocksdb.serialization.RocksDbSerializer.SLOT_AND_BLOCK_ROOT_SERIALIZER;
import static tech.pegasys.teku.storage.server.rocksdb.serialization.RocksDbSerializer.STATE_SERIALIZER;
import static tech.pegasys.teku.storage.server.rocksdb.serialization.RocksDbSerializer.UINT64_SERIALIZER;
import static tech.pegasys.teku.storage.server.rocksdb.serialization.RocksDbSerializer.VOTES_SERIALIZER;

import java.util.List;
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

public class V4SchemaHot implements SchemaHot {
  public static final V4SchemaHot INSTANCE = new V4SchemaHot();

  private static final RocksDbColumn<Bytes32, SignedBeaconBlock> HOT_BLOCKS_BY_ROOT =
      RocksDbColumn.create(1, BYTES32_SERIALIZER, SIGNED_BLOCK_SERIALIZER);
  // Checkpoint states are no longer stored, keeping only for backwards compatibility.
  private static final RocksDbColumn<Checkpoint, BeaconState> CHECKPOINT_STATES =
      RocksDbColumn.create(2, CHECKPOINT_SERIALIZER, STATE_SERIALIZER);
  private static final RocksDbColumn<UInt64, VoteTracker> VOTES =
      RocksDbColumn.create(3, UINT64_SERIALIZER, VOTES_SERIALIZER);
  private static final RocksDbColumn<UInt64, DepositsFromBlockEvent> DEPOSITS_FROM_BLOCK_EVENTS =
      RocksDbColumn.create(4, UINT64_SERIALIZER, DEPOSITS_FROM_BLOCK_EVENT_SERIALIZER);
  private static final RocksDbColumn<Bytes32, SlotAndBlockRoot> STATE_ROOT_TO_SLOT_AND_BLOCK_ROOT =
      RocksDbColumn.create(5, BYTES32_SERIALIZER, SLOT_AND_BLOCK_ROOT_SERIALIZER);
  private static final RocksDbColumn<Bytes32, BeaconState> HOT_STATES_BY_ROOT =
      RocksDbColumn.create(6, BYTES32_SERIALIZER, STATE_SERIALIZER);
  private static final RocksDbColumn<Bytes32, CheckpointEpochs>
      HOT_BLOCK_CHECKPOINT_EPOCHS_BY_ROOT =
          RocksDbColumn.create(7, BYTES32_SERIALIZER, CHECKPOINT_EPOCHS_SERIALIZER);

  // Variables
  private static final RocksDbVariable<UInt64> GENESIS_TIME =
      RocksDbVariable.create(1, UINT64_SERIALIZER);
  private static final RocksDbVariable<Checkpoint> JUSTIFIED_CHECKPOINT =
      RocksDbVariable.create(2, CHECKPOINT_SERIALIZER);
  private static final RocksDbVariable<Checkpoint> BEST_JUSTIFIED_CHECKPOINT =
      RocksDbVariable.create(3, CHECKPOINT_SERIALIZER);
  private static final RocksDbVariable<Checkpoint> FINALIZED_CHECKPOINT =
      RocksDbVariable.create(4, CHECKPOINT_SERIALIZER);
  private static final RocksDbVariable<BeaconState> LATEST_FINALIZED_STATE =
      RocksDbVariable.create(5, STATE_SERIALIZER);
  private static final RocksDbVariable<MinGenesisTimeBlockEvent> MIN_GENESIS_TIME_BLOCK =
      RocksDbVariable.create(6, MIN_GENESIS_TIME_BLOCK_EVENT_SERIALIZER);
  private static final RocksDbVariable<ProtoArraySnapshot> PROTO_ARRAY_SNAPSHOT =
      RocksDbVariable.create(7, PROTO_ARRAY_SNAPSHOT_SERIALIZER);
  private static final RocksDbVariable<Checkpoint> WEAK_SUBJECTIVITY_CHECKPOINT =
      RocksDbVariable.create(8, CHECKPOINT_SERIALIZER);
  private static final RocksDbVariable<Checkpoint> ANCHOR_CHECKPOINT =
      RocksDbVariable.create(9, CHECKPOINT_SERIALIZER);

  private static final List<RocksDbColumn<?, ?>> ALL_COLUMNS =
      List.of(
          HOT_BLOCKS_BY_ROOT,
          CHECKPOINT_STATES,
          VOTES,
          DEPOSITS_FROM_BLOCK_EVENTS,
          STATE_ROOT_TO_SLOT_AND_BLOCK_ROOT,
          HOT_STATES_BY_ROOT,
          HOT_BLOCK_CHECKPOINT_EPOCHS_BY_ROOT);

  private static final List<RocksDbVariable<?>> ALL_VARIABLES =
      List.of(
          GENESIS_TIME,
          JUSTIFIED_CHECKPOINT,
          BEST_JUSTIFIED_CHECKPOINT,
          FINALIZED_CHECKPOINT,
          LATEST_FINALIZED_STATE,
          MIN_GENESIS_TIME_BLOCK,
          PROTO_ARRAY_SNAPSHOT,
          WEAK_SUBJECTIVITY_CHECKPOINT,
          ANCHOR_CHECKPOINT);

  private V4SchemaHot() {}

  @Override
  public RocksDbColumn<Bytes32, SignedBeaconBlock> getColumnHotBlocksByRoot() {
    return HOT_BLOCKS_BY_ROOT;
  }

  @Override
  public RocksDbColumn<Bytes32, CheckpointEpochs> getColumnHotBlockCheckpointEpochsByRoot() {
    return HOT_BLOCK_CHECKPOINT_EPOCHS_BY_ROOT;
  }

  @Override
  public RocksDbColumn<Checkpoint, BeaconState> getColumnCheckpointStates() {
    return CHECKPOINT_STATES;
  }

  @Override
  public RocksDbColumn<UInt64, VoteTracker> getColumnVotes() {
    return VOTES;
  }

  @Override
  public RocksDbColumn<UInt64, DepositsFromBlockEvent> getColumnDepositsFromBlockEvents() {
    return DEPOSITS_FROM_BLOCK_EVENTS;
  }

  @Override
  public RocksDbColumn<Bytes32, SlotAndBlockRoot> getColumnStateRootToSlotAndBlockRoot() {
    return STATE_ROOT_TO_SLOT_AND_BLOCK_ROOT;
  }

  @Override
  public RocksDbColumn<Bytes32, BeaconState> getColumnHotStatesByRoot() {
    return HOT_STATES_BY_ROOT;
  }

  @Override
  public RocksDbVariable<UInt64> getVariableGenesisTime() {
    return GENESIS_TIME;
  }

  @Override
  public RocksDbVariable<Checkpoint> getVariableJustifiedCheckpoint() {
    return JUSTIFIED_CHECKPOINT;
  }

  @Override
  public RocksDbVariable<Checkpoint> getVariableBestJustifiedCheckpoint() {
    return BEST_JUSTIFIED_CHECKPOINT;
  }

  @Override
  public RocksDbVariable<Checkpoint> getVariableFinalizedCheckpoint() {
    return FINALIZED_CHECKPOINT;
  }

  @Override
  public RocksDbVariable<BeaconState> getVariableLatestFinalizedState() {
    return LATEST_FINALIZED_STATE;
  }

  @Override
  public RocksDbVariable<MinGenesisTimeBlockEvent> getVariableMinGenesisTimeBlock() {
    return MIN_GENESIS_TIME_BLOCK;
  }

  @Override
  public RocksDbVariable<ProtoArraySnapshot> getVariableProtoArraySnapshot() {
    return PROTO_ARRAY_SNAPSHOT;
  }

  @Override
  public RocksDbVariable<Checkpoint> getVariableWeakSubjectivityCheckpoint() {
    return WEAK_SUBJECTIVITY_CHECKPOINT;
  }

  @Override
  public RocksDbVariable<Checkpoint> getVariableAnchorCheckpoint() {
    return ANCHOR_CHECKPOINT;
  }

  @Override
  public List<RocksDbColumn<?, ?>> getAllColumns() {
    return ALL_COLUMNS;
  }

  @Override
  public List<RocksDbVariable<?>> getAllVariables() {
    return ALL_VARIABLES;
  }
}
