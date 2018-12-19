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

import tech.pegasys.artemis.Constants;
import tech.pegasys.artemis.datastructures.BeaconChainBlocks.BeaconBlock;
import tech.pegasys.artemis.state.BeaconState;
import tech.pegasys.artemis.util.uint.UInt64;


public class StateTransition{

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

        //TODO: constants either need to be in UINT64 or this needs to be implemented the idiomatic way.
        long epoch_length = Integer.toUnsignedLong(Constants.EPOCH_LENGTH);
        long zero = Integer.toUnsignedLong(0);
        // per-epoch processing
        if( state.getSlot().modulo(epoch_length).equals(UInt64.valueOf(zero)) ){
            epochProcessor(state);
        }

    }

    protected void slotProcessor(BeaconState state){
        // deep copy beacon state
        BeaconState newState = new BeaconState(state);
        state.incrementSlot();
        System.out.println("Processing new slot: " + state.getSlot());
        // Slots the proposer has skipped (i.e. layers of RANDAO expected)
        // should be in ValidatorRecord.randao_skips
        updateProposerRandaoSkips(newState);
        updateRecentBlockHashes(newState);
    }

    protected void blockProcessor(BeaconState state, BeaconBlock block){
        block.setSlot(state.getSlot());
        System.out.println("Processing new block in slot: " + block.getSlot());
        // block header
        verifySignature(state, block);
        verifyAndUpdateRandao(state, block);

        // block body operations
        processAttestations(state, block);
    }

    protected void epochProcessor(BeaconState state){
        System.out.println("Processing new epoch in slot: " + state.getSlot());
        updateJustification(state);
        updateFinalization(state);
        updateCrosslinks(state);
        finalBookKeeping(state);
    }

    // slot processing
    protected void updateProposerRandaoSkips(BeaconState state){

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

    // epoch processing
    protected void updateJustification(BeaconState state){

    }

    protected void updateFinalization(BeaconState state){

    }

    protected void updateCrosslinks(BeaconState state){

    }

    protected void finalBookKeeping(BeaconState state){

    }

}
