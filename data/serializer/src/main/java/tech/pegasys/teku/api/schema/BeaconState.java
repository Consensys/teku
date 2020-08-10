/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.api.schema;

import static tech.pegasys.teku.api.schema.SchemaConstants.DESCRIPTION_BYTES32;
import static tech.pegasys.teku.api.schema.SchemaConstants.DESCRIPTION_BYTES_SSZ;
import static tech.pegasys.teku.util.config.Constants.EPOCHS_PER_ETH1_VOTING_PERIOD;
import static tech.pegasys.teku.util.config.Constants.HISTORICAL_ROOTS_LIMIT;
import static tech.pegasys.teku.util.config.Constants.MAX_ATTESTATIONS;
import static tech.pegasys.teku.util.config.Constants.VALIDATOR_REGISTRY_LIMIT;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.Bitvector;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;
import tech.pegasys.teku.ssz.SSZTypes.SSZVector;
import tech.pegasys.teku.util.config.Constants;

public class BeaconState {
  @Schema(type = "string", format = "uint64")
  public final UInt64 genesis_time;

  public final Bytes32 genesis_validators_root;

  @Schema(type = "string", format = "uint64")
  public final UInt64 slot;

  public final Fork fork;
  public final BeaconBlockHeader latest_block_header;

  @ArraySchema(
      schema = @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES32))
  public final List<Bytes32> block_roots;

  @ArraySchema(
      schema = @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES32))
  public final List<Bytes32> state_roots;

  @ArraySchema(
      schema = @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES32))
  public final List<Bytes32> historical_roots;

  public final Eth1Data eth1_data;
  public final List<Eth1Data> eth1_data_votes;

  @Schema(type = "string", format = "uint64")
  public final UInt64 eth1_deposit_index;

  public final List<Validator> validators;

  @ArraySchema(schema = @Schema(type = "string", format = "uint64"))
  public final List<UInt64> balances;

  @ArraySchema(
      schema = @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES32))
  public final List<Bytes32> randao_mixes;

  @ArraySchema(schema = @Schema(type = "string", format = "uint64"))
  public final List<UInt64> slashings;

  public final List<PendingAttestation> previous_epoch_attestations;
  public final List<PendingAttestation> current_epoch_attestations;

  @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES_SSZ)
  public final Bitvector justification_bits;

  public final Checkpoint previous_justified_checkpoint;
  public final Checkpoint current_justified_checkpoint;
  public final Checkpoint finalized_checkpoint;

  @JsonCreator
  public BeaconState(
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
      @JsonProperty("previous_epoch_attestations")
          final List<PendingAttestation> previous_epoch_attestations,
      @JsonProperty("current_epoch_attestations")
          final List<PendingAttestation> current_epoch_attestations,
      @JsonProperty("justification_bits") final Bitvector justification_bits,
      @JsonProperty("previous_justified_checkpoint") final Checkpoint previous_justified_checkpoint,
      @JsonProperty("current_justified_checkpoint") final Checkpoint current_justified_checkpoint,
      @JsonProperty("finalized_checkpoint") final Checkpoint finalized_checkpoint) {
    this.genesis_time = genesis_time;
    this.genesis_validators_root = genesis_validators_root;
    this.slot = slot;
    this.fork = fork;
    this.latest_block_header = latest_block_header;
    this.block_roots = block_roots;
    this.state_roots = state_roots;
    this.historical_roots = historical_roots;
    this.eth1_data = eth1_data;
    this.eth1_data_votes = eth1_data_votes;
    this.eth1_deposit_index = eth1_deposit_index;
    this.validators = validators;
    this.balances = balances;
    this.randao_mixes = randao_mixes;
    this.slashings = slashings;
    this.previous_epoch_attestations = previous_epoch_attestations;
    this.current_epoch_attestations = current_epoch_attestations;
    this.justification_bits = justification_bits;
    this.previous_justified_checkpoint = previous_justified_checkpoint;
    this.current_justified_checkpoint = current_justified_checkpoint;
    this.finalized_checkpoint = finalized_checkpoint;
  }

  public BeaconState(final BeaconBlockAndState blockAndState) {
    this(blockAndState.getState());
  }

  public BeaconState(final tech.pegasys.teku.datastructures.state.BeaconState beaconState) {
    this.genesis_time = beaconState.getGenesis_time();
    this.genesis_validators_root = beaconState.getGenesis_validators_root();
    this.slot = beaconState.getSlot();
    this.fork = new Fork(beaconState.getFork());
    this.latest_block_header = new BeaconBlockHeader(beaconState.getLatest_block_header());
    this.block_roots = beaconState.getBlock_roots().stream().collect(Collectors.toList());
    this.state_roots = beaconState.getState_roots().stream().collect(Collectors.toList());
    this.historical_roots = beaconState.getHistorical_roots().stream().collect(Collectors.toList());
    this.eth1_data = new Eth1Data(beaconState.getEth1_data());
    this.eth1_data_votes =
        beaconState.getEth1_data_votes().stream().map(Eth1Data::new).collect(Collectors.toList());
    this.eth1_deposit_index = beaconState.getEth1_deposit_index();
    this.validators =
        beaconState.getValidators().stream().map(Validator::new).collect(Collectors.toList());
    this.balances = beaconState.getBalances().stream().collect(Collectors.toList());
    this.randao_mixes = beaconState.getRandao_mixes().stream().collect(Collectors.toList());
    this.slashings = beaconState.getSlashings().stream().collect(Collectors.toList());
    this.previous_epoch_attestations =
        beaconState.getPrevious_epoch_attestations().stream()
            .map(PendingAttestation::new)
            .collect(Collectors.toList());
    this.current_epoch_attestations =
        beaconState.getCurrent_epoch_attestations().stream()
            .map(PendingAttestation::new)
            .collect(Collectors.toList());
    this.justification_bits = beaconState.getJustification_bits();
    this.previous_justified_checkpoint =
        new Checkpoint(beaconState.getPrevious_justified_checkpoint());
    this.current_justified_checkpoint =
        new Checkpoint(beaconState.getCurrent_justified_checkpoint());
    this.finalized_checkpoint = new Checkpoint(beaconState.getFinalized_checkpoint());
  }

  public tech.pegasys.teku.datastructures.state.BeaconState asInternalBeaconState() {
    return tech.pegasys.teku.datastructures.state.BeaconState.create(
        genesis_time,
        genesis_validators_root,
        slot,
        fork.asInternalFork(),
        latest_block_header.asInternalBeaconBlockHeader(),
        SSZVector.createMutable(block_roots, Bytes32.class),
        SSZVector.createMutable(state_roots, Bytes32.class),
        SSZList.createMutable(historical_roots, HISTORICAL_ROOTS_LIMIT, Bytes32.class),
        eth1_data.asInternalEth1Data(),
        SSZList.createMutable(
            eth1_data_votes.stream().map(Eth1Data::asInternalEth1Data).collect(Collectors.toList()),
            EPOCHS_PER_ETH1_VOTING_PERIOD,
            tech.pegasys.teku.datastructures.blocks.Eth1Data.class),
        eth1_deposit_index,
        SSZList.createMutable(
            validators.stream().map(Validator::asInternalValidator).collect(Collectors.toList()),
            Constants.VALIDATOR_REGISTRY_LIMIT,
            tech.pegasys.teku.datastructures.state.Validator.class),
        SSZList.createMutable(balances, VALIDATOR_REGISTRY_LIMIT, UInt64.class),
        SSZVector.createMutable(randao_mixes, Bytes32.class),
        SSZVector.createMutable(slashings, UInt64.class),
        SSZList.createMutable(
            previous_epoch_attestations.stream()
                .map(PendingAttestation::asInternalPendingAttestation)
                .collect(Collectors.toList()),
            MAX_ATTESTATIONS,
            tech.pegasys.teku.datastructures.state.PendingAttestation.class),
        SSZList.createMutable(
            current_epoch_attestations.stream()
                .map(PendingAttestation::asInternalPendingAttestation)
                .collect(Collectors.toList()),
            MAX_ATTESTATIONS,
            tech.pegasys.teku.datastructures.state.PendingAttestation.class),
        justification_bits,
        previous_justified_checkpoint.asInternalCheckpoint(),
        current_justified_checkpoint.asInternalCheckpoint(),
        finalized_checkpoint.asInternalCheckpoint());
  }
}
