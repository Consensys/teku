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

import com.google.common.primitives.UnsignedLong;
import net.consensys.cava.bytes.Bytes32;
import org.apache.logging.log4j.Level;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.state.BeaconStateWithCache;
import tech.pegasys.artemis.datastructures.util.BeaconStateUtil;
import tech.pegasys.artemis.statetransition.util.BlockProcessingException;
import tech.pegasys.artemis.statetransition.util.BlockProcessorUtil;
import tech.pegasys.artemis.statetransition.util.EpochProcessingException;
import tech.pegasys.artemis.statetransition.util.EpochProcessorUtil;
import tech.pegasys.artemis.statetransition.util.PreProcessingUtil;
import tech.pegasys.artemis.statetransition.util.SlotProcessingException;
import tech.pegasys.artemis.statetransition.util.SlotProcessorUtil;
import tech.pegasys.artemis.util.alogger.ALogger;

public class StateTransition {

  private static final ALogger LOG = new ALogger(StateTransition.class.getName());

  private boolean printEnabled = false;

  public StateTransition() {}

  public StateTransition(boolean printEnabled) {
    this.printEnabled = printEnabled;
  }

  public void initiate(BeaconStateWithCache state, BeaconBlock block, Bytes32 previous_block_root)
      throws StateTransitionException {
    LOG.log(Level.INFO, "Begin state transition", printEnabled);
    state.incrementSlot();
    // pre-process and cache selected state transition calculations
    preProcessor(state);
    // per-slot processing
    slotProcessor(state, previous_block_root);
    // per-block processing
    if (block != null) {
      blockProcessor(state, block);
    }
    // per-epoch processing
    if (state
        .getSlot()
        .plus(UnsignedLong.ONE)
        .mod(UnsignedLong.valueOf(SLOTS_PER_EPOCH))
        .equals(UnsignedLong.ZERO)) {
      epochProcessor(state, block);
    }
    // reset all cached state variables
    state.invalidateCache();
    LOG.log(Level.INFO, "End state transition", printEnabled);
  }

  protected void preProcessor(BeaconStateWithCache state) {
    // calcualte currentBeaconProposerIndex
    PreProcessingUtil.cacheCurrentBeaconProposerIndex(state);
  }

  protected void slotProcessor(BeaconStateWithCache state, Bytes32 previous_block_root) {
    try {
      LOG.log(Level.INFO, "  Processing new slot: " + state.getSlot(), printEnabled);
      // Slots the proposer has skipped (i.e. layers of RANDAO expected)
      // should be in Validator.randao_skips
      SlotProcessorUtil.updateBlockRoots(state, previous_block_root);
    } catch (SlotProcessingException e) {
      LOG.log(Level.WARN, "  Slot processing error: " + e, printEnabled);
    }
  }

  private void blockProcessor(BeaconStateWithCache state, BeaconBlock block) {
    if (BlockProcessorUtil.verify_slot(state, block)) {
      try {
        LOG.log(
            Level.INFO,
            "  Processing new block with state root: " + block.getState_root(),
            printEnabled);

        // Block Header
        LOG.log(Level.INFO, "  Processing block header.", printEnabled);

        // Only verify the proposer's signature if we are processing blocks (not proposing them)
        if (!block.getState_root().equals(Bytes32.ZERO)) {
          // Verify Proposer Signature
          BlockProcessorUtil.verify_signature(state, block);
        }

        // Verify and Update RANDAO
        BlockProcessorUtil.verify_and_update_randao(state, block);

        // Update Eth1 Data
        BlockProcessorUtil.update_eth1_data(state, block);

        // Block Body - Operations
        // Execute Proposer Slashings
        BlockProcessorUtil.proposer_slashing(state, block);
        // Execute Attester Slashings
        BlockProcessorUtil.attester_slashing(state, block);
        // Process Attestations
        BlockProcessorUtil.processAttestations(state, block);
        // Process Deposits
        BlockProcessorUtil.processDeposits(state, block);
        // Process Exits
        BlockProcessorUtil.processVoluntaryExits(state, block);
        // Process Transfers
        BlockProcessorUtil.processTransfers(state, block);
      } catch (BlockProcessingException e) {
        LOG.log(Level.WARN, "  Block processing error: " + e, printEnabled);
      }
    } else {
      LOG.log(Level.INFO, "  Skipping block processing for this slot.", printEnabled);
    }
  }

  private void epochProcessor(BeaconStateWithCache state, BeaconBlock block) {
    try {
      LOG.log(
          Level.INFO,
          "\n ******** \n  Processing new epoch: "
              + BeaconStateUtil.get_current_epoch(state)
              + " \n *********  \n slot at: "
              + state.getSlot(),
          printEnabled);

      EpochProcessorUtil.updateEth1Data(state);
      LOG.log(Level.DEBUG, "updateEth1Data()", printEnabled);
      EpochProcessorUtil.updateJustification(state, block);
      LOG.log(Level.DEBUG, "updateJustification()", printEnabled);
      EpochProcessorUtil.updateCrosslinks(state);
      LOG.log(Level.DEBUG, "updateCrosslinks()", printEnabled);

      UnsignedLong previous_total_balance = BeaconStateUtil.previous_total_balance(state);
      LOG.log(Level.DEBUG, "justificationAndFinalization()", printEnabled);
      EpochProcessorUtil.justificationAndFinalization(state, previous_total_balance);
      LOG.log(Level.DEBUG, "attestionInclusion()", printEnabled);
      EpochProcessorUtil.attestionInclusion(state, previous_total_balance);
      LOG.log(Level.DEBUG, "crosslinkRewards()", printEnabled);
      EpochProcessorUtil.crosslinkRewards(state, previous_total_balance);

      LOG.log(Level.DEBUG, "process_ejections()", printEnabled);
      EpochProcessorUtil.process_ejections(state);

      LOG.log(Level.DEBUG, "previousStateUpdates()", printEnabled);
      EpochProcessorUtil.previousStateUpdates(state);
      if (EpochProcessorUtil.shouldUpdateValidatorRegistry(state)) {
        LOG.log(Level.DEBUG, "update_validator_registry()", printEnabled);
        EpochProcessorUtil.update_validator_registry(state);
        LOG.log(Level.DEBUG, "currentStateUpdatesAlt1()", printEnabled);
        EpochProcessorUtil.currentStateUpdatesAlt1(state);
      } else {
        LOG.log(Level.DEBUG, "currentStateUpdatesAlt2()", printEnabled);
        EpochProcessorUtil.currentStateUpdatesAlt2(state);
      }
      LOG.log(Level.DEBUG, "process_penalties_and_exits()", printEnabled);
      EpochProcessorUtil.process_penalties_and_exits(state);
      LOG.log(Level.DEBUG, "finalUpdates()", printEnabled);
      EpochProcessorUtil.finalUpdates(state);
    } catch (EpochProcessingException e) {
      LOG.log(Level.WARN, "  Epoch processing error: " + e, printEnabled);
    }
  }
}
