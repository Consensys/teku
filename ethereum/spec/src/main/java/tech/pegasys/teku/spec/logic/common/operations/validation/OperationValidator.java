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
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.logic.common.operations.validation.AttesterSlashingValidator.SlashedIndicesCaptor;
import tech.pegasys.teku.spec.logic.common.util.AttestationUtil;

public class OperationValidator {
  private final AttestationDataValidator attestationDataValidator;
  private final AttesterSlashingValidator attesterSlashingValidator;
  private final ProposerSlashingValidator proposerSlashingValidator;
  private final VoluntaryExitValidator voluntaryExitValidator;

  private OperationValidator(
      final AttestationDataValidator attestationDataValidator,
      final AttesterSlashingValidator attesterSlashingValidator,
      final ProposerSlashingValidator proposerSlashingValidator,
      final VoluntaryExitValidator voluntaryExitValidator) {
    this.attestationDataValidator = attestationDataValidator;
    this.attesterSlashingValidator = attesterSlashingValidator;
    this.proposerSlashingValidator = proposerSlashingValidator;
    this.voluntaryExitValidator = voluntaryExitValidator;
  }

  public static OperationValidator create(
      final SpecConfig specConfig,
      final Predicates predicates,
      final MiscHelpers miscHelpers,
      final BeaconStateAccessors beaconStateAccessors,
      final AttestationUtil attestationUtil) {
    final AttestationDataValidator attestationDataValidator =
        new AttestationDataValidator(specConfig, miscHelpers, beaconStateAccessors);
    final AttesterSlashingValidator attesterSlashingValidator =
        new AttesterSlashingValidator(predicates, beaconStateAccessors, attestationUtil);
    final ProposerSlashingValidator proposerSlashingValidator =
        new ProposerSlashingValidator(predicates, beaconStateAccessors);
    final VoluntaryExitValidator voluntaryExitValidator =
        new VoluntaryExitValidator(specConfig, predicates, beaconStateAccessors);
    return new OperationValidator(
        attestationDataValidator,
        attesterSlashingValidator,
        proposerSlashingValidator,
        voluntaryExitValidator);
  }

  public Optional<OperationInvalidReason> validateAttestationData(
      final Fork fork, final BeaconState state, final AttestationData data) {
    return attestationDataValidator.validate(fork, state, data);
  }

  public Optional<OperationInvalidReason> validateAttesterSlashing(
      final Fork fork, final BeaconState state, final AttesterSlashing attesterSlashing) {
    return attesterSlashingValidator.validate(fork, state, attesterSlashing);
  }

  public Optional<OperationInvalidReason> validateAttesterSlashing(
      final Fork fork,
      final BeaconState state,
      final AttesterSlashing attesterSlashing,
      SlashedIndicesCaptor slashedIndicesCaptor) {
    return attesterSlashingValidator.validate(fork, state, attesterSlashing, slashedIndicesCaptor);
  }

  public Optional<OperationInvalidReason> validateProposerSlashing(
      final Fork fork, final BeaconState state, final ProposerSlashing proposerSlashing) {
    return proposerSlashingValidator.validate(fork, state, proposerSlashing);
  }

  public Optional<OperationInvalidReason> validateVoluntaryExit(
      final Fork fork, final BeaconState state, final SignedVoluntaryExit signedExit) {
    return voluntaryExitValidator.validate(fork, state, signedExit);
  }
}
