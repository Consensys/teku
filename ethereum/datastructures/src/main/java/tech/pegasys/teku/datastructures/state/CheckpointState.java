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

package tech.pegasys.teku.datastructures.state;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.MoreObjects;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;

public class CheckpointState {

  private final Checkpoint checkpoint;
  private final SignedBeaconBlock block;
  private final BeaconState state;

  public CheckpointState(
      final Checkpoint checkpoint, final SignedBeaconBlock block, final BeaconState state) {
    checkNotNull(checkpoint);
    checkNotNull(block);
    checkNotNull(state);
    checkArgument(checkpoint.getRoot().equals(block.getRoot()), "Block must match checkpoint root");
    checkArgument(
        state.getSlot().equals(checkpoint.getEpochStartSlot()),
        "State must be advanced to checkpoint epoch boundary slot");
    validateState(checkpoint, state);

    this.checkpoint = checkpoint;
    this.block = block;
    this.state = state;
  }

  private static void validateState(final Checkpoint checkpoint, final BeaconState state) {
    final Bytes32 blockRootFromState = deriveBlockHeaderFromState(state).hash_tree_root();
    checkArgument(
        blockRootFromState.equals(checkpoint.getRoot()), "State must derive from checkpoint block");
  }

  private static BeaconBlockHeader deriveBlockHeaderFromState(final BeaconState state) {
    BeaconBlockHeader latestHeader = state.getLatest_block_header();

    if (latestHeader.getState_root().isZero()) {
      // If the state root is empty, replace it with the current state root
      final Bytes32 stateRoot = state.hash_tree_root();
      latestHeader =
          new BeaconBlockHeader(
              latestHeader.getSlot(),
              latestHeader.getProposer_index(),
              latestHeader.getParent_root(),
              stateRoot,
              latestHeader.getBody_root());
    }

    return latestHeader;
  }

  public Checkpoint getCheckpoint() {
    return checkpoint;
  }

  public SignedBeaconBlock getBlock() {
    return block;
  }

  /** @return The checkpoint state which is advanced to the checkpoint epoch boundary */
  public BeaconState getState() {
    return state;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof CheckpointState)) {
      return false;
    }
    final CheckpointState that = (CheckpointState) o;
    return Objects.equals(checkpoint, that.checkpoint)
        && Objects.equals(block, that.block)
        && Objects.equals(state, that.state);
  }

  @Override
  public int hashCode() {
    return Objects.hash(checkpoint, block, state);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("checkpoint", checkpoint)
        .add("block", block)
        .add("state", state)
        .toString();
  }
}
