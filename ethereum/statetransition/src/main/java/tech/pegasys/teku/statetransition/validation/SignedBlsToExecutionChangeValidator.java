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

package tech.pegasys.teku.statetransition.validation;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationInvalidReason;

public class SignedBlsToExecutionChangeValidator
    implements OperationValidator<SignedBlsToExecutionChange> {

  // FIXME Implement validation logic (#6358)

  @Override
  public SafeFuture<InternalValidationResult> validateFully(
      final SignedBlsToExecutionChange operation) {
    return SafeFuture.completedFuture(InternalValidationResult.ACCEPT);
  }

  @Override
  public Optional<OperationInvalidReason> validateForStateTransition(
      final BeaconState state, final SignedBlsToExecutionChange operation) {
    return Optional.empty();
  }

  @Override
  public Optional<OperationInvalidReason> validateForBlockInclusion(
      final BeaconState stateAtBlockSlot, final SignedBlsToExecutionChange operation) {
    return Optional.empty();
  }
}
