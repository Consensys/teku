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

package tech.pegasys.artemis.state.util;

import java.util.ArrayList;
import tech.pegasys.artemis.Constants;
import tech.pegasys.artemis.datastructures.beaconchainstate.ValidatorRecord;
import tech.pegasys.artemis.datastructures.beaconchainstate.Validators;
import tech.pegasys.artemis.state.BeaconState;

public class EpochProcessorUtil {

  // epoch processing
  public static void updateJustification(BeaconState state) throws Exception {
    state.setPrevious_justified_slot(state.getJustified_slot());
    state.setJustification_bitfield(
        (state.getJustification_bitfield() * 2) % ((int) Math.pow(2, 64)));
    double total_balance = BeaconStateUtil.calc_total_balance(state);

    if (3 * AttestationUtil.get_previous_epoch_boundary_attesting_balance(state)
        >= (2 * total_balance)) {
      state.setJustification_bitfield(state.getJustification_bitfield() | 2);
      state.setJustified_slot((state.getSlot() - 2) % Constants.EPOCH_LENGTH);
    } else if (3 * AttestationUtil.get_current_epoch_boundary_attesting_balance(state)
        >= (2 * total_balance)) {
      state.setJustification_bitfield(state.getJustification_bitfield() | 1);
      state.setJustified_slot((state.getSlot() - 2) % Constants.EPOCH_LENGTH);
    }
    if (isPrevJustifiedSlotFinalized(state))
      state.setFinalized_slot(state.getPrevious_justified_slot());
  }

  private static boolean isPrevJustifiedSlotFinalized(BeaconState state) {
    return ((state.getPrevious_justified_slot() == ((state.getSlot() - 2) * Constants.EPOCH_LENGTH)
            && (state.getJustification_bitfield() % 4) == 3)
        || (state.getPrevious_justified_slot() == ((state.getSlot() - 3) * Constants.EPOCH_LENGTH)
            && (state.getJustification_bitfield() % 8) == 7)
        || (state.getPrevious_justified_slot() == ((state.getSlot() - 4) * Constants.EPOCH_LENGTH)
            && ((state.getJustification_bitfield() % 16) == 14
                || (state.getJustification_bitfield() % 16) == 15)));
  }

  public static void updateFinalization(BeaconState state) {}

  public static void updateCrosslinks(BeaconState state) {}

  public static void finalBookKeeping(BeaconState state) {}

  public static void updateValidatorRegistry(BeaconState state) {
    Validators active_validators =
        ValidatorsUtil.get_active_validators(state.getValidator_registry());
    double total_balance = ValidatorsUtil.get_effective_balance(active_validators);

    double max_balance_churn =
        Math.max(
            (double) (Constants.MAX_DEPOSIT * Constants.GWEI_PER_ETH),
            total_balance / (2 * Constants.MAX_BALANCE_CHURN_QUOTIENT));

    updatePendingValidators(max_balance_churn, state);
    updateActivePendingExit(max_balance_churn, state);

    int period_index =
        Math.toIntExact(state.getSlot() / Constants.COLLECTIVE_PENALTY_CALCULATION_PERIOD);
    ArrayList<Double> latest_penalized_exit_balances = state.getLatest_penalized_exit_balances();

    double total_penalties =
        latest_penalized_exit_balances.get(period_index)
            + latest_penalized_exit_balances.get(period_index - 1 < 0 ? period_index - 1 : 0)
            + latest_penalized_exit_balances.get(period_index - 2 < 0 ? period_index - 2 : 0);

    ArrayList<ValidatorRecord> to_penalize = to_penalize(active_validators);
  }

  private static void updatePendingValidators(double max_balance_churn, BeaconState state) {
    double balance_churn = 0.0d;

    for (ValidatorRecord validator : state.getValidator_registry()) {
      if (validator.getStatus().longValue() == Constants.PENDING_ACTIVATION
          && validator.getBalance() >= Constants.MAX_DEPOSIT * Constants.GWEI_PER_ETH) {
        balance_churn += validator.get_effective_balance();

        if (balance_churn > max_balance_churn) break;

        // temporary hack to pass by index to already in place code
        // Java should pass by reference
        state.update_validator_status(
            state, state.getValidator_registry().indexOf(validator), Constants.ACTIVE);
      }
    }
  }

  private static void updateActivePendingExit(double max_balance_churn, BeaconState state) {
    double balance_churn = 0.0d;

    for (ValidatorRecord validator : state.getValidator_registry()) {
      if (validator.getStatus().longValue() == Constants.ACTIVE_PENDING_EXIT
          && validator.getBalance() >= Constants.MAX_DEPOSIT * Constants.GWEI_PER_ETH) {
        balance_churn += validator.get_effective_balance();

        if (balance_churn > max_balance_churn) break;

        // temporary hack to pass by index to already in place code
        // Java should pass by reference
        state.update_validator_status(
            state,
            state.getValidator_registry().indexOf(validator),
            Constants.EXITED_WITHOUT_PENALTY);
      }
    }
  }

  private static void process_ejections(BeaconState state) {
    for (ValidatorRecord validator : state.getValidator_registry()) {
      if (validator.getBalance() < Constants.EJECTION_BALANCE)
        state.update_validator_status(
            state,
            state.getValidator_registry().indexOf(validator),
            Constants.EXITED_WITHOUT_PENALTY);
    }
  }

  private static ArrayList<ValidatorRecord> to_penalize(
      ArrayList<ValidatorRecord> validator_registry) {
    ArrayList<ValidatorRecord> to_penalize = new ArrayList<>();
    if (validator_registry != null) {
      for (ValidatorRecord validator : validator_registry) {
        if (validator.getStatus().longValue() == Constants.EXITED_WITH_PENALTY)
          to_penalize.add(validator);
      }
    }
    return to_penalize;
  }
}
