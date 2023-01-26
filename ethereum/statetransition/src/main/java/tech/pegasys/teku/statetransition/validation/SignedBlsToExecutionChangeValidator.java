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
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.reject;
import static tech.pegasys.teku.statetransition.validation.ValidationResultCode.ACCEPT;
import static tech.pegasys.teku.statetransition.validation.ValidationResultCode.IGNORE;

import java.util.Optional;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.LimitedSet;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.operations.BlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationInvalidReason;
import tech.pegasys.teku.spec.logic.common.util.AsyncBLSSignatureVerifier;
import tech.pegasys.teku.storage.client.RecentChainData;

public class SignedBlsToExecutionChangeValidator
    implements OperationValidator<SignedBlsToExecutionChange> {

  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;

  private final TimeProvider timeProvider;

  private final RecentChainData recentChainData;

  private final AsyncBLSSignatureVerifier blsSignatureVerifier;

  private final Set<UInt64> seenBlsToExecutionChangeMessageFromValidators =
      LimitedSet.createSynchronized(VALID_VALIDATOR_SET_SIZE);

  public SignedBlsToExecutionChangeValidator(
      final Spec spec,
      final TimeProvider timeProvider,
      final RecentChainData recentChainData,
      final AsyncBLSSignatureVerifier blsSignatureVerifier) {
    this.spec = spec;
    this.timeProvider = timeProvider;
    this.recentChainData = recentChainData;
    this.blsSignatureVerifier = blsSignatureVerifier;
  }

  @Override
  public SafeFuture<InternalValidationResult> validateForGossip(
      final SignedBlsToExecutionChange operation) {

    final BlsToExecutionChange blsToExecutionChange = operation.getMessage();
    final UInt64 validatorIndex = blsToExecutionChange.getValidatorIndex();

    /*
     [IGNORE] current_epoch >= CAPELLA_FORK_EPOCH
    */
    if (!isCapellaActive()) {
      final String logMessage =
          String.format(
              "BlsToExecutionChange arrived before Capella and was ignored for validator %s.",
              validatorIndex);
      LOG.trace(logMessage);
      return SafeFuture.completedFuture(InternalValidationResult.create(IGNORE, logMessage));
    }

    /*
     [IGNORE] The signed_bls_to_execution_change is the first valid signed bls to execution change received for the
     validator with index signed_bls_to_execution_change.message.validator_index.
    */
    if (!isFirstBlsToExecutionChangeForValidator(blsToExecutionChange)) {
      return SafeFuture.completedFuture(rejectForDuplicatedMessage(validatorIndex));
    }

    /*
     [REJECT] All of the conditions within process_bls_to_execution_change pass validation.
    */
    return getState()
        .thenCompose(
            state -> {
              final SafeFuture<InternalValidationResult> messageValidation =
                  validateBlsMessage(state, blsToExecutionChange);
              final SafeFuture<InternalValidationResult> signatureValidation =
                  validateBlsMessageSignature(state, operation);

              return SafeFuture.collectAll(messageValidation, signatureValidation)
                  .thenApply(
                      results -> {
                        if (results.stream().allMatch(InternalValidationResult::isAccept)) {
                          if (seenBlsToExecutionChangeMessageFromValidators.add(validatorIndex)) {
                            return InternalValidationResult.ACCEPT;
                          } else {
                            return rejectForDuplicatedMessage(validatorIndex);
                          }
                        }

                        return results.stream()
                            .filter(r -> !r.equals(InternalValidationResult.ACCEPT))
                            .findFirst()
                            .orElse(reject("Rejected for unknown reason"));
                      });
            });
  }

  private static InternalValidationResult rejectForDuplicatedMessage(final UInt64 validatorIndex) {
    final String logMessage =
        String.format(
            "BlsToExecutionChange is not the first one for validator %s.", validatorIndex);
    LOG.trace(logMessage);
    return InternalValidationResult.create(IGNORE, logMessage);
  }

  @SuppressWarnings("FormatStringAnnotation")
  private SafeFuture<InternalValidationResult> validateBlsMessage(
      BeaconState state, BlsToExecutionChange operation) {
    return spec.validateBlsToExecutionChange(state, timeProvider.getTimeInSeconds(), operation)
        .map(reason -> reject(reason.describe()))
        .map(SafeFuture::completedFuture)
        .orElse(SafeFuture.completedFuture(InternalValidationResult.ACCEPT));
  }

  private SafeFuture<InternalValidationResult> validateBlsMessageSignature(
      BeaconState state, SignedBlsToExecutionChange operation) {
    return spec.atSlot(state.getSlot())
        .operationSignatureVerifier()
        .verifyBlsToExecutionChangeSignatureAsync(state, operation, blsSignatureVerifier)
        .thenApply(
            signatureValid -> {
              if (!signatureValid) {
                return reject(
                    "Rejecting bls_to_execution_change message because the signature is invalid");
              }
              return InternalValidationResult.create(ACCEPT);
            });
  }

  private boolean isCapellaActive() {
    final UInt64 genesisTime = recentChainData.getGenesisTime();
    final UInt64 currentTime = timeProvider.getTimeInSeconds();
    return spec.atTime(genesisTime, currentTime)
        .getMilestone()
        .isGreaterThanOrEqualTo(SpecMilestone.CAPELLA);
  }

  @Override
  public Optional<OperationInvalidReason> validateForBlockInclusion(
      final BeaconState state, final SignedBlsToExecutionChange operation) {

    final Optional<OperationInvalidReason> invalidReason =
        spec.validateBlsToExecutionChange(
            state, timeProvider.getTimeInSeconds(), operation.getMessage());
    if (invalidReason.isPresent()) {
      return invalidReason;
    }

    if (!spec.verifyBlsToExecutionChangeSignature(state, operation, BLSSignatureVerifier.SIMPLE)) {
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
