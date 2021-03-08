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
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.genesis.BeaconStateGenesis;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;
import tech.pegasys.teku.ssz.SSZTypes.SSZVector;
import tech.pegasys.teku.ssz.backing.collections.SszBitvector;
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
  public final SszBitvector justification_bits;

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
      @JsonProperty("justification_bits") final SszBitvector justification_bits,
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

  public BeaconState(
      final tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState beaconState) {
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
    this.justification_bits = beaconState.getJustification_bits();
    this.previous_justified_checkpoint =
        new Checkpoint(beaconState.getPrevious_justified_checkpoint());
    this.current_justified_checkpoint =
        new Checkpoint(beaconState.getCurrent_justified_checkpoint());
    this.finalized_checkpoint = new Checkpoint(beaconState.getFinalized_checkpoint());

    // Optionally set genesis-specific versioned fields
    final Optional<BeaconStateGenesis> maybeGenesisState = beaconState.toGenesisVersion();
    if (maybeGenesisState.isPresent()) {
      final BeaconStateGenesis genesisState = maybeGenesisState.get();
      this.previous_epoch_attestations =
          genesisState.getPrevious_epoch_attestations().stream()
              .map(PendingAttestation::new)
              .collect(Collectors.toList());
      this.current_epoch_attestations =
          genesisState.getCurrent_epoch_attestations().stream()
              .map(PendingAttestation::new)
              .collect(Collectors.toList());
    } else {
      this.previous_epoch_attestations = null;
      this.current_epoch_attestations = null;
    }
  }

  public tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState asInternalBeaconState(
      final Spec spec) {
    return spec.atSlot(slot)
        .getSchemaDefinitions()
        .getBeaconStateSchema()
        .createEmpty()
        .updated(
            state -> {
              state.setGenesis_time(genesis_time);
              state.setGenesis_validators_root(genesis_validators_root);
              state.setSlot(slot);
              state.setFork(fork.asInternalFork());
              state.setLatest_block_header(latest_block_header.asInternalBeaconBlockHeader());
              state.getBlock_roots().setAll(SSZVector.createMutable(block_roots, Bytes32.class));
              state.getState_roots().setAll(SSZVector.createMutable(state_roots, Bytes32.class));
              state
                  .getHistorical_roots()
                  .setAll(
                      SSZList.createMutable(
                          historical_roots, HISTORICAL_ROOTS_LIMIT, Bytes32.class));
              state.setEth1_data(eth1_data.asInternalEth1Data());
              state
                  .getEth1_data_votes()
                  .setAll(
                      SSZList.createMutable(
                          eth1_data_votes.stream()
                              .map(Eth1Data::asInternalEth1Data)
                              .collect(Collectors.toList()),
                          EPOCHS_PER_ETH1_VOTING_PERIOD,
                          tech.pegasys.teku.spec.datastructures.blocks.Eth1Data.class));
              state.setEth1_deposit_index(eth1_deposit_index);
              state
                  .getValidators()
                  .setAll(
                      SSZList.createMutable(
                          validators.stream()
                              .map(Validator::asInternalValidator)
                              .collect(Collectors.toList()),
                          Constants.VALIDATOR_REGISTRY_LIMIT,
                          tech.pegasys.teku.spec.datastructures.state.Validator.class));
              state
                  .getBalances()
                  .setAll(SSZList.createMutable(balances, VALIDATOR_REGISTRY_LIMIT, UInt64.class));
              state.getRandao_mixes().setAll(SSZVector.createMutable(randao_mixes, Bytes32.class));
              state.getSlashings().setAll(SSZVector.createMutable(slashings, UInt64.class));
              state.setJustification_bits(justification_bits);
              state.setPrevious_justified_checkpoint(
                  previous_justified_checkpoint.asInternalCheckpoint());
              state.setCurrent_justified_checkpoint(
                  current_justified_checkpoint.asInternalCheckpoint());
              state.setFinalized_checkpoint(finalized_checkpoint.asInternalCheckpoint());

              state
                  .toGenesisVersionMutable()
                  .ifPresent(
                      genesisState -> {
                        genesisState
                            .getPrevious_epoch_attestations()
                            .setAll(
                                SSZList.createMutable(
                                    previous_epoch_attestations.stream()
                                        .map(PendingAttestation::asInternalPendingAttestation)
                                        .collect(Collectors.toList()),
                                    MAX_ATTESTATIONS,
                                    tech.pegasys.teku.spec.datastructures.state.PendingAttestation
                                        .class));
                        genesisState
                            .getCurrent_epoch_attestations()
                            .setAll(
                                SSZList.createMutable(
                                    current_epoch_attestations.stream()
                                        .map(PendingAttestation::asInternalPendingAttestation)
                                        .collect(Collectors.toList()),
                                    MAX_ATTESTATIONS,
                                    tech.pegasys.teku.spec.datastructures.state.PendingAttestation
                                        .class));
                      });
            });
  }
}
