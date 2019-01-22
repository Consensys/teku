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

package tech.pegasys.artemis.state;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.artemis.Constants;
import tech.pegasys.artemis.datastructures.beaconchainblocks.BeaconBlock;
import tech.pegasys.artemis.state.util.EpochProcessorUtil;

public class StateTransition {

  private static final Logger logger = LogManager.getLogger();

  public StateTransition() {}

  public void initiate(BeaconState state, BeaconBlock block) {

    // per-slot processing
    slotProcessor(state);

    // per-block processing
    // TODO: need to check if a new block is produced.
    // For now, we make a new block each slot
    if (block != null) {
      blockProcessor(state, block);
    }

    // per-epoch processing
    if (state.getSlot() % Constants.EPOCH_LENGTH == 0) {
      epochProcessor(state);
    }
  }

  protected void slotProcessor(BeaconState state) {
    // deep copy beacon state
    BeaconState newState = BeaconState.deepCopy(state);
    state.incrementSlot();
    logger.info("Processing new slot: " + state.getSlot());
    // Slots the proposer has skipped (i.e. layers of RANDAO expected)
    // should be in ValidatorRecord.randao_skips
    updateProposerRandaoSkips(newState);
    updateRecentBlockHashes(newState);
  }

  protected void blockProcessor(BeaconState state, BeaconBlock block) {
    block.setSlot(state.getSlot());
    logger.info("Processing new block in slot: " + block.getSlot());
    // block header
    verifySignature(state, block);
    verifyAndUpdateRandao(state, block);

    // block body operations
    processAttestations(state, block);
  }

  protected void epochProcessor(BeaconState state) {
    logger.info("Processing new epoch in slot: " + state.getSlot());
    EpochProcessorUtil.updateJustification(state);
    EpochProcessorUtil.updateFinalization(state);
    EpochProcessorUtil.updateCrosslinks(state);
    EpochProcessorUtil.finalBookKeeping(state);
  }

  // slot processing
  protected void updateProposerRandaoSkips(BeaconState state) {}

  protected void updateRecentBlockHashes(BeaconState state) {}

  // block processing
  protected void verifySignature(BeaconState state, BeaconBlock block) {}

  protected void verifyAndUpdateRandao(BeaconState state, BeaconBlock block) {}

  protected void processAttestations(BeaconState state, BeaconBlock block) {}
}
