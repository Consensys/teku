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

package tech.pegasys.teku.reference.altair.rewards;

import com.google.common.collect.ImmutableMap;
import tech.pegasys.teku.reference.TestExecutor;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.config.SpecConfigMerge;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.merge.BeaconStateMerge;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatuses;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.BeaconStateAccessorsAltair;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.MiscHelpersAltair;
import tech.pegasys.teku.spec.logic.versions.altair.statetransition.epoch.RewardsAndPenaltiesCalculatorAltair;
import tech.pegasys.teku.spec.logic.versions.merge.statetransition.epoch.RewardsAndPenaltiesCalculatorMerge;

public class RewardsTestExecutorMerge extends RewardsTestExecutorAltair {

  public static final ImmutableMap<String, TestExecutor> REWARDS_TEST_TYPES =
      ImmutableMap.of(
          "rewards/basic", new RewardsTestExecutorMerge(),
          "rewards/leak", new RewardsTestExecutorMerge(),
          "rewards/random", new RewardsTestExecutorMerge());

  @Override
  protected RewardsAndPenaltiesCalculatorAltair createRewardsAndPenaltiesCalculator(
      final BeaconState preState,
      final ValidatorStatuses validatorStatuses,
      final SpecVersion spec) {
    return new RewardsAndPenaltiesCalculatorMerge(
        SpecConfigMerge.required(spec.getConfig()),
        BeaconStateMerge.required(preState),
        validatorStatuses,
        (MiscHelpersAltair) spec.miscHelpers(),
        (BeaconStateAccessorsAltair) spec.beaconStateAccessors());
  }
}
