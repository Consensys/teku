/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.teku.datastructures.state;

import com.google.common.base.MoreObjects;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.Bitvector;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;
import tech.pegasys.teku.ssz.SSZTypes.SSZVector;
import tech.pegasys.teku.ssz.backing.SszContainer;
import tech.pegasys.teku.ssz.backing.SszData;
import tech.pegasys.teku.ssz.backing.cache.IntCache;
import tech.pegasys.teku.ssz.backing.cache.SoftRefIntCache;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.type.CompositeViewType;
import tech.pegasys.teku.ssz.backing.type.ContainerViewType;
import tech.pegasys.teku.ssz.backing.view.SszContainerImpl;

class BeaconStateImpl extends SszContainerImpl implements BeaconState, BeaconStateCache {

  private final TransitionCaches transitionCaches;

  public BeaconStateImpl() {
    super(BeaconState.getSszType());
    transitionCaches = TransitionCaches.createNewEmpty();
  }

  BeaconStateImpl(
      CompositeViewType<?> type,
      TreeNode backingNode,
      IntCache<SszData> cache,
      TransitionCaches transitionCaches) {
    super(type, backingNode, cache);
    this.transitionCaches = transitionCaches;
  }

  BeaconStateImpl(ContainerViewType<? extends SszContainer> type, TreeNode backingNode) {
    super(type, backingNode);
    transitionCaches = TransitionCaches.createNewEmpty();
  }

  public BeaconStateImpl(
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
      Bitvector justification_bits,
      Checkpoint previous_justified_checkpoint,
      Checkpoint current_justified_checkpoint,
      Checkpoint finalized_checkpoint) {

    super(
        BeaconState.getSszType(),
        BeaconState.create(
                genesis_time,
                genesis_validators_root,
                slot,
                fork,
                latest_block_header,
                block_roots,
                state_roots,
                historical_roots,
                eth1_data,
                eth1_data_votes,
                eth1_deposit_index,
                validators,
                balances,
                randao_mixes,
                slashings,
                previous_epoch_attestations,
                current_epoch_attestations,
                justification_bits,
                previous_justified_checkpoint,
                current_justified_checkpoint,
                finalized_checkpoint)
            .getBackingNode());

    transitionCaches = TransitionCaches.createNewEmpty();
  }

  @Override
  public <E1 extends Exception, E2 extends Exception, E3 extends Exception> BeaconState updated(
      Mutator<E1, E2, E3> mutator) throws E1, E2, E3 {
    MutableBeaconState writableCopy = createWritableCopyPriv();
    mutator.mutate(writableCopy);
    return writableCopy.commitChanges();
  }

  @Override
  public int hashCode() {
    return hashCode(this);
  }

  static int hashCode(BeaconState state) {
    return state.hashTreeRoot().slice(0, 4).toInt();
  }

  @Override
  public boolean equals(Object obj) {
    return equals(this, obj);
  }

  static boolean equals(BeaconState state, Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }

    if (state == obj) {
      return true;
    }

    if (!(obj instanceof BeaconState)) {
      return false;
    }

    BeaconState other = (BeaconState) obj;
    return state.hashTreeRoot().equals(other.hashTreeRoot());
  }

  @Override
  public TransitionCaches getTransitionCaches() {
    return transitionCaches;
  }

  static String toString(BeaconState state) {
    return MoreObjects.toStringHelper(state)
        .add("genesis_time", state.getGenesis_time())
        .add("genesis_validators_root", state.getGenesis_validators_root())
        .add("slot", state.getSlot())
        .add("fork", state.getFork())
        .add("latest_block_header", state.getLatest_block_header())
        .add("block_roots", state.getBlock_roots())
        .add("state_roots", state.getState_roots())
        .add("historical_roots", state.getHistorical_roots())
        .add("eth1_data", state.getEth1_data())
        .add("eth1_data_votes", state.getEth1_data_votes())
        .add("eth1_deposit_index", state.getEth1_deposit_index())
        .add("validators", state.getValidators())
        .add("balances", state.getBalances())
        .add("randao_mixes", state.getRandao_mixes())
        .add("slashings", state.getSlashings())
        .add("previous_epoch_attestations", state.getPrevious_epoch_attestations())
        .add("current_epoch_attestations", state.getCurrent_epoch_attestations())
        .add("justification_bits", state.getJustification_bits())
        .add("previous_justified_checkpoint", state.getPrevious_justified_checkpoint())
        .add("current_justified_checkpoint", state.getCurrent_justified_checkpoint())
        .add("finalized_checkpoint", state.getFinalized_checkpoint())
        .toString();
  }

  @Override
  protected IntCache<SszData> createCache() {
    return new SoftRefIntCache<>(super::createCache);
  }

  @Override
  public String toString() {
    return toString(this);
  }

  private MutableBeaconState createWritableCopyPriv() {
    return new MutableBeaconStateImpl(this);
  }
}
