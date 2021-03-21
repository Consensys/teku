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
import org.apache.commons.lang3.NotImplementedException;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.AbstractValidatorStatusFactory;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatus;
import tech.pegasys.teku.spec.logic.common.util.AttestationUtil;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;

public class ValidatorStatusFactoryAltair extends AbstractValidatorStatusFactory {
  public ValidatorStatusFactoryAltair(
      final BeaconStateUtil beaconStateUtil,
      final AttestationUtil attestationUtil,
      final BeaconStateAccessors beaconStateAccessors,
      final Predicates predicates) {
    super(beaconStateUtil, attestationUtil, predicates, beaconStateAccessors);
  }

  @Override
  protected void processAttestations(
      final List<ValidatorStatus> statuses,
      final BeaconState genericState,
      final UInt64 previousEpoch,
      final UInt64 currentEpoch) {
    throw new NotImplementedException("TODO");
  }
}
