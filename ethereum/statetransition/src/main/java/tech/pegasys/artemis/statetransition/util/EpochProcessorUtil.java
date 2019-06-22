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

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.Math.toIntExact;
import static tech.pegasys.artemis.datastructures.Constants.ACTIVATION_EXIT_DELAY;
import static tech.pegasys.artemis.datastructures.Constants.BASE_REWARDS_PER_EPOCH;
import static tech.pegasys.artemis.datastructures.Constants.BASE_REWARD_FACTOR;
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
import static tech.pegasys.artemis.datastructures.util.AttestationUtil.get_attestation_data_slot;
import static tech.pegasys.artemis.datastructures.util.AttestationUtil.get_attesting_indices;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_block_root;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_block_root_at_slot;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_churn_limit;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_current_epoch;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_delayed_activation_exit_epoch;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_epoch_committee_count;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_previous_epoch;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_randao_mix;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_total_balance;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.initiate_validator_exit;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.integer_squareroot;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.max;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.min;
import static tech.pegasys.artemis.datastructures.util.CrosslinkCommitteeUtil.get_crosslink_committee;
import static tech.pegasys.artemis.datastructures.util.CrosslinkCommitteeUtil.get_epoch_start_shard;
import static tech.pegasys.artemis.datastructures.util.CrosslinkCommitteeUtil.get_shard_delta;
import static tech.pegasys.artemis.datastructures.util.ValidatorsUtil.decrease_balance;
import static tech.pegasys.artemis.datastructures.util.ValidatorsUtil.get_active_validator_indices;
import static tech.pegasys.artemis.datastructures.util.ValidatorsUtil.increase_balance;
import static tech.pegasys.artemis.datastructures.util.ValidatorsUtil.is_active_validator;

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.BeaconStateWithCache;
import tech.pegasys.artemis.datastructures.state.Crosslink;
import tech.pegasys.artemis.datastructures.state.HistoricalBatch;
import tech.pegasys.artemis.datastructures.state.PendingAttestation;
import tech.pegasys.artemis.datastructures.state.Validator;
import tech.pegasys.artemis.util.alogger.ALogger;
import tech.pegasys.artemis.util.bitwise.BitwiseOps;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil.SSZTypes;

public final class EpochProcessorUtil {

  private static final ALogger LOG = new ALogger(EpochProcessorUtil.class.getName());

  // State Transition Helper Functions

  /**
   * v0.7.1
   * https://github.com/ethereum/eth2.0-specs/blob/v0.7.1/specs/core/0_beacon-chain.md#helper-functions-1
   * Returns total balance of all active validators
   *
   * @param state
   * @return
   */
  private static UnsignedLong get_total_active_balance(BeaconState state) {
    return get_total_balance(state, get_active_validator_indices(state, get_current_epoch(state)));
  }

  /**
   * v0.7.1
   * https://github.com/ethereum/eth2.0-specs/blob/v0.7.1/specs/core/0_beacon-chain.md#helper-functions-1
   * Returns current or previous epoch attestations depending to the epoch passed in
   *
   * @param state
   * @param epoch
   * @return
   * @throws IllegalArgumentException
   */
  private static List<PendingAttestation> get_matching_source_attestations(
      BeaconState state, UnsignedLong epoch) throws IllegalArgumentException {
    checkArgument(
        get_current_epoch(state).equals(epoch) || get_previous_epoch(state).equals(epoch),
        "get_matching_source_attestations");
    if (epoch.equals(get_current_epoch(state))) {
      return state.getCurrent_epoch_attestations();
    }
    return state.getPrevious_epoch_attestations();
  }

  /**
   * v0.7.1
   * https://github.com/ethereum/eth2.0-specs/blob/v0.7.1/specs/core/0_beacon-chain.md#helper-functions-1
   * Returns source attestations that target the block root of the first block in the given epoch
   *
   * @param state
   * @param epoch
   * @return
   * @throws IllegalArgumentException
   */
  private static List<PendingAttestation> get_matching_target_attestations(
      BeaconState state, UnsignedLong epoch) throws IllegalArgumentException {
    return get_matching_source_attestations(state, epoch).stream()
        .filter(a -> a.getData().getTarget_root().equals(get_block_root(state, epoch)))
        .collect(Collectors.toList());
  }

  /**
   * v0.7.1
   * https://github.com/ethereum/eth2.0-specs/blob/v0.7.1/specs/core/0_beacon-chain.md#helper-functions-1
   * Returns source attestations that have the same beacon head block as the one seen in state
   *
   * @param state
   * @param epoch
   * @return
   */
  private static List<PendingAttestation> get_matching_head_attestations(
      BeaconState state, UnsignedLong epoch) throws IllegalArgumentException {
    return get_matching_source_attestations(state, epoch).stream()
        .filter(
            a ->
                a.getData()
                    .getBeacon_block_root()
                    .equals(
                        get_block_root_at_slot(
                            state, get_attestation_data_slot(state, a.getData()))))
        .collect(Collectors.toList());
  }

  /**
   * v0.7.1
   * https://github.com/ethereum/eth2.0-specs/blob/v0.7.1/specs/core/0_beacon-chain.md#helper-functions-1
   * Return a sorted list of all the distinct Validators that have attested in the given list of
   * attestations
   *
   * @param state
   * @param attestations
   * @return
   */
  private static List<Integer> get_unslashed_attesting_indices(
      BeaconState state, List<PendingAttestation> attestations) {
    TreeSet<Integer> output = new TreeSet<>();
    for (PendingAttestation a : attestations) {
      output.addAll(get_attesting_indices(state, a.getData(), a.getAggregation_bitfield()));
    }
    List<Integer> output_list = new ArrayList<>(output);
    return output_list.stream()
        .filter(index -> !state.getValidator_registry().get(index).isSlashed())
        .collect(Collectors.toList());
  }

  /**
   * v0.7.1
   * https://github.com/ethereum/eth2.0-specs/blob/v0.7.1/specs/core/0_beacon-chain.md#helper-functions-1
   * Returns the total balance of all the distinct validators that have attested in the given
   * attestations
   *
   * @param state
   * @param attestations
   * @return
   */
  private static UnsignedLong get_attesting_balance(
      BeaconState state, List<PendingAttestation> attestations) {
    return get_total_balance(state, get_unslashed_attesting_indices(state, attestations));
  }

  /**
   * v0.7.1
   * https://github.com/ethereum/eth2.0-specs/blob/v0.7.1/specs/core/0_beacon-chain.md#helper-functions-1
   * Returns the crosslink that has the data root with most balance voting for it, and the list of
   * Validators that voted for that crosslink
   *
   * @param state
   * @param epoch
   * @param shard
   * @return
   * @throws IllegalArgumentException
   */
  private static ImmutablePair<Crosslink, List<Integer>>
      get_winning_crosslink_and_attesting_indices(
          BeaconState state, UnsignedLong epoch, UnsignedLong shard)
          throws IllegalArgumentException {
    Supplier<Stream<PendingAttestation>> attestations =
        () ->
            get_matching_source_attestations(state, epoch).stream()
                .filter(
                    attestation -> attestation.getData().getCrosslink().getShard().equals(shard));

    Stream<Pair<Crosslink, UnsignedLong>> crosslink_attesting_balances =
        attestations
            .get()
            .map(attestation -> attestation.getData().getCrosslink())
            .filter(
                crosslink -> {
                  Bytes32 hash =
                      state.getCurrent_crosslinks().get(shard.intValue()).hash_tree_root();
                  return hash.equals(crosslink.getParent_root())
                      || hash.equals(crosslink.hash_tree_root());
                })
            .map(
                c ->
                    new ImmutablePair<>(
                        c,
                        get_attesting_balance(
                            state,
                            attestations
                                .get()
                                .filter(a -> a.getData().getCrosslink().equals(c))
                                .collect(Collectors.toList()))));

    Optional<Pair<Crosslink, UnsignedLong>> winning_crosslink_balance =
        crosslink_attesting_balances.max(Comparator.comparing(Pair::getRight));

    Crosslink winning_crosslink;
    if (winning_crosslink_balance.isPresent()) {
      winning_crosslink =
          crosslink_attesting_balances
              .filter(cab -> winning_crosslink_balance.get().getRight().equals(cab.getRight()))
              .max(Comparator.comparing(cab -> cab.getLeft().getData_root().toHexString()))
              .get()
              .getLeft();
    } else {
      winning_crosslink = new Crosslink();
    }

    List<PendingAttestation> winning_attestations =
        attestations
            .get()
            .filter(a -> a.getData().getCrosslink().equals(winning_crosslink))
            .collect(Collectors.toList());
    return new ImmutablePair<>(
        winning_crosslink, get_unslashed_attesting_indices(state, winning_attestations));
  }

  /**
   * v0.7.1
   * https://github.com/ethereum/eth2.0-specs/blob/v0.7.1/specs/core/0_beacon-chain.md#justification-and-finalization
   * Processes justification and finalization
   *
   * @param state
   * @throws EpochProcessingException
   */
  public static void process_justification_and_finalization(BeaconState state)
      throws EpochProcessingException {
    try {
      if (get_current_epoch(state)
              .compareTo(UnsignedLong.valueOf(GENESIS_EPOCH).plus(UnsignedLong.ONE))
          <= 0) {
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
      justification_bitfield =
          BitwiseOps.leftShift(justification_bitfield, 1).mod(UnsignedLong.MAX_VALUE);
      state.setJustification_bitfield(justification_bitfield);

      UnsignedLong previous_epoch_matching_target_balance =
          get_attesting_balance(state, get_matching_target_attestations(state, previous_epoch));

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
              .compareTo(get_total_active_balance(state).times(UnsignedLong.valueOf(2)))
          >= 0) {
        state.setCurrent_justified_epoch(current_epoch);
        state.setCurrent_justified_root(get_block_root(state, state.getCurrent_justified_epoch()));
        state.setJustification_bitfield(
            BitwiseOps.or(state.getJustification_bitfield(), UnsignedLong.ONE));
      }

      // Process finalizations
      UnsignedLong bitfield = state.getJustification_bitfield();

      UnsignedLong decimal4 = UnsignedLong.valueOf(4);
      UnsignedLong decimal8 = UnsignedLong.valueOf(8);
      UnsignedLong binary11 = UnsignedLong.valueOf(3);
      UnsignedLong binary111 = UnsignedLong.valueOf(7);

      // The 2nd/3rd/4th most recent epochs are justified, the 2nd using the 4th as source
      if (BitwiseOps.rightShift(bitfield, 1).mod(decimal8).equals(binary111)
          && old_previous_justified_epoch.plus(UnsignedLong.valueOf(3)).equals(current_epoch)) {
        state.setFinalized_epoch(old_previous_justified_epoch);
        state.setFinalized_root(get_block_root(state, state.getFinalized_epoch()));
      }
      // The 2nd/3rd most recent epochs are both justified, the 2nd using the 3rd as source
      if (BitwiseOps.rightShift(bitfield, 1).mod(decimal4).equals(binary11)
          && old_previous_justified_epoch.plus(UnsignedLong.valueOf(2)).equals(current_epoch)) {
        state.setFinalized_epoch(old_previous_justified_epoch);
        state.setFinalized_root(get_block_root(state, state.getFinalized_epoch()));
      }
      // The 1st/2nd/3rd most recent epochs are all justified, the 1st using the 3rd as source
      if (bitfield.mod(decimal8).equals(binary111)
          && old_current_justified_epoch.plus(UnsignedLong.valueOf(2)).equals(current_epoch)) {
        state.setFinalized_epoch(old_current_justified_epoch);
        state.setFinalized_root(get_block_root(state, state.getFinalized_epoch()));
      }
      // The 1st/2nd most recent epochs are both justified, the 1st using the 2nd as source
      if (bitfield.mod(decimal4).equals(binary11)
          && old_current_justified_epoch.plus(UnsignedLong.ONE).equals(current_epoch)) {
        state.setFinalized_epoch(old_current_justified_epoch);
        state.setFinalized_root(get_block_root(state, state.getFinalized_epoch()));
      }
    } catch (IllegalArgumentException e) {
      LOG.log(Level.WARN, e.getMessage());
      throw new EpochProcessingException(e);
    }
  }

  /**
   * v0.7.1
   * https://github.com/ethereum/eth2.0-specs/blob/v0.7.1/specs/core/0_beacon-chain.md#crosslinks
   * Processes crosslink information
   *
   * @param state
   * @throws EpochProcessingException
   */
  public static void process_crosslinks(BeaconState state) throws EpochProcessingException {
    try {
      state.setPrevious_crosslinks(new ArrayList<>(state.getCurrent_crosslinks()));
      UnsignedLong previous_epoch = get_previous_epoch(state);
      UnsignedLong current_epoch = get_current_epoch(state);

      for (UnsignedLong epoch = previous_epoch;
          epoch.compareTo(current_epoch) < 0;
          epoch = epoch.plus(UnsignedLong.ONE)) {
        for (int offset = 0;
            offset < get_epoch_committee_count(state, epoch).intValue();
            offset++) {
          UnsignedLong shard =
              get_epoch_start_shard(state, epoch)
                  .plus(UnsignedLong.valueOf(offset))
                  .mod(UnsignedLong.valueOf(SHARD_COUNT));
          List<Integer> crosslink_committee = get_crosslink_committee(state, epoch, shard);
          Pair<Crosslink, List<Integer>> winning_crosslink_and_attesting_indices =
              get_winning_crosslink_and_attesting_indices(state, epoch, shard);
          Crosslink winning_crosslink = winning_crosslink_and_attesting_indices.getLeft();
          List<Integer> attesting_indices = winning_crosslink_and_attesting_indices.getRight();
          if (UnsignedLong.valueOf(3L)
                  .times(get_total_balance(state, attesting_indices))
                  .compareTo(
                      UnsignedLong.valueOf(2L).times(get_total_balance(state, crosslink_committee)))
              >= 0) {
            state.getCurrent_crosslinks().set(shard.intValue(), winning_crosslink);
          }
        }
      }
    } catch (IllegalArgumentException e) {
      LOG.log(Level.WARN, e.getMessage());
      throw new EpochProcessingException(e);
    }
  }

  /**
   * v0.7.1
   * https://github.com/ethereum/eth2.0-specs/blob/v0.7.1/specs/core/0_beacon-chain.md#rewards-and-penalties-1
   * Returns the base reward specific to the validator with the given index
   *
   * @param state
   * @param index
   * @return
   */
  private static UnsignedLong get_base_reward(BeaconState state, int index) {
    UnsignedLong total_balance = get_total_active_balance(state);
    UnsignedLong effective_balance =
        state.getValidator_registry().get(index).getEffective_balance();
    return effective_balance
        .times(UnsignedLong.valueOf(BASE_REWARD_FACTOR))
        .dividedBy(integer_squareroot(total_balance))
        .dividedBy(UnsignedLong.valueOf(BASE_REWARDS_PER_EPOCH));
  }

  /**
   * v0.7.1
   * https://github.com/ethereum/eth2.0-specs/blob/v0.7.1/specs/core/0_beacon-chain.md#rewards-and-penalties-1
   * Returns rewards and penalties specific to each validator resulting from ttestations
   *
   * @param state
   * @return
   * @throws IllegalArgumentException
   */
  private static ImmutablePair<List<UnsignedLong>, List<UnsignedLong>> get_attestation_deltas(
      BeaconState state) throws IllegalArgumentException {
    UnsignedLong previous_epoch = get_previous_epoch(state);
    UnsignedLong total_balance = get_total_active_balance(state);

    int list_size = state.getValidator_registry().size();
    List<UnsignedLong> rewards = Arrays.asList(new UnsignedLong[list_size]);
    List<UnsignedLong> penalties = Arrays.asList(new UnsignedLong[list_size]);
    for (int i = 0; i < list_size; i++) {
      rewards.set(i, UnsignedLong.ZERO);
      penalties.set(i, UnsignedLong.ZERO);
    }

    List<Integer> eligible_validator_indices =
        IntStream.range(0, state.getValidator_registry().size())
            .filter(
                index -> {
                  Validator validator = state.getValidator_registry().get(index);
                  return is_active_validator(validator, previous_epoch)
                      || (validator.isSlashed()
                          && previous_epoch
                                  .plus(UnsignedLong.ONE)
                                  .compareTo(validator.getWithdrawable_epoch())
                              < 0);
                })
            .boxed()
            .collect(Collectors.toList());

    // Micro-incentives for matching FFG source, FFG target, and head
    List<PendingAttestation> matching_source_attestations =
        get_matching_source_attestations(state, previous_epoch);
    List<PendingAttestation> matching_target_attestations =
        get_matching_target_attestations(state, previous_epoch);
    List<PendingAttestation> matching_head_attestations =
        get_matching_head_attestations(state, previous_epoch);
    List<List<PendingAttestation>> attestation_lists = new ArrayList<>();
    attestation_lists.add(matching_source_attestations);
    attestation_lists.add(matching_target_attestations);
    attestation_lists.add(matching_head_attestations);
    for (List<PendingAttestation> attestations : attestation_lists) {
      List<Integer> unslashed_attesting_indices =
          get_unslashed_attesting_indices(state, attestations);
      UnsignedLong attesting_balance = get_total_balance(state, unslashed_attesting_indices);
      for (Integer index : eligible_validator_indices) {
        if (unslashed_attesting_indices.contains(index)) {
          rewards.set(
              index,
              rewards
                  .get(index)
                  .plus(
                      get_base_reward(state, index)
                          .times(attesting_balance)
                          .dividedBy(total_balance)));
        } else {
          penalties.set(index, penalties.get(index).plus(get_base_reward(state, index)));
        }
      }
    }

    // Proposer and inclusion delay micro-rewards
    for (Integer index : get_unslashed_attesting_indices(state, matching_source_attestations)) {
      matching_source_attestations.stream()
          .filter(
              a ->
                  get_attesting_indices(state, a.getData(), a.getAggregation_bitfield())
                      .contains(index))
          .min(Comparator.comparing(PendingAttestation::getInclusion_delay))
          .ifPresent(
              attestation -> {
                rewards.set(
                    attestation.getProposer_index().intValue(),
                    rewards
                        .get(attestation.getProposer_index().intValue())
                        .plus(
                            get_base_reward(state, index)
                                .dividedBy(UnsignedLong.valueOf(PROPOSER_REWARD_QUOTIENT))));
                rewards.set(
                    index,
                    rewards
                        .get(index)
                        .plus(
                            get_base_reward(state, index)
                                .times(UnsignedLong.valueOf(MIN_ATTESTATION_INCLUSION_DELAY))
                                .dividedBy(attestation.getInclusion_delay())));
              });
    }

    // Inactivity penalty
    UnsignedLong finality_delay = previous_epoch.minus(state.getFinalized_epoch());
    if (finality_delay.longValue() > MIN_EPOCHS_TO_INACTIVITY_PENALTY) {
      List<Integer> matching_target_attesting_indices =
          get_unslashed_attesting_indices(state, matching_target_attestations);
      for (Integer index : eligible_validator_indices) {
        penalties.set(
            index,
            penalties
                .get(index)
                .plus(
                    UnsignedLong.valueOf(BASE_REWARDS_PER_EPOCH)
                        .times(get_base_reward(state, index))));
        if (!matching_target_attesting_indices.contains(index)) {
          penalties.set(
              index,
              penalties
                  .get(index)
                  .plus(
                      state
                          .getValidator_registry()
                          .get(index)
                          .getEffective_balance()
                          .times(finality_delay)
                          .dividedBy(UnsignedLong.valueOf(INACTIVITY_PENALTY_QUOTIENT))));
        }
      }
    }
    return new ImmutablePair<>(rewards, penalties);
  }

  /**
   * v0.7.1
   * https://github.com/ethereum/eth2.0-specs/blob/v0.7.1/specs/core/0_beacon-chain.md#rewards-and-penalties-1
   * Returns rewards and penalties specific to each validator resulting from voting/not-voting on
   * the correct crosslink
   *
   * @param state
   * @return
   * @throws IllegalArgumentException
   */
  private static ImmutablePair<List<UnsignedLong>, List<UnsignedLong>> get_crosslink_deltas(
      BeaconState state) throws IllegalArgumentException {
    int list_size = state.getValidator_registry().size();
    List<UnsignedLong> rewards = Arrays.asList(new UnsignedLong[list_size]);
    List<UnsignedLong> penalties = Arrays.asList(new UnsignedLong[list_size]);
    for (int i = 0; i < list_size; i++) {
      rewards.set(i, UnsignedLong.ZERO);
      penalties.set(i, UnsignedLong.ZERO);
    }

    UnsignedLong epoch = get_previous_epoch(state);

    for (int offset = 0; offset < get_epoch_committee_count(state, epoch).intValue(); offset++) {
      UnsignedLong shard =
          get_epoch_start_shard(state, epoch)
              .plus(UnsignedLong.valueOf(offset))
              .mod(UnsignedLong.valueOf(SHARD_COUNT));
      List<Integer> crosslink_committee = get_crosslink_committee(state, epoch, shard);
      Pair<Crosslink, List<Integer>> winning_crosslink_and_attesting_indices =
          get_winning_crosslink_and_attesting_indices(state, epoch, shard);
      List<Integer> attesting_indices = winning_crosslink_and_attesting_indices.getRight();
      UnsignedLong attesting_balance = get_total_balance(state, attesting_indices);
      UnsignedLong committee_balance = get_total_balance(state, crosslink_committee);
      for (int index : crosslink_committee) {
        UnsignedLong base_reward = get_base_reward(state, index);
        if (attesting_indices.contains(index)) {
          rewards.set(
              index,
              rewards
                  .get(index)
                  .plus(base_reward.times(attesting_balance).dividedBy(committee_balance)));
        } else {
          penalties.set(index, penalties.get(index).plus(base_reward));
        }
      }
    }
    return new ImmutablePair<>(rewards, penalties);
  }

  /**
   * v0.7.1
   * https://github.com/ethereum/eth2.0-specs/blob/v0.7.1/specs/core/0_beacon-chain.md#rewards-and-penalties-1
   * Processes rewards and penalties
   *
   * @param state
   * @throws EpochProcessingException
   */
  public static void process_rewards_and_penalties(BeaconStateWithCache state)
      throws EpochProcessingException {
    try {
      if (get_current_epoch(state).equals(UnsignedLong.valueOf(GENESIS_EPOCH))) {
        return;
      }

      Pair<List<UnsignedLong>, List<UnsignedLong>> attestation_deltas =
          get_attestation_deltas(state);
      List<UnsignedLong> rewards1 = attestation_deltas.getLeft();
      List<UnsignedLong> penalties1 = attestation_deltas.getRight();
      Pair<List<UnsignedLong>, List<UnsignedLong>> crosslink_deltas = get_crosslink_deltas(state);
      List<UnsignedLong> rewards2 = crosslink_deltas.getLeft();
      List<UnsignedLong> penalties2 = crosslink_deltas.getLeft();

      for (int i = 0; i < state.getValidator_registry().size(); i++) {
        increase_balance(state, i, rewards1.get(i).plus(rewards2.get(i)));
        decrease_balance(state, i, penalties1.get(i).plus(penalties2.get(i)));
      }
    } catch (IllegalArgumentException e) {
      LOG.log(Level.WARN, e.getMessage());
      throw new EpochProcessingException(e);
    }
  }

  /**
   * v0.7.1
   * https://github.com/ethereum/eth2.0-specs/blob/v0.7.1/specs/core/0_beacon-chain.md#registry-updates
   * Processes validator registry updates
   *
   * @param state
   * @throws EpochProcessingException
   */
  public static void process_registry_updates(BeaconState state) throws EpochProcessingException {
    try {

      // Process activation eligibility and ejections
      for (int index = 0; index < state.getValidator_registry().size(); index++) {
        Validator validator = state.getValidator_registry().get(index);

        if (validator.getActivation_eligibility_epoch().equals(FAR_FUTURE_EPOCH)
            && validator
                    .getEffective_balance()
                    .compareTo(UnsignedLong.valueOf(MAX_EFFECTIVE_BALANCE))
                >= 0) {
          validator.setActivation_eligibility_epoch(get_current_epoch(state));
        }

        if (is_active_validator(validator, get_current_epoch(state))
            && validator.getEffective_balance().compareTo(UnsignedLong.valueOf(EJECTION_BALANCE))
                <= 0) {
          initiate_validator_exit(state, index);
        }
      }

      // Queue validators eligible for activation and not dequeued for activation prior to finalized
      // epoch
      List<Integer> activation_queue =
          IntStream.range(0, state.getValidator_registry().size())
              .filter(
                  index -> {
                    Validator validator = state.getValidator_registry().get(index);
                    return !validator.getActivation_eligibility_epoch().equals(FAR_FUTURE_EPOCH)
                        && validator
                                .getActivation_epoch()
                                .compareTo(
                                    get_delayed_activation_exit_epoch(state.getFinalized_epoch()))
                            >= 0;
                  })
              .boxed()
              .sorted(
                  (i1, i2) ->
                      state
                          .getValidator_registry()
                          .get(i1)
                          .getActivation_eligibility_epoch()
                          .compareTo(state.getValidator_registry().get(i2).getActivation_epoch()))
              .collect(Collectors.toList());

      for (Integer index : activation_queue.subList(0, get_churn_limit(state).intValue())) {
        Validator validator = state.getValidator_registry().get(index);
        if (validator.getActivation_epoch().equals(FAR_FUTURE_EPOCH)) {
          validator.setActivation_epoch(
              get_delayed_activation_exit_epoch(get_current_epoch(state)));
        }
      }
    } catch (IllegalArgumentException e) {
      LOG.log(Level.WARN, e.getMessage());
      throw new EpochProcessingException(e);
    }
  }

  /**
   * v0.7.1
   * https://github.com/ethereum/eth2.0-specs/blob/v0.7.1/specs/core/0_beacon-chain.md#slashings
   * Processes slashings
   *
   * @param state
   */
  public static void process_slashings(BeaconState state) {
    UnsignedLong current_epoch = get_current_epoch(state);
    UnsignedLong total_balance = get_total_active_balance(state);

    // Compute `total_penalties`
    UnsignedLong total_at_start =
        state
            .getLatest_slashed_balances()
            .get(
                toIntExact(
                    current_epoch
                        .plus(UnsignedLong.ONE)
                        .mod(UnsignedLong.valueOf(LATEST_SLASHED_EXIT_LENGTH))
                        .longValue()));
    UnsignedLong total_at_end =
        state
            .getLatest_slashed_balances()
            .get(
                toIntExact(
                    current_epoch
                        .mod(UnsignedLong.valueOf(LATEST_SLASHED_EXIT_LENGTH))
                        .longValue()));

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
                validator
                    .getEffective_balance()
                    .times(min(total_penalties.times(UnsignedLong.valueOf(3L)), total_balance))
                    .dividedBy(total_balance),
                validator
                    .getEffective_balance()
                    .dividedBy(UnsignedLong.valueOf(Constants.MIN_SLASHING_PENALTY_QUOTIENT)));
        decrease_balance(state, index, penalty);
      }
    }
  }

  /**
   * v0.7.1
   * https://github.com/ethereum/eth2.0-specs/blob/v0.7.1/specs/core/0_beacon-chain.md#final-updates
   * Processes final updates
   *
   * @param state
   */
  public static void process_final_updates(BeaconState state) {
    UnsignedLong current_epoch = get_current_epoch(state);
    UnsignedLong next_epoch = current_epoch.plus(UnsignedLong.ONE);

    // Reset eth1 data votes
    if (state
        .getSlot()
        .plus(UnsignedLong.ONE)
        .mod(UnsignedLong.valueOf(Constants.SLOTS_PER_ETH1_VOTING_PERIOD))
        .equals(UnsignedLong.ZERO)) {
      state.setEth1_data_votes(new ArrayList<>());
    }

    // Update effective balances with hysteresis
    for (int index = 0; index < state.getValidator_registry().size(); index++) {
      Validator validator = state.getValidator_registry().get(index);
      UnsignedLong balance = state.getBalances().get(index);
      long HALF_INCREMENT = Constants.EFFECTIVE_BALANCE_INCREMENT / 2;
      if (balance.compareTo(validator.getEffective_balance()) < 0
          || validator
                  .getEffective_balance()
                  .plus(UnsignedLong.valueOf(3 * HALF_INCREMENT))
                  .compareTo(balance)
              < 0) {
        validator.setEffective_balance(
            min(
                balance.minus(balance.mod(UnsignedLong.valueOf(EFFECTIVE_BALANCE_INCREMENT))),
                UnsignedLong.valueOf(MAX_EFFECTIVE_BALANCE)));
      }
    }

    // Update start shard
    state.setLatest_start_shard(
        state
            .getLatest_start_shard()
            .plus(get_shard_delta(state, current_epoch))
            .mod(UnsignedLong.valueOf(SHARD_COUNT)));

    // Set active index root
    int index_root_position =
        (toIntExact(next_epoch.longValue()) + ACTIVATION_EXIT_DELAY)
            % LATEST_ACTIVE_INDEX_ROOTS_LENGTH;
    List<Integer> active_validator_indices =
        get_active_validator_indices(
            state, next_epoch.plus(UnsignedLong.valueOf(ACTIVATION_EXIT_DELAY)));
    state
        .getLatest_active_index_roots()
        .set(
            index_root_position,
            HashTreeUtil.hash_tree_root(
                SSZTypes.LIST_OF_BASIC,
                active_validator_indices.stream()
                    .map(
                        item -> {
                          UnsignedLong unsignedItem = UnsignedLong.valueOf(item);
                          return SSZ.encodeUInt64(unsignedItem.longValue());
                        })
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
