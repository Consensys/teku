/*
 * Copyright Consensys Software Inc., 2025
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

  protected final ValidatorStatuses validatorStatuses;

  private final boolean isInactivityLeak;
  private final UInt64 finalityDelay;

  protected RewardsAndPenaltiesCalculator(
      final SpecConfig specConfig,
      final MiscHelpers miscHelpers,
      final BeaconStateAccessors beaconStateAccessors,
      final BeaconState state,
      final ValidatorStatuses validatorStatuses) {
    this.specConfig = specConfig;
    this.miscHelpers = miscHelpers;
    this.beaconStateAccessors = beaconStateAccessors;
    this.validatorStatuses = validatorStatuses;
    this.finalityDelay = beaconStateAccessors.getFinalityDelay(state);
    this.isInactivityLeak = beaconStateAccessors.isInactivityLeak(state);
  }

  public abstract RewardAndPenaltyDeltas getDeltas() throws IllegalArgumentException;

  public abstract RewardAndPenaltyDeltas getDetailedDeltas() throws IllegalArgumentException;

  protected UInt64 getFinalityDelay() {
    return finalityDelay;
  }

  protected boolean isInactivityLeak(final UInt64 finalityDelay) {
    return beaconStateAccessors.isInactivityLeak(finalityDelay);
  }

  protected boolean isInactivityLeak() {
    return isInactivityLeak;
  }
}
