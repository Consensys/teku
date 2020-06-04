/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.core.epoch;

import static java.lang.Math.toIntExact;
import static tech.pegasys.teku.core.epoch.EpochProcessorUtil.get_unslashed_attesting_indices;
import static tech.pegasys.teku.datastructures.util.AttestationUtil.get_attesting_indices;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_previous_epoch;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_total_active_balance;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_total_active_balance_with_root;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_total_balance;
import static tech.pegasys.teku.datastructures.util.ValidatorsUtil.is_active_validator;
import static tech.pegasys.teku.util.config.Constants.BASE_REWARDS_PER_EPOCH;
import static tech.pegasys.teku.util.config.Constants.BASE_REWARD_FACTOR;
import static tech.pegasys.teku.util.config.Constants.EFFECTIVE_BALANCE_INCREMENT;
import static tech.pegasys.teku.util.config.Constants.INACTIVITY_PENALTY_QUOTIENT;
import static tech.pegasys.teku.util.config.Constants.MIN_EPOCHS_TO_INACTIVITY_PENALTY;
import static tech.pegasys.teku.util.config.Constants.PROPOSER_REWARD_QUOTIENT;

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import tech.pegasys.teku.core.Deltas;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.PendingAttestation;
import tech.pegasys.teku.datastructures.state.Validator;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;

public class RewardsAndPenaltiesCalculator {

  private final BeaconState state;
  private final MatchingAttestations matchingAttestations;
  private final List<UnsignedLong> noValues;
  private final Map<Integer, UnsignedLong> eligibleValidatorBaseRewards;
  private final boolean isInInactivityLeak;

  public RewardsAndPenaltiesCalculator(
      final BeaconState state, final MatchingAttestations matchingAttestations) {
    this.state = state;
    this.matchingAttestations = matchingAttestations;
    noValues = Collections.nCopies(state.getValidators().size(), UnsignedLong.ZERO);
    eligibleValidatorBaseRewards = calculateEligibleValidatorBaseRewards();
    isInInactivityLeak = get_finality_delay().compareTo(MIN_EPOCHS_TO_INACTIVITY_PENALTY) > 0;
  }

  /**
   * Returns the base reward specific to the validator with the given index
   *
   * @param index
   * @return
   */
  private UnsignedLong get_base_reward(int index) {
    final UnsignedLong baseReward = eligibleValidatorBaseRewards.get(index);
    return baseReward != null ? baseReward : calculate_base_reward(index);
  }

  private UnsignedLong calculate_base_reward(int index) {
    UnsignedLong total_balance_square_root = get_total_active_balance_with_root(state).getRight();
    UnsignedLong effective_balance = state.getValidators().get(index).getEffective_balance();
    return effective_balance
        .times(UnsignedLong.valueOf(BASE_REWARD_FACTOR))
        .dividedBy(total_balance_square_root)
        .dividedBy(BASE_REWARDS_PER_EPOCH);
  }

  private Map<Integer, UnsignedLong> calculateEligibleValidatorBaseRewards() {
    final UnsignedLong previous_epoch = get_previous_epoch(state);
    final UnsignedLong previous_epoch_plus_one = previous_epoch.plus(UnsignedLong.ONE);
    return IntStream.range(0, state.getValidators().size())
        .parallel()
        .filter(
            index -> {
              final Validator v = state.getValidators().get(index);
              return is_active_validator(v, previous_epoch)
                  || (v.isSlashed()
                      && previous_epoch_plus_one.compareTo(v.getWithdrawable_epoch()) < 0);
            })
        .boxed()
        .collect(Collectors.toConcurrentMap(i -> i, this::calculate_base_reward));
  }

  private Collection<Integer> get_eligible_validator_indices() {
    return eligibleValidatorBaseRewards.keySet();
  }

  private UnsignedLong get_proposer_reward(int attestingIndex) {
    return get_base_reward(attestingIndex).dividedBy(PROPOSER_REWARD_QUOTIENT);
  }

  private UnsignedLong get_finality_delay() {
    return get_previous_epoch(state).minus(state.getFinalized_checkpoint().getEpoch());
  }

  /**
   * Helper with shared logic for use by get source, target and head deltas functions
   *
   * @param attestations
   * @return
   */
  private Deltas get_attestation_component_deltas(SSZList<PendingAttestation> attestations) {
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

    for (int index : get_eligible_validator_indices()) {
      if (unslashed_attesting_indices.contains(index)) {
        UnsignedLong increment = EFFECTIVE_BALANCE_INCREMENT;

        if (isInInactivityLeak) {
          // Since full base reward will be canceled out by inactivity penalty deltas,
          // optimal participation receives full base reward compensation here.
          add(rewards, index, get_base_reward(index));
        } else {
          UnsignedLong reward_numerator =
              get_base_reward(index).times(attesting_balance.dividedBy(increment));
          add(rewards, index, reward_numerator.dividedBy(total_balance.dividedBy(increment)));
        }
      } else {
        add(penalties, index, get_base_reward(index));
      }
    }
    return new Deltas(rewards, penalties);
  }

  /**
   * Return attester micro-rewards/penalties for source-vote for each validator.
   *
   * @return
   */
  public Deltas getSourceDeltas() {
    final SSZList<PendingAttestation> matching_source_attestations =
        matchingAttestations.getMatchingSourceAttestations(get_previous_epoch(state));
    return get_attestation_component_deltas(matching_source_attestations);
  }

  /**
   * Return attester micro-rewards/penalties for target-vote for each validator.
   *
   * @return
   */
  public Deltas getTargetDeltas() {
    final SSZList<PendingAttestation> matching_target_attestations =
        matchingAttestations.getMatchingTargetAttestations(get_previous_epoch(state));
    return get_attestation_component_deltas(matching_target_attestations);
  }

  /**
   * Return attester micro-rewards/penalties for head-vote for each validator.
   *
   * @return
   */
  public Deltas getHeadDeltas() {
    final SSZList<PendingAttestation> matching_head_attestations =
        matchingAttestations.getMatchingHeadAttestations(get_previous_epoch(state));
    return get_attestation_component_deltas(matching_head_attestations);
  }

  /** Return proposer and inclusion delay micro-rewards/penalties for each validator */
  public Deltas getInclusionDelayDeltas() {
    int validatorCount = state.getValidators().size();
    List<UnsignedLong> rewards = new ArrayList<>(validatorCount);
    for (int i = 0; i < validatorCount; i++) {
      rewards.add(UnsignedLong.ZERO);
    }
    SSZList<PendingAttestation> matching_source_attestations =
        matchingAttestations.getMatchingSourceAttestations(get_previous_epoch(state));
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
            add(rewards, toIntExact(a.getProposer_index().longValue()), get_proposer_reward(index));

            UnsignedLong max_attester_reward =
                get_base_reward(index).minus(get_proposer_reward(index));
            add(rewards, index, max_attester_reward.dividedBy(a.getInclusion_delay()));
          });
    }

    // No penalties associtated with inclusion delay
    return new Deltas(rewards, noValues);
  }

  /**
   * Return inactivity reward/penalty deltas for each validator
   *
   * @return
   */
  public Deltas getInactivityPenaltyDeltas() {
    int validatorCount = state.getValidators().size();
    List<UnsignedLong> penalties = new ArrayList<>(validatorCount);
    for (int i = 0; i < validatorCount; i++) {
      penalties.add(UnsignedLong.ZERO);
    }

    if (isInInactivityLeak) {
      SSZList<PendingAttestation> matching_target_attestations =
          matchingAttestations.getMatchingTargetAttestations(get_previous_epoch(state));
      Set<Integer> matching_target_attesting_indices =
          get_unslashed_attesting_indices(state, matching_target_attestations, HashSet::new);
      for (int index : get_eligible_validator_indices()) {
        // If validator is performing optimally this cancels all rewards for a neutral balance
        UnsignedLong base_reward = get_base_reward(index);
        add(
            penalties,
            index,
            BASE_REWARDS_PER_EPOCH.times(base_reward).minus(get_proposer_reward(index)));
        if (!matching_target_attesting_indices.contains(index)) {
          final UnsignedLong effective_balance =
              state.getValidators().get(index).getEffective_balance();
          add(
              penalties,
              index,
              effective_balance.times(get_finality_delay()).dividedBy(INACTIVITY_PENALTY_QUOTIENT));
        }
      }
    }

    // No rewards associated with inactivity penalties
    return new Deltas(noValues, penalties);
  }

  /**
   * Return attestation reward/penalty deltas for each validator
   *
   * @return
   * @throws IllegalArgumentException
   */
  public Deltas getAttestationDeltas() throws IllegalArgumentException {
    Deltas sourceDeltas = getSourceDeltas();
    Deltas targetDeltas = getTargetDeltas();
    Deltas headDeltas = getHeadDeltas();
    Deltas inclusionDelayDeltas = getInclusionDelayDeltas();
    Deltas inactivityDeltas = getInactivityPenaltyDeltas();

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

  private void add(final List<UnsignedLong> list, int index, UnsignedLong amount) {
    final UnsignedLong current = list.get(index);
    list.set(index, current.plus(amount));
  }
}
