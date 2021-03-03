/*
 * Copyright 2021 ConsenSys AG.
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

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.constants.SpecConstants;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.PendingAttestation;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;
import tech.pegasys.teku.ssz.SSZTypes.SSZVector;
import tech.pegasys.teku.ssz.backing.collections.SszBitvector;
import tech.pegasys.teku.ssz.backing.schema.AbstractSszContainerSchema;
import tech.pegasys.teku.ssz.backing.schema.SszListSchema;
import tech.pegasys.teku.ssz.backing.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.ssz.backing.schema.SszSchemaHints;
import tech.pegasys.teku.ssz.backing.schema.SszVectorSchema;
import tech.pegasys.teku.ssz.backing.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.sos.SszField;
import tech.pegasys.teku.util.config.Constants;

public class BeaconStateSchema extends AbstractSszContainerSchema<BeaconState> {

  private static final SszField GENESIS_TIME_FIELD =
      new SszField(0, BeaconStateFields.GENESIS_TIME.name(), SszPrimitiveSchemas.UINT64_SCHEMA);
  private static final SszField GENESIS_VALIDATORS_ROOT_FIELD =
      new SszField(
          1, BeaconStateFields.GENESIS_VALIDATORS_ROOT.name(), SszPrimitiveSchemas.BYTES32_SCHEMA);
  private static final SszField SLOT_FIELD =
      new SszField(2, BeaconStateFields.SLOT.name(), SszPrimitiveSchemas.UINT64_SCHEMA);
  private static final SszField FORK_FIELD =
      new SszField(3, BeaconStateFields.FORK.name(), Fork.SSZ_SCHEMA);
  private static final SszField LATEST_BLOCK_HEADER_FIELD =
      new SszField(4, BeaconStateFields.LATEST_BLOCK_HEADER.name(), BeaconBlockHeader.SSZ_SCHEMA);
  private static final SszField BLOCK_ROOTS_FIELD =
      new SszField(
          5,
          BeaconStateFields.BLOCK_ROOTS.name(),
          () ->
              SszVectorSchema.create(
                  SszPrimitiveSchemas.BYTES32_SCHEMA, Constants.SLOTS_PER_HISTORICAL_ROOT));
  private static final SszField STATE_ROOTS_FIELD =
      new SszField(
          6,
          BeaconStateFields.STATE_ROOTS.name(),
          () ->
              SszVectorSchema.create(
                  SszPrimitiveSchemas.BYTES32_SCHEMA, Constants.SLOTS_PER_HISTORICAL_ROOT));
  private static final SszField HISTORICAL_ROOTS_FIELD =
      new SszField(
          7,
          BeaconStateFields.HISTORICAL_ROOTS.name(),
          () ->
              SszListSchema.create(
                  SszPrimitiveSchemas.BYTES32_SCHEMA, Constants.HISTORICAL_ROOTS_LIMIT));
  private static final SszField ETH1_DATA_FIELD =
      new SszField(8, BeaconStateFields.ETH1_DATA.name(), Eth1Data.SSZ_SCHEMA);
  private static final SszField ETH1_DATA_VOTES_FIELD =
      new SszField(
          9,
          BeaconStateFields.ETH1_DATA_VOTES.name(),
          () ->
              SszListSchema.create(
                  Eth1Data.SSZ_SCHEMA,
                  Constants.EPOCHS_PER_ETH1_VOTING_PERIOD * Constants.SLOTS_PER_EPOCH));
  private static final SszField ETH1_DEPOSIT_INDEX_FIELD =
      new SszField(
          10, BeaconStateFields.ETH1_DEPOSIT_INDEX.name(), SszPrimitiveSchemas.UINT64_SCHEMA);
  private static final SszField VALIDATORS_FIELD =
      new SszField(
          11,
          BeaconStateFields.VALIDATORS.name(),
          () ->
              SszListSchema.create(
                  Validator.SSZ_SCHEMA,
                  Constants.VALIDATOR_REGISTRY_LIMIT,
                  SszSchemaHints.sszSuperNode(8)));
  private static final SszField BALANCES_FIELD =
      new SszField(
          12,
          BeaconStateFields.BALANCES.name(),
          () ->
              SszListSchema.create(
                  SszPrimitiveSchemas.UINT64_SCHEMA, Constants.VALIDATOR_REGISTRY_LIMIT));
  private static final SszField RANDAO_MIXES_FIELD =
      new SszField(
          13,
          BeaconStateFields.RANDAO_MIXES.name(),
          () ->
              SszVectorSchema.create(
                  SszPrimitiveSchemas.BYTES32_SCHEMA, Constants.EPOCHS_PER_HISTORICAL_VECTOR));
  private static final SszField SLASHINGS_FIELD =
      new SszField(
          14,
          BeaconStateFields.SLASHINGS.name(),
          () ->
              SszVectorSchema.create(
                  SszPrimitiveSchemas.UINT64_SCHEMA, Constants.EPOCHS_PER_SLASHINGS_VECTOR));
  private static final SszField PREVIOUS_EPOCH_ATTESTATIONS_FIELD =
      new SszField(
          15,
          BeaconStateFields.PREVIOUS_EPOCH_ATTESTATIONS.name(),
          () ->
              SszListSchema.create(
                  PendingAttestation.SSZ_SCHEMA,
                  Constants.MAX_ATTESTATIONS * Constants.SLOTS_PER_EPOCH));
  private static final SszField CURRENT_EPOCH_ATTESTATIONS_FIELD =
      new SszField(
          16,
          BeaconStateFields.CURRENT_EPOCH_ATTESTATIONS.name(),
          () ->
              SszListSchema.create(
                  PendingAttestation.SSZ_SCHEMA,
                  Constants.MAX_ATTESTATIONS * Constants.SLOTS_PER_EPOCH));
  private static final SszField JUSTIFICATION_BITS_FIELD =
      new SszField(
          17,
          BeaconStateFields.JUSTIFICATION_BITS.name(),
          () -> SszBitvectorSchema.create(Constants.JUSTIFICATION_BITS_LENGTH));
  private static final SszField PREVIOUS_JUSTIFIED_CHECKPOINT_FIELD =
      new SszField(
          18, BeaconStateFields.PREVIOUS_JUSTIFIED_CHECKPOINT.name(), Checkpoint.SSZ_SCHEMA);
  private static final SszField CURRENT_JUSTIFIED_CHECKPOINT_FIELD =
      new SszField(
          19, BeaconStateFields.CURRENT_JUSTIFIED_CHECKPOINT.name(), Checkpoint.SSZ_SCHEMA);
  private static final SszField FINALIZED_CHECKPOINT_FIELD =
      new SszField(20, BeaconStateFields.FINALIZED_CHECKPOINT.name(), Checkpoint.SSZ_SCHEMA);

  private BeaconStateSchema(final List<SszField> fields) {
    super(
        "BeaconState",
        fields.stream()
            .sorted(Comparator.comparing(SszField::getIndex))
            .map(f -> namedSchema(f.getName(), f.getSchema().get()))
            .collect(Collectors.toList()));
  }

  public static BeaconStateSchema create(final SpecConstants specConstants) {
    return new BeaconStateSchema(getFields(specConstants));
  }

  @Deprecated
  public static BeaconStateSchema create() {
    final List<SszField> fields =
        List.of(
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
            FINALIZED_CHECKPOINT_FIELD);
    return new BeaconStateSchema(fields);
  }

  private static List<SszField> getFields(final SpecConstants specConstants) {
    SszField genesisTimeField =
        new SszField(0, BeaconStateFields.GENESIS_TIME.name(), SszPrimitiveSchemas.UINT64_SCHEMA);
    SszField genesisValidatorsRootField =
        new SszField(
            1,
            BeaconStateFields.GENESIS_VALIDATORS_ROOT.name(),
            SszPrimitiveSchemas.BYTES32_SCHEMA);
    SszField sszField =
        new SszField(2, BeaconStateFields.SLOT.name(), SszPrimitiveSchemas.UINT64_SCHEMA);
    SszField fork_field = new SszField(3, BeaconStateFields.FORK.name(), Fork.SSZ_SCHEMA);
    // TODO(#3658) - Use non-static BeaconBlockHeaderSchema
    final BeaconBlockHeader.BeaconBlockHeaderSchema blockHeaderSchema =
        BeaconBlockHeader.SSZ_SCHEMA;
    SszField latestBlockHeaderField =
        new SszField(4, BeaconStateFields.LATEST_BLOCK_HEADER.name(), blockHeaderSchema);
    SszField blockRootsField =
        new SszField(
            5,
            BeaconStateFields.BLOCK_ROOTS.name(),
            () ->
                SszVectorSchema.create(
                    SszPrimitiveSchemas.BYTES32_SCHEMA, specConstants.getSlotsPerHistoricalRoot()));
    SszField stateRootsField =
        new SszField(
            6,
            BeaconStateFields.STATE_ROOTS.name(),
            () ->
                SszVectorSchema.create(
                    SszPrimitiveSchemas.BYTES32_SCHEMA, specConstants.getSlotsPerHistoricalRoot()));
    SszField historicalRootsField =
        new SszField(
            7,
            BeaconStateFields.HISTORICAL_ROOTS.name(),
            () ->
                SszListSchema.create(
                    SszPrimitiveSchemas.BYTES32_SCHEMA, specConstants.getHistoricalRootsLimit()));
    SszField eth1DataField =
        new SszField(8, BeaconStateFields.ETH1_DATA.name(), Eth1Data.SSZ_SCHEMA);
    SszField eth1DataVotesField =
        new SszField(
            9,
            BeaconStateFields.ETH1_DATA_VOTES.name(),
            () ->
                SszListSchema.create(
                    Eth1Data.SSZ_SCHEMA,
                    (long) specConstants.getEpochsPerEth1VotingPeriod()
                        * specConstants.getSlotsPerEpoch()));
    SszField eth1DepositIndexField =
        new SszField(
            10, BeaconStateFields.ETH1_DEPOSIT_INDEX.name(), SszPrimitiveSchemas.UINT64_SCHEMA);
    SszField validatorsField =
        new SszField(
            11,
            BeaconStateFields.VALIDATORS.name(),
            () ->
                SszListSchema.create(
                    Validator.SSZ_SCHEMA,
                    specConstants.getValidatorRegistryLimit(),
                    SszSchemaHints.sszSuperNode(8)));
    SszField balancesField =
        new SszField(
            12,
            BeaconStateFields.BALANCES.name(),
            () ->
                SszListSchema.create(
                    SszPrimitiveSchemas.UINT64_SCHEMA, specConstants.getValidatorRegistryLimit()));
    SszField randaoMixesField =
        new SszField(
            13,
            BeaconStateFields.RANDAO_MIXES.name(),
            () ->
                SszVectorSchema.create(
                    SszPrimitiveSchemas.BYTES32_SCHEMA,
                    specConstants.getEpochsPerHistoricalVector()));
    SszField slashingsField =
        new SszField(
            14,
            BeaconStateFields.SLASHINGS.name(),
            () ->
                SszVectorSchema.create(
                    SszPrimitiveSchemas.UINT64_SCHEMA,
                    specConstants.getEpochsPerSlashingsVector()));
    final SszField previousEpochAttestationsField =
        new SszField(
            15,
            BeaconStateFields.PREVIOUS_EPOCH_ATTESTATIONS.name(),
            () ->
                SszListSchema.create(
                    PendingAttestation.SSZ_SCHEMA,
                    (long) specConstants.getMaxAttestations() * specConstants.getSlotsPerEpoch()));
    final SszField currentEpochAttestationsField =
        new SszField(
            16,
            BeaconStateFields.CURRENT_EPOCH_ATTESTATIONS.name(),
            () ->
                SszListSchema.create(
                    PendingAttestation.SSZ_SCHEMA,
                    (long) specConstants.getMaxAttestations() * specConstants.getSlotsPerEpoch()));
    SszField justificationBitsField =
        new SszField(
            17,
            BeaconStateFields.JUSTIFICATION_BITS.name(),
            () -> SszBitvectorSchema.create(specConstants.getJustificationBitsLength()));
    SszField previousJustifiedCheckpointField =
        new SszField(
            18, BeaconStateFields.PREVIOUS_JUSTIFIED_CHECKPOINT.name(), Checkpoint.SSZ_SCHEMA);
    SszField currentJustifiedCheckpointField =
        new SszField(
            19, BeaconStateFields.CURRENT_JUSTIFIED_CHECKPOINT.name(), Checkpoint.SSZ_SCHEMA);
    SszField finalizedCheckpointField =
        new SszField(20, BeaconStateFields.FINALIZED_CHECKPOINT.name(), Checkpoint.SSZ_SCHEMA);

    return List.of(
        genesisTimeField,
        genesisValidatorsRootField,
        sszField,
        fork_field,
        latestBlockHeaderField,
        blockRootsField,
        stateRootsField,
        historicalRootsField,
        eth1DataField,
        eth1DataVotesField,
        eth1DepositIndexField,
        validatorsField,
        balancesField,
        randaoMixesField,
        slashingsField,
        // TODO(#3658) - Make these fields version-specific
        previousEpochAttestationsField,
        currentEpochAttestationsField,
        // Remaining fields are not yet versioned
        justificationBitsField,
        previousJustifiedCheckpointField,
        currentJustifiedCheckpointField,
        finalizedCheckpointField);
  }

  @Override
  public BeaconState createFromBackingNode(TreeNode node) {
    return new BeaconStateImpl(this, node);
  }

  public MutableBeaconState createBuilder() {
    return new MutableBeaconStateImpl(createEmptyBeaconStateImpl(), true);
  }

  public BeaconState createEmpty() {
    return createEmptyBeaconStateImpl();
  }

  public BeaconState create(

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

  private BeaconStateImpl createEmptyBeaconStateImpl() {
    return new BeaconStateImpl(this);
  }
}
