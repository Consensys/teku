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

package tech.pegasys.artemis.datastructures.state;

import com.google.common.primitives.UnsignedLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;
import tech.pegasys.artemis.ssz.SSZTypes.Bitvector;
import tech.pegasys.artemis.ssz.SSZTypes.SSZBackingList;
import tech.pegasys.artemis.ssz.SSZTypes.SSZBackingVector;
import tech.pegasys.artemis.ssz.SSZTypes.SSZContainer;
import tech.pegasys.artemis.ssz.SSZTypes.SSZList;
import tech.pegasys.artemis.ssz.SSZTypes.SSZVector;
import tech.pegasys.artemis.ssz.backing.ContainerViewRead;
import tech.pegasys.artemis.ssz.backing.ViewWrite;
import tech.pegasys.artemis.ssz.backing.type.BasicViewTypes;
import tech.pegasys.artemis.ssz.backing.type.ContainerViewType;
import tech.pegasys.artemis.ssz.backing.type.ListViewType;
import tech.pegasys.artemis.ssz.backing.type.VectorViewType;
import tech.pegasys.artemis.ssz.backing.view.AbstractBasicView;
import tech.pegasys.artemis.ssz.backing.view.BasicViews.Bytes32View;
import tech.pegasys.artemis.ssz.backing.view.BasicViews.UInt64View;
import tech.pegasys.artemis.ssz.backing.view.ViewUtils;
import tech.pegasys.artemis.ssz.sos.SimpleOffsetSerializable;
import tech.pegasys.artemis.util.config.Constants;
import tech.pegasys.artemis.util.hashtree.Merkleizable;

public interface BeaconState
    extends ContainerViewRead, Merkleizable, SimpleOffsetSerializable, SSZContainer {

  Field GENESIS_TIME_FIELD = new Field(0, BasicViewTypes.UINT64_TYPE);
  Field GENESIS_VALIDATORS_ROOT_FIELD = new Field(1, BasicViewTypes.BYTES32_TYPE);
  Field SLOT_FIELD = new Field(2, BasicViewTypes.UINT64_TYPE);
  Field FORK_FIELD = new Field(3, Fork.TYPE);
  Field LATEST_BLOCK_HEADER_FIELD = new Field(4, BeaconBlockHeader.TYPE);
  Field BLOCK_ROOTS_FIELD =
      new Field(
          5,
          () ->
              new VectorViewType<>(
                  BasicViewTypes.BYTES32_TYPE, Constants.SLOTS_PER_HISTORICAL_ROOT));
  Field STATE_ROOTS_FIELD =
      new Field(
          6,
          () ->
              new VectorViewType<>(
                  BasicViewTypes.BYTES32_TYPE, Constants.SLOTS_PER_HISTORICAL_ROOT));
  Field HISTORICAL_ROOTS_FIELD =
      new Field(
          7,
          () -> new ListViewType<>(BasicViewTypes.BYTES32_TYPE, Constants.HISTORICAL_ROOTS_LIMIT));
  Field ETH1_DATA_FIELD = new Field(8, Eth1Data.TYPE);
  Field ETH1_DATA_VOTES_FIELD =
      new Field(
          9,
          () ->
              new ListViewType<>(
                  Eth1Data.TYPE,
                  Constants.EPOCHS_PER_ETH1_VOTING_PERIOD * Constants.SLOTS_PER_EPOCH));
  Field ETH1_DEPOSIT_INDEX_FIELD = new Field(10, BasicViewTypes.UINT64_TYPE);
  Field VALIDATORS_FIELD =
      new Field(11, () -> new ListViewType<>(Validator.TYPE, Constants.VALIDATOR_REGISTRY_LIMIT));
  Field BALANCES_FIELD =
      new Field(
          12,
          () -> new ListViewType<>(BasicViewTypes.UINT64_TYPE, Constants.VALIDATOR_REGISTRY_LIMIT));
  Field RANDAO_MIXES_FIELD =
      new Field(
          13,
          () ->
              new VectorViewType<>(
                  BasicViewTypes.BYTES32_TYPE, Constants.EPOCHS_PER_HISTORICAL_VECTOR));
  Field SLASHINGS_FIELD =
      new Field(
          14,
          () ->
              new VectorViewType<>(
                  BasicViewTypes.UINT64_TYPE, Constants.EPOCHS_PER_SLASHINGS_VECTOR));
  Field PREVIOUS_EPOCH_ATTESTATIONS_FIELD =
      new Field(
          15,
          () ->
              new ListViewType<>(
                  PendingAttestation.TYPE, Constants.MAX_ATTESTATIONS * Constants.SLOTS_PER_EPOCH));
  Field CURRENT_EPOCH_ATTESTATIONS_FIELD =
      new Field(
          16,
          () ->
              new ListViewType<>(
                  PendingAttestation.TYPE, Constants.MAX_ATTESTATIONS * Constants.SLOTS_PER_EPOCH));
  Field JUSTIFICATION_BITS_FIELD =
      new Field(
          17,
          () -> new VectorViewType<>(BasicViewTypes.BIT_TYPE, Constants.JUSTIFICATION_BITS_LENGTH));
  Field PREVIOUS_JUSTIFIED_CHECKPOINT_FIELD = new Field(18, Checkpoint.TYPE);
  Field CURRENT_JUSTIFIED_CHECKPOINT_FIELD = new Field(19, Checkpoint.TYPE);
  Field FINALIZED_CHECKPOINT_FIELD = new Field(20, Checkpoint.TYPE);

  static ContainerViewType<BeaconState> getSSZType() {
    return new ContainerViewType<>(
        SSZContainer.listFields(BeaconState.class).stream()
            .map(f -> f.getViewType().get())
            .collect(Collectors.toList()),
        BeaconStateImpl::new);
  }

  static BeaconState createEmpty() {
    return new BeaconStateImpl();
  }

  static BeaconState create(

      // Versioning
      UnsignedLong genesis_time,
      Bytes32 genesis_validators_root,
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

    return createEmpty()
        .updated(
            state -> {
              state.setGenesis_time(genesis_time);
              state.setGenesis_validators_root(genesis_validators_root);
              state.setSlot(slot);
              state.setFork(fork);
              state.setLatest_block_header(latest_block_header);
              state.getBlock_roots().setAll(block_roots);
              state.getState_roots().setAll(state_roots);
              state.getHistorical_roots().setAll(historical_roots);
              state.setEth1_data(eth1_data);
              state.getEth1_data_votes().setAll(eth1_data_votes);
              state.setEth1_deposit_index(eth1_deposit_index);
              state.getValidators().setAll(validators);
              state.getBalances().setAll(balances);
              state.getRandao_mixes().setAll(randao_mixes);
              state.getSlashings().setAll(slashings);
              state.getPrevious_epoch_attestations().setAll(previous_epoch_attestations);
              state.getCurrent_epoch_attestations().setAll(current_epoch_attestations);
              state.setJustification_bits(justification_bits);
              state.setPrevious_justified_checkpoint(previous_justified_checkpoint);
              state.setCurrent_justified_checkpoint(current_justified_checkpoint);
              state.setFinalized_checkpoint(finalized_checkpoint);
            });
  }

  // Versioning
  default UnsignedLong getGenesis_time() {
    return ((UInt64View) get(GENESIS_TIME_FIELD.getIndex())).get();
  }

  default Bytes32 getGenesis_validators_root() {
    return ((Bytes32View) get(GENESIS_VALIDATORS_ROOT_FIELD.getIndex())).get();
  }

  default UnsignedLong getSlot() {
    return ((UInt64View) get(SLOT_FIELD.getIndex())).get();
  }

  default Fork getFork() {
    return getAny(FORK_FIELD.getIndex());
  }

  default ForkInfo getForkInfo() {
    return new ForkInfo(getFork(), getGenesis_validators_root());
  }

  // History
  default BeaconBlockHeader getLatest_block_header() {
    return getAny(LATEST_BLOCK_HEADER_FIELD.getIndex());
  }

  default SSZVector<Bytes32> getBlock_roots() {
    return new SSZBackingVector<>(
        Bytes32.class,
        getAny(BLOCK_ROOTS_FIELD.getIndex()),
        Bytes32View::new,
        AbstractBasicView::get);
  }

  default SSZVector<Bytes32> getState_roots() {
    return new SSZBackingVector<>(
        Bytes32.class,
        getAny(STATE_ROOTS_FIELD.getIndex()),
        Bytes32View::new,
        AbstractBasicView::get);
  }

  default SSZList<Bytes32> getHistorical_roots() {
    return new SSZBackingList<>(
        Bytes32.class,
        getAny(HISTORICAL_ROOTS_FIELD.getIndex()),
        Bytes32View::new,
        AbstractBasicView::get);
  }

  // Eth1
  default Eth1Data getEth1_data() {
    return getAny(ETH1_DATA_FIELD.getIndex());
  }

  default SSZList<Eth1Data> getEth1_data_votes() {
    return new SSZBackingList<>(
        Eth1Data.class,
        getAny(ETH1_DATA_VOTES_FIELD.getIndex()),
        Function.identity(),
        Function.identity());
  }

  default UnsignedLong getEth1_deposit_index() {
    return ((UInt64View) get(ETH1_DEPOSIT_INDEX_FIELD.getIndex())).get();
  }

  // Registry
  default SSZList<Validator> getValidators() {
    return new SSZBackingList<>(
        Validator.class,
        getAny(VALIDATORS_FIELD.getIndex()),
        Function.identity(),
        Function.identity());
  }

  default SSZList<UnsignedLong> getBalances() {
    return new SSZBackingList<>(
        UnsignedLong.class,
        getAny(BALANCES_FIELD.getIndex()),
        UInt64View::new,
        AbstractBasicView::get);
  }

  default SSZVector<Bytes32> getRandao_mixes() {
    return new SSZBackingVector<>(
        Bytes32.class,
        getAny(RANDAO_MIXES_FIELD.getIndex()),
        Bytes32View::new,
        AbstractBasicView::get);
  }

  // Slashings
  default SSZVector<UnsignedLong> getSlashings() {
    return new SSZBackingVector<>(
        UnsignedLong.class,
        getAny(SLASHINGS_FIELD.getIndex()),
        UInt64View::new,
        AbstractBasicView::get);
  }

  // Attestations
  default SSZList<PendingAttestation> getPrevious_epoch_attestations() {
    return new SSZBackingList<>(
        PendingAttestation.class,
        getAny(PREVIOUS_EPOCH_ATTESTATIONS_FIELD.getIndex()),
        Function.identity(),
        Function.identity());
  }

  default SSZList<PendingAttestation> getCurrent_epoch_attestations() {
    return new SSZBackingList<>(
        PendingAttestation.class,
        getAny(CURRENT_EPOCH_ATTESTATIONS_FIELD.getIndex()),
        Function.identity(),
        Function.identity());
  }

  // Finality
  default Bitvector getJustification_bits() {
    return ViewUtils.getBitvector(getAny(JUSTIFICATION_BITS_FIELD.getIndex()));
  }

  default Checkpoint getPrevious_justified_checkpoint() {
    return getAny(PREVIOUS_JUSTIFIED_CHECKPOINT_FIELD.getIndex());
  }

  default Checkpoint getCurrent_justified_checkpoint() {
    return getAny(CURRENT_JUSTIFIED_CHECKPOINT_FIELD.getIndex());
  }

  default Checkpoint getFinalized_checkpoint() {
    return getAny(FINALIZED_CHECKPOINT_FIELD.getIndex());
  }

  @Override
  default ViewWrite createWritableCopy() {
    throw new UnsupportedOperationException("Use BeaconState.updated() to modify");
  }

  <E1 extends Exception, E2 extends Exception, E3 extends Exception> BeaconState updated(
      Mutator<E1, E2, E3> mutator) throws E1, E2, E3;

  interface Mutator<E1 extends Exception, E2 extends Exception, E3 extends Exception> {
    void mutate(MutableBeaconState state) throws E1, E2, E3;
  }
}
