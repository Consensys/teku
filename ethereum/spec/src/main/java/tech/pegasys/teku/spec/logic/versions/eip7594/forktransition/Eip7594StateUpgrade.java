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

package tech.pegasys.teku.spec.logic.versions.eip7594.forktransition;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigEip7594;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.ExecutionPayloadHeaderDeneb;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.deneb.BeaconStateDeneb;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.eip7594.BeaconStateEip7594;
import tech.pegasys.teku.spec.logic.common.forktransition.StateUpgrade;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.BeaconStateAccessorsAltair;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsEip7594;

public class Eip7594StateUpgrade implements StateUpgrade<BeaconStateDeneb> {

  private final SpecConfigEip7594 specConfig;
  private final SchemaDefinitionsEip7594 schemaDefinitions;
  private final BeaconStateAccessorsAltair beaconStateAccessors;

  public Eip7594StateUpgrade(
      final SpecConfigEip7594 specConfig,
      final SchemaDefinitionsEip7594 schemaDefinitions,
      final BeaconStateAccessorsAltair beaconStateAccessors) {
    this.specConfig = specConfig;
    this.schemaDefinitions = schemaDefinitions;
    this.beaconStateAccessors = beaconStateAccessors;
  }

  @Override
  public BeaconStateEip7594 upgrade(final BeaconState preState) {
    final UInt64 epoch = beaconStateAccessors.getCurrentEpoch(preState);
    final BeaconStateDeneb preStateDeneb = BeaconStateDeneb.required(preState);
    return schemaDefinitions
        .getBeaconStateSchema()
        .createEmpty()
        .updatedEip7594(
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
                      specConfig.getEip7594ForkVersion(),
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
                                  .excessBlobGas(denebHeader::getExcessBlobGas));

              state.setLatestExecutionPayloadHeader(upgradedExecutionPayloadHeader);

              state.setNextWithdrawalValidatorIndex(
                  preStateDeneb.getNextWithdrawalValidatorIndex());
              state.setNextWithdrawalIndex(preStateDeneb.getNextWithdrawalIndex());
              state.setHistoricalSummaries(preStateDeneb.getHistoricalSummaries());
            });
  }
}
