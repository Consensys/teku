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

import static tech.pegasys.artemis.datastructures.Constants.MAX_DEPOSIT_AMOUNT;

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import net.consensys.cava.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.state.Crosslink;
import tech.pegasys.artemis.datastructures.state.CrosslinkCommittee;
import tech.pegasys.artemis.datastructures.state.Validator;
import tech.pegasys.artemis.statetransition.BeaconState;
import tech.pegasys.artemis.util.bitwise.BitwiseOps;

public class EpochProcessorUtil {

  // epoch processing
  public static void updateJustification(BeaconState state) throws Exception {
    // Get previous and current epoch
    UnsignedLong current_epoch = BeaconStateUtil.get_current_epoch(state);
    UnsignedLong previous_epoch = BeaconStateUtil.get_previous_epoch(state);

    // Get previous and current epoch total balances
    List<Validator> current_active_validators =
        ValidatorsUtil.get_active_validators(state.getValidator_registry(), current_epoch);
    List<Validator> previous_active_validators =
        ValidatorsUtil.get_active_validators(state.getValidator_registry(), previous_epoch);
    UnsignedLong current_total_balance =
        get_total_effective_balance(state, current_active_validators);
    UnsignedLong previous_total_balance =
        get_total_effective_balance(state, previous_active_validators);

    // Update justification bitfield
    UnsignedLong new_justified_epoch = state.getJustified_epoch();
    UnsignedLong justification_bitfield = state.getJustification_bitfield();
    justification_bitfield = BitwiseOps.leftShift(justification_bitfield, 1);

    if (AttestationUtil.get_previous_epoch_boundary_attesting_balance(state)
            .times(UnsignedLong.valueOf(3))
            .compareTo(previous_total_balance.times(UnsignedLong.valueOf(2)))
        >= 0) {
      justification_bitfield = BitwiseOps.or(justification_bitfield, UnsignedLong.valueOf(2));
      new_justified_epoch = previous_epoch;
    }
    if (AttestationUtil.get_current_epoch_boundary_attesting_balance(state)
            .times(UnsignedLong.valueOf(3))
            .compareTo(current_total_balance.times(UnsignedLong.valueOf(2)))
        >= 0) {
      justification_bitfield = BitwiseOps.or(justification_bitfield, UnsignedLong.ONE);
      new_justified_epoch = current_epoch;
    }

    state.setJustification_bitfield(justification_bitfield);

    // Update last finalized epoch if possible
    UnsignedLong decimal4 = UnsignedLong.valueOf(4);
    UnsignedLong decimal8 = UnsignedLong.valueOf(8);
    UnsignedLong binary11 = UnsignedLong.valueOf(3);
    UnsignedLong binary111 = UnsignedLong.valueOf(7);
    UnsignedLong previous_justified_epoch = state.getPrevious_justified_epoch();
    UnsignedLong justified_epoch = state.getJustified_epoch();

    if (BitwiseOps.rightShift(justification_bitfield, 1).mod(decimal8).equals(binary111)
        && previous_justified_epoch.equals(previous_epoch.minus(UnsignedLong.valueOf(2)))) {
      state.setFinalized_epoch(previous_justified_epoch);
    }
    if (BitwiseOps.rightShift(justification_bitfield, 1).mod(decimal4).equals(binary11)
        && previous_justified_epoch.equals(previous_epoch.minus(UnsignedLong.ONE))) {
      state.setFinalized_epoch(previous_justified_epoch);
    }
    if (justification_bitfield.mod(decimal8).equals(binary111)
        && justified_epoch.equals(previous_epoch.minus(UnsignedLong.ONE))) {
      state.setFinalized_epoch(justified_epoch);
    }
    if (justification_bitfield.mod(decimal4).equals(binary11)
        && justified_epoch.equals(previous_epoch)) {
      state.setFinalized_epoch(justified_epoch);
    }

    // Update state justification variables
    state.setPrevious_justified_epoch(state.getJustified_epoch());
    state.setJustified_epoch(new_justified_epoch);
  }

  public static void updateCrosslinks(BeaconState state) throws BlockValidationException {
    UnsignedLong slot = state.getSlot();
    UnsignedLong end = UnsignedLong.valueOf(2*Constants.EPOCH_LENGTH);
    while (slot.compareTo(end) < 0){
      List<CrosslinkCommittee> crosslink_committees_at_slot = BeaconStateUtil.get_crosslink_committees_at_slot(state, slot);
      for (CrosslinkCommittee crosslink_committee : crosslink_committees_at_slot) {
        UnsignedLong shard = crosslink_committee.getShard();
        UnsignedLong max_balance = UnsignedLong.ZERO;
        if (3 * AttestationUtil.getTotal_attesting_balance(state)
            >= 2 * total_balance(crosslink_committee)) {
                Bytes32 shard_block_root = BeaconStateUtil.getShard_block_root(state, shard);
                List<Integer> attesting_validator_indices = AttestationUtil.attesting_validator_indices(state, crosslink_committee,shard_block_root);
                max_balance = get_total_effective_balance(state, attesting_validator_indices);

                return null;
              }
          state
              .getLatest_crosslinks()
              .set(
                  shard.intValue(),
                  new Crosslink(state.getSlot(), winning_root(crosslink_committee)));
        }
      }
      slot = slot.plus(UnsignedLong.ONE);
    }
  }

  public static void finalBookKeeping(BeaconState state) {
    process_ejections(state);
    update_validator_registry(state);
    process_penalties_and_exits(state);
  }

  private static boolean isPrevJustifiedSlotFinalized(BeaconState state) {
    // TODO: change values to UnsignedLong
    // TODO: Method requires major changes following BeaconState refactor
    return true;
    //    return ((state.getPrevious_justified_slot() == ((state.getSlot() - 2) *
    // Constants.EPOCH_LENGTH)
    //            && (state.getJustification_bitfield() % 4) == 3)
    //        || (state.getPrevious_justified_slot() == ((state.getSlot() - 3) *
    // Constants.EPOCH_LENGTH)
    //            && (state.getJustification_bitfield() % 8) == 7)
    //        || (state.getPrevious_justified_slot() == ((state.getSlot() - 4) *
    // Constants.EPOCH_LENGTH)
    //            && ((state.getJustification_bitfield() % 16) == 14
    //                || (state.getJustification_bitfield() % 16) == 15)));
  }


  private static double total_balance(CrosslinkCommittee crosslink_committee) {
    // todo
    return 0.0d;
  }

  static void update_validator_registry(BeaconState state) {
    UnsignedLong currentEpoch = BeaconStateUtil.get_current_epoch(state);
    List<Validator> active_validators =
        ValidatorsUtil.get_active_validators(
            state.getValidator_registry(), BeaconStateUtil.get_current_epoch(state));
    UnsignedLong total_balance = get_total_effective_balance(state, active_validators);

    UnsignedLong max_balance_churn =
        BeaconStateUtil.max(
            UnsignedLong.valueOf(MAX_DEPOSIT_AMOUNT),
            total_balance.dividedBy(
                UnsignedLong.valueOf((2 * Constants.MAX_BALANCE_CHURN_QUOTIENT))));

    // Activate validators within the allowable balance churn
    UnsignedLong balance_churn = UnsignedLong.ZERO;
    int index = 0;
    for (Validator validator : state.getValidator_registry()) {
      if (validator
                  .getActivation_epoch()
                  .compareTo(BeaconStateUtil.get_entry_exit_effect_epoch(currentEpoch))
              > 0
          && state
                  .getValidator_balances()
                  .get(index)
                  .compareTo(UnsignedLong.valueOf(Constants.MAX_DEPOSIT_AMOUNT))
              >= 0) {
        balance_churn = balance_churn.plus(get_effective_balance(state, index));
        if (balance_churn.compareTo(max_balance_churn) > 0) break;
        BeaconStateUtil.activate_validator(state, validator, false);
      }
      index++;
    }

    // Exit validators within the allowable balance churn
    balance_churn = UnsignedLong.ZERO;
    index = 0;
    for (Validator validator : state.getValidator_registry()) {
      List<UnsignedLong> balances = state.getValidator_balances();
      if (validator
                  .getExit_epoch()
                  .compareTo(BeaconStateUtil.get_entry_exit_effect_epoch(currentEpoch))
              > 0
          && validator.getStatus_flags().compareTo(UnsignedLong.valueOf(Constants.INITIATED_EXIT))
              == 0) {
        balance_churn = balance_churn.plus(get_effective_balance(state, validator));
        if (balance_churn.compareTo(max_balance_churn) > 0) break;
        BeaconStateUtil.exit_validator(state, index);
      }
      index++;
    }
    state.setValidator_registry_update_epoch(currentEpoch);
  }

  static void process_ejections(BeaconState state) {
    int index = 0;
    UnsignedLong currentEpoch = BeaconStateUtil.get_current_epoch(state);
    List<Validator> active_validators =
        ValidatorsUtil.get_active_validators(state.getValidator_registry(), currentEpoch);
    for (Validator validator : active_validators) {
      List<UnsignedLong> balances = state.getValidator_balances();
      if (balances.get(index).compareTo(UnsignedLong.valueOf(Constants.EJECTION_BALANCE)) < 0) {
        BeaconStateUtil.exit_validator(state, index);
      }
      index++;
    }
  }

  static void process_penalties_and_exits(BeaconState state) {
    UnsignedLong currentEpoch = BeaconStateUtil.get_current_epoch(state);
    List<Validator> active_validators =
        ValidatorsUtil.get_active_validators(state.getValidator_registry(), currentEpoch);

    // total_balance = sum(get_effective_balance(state, i) for i in active_validator_indices)
    UnsignedLong total_balance = get_total_effective_balance(state, active_validators);

    ListIterator<Validator> itr = state.getValidator_registry().listIterator();
    while (itr.hasNext()) {
      int index = itr.nextIndex();
      Validator validator = itr.next();

      if (currentEpoch.equals(
          validator
              .getPenalized_epoch()
              .plus(UnsignedLong.valueOf(Constants.LATEST_PENALIZED_EXIT_LENGTH / 2)))) {
        int epoch_index = currentEpoch.intValue() % Constants.LATEST_PENALIZED_EXIT_LENGTH;

        UnsignedLong total_at_start =
            state
                .getLatest_penalized_balances()
                .get((epoch_index + 1) % Constants.LATEST_PENALIZED_EXIT_LENGTH);
        UnsignedLong total_at_end = state.getLatest_penalized_balances().get(epoch_index);
        UnsignedLong total_penalties = total_at_end.minus(total_at_start);
        UnsignedLong penalty =
            get_effective_balance(state, validator)
                .times(
                    BeaconStateUtil.min(
                        total_penalties.times(UnsignedLong.valueOf(3)), total_balance))
                .dividedBy(total_balance);
        state
            .getValidator_balances()
            .set(index, state.getValidator_balances().get(index).minus(penalty));
      }
    }

    ArrayList<Validator> eligible_validators = new ArrayList<>();
    for (Validator validator : state.getValidator_registry()) {
      if (eligible(state, validator)) eligible_validators.add(validator);
    }
    Collections.sort(
        eligible_validators,
        (a, b) -> {
          return a.getExit_epoch().compareTo(b.getExit_epoch());
        });

    int withdrawn_so_far = 0;
    for (Validator validator : eligible_validators) {
      validator.setStatus_flags(UnsignedLong.valueOf(Constants.WITHDRAWABLE));
      withdrawn_so_far += 1;
      if (withdrawn_so_far >= Constants.MAX_WITHDRAWALS_PER_EPOCH) break;
    }
  }

  static boolean eligible(BeaconState state, Validator validator) {
    UnsignedLong currentEpoch = BeaconStateUtil.get_current_epoch(state);
    if (validator.getPenalized_epoch().compareTo(currentEpoch) <= 0) {
      UnsignedLong penalized_withdrawal_epochs =
          UnsignedLong.valueOf(
              (long)
                  Math.floor(
                      Constants.LATEST_PENALIZED_EXIT_LENGTH * Constants.EPOCH_LENGTH / 2.0));
      return state
              .getSlot()
              .compareTo(validator.getPenalized_epoch().plus(penalized_withdrawal_epochs))
          >= 0;
    } else {
      return currentEpoch.compareTo(
              validator
                  .getExit_epoch()
                  .plus(UnsignedLong.valueOf(Constants.MIN_VALIDATOR_WITHDRAWAL_EPOCHS)))
          >= 0;
    }
  }

  static UnsignedLong get_total_effective_balance(BeaconState state, List<Integer> validator_indices) {
    UnsignedLong total_balance = UnsignedLong.ZERO;
    for(Integer index : validator_indices){
      total_balance = total_balance.plus(get_effective_balance(state, index));
    }
    return total_balance;
  }

  static UnsignedLong get_effective_balance(BeaconState state, int index) {
    return BeaconStateUtil.min(
        state.getValidator_balances().get(index),
        UnsignedLong.valueOf(Constants.MAX_DEPOSIT_AMOUNT));
  }

  static UnsignedLong get_effective_balance(BeaconState state, Validator record) {
    int index = state.getValidator_registry().indexOf(record);
    return get_effective_balance(state, index);
  }
}
