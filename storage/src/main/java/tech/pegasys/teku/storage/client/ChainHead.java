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

import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSummary;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

class ChainHead extends StateAndBlockSummary {

  private ChainHead(BeaconBlockSummary block, BeaconState state) {
    super(block, state);
  }

  public static ChainHead create(StateAndBlockSummary blockAndState) {
    return new ChainHead(blockAndState.getBlockSummary(), blockAndState.getState());
  }

  public static ChainHead create(SignedBlockAndState blockAndState) {
    return new ChainHead(blockAndState.getBlockSummary(), blockAndState.getState());
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    return super.equals(o);
  }

  @Override
  public int hashCode() {
    return super.hashCode();
  }
}
