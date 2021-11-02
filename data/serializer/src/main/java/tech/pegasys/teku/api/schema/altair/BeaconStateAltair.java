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
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateSchemaAltair;
import tech.pegasys.teku.ssz.SszList;
import tech.pegasys.teku.ssz.collections.SszBitvector;
import tech.pegasys.teku.ssz.primitive.SszByte;

public class BeaconStateAltair extends BeaconState implements State {
  @ArraySchema(
      schema =
          @Schema(name = "previous_epoch_participation", type = "string", example = EXAMPLE_UINT8))
  public final byte[] previousEpochParticipation;

  @ArraySchema(
      schema =
          @Schema(name = "current_epoch_participation", type = "string", example = EXAMPLE_UINT8))
  public final byte[] currentEpochParticipation;

  @ArraySchema(
      schema = @Schema(name = "inactivity_scores", type = "string", example = EXAMPLE_UINT64))
  public final List<UInt64> inactivityScores;

  @Schema(name = "current_sync_committee")
  public final SyncCommittee currentSyncCommittee;

  @Schema(name = "next_sync_committee")
  public final SyncCommittee nextSyncCommittee;

  @JsonCreator
  public BeaconStateAltair(
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
      @JsonProperty("next_sync_committee") final SyncCommittee nextSyncCommittee) {
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
        justificationBits,
        previousJustifiedCheckpoint,
        currentJustifiedCheckpoint,
        finalizedCheckpoint);
    this.previousEpochParticipation = previousEpochParticipation;
    this.currentEpochParticipation = currentEpochParticipation;
    this.inactivityScores = inactivityScores;
    this.currentSyncCommittee = currentSyncCommittee;
    this.nextSyncCommittee = nextSyncCommittee;
  }

  public BeaconStateAltair(
      final tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState beaconState) {
    super(beaconState);
    final tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateAltair
        altair = beaconState.toVersionAltair().orElseThrow();
    this.previousEpochParticipation = toByteArray(altair.getPreviousEpochParticipation());
    this.currentEpochParticipation = toByteArray(altair.getCurrentEpochParticipation());
    this.inactivityScores = altair.getInactivityScores().asListUnboxed();
    this.currentSyncCommittee = new SyncCommittee(altair.getCurrentSyncCommittee());
    this.nextSyncCommittee = new SyncCommittee(altair.getNextSyncCommittee());
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
                      .sszDeserialize(Bytes.wrap(this.previousEpochParticipation));
              final SszList<SszByte> currentEpochParticipation =
                  beaconStateAltair
                      .getCurrentEpochParticipation()
                      .getSchema()
                      .sszDeserialize(Bytes.wrap(this.currentEpochParticipation));

              beaconStateAltair.setPreviousEpochParticipation(previousEpochParticipation);
              beaconStateAltair.setCurrentEpochParticipation(currentEpochParticipation);
              beaconStateAltair.getInactivityScores().setAllElements(inactivityScores);

              beaconStateAltair.setCurrentSyncCommittee(
                  currentSyncCommittee.asInternalSyncCommittee(syncCommitteeSchema));
              beaconStateAltair.setNextSyncCommittee(
                  nextSyncCommittee.asInternalSyncCommittee(syncCommitteeSchema));
            });
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    final BeaconStateAltair that = (BeaconStateAltair) o;
    return Arrays.equals(previousEpochParticipation, that.previousEpochParticipation)
        && Arrays.equals(currentEpochParticipation, that.currentEpochParticipation)
        && Objects.equals(inactivityScores, that.inactivityScores)
        && Objects.equals(currentSyncCommittee, that.currentSyncCommittee)
        && Objects.equals(nextSyncCommittee, that.nextSyncCommittee);
  }

  @Override
  public int hashCode() {
    int result =
        Objects.hash(super.hashCode(), inactivityScores, currentSyncCommittee, nextSyncCommittee);
    result = 31 * result + Arrays.hashCode(previousEpochParticipation);
    result = 31 * result + Arrays.hashCode(currentEpochParticipation);
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
