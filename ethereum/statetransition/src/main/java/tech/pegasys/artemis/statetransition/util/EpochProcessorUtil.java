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
import static tech.pegasys.artemis.datastructures.util.AttestationUtil.get_attesting_indices;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.all;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_activation_exit_epoch;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_block_root;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_block_root_at_slot;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_current_epoch;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_previous_epoch;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_randao_mix;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_total_active_balance;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_total_balance;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_validator_churn_limit;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.initiate_validator_exit;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.integer_squareroot;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.min;
import static tech.pegasys.artemis.datastructures.util.ValidatorsUtil.decrease_balance;
import static tech.pegasys.artemis.datastructures.util.ValidatorsUtil.increase_balance;
import static tech.pegasys.artemis.datastructures.util.ValidatorsUtil.is_active_validator;
import static tech.pegasys.artemis.datastructures.util.ValidatorsUtil.is_eligible_for_activation;
import static tech.pegasys.artemis.datastructures.util.ValidatorsUtil.is_eligible_for_activation_queue;
import static tech.pegasys.artemis.util.config.Constants.BASE_REWARDS_PER_EPOCH;
import static tech.pegasys.artemis.util.config.Constants.BASE_REWARD_FACTOR;
import static tech.pegasys.artemis.util.config.Constants.EFFECTIVE_BALANCE_INCREMENT;
import static tech.pegasys.artemis.util.config.Constants.EJECTION_BALANCE;
import static tech.pegasys.artemis.util.config.Constants.EPOCHS_PER_HISTORICAL_VECTOR;
import static tech.pegasys.artemis.util.config.Constants.EPOCHS_PER_SLASHINGS_VECTOR;
import static tech.pegasys.artemis.util.config.Constants.GENESIS_EPOCH;
import static tech.pegasys.artemis.util.config.Constants.INACTIVITY_PENALTY_QUOTIENT;
import static tech.pegasys.artemis.util.config.Constants.MAX_ATTESTATIONS;
import static tech.pegasys.artemis.util.config.Constants.MAX_EFFECTIVE_BALANCE;
import static tech.pegasys.artemis.util.config.Constants.MIN_EPOCHS_TO_INACTIVITY_PENALTY;
import static tech.pegasys.artemis.util.config.Constants.PROPOSER_REWARD_QUOTIENT;
import static tech.pegasys.artemis.util.config.Constants.SLOTS_PER_EPOCH;
import static tech.pegasys.artemis.util.config.Constants.SLOTS_PER_HISTORICAL_ROOT;

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.datastructures.state.HistoricalBatch;
import tech.pegasys.artemis.datastructures.state.MutableBeaconState;
import tech.pegasys.artemis.datastructures.state.MutableValidator;
import tech.pegasys.artemis.datastructures.state.PendingAttestation;
import tech.pegasys.artemis.datastructures.state.Validator;
import tech.pegasys.artemis.util.SSZTypes.Bitvector;
import tech.pegasys.artemis.util.SSZTypes.SSZList;
import tech.pegasys.artemis.util.SSZTypes.SSZListRead;
import tech.pegasys.artemis.util.SSZTypes.SSZListWriteRef;
import tech.pegasys.artemis.util.config.Constants;

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
   * Returns source attestations that target the block root of the first block in the given epoch
   *
   * @param state
   * @param epoch
   * @return
   * @throws IllegalArgumentException
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#helper-functions-1</a>
   */
  private static List<PendingAttestation> get_matching_target_attestations(
      BeaconState state, UnsignedLong epoch) throws IllegalArgumentException {
    return get_matching_source_attestations(state, epoch).stream()
        .filter(a -> a.getData().getTarget().getRoot().equals(get_block_root(state, epoch)))
        .collect(Collectors.toList());
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
  private static List<PendingAttestation> get_matching_head_attestations(
      BeaconState state, UnsignedLong epoch) throws IllegalArgumentException {
    return get_matching_source_attestations(state, epoch).stream()
        .filter(
            a ->
                a.getData()
                    .getBeacon_block_root()
                    .equals(get_block_root_at_slot(state, a.getData().getSlot())))
        .collect(Collectors.toList());
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
      BeaconState state, List<PendingAttestation> attestations) {
    return get_unslashed_attesting_indices(state, attestations, ArrayList::new);
  }

  private static <T extends Collection<Integer>> T get_unslashed_attesting_indices(
      BeaconState state,
      List<PendingAttestation> attestations,
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
   * Returns the total balance of all the distinct validators that have attested in the given
   * attestations
   *
   * @param state
   * @param attestations
   * @return
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#helper-functions-1</a>
   */
  private static UnsignedLong get_attesting_balance(
      BeaconState state, List<PendingAttestation> attestations) {
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

      List<PendingAttestation> matching_target_attestations =
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
    UnsignedLong total_balance = get_total_active_balance(state);
    UnsignedLong effective_balance = state.getValidators().get(index).getEffective_balance();
    return effective_balance
        .times(UnsignedLong.valueOf(BASE_REWARD_FACTOR))
        .dividedBy(integer_squareroot(total_balance))
        .dividedBy(UnsignedLong.valueOf(BASE_REWARDS_PER_EPOCH));
  }

  /**
   * Returns rewards and penalties specific to each validator resulting from ttestations
   *
   * @param state
   * @return
   * @throws IllegalArgumentException
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#rewards-and-penalties-1</a>
   */
  private static ImmutablePair<List<UnsignedLong>, List<UnsignedLong>> get_attestation_deltas(
      BeaconState state) throws IllegalArgumentException {
    UnsignedLong previous_epoch = get_previous_epoch(state);
    UnsignedLong total_balance = get_total_active_balance(state);

    int list_size = state.getValidators().size();
    List<UnsignedLong> rewards = Arrays.asList(new UnsignedLong[list_size]);
    List<UnsignedLong> penalties = Arrays.asList(new UnsignedLong[list_size]);
    for (int i = 0; i < list_size; i++) {
      rewards.set(i, UnsignedLong.ZERO);
      penalties.set(i, UnsignedLong.ZERO);
    }

    List<Integer> eligible_validator_indices =
        IntStream.range(0, state.getValidators().size())
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
      Set<Integer> unslashed_attesting_indices =
          get_unslashed_attesting_indices(state, attestations, HashSet::new);
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
    // map (unslashed attester index) -> (list of source attestations)
    Map<Integer, List<PendingAttestation>> validator_source_attestations =
        matching_source_attestations.stream()
            .flatMap(
                a ->
                    get_unslashed_attesting_indices(state, Collections.singletonList(a)).stream()
                        .map(i -> Pair.of(i, a)))
            .collect(
                Collectors.groupingBy(
                    Pair::getLeft, Collectors.mapping(Pair::getRight, Collectors.toList())));

    validator_source_attestations.forEach(
        (index, attestations) ->
            attestations.stream()
                .min(Comparator.comparing(PendingAttestation::getInclusion_delay))
                .ifPresent(
                    attestation -> {
                      UnsignedLong proposer_reward =
                          get_base_reward(state, index)
                              .dividedBy(UnsignedLong.valueOf(PROPOSER_REWARD_QUOTIENT));
                      rewards.set(
                          attestation.getProposer_index().intValue(),
                          rewards
                              .get(attestation.getProposer_index().intValue())
                              .plus(proposer_reward));
                      UnsignedLong max_attester_reward =
                          get_base_reward(state, index).minus(proposer_reward);
                      rewards.set(
                          index,
                          rewards
                              .get(index)
                              .plus(
                                  max_attester_reward.dividedBy(attestation.getInclusion_delay())));
                    }));

    // Inactivity penalty
    UnsignedLong finality_delay = previous_epoch.minus(state.getFinalized_checkpoint().getEpoch());
    if (finality_delay.longValue() > MIN_EPOCHS_TO_INACTIVITY_PENALTY) {
      Set<Integer> matching_target_attesting_indices =
          get_unslashed_attesting_indices(state, matching_target_attestations, HashSet::new);

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
                          .getValidators()
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

      Pair<List<UnsignedLong>, List<UnsignedLong>> attestation_deltas =
          get_attestation_deltas(state);
      List<UnsignedLong> rewards = attestation_deltas.getLeft();
      List<UnsignedLong> penalties = attestation_deltas.getRight();

      for (int i = 0; i < state.getValidators().size(); i++) {
        increase_balance(state, i, rewards.get(i));
        decrease_balance(state, i, penalties.get(i));
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
  public static void process_registry_updates(MutableBeaconState state) throws EpochProcessingException {
    try {

      // Process activation eligibility and ejections
      SSZListWriteRef<Validator, MutableValidator> validators = state.getValidators();
      for (int index = 0; index < validators.size(); index++) {
        MutableValidator validator = validators.get(index);

        if (is_eligible_for_activation_queue(validator)) {
          validator.setActivation_eligibility_epoch(
              get_current_epoch(state).plus(UnsignedLong.ONE));
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
        MutableValidator validator = state.getValidators().get(index);
        validator.setActivation_epoch(compute_activation_exit_epoch(get_current_epoch(state)));
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

    SSZListRead<Validator> validators = state.getValidators();
    for (int index = 0; index < validators.size(); index++) {
      Validator validator = validators.get(index);
      if (validator.isSlashed()
          && epoch
              .plus(UnsignedLong.valueOf(EPOCHS_PER_SLASHINGS_VECTOR / 2))
              .equals(validator.getWithdrawable_epoch())) {
        UnsignedLong increment = UnsignedLong.valueOf(EFFECTIVE_BALANCE_INCREMENT);
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
    if (state
        .getSlot()
        .plus(UnsignedLong.ONE)
        .mod(UnsignedLong.valueOf(Constants.SLOTS_PER_ETH1_VOTING_PERIOD))
        .equals(UnsignedLong.ZERO)) {
      state.getEth1_data_votes().clear();
    }

    // Update effective balances with hysteresis
    SSZListWriteRef<Validator, MutableValidator> validators = state.getValidators();
    List<UnsignedLong> balances = state.getBalances();
    for (int index = 0; index < validators.size(); index++) {
      MutableValidator validator = validators.get(index);
      UnsignedLong balance = balances.get(index);
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
    state.getCurrent_epoch_attestations().setAll(
        SSZList.create(PendingAttestation.class, MAX_ATTESTATIONS * SLOTS_PER_EPOCH));
  }
}
