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

package tech.pegasys.teku.core.epoch;

import tech.pegasys.teku.core.exceptions.EpochProcessingException;
import tech.pegasys.teku.datastructures.state.BeaconState;

public class EpochProcessor {

  /**
   * Processes epoch
   *
   * @param preState state prior to epoch transition
   * @throws EpochProcessingException if processing fails
   */
  public static BeaconState processEpoch(final BeaconState preState)
      throws EpochProcessingException {
    return preState.updated(
        state -> {
          final MatchingAttestations matchingAttestations = new MatchingAttestations(state);
          EpochProcessorUtil.process_justification_and_finalization(state, matchingAttestations);
          EpochProcessorUtil.process_rewards_and_penalties(state, matchingAttestations);
          EpochProcessorUtil.process_registry_updates(state);
          EpochProcessorUtil.process_slashings(state);
          EpochProcessorUtil.process_final_updates(state);
        });
  }
}
