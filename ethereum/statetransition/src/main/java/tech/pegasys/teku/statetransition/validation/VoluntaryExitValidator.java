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

package tech.pegasys.teku.statetransition.validation;

import static tech.pegasys.teku.statetransition.validation.ValidationResultCode.ACCEPT;
import static tech.pegasys.teku.statetransition.validation.ValidationResultCode.IGNORE;
import static tech.pegasys.teku.statetransition.validation.ValidationResultCode.REJECT;
import static tech.pegasys.teku.util.config.Constants.VALID_VALIDATOR_SET_SIZE;

import java.util.Optional;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.core.operationsignatureverifiers.VoluntaryExitSignatureVerifier;
import tech.pegasys.teku.core.operationvalidators.OperationInvalidReason;
import tech.pegasys.teku.core.operationvalidators.VoluntaryExitStateTransitionValidator;
import tech.pegasys.teku.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.infrastructure.collections.LimitedSet;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.client.RecentChainData;

public class VoluntaryExitValidator implements OperationValidator<SignedVoluntaryExit> {
  private static final Logger LOG = LogManager.getLogger();

  private final RecentChainData recentChainData;
  private final Set<UInt64> receivedValidExitSet = LimitedSet.create(VALID_VALIDATOR_SET_SIZE);
  private final VoluntaryExitStateTransitionValidator stateTransitionValidator;
  private final VoluntaryExitSignatureVerifier signatureVerifier;

  public VoluntaryExitValidator(
      RecentChainData recentChainData,
      VoluntaryExitStateTransitionValidator stateTransitionValidator,
      VoluntaryExitSignatureVerifier signatureVerifier) {
    this.recentChainData = recentChainData;
    this.stateTransitionValidator = stateTransitionValidator;
    this.signatureVerifier = signatureVerifier;
  }

  @Override
  public InternalValidationResult validateFully(SignedVoluntaryExit exit) {
    if (!isFirstValidExitForValidator(exit)) {
      LOG.trace(
          "VoluntaryExitValidator: Exit is not the first one for validator {}.",
          exit.getMessage().getValidator_index());
      return InternalValidationResult.create(
          IGNORE,
          String.format(
              "Exit is not the first one for validator %s.",
              exit.getMessage().getValidator_index()));
    }

    final Optional<String> failureReason = getFailureReason(exit);
    if (failureReason.isPresent()) {
      return InternalValidationResult.create(REJECT, failureReason.get());
    }

    if (receivedValidExitSet.add(exit.getMessage().getValidator_index())) {
      return InternalValidationResult.create(ACCEPT);
    } else {
      LOG.trace(
          "VoluntaryExitValidator: Exit is not the first one for validator {}.",
          exit.getMessage().getValidator_index());
      return InternalValidationResult.create(
          IGNORE,
          String.format(
              "Exit is not the first one for validator %s.",
              exit.getMessage().getValidator_index()));
    }
  }

  @Override
  public boolean validateForStateTransition(BeaconState state, SignedVoluntaryExit exit) {
    return getFailureReason(state, exit, false).isEmpty();
  }

  private Optional<String> getFailureReason(SignedVoluntaryExit exit) {
    final BeaconState state = getState();
    return getFailureReason(state, exit, true);
  }

  private Optional<String> getFailureReason(
      final BeaconState state, final SignedVoluntaryExit exit, final boolean verifySignature) {
    Optional<OperationInvalidReason> invalidReason = stateTransitionValidator.validate(state, exit);
    if (invalidReason.isPresent()) {
      final String message =
          String.format(
              "Exit for validator %s fails process voluntary exit conditions %s.",
              exit.getMessage().getValidator_index(), invalidReason.get().describe());
      LOG.debug("VoluntaryExitValidator: " + message);
      return Optional.of(message);
    }

    if (verifySignature
        && !signatureVerifier.verifySignature(state, exit, BLSSignatureVerifier.SIMPLE)) {
      final String message =
          String.format(
              "Exit for validator %s fails signature verification.",
              exit.getMessage().getValidator_index());
      LOG.trace("VoluntaryExitValidator: " + message);
      return Optional.of(message);
    }
    return Optional.empty();
  }

  private boolean isFirstValidExitForValidator(SignedVoluntaryExit exit) {
    return !receivedValidExitSet.contains(exit.getMessage().getValidator_index());
  }

  private BeaconState getState() {
    return recentChainData
        .getBestState()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Unable to get best state for voluntary exit processing."));
  }
}
