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
import tech.pegasys.artemis.datastructures.Constants;
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
import tech.pegasys.artemis.util.hashtree.HashTreeUtil;

public class StateTransition {

  private static final ALogger LOG = new ALogger(StateTransition.class.getName());

  private boolean printEnabled = false;

  public StateTransition() {}

  public StateTransition(boolean printEnabled) {
    this.printEnabled = printEnabled;
  }

  public void initiate(BeaconStateWithCache state, BeaconBlock block, Bytes32 previous_block_root)
      throws StateTransitionException {
    state.incrementSlot();
    // pre-process and cache selected state transition calculations
    preProcessor(state);
    // per-slot processing
    slotProcessor(state, previous_block_root);
    LOG.log(
        Level.DEBUG,
        "State root after slotProcessing: " + HashTreeUtil.hash_tree_root(state.toBytes()));
    // per-block processing
    if (block != null) {
      blockProcessor(state, block);
    }
    LOG.log(
        Level.DEBUG,
        "State root after blockProcessing: " + HashTreeUtil.hash_tree_root(state.toBytes()));
    // per-epoch processing
    if (state
        .getSlot()
        .plus(UnsignedLong.ONE)
        .mod(UnsignedLong.valueOf(SLOTS_PER_EPOCH))
        .equals(UnsignedLong.ZERO)) {
      epochProcessor(state, block);
      LOG.log(
          Level.DEBUG,
          "State root after epochProcessing: " + HashTreeUtil.hash_tree_root(state.toBytes()));
    }
    // reset all cached state variables
    state.invalidateCache();
  }

  protected void preProcessor(BeaconStateWithCache state) {
    // calculate currentBeaconProposerIndex
    PreProcessingUtil.cacheCurrentBeaconProposerIndex(state);
  }

  protected void slotProcessor(BeaconStateWithCache state, Bytes32 previous_block_root) {
    try {
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
      EpochProcessorUtil.updateEth1Data(state);
      LOG.log(
          Level.DEBUG,
          "State root after updateEth1Data(): " + HashTreeUtil.hash_tree_root(state.toBytes()),
          printEnabled);
      EpochProcessorUtil.updateJustification(state, block);
      LOG.log(
          Level.DEBUG,
          "State root after updateJustification(): " + HashTreeUtil.hash_tree_root(state.toBytes()),
          printEnabled);
      EpochProcessorUtil.updateCrosslinks(state);
      LOG.log(
          Level.DEBUG,
          "State root after updateCrosslinks(): " + HashTreeUtil.hash_tree_root(state.toBytes()),
          printEnabled);

      UnsignedLong previous_total_balance = BeaconStateUtil.previous_total_balance(state);
      LOG.log(
          Level.DEBUG,
          "State root after justificationAndFinalization(): "
              + HashTreeUtil.hash_tree_root(state.toBytes()),
          printEnabled);
      EpochProcessorUtil.justificationAndFinalization(state, previous_total_balance);
      LOG.log(
          Level.DEBUG,
          "State root after attestionInclusion(): " + HashTreeUtil.hash_tree_root(state.toBytes()),
          printEnabled);
      EpochProcessorUtil.attestionInclusion(state, previous_total_balance);
      LOG.log(
          Level.DEBUG,
          "State root after crosslinkRewards(): " + HashTreeUtil.hash_tree_root(state.toBytes()),
          printEnabled);
      EpochProcessorUtil.crosslinkRewards(state, previous_total_balance);

      LOG.log(
          Level.DEBUG,
          "State root after process_ejections(): " + HashTreeUtil.hash_tree_root(state.toBytes()),
          printEnabled);
      EpochProcessorUtil.process_ejections(state);

      LOG.log(
          Level.DEBUG,
          "State root after previousStateUpdates(): "
              + HashTreeUtil.hash_tree_root(state.toBytes()),
          printEnabled);
      EpochProcessorUtil.previousStateUpdates(state);
      if (EpochProcessorUtil.shouldUpdateValidatorRegistry(state)) {
        LOG.log(
            Level.DEBUG,
            "State root after update_validator_registry(): "
                + HashTreeUtil.hash_tree_root(state.toBytes()),
            printEnabled);
        EpochProcessorUtil.update_validator_registry(state);
        LOG.log(
            Level.DEBUG,
            "State root after currentStateUpdatesAlt1(): "
                + HashTreeUtil.hash_tree_root(state.toBytes()),
            printEnabled);
        EpochProcessorUtil.currentStateUpdatesAlt1(state);
      } else {
        LOG.log(
            Level.DEBUG,
            "State root after currentStateUpdatesAlt2(): "
                + HashTreeUtil.hash_tree_root(state.toBytes()),
            printEnabled);
        EpochProcessorUtil.currentStateUpdatesAlt2(state);
      }
      LOG.log(
          Level.DEBUG,
          "State root after process_penalties_and_exits(): "
              + HashTreeUtil.hash_tree_root(state.toBytes()),
          printEnabled);
      EpochProcessorUtil.process_penalties_and_exits(state);
      LOG.log(
          Level.DEBUG,
          "State root after finalUpdates(): " + HashTreeUtil.hash_tree_root(state.toBytes()),
          printEnabled);
      EpochProcessorUtil.finalUpdates(state);
    } catch (EpochProcessingException e) {
      LOG.log(Level.WARN, "  Epoch processing error: " + e, printEnabled);
    }
  }
}
