/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.spec.logic.versions.eip7805.forktransition;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigEip7805;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.logic.common.forktransition.StateUpgrade;
import tech.pegasys.teku.spec.logic.versions.eip7805.helpers.BeaconStateAccessorsEip7805;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsEip7805;

public class Eip7805StateUpgrade implements StateUpgrade<BeaconStateGloas> {

  private final SpecConfigEip7805 specConfig;
  private final SchemaDefinitionsEip7805 schemaDefinitions;
  private final BeaconStateAccessorsEip7805 beaconStateAccessors;

  public Eip7805StateUpgrade(
      final SpecConfigEip7805 specConfig,
      final SchemaDefinitionsEip7805 schemaDefinitions,
      final BeaconStateAccessorsEip7805 beaconStateAccessors) {
    this.specConfig = specConfig;
    this.schemaDefinitions = schemaDefinitions;
    this.beaconStateAccessors = beaconStateAccessors;
  }

  @Override
  public BeaconStateGloas upgrade(final BeaconState preState) {
    final UInt64 epoch = beaconStateAccessors.getCurrentEpoch(preState);
    final BeaconStateGloas preStateGloas = BeaconStateGloas.required(preState);
    return BeaconStateGloas.required(schemaDefinitions.getBeaconStateSchema().createEmpty())
        .updatedGloas(
            state -> {
              BeaconStateFields.copyCommonFieldsFromSource(state, preState);

              state.setCurrentEpochParticipation(preStateGloas.getCurrentEpochParticipation());
              state.setPreviousEpochParticipation(preStateGloas.getPreviousEpochParticipation());
              state.setCurrentSyncCommittee(preStateGloas.getCurrentSyncCommittee());
              state.setNextSyncCommittee(preStateGloas.getNextSyncCommittee());
              state.setInactivityScores(preStateGloas.getInactivityScores());

              state.setFork(
                  new Fork(
                      preState.getFork().getCurrentVersion(),
                      specConfig.getEip7805ForkVersion(),
                      epoch));

              state.setLatestExecutionPayloadHeader(
                  preStateGloas.getLatestExecutionPayloadHeader());
              state.setNextWithdrawalValidatorIndex(
                  preStateGloas.getNextWithdrawalValidatorIndex());
              state.setNextWithdrawalIndex(preStateGloas.getNextWithdrawalIndex());
              state.setHistoricalSummaries(preStateGloas.getHistoricalSummaries());
              state.setDepositRequestsStartIndex(preStateGloas.getDepositRequestsStartIndex());
              state.setDepositBalanceToConsume(preStateGloas.getDepositBalanceToConsume());
              state.setExitBalanceToConsume(preStateGloas.getExitBalanceToConsume());
              state.setEarliestExitEpoch(preStateGloas.getEarliestExitEpoch());
              state.setConsolidationBalanceToConsume(
                  preStateGloas.getConsolidationBalanceToConsume());
              state.setEarliestConsolidationEpoch(preStateGloas.getEarliestConsolidationEpoch());
              state.setPendingDeposits(preStateGloas.getPendingDeposits());
              state.setPendingPartialWithdrawals(preStateGloas.getPendingPartialWithdrawals());
              state.setPendingConsolidations(preStateGloas.getPendingConsolidations());
              state.setProposerLookahead(preStateGloas.getProposerLookahead());
            });
  }
}
