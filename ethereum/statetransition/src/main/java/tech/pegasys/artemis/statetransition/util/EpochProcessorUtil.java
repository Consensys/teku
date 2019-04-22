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

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes32;
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
    long next_epoch = BeaconStateUtil.get_next_epoch(state);
    if (next_epoch % Constants.EPOCHS_PER_ETH1_VOTING_PERIOD == 0) {
      List<Eth1DataVote> eth1_data_votes = state.getEth1_data_votes();
      for (Eth1DataVote eth1_data_vote : eth1_data_votes) {
        if (eth1_data_vote.getVote_count() * 2
            > Constants.EPOCHS_PER_ETH1_VOTING_PERIOD * Constants.SLOTS_PER_EPOCH) {
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
      long current_epoch = BeaconStateUtil.get_current_epoch(state);
      long previous_epoch = BeaconStateUtil.get_previous_epoch(state);

      // Get previous and current epoch total balances
      List<Integer> current_active_validators =
          ValidatorsUtil.get_active_validator_indices(state.getValidator_registry(), current_epoch);
      List<Integer> previous_active_validators =
          ValidatorsUtil.get_active_validator_indices(
              state.getValidator_registry(), previous_epoch);
      long current_total_balance =
          BeaconStateUtil.get_total_balance(state, current_active_validators);
      long previous_total_balance =
          BeaconStateUtil.get_total_balance(state, previous_active_validators);

      // Update justification bitfield
      long new_justified_epoch = state.getJustified_epoch();
      long justification_bitfield = state.getJustification_bitfield();
      justification_bitfield = justification_bitfield << 1;

      if (AttestationUtil.get_previous_epoch_boundary_attesting_balance(state) * 3
          >= previous_total_balance * 2) {
        justification_bitfield = justification_bitfield | 2;
        new_justified_epoch = previous_epoch;
      }
      if (AttestationUtil.get_current_epoch_boundary_attesting_balance(state) * 3
          >= current_total_balance * 2) {
        justification_bitfield = justification_bitfield | 1;
        new_justified_epoch = current_epoch;
      }

      state.setJustification_bitfield(justification_bitfield);

      // Update last finalized epoch if possible
      long previous_justified_epoch = state.getPrevious_justified_epoch();
      long justified_epoch = state.getJustified_epoch();

      if (justification_bitfield >>> 1 % 8 == 7 && previous_justified_epoch == previous_epoch - 2) {
        state.setFinalized_epoch(previous_justified_epoch);
      }
      if (justification_bitfield >>> 1 % 4 == 3 && previous_justified_epoch == previous_epoch - 1) {
        state.setFinalized_epoch(previous_justified_epoch);
      }
      if (justification_bitfield % 8 == 7 && justified_epoch == previous_epoch - 1) {
        state.setFinalized_epoch(justified_epoch);
      }
      if (justification_bitfield % 4 == 3 && justified_epoch == previous_epoch) {
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
      long previous_epoch = BeaconStateUtil.get_previous_epoch(state);
      long next_epoch = BeaconStateUtil.get_next_epoch(state);

      for (long curr_slot = BeaconStateUtil.get_epoch_start_slot(previous_epoch);
          curr_slot < BeaconStateUtil.get_epoch_start_slot(next_epoch);
          curr_slot = curr_slot + 1) {
        LOG.log(Level.DEBUG, "current slot: " + curr_slot);
        List<CrosslinkCommittee> crosslink_committees_at_slot =
            BeaconStateUtil.get_crosslink_committees_at_slot(state, curr_slot);
        for (CrosslinkCommittee committee : crosslink_committees_at_slot) {
          LOG.log(Level.DEBUG, "Committee Shard: " + committee.getShard());
          LOG.log(
              Level.DEBUG,
              "Total Attesting Balance: "
                  + AttestationUtil.total_attesting_balance(state, committee));
          LOG.log(Level.DEBUG, "committee: " + committee.getCommittee());
          LOG.log(
              Level.DEBUG, "Total Balance: " + BeaconStateUtil.get_total_balance(state, committee));
          if (AttestationUtil.total_attesting_balance(state, committee) * 3L
              >= BeaconStateUtil.get_total_balance(state, committee) * 2L) {
            long shard = committee.getShard();
            state
                .getLatest_crosslinks()
                .set(
                    toIntExact(shard) % Constants.SHARD_COUNT,
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

  static Function<Integer, Long> apply_inactivity_penalty(
      BeaconState state, long epochs_since_finality, long previous_total_balance) {
    return index ->
        get_inactivity_penality(state, index, epochs_since_finality, previous_total_balance);
  }

  static Function<Integer, Long> apply_base_penalty(
      BeaconState state, long epochs_since_finality, long previous_total_balance) {
    return index -> base_reward(state, index, previous_total_balance);
  }

  static Function<Integer, Long> apply_inactivity_base_penalty(
      BeaconState state, long epochs_since_finality, long previous_total_balance) {
    return index ->
        2L * get_inactivity_penality(state, index, epochs_since_finality, previous_total_balance)
            + base_reward(state, index, previous_total_balance);
  }

  static Function<Integer, Long> apply_inclusion_base_penalty(
      BeaconState state, long epochs_since_finality, long previous_total_balance) {
    return index -> {
      long inclusion_distance = 0;
      try {
        inclusion_distance = AttestationUtil.inclusion_distance(state, index);
      } catch (Exception e) {
        LOG.log(Level.WARN, "apply_inclusion_base_penalty(): " + e);
      }
      return base_reward(state, index, previous_total_balance)
          * Constants.MIN_ATTESTATION_INCLUSION_DELAY
          / inclusion_distance;
    };
  }

  // Helper method for justificationAndFinalization()
  static void case_one_penalties_and_rewards(
      BeaconState state,
      List<Long> balances,
      long previous_total_balance,
      long previous_balance,
      List<Integer> previous_indices) {
    long reward_delta = 0;

    // make a list of integers from 0 to numberOfValidators
    List<Integer> missing_indices =
        IntStream.range(0, state.getValidator_registry().size())
            .boxed()
            .collect(Collectors.toList());

    // apply rewards to validator indices in the list
    for (int index : previous_indices) {
      reward_delta =
          base_reward(state, index, previous_total_balance)
              * previous_balance
              / previous_total_balance;
      apply_penalty_or_reward(balances, index, reward_delta, true);
      missing_indices.remove(missing_indices.indexOf(index));
    }

    // apply penalties to active validator indices not in the list
    for (int index : missing_indices) {
      if (ValidatorsUtil.is_active_validator_index(
          state, index, BeaconStateUtil.get_current_epoch(state))) {
        reward_delta =
            base_reward(state, index, previous_total_balance)
                * previous_balance
                / previous_total_balance;
        apply_penalty_or_reward(balances, index, reward_delta, false);
      }
    }
  }

  // Helper method for justificationAndFinalization()
  static void case_two_penalties(
      BeaconState state,
      List<Long> balances,
      List<Integer> validator_indices,
      Function<Integer, Long> penalty) {

    long penalty_delta = 0;
    for (int index : validator_indices) {
      penalty_delta = penalty.apply(index);
      apply_penalty_or_reward(balances, index, penalty_delta, false);
    }
  }

  // Helper method for justificationAndFinalization()
  static void apply_penalty_or_reward(
      List<Long> balances, int index, long delta_balance, Boolean reward) {
    long balance = balances.get(index);
    if (reward) {
      // TODO: add check for overflow
      balance = balance + delta_balance;
    } else {
      // TODO: add check for underflow
      balance = balance - delta_balance;
    }
    balances.set(index, balance);
  }

  /**
   * Rewards and penalties applied with respect to justification and finalization. Spec:
   * https://github.com/ethereum/eth2.0-specs/blob/v0.1/specs/core/0_beacon-chain.md#justification-and-finalization
   *
   * @param state
   */
  public static void justificationAndFinalization(BeaconState state, long previous_total_balance)
      throws EpochProcessingException {
    try {
      long epochs_since_finality =
          BeaconStateUtil.get_next_epoch(state) - state.getFinalized_epoch();
      List<Long> balances = state.getValidator_balances();

      // Case 1: epochs_since_finality <= 4:
      if (epochs_since_finality <= 4L) {
        // Expected FFG source
        long previous_balance = AttestationUtil.get_previous_epoch_attesting_balance(state);
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
        long reward_delta = 0;
        previous_indices = AttestationUtil.get_previous_epoch_attester_indices(state);
        for (int index : previous_indices) {
          long inclusion_distance = AttestationUtil.inclusion_distance(state, index);
          reward_delta =
              base_reward(state, index, previous_total_balance)
                  * Constants.MIN_ATTESTATION_INCLUSION_DELAY
                  / inclusion_distance;
          apply_penalty_or_reward(balances, index, reward_delta, true);
        }

        // Case 2: epochs_since_finality > 4:
      } else if (epochs_since_finality > 4L) {

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
  public static void attestionInclusion(BeaconState state, long previous_total_balance)
      throws EpochProcessingException {
    try {
      List<Integer> previous_indices = AttestationUtil.get_previous_epoch_attester_indices(state);
      for (int index : previous_indices) {
        long inclusion_slot = AttestationUtil.inclusion_slot(state, index);
        int proposer_index = BeaconStateUtil.get_beacon_proposer_index(state, inclusion_slot);
        List<Long> balances = state.getValidator_balances();
        long balance = balances.get(proposer_index);
        long reward =
            base_reward(state, index, previous_total_balance)
                / Constants.ATTESTATION_INCLUSION_REWARD_QUOTIENT;
        balance += reward;
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
  public static void crosslinkRewards(BeaconState state, long previous_total_balance)
      throws EpochProcessingException {
    try {
      Long previous_epoch_start_slot =
          BeaconStateUtil.get_epoch_start_slot(BeaconStateUtil.get_previous_epoch(state));
      Long current_epoch_start_slot =
          BeaconStateUtil.get_epoch_start_slot(BeaconStateUtil.get_current_epoch(state));
      List<Long> slot_range =
          LongStream.range(previous_epoch_start_slot, current_epoch_start_slot)
              .boxed()
              .collect(Collectors.toList());
      for (Long slot : slot_range) {
        List<CrosslinkCommittee> crosslink_committees_at_slot =
            BeaconStateUtil.get_crosslink_committees_at_slot(state, slot, false);
        for (CrosslinkCommittee crosslink_committee : crosslink_committees_at_slot) {
          Map<Integer, Integer> attester_indices =
              AttestationUtil.attesting_validators(state, crosslink_committee).stream()
                  .collect(Collectors.toMap(i -> i, i -> i));
          for (Integer index : crosslink_committee.getCommittee()) {
            List<Long> balances = state.getValidator_balances();
            long balance = balances.get(index);
            if (attester_indices.containsKey(index)) {
              long reward =
                  base_reward(state, index, previous_total_balance)
                      * AttestationUtil.get_total_attesting_balance(
                          state, crosslink_committee.getCommittee())
                      / BeaconStateUtil.get_total_balance(state, crosslink_committee);
              balance += reward;
            } else {
              balance -= base_reward(state, index, previous_total_balance);
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
      long currentEpoch = BeaconStateUtil.get_current_epoch(state);
      List<Integer> active_validator_indices =
          ValidatorsUtil.get_active_validator_indices(state.getValidator_registry(), currentEpoch);
      List<Long> balances = state.getValidator_balances();

      active_validator_indices.forEach(
          index -> {
            if (balances.get(index).compareTo(Constants.EJECTION_BALANCE) < 0) {
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
    long current_calculation_epoch = state.getCurrent_shuffling_epoch();
    state.setPrevious_shuffling_epoch(current_calculation_epoch);

    long current_epoch_start_shard = state.getCurrent_shuffling_start_shard();
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
      long finalized_epoch = state.getFinalized_epoch();
      long validator_registry_update_epoch = state.getValidator_registry_update_epoch();
      check1 = finalized_epoch > validator_registry_update_epoch;

      Boolean check2 = false;
      long committee_count = BeaconStateUtil.get_current_epoch_committee_count(state);
      List<Integer> comnmittee_range =
          IntStream.range(0, toIntExact(committee_count)).boxed().collect(Collectors.toList());
      long SHARD_COUNT = Constants.SHARD_COUNT;
      for (Integer committee_offset : comnmittee_range) {
        long shard = (state.getCurrent_shuffling_start_shard() + committee_offset) % SHARD_COUNT;
        long crosslink_epoch = state.getLatest_crosslinks().get(toIntExact(shard)).getEpoch();
        if (crosslink_epoch > validator_registry_update_epoch) {
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
      long currentEpoch = BeaconStateUtil.get_current_epoch(state);
      List<Integer> active_validators =
          ValidatorsUtil.get_active_validator_indices(state.getValidator_registry(), currentEpoch);
      long total_balance = BeaconStateUtil.get_total_balance(state, active_validators);

      long max_balance_churn =
          Math.max(
              Constants.MAX_DEPOSIT_AMOUNT,
              total_balance / (2 * Constants.MAX_BALANCE_CHURN_QUOTIENT));

      // Activate validators within the allowable balance churn
      long balance_churn = 0;
      int index = 0;
      for (Validator validator : state.getValidator_registry()) {
        if (validator.getActivation_epoch() == Constants.FAR_FUTURE_EPOCH
            && state.getValidator_balances().get(index) >= Constants.MAX_DEPOSIT_AMOUNT) {
          balance_churn = balance_churn + BeaconStateUtil.get_effective_balance(state, index);
          if (balance_churn > max_balance_churn) break;
          BeaconStateUtil.activate_validator(state, validator, false);
        }
        index++;
      }

      // Exit validators within the allowable balance churn
      balance_churn = 0;
      index = 0;
      for (Validator validator : state.getValidator_registry()) {
        if (validator.getActivation_epoch() == Constants.FAR_FUTURE_EPOCH
            && validator.hasInitiatedExit()) {
          balance_churn = balance_churn + BeaconStateUtil.get_effective_balance(state, validator);
          if (balance_churn > max_balance_churn) break;
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
      long epoch = BeaconStateUtil.get_next_epoch(state);
      state.setCurrent_shuffling_epoch(epoch);

      long SHARD_COUNT = Constants.SHARD_COUNT;
      long committee_count = BeaconStateUtil.get_current_epoch_committee_count(state);
      long current_epoch_start_shard =
          (state.getCurrent_shuffling_start_shard() + committee_count) % SHARD_COUNT;
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
      long epochs_since_last_registry_update =
          BeaconStateUtil.get_current_epoch(state) - state.getValidator_registry_update_epoch();
      if (epochs_since_last_registry_update > 1
          && BeaconStateUtil.is_power_of_two(epochs_since_last_registry_update)) {
        long next_epoch = BeaconStateUtil.get_next_epoch(state);
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
      long currentEpoch = BeaconStateUtil.get_current_epoch(state);
      List<Integer> active_validators =
          ValidatorsUtil.get_active_validator_indices(state.getValidator_registry(), currentEpoch);

      long total_balance = BeaconStateUtil.get_total_balance(state, active_validators);

      ListIterator<Validator> itr = state.getValidator_registry().listIterator();
      while (itr.hasNext()) {
        int index = itr.nextIndex();
        Validator validator = itr.next();

        if (validator.isSlashed()
            && currentEpoch
                == validator.getWithdrawal_epoch() - Constants.LATEST_SLASHED_EXIT_LENGTH / 2) {
          int epoch_index = toIntExact(currentEpoch) % Constants.LATEST_SLASHED_EXIT_LENGTH;

          long total_at_start =
              state
                  .getLatest_slashed_balances()
                  .get((epoch_index + 1) % Constants.LATEST_SLASHED_EXIT_LENGTH);
          long total_at_end = state.getLatest_slashed_balances().get(epoch_index);
          long total_penalties = total_at_end - total_at_start;
          long penalty =
              BeaconStateUtil.get_effective_balance(state, validator)
                  * Math.min(total_penalties * 3, total_balance)
                  / total_balance;
          state
              .getValidator_balances()
              .set(index, state.getValidator_balances().get(index) - penalty);
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
    long currentEpoch = BeaconStateUtil.get_current_epoch(state);
    if (validator.getWithdrawal_epoch() != Constants.FAR_FUTURE_EPOCH) {
      return false;
    } else {
      return currentEpoch
          >= validator.getExit_epoch() + Constants.MIN_VALIDATOR_WITHDRAWABILITY_DELAY;
    }
  }

  /**
   * perform the final state updates for epoch processing
   *
   * @param state
   */
  public static void finalUpdates(BeaconState state) throws EpochProcessingException {
    try {
      long current_epoch = BeaconStateUtil.get_current_epoch(state);
      long next_epoch = BeaconStateUtil.get_next_epoch(state);
      long ENTRY_EXIT_DELAY = Constants.ACTIVATION_EXIT_DELAY;
      long LATEST_INDEX_ROOTS_LENGTH = Constants.LATEST_ACTIVE_INDEX_ROOTS_LENGTH;
      long LATEST_RANDAO_MIXES_LENGTH = Constants.LATEST_RANDAO_MIXES_LENGTH;
      long LATEST_PENALIZED_EXIT_LENGTH = Constants.LATEST_SLASHED_EXIT_LENGTH;

      // update hash tree root
      long index = (next_epoch + ENTRY_EXIT_DELAY) % LATEST_INDEX_ROOTS_LENGTH;
      List<Bytes32> latest_index_roots = state.getLatest_active_index_roots();
      Bytes32 root =
          HashTreeUtil.integerListHashTreeRoot(
              ValidatorsUtil.get_active_validator_indices(
                  state.getValidator_registry(), next_epoch + ENTRY_EXIT_DELAY));
      latest_index_roots.set(toIntExact(index), root);

      // update latest penalized balances
      index = next_epoch % LATEST_PENALIZED_EXIT_LENGTH;
      List<Long> latest_penalized_balances = state.getLatest_slashed_balances();
      long balance =
          latest_penalized_balances.get(toIntExact(current_epoch % LATEST_PENALIZED_EXIT_LENGTH));
      latest_penalized_balances.set(toIntExact(index), balance);

      // update latest randao mixes
      List<Bytes32> latest_randao_mixes = state.getLatest_randao_mixes();
      index = next_epoch % LATEST_RANDAO_MIXES_LENGTH;
      Bytes32 randao_mix = BeaconStateUtil.get_randao_mix(state, current_epoch);
      latest_randao_mixes.set(toIntExact(index), randao_mix);

      // remove old attestations
      List<PendingAttestation> pending_attestations = state.getLatest_attestations();
      List<PendingAttestation> remaining_attestations = new ArrayList<>();
      for (PendingAttestation attestation : pending_attestations) {
        if (!(BeaconStateUtil.slot_to_epoch(attestation.getData().getSlot()) < current_epoch)) {
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
  static long base_reward(BeaconState state, int index, long previous_total_balance) {
    long base_reward_quotient =
        BeaconStateUtil.integer_squareroot(previous_total_balance) / Constants.BASE_REWARD_QUOTIENT;
    return BeaconStateUtil.get_effective_balance(state, index) / base_reward_quotient / 5L;
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
  static long get_inactivity_penality(
      BeaconState state, int index, long epochs_since_finality, long previous_total_balance) {
    return base_reward(state, index, previous_total_balance)
        + BeaconStateUtil.get_effective_balance(state, index)
            * epochs_since_finality
            / Constants.INACTIVITY_PENALTY_QUOTIENT
            / 2L;
  }
}
