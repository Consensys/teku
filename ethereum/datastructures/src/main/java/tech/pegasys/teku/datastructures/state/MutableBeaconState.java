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

package tech.pegasys.teku.datastructures.state;

import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.Bitvector;
import tech.pegasys.teku.ssz.SSZTypes.SSZBackingList;
import tech.pegasys.teku.ssz.SSZTypes.SSZBackingVector;
import tech.pegasys.teku.ssz.SSZTypes.SSZMutableList;
import tech.pegasys.teku.ssz.SSZTypes.SSZMutableVector;
import tech.pegasys.teku.ssz.backing.ContainerViewWriteRef;
import tech.pegasys.teku.ssz.backing.view.AbstractBasicView;
import tech.pegasys.teku.ssz.backing.view.BasicViews.Bytes32View;
import tech.pegasys.teku.ssz.backing.view.BasicViews.UInt64View;
import tech.pegasys.teku.ssz.backing.view.ViewUtils;

public interface MutableBeaconState extends BeaconState, ContainerViewWriteRef {

  static MutableBeaconState createBuilder() {
    return MutableBeaconStateImpl.createBuilder();
  }

  // Versioning

  default void setGenesis_time(UInt64 genesis_time) {
    set(0, new UInt64View(genesis_time));
  }

  default void setGenesis_validators_root(Bytes32 genesis_validators_root) {
    set(1, new Bytes32View(genesis_validators_root));
  }

  default void setSlot(UInt64 slot) {
    set(2, new UInt64View(slot));
  }

  default void setFork(Fork fork) {
    set(3, fork);
  }

  // History
  default void setLatest_block_header(BeaconBlockHeader latest_block_header) {
    set(4, latest_block_header);
  }

  @Override
  default SSZMutableVector<Bytes32> getBlock_roots() {
    return new SSZBackingVector<>(
        Bytes32.class, getAnyByRef(5), Bytes32View::new, AbstractBasicView::get);
  }

  @Override
  default SSZMutableVector<Bytes32> getState_roots() {
    return new SSZBackingVector<>(
        Bytes32.class, getAnyByRef(6), Bytes32View::new, AbstractBasicView::get);
  }

  @Override
  default SSZMutableList<Bytes32> getHistorical_roots() {
    return new SSZBackingList<>(
        Bytes32.class, getAnyByRef(7), Bytes32View::new, AbstractBasicView::get);
  }

  // Eth1
  default void setEth1_data(Eth1Data eth1_data) {
    set(8, eth1_data);
  }

  @Override
  default SSZMutableList<Eth1Data> getEth1_data_votes() {
    return new SSZBackingList<>(
        Eth1Data.class, getAnyByRef(9), Function.identity(), Function.identity());
  }

  default void setEth1_deposit_index(UInt64 eth1_deposit_index) {
    set(10, new UInt64View(eth1_deposit_index));
  }

  // Registry
  @Override
  default SSZMutableList<Validator> getValidators() {
    return new SSZBackingList<>(
        Validator.class, getAnyByRef(11), Function.identity(), Function.identity());
  }

  @Override
  default SSZMutableList<UInt64> getBalances() {
    return new SSZBackingList<>(
        UInt64.class, getAnyByRef(12), UInt64View::new, AbstractBasicView::get);
  }

  @Override
  default SSZMutableVector<Bytes32> getRandao_mixes() {
    return new SSZBackingVector<>(
        Bytes32.class, getAnyByRef(13), Bytes32View::new, AbstractBasicView::get);
  }

  // Slashings
  @Override
  default SSZMutableVector<UInt64> getSlashings() {
    return new SSZBackingVector<>(
        UInt64.class, getAnyByRef(14), UInt64View::new, AbstractBasicView::get);
  }

  // Attestations
  @Override
  default SSZMutableList<PendingAttestation> getPrevious_epoch_attestations() {
    return new SSZBackingList<>(
        PendingAttestation.class, getAnyByRef(15), Function.identity(), Function.identity());
  }

  @Override
  default SSZMutableList<PendingAttestation> getCurrent_epoch_attestations() {
    return new SSZBackingList<>(
        PendingAttestation.class, getAnyByRef(16), Function.identity(), Function.identity());
  }

  // Finality
  default void setJustification_bits(Bitvector justification_bits) {
    set(17, ViewUtils.createBitvectorView(justification_bits));
  }

  default void setPrevious_justified_checkpoint(Checkpoint previous_justified_checkpoint) {
    set(18, previous_justified_checkpoint);
  }

  default void setCurrent_justified_checkpoint(Checkpoint current_justified_checkpoint) {
    set(19, current_justified_checkpoint);
  }

  default void setFinalized_checkpoint(Checkpoint finalized_checkpoint) {
    set(20, finalized_checkpoint);
  }

  @Override
  BeaconState commitChanges();
}
