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

package tech.pegasys.teku.networking.eth2.gossip.topics.validation;

import static tech.pegasys.teku.networking.eth2.gossip.topics.validation.InternalValidationResult.ACCEPT;
import static tech.pegasys.teku.networking.eth2.gossip.topics.validation.InternalValidationResult.IGNORE;
import static tech.pegasys.teku.networking.eth2.gossip.topics.validation.InternalValidationResult.REJECT;
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

public class VoluntaryExitValidator {
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

  public InternalValidationResult validate(SignedVoluntaryExit exit) {
    if (!isFirstValidExitForValidator(exit)) {
      LOG.trace("VoluntaryExitValidator: Exit is not the first one for the given validator.");
      return IGNORE;
    }

    if (!passesProcessVoluntaryExitConditions(exit)) {
      return REJECT;
    }

    if (receivedValidExitSet.add(exit.getMessage().getValidator_index())) {
      return ACCEPT;
    } else {
      LOG.trace("VoluntaryExitValidator: Exit is not the first one for the given validator.");
      return IGNORE;
    }
  }

  private boolean passesProcessVoluntaryExitConditions(SignedVoluntaryExit exit) {
    BeaconState state =
        recentChainData
            .getBestState()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Unable to get best state for voluntary exit processing."));

    Optional<OperationInvalidReason> invalidReason = stateTransitionValidator.validate(state, exit);

    if (invalidReason.isPresent()) {
      LOG.trace(
          "VoluntaryExitValidator: Exit fails process voluntary exit conditions {}.",
          invalidReason.get().describe());
      return false;
    }

    if (!signatureVerifier.verifySignature(state, exit, BLSSignatureVerifier.SIMPLE)) {
      LOG.trace("VoluntaryExitValidator: Exit fails signature verification.");
      return false;
    }
    return true;
  }

  private boolean isFirstValidExitForValidator(SignedVoluntaryExit exit) {
    return !receivedValidExitSet.contains(exit.getMessage().getValidator_index());
  }
}
