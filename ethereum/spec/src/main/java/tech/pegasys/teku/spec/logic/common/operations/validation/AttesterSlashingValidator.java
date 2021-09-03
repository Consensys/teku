/*
 * Copyright 2020 ConsenSys AG.
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

import static java.lang.Math.toIntExact;
import static tech.pegasys.teku.spec.logic.common.operations.validation.OperationInvalidReason.check;
import static tech.pegasys.teku.spec.logic.common.operations.validation.OperationInvalidReason.firstOf;

import java.util.Optional;
import java.util.Set;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.logic.common.util.AttestationUtil;

public class AttesterSlashingValidator
    implements OperationStateTransitionValidator<AttesterSlashing> {

  private final Predicates predicates;
  private final BeaconStateAccessors beaconStateAccessors;
  private final AttestationUtil attestationUtil;

  AttesterSlashingValidator(
      final Predicates predicates,
      final BeaconStateAccessors beaconStateAccessors,
      final AttestationUtil attestationUtil) {
    this.predicates = predicates;
    this.beaconStateAccessors = beaconStateAccessors;
    this.attestationUtil = attestationUtil;
  }

  @Override
  public Optional<OperationInvalidReason> validate(
      final Fork fork, final BeaconState state, final AttesterSlashing attesterSlashing) {
    return validate(fork, state, attesterSlashing, SlashedIndicesCaptor.NOOP);
  }

  public Optional<OperationInvalidReason> validate(
      final Fork fork,
      final BeaconState state,
      final AttesterSlashing attesterSlashing,
      final SlashedIndicesCaptor slashedIndicesCaptor) {
    IndexedAttestation attestation_1 = attesterSlashing.getAttestation_1();
    IndexedAttestation attestation_2 = attesterSlashing.getAttestation_2();
    return firstOf(
        () ->
            check(
                attestationUtil.isSlashableAttestationData(
                    attestation_1.getData(), attestation_2.getData()),
                AttesterSlashingInvalidReason.ATTESTATIONS_NOT_SLASHABLE),
        () ->
            check(
                attestationUtil
                    .isValidIndexedAttestation(fork, state, attestation_1)
                    .isSuccessful(),
                AttesterSlashingInvalidReason.ATTESTATION_1_INVALID),
        () ->
            check(
                attestationUtil
                    .isValidIndexedAttestation(fork, state, attestation_2)
                    .isSuccessful(),
                AttesterSlashingInvalidReason.ATTESTATION_2_INVALID),
        () -> {
          boolean slashed_any = false;

          Set<UInt64> intersectingIndices = attesterSlashing.getIntersectingValidatorIndices();

          for (UInt64 index : intersectingIndices) {
            if (predicates.isSlashableValidator(
                state.getValidators().get(toIntExact(index.longValue())),
                beaconStateAccessors.getCurrentEpoch(state))) {
              slashedIndicesCaptor.captureSlashedValidatorIndex(index);
              slashed_any = true;
            }
          }

          return check(slashed_any, AttesterSlashingInvalidReason.NO_ONE_SLASHED);
        });
  }

  public enum AttesterSlashingInvalidReason implements OperationInvalidReason {
    ATTESTATIONS_NOT_SLASHABLE("Attestations are not slashable"),
    ATTESTATION_1_INVALID("Attestation 1 is invalid"),
    ATTESTATION_2_INVALID("Attestation 2 is invalid"),
    NO_ONE_SLASHED("No one is slashed");

    private final String description;

    AttesterSlashingInvalidReason(final String description) {
      this.description = description;
    }

    @Override
    public String describe() {
      return description;
    }
  }

  @FunctionalInterface
  public interface SlashedIndicesCaptor {
    SlashedIndicesCaptor NOOP = __ -> {};

    void captureSlashedValidatorIndex(final UInt64 validatorIndex);
  }
}
