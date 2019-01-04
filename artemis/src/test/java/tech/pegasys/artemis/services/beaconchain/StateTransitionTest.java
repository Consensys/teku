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

package tech.pegasys.artemis.services.beaconchain;

import static java.lang.Math.toIntExact;
import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.artemis.Constants.ACTIVE;
import static tech.pegasys.artemis.Constants.ACTIVE_PENDING_EXIT;
import static tech.pegasys.artemis.Constants.EPOCH_LENGTH;
import static tech.pegasys.artemis.Constants.EXITED_WITHOUT_PENALTY;
import static tech.pegasys.artemis.Constants.EXITED_WITH_PENALTY;
import static tech.pegasys.artemis.Constants.LATEST_RANDAO_MIXES_LENGTH;
import static tech.pegasys.artemis.Constants.PENDING_ACTIVATION;
import static tech.pegasys.artemis.ethereum.core.Hash.hash;

import tech.pegasys.artemis.datastructures.beaconchainstate.ShardCommittee;
import tech.pegasys.artemis.datastructures.beaconchainstate.ValidatorRecord;
import tech.pegasys.artemis.ethereum.core.Hash;
import tech.pegasys.artemis.state.BeaconState;
import tech.pegasys.artemis.util.bytes.Bytes32;
import tech.pegasys.artemis.util.uint.UInt64;

import java.util.ArrayList;
import java.util.Collections;

import org.junit.Test;

public class StateTransitionTest {
  private BeaconState newState() {
    // Initialize state
    BeaconState state = new BeaconState();

    // Add validator records
    ArrayList<ValidatorRecord> validators = new ArrayList<ValidatorRecord>();
    validators.add(new ValidatorRecord(0, Hash.ZERO, Hash.ZERO, UInt64.MIN_VALUE,
        UInt64.valueOf(PENDING_ACTIVATION), UInt64.valueOf(state.getSlot()), UInt64.MIN_VALUE, UInt64.MIN_VALUE, UInt64.MIN_VALUE));
    validators.add(new ValidatorRecord(100, Hash.ZERO, Hash.ZERO, UInt64.MIN_VALUE,
        UInt64.valueOf(ACTIVE), UInt64.valueOf(state.getSlot()), UInt64.MIN_VALUE, UInt64.MIN_VALUE, UInt64.MIN_VALUE));
    validators.add(new ValidatorRecord(200, Hash.ZERO, Hash.ZERO, UInt64.MIN_VALUE,
        UInt64.valueOf(ACTIVE_PENDING_EXIT), UInt64.valueOf(state.getSlot()), UInt64.MIN_VALUE, UInt64.MIN_VALUE, UInt64.MIN_VALUE));
    validators.add(new ValidatorRecord(0, Hash.ZERO, Hash.ZERO, UInt64.MIN_VALUE,
        UInt64.valueOf(EXITED_WITHOUT_PENALTY), UInt64.valueOf(state.getSlot()), UInt64.MIN_VALUE, UInt64.MIN_VALUE, UInt64.MIN_VALUE));
    validators.add(new ValidatorRecord(0, Hash.ZERO, Hash.ZERO, UInt64.MIN_VALUE,
        UInt64.valueOf(EXITED_WITH_PENALTY), UInt64.valueOf(state.getSlot()), UInt64.MIN_VALUE, UInt64.MIN_VALUE, UInt64.MIN_VALUE));
    state.setValidator_registry(validators);

    return state;
  }

  @Test
  public void testUpdateProposerRandaoLayer(){
    // initialize state and state transition objects
    BeaconState state = newState();
    state.setSlot(4);

    ArrayList<ShardCommittee> new_shard_committees = new ArrayList<ShardCommittee>(Collections.nCopies(2,
        new ShardCommittee(UInt64.MIN_VALUE, new int[]{2,4,1,0,3}, UInt64.valueOf(1))));
    state.setShard_committees_at_slots(new ArrayList<ArrayList<ShardCommittee>>(Collections.nCopies(2*EPOCH_LENGTH,
        new_shard_committees)));

    StateTransition state_transition = new StateTransition();

    // get proposer's and a random validator's validator record
    // proposer_index is 3 here since we've set slot to 4
    int curr_slot = toIntExact(state.getSlot());
    int proposer_index = state.get_beacon_proposer_index(state, curr_slot);
    assertThat(proposer_index).isEqualTo(3);

    ArrayList<ValidatorRecord> validator_registry = state.getValidator_registry();
    ValidatorRecord proposer_record = validator_registry.get(proposer_index);
    ValidatorRecord random_record = validator_registry.get(1);

    // obtain proposer's and a random validator's randao layer value
    // before and after update method is called
    long proposer_randao_layer_before = proposer_record.getRandao_layers().getValue();
    long random_randao_layer_before = random_record.getRandao_layers().getValue();
    state_transition.updateProposerRandaoLayer(state);
    long proposer_randao_layer_after = proposer_record.getRandao_layers().getValue();
    long random_randao_layer_after = random_record.getRandao_layers().getValue();

    assertThat(proposer_randao_layer_before + 1).isEqualTo(proposer_randao_layer_after);
    assertThat(random_randao_layer_before).isEqualTo(random_randao_layer_after);
  }

  @Test
  public void testUpdateLatestRandaoMixes(){
    BeaconState state = newState();
    state.setLatest_randao_mixes(new ArrayList<Hash>(Collections.nCopies(LATEST_RANDAO_MIXES_LENGTH,
        Hash.EMPTY)));
    state.setSlot(12);
    ArrayList<Hash> latest_randao_mixes = state.getLatest_randao_mixes();
    latest_randao_mixes.set(11, hash(Bytes32.TRUE));
    latest_randao_mixes.set(12, hash(Bytes32.FALSE));
    StateTransition state_transition = new StateTransition();

    Hash prev_slot_randao_mix = latest_randao_mixes.get(11);
    Hash curr_slot_randao_mix = latest_randao_mixes.get(12);
    assertThat(prev_slot_randao_mix).isNotEqualTo(curr_slot_randao_mix);

    state_transition.updateLatestRandaoMixes(state);
    prev_slot_randao_mix = latest_randao_mixes.get(11);
    curr_slot_randao_mix = latest_randao_mixes.get(12);
    assertThat(prev_slot_randao_mix).isEqualTo(curr_slot_randao_mix);
  }
}
