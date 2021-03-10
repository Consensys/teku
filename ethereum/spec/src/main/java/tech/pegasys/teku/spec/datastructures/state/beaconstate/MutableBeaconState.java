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
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.MutableBeaconStateAltair;
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
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.GENESIS_TIME.name());
    set(fieldIndex, new SszUInt64(genesis_time));
  }

  default void setGenesis_validators_root(Bytes32 genesis_validators_root) {
    final int fieldIndex =
        getSchema().getFieldIndex(BeaconStateFields.GENESIS_VALIDATORS_ROOT.name());
    set(fieldIndex, new SszBytes32(genesis_validators_root));
  }

  default void setSlot(UInt64 slot) {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.SLOT.name());
    set(fieldIndex, new SszUInt64(slot));
  }

  default void setFork(Fork fork) {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.FORK.name());
    set(fieldIndex, fork);
  }

  // History
  default void setLatest_block_header(BeaconBlockHeader latest_block_header) {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.LATEST_BLOCK_HEADER.name());
    set(fieldIndex, latest_block_header);
  }

  @Override
  default SSZMutableVector<Bytes32> getBlock_roots() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.BLOCK_ROOTS.name());
    return new SSZBackingVector<>(
        Bytes32.class, getAnyByRef(fieldIndex), SszBytes32::new, AbstractSszPrimitive::get);
  }

  @Override
  default SSZMutableVector<Bytes32> getState_roots() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.STATE_ROOTS.name());
    return new SSZBackingVector<>(
        Bytes32.class, getAnyByRef(fieldIndex), SszBytes32::new, AbstractSszPrimitive::get);
  }

  @Override
  default SSZMutableList<Bytes32> getHistorical_roots() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.HISTORICAL_ROOTS.name());
    return new SSZBackingList<>(
        Bytes32.class, getAnyByRef(fieldIndex), SszBytes32::new, AbstractSszPrimitive::get);
  }

  // Eth1
  default void setEth1_data(Eth1Data eth1_data) {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.ETH1_DATA.name());
    set(fieldIndex, eth1_data);
  }

  @Override
  default SSZMutableList<Eth1Data> getEth1_data_votes() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.ETH1_DATA_VOTES.name());
    return new SSZBackingList<>(
        Eth1Data.class, getAnyByRef(fieldIndex), Function.identity(), Function.identity());
  }

  default void setEth1_deposit_index(UInt64 eth1_deposit_index) {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.ETH1_DEPOSIT_INDEX.name());
    set(fieldIndex, new SszUInt64(eth1_deposit_index));
  }

  // Registry
  @Override
  default SSZMutableList<Validator> getValidators() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.VALIDATORS.name());
    return new SSZBackingList<>(
        Validator.class, getAnyByRef(fieldIndex), Function.identity(), Function.identity());
  }

  @Override
  default SSZMutableList<UInt64> getBalances() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.BALANCES.name());
    return new SSZBackingList<>(
        UInt64.class, getAnyByRef(fieldIndex), SszUInt64::new, AbstractSszPrimitive::get);
  }

  @Override
  default SSZMutableVector<Bytes32> getRandao_mixes() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.RANDAO_MIXES.name());
    return new SSZBackingVector<>(
        Bytes32.class, getAnyByRef(fieldIndex), SszBytes32::new, AbstractSszPrimitive::get);
  }

  // Slashings
  @Override
  default SSZMutableVector<UInt64> getSlashings() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.SLASHINGS.name());
    return new SSZBackingVector<>(
        UInt64.class, getAnyByRef(fieldIndex), SszUInt64::new, AbstractSszPrimitive::get);
  }

  // Finality
  default void setJustification_bits(SszBitvector justification_bits) {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.JUSTIFICATION_BITS.name());
    set(fieldIndex, justification_bits);
  }

  default void setPrevious_justified_checkpoint(Checkpoint previous_justified_checkpoint) {
    final int fieldIndex =
        getSchema().getFieldIndex(BeaconStateFields.PREVIOUS_JUSTIFIED_CHECKPOINT.name());
    set(fieldIndex, previous_justified_checkpoint);
  }

  default void setCurrent_justified_checkpoint(Checkpoint current_justified_checkpoint) {
    final int fieldIndex =
        getSchema().getFieldIndex(BeaconStateFields.CURRENT_JUSTIFIED_CHECKPOINT.name());
    set(fieldIndex, current_justified_checkpoint);
  }

  default void setFinalized_checkpoint(Checkpoint finalized_checkpoint) {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.FINALIZED_CHECKPOINT.name());
    set(fieldIndex, finalized_checkpoint);
  }

  @Override
  BeaconState commitChanges();

  default Optional<MutableBeaconStatePhase0> toMutableVersionPhase0() {
    return Optional.empty();
  }

  default Optional<MutableBeaconStateAltair> toMutableVersionAltair() {
    return Optional.empty();
  }
}
