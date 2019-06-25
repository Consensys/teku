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
import static tech.pegasys.artemis.datastructures.util.ValidatorsUtil.get_active_validator_indices;

import com.google.common.primitives.UnsignedLong;
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
          state, deposits, UnsignedLong.valueOf(Constants.GENESIS_SLOT), randomEth1Data());
      UnsignedLong currentEpoch = BeaconStateUtil.get_current_epoch(state);

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
  void previousStateUpdatesTests() {
    // todo
  }

  @Test
  void shouldUpdateValidatorRegistryTests() {
    // todo
  }

  @Test
  void currentStateUpdatesAlt1Tests() {
    // todo
  }

  @Test
  void currentStateUpdatesAlt2Tests() {
    // todo
  }

  @Test
  void processRegistryUpdatesTest() throws EpochProcessingException {
    BeaconState state = createArbitraryBeaconState(128);
    UnsignedLong currentEpoch = BeaconStateUtil.get_current_epoch(state);

    // make 4 validators have balance below threshold and 1 right at the threshhold
    state
        .getValidator_registry()
        .get(0)
        .setEffective_balance(UnsignedLong.valueOf(Constants.EJECTION_BALANCE / 4));
    state
        .getValidator_registry()
        .get(10)
        .setEffective_balance(UnsignedLong.valueOf(Constants.EJECTION_BALANCE / 8));
    state.getValidator_registry().get(22).setEffective_balance(UnsignedLong.valueOf(0L));
    state
        .getValidator_registry()
        .get(35)
        .setEffective_balance(UnsignedLong.valueOf(Constants.EJECTION_BALANCE / 2));
    // validator stays active
    state
        .getValidator_registry()
        .get(2)
        .setEffective_balance(UnsignedLong.valueOf(Constants.EJECTION_BALANCE));

    // flag the validators with a balance below the threshold
    EpochProcessorUtil.process_registry_updates(state);
    // increment the epoch to the time where the validator will be considered ejected
    currentEpoch = BeaconStateUtil.get_entry_exit_effect_epoch(currentEpoch);
    List<Integer> list = get_active_validator_indices(state, currentEpoch);

    int expected_num_validators = 128 - 4;

    assertEquals(expected_num_validators, list.size());
  }

  @Test
  void processPenaltiesAndExitsTest() throws EpochProcessingException {
    BeaconState state = createArbitraryBeaconState(128);
    // TODO: Figure out how to test PenaltiesAndExits
    UnsignedLong currentEpoch = BeaconStateUtil.get_current_epoch(state);

    List<Integer> before_active_validators = get_active_validator_indices(state, currentEpoch);
    UnsignedLong before_total_balance =
        BeaconStateUtil.get_total_balance(state, before_active_validators);

    List<Validator> validators =
        ValidatorsUtil.get_active_validators(state.getValidator_registry(), currentEpoch);
    // validators to withdrawal
    state.getBalances().set(0, UnsignedLong.valueOf(Constants.MAX_EFFECTIVE_BALANCE));
    validators.get(0).setSlashed(true);

    // flag the validators with a balance below the threshold
    //    EpochProcessorUtil.process_penalties_and_exits(state);
    // increment the epoch to the time where the validator will be considered ejected
    currentEpoch = BeaconStateUtil.get_entry_exit_effect_epoch(currentEpoch);

    List<Integer> after_active_validators = get_active_validator_indices(state, currentEpoch);
    UnsignedLong after_total_balance =
        BeaconStateUtil.get_total_balance(state, after_active_validators);

    int expected_num_validators = 128;
    UnsignedLong deposit_amount = UnsignedLong.valueOf(Constants.MAX_EFFECTIVE_BALANCE);
    UnsignedLong expected_total_balance =
        UnsignedLong.valueOf(expected_num_validators).times(deposit_amount);

    assertEquals(expected_num_validators, after_active_validators.size());
    assertEquals(expected_total_balance, after_total_balance);
  }

  @Test
  void finalUpdatesTests() {

    // todo
  }
}
