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
import static tech.pegasys.artemis.datastructures.Constants.SLOTS_PER_EPOCH;
import static tech.pegasys.artemis.datastructures.Constants.SLOTS_PER_HISTORICAL_ROOT;
import static tech.pegasys.artemis.datastructures.Constants.ZERO_HASH;
import static tech.pegasys.artemis.statetransition.util.BlockProcessorUtil.process_block_header;
import static tech.pegasys.artemis.statetransition.util.BlockProcessorUtil.process_eth1_data;
import static tech.pegasys.artemis.statetransition.util.BlockProcessorUtil.process_operations;
import static tech.pegasys.artemis.statetransition.util.BlockProcessorUtil.process_randao;
import static tech.pegasys.artemis.statetransition.util.EpochProcessorUtil.process_crosslinks;
import static tech.pegasys.artemis.statetransition.util.EpochProcessorUtil.process_final_updates;
import static tech.pegasys.artemis.statetransition.util.EpochProcessorUtil.process_justification_and_finalization;
import static tech.pegasys.artemis.statetransition.util.EpochProcessorUtil.process_registry_updates;
import static tech.pegasys.artemis.statetransition.util.EpochProcessorUtil.process_rewards_and_penalties;
import static tech.pegasys.artemis.statetransition.util.EpochProcessorUtil.process_slashings;

import com.google.common.primitives.UnsignedLong;
import java.util.Objects;
import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.BeaconStateWithCache;
import tech.pegasys.artemis.datastructures.util.BeaconStateUtil;
import tech.pegasys.artemis.statetransition.util.BlockProcessingException;
import tech.pegasys.artemis.statetransition.util.BlockProcessorUtil;
import tech.pegasys.artemis.statetransition.util.EpochProcessingException;
import tech.pegasys.artemis.statetransition.util.EpochProcessorUtil;
import tech.pegasys.artemis.statetransition.util.SlotProcessingException;
import tech.pegasys.artemis.util.alogger.ALogger;

public class StateTransition {

  private static final ALogger STDOUT = new ALogger("stdout");

  private boolean printEnabled = false;

  public StateTransition() {}

  public StateTransition(boolean printEnabled) {
    this.printEnabled = printEnabled;
  }

  /**
   * v0.7.1
   * https://github.com/ethereum/eth2.0-specs/blob/v0.7.1/specs/core/0_beacon-chain.md#beacon-chain-state-transition-function
   * Runs state transition up to and with the given block
   * @param state
   * @param block
   * @param validate_state_root
   * @return
   * @throws StateTransitionException
   */
  public BeaconState initiate(BeaconStateWithCache state, BeaconBlock block, boolean validate_state_root)
      throws StateTransitionException {
    try {
      // Process slots (including those with no blocks) since block
      process_slots(state, block.getSlot());

      // Process_block
      process_block(state, block);

      // Validate state root (`validate_state_root == True` in production)
      if (validate_state_root) {
        checkArgument(block.getState_root().equals(state.hash_tree_root()));
      }

      // Return post-state
      return state;
    } catch (SlotProcessingException
            | BlockProcessingException
            | EpochProcessingException
            | IllegalArgumentException e) {
      STDOUT.log(Level.WARN, "  State Transition error: " + e, printEnabled);
      throw new StateTransitionException(e.toString());
    }
  }

  /**
   * v0.7.1
   * https://github.com/ethereum/eth2.0-specs/blob/v0.7.1/specs/core/0_beacon-chain.md#beacon-chain-state-transition-function
   * Processes block
   * @param state
   * @param block
   * @throws BlockProcessingException
   */
  private void process_block(BeaconStateWithCache state, BeaconBlock block)
          throws BlockProcessingException{
    process_block_header(state, block);
    process_randao(state, block.getBody());
    process_eth1_data(state, block.getBody());
    process_operations(state, block.getBody());
  }

  /**
   *
   * v0.7.1
   * https://github.com/ethereum/eth2.0-specs/blob/v0.7.1/specs/core/0_beacon-chain.md#beacon-chain-state-transition-function
   * Processes epoch
   * @param state
   * @throws EpochProcessingException
   */
  private void process_epoch(BeaconStateWithCache state) throws EpochProcessingException {
    // Note: the lines with @ label here will be inserted here in a future phase
    process_justification_and_finalization(state);
    process_crosslinks(state);
    process_rewards_and_penalties(state);
    process_registry_updates(state);
    // @process_reveal_deadlines
    // @process_challenge_deadlines
    process_slashings(state);
    process_final_updates(state);
    // @after_process_final_updates
  }

  /**
   *
   * v0.7.1
   * https://github.com/ethereum/eth2.0-specs/blob/v0.7.1/specs/core/0_beacon-chain.md#beacon-chain-state-transition-function
   * Processes slot
   * @param state
   */
  public static void process_slot(BeaconState state) {
    // Cache state root
    Bytes32 previous_state_root = state.hash_tree_root();
    int index = state.getSlot().mod(UnsignedLong.valueOf(SLOTS_PER_HISTORICAL_ROOT)).intValue();
    state.getLatest_state_roots().set(index, previous_state_root);

    // Cache latest block header state root
    if (state.getLatest_block_header().getState_root().equals(ZERO_HASH)) {
      state.getLatest_block_header().setState_root(previous_state_root);
    }

    // Cache block root
    Bytes32 previous_block_root = state.getLatest_block_header().signing_root("signature");
    state.getLatest_block_roots().set(index, previous_block_root);
  }

  /**
   * v0.7.1
   * https://github.com/ethereum/eth2.0-specs/blob/v0.7.1/specs/core/0_beacon-chain.md#beacon-chain-state-transition-function
   * Processes slots through state slot through given slot
   * @param state
   * @param slot
   * @throws EpochProcessingException
   * @throws SlotProcessingException
   */
  public void process_slots(BeaconStateWithCache state, UnsignedLong slot) throws SlotProcessingException, EpochProcessingException {
    try {
      checkArgument(state.getSlot().compareTo(slot) <= 0, "process_slots: State slot higher than given slot");
      while (state.getSlot().compareTo(slot) < 0) {
        process_slot(state);
        // Process epoch on the first slot of the next epoch
        if (state.getSlot().plus(UnsignedLong.ONE).mod(UnsignedLong.valueOf(SLOTS_PER_EPOCH)).equals(UnsignedLong.ZERO)) {
          process_epoch(state);
        }
        state.setSlot(state.getSlot().plus(UnsignedLong.ONE));
      }
    } catch (IllegalArgumentException e) {
      STDOUT.log(Level.WARN, e.getMessage());
      throw new SlotProcessingException(e);
    }
  }
}
