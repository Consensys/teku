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
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.bytes.Bytes48;
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;
import tech.pegasys.artemis.datastructures.blocks.Eth1DataVote;
import tech.pegasys.artemis.datastructures.state.CrosslinkRecord;
import tech.pegasys.artemis.datastructures.state.Fork;
import tech.pegasys.artemis.datastructures.state.PendingAttestationRecord;
import tech.pegasys.artemis.datastructures.state.Validators;

public class BeaconState {
  // Misc
  private UnsignedLong slot;
  private UnsignedLong genesis_time;
  private Fork fork; // For versioning hard forks

  // Validator registry
  private Validators validator_registry;
  private ArrayList<UnsignedLong> validator_balances;
  private UnsignedLong validator_registry_update_epoch;

  // Randomness and committees
  private ArrayList<Bytes32> latest_randao_mixes;
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
  private ArrayList<CrosslinkRecord> latest_crosslinks;
  private ArrayList<Bytes32> latest_block_roots;
  private ArrayList<Bytes32> latest_index_roots;
  private ArrayList<UnsignedLong>
      latest_penalized_balances; // Balances penalized at every withdrawal period
  private ArrayList<PendingAttestationRecord> latest_attestations;
  private ArrayList<Bytes32> batched_block_roots;

  // Ethereum 1.0 chain data
  private Eth1Data latest_eth1_data;
  private ArrayList<Eth1DataVote> eth1_data_votes;

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
      Validators validator_registry,
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
      ArrayList<CrosslinkRecord> latest_crosslinks,
      ArrayList<Bytes32> latest_block_roots,
      ArrayList<Bytes32> latest_index_roots,
      ArrayList<UnsignedLong>
          latest_penalized_balances, // Balances penalized at every withdrawal period
      ArrayList<PendingAttestationRecord> latest_attestations,
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

  public Validators getValidator_registry() {
    return validator_registry;
  }

  public void setValidator_registry(Validators validator_registry) {
    this.validator_registry = validator_registry;
  }

  public ArrayList<UnsignedLong> getValidator_balances() {
    return validator_balances;
  }

  public void setValidator_balances(ArrayList<UnsignedLong> validator_balances) {
    this.validator_balances = validator_balances;
  }

  public UnsignedLong getValidator_registry_update_epoch() {
    return validator_registry_update_epoch;
  }

  public void setValidator_registry_update_epoch(UnsignedLong validator_registry_update_epoch) {
    this.validator_registry_update_epoch = validator_registry_update_epoch;
  }

  public ArrayList<Bytes32> getLatest_randao_mixes() {
    return latest_randao_mixes;
  }

  public void setLatest_randao_mixes(ArrayList<Bytes32> latest_randao_mixes) {
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

  public ArrayList<CrosslinkRecord> getLatest_crosslinks() {
    return latest_crosslinks;
  }

  public void setLatest_crosslinks(ArrayList<CrosslinkRecord> latest_crosslinks) {
    this.latest_crosslinks = latest_crosslinks;
  }

  public ArrayList<Bytes32> getLatest_block_roots() {
    return latest_block_roots;
  }

  public void setLatest_block_roots(ArrayList<Bytes32> latest_block_roots) {
    this.latest_block_roots = latest_block_roots;
  }

  public ArrayList<Bytes32> getLatest_index_roots() {
    return latest_index_roots;
  }

  public void setLatest_index_roots(ArrayList<Bytes32> latest_index_roots) {
    this.latest_index_roots = latest_index_roots;
  }

  public ArrayList<UnsignedLong> getLatest_penalized_balances() {
    return latest_penalized_balances;
  }

  public void setLatest_penalized_balances(ArrayList<UnsignedLong> latest_penalized_balances) {
    this.latest_penalized_balances = latest_penalized_balances;
  }

  public ArrayList<PendingAttestationRecord> getLatest_attestations() {
    return latest_attestations;
  }

  public void setLatest_attestations(ArrayList<PendingAttestationRecord> latest_attestations) {
    this.latest_attestations = latest_attestations;
  }

  public ArrayList<Bytes32> getBatched_block_roots() {
    return batched_block_roots;
  }

  public void setBatched_block_roots(ArrayList<Bytes32> batched_block_roots) {
    this.batched_block_roots = batched_block_roots;
  }

  public Eth1Data getLatest_eth1_data() {
    return latest_eth1_data;
  }

  public void setLatest_eth1_data(Eth1Data latest_eth1_data) {
    this.latest_eth1_data = latest_eth1_data;
  }

  public ArrayList<Eth1DataVote> getEth1_data_votes() {
    return eth1_data_votes;
  }

  public void setEth1_data_votes(ArrayList<Eth1DataVote> eth1_data_votes) {
    this.eth1_data_votes = eth1_data_votes;
  }

  public void incrementSlot() {
    this.slot = slot.plus(UnsignedLong.ONE);
  }
}
