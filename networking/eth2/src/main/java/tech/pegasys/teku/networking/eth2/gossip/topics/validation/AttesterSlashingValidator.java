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

import com.google.common.collect.Sets;
import com.google.common.primitives.UnsignedLong;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.core.operationvalidators.AttesterSlashingStateTransitionValidator;
import tech.pegasys.teku.core.operationvalidators.OperationInvalidReason;
import tech.pegasys.teku.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.util.collections.ConcurrentLimitedSet;
import tech.pegasys.teku.util.collections.LimitStrategy;

public class AttesterSlashingValidator {
  private static final Logger LOG = LogManager.getLogger();

  private final RecentChainData recentChainData;
  private final Set<UnsignedLong> seenIndices =
      ConcurrentLimitedSet.create(
          VALID_VALIDATOR_SET_SIZE, LimitStrategy.DROP_LEAST_RECENTLY_ACCESSED);
  private final AttesterSlashingStateTransitionValidator transitionValidator;

  public AttesterSlashingValidator(
      RecentChainData recentChainData,
      AttesterSlashingStateTransitionValidator attesterSlashingStateTransitionValidator) {
    this.recentChainData = recentChainData;
    this.transitionValidator = attesterSlashingStateTransitionValidator;
  }

  public InternalValidationResult validate(AttesterSlashing slashing) {
    Set<UnsignedLong> intersectingIndices = getIntersectingIndices(slashing);
    if (!includesUnseenIndexToSlash(intersectingIndices)) {
      LOG.trace("AttesterSlashingValidator: Slashing is not the first one for any validator.");
      return IGNORE;
    }

    if (!passesProcessAttesterSlashingConditions(slashing, intersectingIndices)) {
      return REJECT;
    }

    if (seenIndices.addAll(intersectingIndices)) {
      return ACCEPT;
    } else {
      LOG.trace("AttesterSlashingValidator: Slashing is not the first one for any validator.");
      return IGNORE;
    }
  }

  private boolean passesProcessAttesterSlashingConditions(
      AttesterSlashing slashing, Set<UnsignedLong> intersectingIndices) {
    BeaconState state =
        recentChainData
            .getBestState()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Unable to get best state for attester slashing processing."));
    Optional<OperationInvalidReason> invalidReason =
        transitionValidator.validateSlashing(state, slashing, Optional.of(intersectingIndices));

    if (invalidReason.isPresent()) {
      LOG.trace(
          "AttesterSlashingValidator: Slashing fails process attester slashing conditions {}.",
          invalidReason.get().describe());
      return false;
    }
    return true;
  }

  private Set<UnsignedLong> getIntersectingIndices(AttesterSlashing attesterSlashing) {
    IndexedAttestation attestation_1 = attesterSlashing.getAttestation_1();
    IndexedAttestation attestation_2 = attesterSlashing.getAttestation_2();

    return Sets.intersection(
        new TreeSet<>(attestation_1.getAttesting_indices().asList()), // TreeSet as must be sorted
        new HashSet<>(attestation_2.getAttesting_indices().asList()));
  }

  private boolean includesUnseenIndexToSlash(Set<UnsignedLong> intersectingIndices) {
    return !seenIndices.containsAll(intersectingIndices);
  }
}
