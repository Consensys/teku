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
import org.ethereum.beacon.consensus.BlockTransition;
import org.ethereum.beacon.consensus.TransitionType;
import org.ethereum.beacon.core.BeaconBlock;
import org.ethereum.beacon.core.MutableBeaconState;

/**
 * Per-block transition, which happens at every block.
 *
 * <p>Calls {@link BeaconChainSpec#process_block(MutableBeaconState, BeaconBlock)}.
 *
 * @see <a
 *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.8.1/specs/core/0_beacon-chain.md#block-processing">Block
 *     processing</a> in the spec.
 */
public class PerBlockTransition implements BlockTransition<BeaconStateEx> {
  private static final Logger logger = LogManager.getLogger(PerBlockTransition.class);

  private final BeaconChainSpec spec;

  public PerBlockTransition(BeaconChainSpec spec) {
    this.spec = spec;
  }

  @Override
  public BeaconStateEx apply(BeaconStateEx stateEx, BeaconBlock block) {
    logger.trace(
        () ->
            "Applying block transition to state: ("
                + spec.hash_tree_root(stateEx).toStringShort()
                + ") "
                + stateEx.toString(spec.getConstants(), spec::signing_root)
                + ", Block: "
                + block.toString(
                    spec.getConstants(), stateEx.getGenesisTime(), spec::signing_root));

    TransitionType.BLOCK.checkCanBeAppliedAfter(stateEx.getTransition());

    MutableBeaconState state = stateEx.createMutableCopy();

    spec.process_block(state, block);

    BeaconStateEx ret = new BeaconStateExImpl(state.createImmutable(), TransitionType.BLOCK);

    logger.trace(
        () ->
            "Block transition result state: ("
                + spec.hash_tree_root(ret).toStringShort()
                + ") "
                + ret.toString(spec.getConstants(), spec::signing_root));

    return ret;
  }
}
