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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.genesis;

import com.google.common.base.MoreObjects;
import java.util.Objects;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.TransitionCaches;
import tech.pegasys.teku.ssz.backing.SszContainer;
import tech.pegasys.teku.ssz.backing.SszData;
import tech.pegasys.teku.ssz.backing.cache.IntCache;
import tech.pegasys.teku.ssz.backing.cache.SoftRefIntCache;
import tech.pegasys.teku.ssz.backing.schema.AbstractSszContainerSchema;
import tech.pegasys.teku.ssz.backing.schema.SszCompositeSchema;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.view.SszContainerImpl;

class BeaconStateGenesisImpl extends SszContainerImpl
    implements BeaconStateGenesis, BeaconStateCache {

  private final TransitionCaches transitionCaches;

  public BeaconStateGenesisImpl(
      final BeaconStateSchema<BeaconStateGenesis, MutableBeaconStateGenesis> schema) {
    super(schema);
    transitionCaches = TransitionCaches.createNewEmpty();
  }

  BeaconStateGenesisImpl(
      SszCompositeSchema<?> type,
      TreeNode backingNode,
      IntCache<SszData> cache,
      TransitionCaches transitionCaches) {
    super(type, backingNode, cache);
    this.transitionCaches = transitionCaches;
  }

  BeaconStateGenesisImpl(
      AbstractSszContainerSchema<? extends SszContainer> type, TreeNode backingNode) {
    super(type, backingNode);
    transitionCaches = TransitionCaches.createNewEmpty();
  }

  @Override
  public <E1 extends Exception, E2 extends Exception, E3 extends Exception> BeaconState updated(
      Mutator<MutableBeaconState, E1, E2, E3> mutator) throws E1, E2, E3 {
    MutableBeaconStateGenesis writableCopy = createWritableCopyPriv();
    mutator.mutate(writableCopy);
    return writableCopy.commitChanges();
  }

  @Override
  public <E1 extends Exception, E2 extends Exception, E3 extends Exception>
      BeaconStateGenesis updatedGenesis(Mutator<MutableBeaconStateGenesis, E1, E2, E3> mutator)
          throws E1, E2, E3 {
    MutableBeaconStateGenesis writableCopy = createWritableCopyPriv();
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

  static String toString(BeaconStateGenesis state) {
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

  private MutableBeaconStateGenesis createWritableCopyPriv() {
    return new MutableBeaconStateGenesisImpl(this);
  }
}
