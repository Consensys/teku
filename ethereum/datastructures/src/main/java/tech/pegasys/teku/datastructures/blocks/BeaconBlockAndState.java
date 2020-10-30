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

package tech.pegasys.teku.datastructures.blocks;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.MoreObjects;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

/** Helper datastructure that holds an unsigned block with its corresponding state */
public class BeaconBlockAndState {
  private final BeaconBlock block;
  private final BeaconState state;

  public BeaconBlockAndState(final BeaconBlock block, final BeaconState state) {
    checkArgument(
        Objects.equals(block.getStateRoot(), state.hash_tree_root()),
        "State must belong to the given block");
    this.block = block;
    this.state = state;
  }

  public BeaconBlock getBlock() {
    return block;
  }

  public BeaconState getState() {
    return state;
  }

  public Bytes32 getRoot() {
    return block.hash_tree_root();
  }

  public UInt64 getSlot() {
    return block.getSlot();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final BeaconBlockAndState that = (BeaconBlockAndState) o;
    return Objects.equals(block, that.block) && Objects.equals(state, that.state);
  }

  @Override
  public int hashCode() {
    return Objects.hash(block, state);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("block", block).add("state", state).toString();
  }
}
