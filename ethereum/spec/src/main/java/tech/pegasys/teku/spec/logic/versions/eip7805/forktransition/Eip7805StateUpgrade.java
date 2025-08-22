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
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.fulu.BeaconStateFulu;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.fulu.BeaconStateSchemaFulu;
import tech.pegasys.teku.spec.logic.common.forktransition.StateUpgrade;
import tech.pegasys.teku.spec.logic.versions.eip7805.helpers.BeaconStateAccessorsEip7805;
import tech.pegasys.teku.spec.logic.versions.eip7805.helpers.MiscHelpersEip7805;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsEip7805;

public class Eip7805StateUpgrade implements StateUpgrade<BeaconStateFulu> {

  private final SpecConfigEip7805 specConfig;
  private final SchemaDefinitionsEip7805 schemaDefinitions;
  private final BeaconStateAccessorsEip7805 beaconStateAccessors;
  private final MiscHelpersEip7805 miscHelpers;

  public Eip7805StateUpgrade(
      final SpecConfigEip7805 specConfig,
      final SchemaDefinitionsEip7805 schemaDefinitions,
      final BeaconStateAccessorsEip7805 beaconStateAccessors,
      final MiscHelpersEip7805 miscHelpers) {
    this.specConfig = specConfig;
    this.schemaDefinitions = schemaDefinitions;
    this.beaconStateAccessors = beaconStateAccessors;
    this.miscHelpers = miscHelpers;
  }

  @Override
  public BeaconStateFulu upgrade(final BeaconState preState) {
    final UInt64 epoch = beaconStateAccessors.getCurrentEpoch(preState);
    final BeaconStateFulu preStateFulu = BeaconStateFulu.required(preState);
    return BeaconStateFulu.required(schemaDefinitions.getBeaconStateSchema().createEmpty())
        .updatedFulu(
            state -> {
              BeaconStateFields.copyCommonFieldsFromSource(state, preState);

              state.setCurrentEpochParticipation(preStateFulu.getCurrentEpochParticipation());
              state.setPreviousEpochParticipation(preStateFulu.getPreviousEpochParticipation());
              state.setCurrentSyncCommittee(preStateFulu.getCurrentSyncCommittee());
              state.setNextSyncCommittee(preStateFulu.getNextSyncCommittee());
              state.setInactivityScores(preStateFulu.getInactivityScores());

              state.setFork(
                  new Fork(
                      preState.getFork().getCurrentVersion(),
                      specConfig.getEip7805ForkVersion(),
                      epoch));

              state.setLatestExecutionPayloadHeader(preStateFulu.getLatestExecutionPayloadHeader());
              state.setNextWithdrawalValidatorIndex(preStateFulu.getNextWithdrawalValidatorIndex());
              state.setNextWithdrawalIndex(preStateFulu.getNextWithdrawalIndex());
              state.setHistoricalSummaries(preStateFulu.getHistoricalSummaries());
              state.setDepositRequestsStartIndex(preStateFulu.getDepositRequestsStartIndex());
              state.setDepositBalanceToConsume(UInt64.ZERO);
              state.setExitBalanceToConsume(preStateFulu.getExitBalanceToConsume());
              state.setEarliestExitEpoch(preStateFulu.getEarliestExitEpoch());
              state.setConsolidationBalanceToConsume(
                  preStateFulu.getConsolidationBalanceToConsume());
              state.setEarliestConsolidationEpoch(preStateFulu.getEarliestConsolidationEpoch());
              state.setPendingDeposits(preStateFulu.getPendingDeposits());
              state.setPendingPartialWithdrawals(preStateFulu.getPendingPartialWithdrawals());
              state.setPendingDeposits(preStateFulu.getPendingDeposits());

              state.setProposerLookahead(
                  BeaconStateSchemaFulu.required(schemaDefinitions.getBeaconStateSchema())
                      .getProposerLookaheadSchema()
                      .of(
                          miscHelpers.initializeProposerLookahead(
                              preStateFulu, beaconStateAccessors)));
            });
  }
}
