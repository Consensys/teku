/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.spec.logic.versions.eip4844.forktransition;

import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigEip4844;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.ExecutionPayloadHeaderCapella;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.capella.BeaconStateCapella;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.eip4844.BeaconStateEip4844;
import tech.pegasys.teku.spec.logic.common.forktransition.StateUpgrade;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.BeaconStateAccessorsAltair;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsEip4844;

public class Eip4844StateUpgrade implements StateUpgrade<BeaconStateEip4844> {

  private final SpecConfigEip4844 specConfig;
  private final SchemaDefinitionsEip4844 schemaDefinitions;
  private final BeaconStateAccessorsAltair beaconStateAccessors;

  public Eip4844StateUpgrade(
      final SpecConfigEip4844 specConfig,
      final SchemaDefinitionsEip4844 schemaDefinitions,
      final BeaconStateAccessorsAltair beaconStateAccessors) {
    this.specConfig = specConfig;
    this.schemaDefinitions = schemaDefinitions;
    this.beaconStateAccessors = beaconStateAccessors;
  }

  @Override
  public BeaconStateEip4844 upgrade(final BeaconState preState) {
    final UInt64 epoch = beaconStateAccessors.getCurrentEpoch(preState);
    BeaconStateCapella preStateCapella = BeaconStateCapella.required(preState);
    return schemaDefinitions
        .getBeaconStateSchema()
        .createEmpty()
        .updatedEip4844(
            state -> {
              BeaconStateFields.copyCommonFieldsFromSource(state, preState);

              state.setCurrentEpochParticipation(preStateCapella.getCurrentEpochParticipation());
              state.setPreviousEpochParticipation(preStateCapella.getPreviousEpochParticipation());
              state.setCurrentSyncCommittee(preStateCapella.getCurrentSyncCommittee());
              state.setNextSyncCommittee(preStateCapella.getNextSyncCommittee());
              state.setInactivityScores(preStateCapella.getInactivityScores());

              state.setFork(
                  new Fork(
                      preState.getFork().getCurrentVersion(),
                      specConfig.getEip4844ForkVersion(),
                      epoch));

              final ExecutionPayloadHeaderCapella capellaHeader =
                  preStateCapella
                      .getLatestExecutionPayloadHeader()
                      .toVersionCapella()
                      .orElseThrow();
              final ExecutionPayloadHeader upgradedExecutionPayloadHeader =
                  schemaDefinitions
                      .getExecutionPayloadHeaderSchema()
                      .createExecutionPayloadHeader(
                          builder ->
                              builder
                                  .parentHash(capellaHeader.getParentHash())
                                  .feeRecipient(capellaHeader.getFeeRecipient())
                                  .stateRoot(capellaHeader.getStateRoot())
                                  .receiptsRoot(capellaHeader.getReceiptsRoot())
                                  .logsBloom(capellaHeader.getLogsBloom())
                                  .prevRandao(capellaHeader.getPrevRandao())
                                  .blockNumber(capellaHeader.getBlockNumber())
                                  .gasLimit(capellaHeader.getGasLimit())
                                  .gasUsed(capellaHeader.getGasUsed())
                                  .timestamp(capellaHeader.getTimestamp())
                                  .extraData(capellaHeader.getExtraData())
                                  .baseFeePerGas(capellaHeader.getBaseFeePerGas())
                                  .blockHash(capellaHeader.getBlockHash())
                                  .transactionsRoot(capellaHeader.getTransactionsRoot())
                                  .withdrawalsRoot(capellaHeader::getWithdrawalsRoot)
                                  .excessDataGas(() -> UInt256.ZERO));

              state.setLatestExecutionPayloadHeader(upgradedExecutionPayloadHeader);

              state.setNextWithdrawalValidatorIndex(
                  preStateCapella.getNextWithdrawalValidatorIndex());
              state.setNextWithdrawalIndex(preStateCapella.getNextWithdrawalIndex());
            });
  }
}
