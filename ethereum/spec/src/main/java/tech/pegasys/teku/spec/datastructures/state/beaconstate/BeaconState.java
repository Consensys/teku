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
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.analysis.ValidatorStats;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateAltair;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.phase0.BeaconStatePhase0;
import tech.pegasys.teku.ssz.SSZTypes.SSZBackingList;
import tech.pegasys.teku.ssz.SSZTypes.SSZBackingVector;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;
import tech.pegasys.teku.ssz.SSZTypes.SSZVector;
import tech.pegasys.teku.ssz.backing.SszContainer;
import tech.pegasys.teku.ssz.backing.SszMutableContainer;
import tech.pegasys.teku.ssz.backing.collections.SszBitvector;
import tech.pegasys.teku.ssz.backing.view.AbstractSszPrimitive;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszBytes32;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszUInt64;

public interface BeaconState extends SszContainer, ValidatorStats {

  default UInt64 getGenesis_time() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.GENESIS_TIME.name());
    return ((SszUInt64) get(fieldIndex)).get();
  }

  default Bytes32 getGenesis_validators_root() {
    final int fieldIndex =
        getSchema().getFieldIndex(BeaconStateFields.GENESIS_VALIDATORS_ROOT.name());
    return ((SszBytes32) get(fieldIndex)).get();
  }

  default UInt64 getSlot() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.SLOT.name());
    return ((SszUInt64) get(fieldIndex)).get();
  }

  default Fork getFork() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.FORK.name());
    return getAny(fieldIndex);
  }

  default ForkInfo getForkInfo() {
    return new ForkInfo(getFork(), getGenesis_validators_root());
  }

  // History
  default BeaconBlockHeader getLatest_block_header() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.LATEST_BLOCK_HEADER.name());
    return getAny(fieldIndex);
  }

  default SSZVector<Bytes32> getBlock_roots() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.BLOCK_ROOTS.name());
    return new SSZBackingVector<>(
        Bytes32.class, getAny(fieldIndex), SszBytes32::new, AbstractSszPrimitive::get);
  }

  default SSZVector<Bytes32> getState_roots() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.STATE_ROOTS.name());
    return new SSZBackingVector<>(
        Bytes32.class, getAny(fieldIndex), SszBytes32::new, AbstractSszPrimitive::get);
  }

  default SSZList<Bytes32> getHistorical_roots() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.HISTORICAL_ROOTS.name());
    return new SSZBackingList<>(
        Bytes32.class, getAny(fieldIndex), SszBytes32::new, AbstractSszPrimitive::get);
  }

  // Eth1
  default Eth1Data getEth1_data() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.ETH1_DATA.name());
    return getAny(fieldIndex);
  }

  default SSZList<Eth1Data> getEth1_data_votes() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.ETH1_DATA_VOTES.name());
    return new SSZBackingList<>(
        Eth1Data.class, getAny(fieldIndex), Function.identity(), Function.identity());
  }

  default UInt64 getEth1_deposit_index() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.ETH1_DEPOSIT_INDEX.name());
    return ((SszUInt64) get(fieldIndex)).get();
  }

  // Registry
  default SSZList<Validator> getValidators() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.VALIDATORS.name());
    return new SSZBackingList<>(
        Validator.class, getAny(fieldIndex), Function.identity(), Function.identity());
  }

  default SSZList<UInt64> getBalances() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.BALANCES.name());
    return new SSZBackingList<>(
        UInt64.class, getAny(fieldIndex), SszUInt64::new, AbstractSszPrimitive::get);
  }

  default SSZVector<Bytes32> getRandao_mixes() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.RANDAO_MIXES.name());
    return new SSZBackingVector<>(
        Bytes32.class, getAny(fieldIndex), SszBytes32::new, AbstractSszPrimitive::get);
  }

  // Slashings
  default SSZVector<UInt64> getSlashings() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.SLASHINGS.name());
    return new SSZBackingVector<>(
        UInt64.class, getAny(fieldIndex), SszUInt64::new, AbstractSszPrimitive::get);
  }

  // Finality
  default SszBitvector getJustification_bits() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.JUSTIFICATION_BITS.name());
    return getAny(fieldIndex);
  }

  default Checkpoint getPrevious_justified_checkpoint() {
    final int fieldIndex =
        getSchema().getFieldIndex(BeaconStateFields.PREVIOUS_JUSTIFIED_CHECKPOINT.name());
    return getAny(fieldIndex);
  }

  default Checkpoint getCurrent_justified_checkpoint() {
    final int fieldIndex =
        getSchema().getFieldIndex(BeaconStateFields.CURRENT_JUSTIFIED_CHECKPOINT.name());
    return getAny(fieldIndex);
  }

  default Checkpoint getFinalized_checkpoint() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.FINALIZED_CHECKPOINT.name());
    return getAny(fieldIndex);
  }

  @Override
  default SszMutableContainer createWritableCopy() {
    throw new UnsupportedOperationException("Use BeaconState.updated() to modify");
  }

  <E1 extends Exception, E2 extends Exception, E3 extends Exception> BeaconState updated(
      Mutator<MutableBeaconState, E1, E2, E3> mutator) throws E1, E2, E3;

  interface Mutator<
      TState extends MutableBeaconState,
      E1 extends Exception,
      E2 extends Exception,
      E3 extends Exception> {

    void mutate(TState state) throws E1, E2, E3;
  }

  default Optional<BeaconStatePhase0> toVersionPhase0() {
    return Optional.empty();
  }

  default Optional<BeaconStateAltair> toVersionAltair() {
    return Optional.empty();
  }
}
