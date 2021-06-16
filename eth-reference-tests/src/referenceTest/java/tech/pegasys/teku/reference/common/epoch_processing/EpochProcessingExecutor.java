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

package tech.pegasys.teku.reference.common.epoch_processing;

import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.EpochProcessor;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatusFactory;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.EpochProcessingException;

public class EpochProcessingExecutor {
  private final EpochProcessor epochProcessor;
  private final ValidatorStatusFactory validatorStatusFactory;

  public EpochProcessingExecutor(
      final EpochProcessor epochProcessor, final ValidatorStatusFactory validatorStatusFactory) {
    this.epochProcessor = epochProcessor;
    this.validatorStatusFactory = validatorStatusFactory;
  }

  public void executeOperation(final EpochOperation operation, final MutableBeaconState state)
      throws EpochProcessingException {
    switch (operation) {
      case PROCESS_SLASHINGS:
        processSlashings(state);
        break;
      case PROCESS_REGISTRY_UPDATES:
        processRegistryUpdates(state);
        break;
      case PROCESS_REWARDS_AND_PENALTIES:
        processRewardsAndPenalties(state);
        break;
      case PROCESS_JUSTIFICATION_AND_FINALIZATION:
        processJustificationAndFinalization(state);
        break;
      case PROCESS_EFFECTIVE_BALANCE_UPDATES:
        epochProcessor.processEffectiveBalanceUpdates(state);
        break;
      case PROCESS_PARTICIPATION_FLAG_UPDATES:
        epochProcessor.processParticipationUpdates(state);
        break;
      case PROCESS_ETH1_DATA_RESET:
        epochProcessor.processEth1DataReset(state);
        break;
      case PROCESS_SLASHINGS_RESET:
        epochProcessor.processSlashingsReset(state);
        break;
      case PROCESS_RANDAO_MIXES_RESET:
        epochProcessor.processRandaoMixesReset(state);
        break;
      case PROCESS_HISTORICAL_ROOTS_UPDATE:
        epochProcessor.processHistoricalRootsUpdate(state);
        break;
      case SYNC_COMMITTEE_UPDATES:
        epochProcessor.processSyncCommitteeUpdates(state);
        break;
      case INACTIVITY_UPDATES:
        processInactivityUpdates(state);
        break;
      default:
        throw new UnsupportedOperationException(
            "Attempted to execute unknown operation type: " + operation);
    }
  }

  private void processInactivityUpdates(final MutableBeaconState state) {
    epochProcessor.processInactivityUpdates(
        state, validatorStatusFactory.createValidatorStatuses(state));
  }

  public void processSlashings(final MutableBeaconState state) {
    epochProcessor.processSlashings(
        state,
        validatorStatusFactory
            .createValidatorStatuses(state)
            .getTotalBalances()
            .getCurrentEpochActiveValidators());
  }

  public void processRegistryUpdates(final MutableBeaconState state)
      throws EpochProcessingException {
    epochProcessor.processRegistryUpdates(
        state, validatorStatusFactory.createValidatorStatuses(state).getStatuses());
  }

  public void processRewardsAndPenalties(final MutableBeaconState state)
      throws EpochProcessingException {
    epochProcessor.processRewardsAndPenalties(
        state, validatorStatusFactory.createValidatorStatuses(state));
  }

  public void processJustificationAndFinalization(final MutableBeaconState state)
      throws EpochProcessingException {
    epochProcessor.processJustificationAndFinalization(
        state, validatorStatusFactory.createValidatorStatuses(state).getTotalBalances());
  }
}
