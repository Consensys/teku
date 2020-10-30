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

import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ACCEPT;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.IGNORE;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.REJECT;
import static tech.pegasys.teku.util.config.Constants.VALID_VALIDATOR_SET_SIZE;

import java.util.Optional;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.core.operationsignatureverifiers.ProposerSlashingSignatureVerifier;
import tech.pegasys.teku.core.operationvalidators.OperationInvalidReason;
import tech.pegasys.teku.core.operationvalidators.ProposerSlashingStateTransitionValidator;
import tech.pegasys.teku.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.infrastructure.collections.LimitedSet;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.client.RecentChainData;

public class ProposerSlashingValidator implements OperationValidator<ProposerSlashing> {
  private static final Logger LOG = LogManager.getLogger();

  private final RecentChainData recentChainData;
  private final Set<UInt64> receivedValidSlashingForProposerSet =
      LimitedSet.create(VALID_VALIDATOR_SET_SIZE);
  private final ProposerSlashingStateTransitionValidator transitionValidator;
  private final ProposerSlashingSignatureVerifier signatureValidator;

  public ProposerSlashingValidator(
      RecentChainData recentChainData,
      ProposerSlashingStateTransitionValidator proposerSlashingStateTransitionValidator,
      ProposerSlashingSignatureVerifier proposerSlashingSignatureVerifier) {
    this.recentChainData = recentChainData;
    this.transitionValidator = proposerSlashingStateTransitionValidator;
    this.signatureValidator = proposerSlashingSignatureVerifier;
  }

  @Override
  public InternalValidationResult validateFully(ProposerSlashing slashing) {
    if (!isFirstValidSlashingForValidator(slashing)) {
      LOG.trace(
          "ProposerSlashingValidator: Slashing is not the first one for the given validator.");
      return IGNORE;
    }

    if (!passesProcessProposerSlashingConditions(slashing)) {
      return REJECT;
    }

    if (receivedValidSlashingForProposerSet.add(
        slashing.getHeader_1().getMessage().getProposerIndex())) {
      return ACCEPT;
    } else {
      LOG.trace(
          "ProposerSlashingValidator: Slashing is not the first one for the given validator.");
      return IGNORE;
    }
  }

  @Override
  public boolean validateForStateTransition(BeaconState state, ProposerSlashing slashing) {
    Optional<OperationInvalidReason> invalidReason = transitionValidator.validate(state, slashing);

    if (invalidReason.isPresent()) {
      LOG.trace(
          "ProposerSlashingValidator: Slashing fails process proposer slashing conditions {}.",
          invalidReason.get().describe());
      return false;
    }

    return true;
  }

  private boolean passesProcessProposerSlashingConditions(ProposerSlashing slashing) {
    final BeaconState state = getState();
    if (!validateForStateTransition(state, slashing)) {
      return false;
    }

    if (!signatureValidator.verifySignature(state, slashing, BLSSignatureVerifier.SIMPLE)) {
      LOG.trace("ProposerSlashingValidator: Slashing fails signature verification.");
      return false;
    }
    return true;
  }

  private boolean isFirstValidSlashingForValidator(ProposerSlashing slashing) {
    return !receivedValidSlashingForProposerSet.contains(
        slashing.getHeader_1().getMessage().getProposerIndex());
  }

  private BeaconState getState() {
    return recentChainData
        .getBestState()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Unable to get best state for proposer slashing processing."));
  }
}
