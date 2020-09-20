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

package tech.pegasys.teku.core.operationvalidators;

import static java.lang.Math.toIntExact;
import static tech.pegasys.teku.core.operationvalidators.OperationInvalidReason.check;
import static tech.pegasys.teku.core.operationvalidators.OperationInvalidReason.firstOf;
import static tech.pegasys.teku.datastructures.util.AttestationUtil.is_slashable_attestation_data;
import static tech.pegasys.teku.datastructures.util.AttestationUtil.is_valid_indexed_attestation;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_current_epoch;
import static tech.pegasys.teku.datastructures.util.ValidatorsUtil.is_slashable_validator;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import tech.pegasys.teku.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class AttesterSlashingStateTransitionValidator
    implements OperationStateTransitionValidator<AttesterSlashing> {

  public Optional<OperationInvalidReason> validate(
      final BeaconState state,
      final AttesterSlashing attesterSlashing,
      final List<UInt64> indicesToSlash) {
    return validate(state, attesterSlashing, Optional.of(indicesToSlash));
  }

  @Override
  public Optional<OperationInvalidReason> validate(
      final BeaconState state, final AttesterSlashing attesterSlashing) {
    return validate(state, attesterSlashing, Optional.empty());
  }

  private Optional<OperationInvalidReason> validate(
      final BeaconState state,
      final AttesterSlashing attesterSlashing,
      final Optional<List<UInt64>> maybeIndicesToSlash) {
    IndexedAttestation attestation_1 = attesterSlashing.getAttestation_1();
    IndexedAttestation attestation_2 = attesterSlashing.getAttestation_2();
    return firstOf(
        () ->
            check(
                is_slashable_attestation_data(attestation_1.getData(), attestation_2.getData()),
                AttesterSlashingInvalidReason.ATTESTATIONS_NOT_SLASHABLE),
        () ->
            check(
                is_valid_indexed_attestation(state, attestation_1).isSuccessful(),
                AttesterSlashingInvalidReason.ATTESTATION_1_INVALID),
        () ->
            check(
                is_valid_indexed_attestation(state, attestation_2).isSuccessful(),
                AttesterSlashingInvalidReason.ATTESTATION_2_INVALID),
        () -> {
          boolean slashed_any = false;

          Set<UInt64> intersectingIndices = attesterSlashing.getIntersectingValidatorIndices();

          for (UInt64 index : intersectingIndices) {
            if (is_slashable_validator(
                state.getValidators().get(toIntExact(index.longValue())),
                get_current_epoch(state))) {
              maybeIndicesToSlash.ifPresent(indicesToSlash -> indicesToSlash.add(index));
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
}
