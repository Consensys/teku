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

import org.ethereum.beacon.consensus.BeaconChainSpec;
import org.ethereum.beacon.consensus.BeaconStateEx;
import org.ethereum.beacon.consensus.StateTransition;
import org.ethereum.beacon.consensus.TransitionType;
import org.ethereum.beacon.core.MutableBeaconState;
import org.ethereum.beacon.core.types.SlotNumber;

/**
 * An extended state transition that happens at the beginning of each slot.
 *
 * <p>Runs following steps of Beacon chain state transition function:
 *
 * <ol>
 *   <li>The per-slot transitions, which happens at every slot.
 *   <li>The per-epoch transitions, which happens at the start of the first slot of every epoch.
 *   <li>Slot increment.
 * </ol>
 *
 * @see <a
 *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.8.1/specs/core/0_beacon-chain.md#beacon-chain-state-transition-function">Beacon
 *     chain state transition function</a> in the spec.
 * @see PerBlockTransition
 */
public class ExtendedSlotTransition implements StateTransition<BeaconStateEx> {

  private final PerEpochTransition perEpochTransition;
  private final StateTransition<BeaconStateEx> perSlotTransition;
  private final BeaconChainSpec spec;

  public ExtendedSlotTransition(
      PerEpochTransition perEpochTransition,
      StateTransition<BeaconStateEx> perSlotTransition,
      BeaconChainSpec spec) {
    this.perEpochTransition = perEpochTransition;
    this.perSlotTransition = perSlotTransition;
    this.spec = spec;
  }

  public static ExtendedSlotTransition create(BeaconChainSpec spec) {
    return new ExtendedSlotTransition(
        new PerEpochTransition(spec), new PerSlotTransition(spec), spec);
  }

  @Override
  public BeaconStateEx apply(BeaconStateEx source) {
    BeaconStateEx newSlotState = perSlotTransition.apply(source);
    BeaconStateEx newEpochState;

    // Process epoch on the first slot of the next epoch.
    if (newSlotState
        .getSlot()
        .increment()
        .modulo(spec.getConstants().getSlotsPerEpoch())
        .equals(SlotNumber.ZERO)) {
      newEpochState = perEpochTransition.apply(newSlotState);
    } else {
      newEpochState = newSlotState;
    }

    // advance slot
    MutableBeaconState state = newEpochState.createMutableCopy();
    state.setSlot(state.getSlot().increment());

    return new BeaconStateExImpl(state, TransitionType.SLOT);
  }

  public EpochTransitionSummary getEpochTransitionSummary(BeaconStateEx stateEx) {
    // Process epoch on the first slot of the next epoch.
    if (stateEx
        .getSlot()
        .increment()
        .modulo(spec.getConstants().getSlotsPerEpoch())
        .equals(SlotNumber.ZERO)) {
      BeaconStateEx newSlotState = perSlotTransition.apply(stateEx);
      return perEpochTransition.getEpochTransitionSummary(newSlotState);
    } else {
      return new EpochTransitionSummary();
    }
  }
}
