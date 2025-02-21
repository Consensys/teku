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

package tech.pegasys.teku.spec.logic.versions.eip7732.operations.validation;

import static tech.pegasys.teku.spec.logic.common.operations.validation.OperationInvalidReason.check;
import static tech.pegasys.teku.spec.logic.common.operations.validation.OperationInvalidReason.firstOf;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.spec.datastructures.operations.PayloadAttestationData;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationInvalidReason;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationStateTransitionValidator;

public class PayloadAttestationValidator
    implements OperationStateTransitionValidator<PayloadAttestationData> {

  PayloadAttestationValidator() {}

  @Override
  public Optional<OperationInvalidReason> validate(
      final Fork fork, final BeaconState state, final PayloadAttestationData payloadAttestation) {
    return firstOf(
        () -> verifyAttestationIsForTheParentBeaconBlock(state, payloadAttestation),
        () -> verifyAttestationIsForThePreviousSlot(state, payloadAttestation));
  }

  private Optional<OperationInvalidReason> verifyAttestationIsForTheParentBeaconBlock(
      final BeaconState state, final PayloadAttestationData payloadAttestation) {
    final Bytes32 latestBlockRoot = state.getLatestBlockHeader().getRoot();
    return check(
        payloadAttestation.getBeaconBlockRoot().equals(latestBlockRoot),
        // this check is used for validation during block production, so the latest block in the
        // state should refer to the parent block of the current block being produced
        () ->
            String.format(
                "Attestation with block root %s is not for the parent block %s",
                payloadAttestation.getBeaconBlockRoot(), latestBlockRoot));
  }

  private Optional<OperationInvalidReason> verifyAttestationIsForThePreviousSlot(
      final BeaconState state, final PayloadAttestationData payloadAttestation) {
    return check(
        payloadAttestation.getSlot().increment().equals(state.getSlot()),
        () ->
            String.format(
                "Attestation with slot %s is not for the previous slot",
                payloadAttestation.getSlot()));
  }
}
