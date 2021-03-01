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

import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.datastructures.state.PendingAttestation;
import tech.pegasys.teku.spec.datastructures.state.Validator;
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
import tech.pegasys.teku.util.config.SpecDependent;

public interface BeaconState extends SszContainer {

  SpecDependent<BeaconStateSchema> SSZ_SCHEMA = SpecDependent.of(BeaconStateSchema::create);

  static BeaconStateSchema getSszSchema() {
    return SSZ_SCHEMA.get();
  }

  static BeaconState createEmpty() {
    SSZ_SCHEMA.reset();
    return new BeaconStateImpl();
  }

  static BeaconState create(

      // Versioning
      UInt64 genesis_time,
      Bytes32 genesis_validators_root,
      UInt64 slot,
      Fork fork,

      // History
      BeaconBlockHeader latest_block_header,
      SSZVector<Bytes32> block_roots,
      SSZVector<Bytes32> state_roots,
      SSZList<Bytes32> historical_roots,

      // Eth1
      Eth1Data eth1_data,
      SSZList<Eth1Data> eth1_data_votes,
      UInt64 eth1_deposit_index,

      // Registry
      SSZList<? extends Validator> validators,
      SSZList<UInt64> balances,

      // Randomness
      SSZVector<Bytes32> randao_mixes,

      // Slashings
      SSZVector<UInt64> slashings,

      // Attestations
      SSZList<PendingAttestation> previous_epoch_attestations,
      SSZList<PendingAttestation> current_epoch_attestations,

      // Finality
      SszBitvector justification_bits,
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

  // Attestations
  default SSZList<PendingAttestation> getPrevious_epoch_attestations() {
    final int fieldIndex =
        getSchema().getFieldIndex(BeaconStateFields.PREVIOUS_EPOCH_ATTESTATIONS.name());
    return new SSZBackingList<>(
        PendingAttestation.class, getAny(fieldIndex), Function.identity(), Function.identity());
  }

  default SSZList<PendingAttestation> getCurrent_epoch_attestations() {
    final int fieldIndex =
        getSchema().getFieldIndex(BeaconStateFields.CURRENT_EPOCH_ATTESTATIONS.name());
    return new SSZBackingList<>(
        PendingAttestation.class, getAny(fieldIndex), Function.identity(), Function.identity());
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
      Mutator<E1, E2, E3> mutator) throws E1, E2, E3;

  interface Mutator<E1 extends Exception, E2 extends Exception, E3 extends Exception> {

    void mutate(MutableBeaconState state) throws E1, E2, E3;
  }
}
