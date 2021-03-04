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

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateInvariants.GENESIS_TIME_FIELD;
import static tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateInvariants.GENESIS_VALIDATORS_ROOT_FIELD;
import static tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateInvariants.SLOT_FIELD;

import java.util.List;
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
import tech.pegasys.teku.ssz.backing.schema.SszContainerSchema;
import tech.pegasys.teku.ssz.backing.schema.SszListSchema;
import tech.pegasys.teku.ssz.backing.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.ssz.backing.schema.SszSchemaHints;
import tech.pegasys.teku.ssz.backing.schema.SszVectorSchema;
import tech.pegasys.teku.ssz.backing.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.ssz.sos.SszField;

public interface BeaconStateSchema<T extends BeaconState, TMutable extends MutableBeaconState>
    extends SszContainerSchema<T> {
  TMutable createBuilder();

  T createEmpty();

  T create(
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
      Checkpoint finalized_checkpoint);

  static List<SszField> getCommonFields(final SpecConstants specConstants) {
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

  static void validateFields(final List<SszField> fields) {
    for (int i = 0; i < fields.size(); i++) {
      final int fieldIndex = fields.get(i).getIndex();
      checkArgument(
          fieldIndex == i,
          "BeaconStateSchema fields must be ordered and contiguous.  Encountered unexpected index %s at fields element %s",
          fieldIndex,
          i);
    }

    final List<SszField> invariantFields = BeaconStateInvariants.getInvariantFields();
    checkArgument(
        fields.size() >= invariantFields.size(),
        "Must provide at least %s fields",
        invariantFields.size());
    for (SszField invariantField : invariantFields) {
      final int invariantIndex = invariantField.getIndex();
      final SszField actualField = fields.get(invariantIndex);
      checkArgument(
          actualField.equals(invariantField),
          "Expected invariant field '%s' at index %s, but got '%s'",
          invariantField.getName(),
          invariantIndex,
          actualField.getName());
    }
  }
}
