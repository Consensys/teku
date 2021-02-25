/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.reference.phase0.epoch_processing;

import tech.pegasys.teku.core.epoch.EpochProcessorUtil;
import tech.pegasys.teku.datastructures.state.MutableBeaconState;
import tech.pegasys.teku.spec.statetransition.epoch.status.ValidatorStatuses;
import tech.pegasys.teku.spec.statetransition.exceptions.EpochProcessingException;

public class DeprecatedEpochProcessingExecutor implements EpochProcessingExecutor {
  @Override
  public void processSlashings(MutableBeaconState state) {
    EpochProcessorUtil.process_slashings(
        state, ValidatorStatuses.create(state).getTotalBalances().getCurrentEpoch());
  }

  @Override
  public void processRegistryUpdates(MutableBeaconState state) throws EpochProcessingException {
    EpochProcessorUtil.process_registry_updates(
        state, ValidatorStatuses.create(state).getStatuses());
  }

  @Override
  public void processFinalUpdates(MutableBeaconState state) {
    EpochProcessorUtil.process_final_updates(state);
  }

  @Override
  public void processRewardsAndPenalties(MutableBeaconState state) throws EpochProcessingException {
    EpochProcessorUtil.process_rewards_and_penalties(state, ValidatorStatuses.create(state));
  }

  @Override
  public void processJustificationAndFinalization(MutableBeaconState state)
      throws EpochProcessingException {
    EpochProcessorUtil.process_justification_and_finalization(
        state, ValidatorStatuses.create(state).getTotalBalances());
  }
}
