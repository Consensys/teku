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
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;
import tech.pegasys.artemis.util.SSZTypes.Bitvector;
import tech.pegasys.artemis.util.SSZTypes.SSZBackingList;
import tech.pegasys.artemis.util.SSZTypes.SSZBackingListRef;
import tech.pegasys.artemis.util.SSZTypes.SSZBackingVector;
import tech.pegasys.artemis.util.SSZTypes.SSZMutableList;
import tech.pegasys.artemis.util.SSZTypes.SSZMutableRefList;
import tech.pegasys.artemis.util.SSZTypes.SSZMutableVector;
import tech.pegasys.artemis.util.backing.ContainerViewWriteRef;
import tech.pegasys.artemis.util.backing.view.AbstractBasicView;
import tech.pegasys.artemis.util.backing.view.BasicViews.Bytes32View;
import tech.pegasys.artemis.util.backing.view.BasicViews.UInt64View;
import tech.pegasys.artemis.util.backing.view.ViewUtils;

public interface MutableBeaconState extends BeaconState, ContainerViewWriteRef {

  static MutableBeaconState createBuilder() {
    return MutableBeaconStateImpl.createBuilder();
  }

  // Versioning

  default void setGenesis_time(UnsignedLong genesis_time) {
    set(0, new UInt64View(genesis_time));
  }

  default void setSlot(UnsignedLong slot) {
    set(1, new UInt64View(slot));
  }

  default void setFork(Fork fork) {
    set(2, fork);
  }

  // History
  default void setLatest_block_header(BeaconBlockHeader latest_block_header) {
    set(3, latest_block_header);
  }

  @Override
  default SSZMutableVector<Bytes32> getBlock_roots() {
    return new SSZBackingVector<>(
        Bytes32.class, getAnyByRef(4), Bytes32View::new, AbstractBasicView::get);
  }

  @Override
  default SSZMutableVector<Bytes32> getState_roots() {
    return new SSZBackingVector<>(
        Bytes32.class, getAnyByRef(5), Bytes32View::new, AbstractBasicView::get);
  }

  @Override
  default SSZMutableList<Bytes32> getHistorical_roots() {
    return new SSZBackingList<>(
        Bytes32.class, getAnyByRef(6), Bytes32View::new, AbstractBasicView::get);
  }

  // Eth1
  default void setEth1_data(Eth1Data eth1_data) {
    set(7, eth1_data);
  }

  @Override
  default SSZMutableList<Eth1Data> getEth1_data_votes() {
    return new SSZBackingList<>(
        Eth1Data.class, getAnyByRef(8), Function.identity(), Function.identity());
  }

  default void setEth1_deposit_index(UnsignedLong eth1_deposit_index) {
    set(9, new UInt64View(eth1_deposit_index));
  }

  // Registry
  @Override
  default SSZMutableRefList<Validator, MutableValidator> getValidators() {
    return new SSZBackingListRef<>(ValidatorImpl.class, getAnyByRef(10));
  }

  @Override
  default SSZMutableList<UnsignedLong> getBalances() {
    return new SSZBackingList<>(
        UnsignedLong.class, getAnyByRef(11), UInt64View::new, AbstractBasicView::get);
  }

  @Override
  default SSZMutableVector<Bytes32> getRandao_mixes() {
    return new SSZBackingVector<>(
        Bytes32.class, getAnyByRef(12), Bytes32View::new, AbstractBasicView::get);
  }

  // Slashings
  @Override
  default SSZMutableVector<UnsignedLong> getSlashings() {
    return new SSZBackingVector<>(
        UnsignedLong.class, getAnyByRef(13), UInt64View::new, AbstractBasicView::get);
  }

  // Attestations
  @Override
  default SSZMutableList<PendingAttestation> getPrevious_epoch_attestations() {
    return new SSZBackingList<>(
        PendingAttestation.class, getAnyByRef(14), Function.identity(), Function.identity());
  }

  @Override
  default SSZMutableList<PendingAttestation> getCurrent_epoch_attestations() {
    return new SSZBackingList<>(
        PendingAttestation.class, getAnyByRef(15), Function.identity(), Function.identity());
  }

  // Finality
  default void setJustification_bits(Bitvector justification_bits) {
    set(16, ViewUtils.createBitvectorView(justification_bits));
  }

  default void setPrevious_justified_checkpoint(Checkpoint previous_justified_checkpoint) {
    set(17, previous_justified_checkpoint);
  }

  default void setCurrent_justified_checkpoint(Checkpoint current_justified_checkpoint) {
    set(18, current_justified_checkpoint);
  }

  default void setFinalized_checkpoint(Checkpoint finalized_checkpoint) {
    set(19, finalized_checkpoint);
  }

  @Override
  BeaconState commitChanges();
}
