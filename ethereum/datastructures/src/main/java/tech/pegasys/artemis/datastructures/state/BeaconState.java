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

package tech.pegasys.artemis.datastructures.state;

import static tech.pegasys.artemis.datastructures.Constants.ZERO_HASH;

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.ssz.SSZ;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;
import tech.pegasys.artemis.datastructures.blocks.Eth1DataVote;

public class BeaconState {
  // Misc
  protected UnsignedLong slot;
  protected UnsignedLong genesis_time;
  protected Fork fork; // For versioning hard forks

  // Validator registry
  protected List<Validator> validator_registry;
  protected List<UnsignedLong> validator_balances;
  protected UnsignedLong validator_registry_update_epoch;

  // Randomness and committees
  protected List<Bytes32> latest_randao_mixes;
  protected UnsignedLong previous_shuffling_start_shard;
  protected UnsignedLong current_shuffling_start_shard;
  protected UnsignedLong previous_shuffling_epoch;
  protected UnsignedLong current_shuffling_epoch;
  protected Bytes32 previous_shuffling_seed;
  protected Bytes32 current_shuffling_seed;

  // Finality
  protected UnsignedLong previous_justified_epoch;
  protected UnsignedLong justified_epoch;
  protected UnsignedLong justification_bitfield;
  protected UnsignedLong finalized_epoch;

  // Recent state
  protected List<Crosslink> latest_crosslinks;
  protected List<Bytes32> latest_block_roots;
  protected List<Bytes32> latest_active_index_roots;
  protected List<UnsignedLong>
      latest_slashed_balances; // Balances slashed at every withdrawal period
  protected List<PendingAttestation> latest_attestations;
  protected List<Bytes32> batched_block_roots;

  // Ethereum 1.0 chain data
  protected Eth1Data latest_eth1_data;
  protected List<Eth1DataVote> eth1_data_votes;

  protected UnsignedLong deposit_index;

  public BeaconState() {

    this.slot = UnsignedLong.valueOf(Constants.GENESIS_SLOT);
    this.genesis_time = UnsignedLong.ZERO;
    this.fork =
        new Fork(
            UnsignedLong.valueOf(Constants.GENESIS_FORK_VERSION),
            UnsignedLong.valueOf(Constants.GENESIS_FORK_VERSION),
            UnsignedLong.valueOf(Constants.GENESIS_EPOCH));

    this.validator_registry = new ArrayList<>();
    this.validator_balances = new ArrayList<>();
    this.validator_registry_update_epoch = UnsignedLong.valueOf(Constants.GENESIS_EPOCH);

    this.latest_randao_mixes =
        new ArrayList<>(
            Collections.nCopies(Constants.LATEST_RANDAO_MIXES_LENGTH, Constants.ZERO_HASH));
    this.previous_shuffling_start_shard = UnsignedLong.valueOf(Constants.GENESIS_START_SHARD);
    this.current_shuffling_start_shard = UnsignedLong.valueOf(Constants.GENESIS_START_SHARD);
    this.previous_shuffling_epoch = UnsignedLong.valueOf(Constants.GENESIS_EPOCH);
    this.current_shuffling_epoch = UnsignedLong.valueOf(Constants.GENESIS_EPOCH);
    this.previous_shuffling_seed = ZERO_HASH;
    this.current_shuffling_seed = ZERO_HASH;

    this.previous_justified_epoch = UnsignedLong.valueOf(Constants.GENESIS_EPOCH);
    this.justified_epoch = UnsignedLong.valueOf(Constants.GENESIS_EPOCH);
    this.justification_bitfield = UnsignedLong.ZERO;
    this.finalized_epoch = UnsignedLong.valueOf(Constants.GENESIS_EPOCH);

    this.latest_crosslinks = new ArrayList<>(Constants.SHARD_COUNT);
    this.latest_block_roots =
        new ArrayList<>(
            Collections.nCopies(Constants.LATEST_BLOCK_ROOTS_LENGTH, Constants.ZERO_HASH));
    this.latest_active_index_roots =
        new ArrayList<>(
            Collections.nCopies(Constants.LATEST_ACTIVE_INDEX_ROOTS_LENGTH, Constants.ZERO_HASH));
    this.latest_slashed_balances =
        new ArrayList<>(
            Collections.nCopies(Constants.LATEST_SLASHED_EXIT_LENGTH, UnsignedLong.ZERO));
    this.latest_attestations = new ArrayList<>();
    this.batched_block_roots = new ArrayList<>();

    this.latest_eth1_data = new Eth1Data(Bytes32.ZERO, Bytes32.ZERO);
    this.eth1_data_votes = new ArrayList<>();
    this.deposit_index = UnsignedLong.ZERO;
    for (int i = 0; i < Constants.SHARD_COUNT; i++) {
      this.latest_crosslinks.add(
          new Crosslink(UnsignedLong.valueOf(Constants.GENESIS_SLOT), Bytes32.ZERO));
    }
  }

  public BeaconState(
      // Misc
      UnsignedLong slot,
      UnsignedLong genesis_time,
      Fork fork, // For versioning hard forks

      // Validator registry
      List<Validator> validator_registry,
      List<UnsignedLong> validator_balances,
      UnsignedLong validator_registry_update_epoch,

      // Randomness and committees
      List<Bytes32> latest_randao_mixes,
      UnsignedLong previous_shuffling_start_shard,
      UnsignedLong current_shuffling_start_shard,
      UnsignedLong previous_shuffling_epoch,
      UnsignedLong current_shuffling_epoch,
      Bytes32 previous_shuffling_seed,
      Bytes32 current_shuffling_seed,

      // Finality
      UnsignedLong previous_justified_epoch,
      UnsignedLong justified_epoch,
      UnsignedLong justification_bitfield,
      UnsignedLong finalized_epoch,

      // Recent state
      List<Crosslink> latest_crosslinks,
      List<Bytes32> latest_block_roots,
      List<Bytes32> latest_active_index_roots,
      List<UnsignedLong> latest_slashed_balances, // Balances slashed at every withdrawal period
      List<PendingAttestation> latest_attestations,
      List<Bytes32> batched_block_roots,

      // Ethereum 1.0 chain data
      Eth1Data latest_eth1_data,
      List<Eth1DataVote> eth1_data_votes,
      UnsignedLong deposit_index) {
    this.slot = slot;
    this.genesis_time = genesis_time;
    this.fork = fork;

    this.validator_registry = validator_registry;
    this.validator_balances = validator_balances;
    this.validator_registry_update_epoch = validator_registry_update_epoch;

    this.latest_randao_mixes = latest_randao_mixes;
    this.previous_shuffling_start_shard = previous_shuffling_start_shard;
    this.current_shuffling_start_shard = current_shuffling_start_shard;
    this.previous_shuffling_epoch = previous_shuffling_epoch;
    this.current_shuffling_epoch = current_shuffling_epoch;

    this.previous_shuffling_seed = previous_shuffling_seed;
    this.current_shuffling_seed = current_shuffling_seed;
    this.previous_justified_epoch = previous_justified_epoch;
    this.justified_epoch = justified_epoch;
    this.justification_bitfield = justification_bitfield;
    this.finalized_epoch = finalized_epoch;

    this.latest_crosslinks = latest_crosslinks;
    this.latest_block_roots = latest_block_roots;
    this.latest_active_index_roots = latest_active_index_roots;
    this.latest_slashed_balances = latest_slashed_balances;
    this.latest_attestations = latest_attestations;
    this.batched_block_roots = batched_block_roots;

    this.latest_eth1_data = latest_eth1_data;
    this.eth1_data_votes = eth1_data_votes;

    this.deposit_index = deposit_index;
  }

  public static BeaconState fromBytes(Bytes bytes) {
    return SSZ.decode(
        bytes,
        reader ->
            new BeaconState(
                // Misc
                UnsignedLong.fromLongBits(reader.readUInt64()),
                UnsignedLong.fromLongBits(reader.readUInt64()),
                Fork.fromBytes(reader.readBytes()),
                // Validator registry
                reader.readBytesList().stream()
                    // .parallel()
                    .map(Validator::fromBytes)
                    .collect(Collectors.toList()),
                reader.readUInt64List().stream()
                    // .parallel()
                    .map(UnsignedLong::fromLongBits)
                    .collect(Collectors.toList()),
                UnsignedLong.fromLongBits(reader.readUInt64()),
                // Randomness and committees
                reader.readBytesList().stream()
                    // .parallel()
                    .map(Bytes32::wrap)
                    .collect(Collectors.toList()),
                UnsignedLong.fromLongBits(reader.readUInt64()),
                UnsignedLong.fromLongBits(reader.readUInt64()),
                UnsignedLong.fromLongBits(reader.readUInt64()),
                UnsignedLong.fromLongBits(reader.readUInt64()),
                Bytes32.wrap(reader.readBytes()),
                Bytes32.wrap(reader.readBytes()),
                // Finality
                UnsignedLong.fromLongBits(reader.readUInt64()),
                UnsignedLong.fromLongBits(reader.readUInt64()),
                UnsignedLong.fromLongBits(reader.readUInt64()),
                UnsignedLong.fromLongBits(reader.readUInt64()),
                // Recent state
                reader.readBytesList().stream()
                    .map(Crosslink::fromBytes)
                    .collect(Collectors.toList()),
                reader.readBytesList().stream().map(Bytes32::wrap).collect(Collectors.toList()),
                reader.readBytesList().stream().map(Bytes32::wrap).collect(Collectors.toList()),
                reader.readUInt64List().stream()
                    .map(UnsignedLong::fromLongBits)
                    .collect(Collectors.toList()),
                reader.readBytesList().stream()
                    .map(PendingAttestation::fromBytes)
                    .collect(Collectors.toList()),
                reader.readBytesList().stream().map(Bytes32::wrap).collect(Collectors.toList()),
                // Ethereum 1.0 chain data
                Eth1Data.fromBytes(reader.readBytes()),
                reader.readBytesList().stream()
                    .map(Eth1DataVote::fromBytes)
                    .collect(Collectors.toList()),
                UnsignedLong.fromLongBits(reader.readUInt64())));
  }

  public Bytes toBytes() {
    List<Bytes> validator_registryBytes =
        validator_registry.stream().map(item -> item.toBytes()).collect(Collectors.toList());
    List<Bytes> latest_crosslinksBytes =
        latest_crosslinks.stream().map(item -> item.toBytes()).collect(Collectors.toList());
    List<Bytes> latest_attestationBytes =
        latest_attestations.stream().map(item -> item.toBytes()).collect(Collectors.toList());
    List<Bytes> eth1_data_votesBytes =
        eth1_data_votes.stream().map(item -> item.toBytes()).collect(Collectors.toList());

    return SSZ.encode(
        writer -> {
          // Misc
          writer.writeUInt64(slot.longValue());
          writer.writeUInt64(genesis_time.longValue());
          writer.writeBytes(fork.toBytes());
          // Validator registry
          writer.writeBytesList(validator_registryBytes);
          writer.writeULongIntList(
              64,
              validator_balances.stream()
                  .map(UnsignedLong::longValue)
                  .collect(Collectors.toList()));
          writer.writeUInt64(validator_registry_update_epoch.longValue());
          // Randomness and committees
          writer.writeBytesList(latest_randao_mixes);
          writer.writeUInt64(previous_shuffling_start_shard.longValue());
          writer.writeUInt64(current_shuffling_start_shard.longValue());
          writer.writeUInt64(previous_shuffling_epoch.longValue());
          writer.writeUInt64(current_shuffling_epoch.longValue());
          writer.writeBytes(previous_shuffling_seed);
          writer.writeBytes(current_shuffling_seed);
          // Finality
          writer.writeUInt64(previous_justified_epoch.longValue());
          writer.writeUInt64(justified_epoch.longValue());
          writer.writeUInt64(justification_bitfield.longValue());
          writer.writeUInt64(finalized_epoch.longValue());
          // Recent state
          writer.writeBytesList(latest_crosslinksBytes);
          writer.writeBytesList(latest_block_roots);
          writer.writeBytesList(latest_active_index_roots);
          writer.writeULongIntList(
              64,
              latest_slashed_balances.stream()
                  .map(UnsignedLong::longValue)
                  .collect(Collectors.toList()));
          writer.writeBytesList(latest_attestationBytes);
          writer.writeBytesList(batched_block_roots);
          // Ethereum 1.0 chain data
          writer.writeBytes(latest_eth1_data.toBytes());
          writer.writeBytesList(eth1_data_votesBytes);
          writer.writeUInt64(deposit_index.longValue());
        });
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
        previous_shuffling_start_shard,
        current_shuffling_start_shard,
        previous_shuffling_epoch,
        current_shuffling_epoch,
        previous_shuffling_seed,
        current_shuffling_seed,
        previous_justified_epoch,
        justified_epoch,
        justification_bitfield,
        finalized_epoch,
        latest_crosslinks,
        latest_block_roots,
        latest_active_index_roots,
        latest_slashed_balances,
        latest_attestations,
        batched_block_roots,
        latest_eth1_data,
        eth1_data_votes,
        deposit_index);
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
            this.getPrevious_shuffling_start_shard(), other.getPrevious_shuffling_start_shard())
        && Objects.equals(
            this.getCurrent_shuffling_start_shard(), other.getCurrent_shuffling_start_shard())
        && Objects.equals(this.getPrevious_shuffling_epoch(), other.getPrevious_shuffling_epoch())
        && Objects.equals(this.getCurrent_shuffling_epoch(), other.getCurrent_shuffling_epoch())
        && Objects.equals(this.getPrevious_shuffling_seed(), other.getPrevious_shuffling_seed())
        && Objects.equals(this.getCurrent_shuffling_seed(), other.getCurrent_shuffling_seed())
        && Objects.equals(this.getPrevious_justified_epoch(), other.getPrevious_justified_epoch())
        && Objects.equals(this.getJustified_epoch(), other.getJustified_epoch())
        && Objects.equals(this.getJustification_bitfield(), other.getJustification_bitfield())
        && Objects.equals(this.getFinalized_epoch(), other.getFinalized_epoch())
        && Objects.equals(this.getLatest_crosslinks(), other.getLatest_crosslinks())
        && Objects.equals(this.getLatest_block_roots(), other.getLatest_block_roots())
        && Objects.equals(this.getLatest_active_index_roots(), other.getLatest_active_index_roots())
        && Objects.equals(this.getLatest_slashed_balances(), other.getLatest_slashed_balances())
        && Objects.equals(this.getLatest_attestations(), other.getLatest_attestations())
        && Objects.equals(this.getBatched_block_roots(), other.getBatched_block_roots())
        && Objects.equals(this.getLatest_eth1_data(), other.getLatest_eth1_data())
        && Objects.equals(this.getEth1_data_votes(), other.getEth1_data_votes())
        && Objects.equals(this.getDeposit_index(), other.getDeposit_index());
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

  public UnsignedLong getPrevious_shuffling_start_shard() {
    return previous_shuffling_start_shard;
  }

  public void setPrevious_shuffling_start_shard(UnsignedLong previous_shuffling_start_shard) {
    this.previous_shuffling_start_shard = previous_shuffling_start_shard;
  }

  public UnsignedLong getCurrent_shuffling_start_shard() {
    return current_shuffling_start_shard;
  }

  public void setCurrent_shuffling_start_shard(UnsignedLong current_shuffling_start_shard) {
    this.current_shuffling_start_shard = current_shuffling_start_shard;
  }

  public UnsignedLong getPrevious_shuffling_epoch() {
    return previous_shuffling_epoch;
  }

  public void setPrevious_shuffling_epoch(UnsignedLong previous_shuffling_epoch) {
    this.previous_shuffling_epoch = previous_shuffling_epoch;
  }

  public UnsignedLong getCurrent_shuffling_epoch() {
    return current_shuffling_epoch;
  }

  public void setCurrent_shuffling_epoch(UnsignedLong current_shuffling_epoch) {
    this.current_shuffling_epoch = current_shuffling_epoch;
  }

  public Bytes32 getPrevious_shuffling_seed() {
    return previous_shuffling_seed;
  }

  public void setPrevious_shuffling_seed(Bytes32 previous_shuffling_seed) {
    this.previous_shuffling_seed = previous_shuffling_seed;
  }

  public Bytes32 getCurrent_shuffling_seed() {
    return current_shuffling_seed;
  }

  public void setCurrent_shuffling_seed(Bytes32 current_shuffling_seed) {
    this.current_shuffling_seed = current_shuffling_seed;
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

  public List<Bytes32> getLatest_active_index_roots() {
    return latest_active_index_roots;
  }

  public void setLatest_active_index_roots(List<Bytes32> latest_active_index_roots) {
    this.latest_active_index_roots = latest_active_index_roots;
  }

  public List<UnsignedLong> getLatest_slashed_balances() {
    return latest_slashed_balances;
  }

  public void setLatest_slashed_balances(List<UnsignedLong> latest_slashed_balances) {
    this.latest_slashed_balances = latest_slashed_balances;
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

  public UnsignedLong getDeposit_index() {
    return deposit_index;
  }

  public void setDeposit_index(UnsignedLong deposit_index) {
    this.deposit_index = deposit_index;
  }

  public void incrementSlot() {
    this.slot = slot.plus(UnsignedLong.ONE);
  }
}
