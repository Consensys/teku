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

package tech.pegasys.teku.api.schema.altair;

import static tech.pegasys.teku.api.schema.SchemaConstants.EXAMPLE_UINT64;
import static tech.pegasys.teku.api.schema.SchemaConstants.EXAMPLE_UINT8;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.schema.BeaconBlockHeader;
import tech.pegasys.teku.api.schema.BeaconState;
import tech.pegasys.teku.api.schema.Checkpoint;
import tech.pegasys.teku.api.schema.Eth1Data;
import tech.pegasys.teku.api.schema.Fork;
import tech.pegasys.teku.api.schema.Validator;
import tech.pegasys.teku.api.schema.interfaces.State;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateSchemaAltair;

public class BeaconStateAltair extends BeaconState implements State {
  @ArraySchema(schema = @Schema(type = "string", example = EXAMPLE_UINT8))
  public final byte[] previous_epoch_participation;

  @ArraySchema(schema = @Schema(type = "string", example = EXAMPLE_UINT8))
  public final byte[] current_epoch_participation;

  @JsonProperty("inactivity_scores")
  @ArraySchema(schema = @Schema(type = "string", example = EXAMPLE_UINT64))
  public final List<UInt64> inactivity_scores;

  public final SyncCommittee current_sync_committee;
  public final SyncCommittee next_sync_committee;

  @JsonCreator
  public BeaconStateAltair(
      @JsonProperty("genesis_time") final UInt64 genesis_time,
      @JsonProperty("genesis_validators_root") final Bytes32 genesis_validators_root,
      @JsonProperty("slot") final UInt64 slot,
      @JsonProperty("fork") final Fork fork,
      @JsonProperty("latest_block_header") final BeaconBlockHeader latest_block_header,
      @JsonProperty("block_roots") final List<Bytes32> block_roots,
      @JsonProperty("state_roots") final List<Bytes32> state_roots,
      @JsonProperty("historical_roots") final List<Bytes32> historical_roots,
      @JsonProperty("eth1_data") final Eth1Data eth1_data,
      @JsonProperty("eth1_data_votes") final List<Eth1Data> eth1_data_votes,
      @JsonProperty("eth1_deposit_index") final UInt64 eth1_deposit_index,
      @JsonProperty("validators") final List<Validator> validators,
      @JsonProperty("balances") final List<UInt64> balances,
      @JsonProperty("randao_mixes") final List<Bytes32> randao_mixes,
      @JsonProperty("slashings") final List<UInt64> slashings,
      @JsonProperty("previous_epoch_participation") final byte[] previous_epoch_participation,
      @JsonProperty("current_epoch_participation") final byte[] current_epoch_participation,
      @JsonProperty("justification_bits") final SszBitvector justification_bits,
      @JsonProperty("previous_justified_checkpoint") final Checkpoint previous_justified_checkpoint,
      @JsonProperty("current_justified_checkpoint") final Checkpoint current_justified_checkpoint,
      @JsonProperty("finalized_checkpoint") final Checkpoint finalized_checkpoint,
      @JsonProperty("inactivity_scores") final List<UInt64> inactivity_scores,
      @JsonProperty("current_sync_committee") final SyncCommittee current_sync_committee,
      @JsonProperty("next_sync_committee") final SyncCommittee next_sync_committee) {
    super(
        genesis_time,
        genesis_validators_root,
        slot,
        fork,
        latest_block_header,
        block_roots,
        state_roots,
        historical_roots,
        eth1_data,
        eth1_data_votes,
        eth1_deposit_index,
        validators,
        balances,
        randao_mixes,
        slashings,
        justification_bits,
        previous_justified_checkpoint,
        current_justified_checkpoint,
        finalized_checkpoint);
    this.previous_epoch_participation = previous_epoch_participation;
    this.current_epoch_participation = current_epoch_participation;
    this.inactivity_scores = inactivity_scores;
    this.current_sync_committee = current_sync_committee;
    this.next_sync_committee = next_sync_committee;
  }

  public BeaconStateAltair(
      final tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState beaconState) {
    super(beaconState);
    final tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateAltair
        altair = beaconState.toVersionAltair().orElseThrow();
    this.previous_epoch_participation = toByteArray(altair.getPreviousEpochParticipation());
    this.current_epoch_participation = toByteArray(altair.getCurrentEpochParticipation());
    this.inactivity_scores = altair.getInactivityScores().asListUnboxed();
    this.current_sync_committee = new SyncCommittee(altair.getCurrentSyncCommittee());
    this.next_sync_committee = new SyncCommittee(altair.getNextSyncCommittee());
  }

  @Override
  protected void applyAdditionalFields(final MutableBeaconState state) {
    state
        .toMutableVersionAltair()
        .ifPresent(
            beaconStateAltair -> {
              final tech.pegasys.teku.spec.datastructures.state.SyncCommittee.SyncCommitteeSchema
                  syncCommitteeSchema =
                      BeaconStateSchemaAltair.required(beaconStateAltair.getBeaconStateSchema())
                          .getCurrentSyncCommitteeSchema();
              final SszList<SszByte> previousEpochParticipation =
                  beaconStateAltair
                      .getPreviousEpochParticipation()
                      .getSchema()
                      .sszDeserialize(Bytes.wrap(this.previous_epoch_participation));
              final SszList<SszByte> currentEpochParticipation =
                  beaconStateAltair
                      .getCurrentEpochParticipation()
                      .getSchema()
                      .sszDeserialize(Bytes.wrap(this.current_epoch_participation));

              beaconStateAltair.setPreviousEpochParticipation(previousEpochParticipation);
              beaconStateAltair.setCurrentEpochParticipation(currentEpochParticipation);
              beaconStateAltair.getInactivityScores().setAllElements(inactivity_scores);

              beaconStateAltair.setCurrentSyncCommittee(
                  current_sync_committee.asInternalSyncCommittee(syncCommitteeSchema));
              beaconStateAltair.setNextSyncCommittee(
                  next_sync_committee.asInternalSyncCommittee(syncCommitteeSchema));
            });
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    final BeaconStateAltair that = (BeaconStateAltair) o;
    return Arrays.equals(previous_epoch_participation, that.previous_epoch_participation)
        && Arrays.equals(current_epoch_participation, that.current_epoch_participation)
        && Objects.equals(inactivity_scores, that.inactivity_scores)
        && Objects.equals(current_sync_committee, that.current_sync_committee)
        && Objects.equals(next_sync_committee, that.next_sync_committee);
  }

  @Override
  public int hashCode() {
    int result =
        Objects.hash(
            super.hashCode(), inactivity_scores, current_sync_committee, next_sync_committee);
    result = 31 * result + Arrays.hashCode(previous_epoch_participation);
    result = 31 * result + Arrays.hashCode(current_epoch_participation);
    return result;
  }

  private byte[] toByteArray(final SszList<SszByte> byteList) {
    final byte[] array = new byte[byteList.size()];
    for (int i = 0; i < array.length; i++) {
      array[i] = byteList.get(i).get();
    }
    return array;
  }
}
