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
import jdk.jfr.Label;
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
import tech.pegasys.artemis.util.SSZTypes.SSZMutableList;
import tech.pegasys.artemis.util.SSZTypes.SSZMutableRefList;
import tech.pegasys.artemis.util.SSZTypes.SSZMutableVector;
import tech.pegasys.artemis.util.SSZTypes.SSZVector;
import tech.pegasys.artemis.util.backing.ContainerViewWrite;
import tech.pegasys.artemis.util.backing.ListViewWrite;
import tech.pegasys.artemis.util.backing.ListViewWriteRef;
import tech.pegasys.artemis.util.backing.VectorViewRead;
import tech.pegasys.artemis.util.backing.VectorViewWrite;
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

public class BeaconStateImpl extends ContainerViewImpl<BeaconStateImpl>
    implements MutableBeaconState, BeaconStateCache {

  // The number of SimpleSerialize basic types in this SSZ Container/POJO.
  public static final int SSZ_FIELD_COUNT = 14;

  private static volatile ContainerViewType<BeaconStateImpl> TYPE = null;

  private static ContainerViewType<BeaconStateImpl> createSSZType() {
    return new ContainerViewType<>(
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
            BasicViewTypes.UINT64_TYPE,
            new ListViewType<>(ValidatorImpl.TYPE, Constants.VALIDATOR_REGISTRY_LIMIT),
            new ListViewType<>(BasicViewTypes.UINT64_TYPE, Constants.VALIDATOR_REGISTRY_LIMIT),
            new VectorViewType<>(
                BasicViewTypes.BYTES32_TYPE, Constants.EPOCHS_PER_HISTORICAL_VECTOR),
            new VectorViewType<>(BasicViewTypes.UINT64_TYPE, Constants.EPOCHS_PER_SLASHINGS_VECTOR),
            new ListViewType<>(
                PendingAttestation.TYPE, Constants.MAX_ATTESTATIONS * Constants.SLOTS_PER_EPOCH),
            new ListViewType<>(
                PendingAttestation.TYPE, Constants.MAX_ATTESTATIONS * Constants.SLOTS_PER_EPOCH),
            new VectorViewType<>(BasicViewTypes.BIT_TYPE, Constants.JUSTIFICATION_BITS_LENGTH),
            Checkpoint.TYPE,
            Checkpoint.TYPE,
            Checkpoint.TYPE),
        BeaconStateImpl::new);
  }

  public static ContainerViewType<BeaconStateImpl> getSSZType() {
    if (TYPE == null) {
      TYPE = createSSZType();
    }
    return TYPE;
  }

  public static void resetSSZType() {
    TYPE = null;
  }

  @Label("sos-ignore")
  private final TransitionCaches transitionCaches;

  @Label("sos-ignore")
  private final boolean builder;

  // Versioning
  @SuppressWarnings("unused")
  private final UnsignedLong genesis_time = null;

  @SuppressWarnings("unused")
  private final UnsignedLong slot = null;

  @SuppressWarnings("unused")
  private final Fork fork = null; // For versioning hard forks

  // History
  @SuppressWarnings("unused")
  private final BeaconBlockHeader latest_block_header = null;

  @SuppressWarnings("unused")
  private final SSZVector<Bytes32> block_roots =
      SSZVector.create(
          Bytes32.class,
          Constants.SLOTS_PER_HISTORICAL_ROOT); // Vector of length SLOTS_PER_HISTORICAL_ROOT

  @SuppressWarnings("unused")
  private final SSZVector<Bytes32> state_roots =
      SSZVector.create(
          Bytes32.class,
          Constants.SLOTS_PER_HISTORICAL_ROOT); // Vector of length SLOTS_PER_HISTORICAL_ROOT

  @SuppressWarnings("unused")
  private final SSZList<Bytes32> historical_roots =
      SSZList.create(
          Bytes32.class, Constants.HISTORICAL_ROOTS_LIMIT); // Bounded by HISTORICAL_ROOTS_LIMIT

  // Ethereum 1.0 chain data
  @SuppressWarnings("unused")
  private final Eth1Data eth1_data = null;

  @SuppressWarnings("unused")
  private final SSZList<Eth1Data> eth1_data_votes =
      SSZList.create(
          Eth1Data.class,
          Constants.SLOTS_PER_ETH1_VOTING_PERIOD); // List Bounded by SLOTS_PER_ETH1_VOTING_PERIOD

  @SuppressWarnings("unused")
  private final UnsignedLong eth1_deposit_index = null;

  // Validator registry
  @SuppressWarnings("unused")
  private final SSZList<ValidatorImpl> validators =
      SSZList.create(
          ValidatorImpl.class,
          Constants.VALIDATOR_REGISTRY_LIMIT); // List Bounded by VALIDATOR_REGISTRY_LIMIT

  @SuppressWarnings("unused")
  private final SSZList<UnsignedLong> balances =
      SSZList.create(
          UnsignedLong.class,
          Constants.VALIDATOR_REGISTRY_LIMIT); // List Bounded by VALIDATOR_REGISTRY_LIMIT

  @SuppressWarnings("unused")
  private final SSZVector<Bytes32> randao_mixes =
      SSZVector.create(
          Bytes32.class,
          Constants.EPOCHS_PER_HISTORICAL_VECTOR); // Vector of length EPOCHS_PER_HISTORICAL_VECTOR

  // Slashings
  @SuppressWarnings("unused")
  private final SSZVector<UnsignedLong> slashings =
      SSZVector.create(
          UnsignedLong.class,
          Constants.EPOCHS_PER_SLASHINGS_VECTOR); // Vector of length EPOCHS_PER_SLASHINGS_VECTOR

  // Attestations
  @SuppressWarnings("unused")
  private final SSZList<PendingAttestation> previous_epoch_attestations =
      SSZList.create(
          PendingAttestation.class,
          Constants.MAX_ATTESTATIONS
              * Constants.SLOTS_PER_EPOCH); // List bounded by MAX_ATTESTATIONS * SLOTS_PER_EPOCH

  @SuppressWarnings("unused")
  private final SSZList<PendingAttestation> current_epoch_attestations =
      SSZList.create(
          PendingAttestation.class,
          Constants.MAX_ATTESTATIONS
              * Constants.SLOTS_PER_EPOCH); // List bounded by MAX_ATTESTATIONS * SLOTS_PER_EPOCH

  // Finality
  @SuppressWarnings("unused")
  private final Bitvector justification_bits =
      new Bitvector(
          Constants.JUSTIFICATION_BITS_LENGTH); // Bitvector bounded by JUSTIFICATION_BITS_LENGTH

  @SuppressWarnings("unused")
  private final Checkpoint previous_justified_checkpoint = null;

  @SuppressWarnings("unused")
  private final Checkpoint current_justified_checkpoint = null;

  @SuppressWarnings("unused")
  private final Checkpoint finalized_checkpoint = null;

  private BeaconStateImpl(
      ContainerViewType<? extends ContainerViewWrite> type, TreeNode backingNode) {
    super(type, backingNode);
    transitionCaches = TransitionCaches.createNewEmpty();
    builder = false;
  }

  public BeaconStateImpl(
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
      SSZList<? extends Validator> validators,
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
    super(getSSZType());
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
    this.transitionCaches = TransitionCaches.createNewEmpty();
    this.builder = false;
  }

  public BeaconStateImpl() {
    this(false);
  }

  public BeaconStateImpl(boolean builder) {
    super(getSSZType());
    this.builder = builder;
    transitionCaches = builder ? TransitionCaches.getNoOp() : TransitionCaches.createNewEmpty();
  }

  BeaconStateImpl(BeaconState state) {
    super(getSSZType(), state.getBackingNode());
    this.builder = false;
    if (state instanceof BeaconStateImpl && ((BeaconStateImpl) state).builder) {
      transitionCaches = TransitionCaches.createNewEmpty();
    } else if (state instanceof BeaconStateCache) {
      transitionCaches = ((BeaconStateCache) state).getTransitionCaches().copy();
    } else {
      transitionCaches = TransitionCaches.createNewEmpty();
    }
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
            SSZ.encode(writer -> writer.writeFixedBytesVector(getBlock_roots().asList())),
            SSZ.encode(writer -> writer.writeFixedBytesVector(getState_roots().asList())),
            Bytes.EMPTY,
            SimpleOffsetSerializer.serialize(getEth1_data()),
            Bytes.EMPTY,
            SSZ.encodeUInt64(getEth1_deposit_index().longValue()),
            Bytes.EMPTY,
            Bytes.EMPTY,
            SSZ.encode(writer -> writer.writeFixedBytesVector(getRandao_mixes().asList())),
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
    variablePartsList.add(
        SSZ.encode(writer -> writer.writeFixedBytesVector(getHistorical_roots().asList())));
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
    return hashTreeRoot().slice(0, 4).toInt();
  }

  @Override
  public boolean equals(Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }

    if (this == obj) {
      return true;
    }

    if (!(obj instanceof BeaconStateImpl)) {
      return false;
    }

    BeaconStateImpl other = (BeaconStateImpl) obj;
    return hashTreeRoot().equals(other.hashTreeRoot());
  }

  /** ****************** * GETTERS & SETTERS * * ******************* */

  // Versioning
  @Override
  public UnsignedLong getGenesis_time() {
    return ((UInt64View) get(0)).get();
  }

  @Override
  public void setGenesis_time(UnsignedLong genesis_time) {
    set(0, new UInt64View(genesis_time));
  }

  @Override
  public UnsignedLong getSlot() {
    return ((UInt64View) get(1)).get();
  }

  @Override
  public void setSlot(UnsignedLong slot) {
    set(1, new UInt64View(slot));
  }

  @Override
  public Fork getFork() {
    return (Fork) get(2);
  }

  @Override
  public void setFork(Fork fork) {
    set(2, fork);
  }

  // History
  @Override
  public BeaconBlockHeader getLatest_block_header() {
    return (BeaconBlockHeader) get(3);
  }

  @Override
  public void setLatest_block_header(BeaconBlockHeader latest_block_header) {
    set(3, latest_block_header);
  }

  @Override
  public SSZMutableVector<Bytes32> getBlock_roots() {
    return new SSZBackingVector<>(
        Bytes32.class, getBlock_roots_view(), Bytes32View::new, AbstractBasicView::get);
  }

  @SuppressWarnings("unchecked")
  private VectorViewWrite<Bytes32View> getBlock_roots_view() {
    return (VectorViewWrite<Bytes32View>) getByRef(4);
  }

  @Override
  public SSZMutableVector<Bytes32> getState_roots() {
    return new SSZBackingVector<>(
        Bytes32.class, getState_roots_view(), Bytes32View::new, AbstractBasicView::get);
  }

  @SuppressWarnings("unchecked")
  private VectorViewWrite<Bytes32View> getState_roots_view() {
    return (VectorViewWrite<Bytes32View>) getByRef(5);
  }

  @Override
  public SSZMutableList<Bytes32> getHistorical_roots() {
    return new SSZBackingList<>(
        Bytes32.class, getHistorical_roots_view(), Bytes32View::new, AbstractBasicView::get);
  }

  @SuppressWarnings("unchecked")
  private ListViewWrite<Bytes32View> getHistorical_roots_view() {
    return (ListViewWrite<Bytes32View>) getByRef(6);
  }

  // Eth1
  @Override
  public Eth1Data getEth1_data() {
    return (Eth1Data) get(7);
  }

  @Override
  public void setEth1_data(Eth1Data eth1_data) {
    set(7, eth1_data);
  }

  @Override
  public SSZMutableList<Eth1Data> getEth1_data_votes() {
    return new SSZBackingList<>(
        Eth1Data.class, getEth1_data_votes_view(), Function.identity(), Function.identity());
  }

  @SuppressWarnings("unchecked")
  private ListViewWrite<Eth1Data> getEth1_data_votes_view() {
    return (ListViewWrite<Eth1Data>) getByRef(8);
  }

  @Override
  public UnsignedLong getEth1_deposit_index() {
    return ((UInt64View) get(9)).get();
  }

  @Override
  public void setEth1_deposit_index(UnsignedLong eth1_deposit_index) {
    set(9, new UInt64View(eth1_deposit_index));
  }

  // Registry
  @Override
  public SSZMutableRefList<Validator, MutableValidator> getValidators() {
    return new SSZBackingListRef<>(ValidatorImpl.class, getValidators_view());
  }

  @SuppressWarnings("unchecked")
  private ListViewWriteRef<Validator, MutableValidator> getValidators_view() {
    return (ListViewWriteRef<Validator, MutableValidator>) getByRef(10);
  }

  @Override
  public SSZMutableList<UnsignedLong> getBalances() {
    return new SSZBackingList<>(
        UnsignedLong.class, getBalances_view(), UInt64View::new, AbstractBasicView::get);
  }

  @SuppressWarnings("unchecked")
  private ListViewWrite<UInt64View> getBalances_view() {
    return (ListViewWrite<UInt64View>) getByRef(11);
  }

  @Override
  public SSZMutableVector<Bytes32> getRandao_mixes() {
    return new SSZBackingVector<>(
        Bytes32.class, getRandao_mixes_view(), Bytes32View::new, AbstractBasicView::get);
  }

  @SuppressWarnings("unchecked")
  private VectorViewWrite<Bytes32View> getRandao_mixes_view() {
    return (VectorViewWrite<Bytes32View>) getByRef(12);
  }

  // Slashings
  @Override
  public SSZMutableVector<UnsignedLong> getSlashings() {
    return new SSZBackingVector<>(
        UnsignedLong.class, getSlashings_view(), UInt64View::new, AbstractBasicView::get);
  }

  @SuppressWarnings("unchecked")
  private VectorViewWrite<UInt64View> getSlashings_view() {
    return (VectorViewWrite<UInt64View>) getByRef(13);
  }

  // Attestations
  @Override
  public SSZMutableList<PendingAttestation> getPrevious_epoch_attestations() {
    return new SSZBackingList<>(
        PendingAttestation.class,
        getPrevious_epoch_attestations_view(),
        Function.identity(),
        Function.identity());
  }

  @SuppressWarnings("unchecked")
  private ListViewWrite<PendingAttestation> getPrevious_epoch_attestations_view() {
    return (ListViewWrite<PendingAttestation>) getByRef(14);
  }

  @Override
  public SSZMutableList<PendingAttestation> getCurrent_epoch_attestations() {
    return new SSZBackingList<>(
        PendingAttestation.class,
        getCurrent_epoch_attestations_view(),
        Function.identity(),
        Function.identity());
  }

  @SuppressWarnings("unchecked")
  private ListViewWrite<PendingAttestation> getCurrent_epoch_attestations_view() {
    return (ListViewWrite<PendingAttestation>) getByRef(15);
  }

  // Finality
  @SuppressWarnings("unchecked")
  @Override
  public Bitvector getJustification_bits() {
    return ViewUtils.getBitvector((VectorViewRead<BitView>) get(16));
  }

  @Override
  public void setJustification_bits(Bitvector justification_bits) {
    set(16, ViewUtils.createBitvectorView(justification_bits));
  }

  @Override
  public Checkpoint getPrevious_justified_checkpoint() {
    return (Checkpoint) get(17);
  }

  @Override
  public void setPrevious_justified_checkpoint(Checkpoint previous_justified_checkpoint) {
    set(17, previous_justified_checkpoint);
  }

  @Override
  public Checkpoint getCurrent_justified_checkpoint() {
    return (Checkpoint) get(18);
  }

  @Override
  public void setCurrent_justified_checkpoint(Checkpoint current_justified_checkpoint) {
    set(18, current_justified_checkpoint);
  }

  @Override
  public Checkpoint getFinalized_checkpoint() {
    return (Checkpoint) get(19);
  }

  @Override
  public void setFinalized_checkpoint(Checkpoint finalized_checkpoint) {
    set(19, finalized_checkpoint);
  }

  @Override
  public Bytes32 hash_tree_root() {
    return hashTreeRoot();
  }

  @Override
  public TransitionCaches getTransitionCaches() {
    return transitionCaches;
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

  @Override
  public BeaconStateImpl createWritableCopy() {
    return new BeaconStateImpl(this);
  }

  @Override
  public BeaconStateImpl commitChanges() {
    return new BeaconStateImpl(this);
  }
}
