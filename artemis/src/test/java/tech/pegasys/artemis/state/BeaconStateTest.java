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

import tech.pegasys.artemis.datastructures.BeaconChainState.ForkData;
import tech.pegasys.artemis.datastructures.BeaconChainState.ShardCommittee;
import tech.pegasys.artemis.datastructures.BeaconChainState.ValidatorRecord;
import tech.pegasys.artemis.ethereum.core.Hash;
import tech.pegasys.artemis.util.bytes.Bytes32;
import tech.pegasys.artemis.util.bytes.BytesValue;
import tech.pegasys.artemis.util.uint.UInt64;

import java.util.ArrayList;
import java.util.Collections;

import org.junit.Test;

public class BeaconStateTest {

  private BeaconState newState(int status) {
    BeaconState state = new BeaconState(UInt64.MIN_VALUE, UInt64.MIN_VALUE, new ForkData(),
        new ArrayList<>(), UInt64.MIN_VALUE, UInt64.MIN_VALUE, hash(Bytes32.TRUE),
        hash(Bytes32.TRUE), hash(Bytes32.TRUE), new ArrayList<>(),
        new ArrayList<>(), new ArrayList<>(),
        UInt64.MIN_VALUE, UInt64.MIN_VALUE, UInt64.MIN_VALUE, UInt64.MIN_VALUE,  new ArrayList<>(),
        new ArrayList<>(),new ArrayList<>(),
        new ArrayList<>(), new ArrayList<>(),
        hash(Bytes32.TRUE), new ArrayList<>());

    ArrayList<ValidatorRecord> new_records = new ArrayList<ValidatorRecord>(Collections.nCopies(24,
        new ValidatorRecord(1, hash(Bytes32.TRUE), hash(Bytes32.TRUE),
            UInt64.valueOf(status), 100.0, UInt64.valueOf(status), UInt64.MIN_VALUE, UInt64.MIN_VALUE)));
    state.validator_registry = new_records;

    ArrayList<Integer> new_committee = new ArrayList<Integer>();
    new_committee.add(0);
    new_committee.add(50);
    new_committee.add(100);
    state.persistent_committees.add(new_committee);

    state.latest_penalized_exit_balances.add(10.0);

    ArrayList<ShardCommittee> new_shard_committees = new ArrayList<ShardCommittee>(Collections.nCopies(2,
        new ShardCommittee(UInt64.MIN_VALUE, new int[]{20}, UInt64.valueOf(1))));
    state.shard_committees_at_slots = new ArrayList<ArrayList<ShardCommittee>>(Collections.nCopies(65,
        new_shard_committees));

    return state;
  }

  @Test
  public void activateValidatorNotPendingActivation() {
    BeaconState state = newState(ACTIVE);
    state.activate_validator(0);
    assertThat(state.validator_registry.get(0).status.getValue()).isEqualTo(ACTIVE);
  }

  @Test
  public void activateValidator() {
    BeaconState state = newState(PENDING_ACTIVATION);
    assertThat(state.validator_registry.get(0).status.getValue()).isEqualTo(PENDING_ACTIVATION);
    state.activate_validator(0);
    assertThat(state.validator_registry.get(0).status.getValue()).isEqualTo(ACTIVE);
    assertThat(state.validator_registry.get(0).latest_status_change_slot).isEqualTo(state.slot);
  }

  @Test
  public void initiateValidatorExitNotActive() {
    BeaconState state = newState(ACTIVATION);
    state.initiate_validator_exit(0);
    assertThat(state.validator_registry.get(0).status.getValue()).isEqualTo(ACTIVATION);
  }

  @Test
  public void initiateValidatorExit() {
    BeaconState state = newState(ACTIVE);
    state.initiate_validator_exit(0);
    assertThat(state.validator_registry.get(0).status.getValue()).isEqualTo(ACTIVE_PENDING_EXIT);
    assertThat(state.validator_registry.get(0).latest_status_change_slot).isEqualTo(state.slot);
  }

  @Test
  public void exitValidatorPrevStatusExitedWithPenaltyNewStateExitedWithoutPenalty() {
    BeaconState state = newState(EXITED_WITH_PENALTY);
    state.exit_validator(0, EXITED_WITHOUT_PENALTY);
    assertThat(state.validator_registry.get(0).status.getValue()).isEqualTo(EXITED_WITH_PENALTY);
  }

  @Test
  public void exitValidatorPrevStatusExitedWithoutPenaltyNewStateExitedWithoutPenalty() {
    BeaconState state = newState(EXITED_WITHOUT_PENALTY);
    state.exit_validator(0, EXITED_WITHOUT_PENALTY);
    assertThat(state.validator_registry.get(0).status.getValue()).isEqualTo(EXITED_WITHOUT_PENALTY);
    assertThat(state.validator_registry.get(0).latest_status_change_slot).isEqualTo(state.slot);
  }

  @Test
  public void exitValidatorPrevStatusExitedWithoutPenaltyNewStateExitedWithPenalty() {
    // balance changes. no committee changes.
    BeaconState state = newState(EXITED_WITHOUT_PENALTY);
    long before_exit_count = state.validator_registry_exit_count.getValue();
    double before_balance = state.validator_registry.get(0).balance;
//    Hash before_tip = state.validator_registry_delta_chain_tip;

    state.exit_validator(0, EXITED_WITH_PENALTY);

    assertThat(before_exit_count).isEqualTo(state.validator_registry_exit_count.getValue());
    assertThat(state.validator_registry.get(0).balance).isLessThan(before_balance);
    // TODO: Uncomment this when tree_root_hash is working.
//    assertThat(before_tip).isNotEqualTo(state.validator_registry_delta_chain_tip);
  }

  @Test
  public void exitValidatorPrevStatusDidNotExitNewStatusExitedWithPenalty() {
    BeaconState state = newState(ACTIVE_PENDING_EXIT);
    long before_exit_count = state.validator_registry_exit_count.getValue();
    double before_balance = state.validator_registry.get(0).balance;
//    Hash before_tip = state.validator_registry_delta_chain_tip;

    state.exit_validator(0, EXITED_WITH_PENALTY);

    assertThat(before_exit_count).isEqualTo(state.validator_registry_exit_count.getValue() - 1);
    assertThat(state.validator_registry.get(0).balance).isLessThan(before_balance);
    // TODO: Uncomment this when tree_root_hash is working.
//    assertThat(before_tip).isNotEqualTo(state.validator_registry_delta_chain_tip);
  }

  @Test
  public void exitValidatorPrevStatusDidNotExitNewStatusExitedWithoutPenalty() {
    // no balance changes. committee changes.
    BeaconState state = newState(ACTIVE_PENDING_EXIT);
    long before_exit_count = state.validator_registry_exit_count.getValue();
    int before_persistent_committees_size = state.persistent_committees.get(0).size();
//    Hash before_tip = state.validator_registry_delta_chain_tip;

    state.exit_validator(0, EXITED_WITHOUT_PENALTY);

    assertThat(before_exit_count).isEqualTo(state.validator_registry_exit_count.getValue() - 1);
    assertThat(state.persistent_committees.get(0).size()).isEqualTo(before_persistent_committees_size - 1);
    // TODO: Uncomment this when tree_root_hash is working.
//    assertThat(before_tip).isNotEqualTo(state.validator_registry_delta_chain_tip);
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
    Object[] actual = shuffle(new Object[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10}, hashSrc());
    Object[] expected = {2, 4, 10, 7, 5, 6, 9, 8, 1, 3};
    assertThat(actual).isEqualTo(expected);
  }

  @Test(expected = IllegalArgumentException.class)
  public void failsWhenInvalidArgumentTestSplit() {
    split(new Object[]{0, 1, 2, 3, 4, 5, 6, 7}, -1);
  }

  @Test
  public void splitReturnsOneSmallerSizedSplit() {
    Object[] actual = split(new Object[]{0, 1, 2, 3, 4, 5, 6, 7}, 3);
    Object[][] expected = {{0, 1}, {2, 3, 4}, {5, 6, 7}};
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void splitReturnsTwoSmallerSizedSplits() {
    Object[] actual = split(new Object[]{0, 1, 2, 3, 4, 5, 6}, 3);
    Object[][] expected = {{0, 1}, {2, 3}, {4, 5, 6}};
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void splitReturnsEquallySizedSplits() {
    Object[] actual = split(new Object[]{0, 1, 2, 3, 4, 5, 6, 7, 8}, 3);
    Object[][] expected = {{0, 1, 2}, {3, 4, 5}, {6, 7, 8}};
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
