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

package tech.pegasys.teku.spec.logic.versions.merge.statetransition.epoch;

import tech.pegasys.teku.spec.config.SpecConfigMerge;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.merge.BeaconStateMerge;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateMutators;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.RewardAndPenaltyDeltas;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatusFactory;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatuses;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.BeaconStateAccessorsAltair;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.MiscHelpersAltair;
import tech.pegasys.teku.spec.logic.versions.altair.statetransition.epoch.EpochProcessorAltair;

public class EpochProcessorMerge extends EpochProcessorAltair {

  private final SpecConfigMerge specConfigMerge;

  public EpochProcessorMerge(
      final SpecConfigMerge specConfig,
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
    specConfigMerge = specConfig;
  }

  @Override
  public RewardAndPenaltyDeltas getRewardAndPenaltyDeltas(
      final BeaconState genericState, final ValidatorStatuses validatorStatuses) {
    final BeaconStateMerge state = BeaconStateMerge.required(genericState);
    final RewardsAndPenaltiesCalculatorMerge calculator =
        new RewardsAndPenaltiesCalculatorMerge(
            specConfigMerge,
            state,
            validatorStatuses,
            miscHelpersAltair,
            beaconStateAccessorsAltair);

    return calculator.getDeltas();
  }

  @Override
  protected int getProportionalSlashingMultiplier() {
    return specConfigMerge.getProportionalSlashingMultiplierMerge();
  }
}
