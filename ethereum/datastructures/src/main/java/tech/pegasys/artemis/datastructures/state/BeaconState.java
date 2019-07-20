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

import static tech.pegasys.artemis.datastructures.Constants.JUSTIFICATION_BITS_LENGTH;
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
import org.apache.tuweni.bytes.Bytes48;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockBody;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil.SSZTypes;
import tech.pegasys.artemis.util.sos.SimpleOffsetSerializable;

public class BeaconState implements SimpleOffsetSerializable {

  // The number of SimpleSerialize basic types in this SSZ Container/POJO.
  public static final int SSZ_FIELD_COUNT = 11;


  // Versioning
  protected UnsignedLong genesis_time;
  protected UnsignedLong slot;
  protected Fork fork; // For versioning hard forks

  // History
  protected BeaconBlockHeader latest_block_header;
  protected List<Bytes32> block_roots; // Vector Bounded by SLOTS_PER_HISTORICAL_ROOT
  protected List<Bytes32> state_roots; // Vector Bounded by SLOTS_PER_HISTORICAL_ROOT
  protected List<Bytes32> historical_roots; // Bounded by HISTORICAL_ROOTS_LIMIT

  // Ethereum 1.0 chain data
  protected Eth1Data eth1_data;
  protected List<Eth1Data> eth1_data_votes; // List Bounded by SLOTS_PER_ETH1_VOTING_PERIOD
  protected UnsignedLong eth1_deposit_index;

  // Validator registry
  protected List<Validator> validators; // List Bounded by VALIDATOR_REGISTRY_LIMIT
  protected List<UnsignedLong> balances; // List Bounded by VALIDATOR_REGISTRY_LIMIT

  // Shuffling
  protected UnsignedLong start_shard;
  protected List<Bytes32> randao_mixes; // Vector Bounded by EPOCHS_PER_HISTORICAL_VECTOR
  protected List<Bytes32> active_index_roots; // Vector Bounded by EPOCHS_PER_HISTORICAL_VECTOR
  protected List<Bytes32> compact_committees_roots; // Vector Bounded by EPOCHS_PER_HISTORICAL_VECTOR

  // Slashings
  protected List<UnsignedLong> slashings; // Vector Bounded by EPOCHS_PER_SLASHINGS_VECTOR

  // Attestations
  protected List<PendingAttestation> previous_epoch_attestations; // List bounded by MAX_ATTESTATIONS * SLOTS_PER_EPOCH
  protected List<PendingAttestation> current_epoch_attestations; // List bounded by MAX_ATTESTATIONS * SLOTS_PER_EPOCH

  // Crosslinks
  protected List<Crosslink> previous_crosslinks; // Vector Bounded by SHARD_COUNT
  protected List<Crosslink> current_crosslinks; // Vector Bounded by SHARD_COUNT

  // Finality
  protected Bytes justification_bits; // Bitvector bounded by JUSTIFICATION_BITS_LENGTH
  protected Checkpoint previous_justified_checkpoint;
  protected Checkpoint current_justified_checkpoint;
  protected Checkpoint finalized_checkpoint;


  public BeaconState() {

    // Versioning
    this.genesis_time = UnsignedLong.ZERO;
    this.slot = UnsignedLong.valueOf(Constants.GENESIS_SLOT);
    this.fork =
        new Fork(
            int_to_bytes(0, 4),
            int_to_bytes(0, 4),
            UnsignedLong.valueOf(Constants.GENESIS_EPOCH));

    // History
    this.latest_block_header = new BeaconBlockHeader();
    this.block_roots =
        new ArrayList<>(
            Collections.nCopies(Constants.SLOTS_PER_HISTORICAL_ROOT, Constants.ZERO_HASH));
    this.state_roots =
        new ArrayList<>(
            Collections.nCopies(Constants.SLOTS_PER_HISTORICAL_ROOT, Constants.ZERO_HASH));
    this.historical_roots = new ArrayList<>(
            Collections.nCopies(Constants.HISTORICAL_ROOTS_LIMIT, Constants.ZERO_HASH));

    // Eth1
    // TODO gotta change this with genesis eth1DATA because deposit count is dependent on the
    // number of validators
    this.eth1_data = new Eth1Data(ZERO_HASH, UnsignedLong.ZERO, ZERO_HASH);
    this.eth1_data_votes = new ArrayList<>();
    this.eth1_deposit_index = UnsignedLong.ZERO;

    // Registry
    this.validators = new ArrayList<>();
    this.balances = new ArrayList<>();

    // Shuffling
    this.start_shard = UnsignedLong.ZERO;
    this.randao_mixes =
        new ArrayList<>(
            Collections.nCopies(Constants.EPOCHS_PER_HISTORICAL_VECTOR, Constants.ZERO_HASH));
    this.active_index_roots =
            new ArrayList<>(
                    Collections.nCopies(Constants.EPOCHS_PER_HISTORICAL_VECTOR, Constants.ZERO_HASH));
    this.compact_committees_roots =
            new ArrayList<>(
                    Collections.nCopies(Constants.EPOCHS_PER_HISTORICAL_VECTOR, Constants.ZERO_HASH));

    // Slashings
    this.slashings =
            new ArrayList<>(
                    Collections.nCopies(Constants.EPOCHS_PER_SLASHINGS_VECTOR, UnsignedLong.ZERO));

    // Attestations
    this.previous_epoch_attestations = new ArrayList<>();
    this.current_epoch_attestations = new ArrayList<>();

    // Crosslinks
    this.previous_crosslinks =
            new ArrayList<>(
                    Collections.nCopies(Constants.SHARD_COUNT, new Crosslink()));
    this.current_crosslinks =
            new ArrayList<>(
                    Collections.nCopies(Constants.SHARD_COUNT, new Crosslink()));

    // Finality
    this.justification_bits = Bytes.wrap(new byte[1]); // TODO change to bitvector with 4 bits
    this.previous_justified_checkpoint = new Checkpoint();
    this.current_justified_checkpoint = new Checkpoint();
    this.finalized_checkpoint = new Checkpoint();
  }

  public BeaconState(
      // Versioning
      UnsignedLong genesis_time,
      UnsignedLong slot,
      Fork fork,

      // History
      BeaconBlockHeader latest_block_header,
      List<Bytes32> block_roots,
      List<Bytes32> state_roots,
      List<Bytes32> historical_roots,

      // Eth1
      Eth1Data eth1_data,
      List<Eth1Data> eth1_data_votes,
      UnsignedLong eth1_deposit_index,

      // Registry
      List<Validator> validators,
      List<UnsignedLong> balances,

      // Shuffling
      UnsignedLong start_shard,
      List<Bytes32> randao_mixes,
      List<Bytes32> active_index_roots,
      List<Bytes32> compact_committees_roots,

      // Slashings
      List<UnsignedLong> slashings,

      // Attestations
      List<PendingAttestation> previous_epoch_attestations,
      List<PendingAttestation> current_epoch_attestations,

      // Crosslinks
      List<Crosslink> previous_crosslinks,
      List<Crosslink> current_crosslinks,

      // Finality
      Bytes justification_bits,
      Checkpoint previous_justified_checkpoint,
      Checkpoint current_justified_chekpoint,
      Checkpoint finalized_checkpoint) {
    // Versioning
    this.genesis_time = genesis_time;
    this.slot = slot;
    this.fork = fork;

    // History
    this.latest_block_header = latest_block_header;
    this.block_roots = block_roots;
    this.state_roots = state_roots;
    this.historical_roots = historical_roots;

    // Eth1
    this.eth1_data = eth1_data;
    this.eth1_data_votes = eth1_data_votes;
    this.eth1_deposit_index = eth1_deposit_index;

    // Registry
    this.validators = validators;
    this.balances = balances;

    // Shuffling
    this.start_shard = start_shard;
    this.randao_mixes = randao_mixes;
    this.active_index_roots = active_index_roots;
    this.compact_committees_roots = compact_committees_roots;

    // Slashings
    this.slashings = slashings;

    // Attestations
    this.previous_epoch_attestations = previous_epoch_attestations;
    this.current_epoch_attestations = current_epoch_attestations;

    // Crosslinks
    this.previous_crosslinks = previous_crosslinks;
    this.current_crosslinks = current_crosslinks;

    // Finality
    this.justification_bits = justification_bits;
    this.previous_justified_checkpoint = previous_justified_checkpoint;
    this.current_justified_checkpoint = current_justified_chekpoint;
    this.finalized_checkpoint = finalized_checkpoint;
  }

  @Override
  public int getSSZFieldCount() {
    // TODO Finish this stub.
    return SSZ_FIELD_COUNT;
  }

  @Override
  public List<Bytes> get_fixed_parts() {
    // TODO Implement this stub.
    return Collections.nCopies(getSSZFieldCount(), Bytes.EMPTY);
  }

  @Override
  public List<Bytes> get_variable_parts() {
    // TODO Implement this stub.
    return Collections.nCopies(getSSZFieldCount(), Bytes.EMPTY);
  }

  /*
  public static BeaconState fromBytes(Bytes bytes) {

    return SSZ.decode(
        bytes,
        reader ->
            new BeaconState(
                // Versioning
                UnsignedLong.fromLongBits(reader.readUInt64()),
                UnsignedLong.fromLongBits(reader.readUInt64()),
                Fork.fromBytes(reader.readBytes()),

                // History
                BeaconBlockHeader.fromBytes(reader.readBytes()),
                reader.readFixedBytesVector(Constants.SLOTS_PER_HISTORICAL_ROOT, 32).stream()
                    .map(Bytes32::wrap)
                    .collect(Collectors.toList()),
                reader.readFixedBytesVector(Constants.SLOTS_PER_HISTORICAL_ROOT, 32).stream()
                    .map(Bytes32::wrap)
                    .collect(Collectors.toList()),
                reader.readFixedBytesList(32).stream()
                    .map(Bytes32::wrap)
                    .collect(Collectors.toList()),

                // Eth1
                Eth1Data.fromBytes(reader.readBytes()),
                reader.readBytesList().stream()
                    .map(Eth1Data::fromBytes)
                    .collect(Collectors.toList()),
                UnsignedLong.fromLongBits(reader.readUInt64()),

                // Registry
                reader.readBytesList().stream()
                            .map(Validator::fromBytes)
                            .collect(Collectors.toList()),
                reader.readUInt64List().stream()
                    .map(UnsignedLong::fromLongBits)
                    .collect(Collectors.toList()),

                // Shuffling
                UnsignedLong.fromLongBits(reader.readUInt64()),
                reader.readFixedBytesVector(Constants.EPOCHS_PER_HISTORICAL_VECTOR, 32).stream()
                            .map(Bytes32::wrap)
                            .collect(Collectors.toList()),

                    reader.readFixedBytesVector(Constants.EPOCHS_PER_HISTORICAL_VECTOR, 32).stream()
                            .map(Bytes32::wrap)
                            .collect(Collectors.toList()),

                    reader.readFixedBytesVector(Constants.EPOCHS_PER_HISTORICAL_VECTOR, 32).stream()
                            .map(Bytes32::wrap)
                            .collect(Collectors.toList()),

                // Slashings
                // TODO this is actually a vector of unsigned long (altough we currently treat
                // it as a list of unsignedlong cause there is no way to deal with the former, yet.
                    reader.readUInt64List().stream()
                            .map(UnsignedLong::fromLongBits)
                            .collect(Collectors.toList()),
                    reader.readFixedBytesVector(Constants.SHARD_COUNT, 32).stream()
                            .map(Bytes32::wrap)
                            .collect(Collectors.toList()),

                // Attestations
            // TODO skipped rest of deserialization due to SOS

  }
  */

  public Bytes toBytes() {
    List<Bytes> validator_registryBytes =
        validators.stream().map(item -> item.toBytes()).collect(Collectors.toList());
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
          // TODO skipped serialization due to SOS
          /*
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
          writer.writeUInt64(previous_justified_checkpoint.longValue());
          writer.writeUInt64(current_justified_checkpoint.longValue());
          writer.writeFixedBytes(previous_justified_root);
          writer.writeFixedBytes(current_justified_root);
          writer.writeUInt64(justification_bitfield.longValue());
          writer.writeUInt64(finalized_epoch.longValue());
          writer.writeFixedBytes(finalized_checkpoint);
          // Recent state
          writer.writeVector(current_crosslinksBytes);
          writer.writeVector(previous_crosslinksBytes);
          writer.writeFixedBytesVector(block_roots);
          writer.writeFixedBytesVector(state_roots);
          writer.writeFixedBytesVector(latest_active_index_roots);
          writer.writeULongIntList(
              64,
              latest_slashed_balances.stream()
                  .map(UnsignedLong::longValue)
                  .collect(Collectors.toList()));
          writer.writeBytes(latest_block_header.toBytes());
          writer.writeFixedBytesList(historical_roots);
          // Ethereum 1.0 chain data
          writer.writeBytes(eth1_data.toBytes());
          writer.writeBytesList(eth1_data_votesBytes);
          writer.writeUInt64(eth1_deposit_index.longValue());
          */
        });
  }

  @Override
  public int hashCode() {
    return Objects.hash(
            // Versioning
            genesis_time,
        slot,
        fork,

            // History
            latest_block_header,
            block_roots,
            state_roots,
            historical_roots,

            // Eth1
            eth1_data,
            eth1_data_votes,
            eth1_deposit_index,

            // Registry
            validators,
        balances,

        // Shuffling
            start_shard,
        randao_mixes,
        active_index_roots,
        compact_committees_roots,

        // Slashings
            slashings,

        // Attestations
        previous_epoch_attestations,
        current_epoch_attestations,

        // Crosslinks
        current_crosslinks,
        previous_crosslinks,

        // Finality
            justification_bits,
            previous_justified_checkpoint,
            current_justified_checkpoint,
            finalized_checkpoint);
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
    return Objects.equals(this.getGenesis_time(), other.getGenesis_time())
        && Objects.equals(slot, other.getSlot())
        && Objects.equals(this.getFork(), other.getFork())
        && Objects.equals(this.getLatest_block_header(), other.getLatest_block_header())
            && Objects.equals(this.getBlock_roots(), other.getBlock_roots())
            && Objects.equals(this.getState_roots(), other.getState_roots())
            && Objects.equals(this.getHistorical_roots(), other.getHistorical_roots())
            && Objects.equals(this.getEth1_data(), other.getEth1_data())
            && Objects.equals(this.getEth1_data_votes(), other.getEth1_data_votes())
            && Objects.equals(this.getEth1_deposit_index(), other.getEth1_deposit_index())

        && Objects.equals(this.getValidators(), other.getValidators())
        && Objects.equals(this.getBalances(), other.getBalances())
            && Objects.equals(this.getStart_shard(), other.getStart_shard())
        && Objects.equals(this.getRandao_mixes(), other.getRandao_mixes())
            && Objects.equals(this.getActive_index_roots(), other.getActive_index_roots())
            && Objects.equals(this.getCompact_committees_roots(), other.getCompact_committees_roots())

            && Objects.equals(this.getSlashings(), other.getSlashings())

        && Objects.equals(
            this.getPrevious_epoch_attestations(), other.getPrevious_epoch_attestations())
        && Objects.equals(
            this.getCurrent_epoch_attestations(), other.getCurrent_epoch_attestations())

        && Objects.equals(this.getPrevious_crosslinks(), other.getPrevious_crosslinks())
            && Objects.equals(this.getCurrent_crosslinks(), other.getCurrent_crosslinks())

            && Objects.equals(this.getJustification_bits(), other.getJustification_bits())
            && Objects.equals(this.getPrevious_justified_checkpoint(), other.getPrevious_justified_checkpoint())
        && Objects.equals(this.getCurrent_justified_checkpoint(), other.getCurrent_justified_checkpoint())
            && Objects.equals(this.getFinalized_checkpoint(), other.getFinalized_checkpoint());
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */

  // Versioning
  public UnsignedLong getGenesis_time() {
    return genesis_time;
  }

  public void setGenesis_time(UnsignedLong genesis_time) {
    this.genesis_time = genesis_time;
  }

  public UnsignedLong getSlot() {
    return slot;
  }

  public void setSlot(UnsignedLong slot) {
    this.slot = slot;
  }

  public Fork getFork() {
    return fork;
  }

  public void setFork(Fork fork) {
    this.fork = fork;
  }

  // History
  public BeaconBlockHeader getLatest_block_header() {
    return latest_block_header;
  }

  public void setLatest_block_header(BeaconBlockHeader latest_block_header) {
    this.latest_block_header = latest_block_header;
  }

  public List<Bytes32> getBlock_roots() {
    return block_roots;
  }

  public void setBlock_roots(List<Bytes32> block_roots) {
    this.block_roots = block_roots;
  }

  public List<Bytes32> getState_roots() {
    return state_roots;
  }

  public void setState_roots(List<Bytes32> state_roots) {
    this.state_roots = state_roots;
  }

  public List<Bytes32> getHistorical_roots() {
    return historical_roots;
  }

  public void setHistorical_roots(List<Bytes32> historical_roots) {
    this.historical_roots = historical_roots;
  }

  // Eth1
  public Eth1Data getEth1_data() {
    return eth1_data;
  }

  public void setEth1_data(Eth1Data eth1_data) {
    this.eth1_data = eth1_data;
  }

  public List<Eth1Data> getEth1_data_votes() {
    return eth1_data_votes;
  }

  public void setEth1_data_votes(List<Eth1Data> eth1_data_votes) {
    this.eth1_data_votes = eth1_data_votes;
  }

  public UnsignedLong getEth1_deposit_index() {
    return eth1_deposit_index;
  }

  public void setEth1_deposit_index(UnsignedLong eth1_deposit_index) {
    this.eth1_deposit_index = eth1_deposit_index;
  }

  // Registry
  public List<Validator> getValidators() {
    return validators;
  }

  public void setValidators(List<Validator> validators) {
    this.validators = validators;
  }

  public List<UnsignedLong> getBalances() {
    return balances;
  }

  public void setBalances(List<UnsignedLong> balances) {
    this.balances = balances;
  }

  // Shuffling
  public UnsignedLong getStart_shard() {
    return start_shard;
  }

  public void setStart_shard(UnsignedLong start_shard) {
    this.start_shard = start_shard;
  }

  public List<Bytes32> getRandao_mixes() {
    return randao_mixes;
  }

  public void setRandao_mixes(List<Bytes32> randao_mixes) {
    this.randao_mixes = randao_mixes;
  }

  public List<Bytes32> getActive_index_roots() {
    return active_index_roots;
  }

  public void setActive_index_roots(List<Bytes32> active_index_roots) {
    this.active_index_roots = active_index_roots;
  }

  public List<Bytes32> getCompact_committees_roots() {
    return compact_committees_roots;
  }

  public void setCompact_committees_roots(List<Bytes32> compact_committees_roots) {
    this.compact_committees_roots = compact_committees_roots;
  }

  // Slashings
  public List<UnsignedLong> getSlashings() {
    return slashings;
  }

  public void setSlashings(List<UnsignedLong> slashings) {
    this.slashings = slashings;
  }

  // Attestations
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

  // Crosslinks
  public List<Crosslink> getPrevious_crosslinks() {
    return previous_crosslinks;
  }

  public void setPrevious_crosslinks(List<Crosslink> previous_crosslinks) {
    this.previous_crosslinks = previous_crosslinks;
  }

  public List<Crosslink> getCurrent_crosslinks() {
    return current_crosslinks;
  }

  public void setCurrent_crosslinks(List<Crosslink> current_crosslinks) {
    this.current_crosslinks = current_crosslinks;
  }

  // Finality
  public Bytes getJustification_bits() {
    return justification_bits;
  }

  public void setJustification_bits(Bytes justification_bits) {
    this.justification_bits = justification_bits;
  }

  public Checkpoint getPrevious_justified_checkpoint() {
    return previous_justified_checkpoint;
  }

  public void setPrevious_justified_checkpoint(Checkpoint previous_justified_checkpoint) {
    this.previous_justified_checkpoint = previous_justified_checkpoint;
  }

  public Checkpoint getCurrent_justified_checkpoint() {
    return current_justified_checkpoint;
  }

  public void setCurrent_justified_checkpoint(Checkpoint current_justified_checkpoint) {
    this.current_justified_checkpoint = current_justified_checkpoint;
  }

  public Checkpoint getFinalized_checkpoint() {
    return finalized_checkpoint;
  }

  public void setFinalized_checkpoint(Checkpoint finalized_checkpoint) {
    this.finalized_checkpoint = finalized_checkpoint;
  }

  public void incrementSlot() {
    this.slot = slot.plus(UnsignedLong.ONE);
  }

  public Bytes32 hash_tree_root() {
    return Bytes32.ZERO;
  }
}
