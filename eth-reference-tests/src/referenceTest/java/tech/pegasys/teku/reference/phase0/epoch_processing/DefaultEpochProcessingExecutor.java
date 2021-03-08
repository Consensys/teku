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

import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.statetransition.epoch.EpochProcessor;
import tech.pegasys.teku.spec.statetransition.epoch.status.ValidatorStatuses;
import tech.pegasys.teku.spec.statetransition.exceptions.EpochProcessingException;

public class DefaultEpochProcessingExecutor implements EpochProcessingExecutor {
  private final EpochProcessor epochProcessor;

  public DefaultEpochProcessingExecutor(final EpochProcessor epochProcessor) {
    this.epochProcessor = epochProcessor;
  }

  @Override
  public void processSlashings(final MutableBeaconState state) {
    epochProcessor.processSlashings(
        state, ValidatorStatuses.create(state).getTotalBalances().getCurrentEpoch());
  }

  @Override
  public void processRegistryUpdates(final MutableBeaconState state)
      throws EpochProcessingException {
    epochProcessor.processRegistryUpdates(state, ValidatorStatuses.create(state).getStatuses());
  }

  @Override
  public void processFinalUpdates(final MutableBeaconState state) {
    epochProcessor.processFinalUpdates(state);
  }

  @Override
  public void processRewardsAndPenalties(final MutableBeaconState state)
      throws EpochProcessingException {
    epochProcessor.processRewardsAndPenalties(state, ValidatorStatuses.create(state));
  }

  @Override
  public void processJustificationAndFinalization(final MutableBeaconState state)
      throws EpochProcessingException {
    epochProcessor.processJustificationAndFinalization(
        state, ValidatorStatuses.create(state).getTotalBalances());
  }
}
