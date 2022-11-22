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

package tech.pegasys.teku.api.schema.eip4844;

import static tech.pegasys.teku.api.schema.SchemaConstants.EXAMPLE_UINT64;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.schema.BeaconBlockHeader;
import tech.pegasys.teku.api.schema.Checkpoint;
import tech.pegasys.teku.api.schema.Eth1Data;
import tech.pegasys.teku.api.schema.Fork;
import tech.pegasys.teku.api.schema.Validator;
import tech.pegasys.teku.api.schema.altair.BeaconStateAltair;
import tech.pegasys.teku.api.schema.altair.SyncCommittee;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip4844.ExecutionPayloadHeaderSchemaEip4844;
import tech.pegasys.teku.spec.datastructures.state.SyncCommittee.SyncCommitteeSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.eip4844.BeaconStateSchemaEip4844;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.eip4844.MutableBeaconStateEip4844;

public class BeaconStateEip4844 extends BeaconStateAltair {

  @JsonProperty("latest_execution_payload_header")
  public final ExecutionPayloadHeaderEip4844 latestExecutionPayloadHeader;

  @JsonProperty("next_withdrawal_index")
  @Schema(type = "string", example = EXAMPLE_UINT64)
  public final UInt64 nextWithdrawalIndex;

  @JsonProperty("next_withdrawal_validator_index")
  @Schema(type = "string", example = EXAMPLE_UINT64)
  public final UInt64 nextWithdrawalValidatorIndex;

  public BeaconStateEip4844(
      @JsonProperty("genesis_time") final UInt64 genesisTime,
      @JsonProperty("genesis_validators_root") final Bytes32 genesisValidatorsRoot,
      @JsonProperty("slot") final UInt64 slot,
      @JsonProperty("fork") final Fork fork,
      @JsonProperty("latest_block_header") final BeaconBlockHeader latestBlockHeader,
      @JsonProperty("block_roots") final List<Bytes32> blockRoots,
      @JsonProperty("state_roots") final List<Bytes32> stateRoots,
      @JsonProperty("historical_roots") final List<Bytes32> historicalRoots,
      @JsonProperty("eth1_data") final Eth1Data eth1Data,
      @JsonProperty("eth1_data_votes") final List<Eth1Data> eth1DataVotes,
      @JsonProperty("eth1_deposit_index") final UInt64 eth1DepositIndex,
      @JsonProperty("validators") final List<Validator> validators,
      @JsonProperty("balances") final List<UInt64> balances,
      @JsonProperty("randao_mixes") final List<Bytes32> randaoMixes,
      @JsonProperty("slashings") final List<UInt64> slashings,
      @JsonProperty("previous_epoch_participation") final byte[] previousEpochParticipation,
      @JsonProperty("current_epoch_participation") final byte[] currentEpochParticipation,
      @JsonProperty("justification_bits") final SszBitvector justificationBits,
      @JsonProperty("previous_justified_checkpoint") final Checkpoint previousJustifiedCheckpoint,
      @JsonProperty("current_justified_checkpoint") final Checkpoint currentJustifiedCheckpoint,
      @JsonProperty("finalized_checkpoint") final Checkpoint finalizedCheckpoint,
      @JsonProperty("inactivity_scores") final List<UInt64> inactivityScores,
      @JsonProperty("current_sync_committee") final SyncCommittee currentSyncCommittee,
      @JsonProperty("next_sync_committee") final SyncCommittee nextSyncCommittee,
      @JsonProperty("latest_execution_payload_header")
          final ExecutionPayloadHeaderEip4844 latestExecutionPayloadHeader,
      @JsonProperty("next_withdrawal_index") final UInt64 nextWithdrawalIndex,
      @JsonProperty("next_withdrawal_validator_index") final UInt64 nextWithdrawalValidatorIndex) {
    super(
        genesisTime,
        genesisValidatorsRoot,
        slot,
        fork,
        latestBlockHeader,
        blockRoots,
        stateRoots,
        historicalRoots,
        eth1Data,
        eth1DataVotes,
        eth1DepositIndex,
        validators,
        balances,
        randaoMixes,
        slashings,
        previousEpochParticipation,
        currentEpochParticipation,
        justificationBits,
        previousJustifiedCheckpoint,
        currentJustifiedCheckpoint,
        finalizedCheckpoint,
        inactivityScores,
        currentSyncCommittee,
        nextSyncCommittee);
    this.latestExecutionPayloadHeader = latestExecutionPayloadHeader;
    this.nextWithdrawalIndex = nextWithdrawalIndex;
    this.nextWithdrawalValidatorIndex = nextWithdrawalValidatorIndex;
  }

  public BeaconStateEip4844(final BeaconState beaconState) {
    super(beaconState);
    final tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.eip4844
            .BeaconStateEip4844
        eip4844 = beaconState.toVersionEip4844().orElseThrow();
    this.latestExecutionPayloadHeader =
        new ExecutionPayloadHeaderEip4844(eip4844.getLatestExecutionPayloadHeader());
    this.nextWithdrawalIndex = eip4844.getNextWithdrawalIndex();
    this.nextWithdrawalValidatorIndex = eip4844.getNextWithdrawalValidatorIndex();
  }

  @Override
  protected void applyAdditionalFields(final MutableBeaconState state) {
    state
        .toMutableVersionEip4844()
        .ifPresent(
            mutableBeaconStateEip4844 ->
                applyEip4844Fields(
                    mutableBeaconStateEip4844,
                    BeaconStateSchemaEip4844.required(
                            mutableBeaconStateEip4844.getBeaconStateSchema())
                        .getCurrentSyncCommitteeSchema(),
                    BeaconStateSchemaEip4844.required(
                            mutableBeaconStateEip4844.getBeaconStateSchema())
                        .getLastExecutionPayloadHeaderSchema(),
                    this));
  }

  public static void applyEip4844Fields(
      final MutableBeaconStateEip4844 state,
      final SyncCommitteeSchema syncCommitteeSchema,
      final ExecutionPayloadHeaderSchemaEip4844 executionPayloadHeaderSchema,
      final BeaconStateEip4844 instance) {

    BeaconStateAltair.applyAltairFields(state, syncCommitteeSchema, instance);

    state.setLatestExecutionPayloadHeader(
        executionPayloadHeaderSchema.create(
            instance.latestExecutionPayloadHeader.parentHash,
            instance.latestExecutionPayloadHeader.feeRecipient,
            instance.latestExecutionPayloadHeader.stateRoot,
            instance.latestExecutionPayloadHeader.receiptsRoot,
            instance.latestExecutionPayloadHeader.logsBloom,
            instance.latestExecutionPayloadHeader.prevRandao,
            instance.latestExecutionPayloadHeader.blockNumber,
            instance.latestExecutionPayloadHeader.gasLimit,
            instance.latestExecutionPayloadHeader.gasUsed,
            instance.latestExecutionPayloadHeader.timestamp,
            instance.latestExecutionPayloadHeader.extraData,
            instance.latestExecutionPayloadHeader.baseFeePerGas,
            instance.latestExecutionPayloadHeader.excessBlobs,
            instance.latestExecutionPayloadHeader.blockHash,
            instance.latestExecutionPayloadHeader.transactionsRoot,
            instance.latestExecutionPayloadHeader.withdrawalsRoot));

    state.setNextWithdrawalIndex(instance.nextWithdrawalIndex);
    state.setNextWithdrawalValidatorIndex(instance.nextWithdrawalValidatorIndex);
  }
}
