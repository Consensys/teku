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
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;
import tech.pegasys.artemis.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.artemis.util.SSZTypes.Bitvector;
import tech.pegasys.artemis.util.SSZTypes.SSZBackingList;
import tech.pegasys.artemis.util.SSZTypes.SSZBackingListRef;
import tech.pegasys.artemis.util.SSZTypes.SSZBackingVector;
import tech.pegasys.artemis.util.SSZTypes.SSZList;
import tech.pegasys.artemis.util.SSZTypes.SSZListWrite;
import tech.pegasys.artemis.util.SSZTypes.SSZListWriteRef;
import tech.pegasys.artemis.util.SSZTypes.SSZVector;
import tech.pegasys.artemis.util.SSZTypes.SSZVectorWrite;
import tech.pegasys.artemis.util.backing.ContainerViewWrite;
import tech.pegasys.artemis.util.backing.ListViewWrite;
import tech.pegasys.artemis.util.backing.ListViewWriteRef;
import tech.pegasys.artemis.util.backing.VectorViewRead;
import tech.pegasys.artemis.util.backing.VectorViewWrite;
import tech.pegasys.artemis.util.backing.ViewRead;
import tech.pegasys.artemis.util.backing.tree.TreeNode;
import tech.pegasys.artemis.util.backing.type.BasicViewTypes;
import tech.pegasys.artemis.util.backing.type.ContainerViewType;
import tech.pegasys.artemis.util.backing.type.ListViewType;
import tech.pegasys.artemis.util.backing.type.VectorViewType;
import tech.pegasys.artemis.util.backing.view.AbstractBasicView;
import tech.pegasys.artemis.util.backing.view.BasicViews.BitView;
import tech.pegasys.artemis.util.backing.view.BasicViews.Bytes32View;
import tech.pegasys.artemis.util.backing.view.BasicViews.UInt64View;
import tech.pegasys.artemis.util.backing.view.ContainerViewImpl;
import tech.pegasys.artemis.util.backing.view.ViewUtils;
import tech.pegasys.artemis.util.config.Constants;

public class BeaconState extends ContainerViewImpl<BeaconState> implements
    BeaconStateWrite {

  // The number of SimpleSerialize basic types in this SSZ Container/POJO.
  public static final int SSZ_FIELD_COUNT = 14;

  public static final ContainerViewType<BeaconState> TYPE =
      new ContainerViewType<>(
          List.of(
              BasicViewTypes.UINT64_TYPE,
              BasicViewTypes.UINT64_TYPE,
              Fork.TYPE,
              BeaconBlockHeader.TYPE,
              new VectorViewType<>(BasicViewTypes.BYTES32_TYPE, Constants.SLOTS_PER_HISTORICAL_ROOT),
              new VectorViewType<>(BasicViewTypes.BYTES32_TYPE, Constants.SLOTS_PER_HISTORICAL_ROOT),
              new ListViewType<>(BasicViewTypes.BYTES32_TYPE, Constants.HISTORICAL_ROOTS_LIMIT),
              Eth1Data.TYPE,
              new ListViewType<>(Eth1Data.TYPE, Constants.SLOTS_PER_ETH1_VOTING_PERIOD),
              new ListViewType<>(Validator.TYPE, Constants.VALIDATOR_REGISTRY_LIMIT),
              new ListViewType<>(BasicViewTypes.UINT64_TYPE, Constants.VALIDATOR_REGISTRY_LIMIT),
              new VectorViewType<>(BasicViewTypes.BYTES32_TYPE, Constants.EPOCHS_PER_HISTORICAL_VECTOR),
              new VectorViewType<>(BasicViewTypes.UINT64_TYPE, Constants.EPOCHS_PER_SLASHINGS_VECTOR),
              new ListViewType<>(PendingAttestation.TYPE, Constants.MAX_ATTESTATIONS * Constants.SLOTS_PER_EPOCH),
              new ListViewType<>(PendingAttestation.TYPE, Constants.MAX_ATTESTATIONS * Constants.SLOTS_PER_EPOCH),
              new VectorViewType<>(BasicViewTypes.BIT_TYPE, Constants.JUSTIFICATION_BITS_LENGTH),
              Checkpoint.TYPE,
              Checkpoint.TYPE,
              Checkpoint.TYPE
          ),
          BeaconState::new);

  // Versioning
  private final UnsignedLong genesis_time = null;
  private final UnsignedLong slot = null;
  private final Fork fork = null; // For versioning hard forks

  // History
  private final BeaconBlockHeader latest_block_header = null;
  private final SSZVector<Bytes32> block_roots = null; // Vector of length SLOTS_PER_HISTORICAL_ROOT
  private final SSZVector<Bytes32> state_roots = null; // Vector of length SLOTS_PER_HISTORICAL_ROOT
  private final SSZList<Bytes32> historical_roots = null; // Bounded by HISTORICAL_ROOTS_LIMIT

  // Ethereum 1.0 chain data
  private final Eth1Data eth1_data = null;
  private final SSZList<Eth1Data> eth1_data_votes = null; // List Bounded by SLOTS_PER_ETH1_VOTING_PERIOD
  private final UnsignedLong eth1_deposit_index = null;

  // Validator registry
  private final SSZList<Validator> validators = null; // List Bounded by VALIDATOR_REGISTRY_LIMIT
  private final SSZList<UnsignedLong> balances = null; // List Bounded by VALIDATOR_REGISTRY_LIMIT

  private final SSZVector<Bytes32> randao_mixes = null; // Vector of length EPOCHS_PER_HISTORICAL_VECTOR

  // Slashings
  private final SSZVector<UnsignedLong> slashings = null; // Vector of length EPOCHS_PER_SLASHINGS_VECTOR

  // Attestations
  private final SSZList<PendingAttestation>
      previous_epoch_attestations = null; // List bounded by MAX_ATTESTATIONS * SLOTS_PER_EPOCH
  private final SSZList<PendingAttestation>
      current_epoch_attestations = null; // List bounded by MAX_ATTESTATIONS * SLOTS_PER_EPOCH

  // Finality
  private final Bitvector justification_bits = null; // Bitvector bounded by JUSTIFICATION_BITS_LENGTH
  private final Checkpoint previous_justified_checkpoint = null;
  private final Checkpoint current_justified_checkpoint = null;
  private final Checkpoint finalized_checkpoint = null;

  private BeaconState(
      ContainerViewType<? extends ContainerViewWrite<ViewRead>> type,
      TreeNode backingNode) {
    super(type, backingNode);
  }

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
    super(TYPE);
    setGenesis_time(genesis_time);
    setSlot(slot);
    setFork(fork);
    setLatest_block_header(latest_block_header);
    getBlock_roots().setAll(block_roots);
    getState_roots().setAll(state_roots);
    getHistorical_roots().setAll(historical_roots);
    setEth1_data(eth1_data);
    getEth1_data_votes().setAll(eth1_data_votes);
    setEth1_deposit_index(eth1_deposit_index);
    getValidators().setAll(validators);
    getBalances().setAll(balances);
    getRandao_mixes().setAll(randao_mixes);
    getSlashings().setAll(slashings);
    getPrevious_epoch_attestations().setAll(previous_epoch_attestations);
    getCurrent_epoch_attestations().setAll(current_epoch_attestations);
    setJustification_bits(justification_bits);
    setPrevious_justified_checkpoint(previous_justified_checkpoint);
    setCurrent_justified_checkpoint(current_justified_checkpoint);
    setFinalized_checkpoint(finalized_checkpoint);
  }

  public BeaconState() {
    super(TYPE);
  }


  @Override
  public int getSSZFieldCount() {
    return SSZ_FIELD_COUNT
        + getFork().getSSZFieldCount()
        + getLatest_block_header().getSSZFieldCount()
        + getEth1_data().getSSZFieldCount()
        + getPrevious_justified_checkpoint().getSSZFieldCount()
        + getCurrent_justified_checkpoint().getSSZFieldCount()
        + getFinalized_checkpoint().getSSZFieldCount();
  }

  @Override
  public List<Bytes> get_fixed_parts() {
    List<Bytes> fixedPartsList = new ArrayList<>();
    fixedPartsList.addAll(
        List.of(
            SSZ.encodeUInt64(getGenesis_time().longValue()),
            SSZ.encodeUInt64(getSlot().longValue()),
            SimpleOffsetSerializer.serialize(getFork()),
            SimpleOffsetSerializer.serialize(getLatest_block_header()),
            SSZ.encode(writer -> writer.writeFixedBytesVector(getBlock_roots())),
            SSZ.encode(writer -> writer.writeFixedBytesVector(getState_roots())),
            Bytes.EMPTY,
            SimpleOffsetSerializer.serialize(getEth1_data()),
            Bytes.EMPTY,
            SSZ.encodeUInt64(getEth1_deposit_index().longValue()),
            Bytes.EMPTY,
            Bytes.EMPTY,
            SSZ.encode(writer -> writer.writeFixedBytesVector(getRandao_mixes())),
            SSZ.encode(
                writer ->
                    writer.writeFixedBytesVector(
                        getSlashings().stream()
                            .map(slashing -> SSZ.encodeUInt64(slashing.longValue()))
                            .collect(Collectors.toList()))),
            Bytes.EMPTY,
            Bytes.EMPTY,
            getJustification_bits().serialize(),
            SimpleOffsetSerializer.serialize(getPrevious_justified_checkpoint()),
            SimpleOffsetSerializer.serialize(getCurrent_justified_checkpoint()),
            SimpleOffsetSerializer.serialize(getFinalized_checkpoint())));
    return fixedPartsList;
  }

  @Override
  public List<Bytes> get_variable_parts() {
    List<Bytes> variablePartsList = new ArrayList<>();
    variablePartsList.addAll(
        List.of(Bytes.EMPTY, Bytes.EMPTY, Bytes.EMPTY, Bytes.EMPTY, Bytes.EMPTY, Bytes.EMPTY));
    variablePartsList.add(SSZ.encode(writer -> writer.writeFixedBytesVector(getHistorical_roots())));
    variablePartsList.add(Bytes.EMPTY);
    variablePartsList.add(SimpleOffsetSerializer.serializeFixedCompositeList(getEth1_data_votes()));
    variablePartsList.add(Bytes.EMPTY);
    variablePartsList.add(SimpleOffsetSerializer.serializeFixedCompositeList(getValidators()));
    // TODO The below lines are a hack while Tuweni SSZ/SOS is being upgraded.
    variablePartsList.add(
        Bytes.fromHexString(
            getBalances().stream()
                .map(value -> SSZ.encodeUInt64(value.longValue()).toHexString().substring(2))
                .collect(Collectors.joining())));
    variablePartsList.addAll(List.of(Bytes.EMPTY, Bytes.EMPTY));
    variablePartsList.add(
        SimpleOffsetSerializer.serializeVariableCompositeList(getPrevious_epoch_attestations()));
    variablePartsList.add(
        SimpleOffsetSerializer.serializeVariableCompositeList(getCurrent_epoch_attestations()));
    variablePartsList.addAll(List.of(Bytes.EMPTY, Bytes.EMPTY, Bytes.EMPTY));
    variablePartsList.addAll(
        Collections.nCopies(getPrevious_justified_checkpoint().getSSZFieldCount(), Bytes.EMPTY));
    variablePartsList.addAll(
        Collections.nCopies(getCurrent_justified_checkpoint().getSSZFieldCount(), Bytes.EMPTY));
    variablePartsList.addAll(
        Collections.nCopies(getFinalized_checkpoint().getSSZFieldCount(), Bytes.EMPTY));
    return variablePartsList;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        // Versioning
        getGenesis_time(),
        getSlot(),
        getFork(),

        // History
        getLatest_block_header(),
        getBlock_roots(),
        getState_roots(),
        getHistorical_roots(),

        // Eth1
        getEth1_data(),
        getEth1_data_votes(),
        getEth1_deposit_index(),

        // Registry
        getValidators(),
        getBalances(),

        // Randomness
        getRandao_mixes(),

        // Slashings
        getSlashings(),

        // Attestations
        getPrevious_epoch_attestations(),
        getCurrent_epoch_attestations(),

        // Finality
        getJustification_bits(),
        getPrevious_justified_checkpoint(),
        getCurrent_justified_checkpoint(),
        getFinalized_checkpoint());
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
        && Objects.equals(getSlot(), other.getSlot())
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

  /**
   * ****************** * GETTERS & SETTERS * * *******************
   */

  // Versioning
  public UnsignedLong getGenesis_time() {
    return ((UInt64View) get(0)).get();
  }

  public void setGenesis_time(UnsignedLong genesis_time) {
    set(0, new UInt64View(genesis_time));
  }

  public UnsignedLong getSlot() {
    return ((UInt64View) get(1)).get();
  }

  public void setSlot(UnsignedLong slot) {
    set(1, new UInt64View(slot));
  }

  public Fork getFork() {
    return (Fork) get(2);
  }

  public void setFork(Fork fork) {
    set(2, fork);
  }

  // History
  public BeaconBlockHeader getLatest_block_header() {
    return (BeaconBlockHeader) get(3);
  }

  public void setLatest_block_header(BeaconBlockHeader latest_block_header) {
    set(3, latest_block_header);
  }

  public SSZVectorWrite<Bytes32> getBlock_roots() {
    return new SSZBackingVector<>
        (Bytes32.class, getBlock_roots_view(), Bytes32View::new, AbstractBasicView::get);
  }

  @SuppressWarnings("unchecked")
  private VectorViewWrite<Bytes32View> getBlock_roots_view() {
    return (VectorViewWrite<Bytes32View>) get(4);
  }

  public SSZVectorWrite<Bytes32> getState_roots() {
    return new SSZBackingVector<>
        (Bytes32.class, getState_roots_view(), Bytes32View::new, AbstractBasicView::get);
  }

  @SuppressWarnings("unchecked")
  private VectorViewWrite<Bytes32View> getState_roots_view() {
    return (VectorViewWrite<Bytes32View>) get(5);
  }

  public SSZListWrite<Bytes32> getHistorical_roots() {
    return new SSZBackingList<>
        (Bytes32.class, getHistorical_roots_view(), Bytes32View::new, AbstractBasicView::get);
  }

  @SuppressWarnings("unchecked")
  private ListViewWrite<Bytes32View> getHistorical_roots_view() {
    return (ListViewWrite<Bytes32View>) get(6);
  }

  // Eth1
  public Eth1Data getEth1_data() {
    return (Eth1Data) get(7);
  }

  public void setEth1_data(Eth1Data eth1_data) {
    set(7, eth1_data);
  }

  public SSZListWrite<Eth1Data> getEth1_data_votes() {
    return new SSZBackingList<>
        (Eth1Data.class, getEth1_data_votes_view(), Function.identity(), Function.identity());
  }

  @SuppressWarnings("unchecked")
  private ListViewWrite<Eth1Data> getEth1_data_votes_view() {
    return (ListViewWrite<Eth1Data>) get(8);
  }

  public UnsignedLong getEth1_deposit_index() {
    return ((UInt64View) get(9)).get();
  }

  public void setEth1_deposit_index(UnsignedLong eth1_deposit_index) {
    set(9, new UInt64View(eth1_deposit_index));
  }

  // Registry
  public SSZListWriteRef<ValidatorRead, ValidatorWrite> getValidators() {
    return new SSZBackingListRef<>(Validator.class, getValidators_view());
  }

  @SuppressWarnings("unchecked")
  private ListViewWriteRef<ValidatorRead, ValidatorWrite> getValidators_view() {
    return (ListViewWriteRef<ValidatorRead, ValidatorWrite>) get(10);
  }

  public SSZListWrite<UnsignedLong> getBalances() {
    return new SSZBackingList<>
        (UnsignedLong.class, getBalances_view(), UInt64View::new, AbstractBasicView::get);
  }
  @SuppressWarnings("unchecked")
  private ListViewWrite<UInt64View> getBalances_view() {
    return (ListViewWrite<UInt64View>) get(11);
  }

  public SSZVectorWrite<Bytes32> getRandao_mixes() {
    return new SSZBackingVector<>
        (Bytes32.class, getRandao_mixes_view(), Bytes32View::new, AbstractBasicView::get);
  }

  @SuppressWarnings("unchecked")
  private VectorViewWrite<Bytes32View> getRandao_mixes_view() {
    return (VectorViewWrite<Bytes32View>) get(12);
  }

  // Slashings
  public SSZVectorWrite<UnsignedLong> getSlashings() {
    return new SSZBackingVector<>
        (UnsignedLong.class, getSlashings_view(), UInt64View::new, AbstractBasicView::get);
  }

  @SuppressWarnings("unchecked")
  private VectorViewWrite<UInt64View> getSlashings_view() {
    return (VectorViewWrite<UInt64View>) get(13);
  }

  // Attestations
  public SSZListWrite<PendingAttestation> getPrevious_epoch_attestations() {
    return new SSZBackingList<>
        (PendingAttestation.class, getPrevious_epoch_attestations_view(), Function.identity(), Function.identity());
  }

  @SuppressWarnings("unchecked")
  private ListViewWrite<PendingAttestation> getPrevious_epoch_attestations_view() {
    return (ListViewWrite<PendingAttestation>) get(14);
  }

  public SSZListWrite<PendingAttestation> getCurrent_epoch_attestations() {
    return new SSZBackingList<>
        (PendingAttestation.class, getCurrent_epoch_attestations_view(), Function.identity(), Function.identity());
  }

  @SuppressWarnings("unchecked")
  private ListViewWrite<PendingAttestation> getCurrent_epoch_attestations_view() {
    return (ListViewWrite<PendingAttestation>) get(15);
  }

  // Finality
  @SuppressWarnings("unchecked")
  public Bitvector getJustification_bits() {
    return ViewUtils.getBitlist((VectorViewRead<BitView>) get(16));
  }

  public void setJustification_bits(Bitvector justification_bits) {
    set(16, ViewUtils.createBitvectorView(justification_bits));
  }

  public Checkpoint getPrevious_justified_checkpoint() {
    return (Checkpoint) get(17);
  }

  public void setPrevious_justified_checkpoint(Checkpoint previous_justified_checkpoint) {
    set(17, previous_justified_checkpoint);
  }

  public Checkpoint getCurrent_justified_checkpoint() {
    return (Checkpoint) get(18);
  }

  public void setCurrent_justified_checkpoint(Checkpoint current_justified_checkpoint) {
    set(18, current_justified_checkpoint);
  }

  public Checkpoint getFinalized_checkpoint() {
    return (Checkpoint) get(19);
  }

  public void setFinalized_checkpoint(Checkpoint finalized_checkpoint) {
    set(19, finalized_checkpoint);
  }

  @Override
  public Bytes32 hash_tree_root() {
    return hashTreeRoot();
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("genesis_time", getGenesis_time())
        .add("slot", getSlot())
        .add("fork", getFork())
        .add("latest_block_header", getLatest_block_header())
        .add("block_roots", getBlock_roots())
        .add("state_roots", getState_roots())
        .add("historical_roots", getHistorical_roots())
        .add("eth1_data", getEth1_data())
        .add("eth1_data_votes", getEth1_data_votes())
        .add("eth1_deposit_index", getEth1_deposit_index())
        .add("validators", getValidators())
        .add("balances", getBalances())
        .add("randao_mixes", getRandao_mixes())
        .add("slashings", getSlashings())
        .add("previous_epoch_attestations", getPrevious_epoch_attestations())
        .add("current_epoch_attestations", getCurrent_epoch_attestations())
        .add("justification_bits", getJustification_bits())
        .add("previous_justified_checkpoint", getPrevious_justified_checkpoint())
        .add("current_justified_checkpoint", getCurrent_justified_checkpoint())
        .add("finalized_checkpoint", getFinalized_checkpoint())
        .toString();
  }
}
