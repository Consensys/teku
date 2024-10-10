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

package tech.pegasys.teku.spec.logic.versions.eip7732.forktransition;

import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigEip7732;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.eip7732.BeaconStateEip7732;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateElectra;
import tech.pegasys.teku.spec.logic.common.forktransition.StateUpgrade;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.BeaconStateAccessorsElectra;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.MiscHelpersElectra;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.PredicatesElectra;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsEip7732;

public class Eip7732StateUpgrade implements StateUpgrade<BeaconStateElectra> {

  private final SpecConfigEip7732 specConfig;
  private final SchemaDefinitionsEip7732 schemaDefinitions;
  private final BeaconStateAccessorsElectra beaconStateAccessors;

  public Eip7732StateUpgrade(
      final SpecConfigEip7732 specConfig,
      final SchemaDefinitionsEip7732 schemaDefinitions,
      final BeaconStateAccessorsElectra beaconStateAccessors) {
    this.specConfig = specConfig;
    this.schemaDefinitions = schemaDefinitions;
    this.beaconStateAccessors = beaconStateAccessors;
  }

  @Override
  public BeaconStateEip7732 upgrade(final BeaconState preState) {
    final UInt64 epoch = beaconStateAccessors.getCurrentEpoch(preState);
    final BeaconStateElectra preStateElectra = BeaconStateElectra.required(preState);
    final PredicatesElectra predicatesElectra = new PredicatesElectra(specConfig);
    final MiscHelpersElectra miscHelpersElectra =
        new MiscHelpersElectra(specConfig, predicatesElectra, schemaDefinitions);
    return schemaDefinitions
        .getBeaconStateSchema()
        .createEmpty()
        .updatedEip7732(
            state -> {
              BeaconStateFields.copyCommonFieldsFromSource(state, preState);

              state.setCurrentEpochParticipation(preStateElectra.getCurrentEpochParticipation());
              state.setPreviousEpochParticipation(preStateElectra.getPreviousEpochParticipation());
              state.setCurrentSyncCommittee(preStateElectra.getCurrentSyncCommittee());
              state.setNextSyncCommittee(preStateElectra.getNextSyncCommittee());
              state.setInactivityScores(preStateElectra.getInactivityScores());

              state.setFork(
                  new Fork(
                      preState.getFork().getCurrentVersion(),
                      specConfig.getEip7732ForkVersion(),
                      epoch));

              final ExecutionPayloadHeader upgradedExecutionPayloadHeader =
                  schemaDefinitions
                      .getExecutionPayloadHeaderSchema()
                      .createExecutionPayloadHeader(
                          builder ->
                              builder
                                  .parentBlockHash(() -> Bytes32.ZERO)
                                  .parentBlockRoot(() -> Bytes32.ZERO)
                                  .blockHash(Bytes32.ZERO)
                                  .gasLimit(UInt64.ZERO)
                                  .builderIndex(() -> UInt64.ZERO)
                                  .slot(() -> UInt64.ZERO)
                                  .value(() -> UInt64.ZERO)
                                  .blobKzgCommitmentsRoot(() -> Bytes32.ZERO));

              state.setLatestExecutionPayloadHeader(upgradedExecutionPayloadHeader);

              state.setNextWithdrawalValidatorIndex(
                  preStateElectra.getNextWithdrawalValidatorIndex());
              state.setNextWithdrawalIndex(preStateElectra.getNextWithdrawalIndex());
              state.setHistoricalSummaries(preStateElectra.getHistoricalSummaries());
              state.setDepositRequestsStartIndex(
                  SpecConfigElectra.UNSET_DEPOSIT_REQUESTS_START_INDEX);
              state.setDepositBalanceToConsume(UInt64.ZERO);
              state.setExitBalanceToConsume(
                  beaconStateAccessors.getActivationExitChurnLimit(state));
              state.setEarliestExitEpoch(findEarliestExitEpoch(state, epoch));
              state.setConsolidationBalanceToConsume(
                  beaconStateAccessors.getConsolidationChurnLimit(state));
              state.setEarliestConsolidationEpoch(
                  miscHelpersElectra.computeActivationExitEpoch(epoch));
              state.setPendingDeposits(preStateElectra.getPendingDeposits());
              state.setPendingPartialWithdrawals(preStateElectra.getPendingPartialWithdrawals());
              state.setPendingConsolidations(preStateElectra.getPendingConsolidations());
              // ePBS
              state.setLatestBlockHash(
                  preStateElectra.getLatestExecutionPayloadHeader().getBlockHash());
              state.setLatestFullSlot(preState.getSlot());
              state.setLatestWithdrawalsRoot(Bytes32.ZERO);
            });
  }

  private UInt64 findEarliestExitEpoch(final BeaconState state, final UInt64 currentEpoch) {
    return state.getValidators().stream()
        .map(Validator::getExitEpoch)
        .filter(exitEpoch -> !exitEpoch.equals(FAR_FUTURE_EPOCH))
        .max(UInt64::compareTo)
        .orElse(currentEpoch)
        .increment();
  }
}
