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

import static tech.pegasys.teku.spec.config.Constants.VALID_VALIDATOR_SET_SIZE;
import static tech.pegasys.teku.statetransition.validation.ValidationResultCode.IGNORE;

import java.util.Optional;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.LimitedSet;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.operations.BlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationInvalidReason;
import tech.pegasys.teku.storage.client.RecentChainData;

public class SignedBlsToExecutionChangeValidator
    implements OperationValidator<SignedBlsToExecutionChange> {

  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final RecentChainData recentChainData;
  private final Set<UInt64> seenBlsToExecutionChangeMessageFromValidators =
      LimitedSet.createSynchronized(VALID_VALIDATOR_SET_SIZE);

  public SignedBlsToExecutionChangeValidator(final Spec spec, RecentChainData recentChainData) {
    this.spec = spec;
    this.recentChainData = recentChainData;
  }

  @Override
  public SafeFuture<InternalValidationResult> validateForGossip(
      final SignedBlsToExecutionChange operation) {

    final BlsToExecutionChange blsToExecutionChange = operation.getMessage();
    final UInt64 validatorIndex = blsToExecutionChange.getValidatorIndex();

    /*
     [IGNORE] The signed_bls_to_execution_change is the first valid signed bls to execution change received for the
     validator with index signed_bls_to_execution_change.message.validator_index.
    */
    if (!isFirstBlsToExecutionChangeForValidator(blsToExecutionChange)) {
      final String logMessage =
          String.format(
              "BlsToExecutionChange is not the first one for validator %s.", validatorIndex);
      LOG.trace(logMessage);
      return SafeFuture.completedFuture(InternalValidationResult.create(IGNORE, logMessage));
    }

    /*
     [REJECT] All of the conditions within process_bls_to_execution_change pass validation.
    */
    return getMaybeFailureReason(operation)
        .thenApply(
            maybeFailureReason -> {
              if (maybeFailureReason.isPresent()) {
                return InternalValidationResult.reject(
                    "BlsToExecutionChange for validator %s is invalid: %s",
                    validatorIndex, maybeFailureReason.get().describe());
              } else {
                seenBlsToExecutionChangeMessageFromValidators.add(validatorIndex);
                return InternalValidationResult.ACCEPT;
              }
            });
  }

  @Override
  public Optional<OperationInvalidReason> validateForBlockInclusion(
      final BeaconState state, final SignedBlsToExecutionChange operation) {
    return getMaybeFailureReason(state, operation, true);
  }

  private SafeFuture<Optional<OperationInvalidReason>> getMaybeFailureReason(
      final SignedBlsToExecutionChange signedBlsToExecutionChange) {
    return getState()
        .thenApply(state -> getMaybeFailureReason(state, signedBlsToExecutionChange, true));
  }

  private Optional<OperationInvalidReason> getMaybeFailureReason(
      final BeaconState state,
      final SignedBlsToExecutionChange signedBlsToExecutionChange,
      final boolean verifySignature) {
    final Optional<OperationInvalidReason> invalidReason =
        spec.validateBlsToExecutionChange(state, signedBlsToExecutionChange.getMessage());
    if (invalidReason.isPresent()) {
      return invalidReason;
    }

    if (verifySignature
        && !spec.verifyBlsToExecutionChangeSignature(
            state, signedBlsToExecutionChange, BLSSignatureVerifier.SIMPLE)) {
      return Optional.of(() -> "Signature is invalid");
    }
    return Optional.empty();
  }

  private SafeFuture<BeaconState> getState() {
    return recentChainData
        .getBestState()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Unable to get best state for BlsToExecutionChange processing."));
  }

  private boolean isFirstBlsToExecutionChangeForValidator(
      final BlsToExecutionChange blsToExecutionChange) {
    return !seenBlsToExecutionChangeMessageFromValidators.contains(
        blsToExecutionChange.getValidatorIndex());
  }
}
