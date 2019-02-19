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

package tech.pegasys.artemis.statetransition;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.artemis.datastructures.Constants.EPOCH_LENGTH;

import com.google.common.primitives.UnsignedLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.statetransition.util.BeaconStateUtil;
import tech.pegasys.artemis.statetransition.util.BlockProcessorUtil;
import tech.pegasys.artemis.statetransition.util.EpochProcessorUtil;
import tech.pegasys.artemis.statetransition.util.SlotProcessorUtil;
import tech.pegasys.artemis.statetransition.util.TreeHashUtil;

public class StateTransition {

  private static final Logger LOG = LogManager.getLogger(StateTransition.class.getName());

  public StateTransition() {}

  public void initiate(BeaconState state, BeaconBlock block) {

    try {
      // per-slot processing
      slotProcessor(state, block);
      // per-block processing
      if (block != null) {
        blockProcessor(state, block);
      }
      // per-epoch processing
      if (state
          .getSlot()
          .plus(UnsignedLong.ONE)
          .mod(UnsignedLong.valueOf(EPOCH_LENGTH))
          .equals(UnsignedLong.ZERO)) {
        epochProcessor(state);
      }
      // state root verification
      if (block != null) {
        checkArgument(
            block.getState_root().equals(TreeHashUtil.hash_tree_root(state)),
            StateTransitionException.class);
      }
    } catch (Exception e) {
      LOG.warn(e.toString());
    }
  }

  protected void slotProcessor(BeaconState state, BeaconBlock block) throws Exception {
    state.incrementSlot();
    LOG.info("Processing new slot: " + state.getSlot());
    // Slots the proposer has skipped (i.e. layers of RANDAO expected)
    // should be in Validator.randao_skips
    SlotProcessorUtil.updateLatestRandaoMixes(state);
    SlotProcessorUtil.updateRecentBlockHashes(state, block);
  }

  protected void blockProcessor(BeaconState state, BeaconBlock block) throws Exception {
    LOG.info("Processing new block in slot: " + block.getSlot());
    // block header
    BlockProcessorUtil.verify_signature(state, block);
    // verifyAndUpdateRandao(state, block);

    // block body operations
    // processAttestations(state, block);
  }

  protected void epochProcessor(BeaconState state) throws Exception {
    LOG.info("Processing new epoch in slot: " + state.getSlot());

    EpochProcessorUtil.updateEth1Data(state);
    EpochProcessorUtil.updateJustification(state);
    EpochProcessorUtil.updateCrosslinks(state);

    UnsignedLong previous_total_balance = BeaconStateUtil.previous_total_balance(state);
    EpochProcessorUtil.justificationAndFinalization(state, previous_total_balance);
    EpochProcessorUtil.attestionInclusion(state, previous_total_balance);
    EpochProcessorUtil.crosslinkRewards(state, previous_total_balance);

    EpochProcessorUtil.process_ejections(state);

    EpochProcessorUtil.previousStateUpdates(state);
    if (EpochProcessorUtil.shouldUpdateValidatorRegistry(state)) {
      EpochProcessorUtil.update_validator_registry(state);
      EpochProcessorUtil.currentStateUpdatesAlt1(state);
    } else {
      EpochProcessorUtil.currentStateUpdatesAlt2(state);
    }
    EpochProcessorUtil.process_penalties_and_exits(state);
    EpochProcessorUtil.finalUpdates(state);
  }
}
