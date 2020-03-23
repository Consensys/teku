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

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.primitives.UnsignedLong;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;
import tech.pegasys.artemis.util.SSZTypes.Bitvector;
import tech.pegasys.artemis.util.SSZTypes.SSZBackingList;
import tech.pegasys.artemis.util.SSZTypes.SSZBackingVector;
import tech.pegasys.artemis.util.SSZTypes.SSZContainer;
import tech.pegasys.artemis.util.SSZTypes.SSZList;
import tech.pegasys.artemis.util.SSZTypes.SSZVector;
import tech.pegasys.artemis.util.backing.ContainerViewRead;
import tech.pegasys.artemis.util.backing.view.AbstractBasicView;
import tech.pegasys.artemis.util.backing.view.BasicViews.Bytes32View;
import tech.pegasys.artemis.util.backing.view.BasicViews.UInt64View;
import tech.pegasys.artemis.util.backing.view.ViewUtils;
import tech.pegasys.artemis.util.hashtree.Merkleizable;
import tech.pegasys.artemis.util.sos.SimpleOffsetSerializable;

@JsonAutoDetect(getterVisibility = Visibility.NONE)
public interface BeaconState
    extends ContainerViewRead, Merkleizable, SimpleOffsetSerializable, SSZContainer {

  static BeaconState createEmpty() {
    return new BeaconStateImpl();
  }

  static BeaconState create(

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

    MutableBeaconState state = createEmpty().createWritableCopy();

    state.setGenesis_time(genesis_time);
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

    return state.commitChanges();
  }

  static void setConstants() {
    BeaconStateImpl.resetSSZType();
  }

  // Versioning
  @JsonProperty
  default UnsignedLong getGenesis_time() {
    return ((UInt64View) get(0)).get();
  }

  @JsonProperty
  default UnsignedLong getSlot() {
    return ((UInt64View) get(1)).get();
  }

  @JsonProperty
  default Fork getFork() {
    return getAny(2);
  }

  // History
  @JsonProperty
  default BeaconBlockHeader getLatest_block_header() {
    return getAny(3);
  }

  @JsonProperty
  default SSZVector<Bytes32> getBlock_roots() {
    return new SSZBackingVector<>(
        Bytes32.class, getAny(4), Bytes32View::new, AbstractBasicView::get);
  }

  @JsonProperty
  default SSZVector<Bytes32> getState_roots() {
    return new SSZBackingVector<>(
        Bytes32.class, getAny(5), Bytes32View::new, AbstractBasicView::get);
  }

  @JsonProperty
  default SSZList<Bytes32> getHistorical_roots() {
    return new SSZBackingList<>(Bytes32.class, getAny(6), Bytes32View::new, AbstractBasicView::get);
  }

  // Eth1
  @JsonProperty
  default Eth1Data getEth1_data() {
    return getAny(7);
  }

  @JsonProperty
  default SSZList<Eth1Data> getEth1_data_votes() {
    return new SSZBackingList<>(
        Eth1Data.class, getAny(8), Function.identity(), Function.identity());
  }

  @JsonProperty
  default UnsignedLong getEth1_deposit_index() {
    return ((UInt64View) get(9)).get();
  }

  // Registry
  @JsonProperty
  default SSZList<Validator> getValidators() {
    return new SSZBackingList<>(
        Validator.class, getAny(10), Function.identity(), Function.identity());
  }

  @JsonProperty
  default SSZList<UnsignedLong> getBalances() {
    return new SSZBackingList<>(
        UnsignedLong.class, getAny(11), UInt64View::new, AbstractBasicView::get);
  }

  @JsonProperty
  default SSZVector<Bytes32> getRandao_mixes() {
    return new SSZBackingVector<>(
        Bytes32.class, getAny(12), Bytes32View::new, AbstractBasicView::get);
  }

  // Slashings
  @JsonProperty
  default SSZVector<UnsignedLong> getSlashings() {
    return new SSZBackingVector<>(
        UnsignedLong.class, getAny(13), UInt64View::new, AbstractBasicView::get);
  }

  // Attestations
  @JsonProperty
  default SSZList<PendingAttestation> getPrevious_epoch_attestations() {
    return new SSZBackingList<>(
        PendingAttestation.class, getAny(14), Function.identity(), Function.identity());
  }

  @JsonProperty
  default SSZList<PendingAttestation> getCurrent_epoch_attestations() {
    return new SSZBackingList<>(
        PendingAttestation.class, getAny(15), Function.identity(), Function.identity());
  }

  // Finality
  @JsonProperty
  default Bitvector getJustification_bits() {
    return ViewUtils.getBitvector(getAny(16));
  }

  @JsonProperty
  default Checkpoint getPrevious_justified_checkpoint() {
    return getAny(17);
  }

  @JsonProperty
  default Checkpoint getCurrent_justified_checkpoint() {
    return getAny(18);
  }

  @JsonProperty
  default Checkpoint getFinalized_checkpoint() {
    return getAny(19);
  }

  @Override
  MutableBeaconState createWritableCopy();
}
