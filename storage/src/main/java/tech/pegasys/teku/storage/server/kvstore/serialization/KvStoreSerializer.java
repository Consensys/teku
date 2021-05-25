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

package tech.pegasys.teku.storage.server.kvstore.serialization;

import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.pow.api.DepositsFromBlockEvent;
import tech.pegasys.teku.ethereum.pow.api.MinGenesisTimeBlockEvent;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.protoarray.ProtoArraySnapshot;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.CheckpointEpochs;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public interface KvStoreSerializer<T> {
  KvStoreSerializer<UInt64> UINT64_SERIALIZER = new UInt64Serializer();
  KvStoreSerializer<Bytes32> BYTES32_SERIALIZER = new BytesSerializer<>(Bytes32::wrap);
  KvStoreSerializer<Checkpoint> CHECKPOINT_SERIALIZER = new SszSerializer<>(Checkpoint.SSZ_SCHEMA);
  KvStoreSerializer<VoteTracker> VOTES_SERIALIZER = new VoteTrackerSerializer();
  KvStoreSerializer<DepositsFromBlockEvent> DEPOSITS_FROM_BLOCK_EVENT_SERIALIZER =
      new DepositsFromBlockEventSerializer();
  KvStoreSerializer<MinGenesisTimeBlockEvent> MIN_GENESIS_TIME_BLOCK_EVENT_SERIALIZER =
      new MinGenesisTimeBlockEventSerializer();
  KvStoreSerializer<ProtoArraySnapshot> PROTO_ARRAY_SNAPSHOT_SERIALIZER =
      new ProtoArraySnapshotSerializer();
  KvStoreSerializer<SlotAndBlockRoot> SLOT_AND_BLOCK_ROOT_SERIALIZER =
      new SlotAndBlockRootSerializer();
  KvStoreSerializer<CheckpointEpochs> CHECKPOINT_EPOCHS_SERIALIZER =
      new CheckpointEpochsSerializer();
  KvStoreSerializer<Set<Bytes32>> BLOCK_ROOTS_SERIALIZER = new Bytes32SetSerializer();

  static KvStoreSerializer<BeaconState> createStateSerializer(final Spec spec) {
    return new BeaconStateSerializer(spec);
  }

  static KvStoreSerializer<SignedBeaconBlock> createSignedBlockSerializer(final Spec spec) {
    return new SignedBeaconBlockSerializer(spec);
  }

  T deserialize(final byte[] data);

  byte[] serialize(final T value);
}
