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
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationInvalidReason;
import tech.pegasys.teku.spec.logic.common.operations.validation.VoluntaryExitValidator.ExitInvalidReason;
import tech.pegasys.teku.storage.client.RecentChainData;

public class VoluntaryExitValidator implements OperationValidator<SignedVoluntaryExit> {
  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final RecentChainData recentChainData;
  private final Set<UInt64> receivedValidExitSet =
      LimitedSet.createSynchronized(VALID_VALIDATOR_SET_SIZE);

  public VoluntaryExitValidator(final Spec spec, RecentChainData recentChainData) {
    this.spec = spec;
    this.recentChainData = recentChainData;
  }

  @Override
  public SafeFuture<InternalValidationResult> validateFully(SignedVoluntaryExit exit) {
    if (!isFirstValidExitForValidator(exit)) {
      LOG.trace(
          "VoluntaryExitValidator: Exit is not the first one for validator {}.",
          exit.getMessage().getValidatorIndex());
      return SafeFuture.completedFuture(
          InternalValidationResult.create(
              IGNORE,
              String.format(
                  "Exit is not the first one for validator %s.",
                  exit.getMessage().getValidatorIndex())));
    }

    return getFailureReason(exit)
        .thenApply(
            failureReason -> {
              if (failureReason.isPresent()) {
                return InternalValidationResult.reject(
                    "Exit for validator %s is invalid: %s",
                    exit.getMessage().getValidatorIndex(), failureReason.get().describe());
              }

              if (receivedValidExitSet.add(exit.getMessage().getValidatorIndex())) {
                return InternalValidationResult.ACCEPT;
              } else {
                LOG.trace(
                    "VoluntaryExitValidator: Exit is not the first one for validator {}.",
                    exit.getMessage().getValidatorIndex());
                return InternalValidationResult.create(
                    IGNORE,
                    String.format(
                        "Exit is not the first one for validator %s.",
                        exit.getMessage().getValidatorIndex()));
              }
            });
  }

  @Override
  public Optional<OperationInvalidReason> validateForStateTransition(
      BeaconState state, SignedVoluntaryExit exit) {
    return getFailureReason(state, exit, false);
  }

  @Override
  public Optional<OperationInvalidReason> validateForBlockInclusion(
      final BeaconState stateAtBlockSlot, final SignedVoluntaryExit exit) {
    return getFailureReason(stateAtBlockSlot, exit, true);
  }

  private SafeFuture<Optional<OperationInvalidReason>> getFailureReason(SignedVoluntaryExit exit) {
    return getState().thenApply(state -> getFailureReason(state, exit, true));
  }

  private Optional<OperationInvalidReason> getFailureReason(
      final BeaconState state, final SignedVoluntaryExit exit, final boolean verifySignature) {
    Optional<OperationInvalidReason> invalidReason = spec.validateVoluntaryExit(state, exit);
    if (invalidReason.isPresent()) {
      return invalidReason;
    }

    if (verifySignature
        && !spec.verifyVoluntaryExitSignature(state, exit, BLSSignatureVerifier.SIMPLE)) {
      return Optional.of(ExitInvalidReason.invalidSignature());
    }
    return Optional.empty();
  }

  private boolean isFirstValidExitForValidator(SignedVoluntaryExit exit) {
    return !receivedValidExitSet.contains(exit.getMessage().getValidatorIndex());
  }

  private SafeFuture<BeaconState> getState() {
    return recentChainData
        .getBestState()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Unable to get best state for voluntary exit processing."));
  }
}
