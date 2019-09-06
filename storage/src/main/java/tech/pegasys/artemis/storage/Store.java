/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.artemis.storage;

import com.google.common.primitives.UnsignedLong;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.BeaconStateWithCache;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.datastructures.util.SimpleOffsetSerializer;

public class Store {

  private UnsignedLong time;
  private Checkpoint justified_checkpoint;
  private Checkpoint finalized_checkpoint;
  private ConcurrentHashMap<Bytes32, BeaconBlock> blocks;
  private ConcurrentHashMap<Bytes32, BeaconState> block_states;
  private ConcurrentHashMap<Checkpoint, BeaconState> checkpoint_states;
  private ConcurrentHashMap<UnsignedLong, LatestMessage> latest_messages;

  public Store(
      UnsignedLong time,
      Checkpoint justified_checkpoint,
      Checkpoint finalized_checkpoint,
      ConcurrentHashMap<Bytes32, BeaconBlock> blocks,
      ConcurrentHashMap<Bytes32, BeaconState> block_states,
      ConcurrentHashMap<Checkpoint, BeaconState> checkpoint_states) {
    this.time = time;
    this.justified_checkpoint = justified_checkpoint;
    this.finalized_checkpoint = finalized_checkpoint;
    this.blocks = blocks;
    this.block_states = block_states;
    this.checkpoint_states = checkpoint_states;
    this.latest_messages = new ConcurrentHashMap<>();
  }

  public UnsignedLong getTime() {
    return time;
  }

  public void setTime(UnsignedLong time) {
    this.time = time;
  }

  public Checkpoint getJustified_checkpoint() {
    return justified_checkpoint;
  }

  public void setJustified_checkpoint(Checkpoint justified_checkpoint) {
    this.justified_checkpoint = justified_checkpoint;
  }

  public Checkpoint getFinalized_checkpoint() {
    return finalized_checkpoint;
  }

  public void setFinalized_checkpoint(Checkpoint finalized_checkpoint) {
    this.finalized_checkpoint = finalized_checkpoint;
  }

  private ConcurrentHashMap<Bytes32, BeaconBlock> getBlocks() {
    return blocks;
  }

  public BeaconBlock getBlock(Bytes32 blockRoot) {
    return blocks.get(blockRoot);
  }

  public boolean containsBlock(Bytes32 blockRoot) {
    return blocks.containsKey(blockRoot);
  }

  public void putBlock(Bytes32 blockRoot, BeaconBlock block) {
    blocks.put(blockRoot, block);
  }

  public Set<Bytes32> getBlockRoots() {
    return Collections.unmodifiableSet(blocks.keySet());
  }

  private void setBlocks(ConcurrentHashMap<Bytes32, BeaconBlock> blocks) {
    this.blocks = blocks;
  }

  private ConcurrentHashMap<Bytes32, BeaconState> getBlock_states() {
    return block_states;
  }

  private void setBlock_states(ConcurrentHashMap<Bytes32, BeaconState> block_states) {
    this.block_states = block_states;
  }

  public BeaconState getBlockState(Bytes32 blockRoot) {
    return block_states.get(blockRoot);
  }

  public void putBlockState(Bytes32 blockRoot, BeaconState state) {
    block_states.put(blockRoot, state);
  }

  public boolean containsBlockState(Bytes32 blockRoot) {
    return block_states.containsKey(blockRoot);
  }

  public ConcurrentHashMap<Checkpoint, BeaconState> getCheckpoint_states() {
    return checkpoint_states;
  }

  public void setCheckpoint_states(ConcurrentHashMap<Checkpoint, BeaconState> checkpoint_states) {
    this.checkpoint_states = checkpoint_states;
  }

  public ConcurrentHashMap<UnsignedLong, LatestMessage> getLatest_messages() {
    return latest_messages;
  }

  public void setLatest_messages(ConcurrentHashMap<UnsignedLong, LatestMessage> latest_messages) {
    this.latest_messages = latest_messages;
  }

  public Bytes toBytes() {
    return SSZ.encode(writer -> {
      writer.writeUInt64(time.longValue());
      writer.writeBytes(justified_checkpoint.toBytes());
      writer.writeBytes(finalized_checkpoint.toBytes());
      writer.writeInt32(blocks.size());
      blocks.forEach((key, value) -> {
        writer.writeBytes(key);
        writer.writeBytes(SimpleOffsetSerializer.serialize(value));
      });
      writer.writeInt32(block_states.size());
      block_states.forEach((key, value) -> {
        writer.writeBytes(key);
        writer.writeBytes(SimpleOffsetSerializer.serialize(value));
      });
      writer.writeInt32(checkpoint_states.size());
      checkpoint_states.forEach((key, value) -> {
        writer.writeBytes(key.toBytes());
        writer.writeBytes(SimpleOffsetSerializer.serialize(value));
      });
      writer.writeInt32(latest_messages.size());
      latest_messages.forEach((key, value) -> {
        writer.writeBytes(value.getRoot());
        writer.writeUInt64(key.longValue());
        writer.writeUInt64(value.getEpoch().longValue());
      });
    });
  }

  public static Store fromBytes(Bytes bytes) {
    return SSZ.decode(bytes, reader -> {
      final UnsignedLong time = UnsignedLong.fromLongBits(reader.readUInt64());
      final Checkpoint justified_checkpoint = Checkpoint.fromBytes(reader.readBytes());
      final Checkpoint finalized_checkpoint = Checkpoint.fromBytes(reader.readBytes());
      final ConcurrentHashMap<Bytes32, BeaconBlock> blocks = new ConcurrentHashMap<>();
      int size = reader.readInt32();
      for (int i = 0; i < size; i++) {
        final Bytes32 key = Bytes32.wrap(reader.readBytes());
        final BeaconBlock value = SimpleOffsetSerializer.deserialize(reader.readBytes(), BeaconBlock.class);
        blocks.put(key, value);
      }

      final ConcurrentHashMap<Bytes32, BeaconState> block_states = new ConcurrentHashMap<>();
      size = reader.readInt32();
      for (int i = 0; i < size; i++) {
        final Bytes32 key = Bytes32.wrap(reader.readBytes());
        final BeaconState value = wrap(SimpleOffsetSerializer.deserialize(reader.readBytes(), BeaconState.class));
        block_states.put(key, value);
      }

      final ConcurrentHashMap<Checkpoint, BeaconState> checkpoint_states = new ConcurrentHashMap<>();
      size = reader.readInt32();
      for (int i = 0; i < size; i++) {
        final Checkpoint key = Checkpoint.fromBytes(reader.readBytes());
        final BeaconState value = wrap(SimpleOffsetSerializer.deserialize(reader.readBytes(), BeaconState.class));
        checkpoint_states.put(key, value);
      }

      final ConcurrentHashMap<UnsignedLong, LatestMessage> latest_messages = new ConcurrentHashMap<>();
      size = reader.readInt32();
      for (int i = 0; i < size; i++) {
        final Bytes32 root = Bytes32.wrap(reader.readBytes());
        final UnsignedLong key = UnsignedLong.fromLongBits(reader.readUInt64());
        final UnsignedLong epoch = UnsignedLong.fromLongBits(reader.readUInt64());
        final LatestMessage value = new LatestMessage(epoch, root);
        latest_messages.put(key, value);
      }
      final Store store = new Store(
          time,
          justified_checkpoint,
          finalized_checkpoint,
          blocks,
          block_states,
          checkpoint_states);
      store.setLatest_messages(latest_messages);
      return store;
    });
  }

  private static BeaconStateWithCache wrap(BeaconState initialState) {
    return new BeaconStateWithCache(
        initialState.getGenesis_time(),
        initialState.getSlot(),
        initialState.getFork(),
        initialState.getLatest_block_header(),
        initialState.getBlock_roots(),
        initialState.getState_roots(),
        initialState.getHistorical_roots(),
        initialState.getEth1_data(),
        initialState.getEth1_data_votes(),
        initialState.getEth1_deposit_index(),
        initialState.getValidators(),
        initialState.getBalances(),
        initialState.getStart_shard(),
        initialState.getRandao_mixes(),
        initialState.getActive_index_roots(),
        initialState.getCompact_committees_roots(),
        initialState.getSlashings(),
        initialState.getPrevious_epoch_attestations(),
        initialState.getCurrent_epoch_attestations(),
        initialState.getPrevious_crosslinks(),
        initialState.getCurrent_crosslinks(),
        initialState.getJustification_bits(),
        initialState.getPrevious_justified_checkpoint(),
        initialState.getCurrent_justified_checkpoint(),
        initialState.getFinalized_checkpoint());
  }
}
