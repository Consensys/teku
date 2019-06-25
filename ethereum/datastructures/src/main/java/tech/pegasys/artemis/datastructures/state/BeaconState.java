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
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.int_to_bytes;

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockBody;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil.SSZTypes;

public class BeaconState {
  // Misc
  protected UnsignedLong slot;
  protected UnsignedLong genesis_time;
  protected Fork fork; // For versioning hard forks

  // Validator registry
  protected List<Validator> validator_registry;
  protected List<UnsignedLong> balances;

  // Randomness and committees
  protected List<Bytes32> latest_randao_mixes; // Bounded by LATEST_RANDAO_MIXES_LENGTH
  protected UnsignedLong latest_start_shard;

  // Finality
  protected List<PendingAttestation> previous_epoch_attestations;
  protected List<PendingAttestation> current_epoch_attestations;
  protected UnsignedLong previous_justified_epoch;
  protected UnsignedLong current_justified_epoch;
  protected Bytes32 previous_justified_root;
  protected Bytes32 current_justified_root;
  protected UnsignedLong justification_bitfield;
  protected UnsignedLong finalized_epoch;
  protected Bytes32 finalized_root;

  // Recent state
  protected List<Crosslink> current_crosslinks; // Bounded by SHARD_COUNT
  protected List<Crosslink> previous_crosslinks; // Bounded by SHARD_COUNT
  protected List<Bytes32> latest_block_roots; // Bounded by SLOTS_PER_HISTORICAL_ROOT
  protected List<Bytes32> latest_state_roots; // Bounded by SLOTS_PER_HISTORICAL_ROOT
  protected List<Bytes32> latest_active_index_roots; // Bounded by LATEST_ACTIVE_INDEX_ROOTS_LENGTH
  // TODO This is bounded by LATEST_SLASHED_EXIT_LENGTH
  protected List<UnsignedLong>
      latest_slashed_balances; // Balances slashed at every withdrawal period
  protected BeaconBlockHeader
      latest_block_header; // `latest_block_header.state_root == ZERO_HASH` temporarily
  protected List<Bytes32> historical_roots;

  // Ethereum 1.0 chain data
  protected Eth1Data latest_eth1_data;
  protected List<Eth1Data> eth1_data_votes;
  protected UnsignedLong deposit_index;

  public BeaconState() {

    this.slot = UnsignedLong.valueOf(Constants.GENESIS_SLOT);
    this.genesis_time = UnsignedLong.ZERO;
    this.fork =
        new Fork(
            int_to_bytes(Constants.GENESIS_FORK_VERSION, 4),
            int_to_bytes(Constants.GENESIS_FORK_VERSION, 4),
            UnsignedLong.valueOf(Constants.GENESIS_EPOCH));

    this.validator_registry = new ArrayList<>();
    this.balances = new ArrayList<>();

    this.latest_randao_mixes =
        new ArrayList<>(
            Collections.nCopies(Constants.LATEST_RANDAO_MIXES_LENGTH, Constants.ZERO_HASH));
    this.latest_start_shard = UnsignedLong.ZERO; // TODO: not sure about this

    this.previous_epoch_attestations = new ArrayList<>();
    this.current_epoch_attestations = new ArrayList<>();
    this.previous_justified_epoch = UnsignedLong.valueOf(Constants.GENESIS_EPOCH);
    this.current_justified_epoch = UnsignedLong.valueOf(Constants.GENESIS_EPOCH);
    this.previous_justified_root = Constants.ZERO_HASH;
    this.current_justified_root = Constants.ZERO_HASH;
    this.justification_bitfield = UnsignedLong.ZERO;
    this.finalized_epoch = UnsignedLong.valueOf(Constants.GENESIS_EPOCH);
    this.finalized_root = Constants.ZERO_HASH;

    this.current_crosslinks =
        new ArrayList<>(Collections.nCopies(Constants.SHARD_COUNT, new Crosslink()));
    this.previous_crosslinks =
        new ArrayList<>(Collections.nCopies(Constants.SHARD_COUNT, new Crosslink()));
    this.latest_block_roots =
        new ArrayList<>(
            Collections.nCopies(Constants.SLOTS_PER_HISTORICAL_ROOT, Constants.ZERO_HASH));
    this.latest_state_roots =
        new ArrayList<>(
            Collections.nCopies(Constants.SLOTS_PER_HISTORICAL_ROOT, Constants.ZERO_HASH));
    this.latest_active_index_roots =
        new ArrayList<>(
            Collections.nCopies(Constants.LATEST_ACTIVE_INDEX_ROOTS_LENGTH, Constants.ZERO_HASH));
    this.latest_slashed_balances =
        new ArrayList<>(
            Collections.nCopies(Constants.LATEST_SLASHED_EXIT_LENGTH, UnsignedLong.ZERO));
    Bytes32 body_root = new BeaconBlockBody().hash_tree_root();
    this.latest_block_header = new BeaconBlockHeader(body_root);
    this.historical_roots = new ArrayList<>();

    // TODO gotta change this with genesis eth1DATA because deposit count is dependent on the
    // number of validators
    this.latest_eth1_data = new Eth1Data(ZERO_HASH, UnsignedLong.ZERO, ZERO_HASH);
    this.eth1_data_votes = new ArrayList<>();
    this.deposit_index = UnsignedLong.ZERO;
  }

  public BeaconState(
      // Misc
      UnsignedLong slot,
      UnsignedLong genesis_time,
      Fork fork, // For versioning hard forks

      // Validator registry
      List<Validator> validator_registry,
      List<UnsignedLong> balances,

      // Randomness and committees
      List<Bytes32> latest_randao_mixes,
      UnsignedLong latest_start_shard,

      // Finality
      List<PendingAttestation> previous_epoch_attestations,
      List<PendingAttestation> current_epoch_attestations,
      UnsignedLong previous_justified_epoch,
      UnsignedLong current_justified_epoch,
      Bytes32 previous_justified_root,
      Bytes32 current_justified_root,
      UnsignedLong justification_bitfield,
      UnsignedLong finalized_epoch,
      Bytes32 finalized_root,

      // Recent state
      List<Crosslink> current_crosslinks,
      List<Crosslink> previous_crosslinks,
      List<Bytes32> latest_block_roots,
      List<Bytes32> latest_state_roots,
      List<Bytes32> latest_active_index_roots,
      List<UnsignedLong> latest_slashed_balances, // Balances slashed at every withdrawal period
      BeaconBlockHeader latest_block_header,
      List<Bytes32> historical_roots,

      // Ethereum 1.0 chain data
      Eth1Data latest_eth1_data,
      List<Eth1Data> eth1_data_votes,
      UnsignedLong deposit_index) {
    this.slot = slot;
    this.fork = fork;
    this.genesis_time = genesis_time;

    this.validator_registry = validator_registry;
    this.balances = balances;

    this.latest_randao_mixes = latest_randao_mixes;
    this.latest_start_shard = latest_start_shard;

    this.previous_epoch_attestations = previous_epoch_attestations;
    this.current_epoch_attestations = current_epoch_attestations;
    this.previous_justified_epoch = previous_justified_epoch;
    this.current_justified_epoch = current_justified_epoch;
    this.previous_justified_root = previous_justified_root;
    this.current_justified_root = current_justified_root;
    this.justification_bitfield = justification_bitfield;
    this.finalized_epoch = finalized_epoch;
    this.finalized_root = finalized_root;

    this.current_crosslinks = current_crosslinks;
    this.previous_crosslinks = previous_crosslinks;
    this.latest_block_roots = latest_block_roots;
    this.latest_state_roots = latest_state_roots;
    this.latest_active_index_roots = latest_active_index_roots;
    this.latest_slashed_balances = latest_slashed_balances;
    this.latest_block_header = latest_block_header;
    this.historical_roots = historical_roots;

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
                    .map(Validator::fromBytes)
                    .collect(Collectors.toList()),
                reader.readUInt64List().stream()
                    .map(UnsignedLong::fromLongBits)
                    .collect(Collectors.toList()),
                // Randomness and committees
                reader.readFixedBytesVector(Constants.LATEST_RANDAO_MIXES_LENGTH, 32).stream()
                    .map(Bytes32::wrap)
                    .collect(Collectors.toList()),
                UnsignedLong.fromLongBits(reader.readUInt64()),
                // Finality
                reader.readBytesList().stream()
                    .map(PendingAttestation::fromBytes)
                    .collect(Collectors.toList()),
                reader.readBytesList().stream()
                    .map(PendingAttestation::fromBytes)
                    .collect(Collectors.toList()),
                UnsignedLong.fromLongBits(reader.readUInt64()),
                UnsignedLong.fromLongBits(reader.readUInt64()),
                Bytes32.wrap(reader.readFixedBytes(32)),
                Bytes32.wrap(reader.readFixedBytes(32)),
                UnsignedLong.fromLongBits(reader.readUInt64()),
                UnsignedLong.fromLongBits(reader.readUInt64()),
                Bytes32.wrap(reader.readFixedBytes(32)),
                // Recent state
                // TODO This should be a vector bounded by SHARD_COUNT, pending an issue fix in
                // Tuweni.
                reader.readBytesList().stream()
                    .map(Crosslink::fromBytes)
                    .collect(Collectors.toList()),
                // TODO This should be a vector bounded by SHARD_COUNT, pending an issue fix in
                // Tuweni.
                reader.readBytesList().stream()
                    .map(Crosslink::fromBytes)
                    .collect(Collectors.toList()),
                reader.readFixedBytesVector(Constants.SLOTS_PER_HISTORICAL_ROOT, 32).stream()
                    .map(Bytes32::wrap)
                    .collect(Collectors.toList()),
                reader.readFixedBytesVector(Constants.SLOTS_PER_HISTORICAL_ROOT, 32).stream()
                    .map(Bytes32::wrap)
                    .collect(Collectors.toList()),
                reader.readFixedBytesVector(Constants.LATEST_ACTIVE_INDEX_ROOTS_LENGTH, 32).stream()
                    .map(Bytes32::wrap)
                    .collect(Collectors.toList()),
                reader.readUInt64List().stream()
                    .map(UnsignedLong::fromLongBits)
                    .collect(Collectors.toList()), // TODO This must be a fixed sized list
                BeaconBlockHeader.fromBytes(reader.readBytes()),
                reader.readFixedBytesList(32).stream()
                    .map(Bytes32::wrap)
                    .collect(Collectors.toList()),
                // Ethereum 1.0 chain data
                Eth1Data.fromBytes(reader.readBytes()),
                reader.readBytesList().stream()
                    .map(Eth1Data::fromBytes)
                    .collect(Collectors.toList()),
                UnsignedLong.fromLongBits(reader.readUInt64())));
  }

  public Bytes toBytes() {
    List<Bytes> validator_registryBytes =
        validator_registry.stream().map(item -> item.toBytes()).collect(Collectors.toList());
    List<Bytes> current_crosslinksBytes =
        current_crosslinks.stream().map(item -> item.toBytes()).collect(Collectors.toList());
    List<Bytes> previous_crosslinksBytes =
        previous_crosslinks.stream().map(item -> item.toBytes()).collect(Collectors.toList());
    List<Bytes> eth1_data_votesBytes =
        eth1_data_votes.stream().map(item -> item.toBytes()).collect(Collectors.toList());
    List<Bytes> previous_epoch_attestationsBytes =
        previous_epoch_attestations.stream()
            .map(item -> item.toBytes())
            .collect(Collectors.toList());
    List<Bytes> current_epoch_attestationsBytes =
        current_epoch_attestations.stream()
            .map(item -> item.toBytes())
            .collect(Collectors.toList());

    return SSZ.encode(
        writer -> {
          // Misc
          writer.writeUInt64(slot.longValue());
          writer.writeUInt64(genesis_time.longValue());
          writer.writeBytes(fork.toBytes());
          // Validator registry
          writer.writeBytesList(validator_registryBytes);
          writer.writeULongIntList(
              64, balances.stream().map(UnsignedLong::longValue).collect(Collectors.toList()));
          // Randomness and committees
          writer.writeFixedBytesVector(latest_randao_mixes);
          writer.writeUInt64(latest_start_shard.longValue());
          // Finality
          writer.writeBytesList(previous_epoch_attestationsBytes);
          writer.writeBytesList(current_epoch_attestationsBytes);
          writer.writeUInt64(previous_justified_epoch.longValue());
          writer.writeUInt64(current_justified_epoch.longValue());
          writer.writeFixedBytes(32, previous_justified_root);
          writer.writeFixedBytes(32, current_justified_root);
          writer.writeUInt64(justification_bitfield.longValue());
          writer.writeUInt64(finalized_epoch.longValue());
          writer.writeFixedBytes(32, finalized_root);
          // Recent state
          // TODO This should be a vector bounded by SHARD_COUNT, pending an issue fix in Tuweni.
          writer.writeBytesList(current_crosslinksBytes);
          // TODO This should be a vector bounded by SHARD_COUNT, pending an issue fix in Tuweni.
          writer.writeBytesList(previous_crosslinksBytes);
          writer.writeFixedBytesVector(latest_block_roots);
          writer.writeFixedBytesVector(latest_state_roots);
          writer.writeFixedBytesVector(latest_active_index_roots);
          writer.writeULongIntList(
              64,
              latest_slashed_balances.stream()
                  .map(UnsignedLong::longValue)
                  .collect(Collectors.toList()));
          writer.writeBytes(latest_block_header.toBytes());
          writer.writeFixedBytesList(32, historical_roots);
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
        balances,
        latest_randao_mixes,
        latest_start_shard,
        previous_epoch_attestations,
        current_epoch_attestations,
        previous_justified_epoch,
        current_justified_epoch,
        previous_justified_root,
        current_justified_root,
        justification_bitfield,
        finalized_epoch,
        finalized_root,
        current_crosslinks,
        previous_crosslinks,
        latest_block_roots,
        latest_state_roots,
        latest_active_index_roots,
        latest_slashed_balances,
        latest_block_header,
        historical_roots,
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
        && Objects.equals(this.getBalances(), other.getBalances())
        && Objects.equals(this.getLatest_randao_mixes(), other.getLatest_randao_mixes())
        && Objects.equals(this.getLatest_start_shard(), other.getLatest_start_shard())
        && Objects.equals(
            this.getPrevious_epoch_attestations(), other.getPrevious_epoch_attestations())
        && Objects.equals(
            this.getCurrent_epoch_attestations(), other.getCurrent_epoch_attestations())
        && Objects.equals(this.getPrevious_justified_epoch(), other.getPrevious_justified_epoch())
        && Objects.equals(this.getCurrent_justified_epoch(), other.getCurrent_justified_epoch())
        && Objects.equals(this.getPrevious_justified_root(), other.getPrevious_justified_root())
        && Objects.equals(this.getCurrent_justified_root(), other.getCurrent_justified_root())
        && Objects.equals(this.getJustification_bitfield(), other.getJustification_bitfield())
        && Objects.equals(this.getFinalized_epoch(), other.getFinalized_epoch())
        && Objects.equals(this.getFinalized_root(), other.getFinalized_root())
        && Objects.equals(this.getCurrent_crosslinks(), other.getCurrent_crosslinks())
        && Objects.equals(this.getPrevious_crosslinks(), other.getPrevious_crosslinks())
        && Objects.equals(this.getLatest_block_roots(), other.getLatest_block_roots())
        && Objects.equals(this.getLatest_state_roots(), other.getLatest_state_roots())
        && Objects.equals(this.getLatest_active_index_roots(), other.getLatest_active_index_roots())
        && Objects.equals(this.getLatest_slashed_balances(), other.getLatest_slashed_balances())
        && Objects.equals(this.getLatest_block_header(), other.getLatest_block_header())
        && Objects.equals(this.getHistorical_roots(), other.getHistorical_roots())
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

  public List<UnsignedLong> getBalances() {
    return balances;
  }

  public void setBalances(List<UnsignedLong> balances) {
    this.balances = balances;
  }

  public List<Bytes32> getLatest_randao_mixes() {
    return latest_randao_mixes;
  }

  public void setLatest_randao_mixes(List<Bytes32> latest_randao_mixes) {
    this.latest_randao_mixes = latest_randao_mixes;
  }

  public UnsignedLong getLatest_start_shard() {
    return latest_start_shard;
  }

  public void setLatest_start_shard(UnsignedLong latest_start_shard) {
    this.latest_start_shard = latest_start_shard;
  }

  public List<PendingAttestation> getPrevious_epoch_attestations() {
    return previous_epoch_attestations;
  }

  public void setPrevious_epoch_attestations(List<PendingAttestation> previous_epoch_attestations) {
    this.previous_epoch_attestations = previous_epoch_attestations;
  }

  public List<PendingAttestation> getCurrent_epoch_attestations() {
    return current_epoch_attestations;
  }

  public void setCurrent_epoch_attestations(List<PendingAttestation> current_epoch_attestations) {
    this.current_epoch_attestations = current_epoch_attestations;
  }

  public UnsignedLong getPrevious_justified_epoch() {
    return previous_justified_epoch;
  }

  public void setPrevious_justified_epoch(UnsignedLong previous_justified_epoch) {
    this.previous_justified_epoch = previous_justified_epoch;
  }

  public UnsignedLong getCurrent_justified_epoch() {
    return current_justified_epoch;
  }

  public void setCurrent_justified_epoch(UnsignedLong current_justified_epoch) {
    this.current_justified_epoch = current_justified_epoch;
  }

  public Bytes32 getPrevious_justified_root() {
    return previous_justified_root;
  }

  public void setPrevious_justified_root(Bytes32 previous_justified_root) {
    this.previous_justified_root = previous_justified_root;
  }

  public Bytes32 getCurrent_justified_root() {
    return current_justified_root;
  }

  public void setCurrent_justified_root(Bytes32 current_justified_root) {
    this.current_justified_root = current_justified_root;
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

  public Bytes32 getFinalized_root() {
    return finalized_root;
  }

  public void setFinalized_root(Bytes32 finalized_root) {
    this.finalized_root = finalized_root;
  }

  public List<Crosslink> getCurrent_crosslinks() {
    return current_crosslinks;
  }

  public void setCurrent_crosslinks(ArrayList<Crosslink> current_crosslinks) {
    this.current_crosslinks = current_crosslinks;
  }

  public List<Crosslink> getPrevious_crosslinks() {
    return previous_crosslinks;
  }

  public void setPrevious_crosslinks(List<Crosslink> previous_crosslinks) {
    this.previous_crosslinks = previous_crosslinks;
  }

  public List<Bytes32> getLatest_block_roots() {
    return latest_block_roots;
  }

  public void setLatest_block_roots(List<Bytes32> latest_block_roots) {
    this.latest_block_roots = latest_block_roots;
  }

  public List<Bytes32> getLatest_state_roots() {
    return latest_state_roots;
  }

  public void setLatest_state_roots(List<Bytes32> latest_state_roots) {
    this.latest_state_roots = latest_state_roots;
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

  public BeaconBlockHeader getLatest_block_header() {
    return latest_block_header;
  }

  public void setLatest_block_header(BeaconBlockHeader latest_block_header) {
    this.latest_block_header = latest_block_header;
  }

  public List<Bytes32> getHistorical_roots() {
    return historical_roots;
  }

  public void setHistorical_roots(List<Bytes32> historical_roots) {
    this.historical_roots = historical_roots;
  }

  public Eth1Data getLatest_eth1_data() {
    return latest_eth1_data;
  }

  public void setLatest_eth1_data(Eth1Data latest_eth1_data) {
    this.latest_eth1_data = latest_eth1_data;
  }

  public List<Eth1Data> getEth1_data_votes() {
    return eth1_data_votes;
  }

  public void setEth1_data_votes(List<Eth1Data> eth1_data_votes) {
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

  public Bytes32 hash_tree_root() {
    return HashTreeUtil.merkleize(
        Arrays.asList(
            // Misc
            HashTreeUtil.hash_tree_root(SSZTypes.BASIC, SSZ.encodeUInt64(slot.longValue())),
            HashTreeUtil.hash_tree_root(SSZTypes.BASIC, SSZ.encodeUInt64(genesis_time.longValue())),
            fork.hash_tree_root(),
            // Validator registry
            HashTreeUtil.hash_tree_root(SSZTypes.LIST_OF_COMPOSITE, validator_registry),
            HashTreeUtil.hash_tree_root(
                SSZTypes.LIST_OF_BASIC,
                balances.stream()
                    .map(item -> SSZ.encodeUInt64(item.longValue()))
                    .collect(Collectors.toList())),
            // Randomness and committees
            HashTreeUtil.hash_tree_root(SSZTypes.TUPLE_OF_COMPOSITE, latest_randao_mixes),
            HashTreeUtil.hash_tree_root(
                SSZTypes.BASIC, SSZ.encodeUInt64(latest_start_shard.longValue())),
            // Finality
            HashTreeUtil.hash_tree_root(SSZTypes.LIST_OF_COMPOSITE, previous_epoch_attestations),
            HashTreeUtil.hash_tree_root(SSZTypes.LIST_OF_COMPOSITE, current_epoch_attestations),
            HashTreeUtil.hash_tree_root(
                SSZTypes.BASIC, SSZ.encodeUInt64(previous_justified_epoch.longValue())),
            HashTreeUtil.hash_tree_root(
                SSZTypes.BASIC, SSZ.encodeUInt64(current_justified_epoch.longValue())),
            HashTreeUtil.hash_tree_root(SSZTypes.TUPLE_OF_BASIC, previous_justified_root),
            HashTreeUtil.hash_tree_root(SSZTypes.TUPLE_OF_BASIC, current_justified_root),
            HashTreeUtil.hash_tree_root(
                SSZTypes.BASIC, SSZ.encodeUInt64(justification_bitfield.longValue())),
            HashTreeUtil.hash_tree_root(
                SSZTypes.BASIC, SSZ.encodeUInt64(finalized_epoch.longValue())),
            HashTreeUtil.hash_tree_root(SSZTypes.TUPLE_OF_BASIC, finalized_root),
            // Recent state
            HashTreeUtil.merkleize(
                current_crosslinks.stream()
                    .map(item -> item.hash_tree_root())
                    .collect(Collectors.toList())),
            HashTreeUtil.merkleize(
                previous_crosslinks.stream()
                    .map(item -> item.hash_tree_root())
                    .collect(Collectors.toList())),
            HashTreeUtil.hash_tree_root(SSZTypes.TUPLE_OF_COMPOSITE, latest_block_roots),
            HashTreeUtil.hash_tree_root(SSZTypes.TUPLE_OF_COMPOSITE, latest_state_roots),
            HashTreeUtil.hash_tree_root(SSZTypes.TUPLE_OF_COMPOSITE, latest_active_index_roots),
            HashTreeUtil.hash_tree_root(
                SSZTypes.TUPLE_OF_BASIC,
                latest_slashed_balances.stream()
                    .map(item -> SSZ.encodeUInt64(item.longValue()))
                    .collect(Collectors.toList())
                    .toArray(new Bytes[0])),
            latest_block_header.hash_tree_root(),
            HashTreeUtil.hash_tree_root(SSZTypes.LIST_OF_COMPOSITE, historical_roots),
            // Ethereum 1.0 chain data
            latest_eth1_data.hash_tree_root(),
            HashTreeUtil.hash_tree_root(SSZTypes.LIST_OF_COMPOSITE, eth1_data_votes),
            HashTreeUtil.hash_tree_root(
                SSZTypes.BASIC, SSZ.encodeUInt64(deposit_index.longValue()))));
  }
}
