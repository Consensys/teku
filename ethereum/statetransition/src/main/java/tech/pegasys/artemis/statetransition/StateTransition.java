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

import static tech.pegasys.artemis.datastructures.Constants.EPOCH_LENGTH;

import com.google.common.primitives.UnsignedLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.util.BeaconStateUtil;
import tech.pegasys.artemis.statetransition.util.BlockProcessingException;
import tech.pegasys.artemis.statetransition.util.BlockProcessorUtil;
import tech.pegasys.artemis.statetransition.util.EpochProcessingException;
import tech.pegasys.artemis.statetransition.util.EpochProcessorUtil;
import tech.pegasys.artemis.statetransition.util.SlotProcessingException;
import tech.pegasys.artemis.statetransition.util.SlotProcessorUtil;

public class StateTransition {

  private static final Logger LOG = LogManager.getLogger(StateTransition.class.getName());

  public StateTransition() {}

  public void initiate(BeaconState state, BeaconBlock block) throws StateTransitionException {
    LOG.info("Begin state transition");
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
      epochProcessor(state, block);
    }
    LOG.info("End state transition");
  }

  protected void slotProcessor(BeaconState state, BeaconBlock block) {
    try {
      state.incrementSlot();
      LOG.info("  Processing new slot: " + state.getSlot());
      // Slots the proposer has skipped (i.e. layers of RANDAO expected)
      // should be in Validator.randao_skips
      SlotProcessorUtil.updateLatestRandaoMixes(state);
      SlotProcessorUtil.updateRecentBlockHashes(state, block);
    } catch (SlotProcessingException e) {
      LOG.warn("  Slot processing error: " + e);
    } catch (Exception e) {
      LOG.warn("  Unexpected slot processing error: " + e);
    }
  }

  protected void blockProcessor(BeaconState state, BeaconBlock block) {
    if (BlockProcessorUtil.verify_slot(state, block)) {
      try {
        LOG.info("  Processing new block with state root: " + block.getState_root());

        // Block Header
        LOG.info("  Processing block header.");
        // Verify Proposer Signature
        BlockProcessorUtil.verify_signature(state, block);
        // Verify and Update RANDAO
        BlockProcessorUtil.verify_and_update_randao(state, block);
        // Update Eth1 Data
        BlockProcessorUtil.update_eth1_data(state, block);

        // Block Body - Operations
        LOG.info("  Processing block body.");
        // Execute Proposer Slashings
        BlockProcessorUtil.proposer_slashing(state, block);
        // Execute Attester Slashings
        BlockProcessorUtil.attester_slashing(state, block);
        // Process Attestations
        BlockProcessorUtil.processAttestations(state, block);
        // Process Deposits
        BlockProcessorUtil.processDeposits(state, block);
        // Process Exits
        BlockProcessorUtil.processExits(state, block);
      } catch (BlockProcessingException e) {
        LOG.warn("  Block processing error: " + e);
      } catch (Exception e) {
        LOG.warn("  Unexpected block processing error: " + e);
      }
    } else {
      LOG.info("  Skipping block processing for this slot.");
    }
  }

  protected void epochProcessor(BeaconState state, BeaconBlock block) {
    try {
      LOG.info("  Processing new epoch: " + BeaconStateUtil.get_current_epoch(state));

      EpochProcessorUtil.updateEth1Data(state);
      EpochProcessorUtil.updateJustification(state, block);
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
    } catch (EpochProcessingException e) {
      LOG.warn("  Epoch processing error: " + e);
    } catch (Exception e) {
      LOG.warn("  Unexpected epoch processing error: " + e);
    }
  }
}
