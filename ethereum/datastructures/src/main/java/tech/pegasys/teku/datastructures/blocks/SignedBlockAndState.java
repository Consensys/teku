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

import com.google.common.base.MoreObjects;
import java.util.Objects;
import tech.pegasys.teku.datastructures.state.BeaconState;

/** Helper datastructure that holds a signed block with its corresponding state */
public class SignedBlockAndState extends StateAndBlockSummary {
  private final SignedBeaconBlock block;

  public SignedBlockAndState(final SignedBeaconBlock block, final BeaconState state) {
    super(block, state);
    this.block = block;
  }

  public BeaconBlockAndState toUnsigned() {
    return new BeaconBlockAndState(block.getMessage(), state);
  }

  public SignedBeaconBlock getBlock() {
    return block;
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
