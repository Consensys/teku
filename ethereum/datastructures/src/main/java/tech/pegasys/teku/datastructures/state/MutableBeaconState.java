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
import tech.pegasys.teku.ssz.SSZTypes.SSZBackingList;
import tech.pegasys.teku.ssz.SSZTypes.SSZBackingVector;
import tech.pegasys.teku.ssz.SSZTypes.SSZMutableList;
import tech.pegasys.teku.ssz.SSZTypes.SSZMutableVector;
import tech.pegasys.teku.ssz.backing.SszList;
import tech.pegasys.teku.ssz.backing.SszMutableList;
import tech.pegasys.teku.ssz.backing.SszMutableRefContainer;
import tech.pegasys.teku.ssz.backing.collections.SszBitvector;
import tech.pegasys.teku.ssz.backing.collections.SszBytes32Vector;
import tech.pegasys.teku.ssz.backing.collections.SszMutableBytes32Vector;
import tech.pegasys.teku.ssz.backing.view.AbstractSszPrimitive;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszBytes32;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszUInt64;

public interface MutableBeaconState extends BeaconState, SszMutableRefContainer {

  static MutableBeaconState createBuilder() {
    return MutableBeaconStateImpl.createBuilder();
  }

  // Versioning

  default void setGenesis_time(UInt64 genesis_time) {
    set(GENESIS_TIME_FIELD.getIndex(), new SszUInt64(genesis_time));
  }

  default void setGenesis_validators_root(Bytes32 genesis_validators_root) {
    set(GENESIS_VALIDATORS_ROOT_FIELD.getIndex(), new SszBytes32(genesis_validators_root));
  }

  default void setSlot(UInt64 slot) {
    set(SLOT_FIELD.getIndex(), new SszUInt64(slot));
  }

  default void setFork(Fork fork) {
    set(FORK_FIELD.getIndex(), fork);
  }

  // History
  default void setLatest_block_header(BeaconBlockHeader latest_block_header) {
    set(LATEST_BLOCK_HEADER_FIELD.getIndex(), latest_block_header);
  }

  @Override
  default SszMutableBytes32Vector getBlock_roots() {
    return getAnyByRef(BLOCK_ROOTS_FIELD.getIndex());
  }

  default void setBlock_roots(SszBytes32Vector block_roots) {
    set(BLOCK_ROOTS_FIELD.getIndex(), block_roots);
  }

  @Override
  default SszMutableBytes32Vector getState_roots() {
    return getAnyByRef(STATE_ROOTS_FIELD.getIndex());
  }

  default void setState_roots(SszBytes32Vector state_roots) {
    set(STATE_ROOTS_FIELD.getIndex(), state_roots);
  }

  @Override
  default SSZMutableList<Bytes32> getHistorical_roots() {
    return new SSZBackingList<>(
        Bytes32.class, getAnyByRef(HISTORICAL_ROOTS_FIELD.getIndex()), SszBytes32::new, AbstractSszPrimitive::get);
  }

  // Eth1
  default void setEth1_data(Eth1Data eth1_data) {
    set(ETH1_DATA_FIELD.getIndex(), eth1_data);
  }

  @Override
  default SszMutableList<Eth1Data> getEth1_data_votes() {
    return getAnyByRef(ETH1_DATA_VOTES_FIELD.getIndex());
  }

  default void setEth1_data_votes(SszList<Eth1Data> eth1DataList) {
    set(ETH1_DATA_VOTES_FIELD.getIndex(), eth1DataList);
  }

  default void setEth1_deposit_index(UInt64 eth1_deposit_index) {
    set(ETH1_DEPOSIT_INDEX_FIELD.getIndex(), new SszUInt64(eth1_deposit_index));
  }

  // Registry
  @Override
  default SszMutableList<Validator> getValidators() {
    return getAnyByRef(VALIDATORS_FIELD.getIndex());
  }

  default void setValidators(SszList<Validator> validators) {
    set(VALIDATORS_FIELD.getIndex(), validators);
  }

  @Override
  default SSZMutableList<UInt64> getBalances() {
    return new SSZBackingList<>(
        UInt64.class, getAnyByRef(BALANCES_FIELD.getIndex()), SszUInt64::new, AbstractSszPrimitive::get);
  }

  @Override
  default SszMutableBytes32Vector getRandao_mixes() {
    return getAnyByRef(RANDAO_MIXES_FIELD.getIndex());
  }

  default void setRandao_mixes(SszBytes32Vector randao_mixes) {
    set(RANDAO_MIXES_FIELD.getIndex(), randao_mixes);
  }

  // Slashings
  @Override
  default SSZMutableVector<UInt64> getSlashings() {
    return new SSZBackingVector<>(
        UInt64.class, getAnyByRef(SLASHINGS_FIELD.getIndex()), SszUInt64::new, AbstractSszPrimitive::get);
  }

  // Attestations
  @Override
  default SSZMutableList<PendingAttestation> getPrevious_epoch_attestations() {
    return new SSZBackingList<>(
        PendingAttestation.class, getAnyByRef(PREVIOUS_EPOCH_ATTESTATIONS_FIELD.getIndex()), Function.identity(), Function.identity());
  }

  @Override
  default SSZMutableList<PendingAttestation> getCurrent_epoch_attestations() {
    return new SSZBackingList<>(
        PendingAttestation.class, getAnyByRef(CURRENT_EPOCH_ATTESTATIONS_FIELD.getIndex()), Function.identity(), Function.identity());
  }

  // Finality
  default void setJustification_bits(SszBitvector justification_bits) {
    set(JUSTIFICATION_BITS_FIELD.getIndex(), justification_bits);
  }

  default void setPrevious_justified_checkpoint(Checkpoint previous_justified_checkpoint) {
    set(PREVIOUS_JUSTIFIED_CHECKPOINT_FIELD.getIndex(), previous_justified_checkpoint);
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
