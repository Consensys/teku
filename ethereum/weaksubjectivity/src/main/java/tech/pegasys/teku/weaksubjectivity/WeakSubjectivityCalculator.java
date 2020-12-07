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

package tech.pegasys.teku.weaksubjectivity;

import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_current_epoch;
import static tech.pegasys.teku.datastructures.util.ValidatorsUtil.get_active_validator_indices;

import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.CheckpointState;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.util.config.Constants;
import tech.pegasys.teku.weaksubjectivity.config.WeakSubjectivityConfig;

/**
 * This utility contains helpers for calculating weak-subjectivity-related values. Logic is derived
 * from: https://notes.ethereum.org/@adiasg/weak-subjectvity-eth2 and:
 * https://github.com/ethereum/eth2.0-specs/blob/weak-subjectivity-guide/specs/phase0/weak-subjectivity.md
 */
public class WeakSubjectivityCalculator {
  private static final UInt64 WITHDRAWAL_DELAY =
      UInt64.valueOf(Constants.MIN_VALIDATOR_WITHDRAWABILITY_DELAY);

  private final UInt64 safetyDecay;
  // Use injectable activeValidatorCalculator to make unit testing simpler
  private final ActiveValidatorCalculator activeValidatorCalculator;

  WeakSubjectivityCalculator(
      final UInt64 safetyDecay, final ActiveValidatorCalculator activeValidatorCalculator) {
    this.safetyDecay = safetyDecay;
    this.activeValidatorCalculator = activeValidatorCalculator;
  }

  public static WeakSubjectivityCalculator create(final WeakSubjectivityConfig config) {
    return new WeakSubjectivityCalculator(
        config.getSafetyDecay(), ActiveValidatorCalculator.DEFAULT);
  }

  /**
   * Determines whether the weak subjectivity period calculated from the latest finalized checkpoint
   * extends through the current epoch.
   *
   * @param finalizedCheckpoint The latest finalized checkpoint
   * @param currentSlot The current slot by clock time
   * @return True if the latest finalized checkpoint is still within the weak subjectivity period
   */
  public boolean isWithinWeakSubjectivityPeriod(
      final CheckpointState finalizedCheckpoint, final UInt64 currentSlot) {
    final int validatorCount = getActiveValidators(finalizedCheckpoint.getState());
    UInt64 wsPeriod = computeWeakSubjectivityPeriod(validatorCount);
    final UInt64 currentEpoch = compute_epoch_at_slot(currentSlot);

    return finalizedCheckpoint
        .getCheckpoint()
        .getEpoch()
        .plus(wsPeriod)
        .isGreaterThanOrEqualTo(currentEpoch);
  }

  /**
   * @param state A trusted / effectively finalized state
   * @return The weak subjectivity period in epochs
   */
  public UInt64 computeWeakSubjectivityPeriod(final BeaconState state) {
    return computeWeakSubjectivityPeriod(getActiveValidators(state));
  }

  // TODO(#2779) - This calculation is still under development, make sure it is updated to the
  // latest when possible
  public UInt64 computeWeakSubjectivityPeriod(final int validatorCount) {
    final UInt64 safeEpochs;
    if (validatorCount > Constants.MIN_PER_EPOCH_CHURN_LIMIT * Constants.CHURN_LIMIT_QUOTIENT) {
      safeEpochs = safetyDecay.times(Constants.CHURN_LIMIT_QUOTIENT).dividedBy(200);
    } else {
      safeEpochs =
          safetyDecay.times(validatorCount).dividedBy(200 * Constants.MIN_PER_EPOCH_CHURN_LIMIT);
    }

    return safeEpochs.plus(WITHDRAWAL_DELAY);
  }

  public int getActiveValidators(final BeaconState state) {
    return activeValidatorCalculator.getActiveValidators(state);
  }

  @FunctionalInterface
  interface ActiveValidatorCalculator {
    ActiveValidatorCalculator DEFAULT =
        (state) -> get_active_validator_indices(state, get_current_epoch(state)).size();

    int getActiveValidators(final BeaconState state);
  }
}
