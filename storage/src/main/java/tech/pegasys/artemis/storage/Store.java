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
import java.util.HashMap;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.Checkpoint;

public class Store {

  private UnsignedLong time;
  private Checkpoint justified_checkpoint;
  private Checkpoint finalized_checkpoint;
  private HashMap<Bytes32, BeaconBlock> blocks;
  private HashMap<Bytes32, BeaconState> block_states;
  private HashMap<Checkpoint, BeaconState> checkpoint_states;
  private HashMap<UnsignedLong, LatestMessage> latest_messages;

  public Store(
      UnsignedLong time,
      Checkpoint justified_checkpoint,
      Checkpoint finalized_checkpoint,
      HashMap<Bytes32, BeaconBlock> blocks,
      HashMap<Bytes32, BeaconState> block_states,
      HashMap<Checkpoint, BeaconState> checkpoint_states) {
    this.time = time;
    this.justified_checkpoint = justified_checkpoint;
    this.finalized_checkpoint = finalized_checkpoint;
    this.blocks = blocks;
    this.block_states = block_states;
    this.checkpoint_states = checkpoint_states;
    this.latest_messages = new HashMap<>();
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

  public HashMap<Bytes32, BeaconBlock> getBlocks() {
    return blocks;
  }

  public void setBlocks(HashMap<Bytes32, BeaconBlock> blocks) {
    this.blocks = blocks;
  }

  public HashMap<Bytes32, BeaconState> getBlock_states() {
    return block_states;
  }

  public void setBlock_states(HashMap<Bytes32, BeaconState> block_states) {
    this.block_states = block_states;
  }

  public HashMap<Checkpoint, BeaconState> getCheckpoint_states() {
    return checkpoint_states;
  }

  public void setCheckpoint_states(HashMap<Checkpoint, BeaconState> checkpoint_states) {
    this.checkpoint_states = checkpoint_states;
  }

  public HashMap<UnsignedLong, LatestMessage> getLatest_messages() {
    return latest_messages;
  }

  public void setLatest_messages(HashMap<UnsignedLong, LatestMessage> latest_messages) {
    this.latest_messages = latest_messages;
  }
}
