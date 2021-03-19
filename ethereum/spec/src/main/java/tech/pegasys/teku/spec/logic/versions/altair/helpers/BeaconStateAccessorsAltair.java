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

package tech.pegasys.teku.spec.logic.versions.altair.helpers;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;

public class BeaconStateAccessorsAltair extends BeaconStateAccessors {

  public BeaconStateAccessorsAltair(
      final SpecConfigAltair config,
      final Predicates predicates,
      final MiscHelpersAltair miscHelpers) {
    super(config, predicates, miscHelpers);
  }

  public UInt64 getBaseRewardPerIncrement(final BeaconState state) {
    return config
        .getEffectiveBalanceIncrement()
        .times(config.getBaseRewardFactor())
        .dividedBy(getTotalActiveBalance(state));
  }

  public UInt64 getBaseReward(final BeaconState state, final int validatorIndex) {
    final UInt64 increments =
        state
            .getValidators()
            .get(validatorIndex)
            .getEffective_balance()
            .dividedBy(config.getEffectiveBalanceIncrement());
    return increments.times(getBaseRewardPerIncrement(state));
  }
}
