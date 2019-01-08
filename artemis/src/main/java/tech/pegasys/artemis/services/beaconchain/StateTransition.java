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
import static tech.pegasys.artemis.Constants.LATEST_BLOCK_ROOTS_LENGTH;
import static tech.pegasys.artemis.Constants.LATEST_RANDAO_MIXES_LENGTH;

import tech.pegasys.artemis.Constants;
import tech.pegasys.artemis.datastructures.beaconchainblocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.beaconchainstate.ValidatorRecord;
import tech.pegasys.artemis.ethereum.core.Hash;
import tech.pegasys.artemis.ethereum.core.TreeHash;
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
        slotProcessor(state, block);

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

    protected void slotProcessor(BeaconState state, BeaconBlock block){
        // deep copy beacon state
        BeaconState newState = BeaconState.deepCopy(state);
        state.incrementSlot();
        logger.info("Processing new slot: " + state.getSlot());

        updateProposerRandaoLayer(newState);
        updateLatestRandaoMixes(newState);
        updateRecentBlockHashes(newState, block);
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
      int currSlot = toIntExact(state.getSlot());
      int proposerIndex = state.get_beacon_proposer_index(state, currSlot);

      ArrayList<ValidatorRecord> validatorRegistry = state.getValidator_registry();
      ValidatorRecord proposerRecord = validatorRegistry.get(proposerIndex);
      proposerRecord.setRandao_layers(proposerRecord.getRandao_layers().increment());
    }

    protected void updateLatestRandaoMixes(BeaconState state){
      int currSlot = toIntExact(state.getSlot());
      ArrayList<Hash> latestRandaoMixes = state.getLatest_randao_mixes();
      Hash prevSlotRandaoMix = latestRandaoMixes.get((currSlot - 1) % LATEST_RANDAO_MIXES_LENGTH);
      latestRandaoMixes.set(currSlot % LATEST_RANDAO_MIXES_LENGTH, prevSlotRandaoMix);
    }

    protected void updateRecentBlockHashes(BeaconState state, BeaconBlock block){
        state.setPrevious_block_root(block.getState_root());
    }

    // block processing
    protected void verifySignature(BeaconState state, BeaconBlock block){

    }

    protected void verifyAndUpdateRandao(BeaconState state, BeaconBlock block){

    }

    protected void processAttestations(BeaconState state, BeaconBlock block){

    }



}
