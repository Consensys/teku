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

package tech.pegasys.artemis.statetransition.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomDeposits;
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomEth1Data;

import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.junit.BouncyCastleExtension;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.BeaconStateWithCache;
import tech.pegasys.artemis.datastructures.state.Validator;
import tech.pegasys.artemis.datastructures.util.BeaconStateUtil;
import tech.pegasys.artemis.datastructures.util.ValidatorsUtil;

@ExtendWith(BouncyCastleExtension.class)
class EpochProcessingUtilTest {

  BeaconState createArbitraryBeaconState(int numValidators) {
    ArrayList<Deposit> deposits = randomDeposits(numValidators);
    try {
      // get initial state
      BeaconStateWithCache state = new BeaconStateWithCache();
      BeaconStateUtil.get_genesis_beacon_state(
          state, deposits, Constants.GENESIS_SLOT, randomEth1Data());
      long currentEpoch = BeaconStateUtil.get_current_epoch(state);

      // set validators to active
      for (Validator validator : state.getValidator_registry()) {
        validator.setActivation_epoch(currentEpoch);
      }
      return state;
    } catch (Exception e) {
      return null;
    }
  }

  @Disabled
  @Test
  void updateEth1DataTests() {
    // todo
  }

  @Disabled
  @Test
  void updateJustificationTests() {
    // todo
  }

  @Disabled
  @Test
  void updateCrosslinksTests() {
    // todo
  }

  @Disabled
  @Test
  void justificationAndFinalizationTests() {
    // todo
  }

  @Disabled
  @Test
  void attestionInclusionTests() {
    // todo
  }

  @Disabled
  @Test
  void crosslinkRewardsTests() {
    // todo
  }

  @Test
  @Disabled
  void processEjectionsTest() throws EpochProcessingException {

    BeaconState state = createArbitraryBeaconState(25);
    long currentEpoch = BeaconStateUtil.get_current_epoch(state);

    List<Long> lowBalances = new ArrayList<>();
    lowBalances.add(Constants.EJECTION_BALANCE / 4);
    lowBalances.add(Constants.EJECTION_BALANCE / 8);
    lowBalances.add(0L);
    lowBalances.add(Constants.EJECTION_BALANCE / 2);
    lowBalances.add(Constants.EJECTION_BALANCE);
    // make 4 validators have balance below threshold and 1 right at the threshhold
    // validators to be ejected
    state.getValidator_balances().set(0, lowBalances.get(0));
    state.getValidator_balances().set(5, lowBalances.get(1));
    state.getValidator_balances().set(15, lowBalances.get(2));
    state.getValidator_balances().set(20, lowBalances.get(3));
    // validator stays active
    state.getValidator_balances().set(1, lowBalances.get(4));

    // TODO this value is never used
    long lowBalance = 0;
    for (long i : lowBalances) {
      lowBalance += i;
    }

    // flag the validators with a balance below the threshold
    EpochProcessorUtil.process_ejections(state);
    // increment the epoch to the time where the validator will be considered ejected
    currentEpoch = BeaconStateUtil.get_entry_exit_effect_epoch(currentEpoch);

    List<Validator> after_active_validators =
        ValidatorsUtil.get_active_validators(state.getValidator_registry(), currentEpoch);
    int expected_num_validators = 21;

    assertEquals(expected_num_validators, after_active_validators.size());
  }

  @Disabled
  @Test
  void previousStateUpdatesTests() {
    // todo
  }

  @Disabled
  @Test
  void shouldUpdateValidatorRegistryTests() {
    // todo
  }

  @Disabled
  @Test
  void currentStateUpdatesAlt1Tests() {
    // todo
  }

  @Disabled
  @Test
  void currentStateUpdatesAlt2Tests() {
    // todo
  }

  @Test
  @Disabled
  void updateValidatorRegistryTest() throws EpochProcessingException {
    BeaconState state = createArbitraryBeaconState(25);
    long currentEpoch = BeaconStateUtil.get_current_epoch(state);

    // make 4 validators have balance below threshold and 1 right at the threshhold
    List<Validator> validators =
        ValidatorsUtil.get_active_validators(state.getValidator_registry(), currentEpoch);
    // validators to be ejected
    state.getValidator_balances().set(0, Constants.EJECTION_BALANCE / 4);
    validators.get(0).setInitiatedExit(true);
    state.getValidator_balances().set(5, Constants.EJECTION_BALANCE / 8);
    validators.get(5).setInitiatedExit(true);
    state.getValidator_balances().set(15, 0L);
    validators.get(15).setInitiatedExit(true);
    state.getValidator_balances().set(20, Constants.EJECTION_BALANCE / 2);
    validators.get(20).setInitiatedExit(true);
    // validator stays active
    state.getValidator_balances().set(1, Constants.EJECTION_BALANCE);

    // flag the validators with a balance below the threshold
    EpochProcessorUtil.update_validator_registry(state);
    // increment the epoch to the time where the validator will be considered ejected
    currentEpoch = BeaconStateUtil.get_entry_exit_effect_epoch(currentEpoch);

    List<Validator> after_active_validators =
        ValidatorsUtil.get_active_validators(state.getValidator_registry(), currentEpoch);

    int expected_num_validators = 21;

    assertEquals(expected_num_validators, after_active_validators.size());
  }

  @Test
  @Disabled
  void updateValidatorRegistryTestWithMissingFlag() throws EpochProcessingException {
    BeaconState state = createArbitraryBeaconState(25);
    long currentEpoch = BeaconStateUtil.get_current_epoch(state);

    // make 4 validators have balance below threshold and 1 right at the threshhold
    // validators to be ejected
    long val_balance = Constants.EJECTION_BALANCE - 6;
    state.getValidator_balances().set(0, val_balance);

    // flag the validators with a balance below the threshold
    EpochProcessorUtil.update_validator_registry(state);
    // increment the epoch to the time where the validator will be considered ejected
    currentEpoch = BeaconStateUtil.get_entry_exit_effect_epoch(currentEpoch);

    List<Validator> after_active_validators =
        ValidatorsUtil.get_active_validators(state.getValidator_registry(), currentEpoch);

    int expected_num_validators = 25;
    assertEquals(expected_num_validators, after_active_validators.size());
  }

  @Disabled
  @Test
  void processPenaltiesAndExitsTest() throws EpochProcessingException {
    BeaconState state = createArbitraryBeaconState(25);
    // TODO: Figure out how to test PenaltiesAndExits
    long currentEpoch = BeaconStateUtil.get_current_epoch(state);

    List<Integer> before_active_validators =
        ValidatorsUtil.get_active_validator_indices(state.getValidator_registry(), currentEpoch);
    long before_total_balance = BeaconStateUtil.get_total_balance(state, before_active_validators);

    List<Validator> validators =
        ValidatorsUtil.get_active_validators(state.getValidator_registry(), currentEpoch);
    // validators to withdrawal
    state.getValidator_balances().set(0, Constants.MAX_DEPOSIT_AMOUNT);
    validators.get(0).setSlashed(true);

    // flag the validators with a balance below the threshold
    EpochProcessorUtil.process_penalties_and_exits(state);
    // increment the epoch to the time where the validator will be considered ejected
    currentEpoch = BeaconStateUtil.get_entry_exit_effect_epoch(currentEpoch);

    List<Integer> after_active_validators =
        ValidatorsUtil.get_active_validator_indices(state.getValidator_registry(), currentEpoch);
    long after_total_balance = BeaconStateUtil.get_total_balance(state, after_active_validators);

    int expected_num_validators = 24;
    long deposit_amount = Constants.MAX_DEPOSIT_AMOUNT;
    long expected_total_balance = expected_num_validators * deposit_amount;

    assertEquals(expected_num_validators, after_active_validators.size());
    assertEquals(expected_total_balance, after_total_balance);
  }

  @Disabled
  @Test
  void finalUpdatesTests() {
    // todo
  }
}
