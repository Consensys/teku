/*
 * Copyright 2018 ConsenSys AG.
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

package tech.pegasys.artemis.services.beaconchain;

import static java.lang.Math.toIntExact;
import static tech.pegasys.artemis.Constants.LATEST_RANDAO_MIXES_LENGTH;

import tech.pegasys.artemis.Constants;
import tech.pegasys.artemis.datastructures.beaconchainblocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.beaconchainstate.ValidatorRecord;
import tech.pegasys.artemis.ethereum.core.Hash;
import tech.pegasys.artemis.state.BeaconState;
import tech.pegasys.artemis.state.util.EpochProcessorUtil;

import java.util.ArrayList;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class StateTransition{

    private static final Logger logger = LogManager.getLogger();

    public StateTransition(){

    }

    public void initiate(BeaconState state, BeaconBlock block){


        // per-slot processing
        slotProcessor(state);

        // per-block processing
         //TODO: need to check if a new block is produced.
         //For now, we make a new block each slot
        //if( block != null ){
        blockProcessor(state, block);
        //}

        // per-epoch processing
        if( state.getSlot() % Constants.EPOCH_LENGTH == 0){
            epochProcessor(state);
        }

    }

    protected void slotProcessor(BeaconState state){
        // deep copy beacon state
        BeaconState newState = BeaconState.deepCopy(state);
        state.incrementSlot();
        logger.info("Processing new slot: " + state.getSlot());

        updateProposerRandaoLayer(newState);
        updateLatestRandaoMixes(newState);
        updateRecentBlockHashes(newState);
    }

    protected void blockProcessor(BeaconState state, BeaconBlock block){
        block.setSlot(state.getSlot());
        logger.info("Processing new block in slot: " + block.getSlot());
        // block header
        verifySignature(state, block);
        verifyAndUpdateRandao(state, block);

        // block body operations
        processAttestations(state, block);
    }

    protected void epochProcessor(BeaconState state){
        logger.info("Processing new epoch in slot: " + state.getSlot());
        EpochProcessorUtil.updateJustification(state);
        EpochProcessorUtil.updateFinalization(state);
        EpochProcessorUtil.updateCrosslinks(state);
        EpochProcessorUtil.finalBookKeeping(state);
    }

    // slot processing
    protected void updateProposerRandaoLayer(BeaconState state){
      int curr_slot = toIntExact(state.getSlot());
      int proposer_index = state.get_beacon_proposer_index(state, curr_slot);

      ArrayList<ValidatorRecord> validator_registry = state.getValidator_registry();
      ValidatorRecord proposer_record = validator_registry.get(proposer_index);
      proposer_record.setRandao_layers(proposer_record.getRandao_layers().increment());
    }

    protected void updateLatestRandaoMixes(BeaconState state){
      int curr_slot = toIntExact(state.getSlot());
      ArrayList<Hash> latest_randao_mixes = state.getLatest_randao_mixes();
      Hash prev_slot_randao_mix = latest_randao_mixes.get((curr_slot - 1) % LATEST_RANDAO_MIXES_LENGTH);
      latest_randao_mixes.set(curr_slot % LATEST_RANDAO_MIXES_LENGTH, prev_slot_randao_mix);
    }

    protected void updateRecentBlockHashes(BeaconState state){

    }

    // block processing
    protected void verifySignature(BeaconState state, BeaconBlock block){

    }

    protected void verifyAndUpdateRandao(BeaconState state, BeaconBlock block){

    }

    protected void processAttestations(BeaconState state, BeaconBlock block){

    }



}
