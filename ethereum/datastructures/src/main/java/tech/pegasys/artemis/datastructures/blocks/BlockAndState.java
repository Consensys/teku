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

package tech.pegasys.artemis.datastructures.blocks;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Objects;
import tech.pegasys.artemis.datastructures.state.BeaconState;

/** Helper datastructure that holds an unsigned block with its corresponding state */
public class BlockAndState {
  private final BeaconBlock block;
  private final BeaconState state;

  public BlockAndState(final BeaconBlock block, final BeaconState state) {
    checkArgument(
        Objects.equals(block.getState_root(), state.hash_tree_root()),
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
}
