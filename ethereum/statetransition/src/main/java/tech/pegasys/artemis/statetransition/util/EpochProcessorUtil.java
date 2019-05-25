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
import static tech.pegasys.artemis.datastructures.Constants.FAR_FUTURE_EPOCH;
import static tech.pegasys.artemis.datastructures.Constants.LATEST_ACTIVE_INDEX_ROOTS_LENGTH;
import static tech.pegasys.artemis.datastructures.Constants.LATEST_RANDAO_MIXES_LENGTH;
import static tech.pegasys.artemis.datastructures.Constants.LATEST_SLASHED_EXIT_LENGTH;
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
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_effective_balance;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_epoch_start_slot;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_previous_epoch;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_randao_mix;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_total_balance;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.is_power_of_two;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.max;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.min;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.prepare_validator_for_withdrawal;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.slot_to_epoch;
import static tech.pegasys.artemis.datastructures.util.ValidatorsUtil.get_active_validator_indices;

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.ssz.SSZ;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.logging.log4j.Level;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.blocks.Eth1DataVote;
import tech.pegasys.artemis.datastructures.state.BeaconState;
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
    return get_total_balance(
        state,
        get_active_validator_indices(state.getValidator_registry(), get_previous_epoch(state)));
  }

  /**
   * Returns the union of validator index sets, where the sets are the attestation participants of
   * attestations passed in TODO: the union part takes O(n^2) time, where n is the number of
   * validators. OPTIMIZE
   */
  public static List<Integer> get_attesting_indices(
      BeaconState state, List<PendingAttestation> attestations) throws IllegalArgumentException {

    HashSet<Integer> output = new HashSet<>();
    for (PendingAttestation a : attestations) {
      output.addAll(get_attestation_participants(state, a.getData(), a.getAggregation_bitfield()));
    }
    List<Integer> output_list = new ArrayList<>(output);
    Collections.sort(output_list);
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
    List<PendingAttestation> all_attestations = new ArrayList<>();
    all_attestations.addAll(state.getCurrent_epoch_attestations());
    all_attestations.addAll(state.getPrevious_epoch_attestations());

    List<PendingAttestation> valid_attestations = new ArrayList<>();
    for (PendingAttestation a : all_attestations) {
      if (a.getData()
          .getPrevious_crosslink()
          .equals(state.getLatest_crosslinks().get(shard.intValue()))) {
        valid_attestations.add(a);
      }
    }

    List<Bytes32> all_roots = new ArrayList<>();
    for (PendingAttestation a : valid_attestations) {
      all_roots.add(a.getData().getCrosslink_data_root());
    }

    if (all_roots.size() == 0) {
      return new MutablePair<>(ZERO_HASH, new ArrayList<>());
    }

    HashMap<Bytes32, UnsignedLong> root_balances = new HashMap<>();
    for (Bytes32 root : all_roots) {
      root_balances.put(
          root, get_attesting_balance(state, get_attestations_for(root, valid_attestations)));
    }

    // TODO: make sure ties broken in favor of lexicographically higher hash
    Bytes32 winning_root =
        Collections.max(root_balances.entrySet(), Map.Entry.comparingByValue()).getKey();
    return new MutablePair<>(
        winning_root,
        get_attesting_indices(state, get_attestations_for(winning_root, valid_attestations)));
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

  /**
   * Update Justification state fields
   *
   * @param state
   * @throws EpochProcessingException
   */
  public static void update_justification_and_finalization(BeaconState state)
      throws EpochProcessingException {
    try {
      UnsignedLong new_justified_epoch = state.getCurrent_justified_epoch();
      UnsignedLong new_finalized_epoch = state.getFinalized_epoch();

      // Rotate the justification bitfield up one epoch to make room for the current epoch
      UnsignedLong justification_bitfield = state.getJustification_bitfield();
      justification_bitfield = BitwiseOps.leftShift(justification_bitfield, 1);

      // If the previous epoch gets justified, fill the second last bit
      UnsignedLong previous_boundary_attesting_balance =
          get_attesting_balance(state, get_previous_epoch_boundary_attestations(state));
      if (previous_boundary_attesting_balance
              .times(UnsignedLong.valueOf(3))
              .compareTo(get_previous_total_balance(state).times(UnsignedLong.valueOf(2)))
          >= 0) {
        new_justified_epoch = get_previous_epoch(state);
        justification_bitfield = BitwiseOps.or(justification_bitfield, UnsignedLong.valueOf(2));
      }
      // If the current epoch gets justified, fill the last bit
      UnsignedLong current_boundary_attesting_balance =
          get_attesting_balance(state, get_current_epoch_boundary_attestations(state));

      if (current_boundary_attesting_balance
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
    } catch (IllegalArgumentException e) {
      LOG.log(Level.WARN, "EpochProcessingException thrown in updateJustification()");
      throw new EpochProcessingException(e);
    }
  }

  /**
   * @param state
   * @throws EpochProcessingException
   */
  public static void process_crosslinks(BeaconState state) throws EpochProcessingException {
    try {
      UnsignedLong current_epoch = get_current_epoch(state);
      UnsignedLong previous_epoch = get_previous_epoch(state);
      UnsignedLong next_epoch = current_epoch.plus(UnsignedLong.ONE);

      for (UnsignedLong curr_slot = get_epoch_start_slot(previous_epoch);
          curr_slot.compareTo(get_epoch_start_slot(next_epoch)) < 0;
          curr_slot = curr_slot.plus(UnsignedLong.ONE)) {
        List<CrosslinkCommittee> crosslink_committees_at_slot =
            get_crosslink_committees_at_slot(state, curr_slot);
        for (CrosslinkCommittee committee : crosslink_committees_at_slot) {
          MutablePair<Bytes32, List<Integer>> winning_root_and_participants =
              get_winning_root_and_participants(state, committee.getShard());
          Bytes32 winning_root = winning_root_and_participants.getLeft();
          List<Integer> participants = winning_root_and_participants.getRight();
          UnsignedLong participating_balance = get_total_balance(state, participants);
          UnsignedLong total_balance = get_total_balance(state, committee.getCommittee());

          if (participating_balance
                  .times(UnsignedLong.valueOf(3L))
                  .compareTo(total_balance.times(UnsignedLong.valueOf(2L)))
              >= 0) {
            UnsignedLong shard = committee.getShard();
            state
                .getLatest_crosslinks()
                .set(
                    toIntExact(shard.longValue()),
                    new Crosslink(slot_to_epoch(state.getSlot()), winning_root));
          }
        }
      }
    } catch (IllegalArgumentException e) {
      LOG.log(Level.WARN, "EpochProcessingException thrown in process_crosslinks()");
      throw new EpochProcessingException(e);
    }
  }

  /** @param state */
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

  /**
   * @param state
   * @param index
   * @return
   */
  private static UnsignedLong get_base_reward(BeaconState state, int index) {
    if (get_previous_total_balance(state).equals(UnsignedLong.ZERO)) {
      return UnsignedLong.ZERO;
    }
    UnsignedLong adjusted_quotient =
        BeaconStateUtil.integer_squareroot(get_previous_total_balance(state))
            .dividedBy(UnsignedLong.valueOf(Constants.BASE_REWARD_QUOTIENT));
    return get_effective_balance(state, index)
        .dividedBy(adjusted_quotient)
        .dividedBy(UnsignedLong.valueOf(5L));
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

  /**
   * @param state
   * @return
   */
  private static MutablePair<List<UnsignedLong>, List<UnsignedLong>> get_crosslink_deltas(
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

    UnsignedLong previous_epoch_start_slot = get_epoch_start_slot(get_previous_epoch(state));
    UnsignedLong current_epoch_start_slot = get_epoch_start_slot(get_current_epoch(state));

    for (UnsignedLong slot = previous_epoch_start_slot;
        slot.compareTo(current_epoch_start_slot) < 0;
        slot = slot.plus(UnsignedLong.ONE)) {
      for (CrosslinkCommittee committee : get_crosslink_committees_at_slot(state, slot)) {
        MutablePair<Bytes32, List<Integer>> winning_root_and_participants =
            get_winning_root_and_participants(state, committee.getShard());
        List<Integer> participants = winning_root_and_participants.getRight();
        UnsignedLong participating_balance = get_total_balance(state, participants);
        UnsignedLong total_balance = get_total_balance(state, committee.getCommittee());
        for (Integer index : committee.getCommittee()) {
          if (participants.contains(index)) {
            deltas
                .getLeft()
                .set(
                    index,
                    deltas
                        .getLeft()
                        .get(index)
                        .plus(
                            get_base_reward(state, index)
                                .times(participating_balance)
                                .dividedBy(total_balance)));
          } else {
            deltas
                .getRight()
                .set(index, deltas.getRight().get(index).plus(get_base_reward(state, index)));
          }
        }
      }
    }
    return deltas;
  }

  /** @param state */
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

  /**
   * @param state
   * @throws EpochProcessingException
   */
  public static void update_registry_and_shuffling_data(BeaconState state)
      throws EpochProcessingException {
    try {
      // First set previous shuffling data to current shuffling data
      state.setPrevious_shuffling_epoch(state.getCurrent_shuffling_epoch());
      state.setPrevious_shuffling_start_shard(state.getCurrent_shuffling_start_shard());
      state.setPrevious_shuffling_seed(state.getCurrent_shuffling_seed());
      UnsignedLong current_epoch = get_current_epoch(state);
      UnsignedLong next_epoch = current_epoch.plus(UnsignedLong.ONE);

      // Check if we should update, and if so, update
      if (should_update_validator_registry(state)) {
        update_validator_registry(state);
        // If we update the registry, update the shuffling data and shards as well
        state.setCurrent_shuffling_epoch(next_epoch);
        state.setCurrent_shuffling_start_shard(
            state
                .getCurrent_shuffling_start_shard()
                .plus(get_current_epoch_committee_count(state))
                .mod(UnsignedLong.valueOf(Constants.SHARD_COUNT)));
        state.setCurrent_shuffling_seed(generate_seed(state, state.getCurrent_shuffling_epoch()));
      } else {
        // If processing at least one crosslink keeps failing, then reshuffle every power of two,
        // but don't update the current_shuffling_start_shard
        UnsignedLong epochs_since_last_registry_update =
            current_epoch.minus(state.getValidator_registry_update_epoch());
        if (epochs_since_last_registry_update.compareTo(UnsignedLong.ONE) > 0
            && is_power_of_two(epochs_since_last_registry_update)) {
          state.setCurrent_shuffling_epoch(next_epoch);
          state.setCurrent_shuffling_seed(generate_seed(state, state.getCurrent_shuffling_epoch()));
        }
      }
    } catch (IllegalArgumentException e) {
      LOG.log(Level.WARN, "EpochProcessingException thrown in update_validator_registry()");
      throw new EpochProcessingException(e);
    }
  }

  /**
   * Process the slashings. Note that this function mutates ``state``.
   *
   * @param state
   */
  public static void process_slashings(BeaconState state) {
    UnsignedLong current_epoch = get_current_epoch(state);
    List<Integer> active_validator_indices =
        get_active_validator_indices(state.getValidator_registry(), current_epoch);
    UnsignedLong total_balance = get_total_balance(state, active_validator_indices);

    // Compute `total_penalties`
    UnsignedLong epoch_index = current_epoch.mod(UnsignedLong.valueOf(LATEST_SLASHED_EXIT_LENGTH));
    UnsignedLong total_at_start =
        state
            .getLatest_slashed_balances()
            .get(
                epoch_index
                    .plus(UnsignedLong.ONE)
                    .mod(UnsignedLong.valueOf(LATEST_SLASHED_EXIT_LENGTH))
                    .intValue());
    UnsignedLong total_at_end = state.getLatest_slashed_balances().get(epoch_index.intValue());
    UnsignedLong total_penalties = total_at_end.minus(total_at_start);

    int index = 0;
    for (Validator validator : state.getValidator_registry()) {
      if (validator.isSlashed()
          && current_epoch.equals(
              validator
                  .getWithdrawable_epoch()
                  .minus(UnsignedLong.valueOf(LATEST_SLASHED_EXIT_LENGTH)))) {
        UnsignedLong penalty =
            max(
                get_effective_balance(state, index)
                    .times(min(total_penalties.times(UnsignedLong.valueOf(3L)), total_balance))
                    .dividedBy(total_balance),
                get_effective_balance(state, index)
                    .dividedBy(UnsignedLong.valueOf(Constants.MIN_PENALTY_QUOTIENT)));
        state
            .getValidator_balances()
            .set(index, state.getValidator_balances().get(index).minus(penalty));
      }
      index++;
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

  /** @param state */
  public static void finish_epoch_update(BeaconState state) {
    UnsignedLong current_epoch = get_current_epoch(state);
    UnsignedLong next_epoch = current_epoch.plus(UnsignedLong.ONE);

    // Set active index root
    int index_root_position =
        (next_epoch.intValue() + ACTIVATION_EXIT_DELAY) % LATEST_ACTIVE_INDEX_ROOTS_LENGTH;
    List<Integer> active_validator_indices =
        get_active_validator_indices(
            state.getValidator_registry(),
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
            next_epoch.intValue() % LATEST_SLASHED_EXIT_LENGTH,
            state
                .getLatest_slashed_balances()
                .get(current_epoch.intValue() % LATEST_SLASHED_EXIT_LENGTH));

    // Set randao mix
    state
        .getLatest_randao_mixes()
        .set(
            next_epoch.intValue() % LATEST_RANDAO_MIXES_LENGTH,
            get_randao_mix(state, current_epoch));

    // Set historical root accumulator
    if (next_epoch.intValue() % (SLOTS_PER_HISTORICAL_ROOT / SLOTS_PER_EPOCH) == 0) {
      HistoricalBatch historical_batch =
          new HistoricalBatch(state.getLatest_block_roots(), state.getLatest_state_roots());
      state.getHistorical_roots().add(historical_batch.hash_tree_root());
    }

    // Rotate current/previous epoch attestations
    state.setPrevious_epoch_attestations(state.getCurrent_epoch_attestations());
    state.setCurrent_epoch_attestations(new ArrayList<>());
  }
}
