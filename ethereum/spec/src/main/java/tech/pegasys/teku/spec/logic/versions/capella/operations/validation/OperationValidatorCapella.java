/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.spec.logic.versions.capella.operations.validation;

import java.util.Optional;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.operations.BlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationInvalidReason;
import tech.pegasys.teku.spec.logic.common.util.AttestationUtil;
import tech.pegasys.teku.spec.logic.versions.phase0.operations.validation.OperationValidatorPhase0;

public class OperationValidatorCapella extends OperationValidatorPhase0 {

  private final BlsToExecutionChangesValidator blsToExecutionChangesValidator =
      new BlsToExecutionChangesValidator();

  public OperationValidatorCapella(
      final SpecConfig specConfig,
      final Predicates predicates,
      final MiscHelpers miscHelpers,
      final BeaconStateAccessors beaconStateAccessors,
      final AttestationUtil attestationUtil) {
    super(specConfig, predicates, miscHelpers, beaconStateAccessors, attestationUtil);
  }

  @Override
  public Optional<OperationInvalidReason> validateBlsToExecutionChange(
      final Fork fork, final BeaconState state, final BlsToExecutionChange blsToExecutionChange) {
    return blsToExecutionChangesValidator.validate(fork, state, blsToExecutionChange);
  }
}
