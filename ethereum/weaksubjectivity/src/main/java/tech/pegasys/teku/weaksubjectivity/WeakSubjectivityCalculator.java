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
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_total_active_balance;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_validator_churn_limit;
import static tech.pegasys.teku.datastructures.util.ValidatorsUtil.get_active_validator_indices;

import com.google.common.annotations.VisibleForTesting;
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
  private final UInt64 safetyDecay;
  // Use injectable StateCalculator to make unit testing simpler
  private final StateCalculator stateCalculator;

  WeakSubjectivityCalculator(final UInt64 safetyDecay, final StateCalculator stateCalculator) {
    this.safetyDecay = safetyDecay;
    this.stateCalculator = stateCalculator;
  }

  public static WeakSubjectivityCalculator create(final WeakSubjectivityConfig config) {
    return new WeakSubjectivityCalculator(config.getSafetyDecay(), StateCalculator.DEFAULT);
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
    UInt64 wsPeriod = computeWeakSubjectivityPeriod(finalizedCheckpoint.getState());
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
    final int activeValidators = stateCalculator.getActiveValidators(state);
    final UInt64 avgActiveValidatorBalance =
        stateCalculator.getAverageActiveBalance(state, activeValidators);
    return computeWeakSubjectivityPeriod(activeValidators, avgActiveValidatorBalance);
  }

  @VisibleForTesting
  UInt64 computeWeakSubjectivityPeriod(
      final int activeValidatorCount, final UInt64 averageActiveValidatorBalance) {
    // Term appearing in the weak subjectivity period calculation due to top-up limits
    final UInt64 maxMinusAvgBalance =
        Constants.MAX_EFFECTIVE_BALANCE.minus(averageActiveValidatorBalance);
    final UInt64 topUpTerm =
        maxMinusAvgBalance.times(Constants.MAX_DEPOSITS).times(Constants.SLOTS_PER_EPOCH);
    // Term appearing in the weak subjectivity period calculation due to deposits
    final UInt64 validatorChurnLimit = get_validator_churn_limit(activeValidatorCount);
    final UInt64 churnMultiplier =
        averageActiveValidatorBalance.times(2).plus(Constants.MAX_EFFECTIVE_BALANCE);
    final UInt64 depositTerm = validatorChurnLimit.times(churnMultiplier);

    final UInt64 dividend =
        averageActiveValidatorBalance.times(activeValidatorCount).times(safetyDecay).times(3);
    final UInt64 divisor = depositTerm.plus(topUpTerm).times(200);
    return dividend.dividedBy(divisor).plus(Constants.MIN_VALIDATOR_WITHDRAWABILITY_DELAY);
  }

  interface StateCalculator {
    StateCalculator DEFAULT =
        new StateCalculator() {
          @Override
          public int getActiveValidators(final BeaconState state) {
            return get_active_validator_indices(state, get_current_epoch(state)).size();
          }

          @Override
          public UInt64 getAverageActiveBalance(
              final BeaconState state, final int activeValidatorCount) {
            final UInt64 totalActiveBalance = get_total_active_balance(state);
            return totalActiveBalance.dividedBy(activeValidatorCount);
          }
        };

    static StateCalculator createStaticCalculator(
        final int activeValidators, final UInt64 avgValidatorBalance) {
      return new StateCalculator() {
        @Override
        public int getActiveValidators(final BeaconState state) {
          return activeValidators;
        }

        @Override
        public UInt64 getAverageActiveBalance(
            final BeaconState state, final int activeValidatorCount) {
          return avgValidatorBalance;
        }
      };
    }

    int getActiveValidators(final BeaconState state);

    UInt64 getAverageActiveBalance(final BeaconState state, final int activeValidatorCount);
  }
}
