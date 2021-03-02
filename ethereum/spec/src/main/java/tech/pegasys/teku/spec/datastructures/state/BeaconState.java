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

package tech.pegasys.teku.spec.datastructures.state;

import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.ssz.SSZTypes.SSZBackingList;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;
import tech.pegasys.teku.ssz.backing.SszContainer;
import tech.pegasys.teku.ssz.backing.SszList;
import tech.pegasys.teku.ssz.backing.SszMutableContainer;
import tech.pegasys.teku.ssz.backing.collections.SszBitvector;
import tech.pegasys.teku.ssz.backing.collections.SszBytes32Vector;
import tech.pegasys.teku.ssz.backing.collections.SszPrimitiveList;
import tech.pegasys.teku.ssz.backing.collections.SszPrimitiveVector;
import tech.pegasys.teku.ssz.backing.collections.SszUInt64List;
import tech.pegasys.teku.ssz.backing.schema.SszListSchema;
import tech.pegasys.teku.ssz.backing.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.ssz.backing.schema.SszSchemaHints;
import tech.pegasys.teku.ssz.backing.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.ssz.backing.schema.collections.SszBytes32VectorSchema;
import tech.pegasys.teku.ssz.backing.schema.collections.SszPrimitiveListSchema;
import tech.pegasys.teku.ssz.backing.schema.collections.SszPrimitiveVectorSchema;
import tech.pegasys.teku.ssz.backing.schema.collections.SszUInt64ListSchema;
import tech.pegasys.teku.ssz.backing.schema.impl.AbstractSszContainerSchema;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.view.AbstractSszPrimitive;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszBytes32;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszUInt64;
import tech.pegasys.teku.ssz.sos.SszField;
import tech.pegasys.teku.util.config.Constants;
import tech.pegasys.teku.util.config.SpecDependent;

public interface BeaconState extends SszContainer {

  SszField GENESIS_TIME_FIELD = new SszField(0, "genesis_time", SszPrimitiveSchemas.UINT64_SCHEMA);
  SszField GENESIS_VALIDATORS_ROOT_FIELD =
      new SszField(1, "genesis_validators_root", SszPrimitiveSchemas.BYTES32_SCHEMA);
  SszField SLOT_FIELD = new SszField(2, "slot", SszPrimitiveSchemas.UINT64_SCHEMA);
  SszField FORK_FIELD = new SszField(3, "fork", Fork.SSZ_SCHEMA);
  SszField LATEST_BLOCK_HEADER_FIELD =
      new SszField(4, "latest_block_header", BeaconBlockHeader.SSZ_SCHEMA);

  SpecDependent<SszBytes32VectorSchema<?>> BLOCK_ROOTS_FIELD_SCHEMA =
      SpecDependent.of(() -> SszBytes32VectorSchema.create(Constants.SLOTS_PER_HISTORICAL_ROOT));

  SszField BLOCK_ROOTS_FIELD = new SszField(5, "block_roots", BLOCK_ROOTS_FIELD_SCHEMA::get);

  SpecDependent<SszBytes32VectorSchema<?>> STATE_ROOTS_FIELD_SCHEMA =
      SpecDependent.of(() -> SszBytes32VectorSchema.create(Constants.SLOTS_PER_HISTORICAL_ROOT));

  SszField STATE_ROOTS_FIELD = new SszField(6, "state_roots", STATE_ROOTS_FIELD_SCHEMA::get);

  SpecDependent<SszPrimitiveListSchema<Bytes32, SszBytes32, ?>> HISTORICAL_ROOTS_FIELD_SCHEMA =
      SpecDependent.of(() -> SszPrimitiveListSchema.create(
          SszPrimitiveSchemas.BYTES32_SCHEMA, Constants.HISTORICAL_ROOTS_LIMIT));
  SszField HISTORICAL_ROOTS_FIELD =
      new SszField(
          7,
          "historical_roots",
          HISTORICAL_ROOTS_FIELD_SCHEMA::get);

  SszField ETH1_DATA_FIELD = new SszField(8, "eth1_data", Eth1Data.SSZ_SCHEMA);

  SpecDependent<SszListSchema<Eth1Data, ?>> ETH1_DATA_VOTES_FIELD_SCHEMA =
      SpecDependent.of(
          () ->
              SszListSchema.create(
                  Eth1Data.SSZ_SCHEMA,
                  Constants.EPOCHS_PER_ETH1_VOTING_PERIOD * Constants.SLOTS_PER_EPOCH));
  SszField ETH1_DATA_VOTES_FIELD =
      new SszField(9, "eth1_data_votes", ETH1_DATA_VOTES_FIELD_SCHEMA::get);

  SszField ETH1_DEPOSIT_INDEX_FIELD =
      new SszField(10, "eth1_deposit_index", SszPrimitiveSchemas.UINT64_SCHEMA);

  SpecDependent<SszListSchema<Validator, ?>> VALIDATORS_FIELD_SCHEMA =
      SpecDependent.of(
          () ->
              SszListSchema.create(
                  Validator.SSZ_SCHEMA,
                  Constants.VALIDATOR_REGISTRY_LIMIT,
                  SszSchemaHints.sszSuperNode(8)));

  SszField VALIDATORS_FIELD = new SszField(11, "validators", VALIDATORS_FIELD_SCHEMA::get);

  SpecDependent<SszUInt64ListSchema<?>> BALANCES_FIELD_SCHEMA =
      SpecDependent.of(() -> SszUInt64ListSchema.create(Constants.VALIDATOR_REGISTRY_LIMIT));
  SszField BALANCES_FIELD = new SszField(12, "balances", BALANCES_FIELD_SCHEMA::get);
  SpecDependent<SszBytes32VectorSchema<?>> RANDAO_MIXES_FIELD_SCHEMA =
      SpecDependent.of(() -> SszBytes32VectorSchema.create(Constants.EPOCHS_PER_HISTORICAL_VECTOR));
  SszField RANDAO_MIXES_FIELD = new SszField(13, "randao_mixes", RANDAO_MIXES_FIELD_SCHEMA::get);

  SpecDependent<SszPrimitiveVectorSchema<UInt64, SszUInt64, ?>> SLASHINGS_FIELD_SCHEMA =
      SpecDependent.of(
          () ->
              SszPrimitiveVectorSchema.create(
                  SszPrimitiveSchemas.UINT64_SCHEMA, Constants.EPOCHS_PER_SLASHINGS_VECTOR));
  SszField SLASHINGS_FIELD = new SszField(14, "slashings", SLASHINGS_FIELD_SCHEMA::get);

  SpecDependent<SszListSchema<PendingAttestation, ?>> PREVIOUS_EPOCH_ATTESTATIONS_FIELD_SCHEMA =
      SpecDependent.of(
          () ->
              SszListSchema.create(
                  PendingAttestation.SSZ_SCHEMA,
                  Constants.MAX_ATTESTATIONS * Constants.SLOTS_PER_EPOCH));
  SszField PREVIOUS_EPOCH_ATTESTATIONS_FIELD =
      new SszField(
          15, "previous_epoch_attestations", PREVIOUS_EPOCH_ATTESTATIONS_FIELD_SCHEMA::get);

  SpecDependent<SszListSchema<PendingAttestation, ?>> CURRENT_EPOCH_ATTESTATIONS_FIELD_SCHEMA =
      SpecDependent.of(
          () ->
              SszListSchema.create(
                  PendingAttestation.SSZ_SCHEMA,
                  Constants.MAX_ATTESTATIONS * Constants.SLOTS_PER_EPOCH));
  SszField CURRENT_EPOCH_ATTESTATIONS_FIELD =
      new SszField(16, "current_epoch_attestations", CURRENT_EPOCH_ATTESTATIONS_FIELD_SCHEMA::get);

  SszField JUSTIFICATION_BITS_FIELD =
      new SszField(
          17,
          "justification_bits",
          () -> SszBitvectorSchema.create(Constants.JUSTIFICATION_BITS_LENGTH));
  SszField PREVIOUS_JUSTIFIED_CHECKPOINT_FIELD =
      new SszField(18, "previous_justified_checkpoint", Checkpoint.SSZ_SCHEMA);
  SszField CURRENT_JUSTIFIED_CHECKPOINT_FIELD =
      new SszField(19, "current_justified_checkpoint_field", Checkpoint.SSZ_SCHEMA);
  SszField FINALIZED_CHECKPOINT_FIELD =
      new SszField(20, "finalized_checkpoint", Checkpoint.SSZ_SCHEMA);

  SpecDependent<BeaconStateSchema> SSZ_SCHEMA = SpecDependent.of(BeaconStateSchema::new);

  static BeaconStateSchema getSszSchema() {
    return SSZ_SCHEMA.get();
  }

  class BeaconStateSchema extends AbstractSszContainerSchema<BeaconState> {

    public BeaconStateSchema() {
      super(
          "BeaconState",
          Stream.of(
                  GENESIS_TIME_FIELD,
                  GENESIS_VALIDATORS_ROOT_FIELD,
                  SLOT_FIELD,
                  FORK_FIELD,
                  LATEST_BLOCK_HEADER_FIELD,
                  BLOCK_ROOTS_FIELD,
                  STATE_ROOTS_FIELD,
                  HISTORICAL_ROOTS_FIELD,
                  ETH1_DATA_FIELD,
                  ETH1_DATA_VOTES_FIELD,
                  ETH1_DEPOSIT_INDEX_FIELD,
                  VALIDATORS_FIELD,
                  BALANCES_FIELD,
                  RANDAO_MIXES_FIELD,
                  SLASHINGS_FIELD,
                  PREVIOUS_EPOCH_ATTESTATIONS_FIELD,
                  CURRENT_EPOCH_ATTESTATIONS_FIELD,
                  JUSTIFICATION_BITS_FIELD,
                  PREVIOUS_JUSTIFIED_CHECKPOINT_FIELD,
                  CURRENT_JUSTIFIED_CHECKPOINT_FIELD,
                  FINALIZED_CHECKPOINT_FIELD)
              .map(f -> namedSchema(f.getName(), f.getSchema().get()))
              .collect(Collectors.toList()));
    }

    @Override
    public BeaconState createFromBackingNode(TreeNode node) {
      return new BeaconStateImpl(this, node);
    }
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
      SszBytes32Vector block_roots,
      SszBytes32Vector state_roots,
      SszPrimitiveList<Bytes32, SszBytes32> historical_roots,

      // Eth1
      Eth1Data eth1_data,
      SszList<Eth1Data> eth1_data_votes,
      UInt64 eth1_deposit_index,

      // Registry
      SszList<Validator> validators,
      SszUInt64List balances,

      // Randomness
      SszBytes32Vector randao_mixes,

      // Slashings
      SszPrimitiveVector<UInt64, SszUInt64> slashings,

      // Attestations
      SszList<PendingAttestation> previous_epoch_attestations,
      SszList<PendingAttestation> current_epoch_attestations,

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
              state.setBlock_roots(block_roots);
              state.setState_roots(state_roots);
              state.setHistorical_roots(historical_roots);
              state.setEth1_data(eth1_data);
              state.setEth1_data_votes(eth1_data_votes);
              state.setEth1_deposit_index(eth1_deposit_index);
              state.setValidators(validators);
              state.setBalances(balances);
              state.setRandao_mixes(randao_mixes);
              state.setSlashings(slashings);
              state.setPrevious_epoch_attestations(previous_epoch_attestations);
              state.setCurrent_epoch_attestations(current_epoch_attestations);
              state.setJustification_bits(justification_bits);
              state.setPrevious_justified_checkpoint(previous_justified_checkpoint);
              state.setCurrent_justified_checkpoint(current_justified_checkpoint);
              state.setFinalized_checkpoint(finalized_checkpoint);
            });
  }

  // Versioning
  default UInt64 getGenesis_time() {
    return ((SszUInt64) get(GENESIS_TIME_FIELD.getIndex())).get();
  }

  default Bytes32 getGenesis_validators_root() {
    return ((SszBytes32) get(GENESIS_VALIDATORS_ROOT_FIELD.getIndex())).get();
  }

  default UInt64 getSlot() {
    return ((SszUInt64) get(SLOT_FIELD.getIndex())).get();
  }

  default Fork getFork() {
    return getAny(FORK_FIELD.getIndex());
  }

  default ForkInfo getForkInfo() {
    return new ForkInfo(getFork(), getGenesis_validators_root());
  }

  // History
  default BeaconBlockHeader getLatest_block_header() {
    return getAny(LATEST_BLOCK_HEADER_FIELD.getIndex());
  }

  default SszBytes32Vector getBlock_roots() {
    return getAny(BLOCK_ROOTS_FIELD.getIndex());
  }

  default SszBytes32Vector getState_roots() {
    return getAny(STATE_ROOTS_FIELD.getIndex());
  }

  default SszPrimitiveList<Bytes32, SszBytes32> getHistorical_roots() {
    return getAny(HISTORICAL_ROOTS_FIELD.getIndex());
  }

  // Eth1
  default Eth1Data getEth1_data() {
    return getAny(ETH1_DATA_FIELD.getIndex());
  }

  default SszList<Eth1Data> getEth1_data_votes() {
    return getAny(ETH1_DATA_VOTES_FIELD.getIndex());
  }

  default UInt64 getEth1_deposit_index() {
    return ((SszUInt64) get(ETH1_DEPOSIT_INDEX_FIELD.getIndex())).get();
  }

  // Registry
  default SszList<Validator> getValidators() {
    return getAny(VALIDATORS_FIELD.getIndex());
  }

  default SszUInt64List getBalances() {
    return getAny(BALANCES_FIELD.getIndex());
  }

  default SszBytes32Vector getRandao_mixes() {
    return getAny(RANDAO_MIXES_FIELD.getIndex());
  }

  // Slashings
  default SszPrimitiveVector<UInt64, SszUInt64> getSlashings() {
    return getAny(SLASHINGS_FIELD.getIndex());
  }

  // Attestations
  default SszList<PendingAttestation> getPrevious_epoch_attestations() {
    return getAny(PREVIOUS_EPOCH_ATTESTATIONS_FIELD.getIndex());
  }

  default SszList<PendingAttestation> getCurrent_epoch_attestations() {
    return getAny(CURRENT_EPOCH_ATTESTATIONS_FIELD.getIndex());
  }

  // Finality
  default SszBitvector getJustification_bits() {
    return getAny(JUSTIFICATION_BITS_FIELD.getIndex());
  }

  default Checkpoint getPrevious_justified_checkpoint() {
    return getAny(PREVIOUS_JUSTIFIED_CHECKPOINT_FIELD.getIndex());
  }

  default Checkpoint getCurrent_justified_checkpoint() {
    return getAny(CURRENT_JUSTIFIED_CHECKPOINT_FIELD.getIndex());
  }

  default Checkpoint getFinalized_checkpoint() {
    return getAny(FINALIZED_CHECKPOINT_FIELD.getIndex());
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
