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

import static tech.pegasys.teku.core.operationvalidators.OperationInvalidReason.check;
import static tech.pegasys.teku.core.operationvalidators.OperationInvalidReason.firstOf;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_committee_count_per_slot;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_current_epoch;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_previous_epoch;
import static tech.pegasys.teku.util.config.Constants.SLOTS_PER_EPOCH;

import java.util.Optional;
import tech.pegasys.teku.datastructures.operations.AttestationData;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.util.config.Constants;

public class AttestationDataStateTransitionValidator
    implements OperationStateTransitionValidator<AttestationData> {

  @Override
  public Optional<OperationInvalidReason> validate(
      final BeaconState state, final AttestationData data) {
    return firstOf(
        () ->
            check(
                data.getIndex()
                        .compareTo(get_committee_count_per_slot(state, data.getTarget().getEpoch()))
                    < 0,
                AttestationInvalidReason.COMMITTEE_INDEX_TOO_HIGH),
        () ->
            check(
                data.getTarget().getEpoch().equals(get_previous_epoch(state))
                    || data.getTarget().getEpoch().equals(get_current_epoch(state)),
                AttestationInvalidReason.NOT_FROM_CURRENT_OR_PREVIOUS_EPOCH),
        () ->
            check(
                data.getTarget().getEpoch().equals(compute_epoch_at_slot(data.getSlot())),
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
                state.getSlot().isLessThanOrEqualTo(data.getSlot().plus(SLOTS_PER_EPOCH)),
                AttestationInvalidReason.SUBMITTED_TOO_LATE),
        () -> {
          if (data.getTarget().getEpoch().equals(get_current_epoch(state))) {
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
