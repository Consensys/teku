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

package tech.pegasys.teku.spec.logic.versions.electra.forktransition;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.ExecutionPayloadHeaderDeneb;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.deneb.BeaconStateDeneb;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateElectra;
import tech.pegasys.teku.spec.logic.common.forktransition.StateUpgrade;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.BeaconStateAccessorsAltair;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;

public class ElectraStateUpgrade implements StateUpgrade<BeaconStateDeneb> {

  private final SpecConfigElectra specConfig;
  private final SchemaDefinitionsElectra schemaDefinitions;
  private final BeaconStateAccessorsAltair beaconStateAccessors;

  public ElectraStateUpgrade(
      final SpecConfigElectra specConfig,
      final SchemaDefinitionsElectra schemaDefinitions,
      final BeaconStateAccessorsAltair beaconStateAccessors) {
    this.specConfig = specConfig;
    this.schemaDefinitions = schemaDefinitions;
    this.beaconStateAccessors = beaconStateAccessors;
  }

  @Override
  public BeaconStateElectra upgrade(final BeaconState preState) {
    final UInt64 epoch = beaconStateAccessors.getCurrentEpoch(preState);
    final BeaconStateDeneb preStateDeneb = BeaconStateDeneb.required(preState);
    return schemaDefinitions
        .getBeaconStateSchema()
        .createEmpty()
        .updatedElectra(
            state -> {
              BeaconStateFields.copyCommonFieldsFromSource(state, preState);

              state.setCurrentEpochParticipation(preStateDeneb.getCurrentEpochParticipation());
              state.setPreviousEpochParticipation(preStateDeneb.getPreviousEpochParticipation());
              state.setCurrentSyncCommittee(preStateDeneb.getCurrentSyncCommittee());
              state.setNextSyncCommittee(preStateDeneb.getNextSyncCommittee());
              state.setInactivityScores(preStateDeneb.getInactivityScores());

              state.setFork(
                  new Fork(
                      preState.getFork().getCurrentVersion(),
                      specConfig.getElectraForkVersion(),
                      epoch));

              final ExecutionPayloadHeaderDeneb denebHeader =
                  preStateDeneb.getLatestExecutionPayloadHeader().toVersionDeneb().orElseThrow();
              final ExecutionPayloadHeader upgradedExecutionPayloadHeader =
                  schemaDefinitions
                      .getExecutionPayloadHeaderSchema()
                      .createExecutionPayloadHeader(
                          builder ->
                              builder
                                  .parentHash(denebHeader.getParentHash())
                                  .feeRecipient(denebHeader.getFeeRecipient())
                                  .stateRoot(denebHeader.getStateRoot())
                                  .receiptsRoot(denebHeader.getReceiptsRoot())
                                  .logsBloom(denebHeader.getLogsBloom())
                                  .prevRandao(denebHeader.getPrevRandao())
                                  .blockNumber(denebHeader.getBlockNumber())
                                  .gasLimit(denebHeader.getGasLimit())
                                  .gasUsed(denebHeader.getGasUsed())
                                  .timestamp(denebHeader.getTimestamp())
                                  .extraData(denebHeader.getExtraData())
                                  .baseFeePerGas(denebHeader.getBaseFeePerGas())
                                  .blockHash(denebHeader.getBlockHash())
                                  .transactionsRoot(denebHeader.getTransactionsRoot())
                                  .withdrawalsRoot(denebHeader::getWithdrawalsRoot)
                                  .blobGasUsed(denebHeader::getBlobGasUsed)
                                  .excessBlobGas(denebHeader::getExcessBlobGas)
                                  .previousInclusionListSummaryRoot(() -> Bytes32.ZERO));

              state.setLatestExecutionPayloadHeader(upgradedExecutionPayloadHeader);

              state.setNextWithdrawalValidatorIndex(
                  preStateDeneb.getNextWithdrawalValidatorIndex());
              state.setNextWithdrawalIndex(preStateDeneb.getNextWithdrawalIndex());
              state.setHistoricalSummaries(preStateDeneb.getHistoricalSummaries());

              state.setPreviousProposerIndex(UInt64.ZERO);
            });
  }
}
