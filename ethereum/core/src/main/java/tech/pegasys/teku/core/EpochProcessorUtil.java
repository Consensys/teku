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

package tech.pegasys.teku.core;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.Math.toIntExact;
import static tech.pegasys.teku.datastructures.util.AttestationUtil.get_attesting_indices;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.all;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_activation_exit_epoch;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_block_root;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_block_root_at_slot;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_current_epoch;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_previous_epoch;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_randao_mix;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_total_active_balance;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_total_active_balance_with_root;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_total_balance;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_validator_churn_limit;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.initiate_validator_exit;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.min;
import static tech.pegasys.teku.datastructures.util.ValidatorsUtil.decrease_balance;
import static tech.pegasys.teku.datastructures.util.ValidatorsUtil.increase_balance;
import static tech.pegasys.teku.datastructures.util.ValidatorsUtil.is_active_validator;
import static tech.pegasys.teku.datastructures.util.ValidatorsUtil.is_eligible_for_activation;
import static tech.pegasys.teku.datastructures.util.ValidatorsUtil.is_eligible_for_activation_queue;
import static tech.pegasys.teku.util.config.Constants.BASE_REWARDS_PER_EPOCH;
import static tech.pegasys.teku.util.config.Constants.BASE_REWARD_FACTOR;
import static tech.pegasys.teku.util.config.Constants.EFFECTIVE_BALANCE_INCREMENT;
import static tech.pegasys.teku.util.config.Constants.EJECTION_BALANCE;
import static tech.pegasys.teku.util.config.Constants.EPOCHS_PER_ETH1_VOTING_PERIOD;
import static tech.pegasys.teku.util.config.Constants.EPOCHS_PER_HISTORICAL_VECTOR;
import static tech.pegasys.teku.util.config.Constants.EPOCHS_PER_SLASHINGS_VECTOR;
import static tech.pegasys.teku.util.config.Constants.GENESIS_EPOCH;
import static tech.pegasys.teku.util.config.Constants.HYSTERESIS_DOWNWARD_MULTIPLIER;
import static tech.pegasys.teku.util.config.Constants.HYSTERESIS_QUOTIENT;
import static tech.pegasys.teku.util.config.Constants.HYSTERESIS_UPWARD_MULTIPLIER;
import static tech.pegasys.teku.util.config.Constants.INACTIVITY_PENALTY_QUOTIENT;
import static tech.pegasys.teku.util.config.Constants.MAX_ATTESTATIONS;
import static tech.pegasys.teku.util.config.Constants.MAX_EFFECTIVE_BALANCE;
import static tech.pegasys.teku.util.config.Constants.MIN_EPOCHS_TO_INACTIVITY_PENALTY;
import static tech.pegasys.teku.util.config.Constants.PROPOSER_REWARD_QUOTIENT;
import static tech.pegasys.teku.util.config.Constants.SLOTS_PER_EPOCH;
import static tech.pegasys.teku.util.config.Constants.SLOTS_PER_HISTORICAL_ROOT;

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import tech.pegasys.teku.core.exceptions.EpochProcessingException;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.state.HistoricalBatch;
import tech.pegasys.teku.datastructures.state.MutableBeaconState;
import tech.pegasys.teku.datastructures.state.PendingAttestation;
import tech.pegasys.teku.datastructures.state.Validator;
import tech.pegasys.teku.ssz.SSZTypes.Bitvector;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;
import tech.pegasys.teku.ssz.SSZTypes.SSZMutableList;

public final class EpochProcessorUtil {

  // State Transition Helper Functions

  /**
   * Returns current or previous epoch attestations depending to the epoch passed in
   *
   * @param state
   * @param epoch
   * @return
   * @throws IllegalArgumentException
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#helper-functions-1</a>
   */
  private static SSZList<PendingAttestation> get_matching_source_attestations(
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
   * Returns source attestations that target the block root of the first block in the given epoch
   *
   * @param state
   * @param epoch
   * @return
   * @throws IllegalArgumentException
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#helper-functions-1</a>
   */
  private static SSZList<PendingAttestation> get_matching_target_attestations(
      BeaconState state, UnsignedLong epoch) throws IllegalArgumentException {
    return get_matching_source_attestations(state, epoch)
        .filter(a -> a.getData().getTarget().getRoot().equals(get_block_root(state, epoch)));
  }

  /**
   * Returns source attestations that have the same beacon head block as the one seen in state
   *
   * @param state
   * @param epoch
   * @return
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#helper-functions-1</a>
   */
  private static SSZList<PendingAttestation> get_matching_head_attestations(
      BeaconState state, UnsignedLong epoch) throws IllegalArgumentException {
    return get_matching_target_attestations(state, epoch)
        .filter(
            a ->
                a.getData()
                    .getBeacon_block_root()
                    .equals(get_block_root_at_slot(state, a.getData().getSlot())));
  }

  /**
   * Return a sorted list of all the distinct Validators that have attested in the given list of
   * attestations
   *
   * @param state
   * @param attestations
   * @return
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#helper-functions-1</a>
   */
  private static List<Integer> get_unslashed_attesting_indices(
      BeaconState state, SSZList<PendingAttestation> attestations) {
    return get_unslashed_attesting_indices(state, attestations, ArrayList::new);
  }

  private static <T extends Collection<Integer>> T get_unslashed_attesting_indices(
      BeaconState state,
      SSZList<PendingAttestation> attestations,
      final Supplier<T> collectionFactory) {
    TreeSet<Integer> output = new TreeSet<>();
    for (PendingAttestation a : attestations) {
      output.addAll(get_attesting_indices(state, a.getData(), a.getAggregation_bits()));
    }
    List<Integer> output_list = new ArrayList<>(output);
    return output_list.stream()
        .filter(index -> !state.getValidators().get(index).isSlashed())
        .collect(Collectors.toCollection(collectionFactory));
  }

  /**
   * Returns the combined effective balance of the set of unslashed validators participating in
   * attestations. Note: get_total_balance returns EFFECTIVE_BALANCE_INCREMENT Gwei minimum to avoid
   * divisions by zero. attestations
   *
   * @param state
   * @param attestations
   * @return
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#helper-functions-1</a>
   */
  private static UnsignedLong get_attesting_balance(
      BeaconState state, SSZList<PendingAttestation> attestations) {
    return get_total_balance(state, get_unslashed_attesting_indices(state, attestations));
  }

  /**
   * Processes justification and finalization
   *
   * @param state
   * @throws EpochProcessingException
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#justification-and-finalization</a>
   */
  public static void process_justification_and_finalization(MutableBeaconState state)
      throws EpochProcessingException {
    try {
      if (get_current_epoch(state)
              .compareTo(UnsignedLong.valueOf(GENESIS_EPOCH).plus(UnsignedLong.ONE))
          <= 0) {
        return;
      }

      UnsignedLong previous_epoch = get_previous_epoch(state);
      UnsignedLong current_epoch = get_current_epoch(state);
      Checkpoint old_previous_justified_checkpoint = state.getPrevious_justified_checkpoint();
      Checkpoint old_current_justified_checkpoint = state.getCurrent_justified_checkpoint();

      // Process justifications
      state.setPrevious_justified_checkpoint(state.getCurrent_justified_checkpoint());
      Bitvector justificationBits = state.getJustification_bits().rightShift(1);

      SSZList<PendingAttestation> matching_target_attestations =
          get_matching_target_attestations(state, previous_epoch);
      if (get_attesting_balance(state, matching_target_attestations)
              .times(UnsignedLong.valueOf(3))
              .compareTo(get_total_active_balance(state).times(UnsignedLong.valueOf(2)))
          >= 0) {
        Checkpoint newCheckpoint =
            new Checkpoint(previous_epoch, get_block_root(state, previous_epoch));
        state.setCurrent_justified_checkpoint(newCheckpoint);
        justificationBits.setBit(1);
      }
      matching_target_attestations = get_matching_target_attestations(state, current_epoch);
      if (get_attesting_balance(state, matching_target_attestations)
              .times(UnsignedLong.valueOf(3))
              .compareTo(get_total_active_balance(state).times(UnsignedLong.valueOf(2)))
          >= 0) {
        Checkpoint newCheckpoint =
            new Checkpoint(current_epoch, get_block_root(state, current_epoch));
        state.setCurrent_justified_checkpoint(newCheckpoint);
        justificationBits.setBit(0);
      }

      state.setJustification_bits(justificationBits);

      // Process finalizations

      // The 2nd/3rd/4th most recent epochs are justified, the 2nd using the 4th as source
      if (all(justificationBits, 1, 4)
          && old_previous_justified_checkpoint
              .getEpoch()
              .plus(UnsignedLong.valueOf(3))
              .equals(current_epoch)) {
        state.setFinalized_checkpoint(old_previous_justified_checkpoint);
      }
      // The 2nd/3rd most recent epochs are justified, the 2nd using the 3rd as source
      if (all(justificationBits, 1, 3)
          && old_previous_justified_checkpoint
              .getEpoch()
              .plus(UnsignedLong.valueOf(2))
              .equals(current_epoch)) {
        state.setFinalized_checkpoint(old_previous_justified_checkpoint);
      }
      // The 1st/2nd/3rd most recent epochs are justified, the 1st using the 3rd as source
      if (all(justificationBits, 0, 3)
          && old_current_justified_checkpoint
              .getEpoch()
              .plus(UnsignedLong.valueOf(2))
              .equals(current_epoch)) {
        state.setFinalized_checkpoint(old_current_justified_checkpoint);
      }
      // The 1st/2nd most recent epochs are justified, the 1st using the 2nd as source
      if (all(justificationBits, 0, 2)
          && old_current_justified_checkpoint
              .getEpoch()
              .plus(UnsignedLong.valueOf(1))
              .equals(current_epoch)) {
        state.setFinalized_checkpoint(old_current_justified_checkpoint);
      }

    } catch (IllegalArgumentException e) {
      throw new EpochProcessingException(e);
    }
  }

  /**
   * Returns the base reward specific to the validator with the given index
   *
   * @param state
   * @param index
   * @return
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#rewards-and-penalties-1</a>
   */
  private static UnsignedLong get_base_reward(BeaconState state, int index) {
    UnsignedLong total_balance_square_root = get_total_active_balance_with_root(state).getRight();
    UnsignedLong effective_balance = state.getValidators().get(index).getEffective_balance();
    return effective_balance
        .times(UnsignedLong.valueOf(BASE_REWARD_FACTOR))
        .dividedBy(total_balance_square_root)
        .dividedBy(BASE_REWARDS_PER_EPOCH);
  }

  private static List<Integer> get_eligible_validator_indices(BeaconState state) {
    final UnsignedLong previous_epoch = get_previous_epoch(state);
    final UnsignedLong previous_epoch_plus_one = previous_epoch.plus(UnsignedLong.ONE);
    return IntStream.range(0, state.getValidators().size())
        .filter(
            index -> {
              final Validator v = state.getValidators().get(index);
              return is_active_validator(v, previous_epoch)
                  || (v.isSlashed()
                      && previous_epoch_plus_one.compareTo(v.getWithdrawable_epoch()) < 0);
            })
        .boxed()
        .collect(Collectors.toList());
  }

  private static UnsignedLong get_proposer_reward(BeaconState state, int attestingIndex) {
    return get_base_reward(state, attestingIndex).dividedBy(PROPOSER_REWARD_QUOTIENT);
  }

  private static UnsignedLong get_finality_delay(BeaconState state) {
    return get_previous_epoch(state).minus(state.getFinalized_checkpoint().getEpoch());
  }

  private static boolean is_in_inactivity_leak(BeaconState state) {
    return get_finality_delay(state).compareTo(MIN_EPOCHS_TO_INACTIVITY_PENALTY) > 0;
  }

  /**
   * Helper with shared logic for use by get source, target and head deltas functions
   *
   * @param state
   * @param attestations
   * @return
   */
  private static Deltas get_attestation_component_deltas(
      BeaconState state, SSZList<PendingAttestation> attestations) {
    int validatorCount = state.getValidators().size();
    List<UnsignedLong> rewards = new ArrayList<>(validatorCount);
    List<UnsignedLong> penalties = new ArrayList<>(validatorCount);
    for (int i = 0; i < validatorCount; i++) {
      rewards.add(UnsignedLong.ZERO);
      penalties.add(UnsignedLong.ZERO);
    }
    UnsignedLong total_balance = get_total_active_balance(state);
    Set<Integer> unslashed_attesting_indices =
        get_unslashed_attesting_indices(state, attestations, HashSet::new);
    UnsignedLong attesting_balance = get_total_balance(state, unslashed_attesting_indices);

    for (int index : get_eligible_validator_indices(state)) {
      if (unslashed_attesting_indices.contains(index)) {
        UnsignedLong increment = EFFECTIVE_BALANCE_INCREMENT;
        if (is_in_inactivity_leak(state)) {
          // Since full base reward will be canceled out by inactivity penalty deltas,
          // optimal participation receives full base reward compensation here.
          add(rewards, index, get_base_reward(state, index));
        } else {
          UnsignedLong reward_numerator =
              get_base_reward(state, index).times(attesting_balance.dividedBy(increment));
          add(rewards, index, reward_numerator.dividedBy(total_balance.dividedBy(increment)));
        }
      } else {
        add(penalties, index, get_base_reward(state, index));
      }
    }
    return new Deltas(rewards, penalties);
  }

  /**
   * Return attester micro-rewards/penalties for source-vote for each validator.
   *
   * @param state
   * @return
   */
  public static Deltas getSourceDeltas(BeaconState state) {
    final SSZList<PendingAttestation> matching_source_attestations =
        get_matching_source_attestations(state, get_previous_epoch(state));
    return get_attestation_component_deltas(state, matching_source_attestations);
  }

  /**
   * Return attester micro-rewards/penalties for target-vote for each validator.
   *
   * @param state
   * @return
   */
  public static Deltas getTargetDeltas(BeaconState state) {
    final SSZList<PendingAttestation> matching_target_attestations =
        get_matching_target_attestations(state, get_previous_epoch(state));
    return get_attestation_component_deltas(state, matching_target_attestations);
  }

  /**
   * Return attester micro-rewards/penalties for head-vote for each validator.
   *
   * @param state
   * @return
   */
  public static Deltas getHeadDeltas(BeaconState state) {
    final SSZList<PendingAttestation> matching_head_attestations =
        get_matching_head_attestations(state, get_previous_epoch(state));
    return get_attestation_component_deltas(state, matching_head_attestations);
  }

  /**
   * Return proposer and inclusion delay micro-rewards/penalties for each validator
   *
   * @param state
   */
  public static Deltas getInclusionDelayDeltas(BeaconState state) {
    int validatorCount = state.getValidators().size();
    List<UnsignedLong> rewards = new ArrayList<>(validatorCount);
    List<UnsignedLong> penalties = new ArrayList<>(validatorCount);
    for (int i = 0; i < validatorCount; i++) {
      rewards.add(UnsignedLong.ZERO);
      penalties.add(UnsignedLong.ZERO);
    }
    SSZList<PendingAttestation> matching_source_attestations =
        get_matching_source_attestations(state, get_previous_epoch(state));
    for (int index : get_unslashed_attesting_indices(state, matching_source_attestations)) {
      Optional<PendingAttestation> attestation =
          matching_source_attestations.stream()
              .filter(
                  a ->
                      get_attesting_indices(state, a.getData(), a.getAggregation_bits())
                          .contains(index))
              .min(Comparator.comparing(PendingAttestation::getInclusion_delay));
      attestation.ifPresent(
          a -> {
            add(
                rewards,
                toIntExact(a.getProposer_index().longValue()),
                get_proposer_reward(state, index));

            UnsignedLong max_attester_reward =
                get_base_reward(state, index).minus(get_proposer_reward(state, index));
            add(rewards, index, max_attester_reward.dividedBy(a.getInclusion_delay()));
          });
    }

    // No penalties associtated with inclusion delay
    // TODO: Don't create a list of zeros for no reason
    return new Deltas(rewards, penalties);
  }

  /**
   * Return inactivity reward/penalty deltas for each validator
   *
   * @param state
   * @return
   */
  public static Deltas getInactivityPenaltyDeltas(BeaconState state) {
    int validatorCount = state.getValidators().size();
    List<UnsignedLong> rewards = new ArrayList<>(validatorCount);
    List<UnsignedLong> penalties = new ArrayList<>(validatorCount);
    for (int i = 0; i < validatorCount; i++) {
      rewards.add(UnsignedLong.ZERO);
      penalties.add(UnsignedLong.ZERO);
    }
    if (is_in_inactivity_leak(state)) {
      SSZList<PendingAttestation> matching_target_attestations =
          get_matching_target_attestations(state, get_previous_epoch(state));
      Set<Integer> matching_target_attesting_indices =
          get_unslashed_attesting_indices(state, matching_target_attestations, HashSet::new);
      for (int index : get_eligible_validator_indices(state)) {
        // If validator is performing optimally this cancels all rewards for a neutral balance
        UnsignedLong base_reward = get_base_reward(state, index);
        add(
            penalties,
            index,
            BASE_REWARDS_PER_EPOCH.times(base_reward).minus(get_proposer_reward(state, index)));
        if (!matching_target_attesting_indices.contains(index)) {
          final UnsignedLong effective_balance =
              state.getValidators().get(index).getEffective_balance();
          add(
              penalties,
              index,
              effective_balance
                  .times(get_finality_delay(state))
                  .dividedBy(INACTIVITY_PENALTY_QUOTIENT));
        }
      }
    }

    // No rewards associated with inactivity penalties
    // TODO: Don't create a list of zeros for no reason
    return new Deltas(rewards, penalties);
  }

  /**
   * Return attestation reward/penalty deltas for each validator
   *
   * @param state
   * @return
   * @throws IllegalArgumentException
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#rewards-and-penalties-1</a>
   */
  private static Deltas get_attestation_deltas(BeaconState state) throws IllegalArgumentException {
    Deltas sourceDeltas = getSourceDeltas(state);
    Deltas targetDeltas = getTargetDeltas(state);
    Deltas headDeltas = getHeadDeltas(state);
    Deltas inclusionDelayDeltas = getInclusionDelayDeltas(state);
    Deltas inactivityDeltas = getInactivityPenaltyDeltas(state);

    List<UnsignedLong> rewards = new ArrayList<>();
    List<UnsignedLong> penalties = new ArrayList<>();
    for (int i = 0; i < state.getValidators().size(); i++) {
      rewards.add(
          sourceDeltas
              .getReward(i)
              .plus(targetDeltas.getReward(i))
              .plus(headDeltas.getReward(i))
              .plus(inclusionDelayDeltas.getReward(i)));
      penalties.add(
          sourceDeltas
              .getPenalty(i)
              .plus(targetDeltas.getPenalty(i))
              .plus(headDeltas.getPenalty(i))
              .plus(inactivityDeltas.getPenalty(i)));
    }
    return new Deltas(rewards, penalties);
  }

  private static Pair<List<UnsignedLong>, List<UnsignedLong>> old_get_attestation_deltas(
      BeaconState state) {
    UnsignedLong previous_epoch = get_previous_epoch(state);
    UnsignedLong total_balance = get_total_active_balance(state);

    int list_size = state.getValidators().size();
    List<UnsignedLong> rewards = new ArrayList<>(list_size);
    List<UnsignedLong> penalties = new ArrayList<>(list_size);
    for (int i = 0; i < list_size; i++) {
      rewards.add(UnsignedLong.ZERO);
      penalties.add(UnsignedLong.ZERO);
    }

    // TODO: Restore this optimization
    Map<Integer, UnsignedLong> eligible_validator_base_rewards =
        IntStream.range(0, state.getValidators().size())
            .parallel()
            .filter(
                index -> {
                  Validator validator = state.getValidators().get(index);
                  return is_active_validator(validator, previous_epoch)
                      || (validator.isSlashed()
                          && previous_epoch
                                  .plus(UnsignedLong.ONE)
                                  .compareTo(validator.getWithdrawable_epoch())
                              < 0);
                })
            .boxed()
            .collect(Collectors.toMap(i -> i, i -> get_base_reward(state, i)));

    // Micro-incentives for matching FFG source, FFG target, and head
    SSZList<PendingAttestation> matching_source_attestations =
        get_matching_source_attestations(state, previous_epoch);
    SSZList<PendingAttestation> matching_target_attestations =
        get_matching_target_attestations(state, previous_epoch);
    SSZList<PendingAttestation> matching_head_attestations =
        get_matching_head_attestations(state, previous_epoch);
    List<SSZList<PendingAttestation>> attestation_lists = new ArrayList<>();
    attestation_lists.add(matching_source_attestations);
    attestation_lists.add(matching_target_attestations);
    attestation_lists.add(matching_head_attestations);
    for (SSZList<PendingAttestation> attestations : attestation_lists) {
      Set<Integer> unslashed_attesting_indices =
          get_unslashed_attesting_indices(state, attestations, HashSet::new);
      UnsignedLong attesting_balance = get_total_balance(state, unslashed_attesting_indices);
      for (Entry<Integer, UnsignedLong> index_base_reward :
          eligible_validator_base_rewards.entrySet()) {
        int index = index_base_reward.getKey();
        if (unslashed_attesting_indices.contains(index)) {
          // Factored out from balance totals to avoid uint64 overflow
          UnsignedLong increment = EFFECTIVE_BALANCE_INCREMENT;
          final UnsignedLong reward_numerator =
              index_base_reward.getValue().times(attesting_balance.dividedBy(increment));
          add(rewards, index, reward_numerator.dividedBy(total_balance.dividedBy(increment)));
        } else {
          add(penalties, index, index_base_reward.getValue());
        }
      }
    }

    // Proposer and inclusion delay micro-rewards
    // map (unslashed attester index) -> (list of source attestations)
    Map<Integer, List<PendingAttestation>> validator_source_attestations =
        matching_source_attestations.stream()
            .flatMap(
                a ->
                    get_unslashed_attesting_indices(state, SSZList.singleton(a)).stream()
                        .map(i -> Pair.of(i, a)))
            .collect(
                Collectors.groupingBy(
                    Pair::getLeft, Collectors.mapping(Pair::getRight, Collectors.toList())));

    // in theory attestation can be be from non eligible validator
    // so we need to fall back to reward calculation in this case
    IntFunction<UnsignedLong> base_reward_func =
        index -> {
          UnsignedLong ret = eligible_validator_base_rewards.get(index);
          if (ret == null) {
            ret = get_base_reward(state, index);
          }
          return ret;
        };

    validator_source_attestations.forEach(
        (index, attestations) ->
            attestations.stream()
                .min(Comparator.comparing(PendingAttestation::getInclusion_delay))
                .ifPresent(
                    attestation -> {
                      UnsignedLong proposer_reward =
                          base_reward_func.apply(index).dividedBy(PROPOSER_REWARD_QUOTIENT);
                      add(rewards, attestation.getProposer_index().intValue(), proposer_reward);
                      UnsignedLong max_attester_reward =
                          base_reward_func.apply(index).minus(proposer_reward);
                      add(
                          rewards,
                          index,
                          max_attester_reward.dividedBy(attestation.getInclusion_delay()));
                    }));

    // Inactivity penalty
    UnsignedLong finality_delay = previous_epoch.minus(state.getFinalized_checkpoint().getEpoch());
    if (finality_delay.longValue() > MIN_EPOCHS_TO_INACTIVITY_PENALTY.longValue()) {
      Set<Integer> matching_target_attesting_indices =
          get_unslashed_attesting_indices(state, matching_target_attestations, HashSet::new);

      for (Entry<Integer, UnsignedLong> index_base_reward :
          eligible_validator_base_rewards.entrySet()) {
        int index = index_base_reward.getKey();
        add(penalties, index, BASE_REWARDS_PER_EPOCH.times(index_base_reward.getValue()));
        if (!matching_target_attesting_indices.contains(index)) {
          add(
              penalties,
              index,
              state
                  .getValidators()
                  .get(index)
                  .getEffective_balance()
                  .times(finality_delay)
                  .dividedBy(INACTIVITY_PENALTY_QUOTIENT));
        }
      }
    }
    return new ImmutablePair<>(rewards, penalties);
  }

  private static void add(final List<UnsignedLong> list, int index, UnsignedLong amount) {
    final UnsignedLong current = list.get(index);
    list.set(index, current.plus(amount));
  }

  /**
   * Processes rewards and penalties
   *
   * @param state
   * @throws EpochProcessingException
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#rewards-and-penalties-1</a>
   */
  public static void process_rewards_and_penalties(MutableBeaconState state)
      throws EpochProcessingException {
    try {
      if (get_current_epoch(state).equals(UnsignedLong.valueOf(GENESIS_EPOCH))) {
        return;
      }

      Deltas attestation_deltas = get_attestation_deltas(state);

      for (int i = 0; i < state.getValidators().size(); i++) {
        increase_balance(state, i, attestation_deltas.getReward(i));
        decrease_balance(state, i, attestation_deltas.getPenalty(i));
      }
    } catch (IllegalArgumentException e) {
      throw new EpochProcessingException(e);
    }
  }

  /**
   * Processes validator registry updates
   *
   * @param state
   * @throws EpochProcessingException
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#registry-updates</a>
   */
  public static void process_registry_updates(MutableBeaconState state)
      throws EpochProcessingException {
    try {

      // Process activation eligibility and ejections
      SSZMutableList<Validator> validators = state.getValidators();
      for (int index = 0; index < validators.size(); index++) {
        Validator validator = validators.get(index);

        if (is_eligible_for_activation_queue(validator)) {
          validators.set(
              index,
              validator.withActivation_eligibility_epoch(
                  get_current_epoch(state).plus(UnsignedLong.ONE)));
        }

        if (is_active_validator(validator, get_current_epoch(state))
            && validator.getEffective_balance().compareTo(UnsignedLong.valueOf(EJECTION_BALANCE))
                <= 0) {
          initiate_validator_exit(state, index);
        }
      }

      // Queue validators eligible for activation and not yet dequeued for activation
      List<Integer> activation_queue =
          IntStream.range(0, state.getValidators().size())
              .sequential()
              .filter(
                  index -> {
                    Validator validator = state.getValidators().get(index);
                    return is_eligible_for_activation(state, validator);
                  })
              .boxed()
              .sorted(
                  (index1, index2) -> {
                    int comparisonResult =
                        state
                            .getValidators()
                            .get(index1)
                            .getActivation_eligibility_epoch()
                            .compareTo(
                                state
                                    .getValidators()
                                    .get(index2)
                                    .getActivation_eligibility_epoch());
                    if (comparisonResult == 0) {
                      return index1.compareTo(index2);
                    } else {
                      return comparisonResult;
                    }
                  })
              .collect(Collectors.toList());

      // Dequeued validators for activation up to churn limit (without resetting activation epoch)
      int churn_limit = get_validator_churn_limit(state).intValue();
      int sublist_size = Math.min(churn_limit, activation_queue.size());
      for (Integer index : activation_queue.subList(0, sublist_size)) {
        state
            .getValidators()
            .update(
                index,
                validator ->
                    validator.withActivation_epoch(
                        compute_activation_exit_epoch(get_current_epoch(state))));
      }
    } catch (IllegalArgumentException e) {
      throw new EpochProcessingException(e);
    }
  }

  /**
   * Processes slashings
   *
   * @param state
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#slashings</a>
   */
  public static void process_slashings(MutableBeaconState state) {
    UnsignedLong epoch = get_current_epoch(state);
    UnsignedLong total_balance = get_total_active_balance(state);

    SSZList<Validator> validators = state.getValidators();
    for (int index = 0; index < validators.size(); index++) {
      Validator validator = validators.get(index);
      if (validator.isSlashed()
          && epoch
              .plus(UnsignedLong.valueOf(EPOCHS_PER_SLASHINGS_VECTOR / 2))
              .equals(validator.getWithdrawable_epoch())) {
        UnsignedLong increment = EFFECTIVE_BALANCE_INCREMENT;
        UnsignedLong penalty_numerator =
            validator
                .getEffective_balance()
                .dividedBy(increment)
                .times(
                    min(
                        UnsignedLong.valueOf(
                            state.getSlashings().stream().mapToLong(UnsignedLong::longValue).sum()
                                * 3),
                        total_balance));
        UnsignedLong penalty = penalty_numerator.dividedBy(total_balance).times(increment);
        decrease_balance(state, index, penalty);
      }
    }
  }

  /**
   * Processes final updates
   *
   * @param state
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#final-updates</a>
   */
  public static void process_final_updates(MutableBeaconState state) {
    UnsignedLong current_epoch = get_current_epoch(state);
    UnsignedLong next_epoch = current_epoch.plus(UnsignedLong.ONE);

    // Reset eth1 data votes
    if (next_epoch
        .mod(UnsignedLong.valueOf(EPOCHS_PER_ETH1_VOTING_PERIOD))
        .equals(UnsignedLong.ZERO)) {
      state.getEth1_data_votes().clear();
    }

    // Update effective balances with hysteresis
    SSZMutableList<Validator> validators = state.getValidators();
    SSZList<UnsignedLong> balances = state.getBalances();
    for (int index = 0; index < validators.size(); index++) {
      Validator validator = validators.get(index);
      UnsignedLong balance = balances.get(index);

      final UnsignedLong hysteresis_increment =
          EFFECTIVE_BALANCE_INCREMENT.dividedBy(HYSTERESIS_QUOTIENT);
      final UnsignedLong downward_threshold =
          hysteresis_increment.times(HYSTERESIS_DOWNWARD_MULTIPLIER);
      final UnsignedLong upward_threshold =
          hysteresis_increment.times(HYSTERESIS_UPWARD_MULTIPLIER);
      if (balance.plus(downward_threshold).compareTo(validator.getEffective_balance()) < 0
          || validator.getEffective_balance().plus(upward_threshold).compareTo(balance) < 0) {
        state
            .getValidators()
            .set(
                index,
                validator.withEffective_balance(
                    min(
                        balance.minus(balance.mod(EFFECTIVE_BALANCE_INCREMENT)),
                        UnsignedLong.valueOf(MAX_EFFECTIVE_BALANCE))));
      }
    }

    // Reset slashings
    int index = next_epoch.mod(UnsignedLong.valueOf(EPOCHS_PER_SLASHINGS_VECTOR)).intValue();
    state.getSlashings().set(index, UnsignedLong.ZERO);

    // Set randao mix
    final int randaoIndex =
        toIntExact(next_epoch.mod(UnsignedLong.valueOf(EPOCHS_PER_HISTORICAL_VECTOR)).longValue());
    state.getRandao_mixes().set(randaoIndex, get_randao_mix(state, current_epoch));

    // Set historical root accumulator
    if (next_epoch
        .mod(UnsignedLong.valueOf(SLOTS_PER_HISTORICAL_ROOT / SLOTS_PER_EPOCH))
        .equals(UnsignedLong.ZERO)) {
      HistoricalBatch historical_batch =
          new HistoricalBatch(state.getBlock_roots(), state.getState_roots());
      state.getHistorical_roots().add(historical_batch.hash_tree_root());
    }

    // Rotate current/previous epoch attestations
    state.getPrevious_epoch_attestations().setAll(state.getCurrent_epoch_attestations());
    state
        .getCurrent_epoch_attestations()
        .setAll(
            SSZList.createMutable(PendingAttestation.class, MAX_ATTESTATIONS * SLOTS_PER_EPOCH));
  }
}
