/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.artemis.statetransition;

import com.google.common.primitives.UnsignedLong;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.bytes.Bytes48;
import net.consensys.cava.ssz.SSZ;
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;
import tech.pegasys.artemis.datastructures.blocks.Eth1DataVote;
import tech.pegasys.artemis.datastructures.state.Crosslink;
import tech.pegasys.artemis.datastructures.state.Fork;
import tech.pegasys.artemis.datastructures.state.PendingAttestation;
import tech.pegasys.artemis.datastructures.state.Validator;

public class BeaconState {
  // Misc
  private UnsignedLong slot;
  private UnsignedLong genesis_time;
  private Fork fork; // For versioning hard forks

  // Validator registry
  private List<Validator> validator_registry;
  private List<UnsignedLong> validator_balances;
  private UnsignedLong validator_registry_update_epoch;

  // Randomness and committees
  private List<Bytes32> latest_randao_mixes;
  private UnsignedLong previous_epoch_start_shard;
  private UnsignedLong current_epoch_start_shard;
  private UnsignedLong previous_calculation_epoch;
  private UnsignedLong current_calculation_epoch;

  // Finality
  private Bytes32 previous_epoch_seed;
  private Bytes32 current_epoch_seed;
  private UnsignedLong previous_justified_epoch;
  private UnsignedLong justified_epoch;
  private UnsignedLong justification_bitfield;
  private UnsignedLong finalized_epoch;

  // Recent state
  private List<Crosslink> latest_crosslinks;
  private List<Bytes32> latest_block_roots;
  private List<Bytes32> latest_index_roots;
  private List<UnsignedLong>
      latest_penalized_balances; // Balances penalized at every withdrawal period
  private List<PendingAttestation> latest_attestations;
  private List<Bytes32> batched_block_roots;

  // Ethereum 1.0 chain data
  private Eth1Data latest_eth1_data;
  private List<Eth1DataVote> eth1_data_votes;

  public static BeaconState deepCopy(BeaconState state) {
    Gson gson =
        new GsonBuilder()
            .registerTypeAdapter(Bytes32.class, new InterfaceAdapter<Bytes32>())
            .registerTypeAdapter(Bytes48.class, new InterfaceAdapter<Bytes48>())
            .create();
    return gson.fromJson(gson.toJson(state), BeaconState.class);
  }

  public BeaconState() {}

  public BeaconState(
      // Misc
      UnsignedLong slot,
      UnsignedLong genesis_time,
      Fork fork, // For versioning hard forks

      // Validator registry
      ArrayList<Validator> validator_registry,
      ArrayList<UnsignedLong> validator_balances,
      UnsignedLong validator_registry_update_epoch,

      // Randomness and committees
      ArrayList<Bytes32> latest_randao_mixes,
      UnsignedLong previous_epoch_start_shard,
      UnsignedLong current_epoch_start_shard,
      UnsignedLong previous_calculation_epoch,
      UnsignedLong current_calculation_epoch,
      Bytes32 previous_epoch_seed,
      Bytes32 current_epoch_seed,

      // Finality
      UnsignedLong previous_justified_epoch,
      UnsignedLong justified_epoch,
      UnsignedLong justification_bitfield,
      UnsignedLong finalized_epoch,

      // Recent state
      ArrayList<Crosslink> latest_crosslinks,
      ArrayList<Bytes32> latest_block_roots,
      ArrayList<Bytes32> latest_index_roots,
      ArrayList<UnsignedLong>
          latest_penalized_balances, // Balances penalized at every withdrawal period
      ArrayList<PendingAttestation> latest_attestations,
      ArrayList<Bytes32> batched_block_roots,

      // Ethereum 1.0 chain data
      Eth1Data latest_eth1_data,
      ArrayList<Eth1DataVote> eth1_data_votes) {
    this.slot = slot;
    this.genesis_time = genesis_time;
    this.fork = fork;

    this.validator_registry = validator_registry;
    this.validator_balances = validator_balances;
    this.validator_registry_update_epoch = validator_registry_update_epoch;

    this.latest_randao_mixes = latest_randao_mixes;
    this.previous_epoch_start_shard = previous_epoch_start_shard;
    this.current_epoch_start_shard = current_epoch_start_shard;
    this.previous_calculation_epoch = previous_calculation_epoch;
    this.current_calculation_epoch = current_calculation_epoch;

    this.previous_epoch_seed = previous_epoch_seed;
    this.current_epoch_seed = current_epoch_seed;
    this.previous_justified_epoch = previous_justified_epoch;
    this.justified_epoch = justified_epoch;
    this.justification_bitfield = justification_bitfield;
    this.finalized_epoch = finalized_epoch;

    this.latest_crosslinks = latest_crosslinks;
    this.latest_block_roots = latest_block_roots;
    this.latest_index_roots = latest_index_roots;
    this.latest_penalized_balances = latest_penalized_balances;
    this.latest_attestations = latest_attestations;
    this.batched_block_roots = batched_block_roots;

    this.latest_eth1_data = latest_eth1_data;
    this.eth1_data_votes = eth1_data_votes;
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public UnsignedLong getSlot() {
    return slot;
  }

  public void setSlot(UnsignedLong slot) {
    this.slot = slot;
  }

  public UnsignedLong getGenesis_time() {
    return genesis_time;
  }

  public void setGenesis_time(UnsignedLong genesis_time) {
    this.genesis_time = genesis_time;
  }

  public Fork getFork() {
    return fork;
  }

  public void setFork(Fork fork) {
    this.fork = fork;
  }

  public List<Validator> getValidator_registry() {
    return validator_registry;
  }

  public void setValidator_registry(List<Validator> validator_registry) {
    this.validator_registry = validator_registry;
  }

  public List<UnsignedLong> getValidator_balances() {
    return validator_balances;
  }

  public void setValidator_balances(List<UnsignedLong> validator_balances) {
    this.validator_balances = validator_balances;
  }

  public UnsignedLong getValidator_registry_update_epoch() {
    return validator_registry_update_epoch;
  }

  public void setValidator_registry_update_epoch(UnsignedLong validator_registry_update_epoch) {
    this.validator_registry_update_epoch = validator_registry_update_epoch;
  }

  public List<Bytes32> getLatest_randao_mixes() {
    return latest_randao_mixes;
  }

  public void setLatest_randao_mixes(List<Bytes32> latest_randao_mixes) {
    this.latest_randao_mixes = latest_randao_mixes;
  }

  public UnsignedLong getPrevious_epoch_start_shard() {
    return previous_epoch_start_shard;
  }

  public void setPrevious_epoch_start_shard(UnsignedLong previous_epoch_start_shard) {
    this.previous_epoch_start_shard = previous_epoch_start_shard;
  }

  public UnsignedLong getCurrent_epoch_start_shard() {
    return current_epoch_start_shard;
  }

  public void setCurrent_epoch_start_shard(UnsignedLong current_epoch_start_shard) {
    this.current_epoch_start_shard = current_epoch_start_shard;
  }

  public UnsignedLong getPrevious_calculation_epoch() {
    return previous_calculation_epoch;
  }

  public void setPrevious_calculation_epoch(UnsignedLong previous_calculation_epoch) {
    this.previous_calculation_epoch = previous_calculation_epoch;
  }

  public UnsignedLong getCurrent_calculation_epoch() {
    return current_calculation_epoch;
  }

  public void setCurrent_calculation_epoch(UnsignedLong current_calculation_epoch) {
    this.current_calculation_epoch = current_calculation_epoch;
  }

  public Bytes32 getPrevious_epoch_seed() {
    return previous_epoch_seed;
  }

  public void setPrevious_epoch_seed(Bytes32 previous_epoch_seed) {
    this.previous_epoch_seed = previous_epoch_seed;
  }

  public Bytes32 getCurrent_epoch_seed() {
    return current_epoch_seed;
  }

  public void setCurrent_epoch_seed(Bytes32 current_epoch_seed) {
    this.current_epoch_seed = current_epoch_seed;
  }

  public UnsignedLong getPrevious_justified_epoch() {
    return previous_justified_epoch;
  }

  public void setPrevious_justified_epoch(UnsignedLong previous_justified_epoch) {
    this.previous_justified_epoch = previous_justified_epoch;
  }

  public UnsignedLong getJustified_epoch() {
    return justified_epoch;
  }

  public void setJustified_epoch(UnsignedLong justified_epoch) {
    this.justified_epoch = justified_epoch;
  }

  public UnsignedLong getJustification_bitfield() {
    return justification_bitfield;
  }

  public void setJustification_bitfield(UnsignedLong justification_bitfield) {
    this.justification_bitfield = justification_bitfield;
  }

  public UnsignedLong getFinalized_epoch() {
    return finalized_epoch;
  }

  public void setFinalized_epoch(UnsignedLong finalized_epoch) {
    this.finalized_epoch = finalized_epoch;
  }

  public List<Crosslink> getLatest_crosslinks() {
    return latest_crosslinks;
  }

  public void setLatest_crosslinks(ArrayList<Crosslink> latest_crosslinks) {
    this.latest_crosslinks = latest_crosslinks;
  }

  public List<Bytes32> getLatest_block_roots() {
    return latest_block_roots;
  }

  public void setLatest_block_roots(List<Bytes32> latest_block_roots) {
    this.latest_block_roots = latest_block_roots;
  }

  public List<Bytes32> getLatest_index_roots() {
    return latest_index_roots;
  }

  public void setLatest_index_roots(List<Bytes32> latest_index_roots) {
    this.latest_index_roots = latest_index_roots;
  }

  public List<UnsignedLong> getLatest_penalized_balances() {
    return latest_penalized_balances;
  }

  public void setLatest_penalized_balances(List<UnsignedLong> latest_penalized_balances) {
    this.latest_penalized_balances = latest_penalized_balances;
  }

  public List<PendingAttestation> getLatest_attestations() {
    return latest_attestations;
  }

  public void setLatest_attestations(List<PendingAttestation> latest_attestations) {
    this.latest_attestations = latest_attestations;
  }

  public List<Bytes32> getBatched_block_roots() {
    return batched_block_roots;
  }

  public void setBatched_block_roots(List<Bytes32> batched_block_roots) {
    this.batched_block_roots = batched_block_roots;
  }

  public Eth1Data getLatest_eth1_data() {
    return latest_eth1_data;
  }

  public void setLatest_eth1_data(Eth1Data latest_eth1_data) {
    this.latest_eth1_data = latest_eth1_data;
  }

  public List<Eth1DataVote> getEth1_data_votes() {
    return eth1_data_votes;
  }

  public void setEth1_data_votes(List<Eth1DataVote> eth1_data_votes) {
    this.eth1_data_votes = eth1_data_votes;
  }

  public void incrementSlot() {
    this.slot = slot.plus(UnsignedLong.ONE);
  }

  public static BeaconState fromBytes(Bytes bytes) {
    // todo
    return SSZ.decode(bytes, reader -> new BeaconState());
  }

  public Bytes toBytes() {
    // todo
    return Bytes32.ZERO;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        slot,
        genesis_time,
        fork,
        validator_registry,
        validator_balances,
        validator_registry_update_epoch,
        latest_randao_mixes,
        previous_epoch_start_shard,
        current_epoch_start_shard,
        previous_calculation_epoch,
        current_calculation_epoch,
        previous_epoch_seed,
        current_epoch_seed,
        previous_justified_epoch,
        justified_epoch,
        justification_bitfield,
        finalized_epoch,
        latest_crosslinks,
        latest_block_roots,
        latest_index_roots,
        latest_penalized_balances,
        latest_attestations,
        batched_block_roots,
        latest_eth1_data,
        eth1_data_votes);
  }

  @Override
  public boolean equals(Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }

    if (this == obj) {
      return true;
    }

    if (!(obj instanceof BeaconState)) {
      return false;
    }

    BeaconState other = (BeaconState) obj;
    return Objects.equals(slot, other.getSlot())
        && Objects.equals(this.getGenesis_time(), other.getGenesis_time())
        && Objects.equals(this.getFork(), other.getFork())
        && Objects.equals(this.getValidator_registry(), other.getValidator_registry())
        && Objects.equals(this.getValidator_balances(), other.getValidator_balances())
        && Objects.equals(
            this.getValidator_registry_update_epoch(), other.getValidator_registry_update_epoch())
        && Objects.equals(this.getLatest_randao_mixes(), other.getLatest_randao_mixes())
        && Objects.equals(
            this.getPrevious_epoch_start_shard(), other.getPrevious_epoch_start_shard())
        && Objects.equals(this.getCurrent_epoch_start_shard(), other.getCurrent_epoch_start_shard())
        && Objects.equals(
            this.getPrevious_calculation_epoch(), other.getPrevious_calculation_epoch())
        && Objects.equals(this.getCurrent_calculation_epoch(), other.getCurrent_calculation_epoch())
        && Objects.equals(this.getPrevious_epoch_seed(), other.getPrevious_epoch_seed())
        && Objects.equals(this.getCurrent_epoch_seed(), other.getCurrent_epoch_seed())
        && Objects.equals(this.getPrevious_justified_epoch(), other.getPrevious_justified_epoch())
        && Objects.equals(this.getJustified_epoch(), other.getJustified_epoch())
        && Objects.equals(this.getJustification_bitfield(), other.getJustification_bitfield())
        && Objects.equals(this.getFinalized_epoch(), other.getFinalized_epoch())
        && Objects.equals(this.getLatest_crosslinks(), other.getLatest_crosslinks())
        && Objects.equals(this.getLatest_block_roots(), other.getLatest_block_roots())
        && Objects.equals(this.getLatest_index_roots(), other.getLatest_index_roots())
        && Objects.equals(this.getLatest_penalized_balances(), other.getLatest_penalized_balances())
        && Objects.equals(this.getLatest_attestations(), other.getLatest_attestations())
        && Objects.equals(this.getBatched_block_roots(), other.getBatched_block_roots())
        && Objects.equals(this.getLatest_eth1_data(), other.getLatest_eth1_data())
        && Objects.equals(this.getEth1_data_votes(), other.getEth1_data_votes());
  }
}
