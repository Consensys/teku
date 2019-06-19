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
import static tech.pegasys.artemis.datastructures.Constants.ACTIVATION_EXIT_DELAY;
import static tech.pegasys.artemis.datastructures.Constants.BASE_REWARDS_PER_EPOCH;
import static tech.pegasys.artemis.datastructures.Constants.BASE_REWARD_FACTOR;
import static tech.pegasys.artemis.datastructures.Constants.BASE_REWARD_PER_EPOCH;
import static tech.pegasys.artemis.datastructures.Constants.EFFECTIVE_BALANCE_INCREMENT;
import static tech.pegasys.artemis.datastructures.Constants.EJECTION_BALANCE;
import static tech.pegasys.artemis.datastructures.Constants.FAR_FUTURE_EPOCH;
import static tech.pegasys.artemis.datastructures.Constants.GENESIS_EPOCH;
import static tech.pegasys.artemis.datastructures.Constants.INACTIVITY_PENALTY_QUOTIENT;
import static tech.pegasys.artemis.datastructures.Constants.LATEST_ACTIVE_INDEX_ROOTS_LENGTH;
import static tech.pegasys.artemis.datastructures.Constants.LATEST_RANDAO_MIXES_LENGTH;
import static tech.pegasys.artemis.datastructures.Constants.LATEST_SLASHED_EXIT_LENGTH;
import static tech.pegasys.artemis.datastructures.Constants.MAX_EFFECTIVE_BALANCE;
import static tech.pegasys.artemis.datastructures.Constants.MIN_ATTESTATION_INCLUSION_DELAY;
import static tech.pegasys.artemis.datastructures.Constants.MIN_EPOCHS_TO_INACTIVITY_PENALTY;
import static tech.pegasys.artemis.datastructures.Constants.PROPOSER_REWARD_QUOTIENT;
import static tech.pegasys.artemis.datastructures.Constants.SHARD_COUNT;
import static tech.pegasys.artemis.datastructures.Constants.SLOTS_PER_EPOCH;
import static tech.pegasys.artemis.datastructures.Constants.SLOTS_PER_HISTORICAL_ROOT;
import static tech.pegasys.artemis.datastructures.Constants.ZERO_HASH;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.exit_validator;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.generate_seed;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_attestation_participants;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_beacon_proposer_index;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_block_root;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_crosslink_committees_at_slot;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_current_epoch;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_current_epoch_committee_count;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_delayed_activation_exit_epoch;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_effective_balance;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_epoch_committee_count;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_epoch_start_slot;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_previous_epoch;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_randao_mix;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_total_balance;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.initiate_validator_exit;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.integer_squareroot;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.is_power_of_two;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.max;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.min;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.prepare_validator_for_withdrawal;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.slot_to_epoch;
import static tech.pegasys.artemis.datastructures.util.ValidatorsUtil.get_active_validator_indices;
import static tech.pegasys.artemis.datastructures.util.ValidatorsUtil.is_active_validator;

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.blocks.Eth1DataVote;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.BeaconStateWithCache;
import tech.pegasys.artemis.datastructures.state.Crosslink;
import tech.pegasys.artemis.datastructures.state.CrosslinkCommittee;
import tech.pegasys.artemis.datastructures.state.HistoricalBatch;
import tech.pegasys.artemis.datastructures.state.PendingAttestation;
import tech.pegasys.artemis.datastructures.state.Validator;
import tech.pegasys.artemis.datastructures.util.BeaconStateUtil;
import tech.pegasys.artemis.util.alogger.ALogger;
import tech.pegasys.artemis.util.bitwise.BitwiseOps;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil.SSZTypes;

public final class EpochProcessorUtil {

  private static final ALogger LOG = new ALogger(EpochProcessorUtil.class.getName());

  /**
   * @param state
   * @return
   */
  public static UnsignedLong get_current_total_balance(BeaconState state) {
    return get_total_balance(
            state,
            get_active_validator_indices(state.getValidator_registry(), get_current_epoch(state)));
  }

  /**
   * @param state
   * @return
   */
  public static UnsignedLong get_previous_total_balance(BeaconState state) {
    BeaconStateWithCache stateWithCash = (BeaconStateWithCache) state;
    if (stateWithCash.getPreviousTotalBalance().compareTo(UnsignedLong.MAX_VALUE) < 0) {

      return stateWithCash.getPreviousTotalBalance();
    } else {
      UnsignedLong previousTotalBalance =
              get_total_balance(
                      state,
                      get_active_validator_indices(
                              state.getValidator_registry(), get_previous_epoch(state)));
      stateWithCash.setPreviousTotalBalance(previousTotalBalance);
      return previousTotalBalance;
    }
  }

  /**
   * Returns the union of validator index sets, where the sets are the attestation participants of
   * attestations passed in TODO: the union part takes O(n^2) time, where n is the number of
   * validators. OPTIMIZE
   */
  public static List<Integer> get_attesting_indices(
          BeaconState state, List<PendingAttestation> attestations) throws IllegalArgumentException {

    TreeSet<Integer> output = new TreeSet<>();
    for (PendingAttestation a : attestations) {
      output.addAll(get_attestation_participants(state, a.getData(), a.getAggregation_bitfield()));
    }
    List<Integer> output_list = new ArrayList<>(output);
    return output_list;
  }

  /**
   * @param state
   * @param attestations
   * @return
   */
  public static UnsignedLong get_attesting_balance(
          BeaconState state, List<PendingAttestation> attestations) {
    return get_total_balance(state, get_attesting_indices(state, attestations));
  }

  /**
   * @param state
   * @return
   */
  public static List<PendingAttestation> get_current_epoch_boundary_attestations(
          BeaconState state) {
    List<PendingAttestation> attestations = new ArrayList<>();
    for (PendingAttestation a : state.getCurrent_epoch_attestations()) {
      if (a.getData()
              .getTarget_root()
              .equals(get_block_root(state, get_epoch_start_slot(get_current_epoch(state))))) {
        attestations.add(a);
      }
    }
    return attestations;
  }

  /**
   * @param state
   * @return
   */
  public static List<PendingAttestation> get_previous_epoch_boundary_attestations(
          BeaconState state) {
    List<PendingAttestation> attestations = new ArrayList<>();
    for (PendingAttestation a : state.getPrevious_epoch_attestations()) {
      if (a.getData()
              .getTarget_root()
              .equals(get_block_root(state, get_epoch_start_slot(get_previous_epoch(state))))) {
        attestations.add(a);
      }
    }
    return attestations;
  }

  /**
   * @param state
   * @return
   */
  public static List<PendingAttestation> get_previous_epoch_matching_head_attestations(
          BeaconState state) {
    List<PendingAttestation> attestations = new ArrayList<>();
    for (PendingAttestation a : state.getPrevious_epoch_attestations()) {
      if (a.getData().getBeacon_block_root().equals(get_block_root(state, a.getData().getSlot()))) {
        attestations.add(a);
      }
    }
    return attestations;
  }

  /**
   * @param state
   * @param shard
   * @return
   */
  public static MutablePair<Bytes32, List<Integer>> get_winning_root_and_participants(
          BeaconState state, UnsignedLong shard) {

    Crosslink shardLatestCrosslink = state.getLatest_crosslinks().get(shard.intValue());

    List<PendingAttestation> valid_attestations = new ArrayList<>();
    List<Bytes32> all_roots = new ArrayList<>();
    for (PendingAttestation a : state.getCurrent_epoch_attestations()) {
      if (a.getData().getCrosslink().equals(shardLatestCrosslink)) {
        valid_attestations.add(a);
        all_roots.add(a.getData().getCrosslink_data_root());
      }
    }
    for (PendingAttestation a : state.getPrevious_epoch_attestations()) {
      if (a.getData().getCrosslink().equals(shardLatestCrosslink)) {
        valid_attestations.add(a);
        all_roots.add(a.getData().getCrosslink_data_root());
      }
    }

    if (all_roots.isEmpty()) {
      return new MutablePair<>(ZERO_HASH, new ArrayList<>());
    }

    // TODO: make sure ties broken in favor of lexicographically higher hash
    UnsignedLong max = null;
    Bytes32 winning_root = null;
    List<PendingAttestation> attestations = null;
    for (Bytes32 root : all_roots) {
      List<PendingAttestation> candidateAttestations =
              get_attestations_for(root, valid_attestations);
      UnsignedLong value = get_attesting_balance(state, candidateAttestations);
      if (max == null) {
        max = value;
        winning_root = root;
        attestations = candidateAttestations;
      } else {
        if (value.compareTo(max) < 0) {
          max = value;
          winning_root = root;
          attestations = candidateAttestations;
        }
      }
    }

    return new MutablePair<>(winning_root, get_attesting_indices(state, attestations));
  }

  /**
   * Helper function for get_winning_root_and_participants.
   *
   * @param root
   * @param valid_attestations
   * @return
   */
  public static List<PendingAttestation> get_attestations_for(
          Bytes32 root, List<PendingAttestation> valid_attestations) {
    List<PendingAttestation> attestations = new ArrayList<>();
    for (PendingAttestation a : valid_attestations) {
      if (a.getData().getCrosslink_data_root().equals(root)) {
        attestations.add(a);
      }
    }
    return attestations;
  }

  /**
   * @param state
   * @param validator_index
   * @return
   */
  public static PendingAttestation earliest_attestation(BeaconState state, int validator_index) {
    List<PendingAttestation> attestations = new ArrayList<>();
    for (PendingAttestation a : state.getPrevious_epoch_attestations()) {
      if (get_attestation_participants(state, a.getData(), a.getAggregation_bitfield())
              .contains(validator_index)) {
        attestations.add(a);
      }
    }

    return Collections.min(
            attestations, Comparator.comparing(PendingAttestation::getInclusion_slot));
  }

  /**
   * @param state
   * @param validator_index
   * @return
   */
  public static UnsignedLong inclusion_slot(BeaconState state, int validator_index) {
    return earliest_attestation(state, validator_index).getInclusion_slot();
  }

  /**
   * @param state
   * @param validator_index
   * @return
   */
  public static UnsignedLong inclusion_distance(BeaconState state, int validator_index) {
    PendingAttestation a = earliest_attestation(state, validator_index);
    return a.getInclusion_slot().minus(a.getData().getSlot());
  }

  // @v0.7.1
  public static void process_justification_and_finalization(BeaconState state){
    if (get_current_epoch(state).compareTo(UnsignedLong.valueOf(GENESIS_EPOCH).plus(UnsignedLong.ONE)) <= 0) {
      return;
    }

    UnsignedLong previous_epoch = get_previous_epoch(state);
    UnsignedLong current_epoch = get_current_epoch(state);
    UnsignedLong old_previous_justified_epoch = state.getPrevious_justified_epoch();
    UnsignedLong old_current_justified_epoch = state.getCurrent_justified_epoch();

    // Process justifications
    state.setPrevious_justified_epoch(state.getCurrent_justified_epoch());
    state.setPrevious_justified_root(state.getCurrent_justified_root());
    UnsignedLong justification_bitfield = state.getJustification_bitfield();
    justification_bitfield = BitwiseOps.leftShift(justification_bitfield, 1).mod(UnsignedLong.MAX_VALUE);
    state.setJustification_bitfield(justification_bitfield);

    UnsignedLong previous_epoch_matching_target_balance = get_attesting_balance(
            state, get_matching_target_attestations(state, previous_epoch));

      if (previous_epoch_matching_target_balance
              .times(UnsignedLong.valueOf(3))
              .compareTo(get_total_active_balance(state).times(UnsignedLong.valueOf(2)))
              >= 0) {
        state.setCurrent_justified_epoch(previous_epoch);
        state.setCurrent_justified_root(get_block_root(state, state.getCurrent_justified_epoch()));
        UnsignedLong one = BitwiseOps.leftShift(UnsignedLong.ONE, 1);
        state.setJustification_bitfield(BitwiseOps.or(state.getJustification_bitfield(), one));
      }

      UnsignedLong current_epoch_matching_target_balance =
              get_attesting_balance(state, get_matching_target_attestations(state, current_epoch));

      if (current_epoch_matching_target_balance
              .times(UnsignedLong.valueOf(3))
              .compareTo(get_current_total_balance(state).times(UnsignedLong.valueOf(2)))
              >= 0) {
        new_justified_epoch = get_current_epoch(state);
        justification_bitfield = BitwiseOps.or(justification_bitfield, UnsignedLong.valueOf(1));
      }

      state.setJustification_bitfield(justification_bitfield);

      // Process finalizations
      UnsignedLong decimal4 = UnsignedLong.valueOf(4);
      UnsignedLong decimal8 = UnsignedLong.valueOf(8);
      UnsignedLong binary11 = UnsignedLong.valueOf(3);
      UnsignedLong binary111 = UnsignedLong.valueOf(7);
      UnsignedLong previous_justified_epoch = state.getPrevious_justified_epoch();
      UnsignedLong current_justified_epoch = state.getCurrent_justified_epoch();
      UnsignedLong current_epoch = get_current_epoch(state);

      // The 2nd/3rd/4th most recent epochs are all justified, the 2nd using the 4th as source
      if (BitwiseOps.rightShift(justification_bitfield, 1).mod(decimal8).equals(binary111)
              && previous_justified_epoch.equals(current_epoch.minus(UnsignedLong.valueOf(3)))) {
        new_finalized_epoch = previous_justified_epoch;
      }
      // The 2nd/3rd most recent epochs are both justified, the 2nd using the 3rd as source
      if (BitwiseOps.rightShift(justification_bitfield, 1).mod(decimal4).equals(binary11)
              && previous_justified_epoch.equals(current_epoch.minus(UnsignedLong.valueOf(2)))) {
        new_finalized_epoch = previous_justified_epoch;
      }
      // The 1st/2nd/3rd most recent epochs are all justified, the 1st using the 3rd as source
      if (justification_bitfield.mod(decimal8).equals(binary111)
              && current_justified_epoch.equals(current_epoch.minus(UnsignedLong.valueOf(2)))) {
        new_finalized_epoch = current_justified_epoch;
      }
      // The 1st/2nd most recent epochs are both justified, the 1st using the 2nd as source
      if (justification_bitfield.mod(decimal4).equals(binary11)
              && current_justified_epoch.equals(current_epoch.minus(UnsignedLong.ONE))) {
        new_finalized_epoch = current_justified_epoch;
      }

      // Update state justification variables
      state.setPrevious_justified_epoch(state.getCurrent_justified_epoch());
      state.setPrevious_justified_root(state.getCurrent_justified_root());
      if (!new_justified_epoch.equals(state.getCurrent_justified_epoch())) {
        state.setCurrent_justified_epoch(new_justified_epoch);
        state.setCurrent_justified_root(
                get_block_root(state, get_epoch_start_slot(new_justified_epoch)));
      }
      if (!new_finalized_epoch.equals(state.getFinalized_epoch())) {
        state.setFinalized_epoch(new_finalized_epoch);
        state.setFinalized_root(get_block_root(state, get_epoch_start_slot(new_finalized_epoch)));
      }
  }

  // @v0.7.1
  public static void process_crosslinks(BeaconState state) {
    state.setPrevious_crosslinks(new ArrayList<>(state.getCurrent_crosslinks()));
    UnsignedLong previous_epoch = get_previous_epoch(state);
    UnsignedLong current_epoch = get_current_epoch(state);

    for (UnsignedLong epoch = previous_epoch;
         epoch.compareTo(current_epoch) < 0;
         epoch = epoch.plus(UnsignedLong.ONE)) {
      for (int offset = 0; offset < get_epoch_committee_count(state, epoch).intValue(); offset++) {
        UnsignedLong shard = get_epoch_start_shard(state, epoch).plus(UnsignedLong.valueOf(offset)).mod(UnsignedLong.valueOf(SHARD_COUNT));
        List<Integer> crosslink_committee = get_crosslink_committee(state, epoch, shard);
        Pair<Crosslink, List<Integer>> winning_crosslink_and_attesting_indices =
                get_winning_crosslink_and_attesting_indices(state, epoch, shard);
        Crosslink winning_crosslink = winning_crosslink_and_attesting_indices.getLeft();
        List<Integer> attesting_indices = winning_crosslink_and_attesting_indices.getRight();
        if (UnsignedLong.valueOf(3L).times(get_total_balance(state, attesting_indices)).compareTo(UnsignedLong.valueOf(2L).times(get_total_balance(state, crosslink_committee))) >= 0) {
          state.getCurrent_crosslinks().set(shard.intValue(), winning_crosslink);
        }
      }
    }
  }

  /**
   * @param state
   */
  public static void maybe_reset_eth1_period(BeaconState state) {
    if (get_current_epoch(state)
            .plus(UnsignedLong.ONE)
            .mod(UnsignedLong.valueOf(Constants.EPOCHS_PER_ETH1_VOTING_PERIOD))
            .equals(UnsignedLong.ZERO)) {
      for (Eth1DataVote eth1DataVote : state.getEth1_data_votes()) {
        // If a majority of all votes were for a particular eth1_data value,
        // then set that as the new canonical value
        if (eth1DataVote
                .getVote_count()
                .times(UnsignedLong.valueOf(2L))
                .compareTo(
                        UnsignedLong.valueOf(Constants.EPOCHS_PER_ETH1_VOTING_PERIOD * SLOTS_PER_EPOCH))
                > 0) {
          state.setLatest_eth1_data(eth1DataVote.getEth1_data());
        }
      }
      state.setEth1_data_votes(new ArrayList<>());
    }
  }

  // @v0.7.1
  private static UnsignedLong get_base_reward(BeaconState state, int index) {
    UnsignedLong total_balance = get_total_active_balance(state);
    UnsignedLong effective_balance = state.getValidator_registry().get(index).getEffective_balance();
    return effective_balance.times(UnsignedLong.valueOf(BASE_REWARD_FACTOR)).dividedBy(integer_squareroot(total_balance)).dividedBy(UnsignedLong.valueOf(BASE_REWARDS_PER_EPOCH));
  }

  /**
   * @param state
   * @param index
   * @param epochs_since_finality
   * @return
   */
  private static UnsignedLong get_inactivity_penalty(
          BeaconState state, int index, UnsignedLong epochs_since_finality) {
    UnsignedLong intermediate_value =
            get_effective_balance(state, index)
                    .times(epochs_since_finality)
                    .dividedBy(UnsignedLong.valueOf(Constants.INACTIVITY_PENALTY_QUOTIENT))
                    .dividedBy(UnsignedLong.valueOf(2L));
    return get_base_reward(state, index).plus(intermediate_value);
  }

  /**
   * @param state
   * @return
   */
  private static MutablePair<List<UnsignedLong>, List<UnsignedLong>>
  get_justification_and_finalization_deltas(BeaconState state) {
    UnsignedLong epochs_since_finality =
            get_current_epoch(state).plus(UnsignedLong.ONE).minus(state.getFinalized_epoch());
    if (epochs_since_finality.compareTo(UnsignedLong.valueOf(4L)) <= 0) {
      return compute_normal_justification_and_finalization_deltas(state);
    } else {
      return compute_inactivity_leak_deltas(state);
    }
  }

  /**
   * @param state
   * @return
   */
  private static MutablePair<List<UnsignedLong>, List<UnsignedLong>>
  compute_normal_justification_and_finalization_deltas(BeaconState state) {
    int list_size = state.getValidator_registry().size();
    List<UnsignedLong> rewards = Arrays.asList(new UnsignedLong[list_size]);
    List<UnsignedLong> penalties = Arrays.asList(new UnsignedLong[list_size]);
    for (int i = 0; i < list_size; i++) {
      rewards.set(i, UnsignedLong.ZERO);
      penalties.set(i, UnsignedLong.ZERO);
    }
    MutablePair<List<UnsignedLong>, List<UnsignedLong>> deltas =
            new MutablePair<>(rewards, penalties);

    // Some helper variables
    List<PendingAttestation> boundary_attestations =
            get_previous_epoch_boundary_attestations(state);
    UnsignedLong boundary_attesting_balance = get_attesting_balance(state, boundary_attestations);
    UnsignedLong total_balance = get_previous_total_balance(state);
    UnsignedLong total_attesting_balance =
            get_attesting_balance(state, state.getPrevious_epoch_attestations());
    List<PendingAttestation> matching_head_attestations =
            get_previous_epoch_matching_head_attestations(state);
    UnsignedLong matching_head_balance = get_attesting_balance(state, matching_head_attestations);

    // Process rewards or penalties for all validators
    for (Integer index :
            get_active_validator_indices(state.getValidator_registry(), get_previous_epoch(state))) {
      // Expected FFG source
      if (get_attesting_indices(state, state.getPrevious_epoch_attestations()).contains(index)) {
        deltas
                .getLeft()
                .set(
                        index,
                        deltas
                                .getLeft()
                                .get(index)
                                .plus(
                                        get_base_reward(state, index)
                                                .times(total_attesting_balance)
                                                .dividedBy(total_balance)));
        // Inclusion speed bonus
        deltas
                .getLeft()
                .set(
                        index,
                        deltas
                                .getLeft()
                                .get(index)
                                .plus(
                                        get_base_reward(state, index)
                                                .times(UnsignedLong.valueOf(Constants.MIN_ATTESTATION_INCLUSION_DELAY))
                                                .dividedBy(inclusion_distance(state, index))));
      } else {
        deltas
                .getRight()
                .set(index, deltas.getRight().get(index).plus(get_base_reward(state, index)));
      }

      // Expected FFG target
      if (get_attesting_indices(state, boundary_attestations).contains(index)) {
        deltas
                .getLeft()
                .set(
                        index,
                        deltas
                                .getLeft()
                                .get(index)
                                .plus(
                                        get_base_reward(state, index)
                                                .times(boundary_attesting_balance)
                                                .dividedBy(total_balance)));
      } else {
        deltas
                .getRight()
                .set(index, deltas.getRight().get(index).plus(get_base_reward(state, index)));
      }

      // Expected head
      if (get_attesting_indices(state, matching_head_attestations).contains(index)) {
        deltas
                .getLeft()
                .set(
                        index,
                        deltas
                                .getLeft()
                                .get(index)
                                .plus(
                                        get_base_reward(state, index)
                                                .times(matching_head_balance)
                                                .dividedBy(total_balance)));
      } else {
        deltas
                .getRight()
                .set(index, deltas.getRight().get(index).plus(get_base_reward(state, index)));
      }

      // Proposer bonus
      if (get_attesting_indices(state, state.getPrevious_epoch_attestations()).contains(index)) {
        Integer proposer_index = get_beacon_proposer_index(state, inclusion_slot(state, index));
        deltas
                .getLeft()
                .set(
                        proposer_index,
                        deltas
                                .getRight()
                                .get(proposer_index)
                                .plus(get_base_reward(state, index))
                                .dividedBy(
                                        UnsignedLong.valueOf(Constants.ATTESTATION_INCLUSION_REWARD_QUOTIENT)));
      }
    }
    return deltas;
  }

  /**
   * @param state
   * @return
   */
  private static MutablePair<List<UnsignedLong>, List<UnsignedLong>> compute_inactivity_leak_deltas(
          BeaconState state) {

    int list_size = state.getValidator_registry().size();
    List<UnsignedLong> rewards = Arrays.asList(new UnsignedLong[list_size]);
    List<UnsignedLong> penalties = Arrays.asList(new UnsignedLong[list_size]);
    for (int i = 0; i < list_size; i++) {
      rewards.set(i, UnsignedLong.ZERO);
      penalties.set(i, UnsignedLong.ZERO);
    }
    MutablePair<List<UnsignedLong>, List<UnsignedLong>> deltas =
            new MutablePair<>(rewards, penalties);

    List<PendingAttestation> boundary_attestations =
            get_previous_epoch_boundary_attestations(state);
    List<PendingAttestation> matching_head_attestations =
            get_previous_epoch_matching_head_attestations(state);
    List<Integer> active_validator_indices =
            get_active_validator_indices(state.getValidator_registry(), get_previous_epoch(state));
    UnsignedLong epochs_since_finality =
            get_current_epoch(state).plus(UnsignedLong.ONE).minus(state.getFinalized_epoch());

    for (Integer index : active_validator_indices) {
      if (!get_attesting_indices(state, state.getPrevious_epoch_attestations()).contains(index)) {
        deltas
                .getRight()
                .set(
                        index,
                        deltas
                                .getRight()
                                .get(index)
                                .plus(get_inactivity_penalty(state, index, epochs_since_finality)));
      } else {
        // If a validator did attest, apply a small penalty for getting attestations included late
        deltas
                .getLeft()
                .set(
                        index,
                        deltas
                                .getLeft()
                                .get(index)
                                .plus(
                                        get_base_reward(state, index)
                                                .times(UnsignedLong.valueOf(Constants.MIN_ATTESTATION_INCLUSION_DELAY))
                                                .dividedBy(inclusion_distance(state, index))));
        deltas
                .getRight()
                .set(
                        index,
                        deltas
                                .getRight()
                                .get(index)
                                .plus(get_inactivity_penalty(state, index, epochs_since_finality)));
      }
      if (!get_attesting_indices(state, boundary_attestations).contains(index)) {
        deltas
                .getRight()
                .set(
                        index,
                        deltas
                                .getRight()
                                .get(index)
                                .plus(get_inactivity_penalty(state, index, epochs_since_finality)));
      }
      if (!get_attesting_indices(state, matching_head_attestations).contains(index)) {
        deltas
                .getRight()
                .set(index, deltas.getRight().get(index).plus(get_base_reward(state, index)));
      }
    }
    // Penalize slashed-but-inactive validators as though they were active but offline
    for (int index = 0; index < state.getValidator_registry().size(); index++) {
      boolean eligible =
              active_validator_indices.contains(index)
                      && state.getValidator_registry().get(index).isSlashed()
                      && (get_current_epoch(state)
                      .compareTo(state.getValidator_registry().get(index).getWithdrawable_epoch())
                      < 0);
      if (eligible) {
        deltas
                .getRight()
                .set(
                        index,
                        deltas
                                .getRight()
                                .get(index)
                                .plus(
                                        UnsignedLong.valueOf(2L)
                                                .times(get_inactivity_penalty(state, index, epochs_since_finality))
                                                .plus(get_base_reward(state, index))));
      }
    }
    return deltas;
  }

  // @v0.7.1
  public static ImmutablePair<List<UnsignedLong>, List<UnsignedLong>> get_attestation_deltas(
          BeaconState state) {
    UnsignedLong previous_epoch = get_previous_epoch(state);
    UnsignedLong total_balance = get_total_active_balance(state);

    int list_size = state.getValidator_registry().size();
    List<UnsignedLong> rewards = Arrays.asList(new UnsignedLong[list_size]);
    List<UnsignedLong> penalties = Arrays.asList(new UnsignedLong[list_size]);
    for (int i = 0; i < list_size; i++) {
      rewards.set(i, UnsignedLong.ZERO);
      penalties.set(i, UnsignedLong.ZERO);
    }

    List<Integer> eligible_validator_indices = IntStream.range(0, state.getValidator_registry().size())
            .filter(index -> {
              Validator validator = state.getValidator_registry().get(index);
              return is_active_validator(validator, previous_epoch) || (validator.isSlashed()
                      && previous_epoch.plus(UnsignedLong.ONE).compareTo(validator.getWithdrawable_epoch()) < 0);
            })
            .boxed()
            .collect(Collectors.toList());

    // Micro-incentives for matching FFG source, FFG target, and head
    List<PendingAttestation> matching_source_attestations = get_matching_source_attestations(state, previous_epoch);
    List<PendingAttestation> matching_target_attestations = get_matching_target_attestations(state, previous_epoch);
    List<PendingAttestation> matching_head_attestations = get_matching_head_attestations(state, previous_epoch);
    List<List<PendingAttestation>> attestation_lists = new ArrayList<>();
    attestation_lists.add(matching_source_attestations);
    attestation_lists.add(matching_target_attestations);
    attestation_lists.add(matching_head_attestations);
    for (List<PendingAttestation> attestations : attestation_lists) {
      List<Integer> unslashed_attesting_indices = get_unslashed_attesting_indices(state, attestations);
      UnsignedLong attesting_balance = get_total_balance(state, unslashed_attesting_indices);
      for (Integer index : eligible_validator_indices) {
        if (unslashed_attesting_indices.contains(index)) {
          rewards.set(index, rewards.get(index).plus(get_base_reward(state, index).times(attesting_balance).dividedBy(total_balance)));
        } else {
          penalties.set(index, penalties.get(index).plus(get_base_reward(state, index)));
        }
      }
    }

    // Proposer and inclusion delay micro-rewards
    for (Integer index : get_unslashed_attesting_indices(state, matching_source_attestations)) {
      PendingAttestation attestation = matching_source_attestations.stream()
              .filter(a -> get_attesting_indices(state, a.getData(), a.getAggregation_bitfield()).contains(index))
              .min(Comparator.comparing(PendingAttestation::getInclusion_delay));
      rewards.set(attestation.getProposer_index().intValue(), rewards.get(attestation.getProposer_index().intValue()).plus(get_base_reward(state, index).dividedBy(UnsignedLong.valueOf(PROPOSER_REWARD_QUOTIENT))))
      rewards.set(index, rewards.get(index).plus(get_base_reward(state, index).times(UnsignedLong.valueOf(MIN_ATTESTATION_INCLUSION_DELAY)).dividedBy(attestation.getInclusion_delay())));
    }

    // Inactivity penalty
    UnsignedLong finality_delay = previous_epoch.minus(state.getFinalized_epoch());
    if (finality_delay.longValue() > MIN_EPOCHS_TO_INACTIVITY_PENALTY) {
      List<Integer> matching_target_attesting_indices = get_unslashed_attesting_indices(state, matching_target_attestations);
      for (Integer index : eligible_validator_indices) {
        penalties.set(index, penalties.get(index).plus(UnsignedLong.valueOf(BASE_REWARDS_PER_EPOCH).times(get_base_reward(state, index))));
        if (!matching_target_attesting_indices.contains(index)) {
          penalties.set(index, penalties.get(index).plus(state.getValidator_registry().get(index).getEffective_balance().times(finality_delay).dividedBy(UnsignedLong.valueOf(INACTIVITY_PENALTY_QUOTIENT))));
        }
      }
    }
    return new ImmutablePair<>(rewards, penalties);
  }

  // @v0.7.1
  public static ImmutablePair<List<UnsignedLong>, List<UnsignedLong>> get_crosslink_deltas(
          BeaconState state) {
    int list_size = state.getValidator_registry().size();
    List<UnsignedLong> rewards = Arrays.asList(new UnsignedLong[list_size]);
    List<UnsignedLong> penalties = Arrays.asList(new UnsignedLong[list_size]);
    for (int i = 0; i < list_size; i++) {
      rewards.set(i, UnsignedLong.ZERO);
      penalties.set(i, UnsignedLong.ZERO);
    }

    UnsignedLong epoch = get_previous_epoch(state);

    for (int offset = 0; offset < get_epoch_committee_count(state, epoch); offset++) {
      UnsignedLong shard = (get_epoch_start_shard(state, epoch).intValue() + offset) % SHARD_COUNT;
      List<Integer> crosslink_committee = get_crosslink_committee(state, epoch, shard);
      Pair<Crosslink, List<Integer>> winning_crosslink_and_attesting_indices = get_winning_crosslink_and_attesting_indices(state, epoch, shard);
      List<Integer> attesting_indices = winning_crosslink_and_attesting_indices.getRight();
      UnsignedLong attesting_balance = get_total_balance(state, attesting_indices);
      UnsignedLong committee_balance = get_total_balance(state, crosslink_committee);
      for (int index : crosslink_committee) {
        UnsignedLong base_reward = get_base_reward(state, index);
        if (attesting_indices.contains(index)) {
          rewards.set(index, rewards.get(index).plus(base_reward.times(attesting_balance).dividedBy(committee_balance)));
        } else {
          penalties.set(index, penalties.get(index).plus(base_reward));
        }
      }
    }
    return new ImmutablePair<List<UnsignedLong>, List<UnsignedLong>>(rewards, penalties);
  }

  /**
   * @param state
   */
  public static void apply_rewards(BeaconState state) {
    MutablePair<List<UnsignedLong>, List<UnsignedLong>> deltas1 =
            get_justification_and_finalization_deltas(state);
    MutablePair<List<UnsignedLong>, List<UnsignedLong>> deltas2 = get_crosslink_deltas(state);

    List<UnsignedLong> validator_balances = state.getValidator_balances();
    for (int i = 0; i < state.getValidator_registry().size(); i++) {
      validator_balances.set(
              i,
              max(
                      UnsignedLong.ZERO,
                      validator_balances
                              .get(i)
                              .plus(deltas1.getLeft().get(i))
                              .plus(deltas2.getLeft().get(i))
                              .minus(deltas1.getRight().get(i))
                              .minus(deltas2.getRight().get(i))));
    }
  }

  /**
   * Iterate through the validator registry and eject active validators with balance below
   * ``EJECTION_BALANCE``.
   *
   * @param state
   * @throws EpochProcessingException
   */
  public static void process_ejections(BeaconState state) throws EpochProcessingException {
    try {
      UnsignedLong currentEpoch = get_current_epoch(state);

      for (Integer index :
              get_active_validator_indices(state.getValidator_registry(), get_current_epoch(state))) {
        if (state
                .getValidator_balances()
                .get(index)
                .compareTo(UnsignedLong.valueOf(Constants.EJECTION_BALANCE))
                < 0) {
          exit_validator(state, index);
        }
      }
    } catch (IllegalArgumentException e) {
      LOG.log(Level.WARN, "EpochProcessingException thrown in process_ejections()");
      throw new EpochProcessingException(e);
    }
  }

  /**
   * @param state
   * @return
   */
  private static boolean should_update_validator_registry(BeaconState state) {
    // Must have finalized a new block
    if (state.getFinalized_epoch().compareTo(state.getValidator_registry_update_epoch()) <= 0) {
      return false;
    }

    // Must have processed new crosslinks on all shards of the current epoch
    List<UnsignedLong> shards_to_check = new ArrayList<>();
    for (int i = 0; i < get_current_epoch_committee_count(state).intValue(); i++) {
      shards_to_check.add(
              state
                      .getCurrent_shuffling_start_shard()
                      .plus(UnsignedLong.valueOf(i))
                      .mod(UnsignedLong.valueOf(Constants.SHARD_COUNT)));
    }

    for (UnsignedLong shard : shards_to_check) {
      if (state
              .getLatest_crosslinks()
              .get(shard.intValue())
              .getEpoch()
              .compareTo(state.getValidator_registry_update_epoch())
              <= 0) {
        return false;
      }
    }
    return true;
  }

  /**
   * Update the validator registry, Note that this function mutates ``state``.
   *
   * @param state
   */
  public static void update_validator_registry(BeaconState state) throws EpochProcessingException {
    try {
      UnsignedLong currentEpoch = get_current_epoch(state);
      List<Integer> active_validators =
              get_active_validator_indices(state.getValidator_registry(), currentEpoch);
      UnsignedLong total_balance = get_total_balance(state, active_validators);

      UnsignedLong max_balance_churn =
              max(
                      UnsignedLong.valueOf(Constants.MAX_DEPOSIT_AMOUNT),
                      total_balance.dividedBy(
                              UnsignedLong.valueOf((2 * Constants.MAX_BALANCE_CHURN_QUOTIENT))));

      // Activate validators within the allowable balance churn
      UnsignedLong balance_churn = UnsignedLong.ZERO;
      int index = 0;
      for (Validator validator : state.getValidator_registry()) {
        if (validator.getActivation_epoch().compareTo(FAR_FUTURE_EPOCH) == 0
                && state
                .getValidator_balances()
                .get(index)
                .compareTo(UnsignedLong.valueOf(Constants.MAX_DEPOSIT_AMOUNT))
                >= 0) {
          balance_churn = balance_churn.plus(get_effective_balance(state, index));
          if (balance_churn.compareTo(max_balance_churn) > 0) break;
          BeaconStateUtil.activate_validator(state, index, false);
        }
        index++;
      }

      // Exit validators within the allowable balance churn
      balance_churn = UnsignedLong.ZERO;
      index = 0;
      for (Validator validator : state.getValidator_registry()) {
        if (validator.getExit_epoch().compareTo(FAR_FUTURE_EPOCH) == 0
                && validator.hasInitiatedExit()) {
          // Check the balance churn would be within the allowance
          balance_churn = balance_churn.plus(get_effective_balance(state, index));
          if (balance_churn.compareTo(max_balance_churn) > 0) break;

          // Exit validator
          exit_validator(state, index);
        }
        index++;
      }
      state.setValidator_registry_update_epoch(currentEpoch);
    } catch (IllegalArgumentException e) {
      LOG.log(Level.WARN, "EpochProcessingException thrown in update_validator_registry()");
      throw new EpochProcessingException(e);
    }
  }

  // @v0.7.1
  public static void process_rewards_and_penalties(BeaconStateWithCache state) {
    if (get_current_epoch(state).equals(UnsignedLong.valueOf(GENESIS_EPOCH))) {
      return;
    }

    Pair<List<UnsignedLong>, List<UnsignedLong>> attestation_deltas = get_attestation_deltas(state);
    List<UnsignedLong> rewards1 = attestation_deltas.getLeft();
    List<UnsignedLong> penalties1 = attestation_deltas.getRight();
    Pair<List<UnsignedLong>, List<UnsignedLong>> crosslink_deltas = get_crosslink_deltas(state);
    List<UnsignedLong> rewards2 = crosslink_deltas.getLeft();
    List<UnsignedLong> penalties2 = crosslink_deltas.getLeft();

    for (int i = 0; i < state.getValidator_registry().size(); i++) {
      increase_balance(state, i, rewards1.get(i).plus(rewards2.get(i)));
      decrease_balance(state, i, penalties1.get(i).plus(penalties2.get(i)));
    }
  }

  // @v0.7.1
  public static void process_registry_updates(BeaconState state)
          throws EpochProcessingException {
    try {

      // Process activation eligibility and ejections
      for (int index = 0; index < state.getValidator_registry().size(); index++) {
        Validator validator = state.getValidator_registry().get(index);

        if (validator.getActivation_eligibility_epoch().equals(FAR_FUTURE_EPOCH)
                && validator.getEffective_balance().compareTo(UnsignedLong.valueOf(MAX_EFFECTIVE_BALANCE)) >= 0) {
          validator.setActivation_eligibility_epoch(get_current_epoch(state));
        }

        if (is_active_validator(validator, get_current_epoch(state)) && validator.getEffective_balance().compareTo(UnsignedLong.valueOf(EJECTION_BALANCE)) <= 0) {
          initiate_validator_exit(state, index);
        }
      }

      // Queue validators eligible for activation and not dequeued for activation prior to finalized epoch
      List<Integer> activation_queue = IntStream.range(0, state.getValidator_registry().size())
              .filter(index -> {
                Validator validator = state.getValidator_registry().get(index);
                return !validator.getActivation_eligibility_epoch().equals(FAR_FUTURE_EPOCH)
                        && validator.getActivation_epoch().compareTo(get_delayed_activation_exit_epoch(state.getFinalized_epoch())) >= 0;
              })
              .boxed()
              .sorted((i1, i2) -> state.getValidator_registry().get(i1).getActivation_eligibility_epoch()
                      .compareTo(state.getValidator_registry().get(i2).getActivation_epoch()))
              .collect(Collectors.toList());

      for (Integer index : activation_queue.subList(0, get_churn_limit(state))) {
        Validator validator = state.getValidator_registry().get(index);
        if (validator.getActivation_epoch().equals(FAR_FUTURE_EPOCH)) {
          validator.setActivation_epoch(get_delayed_activation_exit_epoch(get_current_epoch(state)));
        }
      }
    } catch (IllegalArgumentException e) {
      LOG.log(Level.WARN, "EpochProcessingException thrown in update_validator_registry()");
      throw new EpochProcessingException(e);
    }
  }

  // @v0.7.1
  public static void process_slashings(BeaconState state) {
    UnsignedLong current_epoch = get_current_epoch(state);
    List<Integer> active_validator_indices =
            get_active_validator_indices(state.getValidator_registry(), current_epoch);
    UnsignedLong total_balance = get_total_balance_active_balance(state);

    // Compute `total_penalties`
    UnsignedLong total_at_start = state
            .getLatest_slashed_balances()
            .get(toIntExact(current_epoch.plus(UnsignedLong.ONE).mod(UnsignedLong.valueOf(LATEST_SLASHED_EXIT_LENGTH)).longValue()));
    UnsignedLong total_at_end = state
            .getLatest_slashed_balances()
            .get(toIntExact(current_epoch.mod(UnsignedLong.valueOf(LATEST_SLASHED_EXIT_LENGTH)).longValue()));

    UnsignedLong total_penalties = total_at_end.minus(total_at_start);

    for (int index = 0; index < state.getValidator_registry().size(); index++) {
      Validator validator = state.getValidator_registry().get(index);
      if (validator.isSlashed()
              && current_epoch.equals(
              validator
                      .getWithdrawable_epoch()
                      .minus(UnsignedLong.valueOf(LATEST_SLASHED_EXIT_LENGTH / 2)))) {
        UnsignedLong penalty =
                max(
                        validator.getEffective_balance()
                                .times(min(total_penalties.times(UnsignedLong.valueOf(3L)), total_balance))
                                .dividedBy(total_balance),
                        validator.getEffective_balance()
                                .dividedBy(UnsignedLong.valueOf(Constants.MIN_SLASHING_PENALTY_QUOTIENT)));
        decrease_balance(state, index, penalty);
      }
    }
  }

  /**
   * Process the exit queue. Note that this function mutates ``state``.
   *
   * @param state
   */
  public static void process_exit_queue(BeaconState state) {
    // Sort in order of exit epoch, and validators that exit within the same epoch exit in order of
    // validator index
    List<Integer> sorted_indices =
            IntStream.range(0, state.getValidator_registry().size())
                    .boxed()
                    .filter(i -> eligible(state, i))
                    .sorted(Comparator.comparing(i -> state.getValidator_registry().get(i).getExit_epoch()))
                    .collect(Collectors.toList());

    int index = 0;
    for (Integer dequeues : sorted_indices) {
      if (dequeues >= Constants.MAX_EXIT_DEQUEUES_PER_EPOCH) {
        break;
      }
      prepare_validator_for_withdrawal(state, index);
    }
  }

  private static boolean eligible(BeaconState state, int index) {
    Validator validator = state.getValidator_registry().get(index);
    // Filter out dequeued validators
    if (!validator.getWithdrawable_epoch().equals(FAR_FUTURE_EPOCH)) {
      return false;
    } else {
      // Dequeue if the minimum amount of time has passed
      return get_current_epoch(state)
              .compareTo(
                      validator
                              .getExit_epoch()
                              .plus(UnsignedLong.valueOf(Constants.MIN_VALIDATOR_WITHDRAWABILITY_DELAY)))
              >= 0;
    }
  }

  // @v0.7.1
  public static void process_final_updates(BeaconState state) {
    UnsignedLong current_epoch = get_current_epoch(state);
    UnsignedLong next_epoch = current_epoch.plus(UnsignedLong.ONE);

    // Reset eth1 data votes
    if (state.getSlot().plus(UnsignedLong.ONE).mod(UnsignedLong.valueOf(Constants.SLOTS_PER_ETH1_VOTING_PERIOD)).equals(UnsignedLong.ZERO)) {
      state.setEth1_data_votes(new ArrayList<>());
    }

    // Update effective balances with hysteresis
    for (int index = 0; index < state.getValidator_registry().size(); index++) {
      Validator validator = state.getValidator_registry().get(index);
      UnsignedLong balance = state.getBalances().get(index);
      long HALF_INCREMENT = Constants.EFFECTIVE_BALANCE_INCREMENT / 2;
      if (balance.compareTo(validator.getEffective_balance()) < 0
              || validator.getEffective_balance().plus(UnsignedLong.valueOf(3 * HALF_INCREMENT)).compareTo(balance) < 0) {
        validator.setEffective_balance(min(balance.minus(balance.mod(UnsignedLong.valueOf(EFFECTIVE_BALANCE_INCREMENT))), UnsignedLong.valueOf(MAX_EFFECTIVE_BALANCE)));
      }
    }

    // Update start shard
    state.setLatest_start_shard(state.getLatest_start_shard().plus(get_shard_delta(state, current_epoch)).mod(UnsignedLong.valueOf(SHARD_COUNT));

    // Set active index root
    int index_root_position =
            (toIntExact(next_epoch.longValue()) + ACTIVATION_EXIT_DELAY) % LATEST_ACTIVE_INDEX_ROOTS_LENGTH;
    List<Integer> active_validator_indices =
            get_active_validator_indices(
                    state,
                    next_epoch.plus(UnsignedLong.valueOf(ACTIVATION_EXIT_DELAY)));
    state
            .getLatest_active_index_roots()
            .set(
                    index_root_position,
                    HashTreeUtil.hash_tree_root(
                            SSZTypes.LIST_OF_BASIC,
                            active_validator_indices.stream()
                                    .map(item -> SSZ.encodeUInt64(item))
                                    .collect(Collectors.toList())));

    // Set total slashed balances
    state
            .getLatest_slashed_balances()
            .set(
                    toIntExact(next_epoch.longValue()) % LATEST_SLASHED_EXIT_LENGTH,
                    state
                            .getLatest_slashed_balances()
                            .get(current_epoch.intValue() % LATEST_SLASHED_EXIT_LENGTH));

    // Set randao mix
    state
            .getLatest_randao_mixes()
            .set(
                    toIntExact(next_epoch.longValue()) % LATEST_RANDAO_MIXES_LENGTH,
                    get_randao_mix(state, current_epoch));

    // Set historical root accumulator
    if (toIntExact(next_epoch.longValue()) % (SLOTS_PER_HISTORICAL_ROOT / SLOTS_PER_EPOCH) == 0) {
      HistoricalBatch historical_batch =
              new HistoricalBatch(state.getLatest_block_roots(), state.getLatest_state_roots());
      state.getHistorical_roots().add(historical_batch.hash_tree_root());
    }

    // Rotate current/previous epoch attestations
    state.setPrevious_epoch_attestations(state.getCurrent_epoch_attestations());
    state.setCurrent_epoch_attestations(new ArrayList<>());
  }
}
