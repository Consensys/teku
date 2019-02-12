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

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.junit.BouncyCastleExtension;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.state.Validator;
import tech.pegasys.artemis.datastructures.state.Validators;
import tech.pegasys.artemis.statetransition.BeaconState;

@ExtendWith(BouncyCastleExtension.class)
class EpochProcessingUtilTest {

  BeaconState createArbitraryBeaconState(int numValidators) {
    ArrayList<Deposit> deposits = randomDeposits(numValidators);
    // get initial state
    BeaconState state =
        BeaconStateUtil.get_initial_beacon_state(
            deposits,
            UnsignedLong.valueOf(Constants.GENESIS_SLOT),
            new Eth1Data(Bytes32.ZERO, Bytes32.ZERO));

    UnsignedLong currentEpoch = BeaconStateUtil.get_current_epoch(state);

    // set validators to active
    for (Validator validator : state.getValidator_registry()) {
      validator.setActivation_epoch(currentEpoch);
    }
    return state;
  }

  @Test
  void processEjectionsTest() {

    BeaconState state = createArbitraryBeaconState(25);
    UnsignedLong currentEpoch = BeaconStateUtil.get_current_epoch(state);

    Validators before_active_validators =
        ValidatorsUtil.get_active_validators(state.getValidator_registry(), currentEpoch);
    UnsignedLong before_total_balance =
        EpochProcessorUtil.get_total_effective_balance(state, before_active_validators);

    // make 4 validators have balance below threshold and 1 right at the threshhold
    Validators validators =
        ValidatorsUtil.get_active_validators(state.getValidator_registry(), currentEpoch);
    // validators to be ejected
    validators.get(0).setBalance(UnsignedLong.valueOf(Constants.EJECTION_BALANCE / 4));
    validators.get(5).setBalance(UnsignedLong.valueOf(Constants.EJECTION_BALANCE / 8));
    validators.get(15).setBalance(UnsignedLong.valueOf(0L));
    validators.get(20).setBalance(UnsignedLong.valueOf(Constants.EJECTION_BALANCE / 2));
    // validator stays active
    validators.get(1).setBalance(UnsignedLong.valueOf(Constants.EJECTION_BALANCE));

    // flag the validators with a balance below the threshold
    EpochProcessorUtil.process_ejections(state);
    // increment the epoch to the time where the validator will be considered ejected
    currentEpoch = BeaconStateUtil.get_entry_exit_effect_epoch(currentEpoch);

    Validators after_active_validators =
        ValidatorsUtil.get_active_validators(state.getValidator_registry(), currentEpoch);
    UnsignedLong after_total_balance =
        EpochProcessorUtil.get_total_effective_balance(state, after_active_validators);

    int expected_num_validators = 21;
    UnsignedLong deposit_amount = UnsignedLong.valueOf(Constants.MAX_DEPOSIT_AMOUNT);
    UnsignedLong expected_total_balance =
        UnsignedLong.valueOf(expected_num_validators)
            .times(deposit_amount)
            .minus(UnsignedLong.valueOf(Constants.EJECTION_BALANCE));

    assertEquals(expected_num_validators, after_active_validators.size());
    assertEquals(expected_total_balance, after_total_balance);
  }

  @Test
  void updateValidatorRegistryTest() {
    BeaconState state = createArbitraryBeaconState(25);
    UnsignedLong currentEpoch = BeaconStateUtil.get_current_epoch(state);

    Validators before_active_validators =
        ValidatorsUtil.get_active_validators(state.getValidator_registry(), currentEpoch);
    UnsignedLong before_total_balance =
        EpochProcessorUtil.get_total_effective_balance(state, before_active_validators);

    // make 4 validators have balance below threshold and 1 right at the threshhold
    Validators validators =
        ValidatorsUtil.get_active_validators(state.getValidator_registry(), currentEpoch);
    // validators to be ejected
    validators.get(0).setBalance(UnsignedLong.valueOf(Constants.EJECTION_BALANCE / 4));
    validators.get(0).setStatus_flags(UnsignedLong.valueOf(Constants.INITIATED_EXIT));
    validators.get(5).setBalance(UnsignedLong.valueOf(Constants.EJECTION_BALANCE / 8));
    validators.get(5).setStatus_flags(UnsignedLong.valueOf(Constants.INITIATED_EXIT));
    validators.get(15).setBalance(UnsignedLong.valueOf(0L));
    validators.get(15).setStatus_flags(UnsignedLong.valueOf(Constants.INITIATED_EXIT));
    validators.get(20).setBalance(UnsignedLong.valueOf(Constants.EJECTION_BALANCE / 2));
    validators.get(20).setStatus_flags(UnsignedLong.valueOf(Constants.INITIATED_EXIT));
    // validator stays active
    validators.get(1).setBalance(UnsignedLong.valueOf(Constants.EJECTION_BALANCE));

    // flag the validators with a balance below the threshold
    EpochProcessorUtil.update_validator_registry(state);
    // increment the epoch to the time where the validator will be considered ejected
    currentEpoch = BeaconStateUtil.get_entry_exit_effect_epoch(currentEpoch);

    Validators after_active_validators =
        ValidatorsUtil.get_active_validators(state.getValidator_registry(), currentEpoch);
    UnsignedLong after_total_balance =
        EpochProcessorUtil.get_total_effective_balance(state, after_active_validators);

    int expected_num_validators = 21;
    UnsignedLong deposit_amount = UnsignedLong.valueOf(Constants.MAX_DEPOSIT_AMOUNT);
    UnsignedLong expected_total_balance =
        UnsignedLong.valueOf(expected_num_validators)
            .times(deposit_amount)
            .minus(UnsignedLong.valueOf(Constants.EJECTION_BALANCE));

    assertEquals(expected_num_validators, after_active_validators.size());
    assertEquals(expected_total_balance, after_total_balance);
  }

  @Test
  void updateValidatorRegistryTest_missingFlag() {
    BeaconState state = createArbitraryBeaconState(25);
    UnsignedLong currentEpoch = BeaconStateUtil.get_current_epoch(state);

    Validators before_active_validators =
        ValidatorsUtil.get_active_validators(state.getValidator_registry(), currentEpoch);
    UnsignedLong before_total_balance =
        EpochProcessorUtil.get_total_effective_balance(state, before_active_validators);

    // make 4 validators have balance below threshold and 1 right at the threshhold
    Validators validators =
        ValidatorsUtil.get_active_validators(state.getValidator_registry(), currentEpoch);
    // validators to be ejected
    long val_balance = Constants.EJECTION_BALANCE - 6;
    validators.get(0).setBalance(UnsignedLong.valueOf(val_balance));

    // flag the validators with a balance below the threshold
    EpochProcessorUtil.update_validator_registry(state);
    // increment the epoch to the time where the validator will be considered ejected
    currentEpoch = BeaconStateUtil.get_entry_exit_effect_epoch(currentEpoch);

    Validators after_active_validators =
        ValidatorsUtil.get_active_validators(state.getValidator_registry(), currentEpoch);
    UnsignedLong after_total_balance =
        EpochProcessorUtil.get_total_effective_balance(state, after_active_validators);

    int expected_num_validators = 25;
    UnsignedLong deposit_amount = UnsignedLong.valueOf(Constants.MAX_DEPOSIT_AMOUNT);
    UnsignedLong expected_total_balance =
        UnsignedLong.valueOf(expected_num_validators)
            .times(deposit_amount)
            .minus(UnsignedLong.valueOf(Constants.MAX_DEPOSIT_AMOUNT - val_balance));

    assertEquals(expected_num_validators, after_active_validators.size());
    assertEquals(expected_total_balance, after_total_balance);
  }

  @Disabled
  @Test
  void processPenaltiesAndExitsTest() {
    BeaconState state = createArbitraryBeaconState(25);
    // TODO: Figure out how to test PenaltiesAndExits
    UnsignedLong currentEpoch = BeaconStateUtil.get_current_epoch(state);

    Validators before_active_validators =
        ValidatorsUtil.get_active_validators(state.getValidator_registry(), currentEpoch);
    UnsignedLong before_total_balance =
        EpochProcessorUtil.get_total_effective_balance(state, before_active_validators);

    Validators validators =
        ValidatorsUtil.get_active_validators(state.getValidator_registry(), currentEpoch);
    // validators to withdrawal
    validators.get(0).setBalance(UnsignedLong.valueOf(Constants.MAX_DEPOSIT_AMOUNT));
    validators.get(0).setStatus_flags(UnsignedLong.valueOf(Constants.WITHDRAWABLE));

    // flag the validators with a balance below the threshold
    EpochProcessorUtil.process_penalties_and_exits(state);
    // increment the epoch to the time where the validator will be considered ejected
    currentEpoch = BeaconStateUtil.get_entry_exit_effect_epoch(currentEpoch);

    Validators after_active_validators =
        ValidatorsUtil.get_active_validators(state.getValidator_registry(), currentEpoch);
    UnsignedLong after_total_balance =
        EpochProcessorUtil.get_total_effective_balance(state, after_active_validators);

    int expected_num_validators = 24;
    UnsignedLong deposit_amount = UnsignedLong.valueOf(Constants.MAX_DEPOSIT_AMOUNT);
    UnsignedLong expected_total_balance =
        UnsignedLong.valueOf(expected_num_validators).times(deposit_amount);

    assertEquals(expected_num_validators, after_active_validators.size());
    assertEquals(expected_total_balance, after_total_balance);
  }
}
