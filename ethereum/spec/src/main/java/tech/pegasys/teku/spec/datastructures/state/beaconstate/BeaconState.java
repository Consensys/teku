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
import tech.pegasys.teku.infrastructure.ssz.SszContainer;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.SszMutableContainer;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBytes32Vector;
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
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.analysis.ValidatorStats;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateAltair;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.merge.BeaconStateMerge;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.phase0.BeaconStatePhase0;

public interface BeaconState extends SszContainer, ValidatorStats {

  BeaconStateSchema<?, ?> getBeaconStateSchema();

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

  default SszBytes32Vector getBlock_roots() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.BLOCK_ROOTS.name());
    return getAny(fieldIndex);
  }

  default SszBytes32Vector getState_roots() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.STATE_ROOTS.name());
    return getAny(fieldIndex);
  }

  default SszPrimitiveList<Bytes32, SszBytes32> getHistorical_roots() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.HISTORICAL_ROOTS.name());
    return getAny(fieldIndex);
  }

  // Eth1
  default Eth1Data getEth1_data() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.ETH1_DATA.name());
    return getAny(fieldIndex);
  }

  default SszList<Eth1Data> getEth1_data_votes() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.ETH1_DATA_VOTES.name());
    return getAny(fieldIndex);
  }

  default UInt64 getEth1_deposit_index() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.ETH1_DEPOSIT_INDEX.name());
    return ((SszUInt64) get(fieldIndex)).get();
  }

  // Registry
  default SszList<Validator> getValidators() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.VALIDATORS.name());
    return getAny(fieldIndex);
  }

  default SszUInt64List getBalances() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.BALANCES.name());
    return getAny(fieldIndex);
  }

  default SszBytes32Vector getRandao_mixes() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.RANDAO_MIXES.name());
    return getAny(fieldIndex);
  }

  // Slashings
  default SszPrimitiveVector<UInt64, SszUInt64> getSlashings() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.SLASHINGS.name());
    return getAny(fieldIndex);
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

  default Optional<BeaconStateMerge> toVersionMerge() {
    return Optional.empty();
  }
}
