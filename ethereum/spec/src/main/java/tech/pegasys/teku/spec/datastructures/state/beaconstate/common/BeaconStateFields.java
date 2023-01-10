/*
 * Copyright ConsenSys Software Inc., 2022
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
import java.util.Locale;
import tech.pegasys.teku.infrastructure.ssz.schema.SszFieldName;
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

public enum BeaconStateFields implements SszFieldName {
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
  // Bellatrix fields
  LATEST_EXECUTION_PAYLOAD_HEADER,
  // Capella fields
  NEXT_WITHDRAWAL_INDEX,
  NEXT_WITHDRAWAL_VALIDATOR_INDEX,
  HISTORICAL_SUMMARIES;

  private final String sszFieldName;

  BeaconStateFields() {
    this.sszFieldName = name().toLowerCase(Locale.ROOT);
  }

  @Override
  public String getSszFieldName() {
    return sszFieldName;
  }

  public static void copyCommonFieldsFromSource(
      final MutableBeaconState state, final BeaconState source) {
    // Version
    state.setGenesisTime(source.getGenesisTime());
    state.setGenesisValidatorsRoot(source.getGenesisValidatorsRoot());
    state.setSlot(source.getSlot());
    state.setFork(source.getFork());
    // History
    state.setLatestBlockHeader(source.getLatestBlockHeader());
    state.setBlockRoots(source.getBlockRoots());
    state.setStateRoots(source.getStateRoots());
    state.setHistoricalRoots(source.getHistoricalRoots());
    // Eth1
    state.setEth1Data(source.getEth1Data());
    state.setEth1DataVotes(source.getEth1DataVotes());
    state.setEth1DepositIndex(source.getEth1DepositIndex());
    // Registry
    state.setValidators(source.getValidators());
    state.setBalances(source.getBalances());
    // Randomness
    state.setRandaoMixes(source.getRandaoMixes());
    // Slashings
    state.setSlashings(source.getSlashings());
    // Finality
    state.setJustificationBits(source.getJustificationBits());
    state.setPreviousJustifiedCheckpoint(source.getPreviousJustifiedCheckpoint());
    state.setCurrentJustifiedCheckpoint(source.getCurrentJustifiedCheckpoint());
    state.setFinalizedCheckpoint(source.getFinalizedCheckpoint());
  }

  static List<SszField> getCommonFields(final SpecConfig specConfig) {
    SszField forkField = new SszField(3, BeaconStateFields.FORK, Fork.SSZ_SCHEMA);
    final BeaconBlockHeader.BeaconBlockHeaderSchema blockHeaderSchema =
        BeaconBlockHeader.SSZ_SCHEMA;
    SszField latestBlockHeaderField =
        new SszField(4, BeaconStateFields.LATEST_BLOCK_HEADER, blockHeaderSchema);
    SszField blockRootsField =
        new SszField(
            5,
            BeaconStateFields.BLOCK_ROOTS,
            () ->
                SszVectorSchema.create(
                    SszPrimitiveSchemas.BYTES32_SCHEMA, specConfig.getSlotsPerHistoricalRoot()));
    SszField stateRootsField =
        new SszField(
            6,
            BeaconStateFields.STATE_ROOTS,
            () ->
                SszVectorSchema.create(
                    SszPrimitiveSchemas.BYTES32_SCHEMA, specConfig.getSlotsPerHistoricalRoot()));
    SszField historicalRootsField =
        new SszField(
            7,
            BeaconStateFields.HISTORICAL_ROOTS,
            () ->
                SszListSchema.create(
                    SszPrimitiveSchemas.BYTES32_SCHEMA, specConfig.getHistoricalRootsLimit()));
    SszField eth1DataField = new SszField(8, BeaconStateFields.ETH1_DATA, Eth1Data.SSZ_SCHEMA);
    SszField eth1DataVotesField =
        new SszField(
            9,
            BeaconStateFields.ETH1_DATA_VOTES,
            () ->
                SszListSchema.create(
                    Eth1Data.SSZ_SCHEMA,
                    (long) specConfig.getEpochsPerEth1VotingPeriod()
                        * specConfig.getSlotsPerEpoch()));
    SszField eth1DepositIndexField =
        new SszField(10, BeaconStateFields.ETH1_DEPOSIT_INDEX, SszPrimitiveSchemas.UINT64_SCHEMA);
    SszField validatorsField =
        new SszField(
            11,
            BeaconStateFields.VALIDATORS,
            () ->
                SszListSchema.create(
                    Validator.SSZ_SCHEMA,
                    specConfig.getValidatorRegistryLimit(),
                    SszSchemaHints.sszSuperNode(8)));
    SszField balancesField =
        new SszField(
            12,
            BeaconStateFields.BALANCES,
            () ->
                SszListSchema.create(
                    SszPrimitiveSchemas.UINT64_SCHEMA, specConfig.getValidatorRegistryLimit()));
    SszField randaoMixesField =
        new SszField(
            13,
            BeaconStateFields.RANDAO_MIXES,
            () ->
                SszVectorSchema.create(
                    SszPrimitiveSchemas.BYTES32_SCHEMA, specConfig.getEpochsPerHistoricalVector()));
    SszField slashingsField =
        new SszField(
            14,
            BeaconStateFields.SLASHINGS,
            () ->
                SszVectorSchema.create(
                    SszPrimitiveSchemas.UINT64_SCHEMA, specConfig.getEpochsPerSlashingsVector()));
    SszField justificationBitsField =
        new SszField(
            17,
            BeaconStateFields.JUSTIFICATION_BITS,
            () -> SszBitvectorSchema.create(specConfig.getJustificationBitsLength()));
    SszField previousJustifiedCheckpointField =
        new SszField(18, BeaconStateFields.PREVIOUS_JUSTIFIED_CHECKPOINT, Checkpoint.SSZ_SCHEMA);
    SszField currentJustifiedCheckpointField =
        new SszField(19, BeaconStateFields.CURRENT_JUSTIFIED_CHECKPOINT, Checkpoint.SSZ_SCHEMA);
    SszField finalizedCheckpointField =
        new SszField(20, BeaconStateFields.FINALIZED_CHECKPOINT, Checkpoint.SSZ_SCHEMA);

    return List.of(
        GENESIS_TIME_FIELD,
        GENESIS_VALIDATORS_ROOT_FIELD,
        SLOT_FIELD,
        forkField,
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

  @Override
  public String toString() {
    return getSszFieldName();
  }
}
