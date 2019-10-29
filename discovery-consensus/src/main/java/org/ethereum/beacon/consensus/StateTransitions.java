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

package org.ethereum.beacon.consensus;

import org.ethereum.beacon.consensus.transition.EmptySlotTransition;
import org.ethereum.beacon.consensus.transition.ExtendedSlotTransition;
import org.ethereum.beacon.consensus.transition.PerBlockTransition;

/** Instantiates high level state transitions. */
public abstract class StateTransitions {
  public StateTransitions() {}

  public static EmptySlotTransition preBlockTransition(BeaconChainSpec spec) {
    ExtendedSlotTransition extendedSlotTransition = ExtendedSlotTransition.create(spec);
    return new EmptySlotTransition(extendedSlotTransition);
  }

  public static PerBlockTransition blockTransition(BeaconChainSpec spec) {
    return new PerBlockTransition(spec);
  }
}
