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

package tech.pegasys.teku.spec.logic.common.operations.validation;

import java.util.Optional;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.logic.common.operations.validation.AttesterSlashingValidator.SlashedIndicesCaptor;
import tech.pegasys.teku.spec.logic.common.util.AttestationUtil;

public class OperationValidator {
  private final AttesterSlashingValidator attesterSlashingValidator;
  private final ProposerSlashingValidator proposerSlashingValidator;
  private final VoluntaryExitValidator voluntaryExitValidator;

  private OperationValidator(
      final AttesterSlashingValidator attesterSlashingValidator,
      final ProposerSlashingValidator proposerSlashingValidator,
      final VoluntaryExitValidator voluntaryExitValidator) {
    this.attesterSlashingValidator = attesterSlashingValidator;
    this.proposerSlashingValidator = proposerSlashingValidator;
    this.voluntaryExitValidator = voluntaryExitValidator;
  }

  public static OperationValidator create(
      final SpecConfig specConfig,
      final Predicates predicates,
      final BeaconStateAccessors beaconStateAccessors,
      final AttestationUtil attestationUtil) {
    final AttesterSlashingValidator attesterSlashingValidator =
        new AttesterSlashingValidator(predicates, beaconStateAccessors, attestationUtil);
    final ProposerSlashingValidator proposerSlashingValidator =
        new ProposerSlashingValidator(predicates, beaconStateAccessors);
    final VoluntaryExitValidator voluntaryExitValidator =
        new VoluntaryExitValidator(specConfig, predicates, beaconStateAccessors);
    return new OperationValidator(
        attesterSlashingValidator, proposerSlashingValidator, voluntaryExitValidator);
  }

  public Optional<OperationInvalidReason> validateAttesterSlashing(
      final BeaconState state, final AttesterSlashing attesterSlashing) {
    return attesterSlashingValidator.validate(state, attesterSlashing);
  }

  public Optional<OperationInvalidReason> validateAttesterSlashing(
      final BeaconState state,
      final AttesterSlashing attesterSlashing,
      SlashedIndicesCaptor slashedIndicesCaptor) {
    return attesterSlashingValidator.validate(state, attesterSlashing, slashedIndicesCaptor);
  }

  public Optional<OperationInvalidReason> validateProposerSlashing(
      final BeaconState state, final ProposerSlashing proposerSlashing) {
    return proposerSlashingValidator.validate(state, proposerSlashing);
  }

  public Optional<OperationInvalidReason> validateVoluntaryExit(
      final BeaconState state, final SignedVoluntaryExit signedExit) {
    return voluntaryExitValidator.validate(state, signedExit);
  }
}
