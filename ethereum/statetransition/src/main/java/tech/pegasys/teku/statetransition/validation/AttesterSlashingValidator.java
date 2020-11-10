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
import tech.pegasys.teku.core.operationvalidators.AttesterSlashingStateTransitionValidator;
import tech.pegasys.teku.core.operationvalidators.OperationInvalidReason;
import tech.pegasys.teku.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.infrastructure.collections.LimitedSet;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.client.RecentChainData;

public class AttesterSlashingValidator implements OperationValidator<AttesterSlashing> {
  private static final Logger LOG = LogManager.getLogger();

  private final RecentChainData recentChainData;
  private final Set<UInt64> seenIndices = LimitedSet.create(VALID_VALIDATOR_SET_SIZE);
  private final AttesterSlashingStateTransitionValidator transitionValidator;

  public AttesterSlashingValidator(
      RecentChainData recentChainData,
      AttesterSlashingStateTransitionValidator attesterSlashingStateTransitionValidator) {
    this.recentChainData = recentChainData;
    this.transitionValidator = attesterSlashingStateTransitionValidator;
  }

  @Override
  public InternalValidationResult validateFully(AttesterSlashing slashing) {
    if (!includesUnseenIndexToSlash(slashing.getIntersectingValidatorIndices())) {
      LOG.trace("AttesterSlashingValidator: Slashing is not the first one for any validator.");
      return IGNORE;
    }

    BeaconState state = getState();
    if (!validateForStateTransition(state, slashing)) {
      return REJECT;
    }

    if (seenIndices.addAll(slashing.getIntersectingValidatorIndices())) {
      return ACCEPT;
    } else {
      LOG.trace("AttesterSlashingValidator: Slashing is not the first one for any validator.");
      return IGNORE;
    }
  }

  @Override
  public boolean validateForStateTransition(BeaconState state, AttesterSlashing slashing) {
    Optional<OperationInvalidReason> invalidReason = transitionValidator.validate(state, slashing);

    if (invalidReason.isPresent()) {
      LOG.trace(
          "AttesterSlashingValidator: Slashing fails process attester slashing conditions {}.",
          invalidReason.get().describe());
      return false;
    }
    return true;
  }

  private boolean includesUnseenIndexToSlash(Set<UInt64> intersectingIndices) {
    return !seenIndices.containsAll(intersectingIndices);
  }

  private BeaconState getState() {
    return recentChainData
        .getBestState()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Unable to get best state for attester slashing processing."));
  }
}
