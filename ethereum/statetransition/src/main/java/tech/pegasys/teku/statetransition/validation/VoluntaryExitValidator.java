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

import static tech.pegasys.teku.statetransition.validation.ValidationResultCode.IGNORE;
import static tech.pegasys.teku.util.config.Constants.VALID_VALIDATOR_SET_SIZE;

import java.util.Optional;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
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
  private final Set<UInt64> receivedValidExitSet = LimitedSet.create(VALID_VALIDATOR_SET_SIZE);

  public VoluntaryExitValidator(final Spec spec, RecentChainData recentChainData) {
    this.spec = spec;
    this.recentChainData = recentChainData;
  }

  @Override
  public InternalValidationResult validateFully(SignedVoluntaryExit exit) {
    if (!isFirstValidExitForValidator(exit)) {
      LOG.trace(
          "VoluntaryExitValidator: Exit is not the first one for validator {}.",
          exit.getMessage().getValidatorIndex());
      return InternalValidationResult.create(
          IGNORE,
          String.format(
              "Exit is not the first one for validator %s.",
              exit.getMessage().getValidatorIndex()));
    }

    final Optional<OperationInvalidReason> failureReason = getFailureReason(exit);
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
  }

  @Override
  public Optional<OperationInvalidReason> validateForStateTransition(
      BeaconState state, SignedVoluntaryExit exit) {
    return getFailureReason(state, exit, false);
  }

  private Optional<OperationInvalidReason> getFailureReason(SignedVoluntaryExit exit) {
    final BeaconState state = getState();
    return getFailureReason(state, exit, true);
  }

  private Optional<OperationInvalidReason> getFailureReason(
      final BeaconState state, final SignedVoluntaryExit exit, final boolean verifySignature) {
    Optional<OperationInvalidReason> invalidReason = spec.validateVoluntaryExit(state, exit);
    if (invalidReason.isPresent()) {
      return invalidReason;
    }

    if (verifySignature
        && !spec.verifyVoluntaryExitSignature(state, exit, BLSSignatureVerifier.SIMPLE)) {
      return Optional.of(ExitInvalidReason.INVALID_SIGNATURE);
    }
    return Optional.empty();
  }

  private boolean isFirstValidExitForValidator(SignedVoluntaryExit exit) {
    return !receivedValidExitSet.contains(exit.getMessage().getValidatorIndex());
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
