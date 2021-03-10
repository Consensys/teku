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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;
import tech.pegasys.teku.ssz.backing.collections.SszBitvector;

interface ValidatorStatsAltair extends BeaconStateAltair {
  @Override
  default CorrectAndLiveValidators getValidatorStatsPreviousEpoch(final Bytes32 correctTargetRoot) {
    return getValidatorStats(getPreviousEpochParticipation(), correctTargetRoot);
  }

  @Override
  default CorrectAndLiveValidators getValidatorStatsCurrentEpoch(final Bytes32 correctTargetRoot) {
    return getValidatorStats(getCurrentEpochParticipation(), correctTargetRoot);
  }

  private CorrectAndLiveValidators getValidatorStats(
      final SSZList<SszBitvector> validatorFlags, final Bytes32 correctTargetRoot) {

    final int numberOfCorrectValidators =
        Math.toIntExact(validatorFlags.stream().filter(ValidatorFlag::isTimelyTarget).count());
    final int numberOfLiveValidators =
        Math.toIntExact(validatorFlags.stream().filter(ValidatorFlag::isAnyFlagSet).count());

    return new CorrectAndLiveValidators(numberOfCorrectValidators, numberOfLiveValidators);
  }
}
