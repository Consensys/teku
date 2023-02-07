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

package tech.pegasys.teku.api.schema.deneb;

import static tech.pegasys.teku.api.schema.SchemaConstants.EXAMPLE_UINT64;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.schema.BeaconBlockHeader;
import tech.pegasys.teku.api.schema.Checkpoint;
import tech.pegasys.teku.api.schema.Eth1Data;
import tech.pegasys.teku.api.schema.Fork;
import tech.pegasys.teku.api.schema.Validator;
import tech.pegasys.teku.api.schema.altair.BeaconStateAltair;
import tech.pegasys.teku.api.schema.altair.SyncCommittee;
import tech.pegasys.teku.api.schema.capella.HistoricalSummary;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.ExecutionPayloadHeaderSchemaDeneb;
import tech.pegasys.teku.spec.datastructures.state.SyncCommittee.SyncCommitteeSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.deneb.BeaconStateSchemaDeneb;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.deneb.MutableBeaconStateDeneb;

public class BeaconStateDeneb extends BeaconStateAltair {

  @JsonProperty("latest_execution_payload_header")
  public final ExecutionPayloadHeaderDeneb latestExecutionPayloadHeader;

  @JsonProperty("next_withdrawal_index")
  @Schema(type = "string", example = EXAMPLE_UINT64)
  public final UInt64 nextWithdrawalIndex;

  @JsonProperty("next_withdrawal_validator_index")
  @Schema(type = "string", example = EXAMPLE_UINT64)
  public final UInt64 nextWithdrawalValidatorIndex;

  @JsonProperty("historical_summaries")
  public final List<HistoricalSummary> historicalSummaries;

  public BeaconStateDeneb(
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
          final ExecutionPayloadHeaderDeneb latestExecutionPayloadHeader,
      @JsonProperty("next_withdrawal_index") final UInt64 nextWithdrawalIndex,
      @JsonProperty("next_withdrawal_validator_index") final UInt64 nextWithdrawalValidatorIndex,
      @JsonProperty("historical_summaries") final List<HistoricalSummary> historicalSummaries) {
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
    this.historicalSummaries = historicalSummaries;
  }

  public BeaconStateDeneb(final BeaconState beaconState) {
    super(beaconState);
    final tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.deneb.BeaconStateDeneb
        deneb = beaconState.toVersionDeneb().orElseThrow();
    this.latestExecutionPayloadHeader =
        new ExecutionPayloadHeaderDeneb(deneb.getLatestExecutionPayloadHeader());
    this.nextWithdrawalIndex = deneb.getNextWithdrawalIndex();
    this.nextWithdrawalValidatorIndex = deneb.getNextWithdrawalValidatorIndex();
    this.historicalSummaries =
        deneb.getHistoricalSummaries().stream()
            .map(HistoricalSummary::new)
            .collect(Collectors.toList());
  }

  @Override
  protected void applyAdditionalFields(
      final MutableBeaconState state, final SpecVersion specVersion) {
    state
        .toMutableVersionDeneb()
        .ifPresent(
            mutableBeaconStateDeneb ->
                applyDenebFields(
                    specVersion,
                    mutableBeaconStateDeneb,
                    BeaconStateSchemaDeneb.required(mutableBeaconStateDeneb.getBeaconStateSchema())
                        .getCurrentSyncCommitteeSchema(),
                    BeaconStateSchemaDeneb.required(mutableBeaconStateDeneb.getBeaconStateSchema())
                        .getLastExecutionPayloadHeaderSchema(),
                    BeaconStateSchemaDeneb.required(mutableBeaconStateDeneb.getBeaconStateSchema())
                        .getHistoricalSummariesSchema(),
                    this));
  }

  protected static void applyDenebFields(
      final SpecVersion specVersion,
      final MutableBeaconStateDeneb state,
      final SyncCommitteeSchema syncCommitteeSchema,
      final ExecutionPayloadHeaderSchemaDeneb executionPayloadHeaderSchema,
      SszListSchema<
              tech.pegasys.teku.spec.datastructures.state.versions.capella.HistoricalSummary, ?>
          historicalSummariesSchema,
      final BeaconStateDeneb instance) {

    BeaconStateAltair.applyAltairFields(state, syncCommitteeSchema, instance);

    state.setLatestExecutionPayloadHeader(
        instance.latestExecutionPayloadHeader.asInternalExecutionPayloadHeader(
            executionPayloadHeaderSchema));

    state.setNextWithdrawalIndex(instance.nextWithdrawalIndex);
    state.setNextWithdrawalValidatorIndex(instance.nextWithdrawalValidatorIndex);
    state.setHistoricalSummaries(
        historicalSummariesSchema.createFromElements(
            instance.historicalSummaries.stream()
                .map(
                    historicalSummary -> historicalSummary.asInternalHistoricalSummary(specVersion))
                .collect(Collectors.toList())));
  }
}
