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

package org.ethereum.beacon.consensus.util;

import org.ethereum.beacon.consensus.BeaconStateEx;
import org.ethereum.beacon.consensus.BlockTransition;
import org.ethereum.beacon.consensus.StateTransition;
import org.ethereum.beacon.consensus.transition.BeaconStateExImpl;
import org.ethereum.beacon.core.MutableBeaconState;

public abstract class StateTransitionTestUtil {
  private StateTransitionTestUtil() {}

  public static BlockTransition<BeaconStateEx> createPerBlockTransition() {
    return (source, block) -> {
      MutableBeaconState newState = source.createMutableCopy();
      newState.setSlot(block.getSlot());
      return new BeaconStateExImpl(newState);
    };
  }

  public static StateTransition<BeaconStateEx> createStateWithNoTransition() {
    return (source) -> source;
  }

  public static StateTransition<BeaconStateEx> createNextSlotTransition() {
    return (source) -> {
      MutableBeaconState result = source.createMutableCopy();
      result.setSlot(result.getSlot().increment());
      return new BeaconStateExImpl(result);
    };
  }
}
