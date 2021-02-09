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

package tech.pegasys.teku.spec.datastructures.state.genesis;

import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.state.Fork;
import tech.pegasys.teku.datastructures.state.ForkInfo;
import tech.pegasys.teku.datastructures.state.PendingAttestation;
import tech.pegasys.teku.datastructures.state.Validator;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.state.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.BeaconStateFields;
import tech.pegasys.teku.spec.datastructures.state.MutableBeaconState;
import tech.pegasys.teku.ssz.SSZTypes.Bitvector;
import tech.pegasys.teku.ssz.SSZTypes.SSZBackingList;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;
import tech.pegasys.teku.ssz.SSZTypes.SSZVector;
import tech.pegasys.teku.ssz.backing.SszData;
import tech.pegasys.teku.ssz.backing.cache.IntCache;
import tech.pegasys.teku.ssz.backing.schema.SszContainerSchema;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.view.SszContainerImpl;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives;

class BeaconStateGenesisImpl extends SszContainerImpl implements BeaconStateGenesis {
  private final SszContainerSchema<BeaconStateGenesis> schema;

  public BeaconStateGenesisImpl(SszContainerSchema<BeaconStateGenesis> schema) {
    super(schema);
    this.schema = schema;
  }

  BeaconStateGenesisImpl(
      SszContainerSchema<BeaconStateGenesis> type, TreeNode backingNode, IntCache<SszData> cache) {
    super(type, backingNode, cache);
    this.schema = type;
  }

  BeaconStateGenesisImpl(SszContainerSchema<BeaconStateGenesis> type, TreeNode backingNode) {
    super(type, backingNode);
    this.schema = type;
  }

  // Example getters

  @Override
  public UInt64 getGenesis_time() {
    final int fieldIndex = schema.getFieldIndex(BeaconStateFields.GENESIS_TIME.name());
    return ((SszPrimitives.SszUInt64) get(fieldIndex)).get();
  }

  @Override
  public SSZList<PendingAttestation> getPrevious_epoch_attestations() {
    final int fieldIndex =
        schema.getFieldIndex(BeaconStateFields.PREVIOUS_EPOCH_ATTESTATIONS.name());
    return new SSZBackingList<>(
        PendingAttestation.class, getAny(fieldIndex), Function.identity(), Function.identity());
  }

  @Override
  public SSZList<PendingAttestation> getCurrent_epoch_attestations() {
    return null;
  }

  @Override
  public BeaconStateGenesis updatedGenesis(final Consumer<MutableBeaconStateGenesis> updater) {
    return null;
  }

  // TODO implement other methods below

  @Override
  public Bytes32 getGenesis_validators_root() {
    return null;
  }

  @Override
  public UInt64 getSlot() {
    return null;
  }

  @Override
  public Fork getFork() {
    return null;
  }

  @Override
  public ForkInfo getForkInfo() {
    return null;
  }

  @Override
  public BeaconBlockHeader getLatest_block_header() {
    return null;
  }

  @Override
  public SSZVector<Bytes32> getBlock_roots() {
    return null;
  }

  @Override
  public SSZVector<Bytes32> getState_roots() {
    return null;
  }

  @Override
  public SSZList<Bytes32> getHistorical_roots() {
    return null;
  }

  @Override
  public Eth1Data getEth1_data() {
    return null;
  }

  @Override
  public SSZList<Eth1Data> getEth1_data_votes() {
    return null;
  }

  @Override
  public UInt64 getEth1_deposit_index() {
    return null;
  }

  @Override
  public SSZList<Validator> getValidators() {
    return null;
  }

  @Override
  public SSZList<UInt64> getBalances() {
    return null;
  }

  @Override
  public SSZVector<Bytes32> getRandao_mixes() {
    return null;
  }

  @Override
  public SSZVector<UInt64> getSlashings() {
    return null;
  }

  @Override
  public Bitvector getJustification_bits() {
    return null;
  }

  @Override
  public Checkpoint getPrevious_justified_checkpoint() {
    return null;
  }

  @Override
  public Checkpoint getCurrent_justified_checkpoint() {
    return null;
  }

  @Override
  public Checkpoint getFinalized_checkpoint() {
    return null;
  }

  @Override
  public BeaconState updated(final Consumer<MutableBeaconState> updater) {
    return null;
  }
}
