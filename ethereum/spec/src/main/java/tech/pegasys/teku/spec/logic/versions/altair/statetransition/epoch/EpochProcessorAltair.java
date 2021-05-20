/*
 * Copyright 2021 ConsenSys AG.
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

import java.util.List;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateAltair;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.MutableBeaconStateAltair;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateMutators;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.AbstractEpochProcessor;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.RewardAndPenaltyDeltas;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatus;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatusFactory;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatuses;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.BeaconStateAccessorsAltair;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.MiscHelpersAltair;
import tech.pegasys.teku.ssz.collections.SszMutableUInt64List;
import tech.pegasys.teku.ssz.primitive.SszByte;

public class EpochProcessorAltair extends AbstractEpochProcessor {

  private final SpecConfigAltair specConfigAltair;
  private final MiscHelpersAltair miscHelpersAltair;
  private final BeaconStateAccessorsAltair beaconStateAccessorsAltair;

  public EpochProcessorAltair(
      final SpecConfigAltair specConfig,
      final MiscHelpersAltair miscHelpers,
      final BeaconStateAccessorsAltair beaconStateAccessors,
      final BeaconStateMutators beaconStateMutators,
      final ValidatorsUtil validatorsUtil,
      final BeaconStateUtil beaconStateUtil,
      final ValidatorStatusFactory validatorStatusFactory) {
    super(
        specConfig,
        miscHelpers,
        beaconStateAccessors,
        beaconStateMutators,
        validatorsUtil,
        beaconStateUtil,
        validatorStatusFactory);
    this.specConfigAltair = specConfig;
    this.miscHelpersAltair = miscHelpers;
    this.beaconStateAccessorsAltair = beaconStateAccessors;
  }

  @Override
  public RewardAndPenaltyDeltas getRewardAndPenaltyDeltas(
      final BeaconState genericState, final ValidatorStatuses validatorStatuses) {
    final BeaconStateAltair state = BeaconStateAltair.required(genericState);
    final RewardsAndPenaltiesCalculatorAltair calculator =
        new RewardsAndPenaltiesCalculatorAltair(
            specConfigAltair,
            state,
            validatorStatuses,
            miscHelpersAltair,
            beaconStateAccessorsAltair);

    return calculator.getDeltas();
  }

  /**
   * Corresponds to process_participation_flag_updates in beacon-chain spec
   *
   * @param genericState The state to process
   * @see <a
   *     href="https://github.com/ethereum/eth2.0-specs/blob/master/specs/altair/beacon-chain.md#participation-flags-updates">Altair
   *     Participation Flags updates</a>
   */
  @Override
  public void processParticipationUpdates(final MutableBeaconState genericState) {
    final MutableBeaconStateAltair state = MutableBeaconStateAltair.required(genericState);

    state.setPreviousEpochParticipation(state.getCurrentEpochParticipation());

    // Reset current epoch participation flags
    state.getCurrentEpochParticipation().clear();
    state.getCurrentEpochParticipation().setAll(SszByte.ZERO, state.getValidators().size());
  }

  @Override
  public void processSyncCommitteeUpdates(final MutableBeaconState genericState) {
    final UInt64 nextEpoch = beaconStateAccessors.getCurrentEpoch(genericState).increment();
    if (nextEpoch.mod(specConfigAltair.getEpochsPerSyncCommitteePeriod()).isZero()) {
      final MutableBeaconStateAltair state = MutableBeaconStateAltair.required(genericState);
      state.setCurrentSyncCommittee(state.getNextSyncCommittee());
      state.setNextSyncCommittee(beaconStateAccessorsAltair.getNextSyncCommittee(state));
    }
  }

  @Override
  public void processInactivityUpdates(
      final MutableBeaconState baseState, final ValidatorStatuses validatorStatuses) {
    if (beaconStateAccessors.getCurrentEpoch(baseState).equals(SpecConfig.GENESIS_EPOCH)) {
      return;
    }
    final MutableBeaconStateAltair state = MutableBeaconStateAltair.required(baseState);
    final SszMutableUInt64List inactivityScores = state.getInactivityScores();
    final List<ValidatorStatus> statuses = validatorStatuses.getStatuses();
    final boolean isInInactivityLeak = beaconStateAccessors.isInactivityLeak(state);
    for (int i = 0; i < statuses.size(); i++) {
      final ValidatorStatus validatorStatus = statuses.get(i);
      if (!validatorStatus.isEligibleValidator()) {
        continue;
      }

      // Increase inactivity score of inactive validators
      final UInt64 currentScore = inactivityScores.getElement(i);
      if (validatorStatus.isNotSlashed() && validatorStatus.isPreviousEpochTargetAttester()) {
        if (currentScore.isGreaterThan(0)) {
          inactivityScores.setElement(i, currentScore.decrement());
        }
      } else {
        inactivityScores.setElement(
            i, currentScore.plus(specConfigAltair.getInactivityScoreBias()));
      }
      // Decrease the score of all validators for forgiveness when not during a leak
      if (!isInInactivityLeak) {
        inactivityScores.setElement(
            i, currentScore.minusMinZero(specConfigAltair.getInactivityScoreRecoveryRate()));
      }
    }
  }

  @Override
  protected int getProportionalSlashingMultiplier() {
    return specConfigAltair.getProportionalSlashingMultiplierAltair();
  }
}
