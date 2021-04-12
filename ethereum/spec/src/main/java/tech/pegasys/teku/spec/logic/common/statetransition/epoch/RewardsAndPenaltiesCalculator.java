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

package tech.pegasys.teku.spec.logic.common.statetransition.epoch;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatuses;

public abstract class RewardsAndPenaltiesCalculator {
  protected final SpecConfig specConfig;
  protected final MiscHelpers miscHelpers;
  protected final BeaconStateAccessors beaconStateAccessors;

  protected final BeaconState state;
  protected final ValidatorStatuses validatorStatuses;

  protected RewardsAndPenaltiesCalculator(
      final SpecConfig specConfig,
      final MiscHelpers miscHelpers,
      final BeaconStateAccessors beaconStateAccessors,
      final BeaconState state,
      final ValidatorStatuses validatorStatuses) {
    this.specConfig = specConfig;
    this.miscHelpers = miscHelpers;
    this.beaconStateAccessors = beaconStateAccessors;
    this.state = state;
    this.validatorStatuses = validatorStatuses;
  }

  public abstract RewardAndPenaltyDeltas getDeltas() throws IllegalArgumentException;

  protected UInt64 getFinalityDelay() {
    return beaconStateAccessors.getFinalityDelay(state);
  }

  protected boolean isInactivityLeak(final UInt64 finalityDelay) {
    return beaconStateAccessors.isInactivityLeak(finalityDelay);
  }

  protected boolean isInactivityLeak() {
    return beaconStateAccessors.isInactivityLeak(state);
  }
}
