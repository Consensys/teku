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

import static java.lang.Math.toIntExact;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import net.consensys.cava.bytes.Bytes32;
import org.apache.logging.log4j.Level;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.blocks.Eth1DataVote;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.Crosslink;
import tech.pegasys.artemis.datastructures.state.CrosslinkCommittee;
import tech.pegasys.artemis.datastructures.state.PendingAttestation;
import tech.pegasys.artemis.datastructures.state.Validator;
import tech.pegasys.artemis.datastructures.util.AttestationUtil;
import tech.pegasys.artemis.datastructures.util.BeaconStateUtil;
import tech.pegasys.artemis.datastructures.util.ValidatorsUtil;
import tech.pegasys.artemis.util.alogger.ALogger;
import tech.pegasys.artemis.util.bitwise.BitwiseOps;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil;

public final class EpochProcessorUtil {
  private static final ALogger LOG = new ALogger(EpochProcessorUtil.class.getName());

  /**
   * update eth1Data state fields. spec:
   * https://github.com/ethereum/eth2.0-specs/blob/v0.1/specs/core/0_beacon-chain.md#eth1-data-1
   *
   * @param state
   * @throws EpochProcessingException
   */
  public static void updateEth1Data(BeaconState state) throws EpochProcessingException {
    UnsignedLong next_epoch = BeaconStateUtil.get_next_epoch(state);
    if (next_epoch
            .mod(UnsignedLong.valueOf(Constants.EPOCHS_PER_ETH1_VOTING_PERIOD))
            .compareTo(UnsignedLong.ZERO)
        == 0) {
      List<Eth1DataVote> eth1_data_votes = state.getEth1_data_votes();
      for (Eth1DataVote eth1_data_vote : eth1_data_votes) {
        if (eth1_data_vote
                .getVote_count()
                .times(UnsignedLong.valueOf(2))
                .compareTo(
                    UnsignedLong.valueOf(
                        Constants.EPOCHS_PER_ETH1_VOTING_PERIOD * Constants.SLOTS_PER_EPOCH))
            > 0) {
          state.setLatest_eth1_data(eth1_data_vote.getEth1_data());
        }
      }

      state.setEth1_data_votes(new ArrayList<>());
    }
  }

  /**
   * Update Justification state fields
   *
   * @param state
   * @throws EpochProcessingException
   */
  public static void updateJustification(BeaconState state, BeaconBlock block)
      throws EpochProcessingException {
    try {
      UnsignedLong current_epoch = BeaconStateUtil.get_current_epoch(state);
      UnsignedLong previous_epoch = BeaconStateUtil.get_previous_epoch(state);

      // Get previous and current epoch total balances
      List<Integer> current_active_validators =
          ValidatorsUtil.get_active_validator_indices(state.getValidator_registry(), current_epoch);
      List<Integer> previous_active_validators =
          ValidatorsUtil.get_active_validator_indices(
              state.getValidator_registry(), previous_epoch);
      UnsignedLong current_total_balance =
          BeaconStateUtil.get_total_balance(state, current_active_validators);
      UnsignedLong previous_total_balance =
          BeaconStateUtil.get_total_balance(state, previous_active_validators);

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
    } catch (IllegalArgumentException e) {
      LOG.log(Level.WARN, "EpochProcessingException thrown in updateJustification()");
      throw new EpochProcessingException(e);
    }
  }

  /**
   * https://github.com/ethereum/eth2.0-specs/blob/v0.1/specs/core/0_beacon-chain.md#crosslinks
   *
   * @param state
   */
  public static void updateCrosslinks(BeaconState state) throws EpochProcessingException {
    try {
      UnsignedLong previous_epoch = BeaconStateUtil.get_previous_epoch(state);
      UnsignedLong next_epoch = BeaconStateUtil.get_next_epoch(state);

      for (UnsignedLong curr_slot = BeaconStateUtil.get_epoch_start_slot(previous_epoch);
          curr_slot.compareTo(BeaconStateUtil.get_epoch_start_slot(next_epoch)) < 0;
          curr_slot = curr_slot.plus(UnsignedLong.ONE)) {
        LOG.log(Level.DEBUG, "current slot: " + curr_slot.toString());
        List<CrosslinkCommittee> crosslink_committees_at_slot =
            BeaconStateUtil.get_crosslink_committees_at_slot(state, curr_slot);
        for (CrosslinkCommittee committee : crosslink_committees_at_slot) {
          LOG.log(Level.DEBUG, "Committee Shard: " + committee.getShard().toString());
          LOG.log(
              Level.DEBUG,
              "Total Attesting Balance: "
                  + AttestationUtil.total_attesting_balance(state, committee));
          LOG.log(Level.DEBUG, "committee: " + committee.getCommittee());
          LOG.log(
              Level.DEBUG, "Total Balance: " + BeaconStateUtil.get_total_balance(state, committee));
          if (AttestationUtil.total_attesting_balance(state, committee)
                  .times(UnsignedLong.valueOf(3L))
                  .compareTo(
                      BeaconStateUtil.get_total_balance(state, committee)
                          .times(UnsignedLong.valueOf(2L)))
              >= 0) {
            UnsignedLong shard = committee.getShard();
            state
                .getLatest_crosslinks()
                .set(
                    toIntExact(shard.longValue()) % Constants.SHARD_COUNT,
                    new Crosslink(
                        BeaconStateUtil.get_current_epoch(state),
                        AttestationUtil.winning_root(state, committee)));
          }
        }
      }
    } catch (IllegalArgumentException e) {
      LOG.log(Level.WARN, "EpochProcessingException thrown in updateCrosslinks()");
      throw new EpochProcessingException(e);
    }
  }

  static Function<Integer, UnsignedLong> apply_inactivity_penalty(
      BeaconState state, UnsignedLong epochs_since_finality, UnsignedLong previous_total_balance) {
    return index ->
        inactivity_penality(state, index, epochs_since_finality, previous_total_balance);
  }

  static Function<Integer, UnsignedLong> apply_base_penalty(
      BeaconState state, UnsignedLong epochs_since_finality, UnsignedLong previous_total_balance) {
    return index -> base_reward(state, index, previous_total_balance);
  }

  static Function<Integer, UnsignedLong> apply_inactivity_base_penalty(
      BeaconState state, UnsignedLong epochs_since_finality, UnsignedLong previous_total_balance) {
    return index ->
        UnsignedLong.valueOf(2L)
            .times(inactivity_penality(state, index, epochs_since_finality, previous_total_balance))
            .plus(base_reward(state, index, previous_total_balance));
  }

  static Function<Integer, UnsignedLong> apply_inclusion_base_penalty(
      BeaconState state, UnsignedLong epochs_since_finality, UnsignedLong previous_total_balance) {
    return index -> {
      UnsignedLong inclusion_distance = UnsignedLong.ZERO;
      try {
        inclusion_distance = AttestationUtil.inclusion_distance(state, index);
      } catch (Exception e) {
        LOG.log(Level.WARN, "apply_inclusion_base_penalty(): " + e);
      }
      return base_reward(state, index, previous_total_balance)
          .times(UnsignedLong.valueOf(Constants.MIN_ATTESTATION_INCLUSION_DELAY))
          .dividedBy(inclusion_distance);
    };
  }

  // Helper method for justificationAndFinalization()
  static void case_one_penalties_and_rewards(
      BeaconState state,
      List<UnsignedLong> balances,
      UnsignedLong previous_total_balance,
      UnsignedLong previous_balance,
      List<Integer> previous_indices) {
    UnsignedLong reward_delta = UnsignedLong.ZERO;

    // make a list of integers from 0 to numberOfValidators
    List<Integer> missing_indices =
        IntStream.range(0, state.getValidator_registry().size())
            .boxed()
            .collect(Collectors.toList());

    // apply rewards to validator indices in the list
    for (int index : previous_indices) {
      reward_delta =
          base_reward(state, index, previous_total_balance)
              .times(previous_balance)
              .dividedBy(previous_total_balance);
      apply_penalty_or_reward(balances, index, reward_delta, true);
      missing_indices.remove(missing_indices.indexOf(index));
    }

    // apply penalties to active validator indices not in the list
    for (int index : missing_indices) {
      if (ValidatorsUtil.is_active_validator_index(
          state, index, BeaconStateUtil.get_current_epoch(state))) {
        reward_delta =
            base_reward(state, index, previous_total_balance)
                .times(previous_balance)
                .dividedBy(previous_total_balance);
        apply_penalty_or_reward(balances, index, reward_delta, false);
      }
    }
  }

  // Helper method for justificationAndFinalization()
  static void case_two_penalties(
      BeaconState state,
      List<UnsignedLong> balances,
      List<Integer> validator_indices,
      Function<Integer, UnsignedLong> penalty) {

    UnsignedLong penalty_delta = UnsignedLong.ZERO;
    for (int index : validator_indices) {
      penalty_delta = penalty.apply(index);
      apply_penalty_or_reward(balances, index, penalty_delta, false);
    }
  }

  // Helper method for justificationAndFinalization()
  static void apply_penalty_or_reward(
      List<UnsignedLong> balances, int index, UnsignedLong delta_balance, Boolean reward) {
    UnsignedLong balance = balances.get(index);
    if (reward) {
      // TODO: add check for overflow
      balance = balance.plus(delta_balance);
    } else {
      // TODO: add check for underflow
      balance = balance.minus(delta_balance);
    }
    balances.set(index, balance);
  }

  /**
   * Rewards and penalties applied with respect to justification and finalization. Spec:
   * https://github.com/ethereum/eth2.0-specs/blob/v0.1/specs/core/0_beacon-chain.md#justification-and-finalization
   *
   * @param state
   */
  public static void justificationAndFinalization(
      BeaconState state, UnsignedLong previous_total_balance) throws EpochProcessingException {
    try {
      final UnsignedLong FOUR = UnsignedLong.valueOf(4L);
      UnsignedLong epochs_since_finality =
          BeaconStateUtil.get_next_epoch(state).minus(state.getFinalized_epoch());
      List<UnsignedLong> balances = state.getValidator_balances();

      // Case 1: epochs_since_finality <= 4:
      if (epochs_since_finality.compareTo(FOUR) <= 0) {
        // Expected FFG source
        UnsignedLong previous_balance = AttestationUtil.get_previous_epoch_attesting_balance(state);
        List<Integer> previous_indices = AttestationUtil.get_previous_epoch_attester_indices(state);
        case_one_penalties_and_rewards(
            state, balances, previous_total_balance, previous_balance, previous_indices);

        // Expected FFG target
        previous_balance = AttestationUtil.get_previous_epoch_boundary_attesting_balance(state);
        previous_indices = AttestationUtil.get_previous_epoch_boundary_attester_indices(state);
        case_one_penalties_and_rewards(
            state, balances, previous_total_balance, previous_balance, previous_indices);

        // Expected beacon chain head
        previous_balance = AttestationUtil.get_previous_epoch_head_attesting_balance(state);
        previous_indices = AttestationUtil.get_previous_epoch_head_attester_indices(state);
        case_one_penalties_and_rewards(
            state, balances, previous_total_balance, previous_balance, previous_indices);

        // Inclusion distance
        UnsignedLong reward_delta = UnsignedLong.ZERO;
        previous_indices = AttestationUtil.get_previous_epoch_attester_indices(state);
        for (int index : previous_indices) {
          UnsignedLong inclusion_distance = AttestationUtil.inclusion_distance(state, index);
          reward_delta =
              base_reward(state, index, previous_total_balance)
                  .times(UnsignedLong.valueOf(Constants.MIN_ATTESTATION_INCLUSION_DELAY))
                  .dividedBy(inclusion_distance);
          apply_penalty_or_reward(balances, index, reward_delta, true);
        }

        // Case 2: epochs_since_finality > 4:
      } else if (epochs_since_finality.compareTo(FOUR) > 0) {

        Predicate<Integer> active_validators_filter =
            index -> {
              return ValidatorsUtil.is_active_validator_index(
                  state, index, BeaconStateUtil.get_current_epoch(state));
            };

        Predicate<Integer> slashed_filter =
            index -> {
              Validator validator = state.getValidator_registry().get(index);
              return validator.isSlashed() == true;
            };

        // prev epoch justified attester
        List<Integer> validator_indices =
            AttestationUtil.get_previous_epoch_attester_indices(state);
        // find all validators not present in the list
        validator_indices = ValidatorsUtil.get_validators_not_present(validator_indices);
        // remove inactive validator indices
        validator_indices =
            Lists.newArrayList(Collections2.filter(validator_indices, active_validators_filter));
        // apply penalty
        case_two_penalties(
            state,
            balances,
            validator_indices,
            apply_inactivity_penalty(state, epochs_since_finality, previous_total_balance));

        // prev epoch boundary attester
        validator_indices = AttestationUtil.get_previous_epoch_boundary_attester_indices(state);
        // find all validators not present in the list
        validator_indices = ValidatorsUtil.get_validators_not_present(validator_indices);
        // remove inactive validator indices
        validator_indices =
            Lists.newArrayList(Collections2.filter(validator_indices, active_validators_filter));
        // apply penalty
        case_two_penalties(
            state,
            balances,
            validator_indices,
            apply_inactivity_penalty(state, epochs_since_finality, previous_total_balance));

        // prev epoch head attester
        validator_indices = AttestationUtil.get_previous_epoch_head_attester_indices(state);
        // find all validators not present in the list
        validator_indices = ValidatorsUtil.get_validators_not_present(validator_indices);
        // remove inactive validator indices
        validator_indices =
            Lists.newArrayList(Collections2.filter(validator_indices, active_validators_filter));
        // apply penalty
        case_two_penalties(
            state,
            balances,
            validator_indices,
            apply_base_penalty(state, epochs_since_finality, previous_total_balance));

        // all validator indices
        List<Integer> all_validator_indices =
            IntStream.range(0, state.getValidator_registry().size())
                .boxed()
                .collect(Collectors.toList());
        // remove inactive validator indices
        validator_indices =
            Lists.newArrayList(
                Collections2.filter(all_validator_indices, active_validators_filter));
        // remove validators that were not slashed in this epoch or a previous one
        validator_indices =
            Lists.newArrayList(Collections2.filter(validator_indices, slashed_filter));
        // apply penalty
        case_two_penalties(
            state,
            balances,
            validator_indices,
            apply_inactivity_base_penalty(state, epochs_since_finality, previous_total_balance));

        // prev epoch attester indices
        validator_indices = AttestationUtil.get_previous_epoch_head_attester_indices(state);
        // apply penalty
        case_two_penalties(
            state,
            balances,
            validator_indices,
            apply_inclusion_base_penalty(state, epochs_since_finality, previous_total_balance));
      }
    } catch (IllegalArgumentException e) {
      LOG.log(Level.WARN, "EpochProcessingException thrown in justificationAndFinalization()");
      throw new EpochProcessingException(e);
    }
  }

  /**
   * applys the attestation inclusion reward to all eligible attestors
   *
   * @param state
   * @param previous_total_balance
   */
  public static void attestionInclusion(BeaconState state, UnsignedLong previous_total_balance)
      throws EpochProcessingException {
    try {
      List<Integer> previous_indices = AttestationUtil.get_previous_epoch_attester_indices(state);
      for (int index : previous_indices) {
        UnsignedLong inclusion_slot = AttestationUtil.inclusion_slot(state, index);
        int proposer_index = BeaconStateUtil.get_beacon_proposer_index(state, inclusion_slot);
        List<UnsignedLong> balances = state.getValidator_balances();
        UnsignedLong balance = balances.get(proposer_index);
        UnsignedLong reward =
            base_reward(state, index, previous_total_balance)
                .dividedBy(UnsignedLong.valueOf(Constants.ATTESTATION_INCLUSION_REWARD_QUOTIENT));
        balance = balance.plus(reward);
        balances.set(proposer_index, balance);
      }
    } catch (IllegalArgumentException e) {
      LOG.log(Level.WARN, "EpochProcessingException thrown in attestationInclusion()");
      throw new EpochProcessingException(e);
    }
  }

  /**
   * Rewards and penalties applied with respect to crosslinks. Spec:
   * https://github.com/ethereum/eth2.0-specs/blob/v0.1/specs/core/0_beacon-chain.md#justification-and-finalization
   *
   * @param state
   */
  public static void crosslinkRewards(BeaconState state, UnsignedLong previous_total_balance)
      throws EpochProcessingException {
    try {
      Long previous_epoch_start_slot =
          BeaconStateUtil.get_epoch_start_slot(BeaconStateUtil.get_previous_epoch(state))
              .longValue();
      Long current_epoch_start_slot =
          BeaconStateUtil.get_epoch_start_slot(BeaconStateUtil.get_current_epoch(state))
              .longValue();
      List<Long> slot_range =
          LongStream.range(previous_epoch_start_slot, current_epoch_start_slot)
              .boxed()
              .collect(Collectors.toList());
      for (Long slot : slot_range) {
        List<CrosslinkCommittee> crosslink_committees_at_slot =
            BeaconStateUtil.get_crosslink_committees_at_slot(
                state, UnsignedLong.valueOf(slot), false);
        for (CrosslinkCommittee crosslink_committee : crosslink_committees_at_slot) {
          Map<Integer, Integer> attester_indices =
              AttestationUtil.attesting_validators(state, crosslink_committee).stream()
                  .collect(Collectors.toMap(i -> i, i -> i));
          for (Integer index : crosslink_committee.getCommittee()) {
            List<UnsignedLong> balances = state.getValidator_balances();
            UnsignedLong balance = balances.get(index);
            if (attester_indices.containsKey(index)) {
              UnsignedLong reward =
                  base_reward(state, index, previous_total_balance)
                      .times(
                          AttestationUtil.get_total_attesting_balance(
                              state, crosslink_committee.getCommittee()))
                      .dividedBy(BeaconStateUtil.get_total_balance(state, crosslink_committee));
              balance = balance.plus(reward);
            } else {
              balance = balance.minus(base_reward(state, index, previous_total_balance));
            }
          }
        }
      }
    } catch (IllegalArgumentException e) {
      LOG.log(Level.WARN, "EpochProcessingException thrown in crosslinkRewards()");
      throw new EpochProcessingException(e);
    }
  }

  /**
   * Iterate through the validator registry and eject active validators with balance below
   * ``EJECTION_BALANCE``.
   *
   * @param state
   */
  public static void process_ejections(BeaconState state) throws EpochProcessingException {
    try {
      UnsignedLong currentEpoch = BeaconStateUtil.get_current_epoch(state);
      List<Integer> active_validator_indices =
          ValidatorsUtil.get_active_validator_indices(state.getValidator_registry(), currentEpoch);
      List<UnsignedLong> balances = state.getValidator_balances();

      active_validator_indices.forEach(
          index -> {
            if (balances.get(index).compareTo(UnsignedLong.valueOf(Constants.EJECTION_BALANCE))
                < 0) {
              BeaconStateUtil.exit_validator(state, index);
            }
          });
    } catch (IllegalArgumentException e) {
      LOG.log(Level.WARN, "EpochProcessingException thrown in process_ejections()");
      throw new EpochProcessingException(e);
    }
  }

  /**
   * This method updates various state variables before it is determined if the validator_regictry
   * needs to be updated
   *
   * @param state
   */
  public static void previousStateUpdates(BeaconState state) {
    UnsignedLong current_calculation_epoch = state.getCurrent_shuffling_epoch();
    state.setPrevious_shuffling_epoch(current_calculation_epoch);

    UnsignedLong current_epoch_start_shard = state.getCurrent_shuffling_start_shard();
    state.setPrevious_shuffling_start_shard(current_epoch_start_shard);

    Bytes32 current_epoch_seed = state.getCurrent_shuffling_seed();
    state.setPrevious_shuffling_seed(current_epoch_seed);
  }

  /**
   * This method determins if the validator registry should be updated
   *
   * @param state
   */
  public static Boolean shouldUpdateValidatorRegistry(BeaconState state)
      throws EpochProcessingException {
    try {
      Boolean check1 = false;
      UnsignedLong finalized_epoch = state.getFinalized_epoch();
      UnsignedLong validator_registry_update_epoch = state.getValidator_registry_update_epoch();
      check1 = finalized_epoch.compareTo(validator_registry_update_epoch) > 0;

      Boolean check2 = false;
      UnsignedLong committee_count = BeaconStateUtil.get_current_epoch_committee_count(state);
      List<Integer> comnmittee_range =
          IntStream.range(0, committee_count.intValue()).boxed().collect(Collectors.toList());
      UnsignedLong SHARD_COUNT = UnsignedLong.valueOf(Constants.SHARD_COUNT);
      for (Integer committee_offset : comnmittee_range) {
        UnsignedLong offset = UnsignedLong.valueOf(committee_offset);
        UnsignedLong shard = state.getCurrent_shuffling_start_shard().plus(offset).mod(SHARD_COUNT);
        UnsignedLong crosslink_epoch =
            state.getLatest_crosslinks().get(shard.intValue()).getEpoch();
        if (crosslink_epoch.compareTo(validator_registry_update_epoch) > 0) {
          check2 = true;
        } else {
          check2 = false;
          break;
        }
      }

      return check1 && check2;
    } catch (IllegalArgumentException e) {
      LOG.log(Level.WARN, "EpochProcessingException thrown in shouldUpdateValidatorRegistry()");
      throw new EpochProcessingException(e);
    }
  }

  /**
   * updates the validator registry and associated fields
   *
   * @param state
   */
  public static void update_validator_registry(BeaconState state) throws EpochProcessingException {
    try {
      UnsignedLong currentEpoch = BeaconStateUtil.get_current_epoch(state);
      List<Integer> active_validators =
          ValidatorsUtil.get_active_validator_indices(state.getValidator_registry(), currentEpoch);
      UnsignedLong total_balance = BeaconStateUtil.get_total_balance(state, active_validators);

      UnsignedLong max_balance_churn =
          BeaconStateUtil.max(
              UnsignedLong.valueOf(Constants.MAX_DEPOSIT_AMOUNT),
              total_balance.dividedBy(
                  UnsignedLong.valueOf((2 * Constants.MAX_BALANCE_CHURN_QUOTIENT))));

      // Activate validators within the allowable balance churn
      UnsignedLong balance_churn = UnsignedLong.ZERO;
      int index = 0;
      for (Validator validator : state.getValidator_registry()) {
        if (validator.getActivation_epoch().compareTo(Constants.FAR_FUTURE_EPOCH) == 0
            && state
                    .getValidator_balances()
                    .get(index)
                    .compareTo(UnsignedLong.valueOf(Constants.MAX_DEPOSIT_AMOUNT))
                >= 0) {
          balance_churn = balance_churn.plus(BeaconStateUtil.get_effective_balance(state, index));
          if (balance_churn.compareTo(max_balance_churn) > 0) break;
          BeaconStateUtil.activate_validator(state, validator, false);
        }
        index++;
      }

      // Exit validators within the allowable balance churn
      balance_churn = UnsignedLong.ZERO;
      index = 0;
      for (Validator validator : state.getValidator_registry()) {
        if (validator.getActivation_epoch().compareTo(Constants.FAR_FUTURE_EPOCH) == 0
            && validator.hasInitiatedExit()) {
          balance_churn =
              balance_churn.plus(BeaconStateUtil.get_effective_balance(state, validator));
          if (balance_churn.compareTo(max_balance_churn) > 0) break;
          BeaconStateUtil.exit_validator(state, index);
        }
        index++;
      }
      state.setValidator_registry_update_epoch(currentEpoch);
    } catch (IllegalArgumentException e) {
      LOG.log(Level.WARN, "EpochProcessingException thrown in update_validator_registry()");
      throw new EpochProcessingException(e);
    }
  }

  /**
   * this method updates state variables if the validator registry is updated
   *
   * @param state
   */
  public static void currentStateUpdatesAlt1(BeaconState state) throws EpochProcessingException {
    try {
      UnsignedLong epoch = BeaconStateUtil.get_next_epoch(state);
      state.setCurrent_shuffling_epoch(epoch);

      UnsignedLong SHARD_COUNT = UnsignedLong.valueOf(Constants.SHARD_COUNT);
      UnsignedLong committee_count = BeaconStateUtil.get_current_epoch_committee_count(state);
      UnsignedLong current_epoch_start_shard =
          state.getCurrent_shuffling_start_shard().plus(committee_count).mod(SHARD_COUNT);
      state.setCurrent_shuffling_start_shard(current_epoch_start_shard);

      Bytes32 current_epoch_seed = BeaconStateUtil.generate_seed(state, epoch);
      state.setCurrent_shuffling_seed(current_epoch_seed);
    } catch (IllegalArgumentException e) {
      LOG.log(Level.WARN, "EpochProcessingException thrown in currentStateUpdatesAlt1()");
      throw new EpochProcessingException(e);
    }
  }

  /**
   * this method updates state variables if the validator registry is NOT updated
   *
   * @param state
   */
  public static void currentStateUpdatesAlt2(BeaconState state) throws EpochProcessingException {
    try {
      UnsignedLong epochs_since_last_registry_update =
          BeaconStateUtil.get_current_epoch(state)
              .minus(state.getValidator_registry_update_epoch());
      if (epochs_since_last_registry_update.compareTo(UnsignedLong.ONE) > 0
          && BeaconStateUtil.is_power_of_two(epochs_since_last_registry_update)) {
        UnsignedLong next_epoch = BeaconStateUtil.get_next_epoch(state);
        state.setCurrent_shuffling_epoch(next_epoch);
        Bytes32 current_epoch_seed = BeaconStateUtil.generate_seed(state, next_epoch);
        state.setCurrent_shuffling_seed(current_epoch_seed);
      }
    } catch (IllegalArgumentException e) {
      LOG.log(Level.WARN, "EpochProcessingException thrown in currentStateUpdatesAlt2()");
      throw new EpochProcessingException(e);
    }
  }

  /**
   * process the validator penalties and exits
   *
   * @param state
   */
  public static void process_penalties_and_exits(BeaconState state)
      throws EpochProcessingException {
    try {
      UnsignedLong currentEpoch = BeaconStateUtil.get_current_epoch(state);
      List<Integer> active_validators =
          ValidatorsUtil.get_active_validator_indices(state.getValidator_registry(), currentEpoch);

      UnsignedLong total_balance = BeaconStateUtil.get_total_balance(state, active_validators);

      ListIterator<Validator> itr = state.getValidator_registry().listIterator();
      while (itr.hasNext()) {
        int index = itr.nextIndex();
        Validator validator = itr.next();

        if (validator.isSlashed()
            && currentEpoch.equals(
                validator
                    .getWithdrawal_epoch()
                    .minus(UnsignedLong.valueOf(Constants.LATEST_SLASHED_EXIT_LENGTH / 2)))) {
          int epoch_index = currentEpoch.intValue() % Constants.LATEST_SLASHED_EXIT_LENGTH;

          UnsignedLong total_at_start =
              state
                  .getLatest_slashed_balances()
                  .get((epoch_index + 1) % Constants.LATEST_SLASHED_EXIT_LENGTH);
          UnsignedLong total_at_end = state.getLatest_slashed_balances().get(epoch_index);
          UnsignedLong total_penalties = total_at_end.minus(total_at_start);
          UnsignedLong penalty =
              BeaconStateUtil.get_effective_balance(state, validator)
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
      Collections.sort(eligible_validators, Comparator.comparing(Validator::getExit_epoch));

      int withdrawn_so_far = 0;
      for (Validator validator : eligible_validators) {
        if (withdrawn_so_far >= Constants.MAX_EXIT_DEQUEUES_PER_EPOCH) break;
        BeaconStateUtil.prepare_validator_for_withdrawal(
            state, state.getValidator_registry().indexOf(validator));
        withdrawn_so_far += 1;
      }
    } catch (IllegalArgumentException e) {
      LOG.log(Level.WARN, "EpochProcessingException thrown in process_penalties_and_exits()");
      throw new EpochProcessingException(e);
    }
  }

  static boolean eligible(BeaconState state, Validator validator) {
    UnsignedLong currentEpoch = BeaconStateUtil.get_current_epoch(state);
    if (validator.getWithdrawal_epoch().compareTo(Constants.FAR_FUTURE_EPOCH) != 0) {
      return false;
    } else {
      return currentEpoch.compareTo(
              validator
                  .getExit_epoch()
                  .plus(UnsignedLong.valueOf(Constants.MIN_VALIDATOR_WITHDRAWABILITY_DELAY)))
          >= 0;
    }
  }

  /**
   * perform the final state updates for epoch processing
   *
   * @param state
   */
  public static void finalUpdates(BeaconState state) throws EpochProcessingException {
    try {
      UnsignedLong current_epoch = BeaconStateUtil.get_current_epoch(state);
      UnsignedLong next_epoch = BeaconStateUtil.get_next_epoch(state);
      UnsignedLong ENTRY_EXIT_DELAY = UnsignedLong.valueOf(Constants.ACTIVATION_EXIT_DELAY);
      UnsignedLong LATEST_INDEX_ROOTS_LENGTH =
          UnsignedLong.valueOf(Constants.LATEST_ACTIVE_INDEX_ROOTS_LENGTH);
      UnsignedLong LATEST_RANDAO_MIXES_LENGTH =
          UnsignedLong.valueOf(Constants.LATEST_RANDAO_MIXES_LENGTH);
      UnsignedLong LATEST_PENALIZED_EXIT_LENGTH =
          UnsignedLong.valueOf(Constants.LATEST_SLASHED_EXIT_LENGTH);

      // update hash tree root
      int index = next_epoch.plus(ENTRY_EXIT_DELAY).mod(LATEST_INDEX_ROOTS_LENGTH).intValue();
      List<Bytes32> latest_index_roots = state.getLatest_active_index_roots();
      Bytes32 root =
          HashTreeUtil.integerListHashTreeRoot(
              ValidatorsUtil.get_active_validator_indices(
                  state.getValidator_registry(), next_epoch.plus(ENTRY_EXIT_DELAY)));
      latest_index_roots.set(index, root);

      // update latest penalized balances
      index = next_epoch.mod(LATEST_PENALIZED_EXIT_LENGTH).intValue();
      List<UnsignedLong> latest_penalized_balances = state.getLatest_slashed_balances();
      UnsignedLong balance =
          latest_penalized_balances.get(current_epoch.mod(LATEST_PENALIZED_EXIT_LENGTH).intValue());
      latest_penalized_balances.set(index, balance);

      // update latest randao mixes
      List<Bytes32> latest_randao_mixes = state.getLatest_randao_mixes();
      index = next_epoch.mod(LATEST_RANDAO_MIXES_LENGTH).intValue();
      Bytes32 randao_mix = BeaconStateUtil.get_randao_mix(state, current_epoch);
      latest_randao_mixes.set(index, randao_mix);

      // remove old attestations
      List<PendingAttestation> pending_attestations = state.getLatest_attestations();
      List<PendingAttestation> remaining_attestations = new ArrayList<>();
      for (PendingAttestation attestation : pending_attestations) {
        if (!(BeaconStateUtil.slot_to_epoch(attestation.getData().getSlot())
                .compareTo(current_epoch)
            < 0)) {
          remaining_attestations.add(attestation);
        }
      }
      state.setLatest_attestations(remaining_attestations);
    } catch (IllegalArgumentException e) {
      LOG.log(Level.WARN, "EpochProcessingException thrown in finalUpdates()");
      throw new EpochProcessingException(e);
    }
  }

  /**
   * calculates the base reward for the supplied validator index
   *
   * @param state
   * @param index
   * @param previous_total_balance
   * @return
   */
  static UnsignedLong base_reward(
      BeaconState state, int index, UnsignedLong previous_total_balance) {
    UnsignedLong base_reward_quotient =
        BeaconStateUtil.integer_squareroot(previous_total_balance)
            .dividedBy(UnsignedLong.valueOf(Constants.BASE_REWARD_QUOTIENT));
    return BeaconStateUtil.get_effective_balance(state, index)
        .dividedBy(base_reward_quotient)
        .dividedBy(UnsignedLong.valueOf(5L));
  }

  /**
   * calculates the inactivity penalty for the supplied validator index
   *
   * @param state
   * @param index
   * @param epochs_since_finality
   * @param previous_total_balance
   * @return
   */
  static UnsignedLong inactivity_penality(
      BeaconState state,
      int index,
      UnsignedLong epochs_since_finality,
      UnsignedLong previous_total_balance) {
    return base_reward(state, index, previous_total_balance)
        .plus(BeaconStateUtil.get_effective_balance(state, index))
        .times(epochs_since_finality)
        .dividedBy(UnsignedLong.valueOf(Constants.INACTIVITY_PENALTY_QUOTIENT))
        .dividedBy(UnsignedLong.valueOf(2L));
  }
}
