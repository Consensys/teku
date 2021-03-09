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

package tech.pegasys.teku.spec.datastructures.state.beaconstate;

import java.util.Optional;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.phase0.MutableBeaconStatePhase0;
import tech.pegasys.teku.ssz.SSZTypes.SSZBackingList;
import tech.pegasys.teku.ssz.SSZTypes.SSZBackingVector;
import tech.pegasys.teku.ssz.SSZTypes.SSZMutableList;
import tech.pegasys.teku.ssz.SSZTypes.SSZMutableVector;
import tech.pegasys.teku.ssz.backing.SszMutableRefContainer;
import tech.pegasys.teku.ssz.backing.collections.SszBitvector;
import tech.pegasys.teku.ssz.backing.view.AbstractSszPrimitive;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszBytes32;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszUInt64;

public interface MutableBeaconState extends BeaconState, SszMutableRefContainer {

  // Versioning

  default void setGenesis_time(UInt64 genesis_time) {
    set(0, new SszUInt64(genesis_time));
  }

  default void setGenesis_validators_root(Bytes32 genesis_validators_root) {
    set(1, new SszBytes32(genesis_validators_root));
  }

  default void setSlot(UInt64 slot) {
    set(2, new SszUInt64(slot));
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
        Bytes32.class, getAnyByRef(5), SszBytes32::new, AbstractSszPrimitive::get);
  }

  @Override
  default SSZMutableVector<Bytes32> getState_roots() {
    return new SSZBackingVector<>(
        Bytes32.class, getAnyByRef(6), SszBytes32::new, AbstractSszPrimitive::get);
  }

  @Override
  default SSZMutableList<Bytes32> getHistorical_roots() {
    return new SSZBackingList<>(
        Bytes32.class, getAnyByRef(7), SszBytes32::new, AbstractSszPrimitive::get);
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
    set(10, new SszUInt64(eth1_deposit_index));
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
        UInt64.class, getAnyByRef(12), SszUInt64::new, AbstractSszPrimitive::get);
  }

  @Override
  default SSZMutableVector<Bytes32> getRandao_mixes() {
    return new SSZBackingVector<>(
        Bytes32.class, getAnyByRef(13), SszBytes32::new, AbstractSszPrimitive::get);
  }

  // Slashings
  @Override
  default SSZMutableVector<UInt64> getSlashings() {
    return new SSZBackingVector<>(
        UInt64.class, getAnyByRef(14), SszUInt64::new, AbstractSszPrimitive::get);
  }

  // Finality
  default void setJustification_bits(SszBitvector justification_bits) {
    set(17, justification_bits);
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

  default Optional<MutableBeaconStatePhase0> toMutableVersionPhase0() {
    return Optional.empty();
  }
}
