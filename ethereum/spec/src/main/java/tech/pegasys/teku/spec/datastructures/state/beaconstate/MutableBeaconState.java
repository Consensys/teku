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
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.SszMutableList;
import tech.pegasys.teku.infrastructure.ssz.SszMutableRefContainer;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBytes32Vector;
import tech.pegasys.teku.infrastructure.ssz.collections.SszMutableBytes32Vector;
import tech.pegasys.teku.infrastructure.ssz.collections.SszMutablePrimitiveList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszMutablePrimitiveVector;
import tech.pegasys.teku.infrastructure.ssz.collections.SszMutableUInt64List;
import tech.pegasys.teku.infrastructure.ssz.collections.SszPrimitiveList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszPrimitiveVector;
import tech.pegasys.teku.infrastructure.ssz.collections.SszUInt64List;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.MutableBeaconStateAltair;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.merge.MutableBeaconStateMerge;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.phase0.MutableBeaconStatePhase0;

public interface MutableBeaconState extends BeaconState, SszMutableRefContainer {

  // Versioning

  default void setGenesis_time(UInt64 genesis_time) {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.GENESIS_TIME.name());
    set(fieldIndex, SszUInt64.of(genesis_time));
  }

  default void setGenesis_validators_root(Bytes32 genesis_validators_root) {
    final int fieldIndex =
        getSchema().getFieldIndex(BeaconStateFields.GENESIS_VALIDATORS_ROOT.name());
    set(fieldIndex, SszBytes32.of(genesis_validators_root));
  }

  default void setSlot(UInt64 slot) {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.SLOT.name());
    set(fieldIndex, SszUInt64.of(slot));
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
  default SszMutableBytes32Vector getBlock_roots() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.BLOCK_ROOTS.name());
    return getAnyByRef(fieldIndex);
  }

  default void setBlock_roots(SszBytes32Vector block_roots) {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.BLOCK_ROOTS.name());
    set(fieldIndex, block_roots);
  }

  @Override
  default SszMutableBytes32Vector getState_roots() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.STATE_ROOTS.name());
    return getAnyByRef(fieldIndex);
  }

  default void setState_roots(SszBytes32Vector state_roots) {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.STATE_ROOTS.name());
    set(fieldIndex, state_roots);
  }

  @Override
  default SszMutablePrimitiveList<Bytes32, SszBytes32> getHistorical_roots() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.HISTORICAL_ROOTS.name());
    return getAnyByRef(fieldIndex);
  }

  default void setHistorical_roots(SszPrimitiveList<Bytes32, SszBytes32> historical_roots) {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.HISTORICAL_ROOTS.name());
    set(fieldIndex, historical_roots);
  }

  // Eth1
  default void setEth1_data(Eth1Data eth1_data) {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.ETH1_DATA.name());
    set(fieldIndex, eth1_data);
  }

  @Override
  default SszMutableList<Eth1Data> getEth1_data_votes() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.ETH1_DATA_VOTES.name());
    return getAnyByRef(fieldIndex);
  }

  default void setEth1_data_votes(SszList<Eth1Data> eth1DataList) {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.ETH1_DATA_VOTES.name());
    set(fieldIndex, eth1DataList);
  }

  default void setEth1_deposit_index(UInt64 eth1_deposit_index) {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.ETH1_DEPOSIT_INDEX.name());
    set(fieldIndex, SszUInt64.of(eth1_deposit_index));
  }

  // Registry
  @Override
  default SszMutableList<Validator> getValidators() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.VALIDATORS.name());
    return getAnyByRef(fieldIndex);
  }

  default void setValidators(SszList<Validator> validators) {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.VALIDATORS.name());
    set(fieldIndex, validators);
  }

  @Override
  default SszMutableUInt64List getBalances() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.BALANCES.name());
    return getAnyByRef(fieldIndex);
  }

  default void setBalances(SszUInt64List balances) {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.BALANCES.name());
    set(fieldIndex, balances);
  }

  @Override
  default SszMutableBytes32Vector getRandao_mixes() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.RANDAO_MIXES.name());
    return getAnyByRef(fieldIndex);
  }

  default void setRandao_mixes(SszBytes32Vector randao_mixes) {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.RANDAO_MIXES.name());
    set(fieldIndex, randao_mixes);
  }

  // Slashings
  @Override
  default SszMutablePrimitiveVector<UInt64, SszUInt64> getSlashings() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.SLASHINGS.name());
    return getAnyByRef(fieldIndex);
  }

  default void setSlashings(SszPrimitiveVector<UInt64, SszUInt64> slashings) {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.SLASHINGS.name());
    set(fieldIndex, slashings);
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

  default Optional<MutableBeaconStateMerge> toMutableVersionMerge() {
    return Optional.empty();
  }
}
