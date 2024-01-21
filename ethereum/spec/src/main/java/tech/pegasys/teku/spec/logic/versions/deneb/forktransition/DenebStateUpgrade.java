/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.spec.logic.versions.deneb.forktransition;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionPayloadHeaderElectra;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.deneb.BeaconStateDeneb;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateElectra;
import tech.pegasys.teku.spec.logic.common.forktransition.StateUpgrade;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.BeaconStateAccessorsAltair;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;

public class DenebStateUpgrade implements StateUpgrade<BeaconStateDeneb> {

  private final SpecConfigDeneb specConfig;
  private final SchemaDefinitionsDeneb schemaDefinitions;
  private final BeaconStateAccessorsAltair beaconStateAccessors;

  public DenebStateUpgrade(
      final SpecConfigDeneb specConfig,
      final SchemaDefinitionsDeneb schemaDefinitions,
      final BeaconStateAccessorsAltair beaconStateAccessors) {
    this.specConfig = specConfig;
    this.schemaDefinitions = schemaDefinitions;
    this.beaconStateAccessors = beaconStateAccessors;
  }

  @Override
  public BeaconStateDeneb upgrade(final BeaconState preState) {
    final UInt64 epoch = beaconStateAccessors.getCurrentEpoch(preState);
    final BeaconStateElectra preStateElectra = BeaconStateElectra.required(preState);
    return schemaDefinitions
        .getBeaconStateSchema()
        .createEmpty()
        .updatedDeneb(
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
                      specConfig.getDenebForkVersion(),
                      epoch));

              final ExecutionPayloadHeaderElectra electraHeader =
                  preStateElectra
                      .getLatestExecutionPayloadHeader()
                      .toVersionElectra()
                      .orElseThrow();
              final ExecutionPayloadHeader upgradedExecutionPayloadHeader =
                  schemaDefinitions
                      .getExecutionPayloadHeaderSchema()
                      .createExecutionPayloadHeader(
                          builder ->
                              builder
                                  .parentHash(electraHeader.getParentHash())
                                  .feeRecipient(electraHeader.getFeeRecipient())
                                  .stateRoot(electraHeader.getStateRoot())
                                  .receiptsRoot(electraHeader.getReceiptsRoot())
                                  .logsBloom(electraHeader.getLogsBloom())
                                  .prevRandao(electraHeader.getPrevRandao())
                                  .blockNumber(electraHeader.getBlockNumber())
                                  .gasLimit(electraHeader.getGasLimit())
                                  .gasUsed(electraHeader.getGasUsed())
                                  .timestamp(electraHeader.getTimestamp())
                                  .extraData(electraHeader.getExtraData())
                                  .baseFeePerGas(electraHeader.getBaseFeePerGas())
                                  .blockHash(electraHeader.getBlockHash())
                                  .transactionsRoot(electraHeader.getTransactionsRoot())
                                  .withdrawalsRoot(electraHeader::getWithdrawalsRoot)
                                  .executionWitnessRoot(electraHeader::getExecutionWitnessRoot)
                                  .blobGasUsed(() -> UInt64.ZERO)
                                  .excessBlobGas(() -> UInt64.ZERO));

              state.setLatestExecutionPayloadHeader(upgradedExecutionPayloadHeader);

              state.setNextWithdrawalValidatorIndex(
                  preStateElectra.getNextWithdrawalValidatorIndex());
              state.setNextWithdrawalIndex(preStateElectra.getNextWithdrawalIndex());
              state.setHistoricalSummaries(preStateElectra.getHistoricalSummaries());
            });
  }
}
