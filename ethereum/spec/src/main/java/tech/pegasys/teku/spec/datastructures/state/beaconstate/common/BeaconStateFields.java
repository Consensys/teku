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
import tech.pegasys.teku.spec.constants.SpecConstants;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.ssz.backing.schema.SszListSchema;
import tech.pegasys.teku.ssz.backing.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.ssz.backing.schema.SszSchemaHints;
import tech.pegasys.teku.ssz.backing.schema.SszVectorSchema;
import tech.pegasys.teku.ssz.backing.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.ssz.sos.SszField;

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
  FINALIZED_CHECKPOINT;

  static List<SszField> getCommonFields(final SpecConstants specConstants) {
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
