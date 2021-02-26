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

package tech.pegasys.teku.spec.statetransition.epoch;

import java.util.List;
import tech.pegasys.teku.independent.TotalBalances;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.constants.SpecConstants;
import tech.pegasys.teku.spec.datastructures.state.BeaconState;
import tech.pegasys.teku.spec.statetransition.epoch.Deltas.Delta;
import tech.pegasys.teku.spec.statetransition.epoch.status.InclusionInfo;
import tech.pegasys.teku.spec.statetransition.epoch.status.ValidatorStatus;
import tech.pegasys.teku.spec.statetransition.epoch.status.ValidatorStatuses;
import tech.pegasys.teku.spec.util.BeaconStateUtil;

public class RewardsAndPenaltiesCalculator {

  private final SpecConstants specConstants;
  private final BeaconStateUtil beaconStateUtil;
  private final BeaconState state;
  private final ValidatorStatuses validatorStatuses;

  RewardsAndPenaltiesCalculator(
      final SpecConstants specConstants,
      final BeaconStateUtil beaconStateUtil,
      final BeaconState state,
      final ValidatorStatuses validatorStatuses) {
    this.specConstants = specConstants;
    this.beaconStateUtil = beaconStateUtil;
    this.state = state;
    this.validatorStatuses = validatorStatuses;
  }

  /**
   * Return attestation reward/penalty deltas for each validator
   *
   * @return
   * @throws IllegalArgumentException
   */
  public Deltas getAttestationDeltas() throws IllegalArgumentException {
    return getDeltas(this::applyAllDeltas);
  }

  public Deltas getDeltas(final Step step) throws IllegalArgumentException {
    final Deltas deltas = new Deltas(validatorStatuses.getValidatorCount());
    final TotalBalances totalBalances = validatorStatuses.getTotalBalances();
    final List<ValidatorStatus> statuses = validatorStatuses.getStatuses();
    final UInt64 finalityDelay = getFinalityDelay();

    final UInt64 totalActiveBalanceSquareRoot = squareRootOrZero(totalBalances.getCurrentEpoch());

    for (int index = 0; index < statuses.size(); index++) {
      final ValidatorStatus validator = statuses.get(index);
      if (!validator.isEligibleValidator()) {
        continue;
      }

      final UInt64 baseReward = getBaseReward(validator, totalActiveBalanceSquareRoot);
      final Delta delta = deltas.getDelta(index);
      step.apply(deltas, totalBalances, finalityDelay, validator, baseReward, delta);
    }
    return deltas;
  }

  private void applyAllDeltas(
      final Deltas deltas,
      final TotalBalances totalBalances,
      final UInt64 finalityDelay,
      final ValidatorStatus validator,
      final UInt64 baseReward,
      final Delta delta) {
    applySourceDelta(validator, baseReward, totalBalances, finalityDelay, delta);
    applyTargetDelta(validator, baseReward, totalBalances, finalityDelay, delta);
    applyHeadDelta(validator, baseReward, totalBalances, finalityDelay, delta);
    applyInclusionDelayDelta(validator, baseReward, delta, deltas);
    applyInactivityPenaltyDelta(validator, baseReward, finalityDelay, delta);
  }

  public void applySourceDelta(
      final ValidatorStatus validator,
      final UInt64 baseReward,
      final TotalBalances totalBalances,
      final UInt64 finalityDelay,
      final Delta delta) {
    applyAttestationComponentDelta(
        validator.isPreviousEpochAttester() && !validator.isSlashed(),
        totalBalances.getPreviousEpochAttesters(),
        totalBalances,
        baseReward,
        finalityDelay,
        delta);
  }

  public void applyTargetDelta(
      final ValidatorStatus validator,
      final UInt64 baseReward,
      final TotalBalances totalBalances,
      final UInt64 finalityDelay,
      final Delta delta) {
    applyAttestationComponentDelta(
        validator.isPreviousEpochTargetAttester() && !validator.isSlashed(),
        totalBalances.getPreviousEpochTargetAttesters(),
        totalBalances,
        baseReward,
        finalityDelay,
        delta);
  }

  public void applyHeadDelta(
      final ValidatorStatus validator,
      final UInt64 baseReward,
      final TotalBalances totalBalances,
      final UInt64 finalityDelay,
      final Delta delta) {
    applyAttestationComponentDelta(
        validator.isPreviousEpochHeadAttester() && !validator.isSlashed(),
        totalBalances.getPreviousEpochHeadAttesters(),
        totalBalances,
        baseReward,
        finalityDelay,
        delta);
  }

  public void applyInclusionDelayDelta(
      final ValidatorStatus validator,
      final UInt64 baseReward,
      final Delta delta,
      final Deltas deltas) {
    if (validator.isPreviousEpochAttester() && !validator.isSlashed()) {
      final InclusionInfo inclusionInfo =
          validator
              .getInclusionInfo()
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "Validator was active in previous epoch but has no inclusion information."));
      final UInt64 proposerReward = getProposerReward(baseReward);
      final UInt64 maxAttesterReward = baseReward.minus(proposerReward);
      delta.reward(maxAttesterReward.dividedBy(inclusionInfo.getDelay()));

      deltas.getDelta(inclusionInfo.getProposerIndex()).reward(getProposerReward(baseReward));
    }
  }

  public void applyInactivityPenaltyDelta(
      final ValidatorStatus validator,
      final UInt64 baseReward,
      final UInt64 finalityDelay,
      final Delta delta) {

    if (finalityDelay.isGreaterThan(specConstants.getMinEpochsToInactivityPenalty())) {
      // If validator is performing optimally this cancels all rewards for a neutral balance
      delta.penalize(
          specConstants
              .getBaseRewardsPerEpoch()
              .times(baseReward)
              .minus(getProposerReward(baseReward)));

      if (validator.isSlashed() || !validator.isPreviousEpochTargetAttester()) {
        delta.penalize(
            validator
                .getCurrentEpochEffectiveBalance()
                .times(finalityDelay)
                .dividedBy(specConstants.getInactivityPenaltyQuotient()));
      }
    }
  }

  private UInt64 getProposerReward(final UInt64 baseReward) {
    return baseReward.dividedBy(specConstants.getProposerRewardQuotient());
  }

  public void applyAttestationComponentDelta(
      final boolean indexInUnslashedAttestingIndices,
      final UInt64 attestingBalance,
      final TotalBalances totalBalances,
      final UInt64 baseReward,
      final UInt64 finalityDelay,
      final Delta delta) {
    final UInt64 totalBalance = totalBalances.getCurrentEpoch();
    if (indexInUnslashedAttestingIndices) {
      if (finalityDelay.isGreaterThan(specConstants.getMinEpochsToInactivityPenalty())) {
        // Since full base reward will be canceled out by inactivity penalty deltas,
        // optimal participation receives full base reward compensation here.
        delta.reward(baseReward);
      } else {
        final UInt64 rewardNumerator =
            baseReward.times(
                attestingBalance.dividedBy(specConstants.getEffectiveBalanceIncrement()));
        delta.reward(
            rewardNumerator.dividedBy(
                totalBalance.dividedBy(specConstants.getEffectiveBalanceIncrement())));
      }
    } else {
      delta.penalize(baseReward);
    }
  }

  private UInt64 getFinalityDelay() {
    return beaconStateUtil
        .getPreviousEpoch(state)
        .minus(state.getFinalized_checkpoint().getEpoch());
  }

  private UInt64 getBaseReward(
      final ValidatorStatus validator, final UInt64 totalActiveBalanceSquareRoot) {
    if (totalActiveBalanceSquareRoot.isZero()) {
      return UInt64.ZERO;
    }
    return validator
        .getCurrentEpochEffectiveBalance()
        .times(specConstants.getBaseRewardFactor())
        .dividedBy(totalActiveBalanceSquareRoot)
        .dividedBy(specConstants.getBaseRewardsPerEpoch());
  }

  private UInt64 squareRootOrZero(final UInt64 totalActiveBalance) {
    return totalActiveBalance.isZero()
        ? UInt64.ZERO
        : beaconStateUtil.integerSquareRoot(totalActiveBalance);
  }

  public interface Step {
    void apply(
        final Deltas deltas,
        final TotalBalances totalBalances,
        final UInt64 finalityDelay,
        final ValidatorStatus validator,
        final UInt64 baseReward,
        final Delta delta);
  }
}
