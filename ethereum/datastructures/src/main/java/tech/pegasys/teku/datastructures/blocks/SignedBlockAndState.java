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
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.MoreObjects;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

/** Helper datastructure that holds a signed block with its corresponding state */
public class SignedBlockAndState {
  private final SignedBeaconBlock block;
  private final BeaconState state;

  public SignedBlockAndState(final SignedBeaconBlock block, final BeaconState state) {
    checkNotNull(block);
    checkNotNull(state);
    checkArgument(
        Objects.equals(block.getStateRoot(), state.hash_tree_root()),
        "State must belong to the given block");

    this.block = block;
    this.state = state;
  }

  public BeaconBlockAndState toUnsigned() {
    return new BeaconBlockAndState(block.getMessage(), state);
  }

  public Bytes32 getRoot() {
    return block.getRoot();
  }

  public Bytes32 getParentRoot() {
    return block.getParent_root();
  }

  public Bytes32 getStateRoot() {
    return state.hash_tree_root();
  }

  public UInt64 getSlot() {
    return getBlock().getSlot();
  }

  public SignedBeaconBlock getBlock() {
    return block;
  }

  public BeaconState getState() {
    return state;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof SignedBlockAndState)) {
      return false;
    }
    final SignedBlockAndState that = (SignedBlockAndState) o;
    return Objects.equals(getBlock(), that.getBlock())
        && Objects.equals(getState(), that.getState());
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
