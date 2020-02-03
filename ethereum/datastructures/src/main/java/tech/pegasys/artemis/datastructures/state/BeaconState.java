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

import com.google.common.base.MoreObjects;
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
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;
import tech.pegasys.artemis.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.artemis.util.SSZTypes.Bitvector;
import tech.pegasys.artemis.util.SSZTypes.SSZContainer;
import tech.pegasys.artemis.util.SSZTypes.SSZList;
import tech.pegasys.artemis.util.SSZTypes.SSZVector;
import tech.pegasys.artemis.util.config.Constants;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil.SSZTypes;
import tech.pegasys.artemis.util.hashtree.Merkleizable;
import tech.pegasys.artemis.util.sos.SimpleOffsetSerializable;

public class BeaconState implements Merkleizable, SimpleOffsetSerializable, SSZContainer {

  // The number of SimpleSerialize basic types in this SSZ Container/POJO.
  public static final int SSZ_FIELD_COUNT = 14;

  // Versioning
  protected UnsignedLong genesis_time;
  protected UnsignedLong slot;
  protected Fork fork; // For versioning hard forks

  // History
  protected BeaconBlockHeader latest_block_header;
  protected final SSZVector<Bytes32> block_roots; // Vector of length SLOTS_PER_HISTORICAL_ROOT
  protected final SSZVector<Bytes32> state_roots; // Vector of length SLOTS_PER_HISTORICAL_ROOT
  protected final SSZList<Bytes32> historical_roots; // Bounded by HISTORICAL_ROOTS_LIMIT

  // Ethereum 1.0 chain data
  protected Eth1Data eth1_data;
  protected SSZList<Eth1Data> eth1_data_votes; // List Bounded by SLOTS_PER_ETH1_VOTING_PERIOD
  protected UnsignedLong eth1_deposit_index;

  // Validator registry
  protected SSZList<Validator> validators; // List Bounded by VALIDATOR_REGISTRY_LIMIT
  protected SSZList<UnsignedLong> balances; // List Bounded by VALIDATOR_REGISTRY_LIMIT

  protected SSZVector<Bytes32> randao_mixes; // Vector of length EPOCHS_PER_HISTORICAL_VECTOR

  // Slashings
  protected SSZVector<UnsignedLong> slashings; // Vector of length EPOCHS_PER_SLASHINGS_VECTOR

  // Attestations
  protected SSZList<PendingAttestation>
      previous_epoch_attestations; // List bounded by MAX_ATTESTATIONS * SLOTS_PER_EPOCH
  protected SSZList<PendingAttestation>
      current_epoch_attestations; // List bounded by MAX_ATTESTATIONS * SLOTS_PER_EPOCH

  // Finality
  protected Bitvector justification_bits; // Bitvector bounded by JUSTIFICATION_BITS_LENGTH
  protected Checkpoint previous_justified_checkpoint;
  protected Checkpoint current_justified_checkpoint;
  protected Checkpoint finalized_checkpoint;

  public BeaconState(
      // Versioning
      UnsignedLong genesis_time,
      UnsignedLong slot,
      Fork fork,

      // History
      BeaconBlockHeader latest_block_header,
      SSZVector<Bytes32> block_roots,
      SSZVector<Bytes32> state_roots,
      SSZList<Bytes32> historical_roots,

      // Eth1
      Eth1Data eth1_data,
      SSZList<Eth1Data> eth1_data_votes,
      UnsignedLong eth1_deposit_index,

      // Registry
      SSZList<Validator> validators,
      SSZList<UnsignedLong> balances,

      // Randomness
      SSZVector<Bytes32> randao_mixes,

      // Slashings
      SSZVector<UnsignedLong> slashings,

      // Attestations
      SSZList<PendingAttestation> previous_epoch_attestations,
      SSZList<PendingAttestation> current_epoch_attestations,

      // Finality
      Bitvector justification_bits,
      Checkpoint previous_justified_checkpoint,
      Checkpoint current_justified_checkpoint,
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

    // Randomness
    this.randao_mixes = randao_mixes;

    // Slashings
    this.slashings = slashings;

    // Attestations
    this.previous_epoch_attestations = previous_epoch_attestations;
    this.current_epoch_attestations = current_epoch_attestations;

    // Finality
    this.justification_bits = justification_bits;
    this.previous_justified_checkpoint = previous_justified_checkpoint;
    this.current_justified_checkpoint = current_justified_checkpoint;
    this.finalized_checkpoint = finalized_checkpoint;
  }

  public BeaconState() {

    // Versioning
    this.genesis_time = UnsignedLong.ZERO;
    this.slot = UnsignedLong.valueOf(Constants.GENESIS_SLOT);
    this.fork =
        new Fork(
            Constants.GENESIS_FORK_VERSION,
            Constants.GENESIS_FORK_VERSION,
            UnsignedLong.valueOf(Constants.GENESIS_EPOCH));

    // History
    this.latest_block_header = new BeaconBlockHeader();
    this.block_roots = new SSZVector<>(Constants.SLOTS_PER_HISTORICAL_ROOT, Constants.ZERO_HASH);
    this.state_roots = new SSZVector<>(Constants.SLOTS_PER_HISTORICAL_ROOT, Constants.ZERO_HASH);
    this.historical_roots = new SSZList<>(Bytes32.class, Constants.HISTORICAL_ROOTS_LIMIT);

    // Eth1
    // TODO gotta change this with genesis eth1DATA because deposit count is dependent on the
    // number of validators
    this.eth1_data = new Eth1Data(Constants.ZERO_HASH, UnsignedLong.ZERO, Constants.ZERO_HASH);
    this.eth1_data_votes = new SSZList<>(Eth1Data.class, Constants.SLOTS_PER_ETH1_VOTING_PERIOD);
    this.eth1_deposit_index = UnsignedLong.ZERO;

    // Registry
    this.validators = new SSZList<>(Validator.class, Constants.VALIDATOR_REGISTRY_LIMIT);
    this.balances = new SSZList<>(UnsignedLong.class, Constants.VALIDATOR_REGISTRY_LIMIT);

    // Randomness
    this.randao_mixes =
        new SSZVector<>(Constants.EPOCHS_PER_HISTORICAL_VECTOR, Constants.ZERO_HASH);

    // Slashings
    this.slashings = new SSZVector<>(Constants.EPOCHS_PER_SLASHINGS_VECTOR, UnsignedLong.ZERO);

    // Attestations
    this.previous_epoch_attestations =
        new SSZList<>(
            PendingAttestation.class, Constants.MAX_ATTESTATIONS * Constants.SLOTS_PER_EPOCH);
    this.current_epoch_attestations =
        new SSZList<>(
            PendingAttestation.class, Constants.MAX_ATTESTATIONS * Constants.SLOTS_PER_EPOCH);

    // Finality
    this.justification_bits = new Bitvector(Constants.JUSTIFICATION_BITS_LENGTH);
    this.previous_justified_checkpoint = new Checkpoint();
    this.current_justified_checkpoint = new Checkpoint();
    this.finalized_checkpoint = new Checkpoint();
  }

  @Override
  public int getSSZFieldCount() {
    return SSZ_FIELD_COUNT
        + fork.getSSZFieldCount()
        + latest_block_header.getSSZFieldCount()
        + eth1_data.getSSZFieldCount()
        + previous_justified_checkpoint.getSSZFieldCount()
        + current_justified_checkpoint.getSSZFieldCount()
        + finalized_checkpoint.getSSZFieldCount();
  }

  @Override
  public List<Bytes> get_fixed_parts() {
    List<Bytes> fixedPartsList = new ArrayList<>();
    fixedPartsList.addAll(
        List.of(
            SSZ.encodeUInt64(genesis_time.longValue()),
            SSZ.encodeUInt64(slot.longValue()),
            SimpleOffsetSerializer.serialize(fork),
            SimpleOffsetSerializer.serialize(latest_block_header),
            SSZ.encode(writer -> writer.writeFixedBytesVector(block_roots)),
            SSZ.encode(writer -> writer.writeFixedBytesVector(state_roots)),
            Bytes.EMPTY,
            SimpleOffsetSerializer.serialize(eth1_data),
            Bytes.EMPTY,
            SSZ.encodeUInt64(eth1_deposit_index.longValue()),
            Bytes.EMPTY,
            Bytes.EMPTY,
            SSZ.encode(writer -> writer.writeFixedBytesVector(randao_mixes)),
            SSZ.encode(
                writer ->
                    writer.writeFixedBytesVector(
                        slashings.stream()
                            .map(slashing -> SSZ.encodeUInt64(slashing.longValue()))
                            .collect(Collectors.toList()))),
            Bytes.EMPTY,
            Bytes.EMPTY,
            justification_bits.serialize(),
            SimpleOffsetSerializer.serialize(previous_justified_checkpoint),
            SimpleOffsetSerializer.serialize(current_justified_checkpoint),
            SimpleOffsetSerializer.serialize(finalized_checkpoint)));
    return fixedPartsList;
  }

  @Override
  public List<Bytes> get_variable_parts() {
    List<Bytes> variablePartsList = new ArrayList<>();
    variablePartsList.addAll(
        List.of(Bytes.EMPTY, Bytes.EMPTY, Bytes.EMPTY, Bytes.EMPTY, Bytes.EMPTY, Bytes.EMPTY));
    variablePartsList.add(SSZ.encode(writer -> writer.writeFixedBytesVector(historical_roots)));
    variablePartsList.add(Bytes.EMPTY);
    variablePartsList.add(SimpleOffsetSerializer.serializeFixedCompositeList(eth1_data_votes));
    variablePartsList.add(Bytes.EMPTY);
    variablePartsList.add(SimpleOffsetSerializer.serializeFixedCompositeList(validators));
    // TODO The below lines are a hack while Tuweni SSZ/SOS is being upgraded.
    variablePartsList.add(
        Bytes.fromHexString(
            balances.stream()
                .map(value -> SSZ.encodeUInt64(value.longValue()).toHexString().substring(2))
                .collect(Collectors.joining())));
    variablePartsList.addAll(List.of(Bytes.EMPTY, Bytes.EMPTY));
    variablePartsList.add(
        SimpleOffsetSerializer.serializeVariableCompositeList(previous_epoch_attestations));
    variablePartsList.add(
        SimpleOffsetSerializer.serializeVariableCompositeList(current_epoch_attestations));
    variablePartsList.addAll(List.of(Bytes.EMPTY, Bytes.EMPTY, Bytes.EMPTY));
    variablePartsList.addAll(
        Collections.nCopies(previous_justified_checkpoint.getSSZFieldCount(), Bytes.EMPTY));
    variablePartsList.addAll(
        Collections.nCopies(current_justified_checkpoint.getSSZFieldCount(), Bytes.EMPTY));
    variablePartsList.addAll(
        Collections.nCopies(finalized_checkpoint.getSSZFieldCount(), Bytes.EMPTY));
    return variablePartsList;
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

        // Randomness
        randao_mixes,

        // Slashings
        slashings,

        // Attestations
        previous_epoch_attestations,
        current_epoch_attestations,

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
        && Objects.equals(this.getRandao_mixes(), other.getRandao_mixes())
        && Objects.equals(this.getSlashings(), other.getSlashings())
        && Objects.equals(
            this.getPrevious_epoch_attestations(), other.getPrevious_epoch_attestations())
        && Objects.equals(
            this.getCurrent_epoch_attestations(), other.getCurrent_epoch_attestations())
        && Objects.equals(this.getJustification_bits(), other.getJustification_bits())
        && Objects.equals(
            this.getPrevious_justified_checkpoint(), other.getPrevious_justified_checkpoint())
        && Objects.equals(
            this.getCurrent_justified_checkpoint(), other.getCurrent_justified_checkpoint())
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

  public SSZVector<Bytes32> getBlock_roots() {
    return block_roots;
  }

  public SSZVector<Bytes32> getState_roots() {
    return state_roots;
  }

  public SSZList<Bytes32> getHistorical_roots() {
    return historical_roots;
  }

  // Eth1
  public Eth1Data getEth1_data() {
    return eth1_data;
  }

  public void setEth1_data(Eth1Data eth1_data) {
    this.eth1_data = eth1_data;
  }

  public SSZList<Eth1Data> getEth1_data_votes() {
    return eth1_data_votes;
  }

  public void setEth1_data_votes(SSZList<Eth1Data> eth1_data_votes) {
    this.eth1_data_votes = eth1_data_votes;
  }

  public UnsignedLong getEth1_deposit_index() {
    return eth1_deposit_index;
  }

  public void setEth1_deposit_index(UnsignedLong eth1_deposit_index) {
    this.eth1_deposit_index = eth1_deposit_index;
  }

  // Registry
  public SSZList<Validator> getValidators() {
    return validators;
  }

  public void setValidators(SSZList<Validator> validators) {
    this.validators = validators;
  }

  public SSZList<UnsignedLong> getBalances() {
    return balances;
  }

  public void setBalances(SSZList<UnsignedLong> balances) {
    this.balances = balances;
  }

  public SSZVector<Bytes32> getRandao_mixes() {
    return randao_mixes;
  }

  // Slashings
  public SSZVector<UnsignedLong> getSlashings() {
    return slashings;
  }

  // Attestations
  public SSZList<PendingAttestation> getPrevious_epoch_attestations() {
    return previous_epoch_attestations;
  }

  public void setPrevious_epoch_attestations(
      SSZList<PendingAttestation> previous_epoch_attestations) {
    this.previous_epoch_attestations = previous_epoch_attestations;
  }

  public SSZList<PendingAttestation> getCurrent_epoch_attestations() {
    return current_epoch_attestations;
  }

  public void setCurrent_epoch_attestations(
      SSZList<PendingAttestation> current_epoch_attestations) {
    this.current_epoch_attestations = current_epoch_attestations;
  }

  // Finality
  public Bitvector getJustification_bits() {
    return justification_bits;
  }

  public void setJustification_bits(Bitvector justification_bits) {
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

  @Override
  public Bytes32 hash_tree_root() {
    return HashTreeUtil.merkleize(
        Arrays.asList(
            // Versioning
            HashTreeUtil.hash_tree_root(SSZTypes.BASIC, SSZ.encodeUInt64(genesis_time.longValue())),
            HashTreeUtil.hash_tree_root(SSZTypes.BASIC, SSZ.encodeUInt64(slot.longValue())),
            fork.hash_tree_root(),

            // History
            latest_block_header.hash_tree_root(),
            HashTreeUtil.hash_tree_root(SSZTypes.VECTOR_OF_COMPOSITE, block_roots),
            HashTreeUtil.hash_tree_root(SSZTypes.VECTOR_OF_COMPOSITE, state_roots),
            HashTreeUtil.hash_tree_root_list_bytes(
                Constants.HISTORICAL_ROOTS_LIMIT, historical_roots),

            // Ethereum 1.0 chain data
            eth1_data.hash_tree_root(),
            HashTreeUtil.hash_tree_root(
                SSZTypes.LIST_OF_COMPOSITE,
                Constants.SLOTS_PER_ETH1_VOTING_PERIOD,
                eth1_data_votes),
            HashTreeUtil.hash_tree_root(
                SSZTypes.BASIC, SSZ.encodeUInt64(eth1_deposit_index.longValue())),

            // Validator registry
            HashTreeUtil.hash_tree_root(
                SSZTypes.LIST_OF_COMPOSITE, Constants.VALIDATOR_REGISTRY_LIMIT, validators),
            HashTreeUtil.hash_tree_root_list_ul(
                Constants.VALIDATOR_REGISTRY_LIMIT,
                balances.stream()
                    .map(item -> SSZ.encodeUInt64(item.longValue()))
                    .collect(Collectors.toList())),

            // Randomness
            HashTreeUtil.hash_tree_root(SSZTypes.VECTOR_OF_COMPOSITE, randao_mixes),

            // Slashings
            HashTreeUtil.hash_tree_root_vector_unsigned_long(slashings),

            // Attestations
            HashTreeUtil.hash_tree_root(
                SSZTypes.LIST_OF_COMPOSITE,
                Constants.MAX_ATTESTATIONS * Constants.SLOTS_PER_EPOCH,
                previous_epoch_attestations),
            HashTreeUtil.hash_tree_root(
                SSZTypes.LIST_OF_COMPOSITE,
                Constants.MAX_ATTESTATIONS * Constants.SLOTS_PER_EPOCH,
                current_epoch_attestations),

            // Finality
            HashTreeUtil.hash_tree_root_bitvector(justification_bits),
            previous_justified_checkpoint.hash_tree_root(),
            current_justified_checkpoint.hash_tree_root(),
            finalized_checkpoint.hash_tree_root()));
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("genesis_time", genesis_time)
        .add("slot", slot)
        .add("fork", fork)
        .add("latest_block_header", latest_block_header)
        .add("block_roots", block_roots)
        .add("state_roots", state_roots)
        .add("historical_roots", historical_roots)
        .add("eth1_data", eth1_data)
        .add("eth1_data_votes", eth1_data_votes)
        .add("eth1_deposit_index", eth1_deposit_index)
        .add("validators", validators)
        .add("balances", balances)
        .add("randao_mixes", randao_mixes)
        .add("slashings", slashings)
        .add("previous_epoch_attestations", previous_epoch_attestations)
        .add("current_epoch_attestations", current_epoch_attestations)
        .add("justification_bits", justification_bits)
        .add("previous_justified_checkpoint", previous_justified_checkpoint)
        .add("current_justified_checkpoint", current_justified_checkpoint)
        .add("finalized_checkpoint", finalized_checkpoint)
        .toString();
  }
}
