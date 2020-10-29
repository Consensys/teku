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
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class CheckpointState {

  private final Checkpoint checkpoint;
  private final BeaconBlockHeader header;
  private final BeaconState state;

  private CheckpointState(
      final Checkpoint checkpoint, final BeaconBlockHeader header, final BeaconState state) {
    checkNotNull(checkpoint);
    checkNotNull(header);
    checkNotNull(state);
    checkArgument(
        checkpoint.getRoot().equals(header.hashTreeRoot()), "Block must match checkpoint root");
    checkArgument(
        state.getSlot().equals(checkpoint.getEpochStartSlot()),
        "State must be advanced to checkpoint epoch boundary slot");

    this.checkpoint = checkpoint;
    this.header = header;
    this.state = state;
  }

  public static CheckpointState create(
      final Checkpoint checkpoint, final BeaconBlockHeader header, final BeaconState state) {
    return new CheckpointState(checkpoint, header, state);
  }

  public static CheckpointState create(
      final Checkpoint checkpoint, final SignedBeaconBlock block, final BeaconState state) {
    return new CheckpointState(checkpoint, BeaconBlockHeader.fromBlock(block), state);
  }

  public Checkpoint getCheckpoint() {
    return checkpoint;
  }

  public UInt64 getEpoch() {
    return getCheckpoint().getEpoch();
  }

  public Bytes32 getRoot() {
    return header.hashTreeRoot();
  }

  public UInt64 getBlockSlot() {
    return header.getSlot();
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
        && Objects.equals(header, that.header)
        && Objects.equals(state, that.state);
  }

  @Override
  public int hashCode() {
    return Objects.hash(checkpoint, header, state);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("checkpoint", checkpoint)
        .add("block", header)
        .add("state", state)
        .toString();
  }
}
