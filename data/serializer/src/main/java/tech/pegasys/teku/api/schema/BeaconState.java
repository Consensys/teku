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

package tech.pegasys.teku.api.schema;

import static tech.pegasys.teku.api.schema.SchemaConstants.DESCRIPTION_BYTES32;
import static tech.pegasys.teku.api.schema.SchemaConstants.DESCRIPTION_BYTES_SSZ;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.schema.interfaces.State;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;

@SuppressWarnings("JavaCase")
public abstract class BeaconState implements State {
  @Schema(type = "string", format = "uint64")
  public final UInt64 genesis_time;

  @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES32)
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

  @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES_SSZ)
  public final SszBitvector justification_bits;

  public final Checkpoint previous_justified_checkpoint;
  public final Checkpoint current_justified_checkpoint;
  public final Checkpoint finalized_checkpoint;

  @JsonCreator
  protected BeaconState(
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
    this.justification_bits = justification_bits;
    this.previous_justified_checkpoint = previous_justified_checkpoint;
    this.current_justified_checkpoint = current_justified_checkpoint;
    this.finalized_checkpoint = finalized_checkpoint;
  }

  protected BeaconState(final BeaconBlockAndState blockAndState) {
    this(blockAndState.getState());
  }

  protected BeaconState(
      final tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState beaconState) {
    this.genesis_time = beaconState.getGenesisTime();
    this.genesis_validators_root = beaconState.getGenesisValidatorsRoot();
    this.slot = beaconState.getSlot();
    this.fork = new Fork(beaconState.getFork());
    this.latest_block_header = new BeaconBlockHeader(beaconState.getLatestBlockHeader());
    this.block_roots = beaconState.getBlockRoots().asListUnboxed();
    this.state_roots = beaconState.getStateRoots().asListUnboxed();
    this.historical_roots = beaconState.getHistoricalRoots().asListUnboxed();
    this.eth1_data = new Eth1Data(beaconState.getEth1Data());
    this.eth1_data_votes =
        beaconState.getEth1DataVotes().stream().map(Eth1Data::new).collect(Collectors.toList());
    this.eth1_deposit_index = beaconState.getEth1DepositIndex();
    this.validators =
        beaconState.getValidators().stream().map(Validator::new).collect(Collectors.toList());
    this.balances = beaconState.getBalances().asListUnboxed();
    this.randao_mixes = beaconState.getRandaoMixes().asListUnboxed();
    this.slashings = beaconState.getSlashings().asListUnboxed();
    this.justification_bits = beaconState.getJustificationBits();
    this.previous_justified_checkpoint =
        new Checkpoint(beaconState.getPreviousJustifiedCheckpoint());
    this.current_justified_checkpoint = new Checkpoint(beaconState.getCurrentJustifiedCheckpoint());
    this.finalized_checkpoint = new Checkpoint(beaconState.getFinalizedCheckpoint());
  }

  public tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState asInternalBeaconState(
      final Spec spec) {
    final BeaconStateSchema<?, ?> schema =
        spec.atSlot(slot).getSchemaDefinitions().getBeaconStateSchema();
    return schema
        .createEmpty()
        .updated(
            state -> {
              state.setGenesisTime(genesis_time);
              state.setGenesisValidatorsRoot(genesis_validators_root);
              state.setSlot(slot);
              state.setFork(fork.asInternalFork());
              state.setLatestBlockHeader(latest_block_header.asInternalBeaconBlockHeader());
              state.getBlockRoots().setAllElements(block_roots);
              state.getStateRoots().setAllElements(state_roots);
              state.getHistoricalRoots().setAllElements(historical_roots);
              state.setEth1Data(eth1_data.asInternalEth1Data());
              state
                  .getEth1DataVotes()
                  .setAll(
                      eth1_data_votes.stream()
                          .map(Eth1Data::asInternalEth1Data)
                          .collect(Collectors.toList()));
              state.setEth1DepositIndex(eth1_deposit_index);
              state
                  .getValidators()
                  .setAll(
                      validators.stream()
                          .map(Validator::asInternalValidator)
                          .collect(Collectors.toList()));
              state.getBalances().setAllElements(balances);
              state.getRandaoMixes().setAllElements(randao_mixes);
              state.getSlashings().setAllElements(slashings);
              SszBitvector newJustificationBits =
                  schema.getJustificationBitsSchema().ofBits(justification_bits.getAllSetBits());
              state.setJustificationBits(newJustificationBits);
              state.setPreviousJustifiedCheckpoint(
                  previous_justified_checkpoint.asInternalCheckpoint());
              state.setCurrentJustifiedCheckpoint(
                  current_justified_checkpoint.asInternalCheckpoint());
              state.setFinalizedCheckpoint(finalized_checkpoint.asInternalCheckpoint());

              applyAdditionalFields(state, spec.atSlot(state.getSlot()));
            });
  }

  protected abstract void applyAdditionalFields(
      final MutableBeaconState state, final SpecVersion specVersion);
}
