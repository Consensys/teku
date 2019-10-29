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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ethereum.beacon.consensus.BeaconChainSpec;
import org.ethereum.beacon.consensus.BeaconStateEx;
import org.ethereum.beacon.consensus.StateTransition;
import org.ethereum.beacon.consensus.TransitionType;
import org.ethereum.beacon.core.MutableBeaconState;

/**
 * Per-slot transition, which happens at every slot.
 *
 * <p>Calls {@link BeaconChainSpec#process_slot(MutableBeaconState)}.
 */
public class PerSlotTransition implements StateTransition<BeaconStateEx> {
  private static final Logger logger = LogManager.getLogger(PerSlotTransition.class);

  private final BeaconChainSpec spec;

  public PerSlotTransition(BeaconChainSpec spec) {
    this.spec = spec;
  }

  @Override
  public BeaconStateEx apply(BeaconStateEx stateEx) {
    logger.trace(
        () ->
            "Applying slot transition to state: ("
                + spec.hash_tree_root(stateEx).toStringShort()
                + ") "
                + stateEx.toString(spec.getConstants(), spec::signing_root));
    TransitionType.SLOT.checkCanBeAppliedAfter(stateEx.getTransition());

    MutableBeaconState state = stateEx.createMutableCopy();

    spec.process_slot(state);

    BeaconStateEx ret = new BeaconStateExImpl(state.createImmutable(), TransitionType.SLOT);

    logger.trace(
        () ->
            "Slot transition result state: ("
                + spec.hash_tree_root(ret).toStringShort()
                + ") "
                + ret.toString(spec.getConstants(), spec::signing_root));

    return ret;
  }
}
