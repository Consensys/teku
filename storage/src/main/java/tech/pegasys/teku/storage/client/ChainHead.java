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

package tech.pegasys.teku.storage.client;

import java.util.Objects;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

class ChainHead extends SignedBlockAndState {
  private final UInt64 forkChoiceSlot;

  public ChainHead(SignedBeaconBlock block, BeaconState state, UInt64 forkChoiceSlot) {
    super(block, state);
    this.forkChoiceSlot = forkChoiceSlot;
  }

  public static ChainHead create(SignedBlockAndState blockAndState, UInt64 forkChoiceSlot) {
    return new ChainHead(blockAndState.getBlock(), blockAndState.getState(), forkChoiceSlot);
  }

  /** @return The slot at which the chain head was calculated */
  public UInt64 getForkChoiceSlot() {
    return forkChoiceSlot;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof ChainHead)) {
      return false;
    }
    final ChainHead chainHead = (ChainHead) o;
    return Objects.equals(getForkChoiceSlot(), chainHead.getForkChoiceSlot())
        && Objects.equals(getBlock(), chainHead.getBlock())
        && Objects.equals(getState(), chainHead.getState());
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), getForkChoiceSlot());
  }
}
