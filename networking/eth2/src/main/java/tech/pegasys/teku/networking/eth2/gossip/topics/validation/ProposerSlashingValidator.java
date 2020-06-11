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

import com.google.common.primitives.UnsignedLong;
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
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.util.collections.ConcurrentLimitedSet;
import tech.pegasys.teku.util.collections.LimitStrategy;

public class ProposerSlashingValidator {
  private static final Logger LOG = LogManager.getLogger();

  private final RecentChainData recentChainData;
  private final Set<UnsignedLong> receivedValidSlashingForProposerSet =
      ConcurrentLimitedSet.create(
          VALID_VALIDATOR_SET_SIZE, LimitStrategy.DROP_LEAST_RECENTLY_ACCESSED);
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

  public InternalValidationResult validate(ProposerSlashing slashing) {
    if (!isFirstValidSlashingForValidator(slashing)) {
      LOG.trace(
          "ProposerSlashingValidator: Slashing is not the first one for the given validator.");
      return IGNORE;
    }

    if (!passesProcessProposerSlashingConditions(slashing)) {
      return REJECT;
    }

    if (receivedValidSlashingForProposerSet.add(
        slashing.getHeader_1().getMessage().getProposer_index())) {
      return ACCEPT;
    } else {
      LOG.trace(
          "ProposerSlashingValidator: Slashing is not the first one for the given validator.");
      return IGNORE;
    }
  }

  private boolean passesProcessProposerSlashingConditions(ProposerSlashing slashing) {
    BeaconState state =
        recentChainData
            .getBestState()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Unable to get best state for proposer slashing processing."));
    Optional<OperationInvalidReason> invalidReason =
        transitionValidator.validateSlashing(state, slashing);

    if (invalidReason.isPresent()) {
      LOG.trace(
          "ProposerSlashingValidator: Slashing fails process proposer slashing conditions {}.",
          invalidReason.get().describe());
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
        slashing.getHeader_1().getMessage().getProposer_index());
  }
}
