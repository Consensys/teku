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

package org.ethereum.beacon.consensus.transition;

import org.ethereum.beacon.consensus.BeaconStateEx;
import org.ethereum.beacon.consensus.spec.SpecStateTransition;
import org.ethereum.beacon.core.MutableBeaconState;
import org.ethereum.beacon.core.types.SlotNumber;

/**
 * Runs state transition up to a given slot as if all slots were empty, i.e. without a block.
 *
 * <p>Reflects {@link SpecStateTransition#process_slots(MutableBeaconState, SlotNumber)} function
 * behaviour.
 *
 * @see ExtendedSlotTransition
 */
public class EmptySlotTransition {

  private final ExtendedSlotTransition onSlotTransition;

  public EmptySlotTransition(ExtendedSlotTransition onSlotTransition) {
    this.onSlotTransition = onSlotTransition;
  }

  /**
   * Applies {@link #onSlotTransition} to a source state until given {@code slot} number is reached.
   *
   * @param source source state.
   * @param tillSlot slot number, inclusively.
   * @return modified source state.
   */
  public BeaconStateEx apply(BeaconStateEx source, SlotNumber tillSlot) {
    BeaconStateEx result = source;
    while (result.getSlot().less(tillSlot)) {
      result = onSlotTransition.apply(result);
    }
    return result;
  }
}
