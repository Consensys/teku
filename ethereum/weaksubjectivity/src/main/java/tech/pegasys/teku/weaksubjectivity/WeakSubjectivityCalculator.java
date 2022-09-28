/*
 * Copyright ConsenSys Software Inc., 2022
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

import com.google.common.annotations.VisibleForTesting;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.constants.EthConstants;
import tech.pegasys.teku.spec.datastructures.state.CheckpointState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.weaksubjectivity.config.WeakSubjectivityConfig;

/**
 * This utility contains helpers for calculating weak-subjectivity-related values. Logic is derived
 * from <a href=https://notes.ethereum.org/@adiasg/weak-subjectvity-eth2>Weak Subjectivity in
 * Eth2.0</a></a> and the <a
 * href=https://github.com/ethereum/consensus-specs/blob/dev/specs/phase0/weak-subjectivity.md>Weak
 * Subjectivity Guide</a>.
 */
public class WeakSubjectivityCalculator {

  private final Spec spec;
  private final UInt64 safetyDecay;
  // Use injectable StateCalculator to make unit testing simpler
  private final StateCalculator stateCalculator;

  WeakSubjectivityCalculator(
      final Spec spec, final UInt64 safetyDecay, final StateCalculator stateCalculator) {
    this.spec = spec;
    this.safetyDecay = safetyDecay;
    this.stateCalculator = stateCalculator;
  }

  public static WeakSubjectivityCalculator create(final WeakSubjectivityConfig config) {
    final Spec spec = config.getSpec();
    return new WeakSubjectivityCalculator(
        spec, config.getSafetyDecay(), StateCalculator.getDefaultCalculator(spec));
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
    UInt64 wsPeriod = computeWeakSubjectivityPeriod(finalizedCheckpoint);
    final UInt64 currentEpoch = spec.computeEpochAtSlot(currentSlot);

    return finalizedCheckpoint
        .getCheckpoint()
        .getEpoch()
        .plus(wsPeriod)
        .isGreaterThanOrEqualTo(currentEpoch);
  }

  /**
   * @param checkpointState A trusted / effectively finalized checkpoint state
   * @return The weak subjectivity period in epochs
   */
  public UInt64 computeWeakSubjectivityPeriod(final CheckpointState checkpointState) {
    final BeaconState state = checkpointState.getState();
    final int activeValidators = stateCalculator.getActiveValidators(state);
    final UInt64 totalActiveValidatorBalance =
        stateCalculator.getTotalActiveValidatorBalance(state, activeValidators);
    final SpecVersion specVersion = spec.atEpoch(checkpointState.getEpoch());
    return computeWeakSubjectivityPeriod(
        specVersion, activeValidators, totalActiveValidatorBalance);
  }

  @VisibleForTesting
  @SuppressWarnings("JavaCase") // Mathematicians...
  UInt64 computeWeakSubjectivityPeriod(
      final SpecVersion specVersion,
      final int activeValidatorCount,
      final UInt64 totalValidatorBalance) {
    final SpecConfig constants = specVersion.getConfig();
    final UInt64 N = UInt64.valueOf(activeValidatorCount);
    final UInt64 t = totalValidatorBalance.dividedBy(N).dividedBy(EthConstants.ETH_TO_GWEI);
    final UInt64 T = constants.getMaxEffectiveBalance().dividedBy(EthConstants.ETH_TO_GWEI);
    final UInt64 delta =
        specVersion.beaconStateAccessors().getValidatorChurnLimit(activeValidatorCount);
    final UInt64 Delta =
        UInt64.valueOf(constants.getMaxDeposits()).times(constants.getSlotsPerEpoch());
    final UInt64 D = safetyDecay;

    final UInt64 wsPeriod;
    final UInt64 maxBalanceMultiplier = D.times(3).plus(200);
    final UInt64 scaledMaxBalance = T.times(maxBalanceMultiplier);
    final UInt64 scaledAverageBalance = t.times(D.times(12).plus(200));
    if (scaledMaxBalance.isLessThan(scaledAverageBalance)) {
      final UInt64 churnDivisor = delta.times(600).times(t.times(2).plus(T));
      final UInt64 epochsForValidatorChurnSet =
          N.times(scaledAverageBalance.minus(scaledMaxBalance)).dividedBy(churnDivisor);
      final UInt64 epochsForBalanceTopUps =
          N.times(maxBalanceMultiplier).dividedBy(Delta.times(600));

      wsPeriod = epochsForValidatorChurnSet.max(epochsForBalanceTopUps);
    } else {
      final UInt64 divisor = Delta.times(200).times(T.minus(t));
      wsPeriod = N.times(D).times(t).times(3).dividedBy(divisor);
    }

    return wsPeriod.plus(constants.getMinValidatorWithdrawabilityDelay());
  }

  interface StateCalculator {
    static StateCalculator getDefaultCalculator(final Spec spec) {
      return new StateCalculator() {
        @Override
        public int getActiveValidators(final BeaconState state) {
          final UInt64 currentEpoch = spec.getCurrentEpoch(state);
          return spec.getActiveValidatorIndices(state, currentEpoch).size();
        }

        @Override
        public UInt64 getTotalActiveValidatorBalance(
            final BeaconState state, final int activeValidatorCount) {
          return spec.getTotalActiveBalance(state);
        }
      };
    }

    static StateCalculator createStaticCalculator(
        final int activeValidators, final UInt64 totalActiveValidatorBalance) {
      return new StateCalculator() {
        @Override
        public int getActiveValidators(final BeaconState state) {
          return activeValidators;
        }

        @Override
        public UInt64 getTotalActiveValidatorBalance(
            final BeaconState state, final int activeValidatorCount) {
          return totalActiveValidatorBalance;
        }
      };
    }

    int getActiveValidators(final BeaconState state);

    UInt64 getTotalActiveValidatorBalance(final BeaconState state, final int activeValidatorCount);
  }
}
