/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.artemis.datastructures.state;

import com.google.common.primitives.UnsignedLong;
import jdk.jfr.Label;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;
import tech.pegasys.artemis.util.SSZTypes.SSZMutableList;
import tech.pegasys.artemis.util.SSZTypes.SSZMutableVector;
import tech.pegasys.artemis.util.backing.ViewRead;
import tech.pegasys.artemis.util.backing.tree.TreeNode;
import tech.pegasys.artemis.util.backing.view.ContainerViewWriteImpl;
import tech.pegasys.artemis.util.cache.IntCache;

class MutableBeaconStateImpl extends ContainerViewWriteImpl
    implements MutableBeaconState, BeaconStateCache {

  static MutableBeaconStateImpl createBuilder() {
    return new MutableBeaconStateImpl(new BeaconStateImpl(), true);
  }

  @Label("sos-ignore")
  private final TransitionCaches transitionCaches;

  @Label("sos-ignore")
  private final boolean builder;

  private SSZMutableList<Validator> validators;
  private SSZMutableList<UnsignedLong> balances;
  private SSZMutableVector<Bytes32> blockRoots;
  private SSZMutableVector<Bytes32> stateRoots;
  private SSZMutableList<Bytes32> historicalRoots;
  private SSZMutableList<Eth1Data> eth1DataVotes;
  private SSZMutableVector<Bytes32> randaoMixes;
  private SSZMutableList<PendingAttestation> previousEpochAttestations;
  private SSZMutableList<PendingAttestation> currentEpochAttestations;

  MutableBeaconStateImpl(BeaconStateImpl backingImmutableView) {
    this(backingImmutableView, false);
  }

  MutableBeaconStateImpl(BeaconStateImpl backingImmutableView, boolean builder) {
    super(backingImmutableView);
    this.transitionCaches =
        builder ? TransitionCaches.getNoOp() : backingImmutableView.getTransitionCaches().copy();
    this.builder = builder;
  }

  @Override
  protected BeaconStateImpl createViewRead(TreeNode backingNode, IntCache<ViewRead> viewCache) {
    return new BeaconStateImpl(
        backingNode, viewCache, builder ? TransitionCaches.createNewEmpty() : transitionCaches);
  }

  @Override
  public TransitionCaches getTransitionCaches() {
    return transitionCaches;
  }

  @Override
  public BeaconState commitChanges() {
    return (BeaconState) super.commitChanges();
  }

  @Override
  public Bytes32 hash_tree_root() {
    return commitChanges().hash_tree_root();
  }

  @Override
  public int getSSZFieldCount() {
    throw new UnsupportedOperationException();
  }

  @Override
  public MutableBeaconState createWritableCopy() {
    return (MutableBeaconState) super.createWritableCopy();
  }

  @Override
  public SSZMutableList<Validator> getValidators() {
    return validators != null
        ? validators
        : (validators = MutableBeaconState.super.getValidators());
  }

  @Override
  public SSZMutableList<UnsignedLong> getBalances() {
    return balances != null ? balances : (balances = MutableBeaconState.super.getBalances());
  }

  @Override
  public SSZMutableVector<Bytes32> getBlock_roots() {
    return blockRoots != null
        ? blockRoots
        : (blockRoots = MutableBeaconState.super.getBlock_roots());
  }

  @Override
  public SSZMutableVector<Bytes32> getState_roots() {
    return stateRoots != null
        ? stateRoots
        : (stateRoots = MutableBeaconState.super.getState_roots());
  }

  @Override
  public SSZMutableList<Bytes32> getHistorical_roots() {
    return historicalRoots != null
        ? historicalRoots
        : (historicalRoots = MutableBeaconState.super.getHistorical_roots());
  }

  @Override
  public SSZMutableList<Eth1Data> getEth1_data_votes() {
    return eth1DataVotes != null
        ? eth1DataVotes
        : (eth1DataVotes = MutableBeaconState.super.getEth1_data_votes());
  }

  @Override
  public SSZMutableVector<Bytes32> getRandao_mixes() {
    return randaoMixes != null
        ? randaoMixes
        : (randaoMixes = MutableBeaconState.super.getRandao_mixes());
  }

  @Override
  public SSZMutableList<PendingAttestation> getPrevious_epoch_attestations() {
    return previousEpochAttestations != null
        ? previousEpochAttestations
        : (previousEpochAttestations = MutableBeaconState.super.getPrevious_epoch_attestations());
  }

  @Override
  public SSZMutableList<PendingAttestation> getCurrent_epoch_attestations() {
    return currentEpochAttestations != null
        ? currentEpochAttestations
        : (currentEpochAttestations = MutableBeaconState.super.getCurrent_epoch_attestations());
  }
}
