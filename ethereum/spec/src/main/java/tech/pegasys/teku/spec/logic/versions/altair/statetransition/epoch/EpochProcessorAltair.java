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

import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateAltair;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.MutableBeaconStateAltair;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.AbstractEpochProcessor;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.RewardAndPenaltyDeltas;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatusFactory;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatuses;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.EpochProcessingException;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.BeaconStateAccessorsAltair;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.MiscHelpersAltair;
import tech.pegasys.teku.ssz.primitive.SszByte;

public class EpochProcessorAltair extends AbstractEpochProcessor {

  private final SpecConfigAltair specConfigAltair;
  private final MiscHelpersAltair miscHelpersAltair;
  private final BeaconStateAccessorsAltair beaconStateAccessorsAltair;

  public EpochProcessorAltair(
      final SpecConfigAltair specConfig,
      final MiscHelpersAltair miscHelpers,
      final ValidatorsUtil validatorsUtil,
      final BeaconStateUtil beaconStateUtil,
      final ValidatorStatusFactory validatorStatusFactory,
      final BeaconStateAccessorsAltair beaconStateAccessors) {
    super(
        specConfig,
        miscHelpers,
        validatorsUtil,
        beaconStateUtil,
        validatorStatusFactory,
        beaconStateAccessors);
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

  @Override
  protected void processEpoch(final BeaconState preState, final MutableBeaconState state)
      throws EpochProcessingException {
    super.processEpoch(preState, state);
    processSyncCommitteeUpdates(state.toMutableVersionAltair().orElseThrow());
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

    state.getPreviousEpochParticipation().setAll(state.getCurrentEpochParticipation());

    // Reset current epoch participation flags
    state.clearCurrentEpochParticipation();
    state.getCurrentEpochParticipation().setAll(SszByte.ZERO, state.getValidators().size());
  }

  protected void processSyncCommitteeUpdates(final MutableBeaconStateAltair state) {
    final UInt64 nextEpoch = beaconStateAccessors.getCurrentEpoch(state).increment();
    if (nextEpoch.mod(specConfigAltair.getEpochsPerSyncCommitteePeriod()).isZero()) {
      state.setCurrentSyncCommittee(state.getNextSyncCommittee());
      state.setNextSyncCommittee(
          beaconStateAccessorsAltair.getSyncCommittee(
              state, nextEpoch.plus(specConfigAltair.getEpochsPerSyncCommitteePeriod())));
    }
  }
}
