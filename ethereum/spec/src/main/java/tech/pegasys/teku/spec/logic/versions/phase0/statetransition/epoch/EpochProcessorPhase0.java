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

package tech.pegasys.teku.spec.logic.versions.phase0.statetransition.epoch;

import tech.pegasys.teku.spec.constants.SpecConstants;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.phase0.MutableBeaconStatePhase0;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.AbstractEpochProcessor;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatusFactory;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;

public class EpochProcessorPhase0 extends AbstractEpochProcessor {

  public EpochProcessorPhase0(
      final SpecConstants specConstants,
      final ValidatorsUtil validatorsUtil,
      final BeaconStateUtil beaconStateUtil,
      final ValidatorStatusFactory validatorStatusFactory) {
    super(specConstants, validatorsUtil, beaconStateUtil, validatorStatusFactory);
  }

  @Override
  public void processParticipationUpdates(MutableBeaconState genericState) {
    // Rotate current/previous epoch attestations
    final MutableBeaconStatePhase0 state = MutableBeaconStatePhase0.required(genericState);
    state.getPrevious_epoch_attestations().setAll(state.getCurrent_epoch_attestations());
    state.getCurrent_epoch_attestations().clear();
  }
}
