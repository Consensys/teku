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

package tech.pegasys.teku.spec.logic.versions.altair.statetransition.epoch;

import static tech.pegasys.teku.spec.constants.IncentivizationWeights.WEIGHT_DENOMINATOR;
import static tech.pegasys.teku.spec.constants.ParticipationFlags.TIMELY_HEAD_FLAG_INDEX;
import static tech.pegasys.teku.spec.logic.versions.altair.helpers.MiscHelpersAltair.PARTICIPATION_FLAG_WEIGHTS;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.constants.ParticipationFlags;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateAltair;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.RewardAndPenaltyDeltas;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.RewardAndPenaltyDeltas.RewardAndPenalty;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.RewardsAndPenaltiesCalculator;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.TotalBalances;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatus;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatuses;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.BeaconStateAccessorsAltair;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.MiscHelpersAltair;

public class RewardsAndPenaltiesCalculatorAltair extends RewardsAndPenaltiesCalculator {

  private final SpecConfigAltair specConfigAltair;
  private final BeaconStateAccessorsAltair beaconStateAccessorsAltair;

  private final BeaconStateAltair stateAltair;
  private final Map<Integer, UInt64> baseRewardCache = new HashMap<>();

  public RewardsAndPenaltiesCalculatorAltair(
      final SpecConfigAltair specConfig,
      final BeaconStateAltair state,
      final ValidatorStatuses validatorStatuses,
      final MiscHelpersAltair miscHelpers,
      final BeaconStateAccessorsAltair beaconStateAccessors) {
    super(specConfig, miscHelpers, beaconStateAccessors, state, validatorStatuses);
    this.stateAltair = state;
    this.specConfigAltair = specConfig;
    this.beaconStateAccessorsAltair = beaconStateAccessors;
  }

  /**
   * Return attestation reward/penalty deltas for each validator
   *
   * @return
   * @throws IllegalArgumentException
   */
  @Override
  public RewardAndPenaltyDeltas getDeltas() throws IllegalArgumentException {
    final RewardAndPenaltyDeltas deltas =
        new RewardAndPenaltyDeltas(validatorStatuses.getValidatorCount());

    for (int flagIndex = 0; flagIndex < PARTICIPATION_FLAG_WEIGHTS.size(); flagIndex++) {
      processFlagIndexDeltas(deltas, flagIndex);
    }
    processInactivityPenaltyDeltas(deltas);

    return deltas;
  }

  /**
   * Corresponds to altair beacon chain accessor get_flag_index_deltas
   *
   * @see <a
   *     href="https://github.com/ethereum/eth2.0-specs/blob/master/specs/altair/beacon-chain.md#beacon-state-accessors">Altair
   *     beacon-chain.md</a>
   * @param deltas The deltas accumulator (holding deltas for all validators) to be updated
   * @param flagIndex The flag index to process
   */
  public void processFlagIndexDeltas(final RewardAndPenaltyDeltas deltas, final int flagIndex) {
    final List<ValidatorStatus> statusList = validatorStatuses.getStatuses();
    final TotalBalances totalBalances = validatorStatuses.getTotalBalances();

    final UInt64 effectiveBalanceIncrement = specConfigAltair.getEffectiveBalanceIncrement();
    final UInt64 unslashedParticipatingIncrements =
        getPrevEpochTotalParticipatingBalance(flagIndex).dividedBy(effectiveBalanceIncrement);
    final UInt64 weight = PARTICIPATION_FLAG_WEIGHTS.get(flagIndex);
    final UInt64 activeIncrements =
        totalBalances.getCurrentEpochActiveValidators().dividedBy(effectiveBalanceIncrement);

    for (int i = 0; i < statusList.size(); i++) {
      final ValidatorStatus validator = statusList.get(i);
      if (!validator.isEligibleValidator()) {
        continue;
      }
      final RewardAndPenalty validatorDeltas = deltas.getDelta(i);

      final UInt64 baseReward = getBaseReward(i);
      if (isUnslashedPrevEpochParticipatingIndex(validator, flagIndex)) {
        if (!isInactivityLeak()) {
          final UInt64 rewardNumerator =
              baseReward.times(weight).times(unslashedParticipatingIncrements);
          validatorDeltas.reward(
              rewardNumerator.dividedBy(activeIncrements.times(WEIGHT_DENOMINATOR)));
        }
      } else if (flagIndex != TIMELY_HEAD_FLAG_INDEX) {
        validatorDeltas.penalize(baseReward.times(weight).dividedBy(WEIGHT_DENOMINATOR));
      }
    }
  }

  /**
   * Corresponds to altair beacon chain accessor get_inactivity_penalty_deltas
   *
   * @see <a
   *     href="https://github.com/ethereum/eth2.0-specs/blob/master/specs/altair/beacon-chain.md#beacon-state-accessors">Altair
   *     beacon-chain.md</a>
   * @param deltas The deltas accumulator (holding deltas for all validators) to be updated
   */
  public void processInactivityPenaltyDeltas(final RewardAndPenaltyDeltas deltas) {
    final List<ValidatorStatus> statusList = validatorStatuses.getStatuses();
    for (int i = 0; i < statusList.size(); i++) {
      final ValidatorStatus validator = statusList.get(i);
      if (!validator.isEligibleValidator()) {
        continue;
      }
      if (validator.isPreviousEpochTargetAttester() && !validator.isSlashed()) {
        continue;
      }

      final UInt64 penaltyNumerator =
          validator
              .getCurrentEpochEffectiveBalance()
              .times(stateAltair.getInactivityScores().get(i).get());
      final UInt64 penaltyDenominator =
          specConfigAltair
              .getInactivityScoreBias()
              .times(specConfigAltair.getInactivityPenaltyQuotientAltair());
      final UInt64 penalty = penaltyNumerator.dividedBy(penaltyDenominator);
      deltas.getDelta(i).penalize(penalty);
    }
  }

  private boolean validatorHasPrevEpochParticipationFlag(
      final ValidatorStatus validator, final int flagIndex) {
    switch (flagIndex) {
      case TIMELY_HEAD_FLAG_INDEX:
        return validator.isPreviousEpochHeadAttester();
      case ParticipationFlags.TIMELY_TARGET_FLAG_INDEX:
        return validator.isPreviousEpochTargetAttester();
      case ParticipationFlags.TIMELY_SOURCE_FLAG_INDEX:
        return validator.isPreviousEpochSourceAttester();
      default:
        throw new IllegalArgumentException("Unable to process unknown flag index:" + flagIndex);
    }
  }

  private UInt64 getPrevEpochTotalParticipatingBalance(final int flagIndex) {
    final TotalBalances totalBalances = validatorStatuses.getTotalBalances();
    switch (flagIndex) {
      case TIMELY_HEAD_FLAG_INDEX:
        return totalBalances.getPreviousEpochHeadAttesters();
      case ParticipationFlags.TIMELY_TARGET_FLAG_INDEX:
        return totalBalances.getPreviousEpochTargetAttesters();
      case ParticipationFlags.TIMELY_SOURCE_FLAG_INDEX:
        return totalBalances.getPreviousEpochSourceAttesters();
      default:
        throw new IllegalArgumentException("Unable to process unknown flag index:" + flagIndex);
    }
  }

  private boolean isUnslashedPrevEpochParticipatingIndex(
      final ValidatorStatus validatorStatus, final int flagIndex) {
    return validatorStatus.isNotSlashed()
        && validatorHasPrevEpochParticipationFlag(validatorStatus, flagIndex);
  }

  private UInt64 getBaseReward(final int validatorIndex) {
    return baseRewardCache.computeIfAbsent(
        validatorIndex, index -> beaconStateAccessorsAltair.getBaseReward(state, validatorIndex));
  }
}
