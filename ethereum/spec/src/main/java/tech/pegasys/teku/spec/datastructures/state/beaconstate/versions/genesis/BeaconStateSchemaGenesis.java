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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.genesis;

import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
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
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;
import tech.pegasys.teku.ssz.SSZTypes.SSZVector;
import tech.pegasys.teku.ssz.backing.collections.SszBitvector;
import tech.pegasys.teku.ssz.backing.schema.AbstractSszContainerSchema;
import tech.pegasys.teku.ssz.backing.schema.SszListSchema;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.sos.SszField;

public class BeaconStateSchemaGenesis extends AbstractSszContainerSchema<BeaconStateGenesis>
    implements BeaconStateSchema<BeaconStateGenesis, MutableBeaconStateGenesis> {

  @VisibleForTesting
  BeaconStateSchemaGenesis(final List<SszField> fields) {
    super(
        "BeaconStateGenesis",
        fields.stream()
            .map(f -> namedSchema(f.getName(), f.getSchema().get()))
            .collect(Collectors.toList()));
    BeaconStateSchema.validateFields(fields);
  }

  public static BeaconStateSchema<BeaconStateGenesis, MutableBeaconStateGenesis> create(
      final SpecConstants specConstants) {
    return new BeaconStateSchemaGenesis(getFields(specConstants));
  }

  private static List<SszField> getFields(final SpecConstants specConstants) {
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

    final ArrayList<SszField> fields =
        new ArrayList<>(BeaconStateSchema.getCommonFields(specConstants));
    fields.add(previousEpochAttestationsField);
    fields.add(currentEpochAttestationsField);

    fields.sort(Comparator.comparing(SszField::getIndex));
    return fields;
  }

  @Override
  public BeaconStateGenesis createFromBackingNode(TreeNode node) {
    return new BeaconStateGenesisImpl(this, node);
  }

  @Override
  public MutableBeaconStateGenesis createBuilder() {
    return new MutableBeaconStateGenesisImpl(createEmptyBeaconStateImpl(), true);
  }

  @Override
  public BeaconStateGenesis createEmpty() {
    return createEmptyBeaconStateImpl();
  }

  @Override
  public BeaconStateGenesis create(

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
        .updatedGenesis(
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

  private BeaconStateGenesisImpl createEmptyBeaconStateImpl() {
    return new BeaconStateGenesisImpl(this);
  }
}
