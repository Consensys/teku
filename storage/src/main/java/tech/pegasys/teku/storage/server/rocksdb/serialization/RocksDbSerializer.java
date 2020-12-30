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

package tech.pegasys.teku.storage.server.rocksdb.serialization;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.CheckpointEpochs;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.BeaconStateImpl;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.pow.event.DepositsFromBlockEvent;
import tech.pegasys.teku.pow.event.MinGenesisTimeBlockEvent;
import tech.pegasys.teku.protoarray.ProtoArraySnapshot;

public interface RocksDbSerializer<T> {
  RocksDbSerializer<UInt64> UINT64_SERIALIZER = new UInt64Serializer();
  RocksDbSerializer<Bytes32> BYTES32_SERIALIZER = new BytesSerializer<>(Bytes32::wrap);
  RocksDbSerializer<SignedBeaconBlock> SIGNED_BLOCK_SERIALIZER =
      new SszSerializer<>(SignedBeaconBlock.class);
  RocksDbSerializer<BeaconState> STATE_SERIALIZER = new SszSerializer<>(BeaconStateImpl.class);
  RocksDbSerializer<Checkpoint> CHECKPOINT_SERIALIZER = new SszSerializer<>(Checkpoint.class);
  RocksDbSerializer<VoteTracker> VOTES_SERIALIZER = new VoteTrackerSerializer();
  RocksDbSerializer<DepositsFromBlockEvent> DEPOSITS_FROM_BLOCK_EVENT_SERIALIZER =
      new DepositsFromBlockEventSerializer();
  RocksDbSerializer<MinGenesisTimeBlockEvent> MIN_GENESIS_TIME_BLOCK_EVENT_SERIALIZER =
      new MinGenesisTimeBlockEventSerializer();
  RocksDbSerializer<ProtoArraySnapshot> PROTO_ARRAY_SNAPSHOT_SERIALIZER =
      new ProtoArraySnapshotSerializer();
  RocksDbSerializer<SlotAndBlockRoot> SLOT_AND_BLOCK_ROOT_SERIALIZER =
      new SlotAndBlockRootSerializer();
  RocksDbSerializer<CheckpointEpochs> CHECKPOINT_EPOCHS_SERIALIZER =
      new CheckpointEpochsSerializer();

  T deserialize(final byte[] data);

  byte[] serialize(final T value);
}
