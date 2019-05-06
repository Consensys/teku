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
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomValidator;

import java.util.ArrayList;
import java.util.Collections;
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

  @Test
  void processPenaltiesAndExitsTest() throws EpochProcessingException {
    BeaconState state = createArbitraryBeaconState(25);
    state.setValidator_registry(new ArrayList<>(Collections.nCopies(26, randomValidator())));
    state.setValidator_balances(
        new ArrayList<>(Collections.nCopies(26, Constants.MIN_DEPOSIT_AMOUNT)));
    long current_epoch = BeaconStateUtil.get_current_epoch(state);
    int validator_index = 0;

    // Test process penalties
    // Slash validator
    Validator validator_to_slash =
        ValidatorsUtil.get_active_validators(state.getValidator_registry(), current_epoch)
            .get(validator_index);

    Long before_balance = state.getValidator_balances().get(validator_index);
    state
        .getLatest_slashed_balances()
        .set(
            (int) current_epoch,
            state.getLatest_slashed_balances().get(validator_index)
                + BeaconStateUtil.get_effective_balance(state, validator_index));

    validator_to_slash.setSlashed(true);

    // Move epoch forward so the validator will trigger the penalty check
    validator_to_slash.setWithdrawal_epoch(4096);
    current_epoch =
        validator_to_slash.getWithdrawal_epoch() - Constants.LATEST_SLASHED_EXIT_LENGTH / 2; // 4096
    state.setSlot(Constants.SLOTS_PER_EPOCH * current_epoch);
    EpochProcessorUtil.process_penalties_and_exits(state);

    // Check that the validator's balance changed by penalty amount
    long expected_validator_balance = 884615385;
    long actual_validator_balance = state.getValidator_balances().get(validator_index);
    assertEquals(expected_validator_balance, actual_validator_balance);
    // todo: test process exit
  }

  @Disabled
  @Test
  void finalUpdatesTests() {
    // todo
  }
}
