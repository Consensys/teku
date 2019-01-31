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

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.ListIterator;
import net.consensys.cava.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.state.CrosslinkRecord;
import tech.pegasys.artemis.datastructures.state.ShardCommittee;
import tech.pegasys.artemis.datastructures.state.ValidatorRecord;
import tech.pegasys.artemis.datastructures.state.Validators;
import tech.pegasys.artemis.statetransition.BeaconState;

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
  }

  public static void updateFinalization(BeaconState state) {
    if (isPrevJustifiedSlotFinalized(state))
      state.setFinalized_slot(state.getPrevious_justified_slot());
  }

  public static void updateCrosslinks(BeaconState state) throws BlockValidationException {
    for (long n = (state.getSlot() - 2 * Constants.EPOCH_LENGTH); n < state.getSlot(); n++) {
      ArrayList<HashMap<Long, ShardCommittee>> crosslink_committees_at_slot =
          BeaconStateUtil.get_crosslink_committees_at_slot(state, n);
      for (HashMap<Long, ShardCommittee> crosslink_committees : crosslink_committees_at_slot) {
        for (Long shard : crosslink_committees.keySet()) {
          ShardCommittee crosslink_committee = crosslink_committees.get(shard);

          if (3 * AttestationUtil.getTotal_attesting_balance(state)
              >= 2 * total_balance(crosslink_committee)) {
            state
                .getLatest_crosslinks()
                .set(
                    Math.toIntExact(shard),
                    new CrosslinkRecord(
                        winning_root(crosslink_committee), UnsignedLong.valueOf(state.getSlot())));
          }
        }
      }
    }
  }

  public static void finalBookKeeping(BeaconState state) {
    process_ejections(state);
    update_validator_registry(state);
    process_penalties_and_exits(state);
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

  private static Bytes32 winning_root(ShardCommittee crosslink_committee) {
    // todo
    return null;
  }

  private static double total_balance(ShardCommittee crosslink_committee) {
    // todo
    return 0.0d;
  }

  public static void update_validator_registry(BeaconState state) {
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

  private static void process_penalties_and_exits(BeaconState state) {
    Validators active_validators =
        ValidatorsUtil.get_active_validators(state.getValidator_registry());
    double total_balance = ValidatorsUtil.get_effective_balance(active_validators);
    ListIterator<ValidatorRecord> itr =
        (ListIterator<ValidatorRecord>) active_validators.iterator();

    while (itr.hasNext()) {
      int index = itr.nextIndex();
      ValidatorRecord validator = itr.next();

      if (Math.floor(state.getSlot() / Constants.EPOCH_LENGTH)
          == Math.floor(validator.getPenalized_slot() / Constants.EPOCH_LENGTH)
              + Math.floor(Constants.LATEST_PENALIZED_EXIT_LENGTH / 2)) {
        int e =
            (int) Math.floor(state.getSlot() / Constants.EPOCH_LENGTH)
                % Constants.LATEST_PENALIZED_EXIT_LENGTH;
        ;
        double total_at_start =
            state
                .getLatest_penalized_exit_balances()
                .get((e + 1) % Constants.LATEST_PENALIZED_EXIT_LENGTH);
        double total_at_end = state.getLatest_penalized_exit_balances().get(e);
        double total_penalties = total_at_end - total_at_start;
        double penalty =
            BeaconStateUtil.get_effective_balance(state, validator)
                * Math.min(total_penalties * 3, total_balance); // total_balance
        state
            .getValidator_balances()
            .set(index, state.getValidator_balances().get(index) - penalty);
      }
    }
    Validators eligible_validators = new Validators();
    for (ValidatorRecord validator : active_validators) {
      if (eligible(state, validator)) eligible_validators.add(validator);
    }
    Collections.sort(
        eligible_validators,
        (a, b) -> {
          return a.getExit_count().compareTo(b.getExit_count());
        });

    int withdrawn_so_far = 0;
    for (ValidatorRecord validator : eligible_validators) {
      validator.setStatus(UnsignedLong.valueOf(Constants.WITHDRAWABLE));
      withdrawn_so_far += 1;
      if (withdrawn_so_far >= Constants.MAX_WITHDRAWALS_PER_EPOCH) break;
    }
  }

  private static boolean eligible(BeaconState state, ValidatorRecord validator) {
    if (validator.getPenalized_slot() <= state.getSlot()) {
      long penalized_withdrawal_time =
          (long) Math.floor(Constants.LATEST_PENALIZED_EXIT_LENGTH * Constants.EPOCH_LENGTH / 2);
      return state.getSlot() >= validator.getPenalized_slot() + penalized_withdrawal_time;
    } else
      return state.getSlot()
          >= validator.getPenalized_slot() + Constants.MIN_VALIDATOR_WITHDRAWAL_TIME;
  }
}
