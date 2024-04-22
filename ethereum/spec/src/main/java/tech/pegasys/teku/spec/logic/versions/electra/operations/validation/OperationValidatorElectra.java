/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.spec.logic.versions.electra.operations.validation;

import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.logic.common.operations.validation.AttestationDataValidator;
import tech.pegasys.teku.spec.logic.common.util.AttestationUtil;
import tech.pegasys.teku.spec.logic.versions.capella.operations.validation.OperationValidatorCapella;
import tech.pegasys.teku.spec.logic.versions.phase0.operations.validation.VoluntaryExitValidator;

public class OperationValidatorElectra extends OperationValidatorCapella {
  public OperationValidatorElectra(
      final SpecConfig specConfig,
      final Predicates predicates,
      final BeaconStateAccessors beaconStateAccessors,
      final AttestationDataValidator attestationDataValidator,
      final AttestationUtil attestationUtil,
      final VoluntaryExitValidator voluntaryExitValidator) {
    super(
        specConfig,
        predicates,
        beaconStateAccessors,
        attestationDataValidator,
        attestationUtil,
        voluntaryExitValidator);
  }
}
