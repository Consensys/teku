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
import java.util.HashMap;
import java.util.Set;
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

  public BeaconBlock getBlock(Bytes32 blockRoot) {
    return blocks.get(blockRoot);
  }

  public boolean containsBlock(Bytes32 blockRoot) {
    return blocks.containsKey(blockRoot);
  }

  public Set<Bytes32> getBlockRoots() {
    return Collections.unmodifiableSet(blocks.keySet());
  }

  public void putBlock(Bytes32 blockRoot, BeaconBlock block) {
    blocks.put(blockRoot, block);
  }

  public BeaconState getBlockState(Bytes32 blockRoot) {
    return block_states.get(blockRoot);
  }

  public boolean containsBlockState(Bytes32 blockRoot) {
    return block_states.containsKey(blockRoot);
  }

  public void putBlockState(Bytes32 blockRoot, BeaconState state) {
    block_states.put(blockRoot, state);
  }

  public BeaconState getCheckpointState(Checkpoint checkpoint) {
    return checkpoint_states.get(checkpoint);
  }

  public boolean containsCheckpointState(Checkpoint checkpoint) {
    return checkpoint_states.containsKey(checkpoint);
  }

  public void putCheckpointState(Checkpoint checkpoint, BeaconState state) {
    checkpoint_states.put(checkpoint, state);
  }

  public LatestMessage getLatestMessage(UnsignedLong validatorIndex) {
    return latest_messages.get(validatorIndex);
  }

  public boolean containsLatestMessage(UnsignedLong validatorIndex) {
    return latest_messages.containsKey(validatorIndex);
  }

  public void putLatestMessage(UnsignedLong validatorIndex, LatestMessage latestMessage) {
    latest_messages.put(validatorIndex, latestMessage);
  }
}
