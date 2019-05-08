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

import static tech.pegasys.artemis.datastructures.Constants.SLOTS_PER_EPOCH;
import static tech.pegasys.artemis.datastructures.Constants.SLOTS_PER_HISTORICAL_ROOT;
import static tech.pegasys.artemis.datastructures.Constants.ZERO_HASH;

import com.google.common.primitives.UnsignedLong;
import net.consensys.cava.bytes.Bytes32;
import org.apache.logging.log4j.Level;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.BeaconStateWithCache;
import tech.pegasys.artemis.datastructures.util.BeaconStateUtil;
import tech.pegasys.artemis.statetransition.util.BlockProcessingException;
import tech.pegasys.artemis.statetransition.util.BlockProcessorUtil;
import tech.pegasys.artemis.statetransition.util.EpochProcessingException;
import tech.pegasys.artemis.statetransition.util.EpochProcessorUtil;
import tech.pegasys.artemis.statetransition.util.PreProcessingUtil;
import tech.pegasys.artemis.util.alogger.ALogger;

public class StateTransition {

  private static final ALogger LOG = new ALogger(StateTransition.class.getName());

  private boolean printEnabled = false;

  public StateTransition() {}

  public StateTransition(boolean printEnabled) {
    this.printEnabled = printEnabled;
  }

  public void initiate(BeaconStateWithCache state, BeaconBlock block)
      throws StateTransitionException {
    // Client specific optimization
    preProcessor(state);

    cache_state(state);

    if (state
        .getSlot()
        .plus(UnsignedLong.ONE)
        .mod(UnsignedLong.valueOf(SLOTS_PER_EPOCH))
        .equals(UnsignedLong.ZERO)) {
      epochProcessor(state, block);
    }

    slotProcessor(state);

    if (block != null) {
      blockProcessor(state, block);
    }

    // Client specific optimization
    state.invalidateCache();
  }

  protected void preProcessor(BeaconStateWithCache state) {
    // calculate currentBeaconProposerIndex
    PreProcessingUtil.cacheCurrentBeaconProposerIndex(state);
  }

  /**
   * Caches the given state.
   *
   * @param state
   */
  protected void cache_state(BeaconState state) {
    Bytes32 previous_slot_state_root = state.hash_tree_root();

    // Store the previous slot's post state transition root
    int prev_slot_index =
        state.getSlot().mod(UnsignedLong.valueOf(SLOTS_PER_HISTORICAL_ROOT)).intValue();
    state.getLatest_state_roots().set(prev_slot_index, previous_slot_state_root);

    // Cache state root in stored latest_block_header if empty
    if (state.getLatest_block_header().getState_root() == ZERO_HASH) {
      state.getLatest_block_header().setState_root(previous_slot_state_root);
    }

    // Store latest known block for previous slot
    state
        .getLatest_block_roots()
        .set(prev_slot_index, state.getLatest_block_header().signed_root("signature"));
  }

  protected void slotProcessor(BeaconStateWithCache state) {
    advance_slot(state);
  }

  private void blockProcessor(BeaconStateWithCache state, BeaconBlock block) {
    try {

      BlockProcessorUtil.process_block_header(state, block);
      BlockProcessorUtil.process_randao(state, block);
      BlockProcessorUtil.process_eth1_data(state, block);
      BlockProcessorUtil.process_proposer_slashings(state, block);
      BlockProcessorUtil.process_attester_slashings(state, block);
      BlockProcessorUtil.process_attestations(state, block);
      BlockProcessorUtil.process_deposits(state, block);
      BlockProcessorUtil.process_voluntary_exits(state, block);
      BlockProcessorUtil.process_transfers(state, block);
      BlockProcessorUtil.verify_block_state_root(state, block);

    } catch (BlockProcessingException e) {
      LOG.log(Level.WARN, "  Block processing error: " + e, printEnabled);
    }
  }

  private void epochProcessor(BeaconStateWithCache state, BeaconBlock block) {
    try {
      String ANSI_YELLOW_BOLD = "\033[1;33m";
      String ANSI_RESET = "\033[0m";
      if (printEnabled) System.out.println();
      LOG.log(
          Level.INFO,
          ANSI_YELLOW_BOLD + "********  Processing new epoch: " + " ********* " + ANSI_RESET,
          printEnabled);

      LOG.log(
          Level.INFO,
          "Epoch:                                  "
              + BeaconStateUtil.get_current_epoch(state)
              + " |  "
              + BeaconStateUtil.get_current_epoch(state).longValue() % Constants.GENESIS_EPOCH,
          printEnabled);

      EpochProcessorUtil.update_justification_and_finalization(state);
      EpochProcessorUtil.process_crosslinks(state);
      EpochProcessorUtil.maybe_reset_eth1_period(state);
      EpochProcessorUtil.apply_rewards(state);
      EpochProcessorUtil.process_ejections(state);
      EpochProcessorUtil.update_registry_and_shuffling_data(state);
      EpochProcessorUtil.process_slashings(state);
      EpochProcessorUtil.process_exit_queue(state);
      EpochProcessorUtil.finish_epoch_update(state);

    } catch (EpochProcessingException e) {
      LOG.log(Level.WARN, "  Epoch processing error: " + e, printEnabled);
    }
  }

  /**
   * Runs at every slot > GENESIS_SLOT.
   *
   * @param state
   */
  private void advance_slot(BeaconStateWithCache state) {
    state.incrementSlot();
  }
}
