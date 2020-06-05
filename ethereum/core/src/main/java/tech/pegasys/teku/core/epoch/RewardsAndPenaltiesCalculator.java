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
import static java.util.stream.Collectors.toMap;
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

import com.google.common.base.Suppliers;
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
import java.util.function.Supplier;
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
  private final Supplier<Map<Integer, UnsignedLong>> eligibleValidatorBaseRewards;
  private final boolean isInInactivityLeak;

  public RewardsAndPenaltiesCalculator(
      final BeaconState state, final MatchingAttestations matchingAttestations) {
    this.state = state;
    this.matchingAttestations = matchingAttestations;
    noValues = Collections.nCopies(state.getValidators().size(), UnsignedLong.ZERO);
    eligibleValidatorBaseRewards = Suppliers.memoize(this::calculateEligibleValidatorBaseRewards);
    isInInactivityLeak = getFinalityDelay().compareTo(MIN_EPOCHS_TO_INACTIVITY_PENALTY) > 0;
  }

  private Map<Integer, UnsignedLong> getEligibleValidatorBaseRewards() {
    return eligibleValidatorBaseRewards.get();
  }

  /**
   * Returns the base reward specific to the validator with the given index
   *
   * @param index
   * @return
   */
  private UnsignedLong getBaseReward(int index) {
    final UnsignedLong baseReward = getEligibleValidatorBaseRewards().get(index);
    return baseReward != null ? baseReward : calculateBaseReward(index);
  }

  private UnsignedLong calculateBaseReward(int index) {
    UnsignedLong totalBalanceSquareRoot = get_total_active_balance_with_root(state).getRight();
    UnsignedLong effectiveBalance = state.getValidators().get(index).getEffective_balance();
    return effectiveBalance
        .times(UnsignedLong.valueOf(BASE_REWARD_FACTOR))
        .dividedBy(totalBalanceSquareRoot)
        .dividedBy(BASE_REWARDS_PER_EPOCH);
  }

  private Map<Integer, UnsignedLong> calculateEligibleValidatorBaseRewards() {
    final UnsignedLong previousEpoch = get_previous_epoch(state);
    final UnsignedLong previousEpochPlusOne = previousEpoch.plus(UnsignedLong.ONE);
    return IntStream.range(0, state.getValidators().size())
        .filter(
            index -> {
              final Validator v = state.getValidators().get(index);
              return is_active_validator(v, previousEpoch)
                  || (v.isSlashed()
                      && previousEpochPlusOne.compareTo(v.getWithdrawable_epoch()) < 0);
            })
        .boxed()
        .collect(toMap(i -> i, this::calculateBaseReward));
  }

  private Collection<Integer> getEligibleValidatorIndices() {
    return getEligibleValidatorBaseRewards().keySet();
  }

  private UnsignedLong getProposerReward(int attestingIndex) {
    return getBaseReward(attestingIndex).dividedBy(PROPOSER_REWARD_QUOTIENT);
  }

  private UnsignedLong getFinalityDelay() {
    return get_previous_epoch(state).minus(state.getFinalized_checkpoint().getEpoch());
  }

  /**
   * Helper with shared logic for use by get source, target and head deltas functions
   *
   * @param attestations
   * @return
   */
  private Deltas getAttestationComponentDeltas(SSZList<PendingAttestation> attestations) {
    int validatorCount = state.getValidators().size();
    List<UnsignedLong> rewards = new ArrayList<>(validatorCount);
    List<UnsignedLong> penalties = new ArrayList<>(validatorCount);
    for (int i = 0; i < validatorCount; i++) {
      rewards.add(UnsignedLong.ZERO);
      penalties.add(UnsignedLong.ZERO);
    }
    UnsignedLong totalBalance = get_total_active_balance(state);
    Set<Integer> unslashedAttestingIndices =
        get_unslashed_attesting_indices(state, attestations, HashSet::new);
    UnsignedLong attestingBalance = get_total_balance(state, unslashedAttestingIndices);

    for (int index : getEligibleValidatorIndices()) {
      if (unslashedAttestingIndices.contains(index)) {
        UnsignedLong increment = EFFECTIVE_BALANCE_INCREMENT;

        if (isInInactivityLeak) {
          // Since full base reward will be canceled out by inactivity penalty deltas,
          // optimal participation receives full base reward compensation here.
          add(rewards, index, getBaseReward(index));
        } else {
          UnsignedLong rewardNumerator =
              getBaseReward(index).times(attestingBalance.dividedBy(increment));
          add(rewards, index, rewardNumerator.dividedBy(totalBalance.dividedBy(increment)));
        }
      } else {
        add(penalties, index, getBaseReward(index));
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
    final SSZList<PendingAttestation> matchingSourceAttestations =
        matchingAttestations.getMatchingSourceAttestations(get_previous_epoch(state));
    return getAttestationComponentDeltas(matchingSourceAttestations);
  }

  /**
   * Return attester micro-rewards/penalties for target-vote for each validator.
   *
   * @return
   */
  public Deltas getTargetDeltas() {
    final SSZList<PendingAttestation> matchingTargetAttestations =
        matchingAttestations.getMatchingTargetAttestations(get_previous_epoch(state));
    return getAttestationComponentDeltas(matchingTargetAttestations);
  }

  /**
   * Return attester micro-rewards/penalties for head-vote for each validator.
   *
   * @return
   */
  public Deltas getHeadDeltas() {
    final SSZList<PendingAttestation> matchingHeadAttestations =
        matchingAttestations.getMatchingHeadAttestations(get_previous_epoch(state));
    return getAttestationComponentDeltas(matchingHeadAttestations);
  }

  /** Return proposer and inclusion delay micro-rewards/penalties for each validator */
  public Deltas getInclusionDelayDeltas() {
    int validatorCount = state.getValidators().size();
    List<UnsignedLong> rewards = new ArrayList<>(validatorCount);
    for (int i = 0; i < validatorCount; i++) {
      rewards.add(UnsignedLong.ZERO);
    }
    SSZList<PendingAttestation> matchingSourceAttestations =
        matchingAttestations.getMatchingSourceAttestations(get_previous_epoch(state));
    for (int index : get_unslashed_attesting_indices(state, matchingSourceAttestations)) {
      Optional<PendingAttestation> attestation =
          matchingSourceAttestations.stream()
              .filter(
                  a ->
                      get_attesting_indices(state, a.getData(), a.getAggregation_bits())
                          .contains(index))
              .min(Comparator.comparing(PendingAttestation::getInclusion_delay));
      attestation.ifPresent(
          a -> {
            add(rewards, toIntExact(a.getProposer_index().longValue()), getProposerReward(index));

            UnsignedLong maxAttesterReward = getBaseReward(index).minus(getProposerReward(index));
            add(rewards, index, maxAttesterReward.dividedBy(a.getInclusion_delay()));
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
      SSZList<PendingAttestation> matchingTargetAttestations =
          matchingAttestations.getMatchingTargetAttestations(get_previous_epoch(state));
      Set<Integer> matchingTargetAttestingIndices =
          get_unslashed_attesting_indices(state, matchingTargetAttestations, HashSet::new);
      for (int index : getEligibleValidatorIndices()) {
        // If validator is performing optimally this cancels all rewards for a neutral balance
        UnsignedLong baseReward = getBaseReward(index);
        add(
            penalties,
            index,
            BASE_REWARDS_PER_EPOCH.times(baseReward).minus(getProposerReward(index)));
        if (!matchingTargetAttestingIndices.contains(index)) {
          final UnsignedLong effectiveBalance =
              state.getValidators().get(index).getEffective_balance();
          add(
              penalties,
              index,
              effectiveBalance.times(getFinalityDelay()).dividedBy(INACTIVITY_PENALTY_QUOTIENT));
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
