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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.common;

import static tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateInvariants.GENESIS_TIME_FIELD;
import static tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateInvariants.GENESIS_VALIDATORS_ROOT_FIELD;
import static tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateInvariants.SLOT_FIELD;

import java.util.List;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchemaHints;
import tech.pegasys.teku.infrastructure.ssz.schema.SszVectorSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.infrastructure.ssz.sos.SszField;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;

public enum BeaconStateFields {
  GENESIS_TIME,
  GENESIS_VALIDATORS_ROOT,
  SLOT,
  FORK,
  LATEST_BLOCK_HEADER,
  BLOCK_ROOTS,
  STATE_ROOTS,
  HISTORICAL_ROOTS,
  ETH1_DATA,
  ETH1_DATA_VOTES,
  ETH1_DEPOSIT_INDEX,
  VALIDATORS,
  BALANCES,
  RANDAO_MIXES,
  SLASHINGS,
  PREVIOUS_EPOCH_ATTESTATIONS,
  CURRENT_EPOCH_ATTESTATIONS,
  JUSTIFICATION_BITS,
  PREVIOUS_JUSTIFIED_CHECKPOINT,
  CURRENT_JUSTIFIED_CHECKPOINT,
  FINALIZED_CHECKPOINT,
  // Altair fields
  PREVIOUS_EPOCH_PARTICIPATION,
  CURRENT_EPOCH_PARTICIPATION,
  INACTIVITY_SCORES,
  CURRENT_SYNC_COMMITTEE,
  NEXT_SYNC_COMMITTEE,
  // Merge fields
  LATEST_EXECUTION_PAYLOAD_HEADER;

  public static void copyCommonFieldsFromSource(
      final MutableBeaconState state, final BeaconState source) {
    // Version
    state.setGenesis_time(source.getGenesis_time());
    state.setGenesis_validators_root(source.getGenesis_validators_root());
    state.setSlot(source.getSlot());
    state.setFork(source.getFork());
    // History
    state.setLatest_block_header(source.getLatest_block_header());
    state.setBlock_roots(source.getBlock_roots());
    state.setState_roots(source.getState_roots());
    state.setHistorical_roots(source.getHistorical_roots());
    // Eth1
    state.setEth1_data(source.getEth1_data());
    state.setEth1_data_votes(source.getEth1_data_votes());
    state.setEth1_deposit_index(source.getEth1_deposit_index());
    // Registry
    state.setValidators(source.getValidators());
    state.setBalances(source.getBalances());
    // Randomness
    state.setRandao_mixes(source.getRandao_mixes());
    // Slashings
    state.setSlashings(source.getSlashings());
    // Finality
    state.setJustification_bits(source.getJustification_bits());
    state.setPrevious_justified_checkpoint(source.getPrevious_justified_checkpoint());
    state.setCurrent_justified_checkpoint(source.getCurrent_justified_checkpoint());
    state.setFinalized_checkpoint(source.getFinalized_checkpoint());
  }

  static List<SszField> getCommonFields(final SpecConfig specConfig) {
    SszField fork_field = new SszField(3, BeaconStateFields.FORK.name(), Fork.SSZ_SCHEMA);
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
                    SszPrimitiveSchemas.BYTES32_SCHEMA, specConfig.getSlotsPerHistoricalRoot()));
    SszField stateRootsField =
        new SszField(
            6,
            BeaconStateFields.STATE_ROOTS.name(),
            () ->
                SszVectorSchema.create(
                    SszPrimitiveSchemas.BYTES32_SCHEMA, specConfig.getSlotsPerHistoricalRoot()));
    SszField historicalRootsField =
        new SszField(
            7,
            BeaconStateFields.HISTORICAL_ROOTS.name(),
            () ->
                SszListSchema.create(
                    SszPrimitiveSchemas.BYTES32_SCHEMA, specConfig.getHistoricalRootsLimit()));
    SszField eth1DataField =
        new SszField(8, BeaconStateFields.ETH1_DATA.name(), Eth1Data.SSZ_SCHEMA);
    SszField eth1DataVotesField =
        new SszField(
            9,
            BeaconStateFields.ETH1_DATA_VOTES.name(),
            () ->
                SszListSchema.create(
                    Eth1Data.SSZ_SCHEMA,
                    (long) specConfig.getEpochsPerEth1VotingPeriod()
                        * specConfig.getSlotsPerEpoch()));
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
                    specConfig.getValidatorRegistryLimit(),
                    SszSchemaHints.sszSuperNode(8)));
    SszField balancesField =
        new SszField(
            12,
            BeaconStateFields.BALANCES.name(),
            () ->
                SszListSchema.create(
                    SszPrimitiveSchemas.UINT64_SCHEMA, specConfig.getValidatorRegistryLimit()));
    SszField randaoMixesField =
        new SszField(
            13,
            BeaconStateFields.RANDAO_MIXES.name(),
            () ->
                SszVectorSchema.create(
                    SszPrimitiveSchemas.BYTES32_SCHEMA, specConfig.getEpochsPerHistoricalVector()));
    SszField slashingsField =
        new SszField(
            14,
            BeaconStateFields.SLASHINGS.name(),
            () ->
                SszVectorSchema.create(
                    SszPrimitiveSchemas.UINT64_SCHEMA, specConfig.getEpochsPerSlashingsVector()));
    SszField justificationBitsField =
        new SszField(
            17,
            BeaconStateFields.JUSTIFICATION_BITS.name(),
            () -> SszBitvectorSchema.create(specConfig.getJustificationBitsLength()));
    SszField previousJustifiedCheckpointField =
        new SszField(
            18, BeaconStateFields.PREVIOUS_JUSTIFIED_CHECKPOINT.name(), Checkpoint.SSZ_SCHEMA);
    SszField currentJustifiedCheckpointField =
        new SszField(
            19, BeaconStateFields.CURRENT_JUSTIFIED_CHECKPOINT.name(), Checkpoint.SSZ_SCHEMA);
    SszField finalizedCheckpointField =
        new SszField(20, BeaconStateFields.FINALIZED_CHECKPOINT.name(), Checkpoint.SSZ_SCHEMA);

    return List.of(
        GENESIS_TIME_FIELD,
        GENESIS_VALIDATORS_ROOT_FIELD,
        SLOT_FIELD,
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
        justificationBitsField,
        previousJustifiedCheckpointField,
        currentJustifiedCheckpointField,
        finalizedCheckpointField);
  }
}
