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

import org.apache.commons.lang3.NotImplementedException;
import tech.pegasys.teku.spec.config.SpecConstantsAltair;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.AbstractEpochProcessor;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatusFactory;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;

public class EpochProcessorAltair extends AbstractEpochProcessor {

  public EpochProcessorAltair(
      final SpecConstantsAltair specConstants,
      final ValidatorsUtil validatorsUtil,
      final BeaconStateUtil beaconStateUtil,
      final ValidatorStatusFactory validatorStatusFactory) {
    super(specConstants, validatorsUtil, beaconStateUtil, validatorStatusFactory);
  }

  @Override
  public void processParticipationUpdates(final MutableBeaconState genericState) {
    throw new NotImplementedException("TODO");
  }
}
