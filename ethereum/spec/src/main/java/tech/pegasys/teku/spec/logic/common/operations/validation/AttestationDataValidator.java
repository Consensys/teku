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

import static tech.pegasys.teku.spec.logic.common.operations.validation.OperationInvalidReason.check;
import static tech.pegasys.teku.spec.logic.common.operations.validation.OperationInvalidReason.firstOf;

import java.util.Optional;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.util.config.Constants;

public class AttestationDataValidator
    implements OperationStateTransitionValidator<AttestationData> {

  private final SpecConfig specConfig;
  private final MiscHelpers miscHelpers;
  private final BeaconStateAccessors beaconStateAccessors;

  AttestationDataValidator(
      final SpecConfig specConfig,
      final MiscHelpers miscHelpers,
      final BeaconStateAccessors beaconStateAccessors) {
    this.specConfig = specConfig;
    this.miscHelpers = miscHelpers;
    this.beaconStateAccessors = beaconStateAccessors;
  }

  @Override
  public Optional<OperationInvalidReason> validate(
      final BeaconState state, final AttestationData data) {
    return firstOf(
        () ->
            check(
                data.getIndex()
                        .compareTo(
                            beaconStateAccessors.getCommitteeCountPerSlot(
                                state, data.getTarget().getEpoch()))
                    < 0,
                AttestationInvalidReason.COMMITTEE_INDEX_TOO_HIGH),
        () ->
            check(
                data.getTarget().getEpoch().equals(beaconStateAccessors.getPreviousEpoch(state))
                    || data.getTarget()
                        .getEpoch()
                        .equals(beaconStateAccessors.getCurrentEpoch(state)),
                AttestationInvalidReason.NOT_FROM_CURRENT_OR_PREVIOUS_EPOCH),
        () ->
            check(
                data.getTarget().getEpoch().equals(miscHelpers.computeEpochAtSlot(data.getSlot())),
                AttestationInvalidReason.SLOT_NOT_IN_EPOCH),
        () ->
            check(
                data.getSlot()
                        .plus(Constants.MIN_ATTESTATION_INCLUSION_DELAY)
                        .compareTo(state.getSlot())
                    <= 0,
                AttestationInvalidReason.SUBMITTED_TOO_QUICKLY),
        () ->
            check(
                state
                    .getSlot()
                    .isLessThanOrEqualTo(data.getSlot().plus(specConfig.getSlotsPerEpoch())),
                AttestationInvalidReason.SUBMITTED_TOO_LATE),
        () -> {
          if (data.getTarget().getEpoch().equals(beaconStateAccessors.getCurrentEpoch(state))) {
            return check(
                data.getSource().equals(state.getCurrent_justified_checkpoint()),
                AttestationInvalidReason.INCORRECT_CURRENT_JUSTIFIED_CHECKPOINT);
          } else {
            return check(
                data.getSource().equals(state.getPrevious_justified_checkpoint()),
                AttestationInvalidReason.INCORRECT_PREVIOUS_JUSTIFIED_CHECKPOINT);
          }
        });
  }

  public enum AttestationInvalidReason implements OperationInvalidReason {
    COMMITTEE_INDEX_TOO_HIGH("CommitteeIndex too high"),
    NOT_FROM_CURRENT_OR_PREVIOUS_EPOCH("Attestation not from current or previous epoch"),
    SLOT_NOT_IN_EPOCH("Attestation slot not in specified epoch"),
    SUBMITTED_TOO_QUICKLY("Attestation submitted too quickly"),
    SUBMITTED_TOO_LATE("Attestation submitted too late"),
    INCORRECT_CURRENT_JUSTIFIED_CHECKPOINT(
        "Attestation source does not match current justified checkpoint"),
    INCORRECT_PREVIOUS_JUSTIFIED_CHECKPOINT(
        "Attestation source does not match previous justified checkpoint");

    private final String description;

    AttestationInvalidReason(final String description) {
      this.description = description;
    }

    @Override
    public String describe() {
      return description;
    }
  }
}
