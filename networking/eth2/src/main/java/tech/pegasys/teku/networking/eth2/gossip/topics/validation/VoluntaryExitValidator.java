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

import static tech.pegasys.teku.core.BlockProcessorUtil.check_voluntary_exit;
import static tech.pegasys.teku.core.BlockProcessorUtil.verify_voluntary_exits;
import static tech.pegasys.teku.networking.eth2.gossip.topics.validation.ValidationResult.INVALID;
import static tech.pegasys.teku.networking.eth2.gossip.topics.validation.ValidationResult.VALID;
import static tech.pegasys.teku.util.config.Constants.VALID_VALIDATOR_SET_SIZE;

import com.google.common.primitives.UnsignedLong;
import java.util.Set;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.util.collections.ConcurrentLimitedSet;
import tech.pegasys.teku.util.collections.LimitStrategy;

public class VoluntaryExitValidator {

  private final RecentChainData recentChainData;
  private final Set<UnsignedLong> receivedValidValidatorExitSet =
      ConcurrentLimitedSet.create(
          VALID_VALIDATOR_SET_SIZE, LimitStrategy.DROP_LEAST_RECENTLY_ACCESSED);

  public VoluntaryExitValidator(RecentChainData recentChainData) {
    this.recentChainData = recentChainData;
  }

  public ValidationResult validate(SignedVoluntaryExit exit) {
    if (!isFirstValidExitForValidator(exit)) {
      return INVALID;
    }

    if (passesProcessVoluntaryExitConditions(exit)) {
      return VALID;
    }

    return INVALID;
  }

  private boolean passesProcessVoluntaryExitConditions(SignedVoluntaryExit exit) {
    try {
      BeaconState state =
          recentChainData
              .getBestState()
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "Unable to get best state for voluntary exit processing"));
      check_voluntary_exit(state, exit.getMessage());
      verify_voluntary_exits(state, SSZList.singleton(exit), BLSSignatureVerifier.SIMPLE);
    } catch (IllegalArgumentException | BLSSignatureVerifier.InvalidSignatureException e) {
      return false;
    }
    return receivedValidValidatorExitSet.add(exit.getMessage().getValidator_index());
  }

  private boolean isFirstValidExitForValidator(SignedVoluntaryExit exit) {
    return !receivedValidValidatorExitSet.contains(exit.getMessage().getValidator_index());
  }
}
