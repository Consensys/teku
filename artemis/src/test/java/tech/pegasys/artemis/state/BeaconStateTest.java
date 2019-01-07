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

package tech.pegasys.artemis.state;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.artemis.Constants.ACTIVATION;
import static tech.pegasys.artemis.Constants.ACTIVE;
import static tech.pegasys.artemis.Constants.ACTIVE_PENDING_EXIT;
import static tech.pegasys.artemis.Constants.EXITED_WITHOUT_PENALTY;
import static tech.pegasys.artemis.Constants.EXITED_WITH_PENALTY;
import static tech.pegasys.artemis.Constants.PENDING_ACTIVATION;
import static tech.pegasys.artemis.ethereum.core.Hash.hash;
import static tech.pegasys.artemis.state.BeaconState.BeaconStateHelperFunctions.bytes3ToInt;
import static tech.pegasys.artemis.state.BeaconState.BeaconStateHelperFunctions.clamp;
import static tech.pegasys.artemis.state.BeaconState.BeaconStateHelperFunctions.shuffle;
import static tech.pegasys.artemis.state.BeaconState.BeaconStateHelperFunctions.split;

import tech.pegasys.artemis.datastructures.beaconchainstate.ForkData;
import tech.pegasys.artemis.datastructures.beaconchainstate.ShardCommittee;
import tech.pegasys.artemis.datastructures.beaconchainstate.ValidatorRecord;
import tech.pegasys.artemis.datastructures.beaconchainstate.Validators;
import tech.pegasys.artemis.ethereum.core.Hash;
import tech.pegasys.artemis.util.bytes.Bytes32;
import tech.pegasys.artemis.util.bytes.BytesValue;
import tech.pegasys.artemis.util.uint.UInt64;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.google.gson.Gson;
import org.junit.Test;

public class BeaconStateTest {

  private BeaconState newState() {
    // Initialize state
    BeaconState state = new BeaconState(0, 0, new ForkData(UInt64.MIN_VALUE,
        UInt64.MIN_VALUE, UInt64.MIN_VALUE), new Validators(), new ArrayList<Double>(),
       0, 0, hash(Bytes32.TRUE), new ArrayList<Hash>(), new ArrayList<Hash>(),
        new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), 0, 0, 0,
        0,  new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
        new ArrayList<>(), hash(Bytes32.TRUE), new ArrayList<>());

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

    // Add validator balances
    state.setValidator_balances(new ArrayList<Double>(Collections.nCopies(5,100.0)));

    // Create committee
    ArrayList<Integer> new_committee = new ArrayList<Integer>();
    new_committee.add(0);
    new_committee.add(50);
    new_committee.add(100);
    state.getPersistent_committees().add(new_committee);
    state.getPersistent_committees().add(new_committee);

    // Add penalized exit balances
    state.getLatest_penalized_exit_balances().add(10.0);

    // Create shard_committees
    ArrayList<ShardCommittee> new_shard_committees = new ArrayList<ShardCommittee>(Collections.nCopies(2,
        new ShardCommittee(UInt64.MIN_VALUE, new int[]{0}, UInt64.valueOf(1))));
    state.setShard_committees_at_slots(new ArrayList<ArrayList<ShardCommittee>>(Collections.nCopies(65,
        new_shard_committees)));

    return state;
  }

  private BeaconState processDepositSetup() {
    BeaconState state = newState();
    ValidatorRecord validator1 = new ValidatorRecord(0, Hash.ZERO, Hash.ZERO, UInt64.valueOf(0), UInt64.valueOf(0),
        UInt64.valueOf(PENDING_ACTIVATION), UInt64.valueOf(state.getSlot()), UInt64.valueOf(0), UInt64.valueOf(0));
    ValidatorRecord validator2 = new ValidatorRecord(100, Hash.ZERO, Hash.ZERO, UInt64.valueOf(0), UInt64.valueOf(0),
        UInt64.valueOf(PENDING_ACTIVATION), UInt64.valueOf(state.getSlot()), UInt64.valueOf(0), UInt64.valueOf(0));
    ValidatorRecord validator3 = new ValidatorRecord(200, Hash.ZERO, Hash.ZERO, UInt64.valueOf(0), UInt64.valueOf(0),
        UInt64.valueOf(PENDING_ACTIVATION), UInt64.valueOf(state.getSlot()), UInt64.valueOf(0), UInt64.valueOf(0));
    ArrayList<ValidatorRecord> validators = new ArrayList<ValidatorRecord>();
    validators.add(validator1);
    validators.add(validator2);
    validators.add(validator3);
    state.setValidator_registry(validators);
    return state;
  }

  @Test
  public void processDepositValidatorPubkeysDoesNotContainPubkeyAndMinEmptyValidatorIndexIsNegative() {
    BeaconState state = processDepositSetup();
    int pubkey = 20;
    assertThat(state.process_deposit(state, pubkey, 100, Bytes32.TRUE, Hash.ZERO, Hash.ZERO))
        .isEqualTo(3);
  }

  @Test
  public void processDepositValidatorPubkeysDoesNotContainPubkey() {
    BeaconState state = processDepositSetup();
    int pubkey = 20;
    assertThat(state.process_deposit(state, pubkey, 100, Bytes32.TRUE, Hash.ZERO, Hash.ZERO))
        .isEqualTo(3);
  }

  @Test
  public void processDepositValidatorPubkeysContainsPubkey() {
    BeaconState state = newState();
    int pubkey = 200;

    double oldBalance = state.getValidator_balances().get(2);

    assertThat(state.process_deposit(state, pubkey, 100, Bytes32.TRUE, Hash.ZERO, Hash.ZERO))
        .isEqualTo(2);

    assertThat(state.getValidator_balances().get(2)).isEqualTo(oldBalance + 100.0);
  }

  @Test
  public void activateValidatorNotPendingActivation() {
    BeaconState state = newState();
    int validator_index = 0;

    state.activate_validator(validator_index);
    assertThat(state.getValidator_registry().get(validator_index).getStatus().getValue()).isEqualTo(ACTIVE);
  }

  @Test
  public void activateValidator() {
    BeaconState state = newState();
    int validator_index = 0;

    assertThat(state.getValidator_registry().get(validator_index).getStatus().getValue()).isEqualTo(PENDING_ACTIVATION);
    state.activate_validator(validator_index);
    assertThat(state.getValidator_registry().get(validator_index).getStatus().getValue()).isEqualTo(ACTIVE);
    assertThat(state.getValidator_registry().get(validator_index).getLatest_status_change_slot().getValue())
        .isEqualTo(state.getSlot());
  }

  @Test
  public void initiateValidatorExitNotActive() {
    BeaconState state = newState();
    int validator_index = 0;

    state.initiate_validator_exit(validator_index);
    assertThat(state.getValidator_registry().get(validator_index).getStatus().getValue()).isEqualTo(ACTIVATION);
  }

  @Test
  public void initiateValidatorExit() {
    BeaconState state = newState();
    int validator_index = 2;

    state.initiate_validator_exit(validator_index);
    assertThat(state.getValidator_registry().get(validator_index).
        getStatus().getValue()).isEqualTo(ACTIVE_PENDING_EXIT);
    assertThat(state.getValidator_registry().get(validator_index).
        getLatest_status_change_slot().getValue()).isEqualTo(state.getSlot());
  }

  @Test
  public void exitValidatorPrevStatusExitedWithPenaltyNewStatusExitedWithoutPenalty() {
    BeaconState state = newState();
    int validator_index = 4;

    state.exit_validator(state,validator_index, EXITED_WITHOUT_PENALTY);
    assertThat(state.getValidator_registry().get(validator_index).getStatus().getValue()).isEqualTo(EXITED_WITH_PENALTY);
  }

  @Test
  public void exitValidatorPrevStatusExitedWithoutPenaltyNewStatusExitedWithoutPenalty() {
    BeaconState state = newState();
    int validator_index = 3;

    state.exit_validator(state, validator_index, EXITED_WITHOUT_PENALTY);
    assertThat(state.getValidator_registry().get(validator_index).
        getStatus().getValue()).isEqualTo(EXITED_WITHOUT_PENALTY);
    assertThat(state.getValidator_registry().get(validator_index).
        getLatest_status_change_slot().getValue()).isEqualTo(state.getSlot());
  }

  @Test
  public void exitValidatorPrevStatusExitedWithoutPenaltyNewStatusExitedWithPenalty() {
    BeaconState state = newState();
    int validator_index = 3;

    long before_exit_count = state.getValidator_registry_exit_count();
    double before_balance = state.getValidator_balances().get(validator_index);
//    Hash before_tip = state.validator_registry_delta_chain_tip;

    state.exit_validator(state, validator_index, EXITED_WITH_PENALTY);

    assertThat(before_exit_count).isEqualTo(state.getValidator_registry_exit_count());
    assertThat(state.getValidator_balances().get(validator_index)).isLessThan(before_balance);
    // TODO: Uncomment this when tree_root_hash is working.
//    assertThat(before_tip).isNotEqualTo(state.validator_registry_delta_chain_tip);
  }

  @Test
  public void exitValidatorPrevStatusDidNotExitNewStatusExitedWithPenalty() {
    BeaconState state = newState();
    int validator_index = 2;

    long before_exit_count = state.getValidator_registry_exit_count();
    double before_balance = state.getValidator_balances().get(validator_index);
//    Hash before_tip = state.validator_registry_delta_chain_tip;

    state.exit_validator(state, validator_index, EXITED_WITH_PENALTY);

    assertThat(before_exit_count).isEqualTo(state.getValidator_registry_exit_count() - 1);
    assertThat(state.getValidator_balances().get(validator_index)).isLessThan(before_balance);
    // TODO: Uncomment this when tree_root_hash is working.
//    assertThat(before_tip).isNotEqualTo(state.validator_registry_delta_chain_tip);
  }

  @Test
  public void exitValidatorPrevStatusDidNotExitNewStatusExitedWithoutPenalty() {
    BeaconState state = newState();
    int validator_index = 0;

    long before_exit_count = state.getValidator_registry_exit_count();
    int before_persistent_committees_size = state.getPersistent_committees().get(validator_index).size();
//    Hash before_tip = state.validator_registry_delta_chain_tip;

    state.exit_validator(state, validator_index, EXITED_WITHOUT_PENALTY);

    assertThat(before_exit_count).isEqualTo(state.getValidator_registry_exit_count() - 1);
    assertThat(state.getPersistent_committees().get(validator_index).size())
        .isEqualTo(before_persistent_committees_size - 1);
    // TODO: Uncomment this when tree_root_hash is working.
//    assertThat(before_tip).isNotEqualTo(state.validator_registry_delta_chain_tip);
  }

  @Test
  public void deepCopyBeaconState() {
    BeaconState state = newState();
    BeaconState deepCopy = BeaconState.deepCopy(state);

    // Test if deepCopy has the same values as the original state
    Gson gson = new Gson();
    String stateJson = gson.toJson(state);
    String deepCopyJson = gson.toJson(deepCopy);
    assertThat(stateJson).isEqualTo(deepCopyJson);

    // Test persistent committees
    ArrayList<Integer> new_committee = new ArrayList<Integer>();
    new_committee.add(20);
    deepCopy.getPersistent_committees().add(new_committee);
    assertThat(deepCopy.getPersistent_committees()).isNotEqualTo(state.getPersistent_committees());

    // Test slot
    state.incrementSlot();
    assertThat(deepCopy.getSlot()).isNotEqualTo(state.getSlot());

    // Test fork_data
    state.setFork_data(new ForkData(UInt64.valueOf(1),UInt64.valueOf(1), UInt64.valueOf(1)));
    assertThat(deepCopy.getFork_data().getPre_fork_version()).isNotEqualTo(state.getFork_data().getPre_fork_version());

    // Test validator registry
    ArrayList<ValidatorRecord> new_records = new ArrayList<ValidatorRecord>(Collections.nCopies(12,
        new ValidatorRecord(2, hash(Bytes32.FALSE), hash(Bytes32.FALSE), UInt64.valueOf(PENDING_ACTIVATION),
            UInt64.valueOf(PENDING_ACTIVATION), UInt64.MAX_VALUE, UInt64.MAX_VALUE, UInt64.MIN_VALUE,
            UInt64.MIN_VALUE)));
    deepCopy.setValidator_registry(new_records);
    assertThat(deepCopy.getValidator_registry().get(0).getPubkey().getValue())
        .isNotEqualTo(state.getValidator_registry().get(0).getPubkey().getValue());
  }

  private Hash hashSrc() {
    BytesValue bytes = BytesValue.wrap(new byte[]{(byte) 1, (byte) 256, (byte) 65656});
    return hash(bytes);
  }

  @Test(expected = IllegalArgumentException.class)
  public void failsWhenInvalidArgumentsBytes3ToInt() {
    bytes3ToInt(hashSrc(), -1);
  }

  @Test
  public void convertBytes3ToInt() {
    int expected = 817593;
    int actual = bytes3ToInt(hashSrc(), 0);
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void testShuffle() {
    List<Integer> input = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
    ArrayList<Integer> sample = new ArrayList<>(input);

    ArrayList<Integer> actual = shuffle(sample, hashSrc());
    List<Integer> expected_input = Arrays.asList(2, 4, 10, 7, 5, 6, 9, 8, 1, 3);
    ArrayList<Integer> expected = new ArrayList<>(expected_input);
    assertThat(actual).isEqualTo(expected);
  }

  @Test(expected = IllegalArgumentException.class)
  public void failsWhenInvalidArgumentTestSplit() {
    List<Integer> input = Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7);
    ArrayList<Integer> sample = new ArrayList<>(input);

    split(sample, -1);
  }

  @Test
  public void splitReturnsOneSmallerSizedSplit() {
    List<Integer> input = Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7);
    ArrayList<Integer> sample = new ArrayList<>(input);

    ArrayList<ArrayList<Integer>> actual = split(sample, 3);

    ArrayList<ArrayList<Integer>> expected = new ArrayList<>();
    List<Integer> one = Arrays.asList(0, 1);
    expected.add(new ArrayList<>(one));
    List<Integer> two = Arrays.asList(2, 3, 4);
    expected.add(new ArrayList<>(two));
    List<Integer> three = Arrays.asList(5, 6, 7);
    expected.add(new ArrayList<>(three));

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void splitReturnsTwoSmallerSizedSplits() {
    List<Integer> input = Arrays.asList(0, 1, 2, 3, 4, 5, 6);
    ArrayList<Integer> sample = new ArrayList<>(input);

    ArrayList<ArrayList<Integer>> actual = split(sample, 3);

    ArrayList<ArrayList<Integer>> expected = new ArrayList<>();
    List<Integer> one = Arrays.asList(0, 1);
    expected.add(new ArrayList<>(one));
    List<Integer> two = Arrays.asList(2, 3);
    expected.add(new ArrayList<>(two));
    List<Integer> three = Arrays.asList(4, 5, 6);
    expected.add(new ArrayList<>(three));

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void splitReturnsEquallySizedSplits() {
    List<Integer> input = Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8);
    ArrayList<Integer> sample = new ArrayList<>(input);

    ArrayList<ArrayList<Integer>> actual = split(sample, 3);

    ArrayList<ArrayList<Integer>> expected = new ArrayList<>();
    List<Integer> one = Arrays.asList(0, 1, 2);
    expected.add(new ArrayList<>(one));
    List<Integer> two = Arrays.asList(3, 4, 5);
    expected.add(new ArrayList<>(two));
    List<Integer> three = Arrays.asList(6, 7, 8);
    expected.add(new ArrayList<>(three));

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void clampReturnsMinVal() {
    int actual = clamp(3, 5, 0);
    int expected = 3;
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void clampReturnsMaxVal() {
    int actual = clamp(3, 5, 6);
    int expected = 5;
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void clampReturnsX() {
    int actual = clamp(3, 5, 4);
    int expected = 4;
    assertThat(actual).isEqualTo(expected);
  }

}
