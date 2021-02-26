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

import java.util.stream.Collectors;
import java.util.stream.Stream;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.PendingAttestation;
import tech.pegasys.teku.spec.datastructures.state.Validator;
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

  public static final SszField GENESIS_TIME_FIELD =
      new SszField(0, BeaconStateFields.GENESIS_TIME.name(), SszPrimitiveSchemas.UINT64_SCHEMA);
  public static final SszField GENESIS_VALIDATORS_ROOT_FIELD =
      new SszField(
          1, BeaconStateFields.GENESIS_VALIDATORS_ROOT.name(), SszPrimitiveSchemas.BYTES32_SCHEMA);
  public static final SszField SLOT_FIELD =
      new SszField(2, BeaconStateFields.SLOT.name(), SszPrimitiveSchemas.UINT64_SCHEMA);
  public static final SszField FORK_FIELD =
      new SszField(3, BeaconStateFields.FORK.name(), Fork.SSZ_SCHEMA);
  public static final SszField LATEST_BLOCK_HEADER_FIELD =
      new SszField(4, BeaconStateFields.LATEST_BLOCK_HEADER.name(), BeaconBlockHeader.SSZ_SCHEMA);
  public static final SszField BLOCK_ROOTS_FIELD =
      new SszField(
          5,
          BeaconStateFields.BLOCK_ROOTS.name(),
          () ->
              SszVectorSchema.create(
                  SszPrimitiveSchemas.BYTES32_SCHEMA, Constants.SLOTS_PER_HISTORICAL_ROOT));
  public static final SszField STATE_ROOTS_FIELD =
      new SszField(
          6,
          BeaconStateFields.STATE_ROOTS.name(),
          () ->
              SszVectorSchema.create(
                  SszPrimitiveSchemas.BYTES32_SCHEMA, Constants.SLOTS_PER_HISTORICAL_ROOT));
  public static final SszField HISTORICAL_ROOTS_FIELD =
      new SszField(
          7,
          BeaconStateFields.HISTORICAL_ROOTS.name(),
          () ->
              SszListSchema.create(
                  SszPrimitiveSchemas.BYTES32_SCHEMA, Constants.HISTORICAL_ROOTS_LIMIT));
  public static final SszField ETH1_DATA_FIELD =
      new SszField(8, BeaconStateFields.ETH1_DATA.name(), Eth1Data.SSZ_SCHEMA);
  public static final SszField ETH1_DATA_VOTES_FIELD =
      new SszField(
          9,
          BeaconStateFields.ETH1_DATA_VOTES.name(),
          () ->
              SszListSchema.create(
                  Eth1Data.SSZ_SCHEMA,
                  Constants.EPOCHS_PER_ETH1_VOTING_PERIOD * Constants.SLOTS_PER_EPOCH));
  public static final SszField ETH1_DEPOSIT_INDEX_FIELD =
      new SszField(
          10, BeaconStateFields.ETH1_DEPOSIT_INDEX.name(), SszPrimitiveSchemas.UINT64_SCHEMA);
  public static final SszField VALIDATORS_FIELD =
      new SszField(
          11,
          BeaconStateFields.VALIDATORS.name(),
          () ->
              SszListSchema.create(
                  Validator.SSZ_SCHEMA,
                  Constants.VALIDATOR_REGISTRY_LIMIT,
                  SszSchemaHints.sszSuperNode(8)));
  public static final SszField BALANCES_FIELD =
      new SszField(
          12,
          BeaconStateFields.BALANCES.name(),
          () ->
              SszListSchema.create(
                  SszPrimitiveSchemas.UINT64_SCHEMA, Constants.VALIDATOR_REGISTRY_LIMIT));
  public static final SszField RANDAO_MIXES_FIELD =
      new SszField(
          13,
          BeaconStateFields.RANDAO_MIXES.name(),
          () ->
              SszVectorSchema.create(
                  SszPrimitiveSchemas.BYTES32_SCHEMA, Constants.EPOCHS_PER_HISTORICAL_VECTOR));
  public static final SszField SLASHINGS_FIELD =
      new SszField(
          14,
          BeaconStateFields.SLASHINGS.name(),
          () ->
              SszVectorSchema.create(
                  SszPrimitiveSchemas.UINT64_SCHEMA, Constants.EPOCHS_PER_SLASHINGS_VECTOR));
  public static final SszField PREVIOUS_EPOCH_ATTESTATIONS_FIELD =
      new SszField(
          15,
          BeaconStateFields.PREVIOUS_EPOCH_ATTESTATIONS.name(),
          () ->
              SszListSchema.create(
                  PendingAttestation.SSZ_SCHEMA,
                  Constants.MAX_ATTESTATIONS * Constants.SLOTS_PER_EPOCH));
  public static final SszField CURRENT_EPOCH_ATTESTATIONS_FIELD =
      new SszField(
          16,
          BeaconStateFields.CURRENT_EPOCH_ATTESTATIONS.name(),
          () ->
              SszListSchema.create(
                  PendingAttestation.SSZ_SCHEMA,
                  Constants.MAX_ATTESTATIONS * Constants.SLOTS_PER_EPOCH));
  public static final SszField JUSTIFICATION_BITS_FIELD =
      new SszField(
          17,
          BeaconStateFields.JUSTIFICATION_BITS.name(),
          () -> SszBitvectorSchema.create(Constants.JUSTIFICATION_BITS_LENGTH));
  public static final SszField PREVIOUS_JUSTIFIED_CHECKPOINT_FIELD =
      new SszField(
          18, BeaconStateFields.PREVIOUS_JUSTIFIED_CHECKPOINT.name(), Checkpoint.SSZ_SCHEMA);
  public static final SszField CURRENT_JUSTIFIED_CHECKPOINT_FIELD =
      new SszField(
          19, BeaconStateFields.CURRENT_JUSTIFIED_CHECKPOINT.name(), Checkpoint.SSZ_SCHEMA);
  public static final SszField FINALIZED_CHECKPOINT_FIELD =
      new SszField(20, BeaconStateFields.FINALIZED_CHECKPOINT.name(), Checkpoint.SSZ_SCHEMA);

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
