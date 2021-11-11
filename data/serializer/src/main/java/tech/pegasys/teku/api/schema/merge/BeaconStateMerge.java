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

package tech.pegasys.teku.api.schema.merge;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.schema.BeaconBlockHeader;
import tech.pegasys.teku.api.schema.Checkpoint;
import tech.pegasys.teku.api.schema.Eth1Data;
import tech.pegasys.teku.api.schema.Fork;
import tech.pegasys.teku.api.schema.Validator;
import tech.pegasys.teku.api.schema.altair.BeaconStateAltair;
import tech.pegasys.teku.api.schema.altair.SyncCommittee;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.merge.BeaconStateSchemaMerge;
import tech.pegasys.teku.ssz.SszList;
import tech.pegasys.teku.ssz.collections.SszBitvector;
import tech.pegasys.teku.ssz.primitive.SszByte;

public class BeaconStateMerge extends BeaconStateAltair {
  @JsonProperty("latest_execution_payload_header")
  public final ExecutionPayloadHeader latestExecutionPayloadHeader;

  @JsonCreator
  public BeaconStateMerge(
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
          final ExecutionPayloadHeader latestExecutionPayloadHeader) {
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
  }

  @Override
  protected void applyAdditionalFields(MutableBeaconState state) {
    state
        .toMutableVersionMerge()
        .ifPresent(
            beaconStateMerge -> {
              final tech.pegasys.teku.spec.datastructures.state.SyncCommittee.SyncCommitteeSchema
                  syncCommitteeSchema =
                      BeaconStateSchemaMerge.required(beaconStateMerge.getBeaconStateSchema())
                          .getCurrentSyncCommitteeSchema();
              final SszList<SszByte> previousEpochParticipation =
                  beaconStateMerge
                      .getPreviousEpochParticipation()
                      .getSchema()
                      .sszDeserialize(Bytes.wrap(this.previous_epoch_participation));
              final SszList<SszByte> currentEpochParticipation =
                  beaconStateMerge
                      .getCurrentEpochParticipation()
                      .getSchema()
                      .sszDeserialize(Bytes.wrap(this.current_epoch_participation));

              beaconStateMerge.setPreviousEpochParticipation(previousEpochParticipation);
              beaconStateMerge.setCurrentEpochParticipation(currentEpochParticipation);
              beaconStateMerge.getInactivityScores().setAllElements(inactivity_scores);

              beaconStateMerge.setCurrentSyncCommittee(
                  current_sync_committee.asInternalSyncCommittee(syncCommitteeSchema));
              beaconStateMerge.setNextSyncCommittee(
                  next_sync_committee.asInternalSyncCommittee(syncCommitteeSchema));

              final tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeaderSchema
                  executionPayloadHeaderSchema =
                      BeaconStateSchemaMerge.required(beaconStateMerge.getBeaconStateSchema())
                          .getLastExecutionPayloadHeaderSchema();
              beaconStateMerge.setLatestExecutionPayloadHeader(
                  executionPayloadHeaderSchema.create(
                      latestExecutionPayloadHeader.parentHash,
                      latestExecutionPayloadHeader.coinbase,
                      latestExecutionPayloadHeader.stateRoot,
                      latestExecutionPayloadHeader.receiptRoot,
                      latestExecutionPayloadHeader.logsBloom,
                      latestExecutionPayloadHeader.random,
                      latestExecutionPayloadHeader.blockNumber,
                      latestExecutionPayloadHeader.gasLimit,
                      latestExecutionPayloadHeader.gasUsed,
                      latestExecutionPayloadHeader.timestamp,
                      latestExecutionPayloadHeader.extraData,
                      latestExecutionPayloadHeader.baseFeePerGas,
                      latestExecutionPayloadHeader.blockHash,
                      latestExecutionPayloadHeader.transactionsRoot));
            });
  }

  public BeaconStateMerge(BeaconState beaconState) {
    super(beaconState);
    final tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.merge.BeaconStateMerge
        merge = beaconState.toVersionMerge().orElseThrow();
    this.latestExecutionPayloadHeader =
        new ExecutionPayloadHeader(merge.getLatestExecutionPayloadHeader());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof BeaconStateMerge)) {
      return false;
    }
    if (!super.equals(o)) return false;
    BeaconStateMerge that = (BeaconStateMerge) o;
    return Objects.equals(latestExecutionPayloadHeader, that.latestExecutionPayloadHeader);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), latestExecutionPayloadHeader);
  }
}
