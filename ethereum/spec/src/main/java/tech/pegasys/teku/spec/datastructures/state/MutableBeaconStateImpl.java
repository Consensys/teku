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

package tech.pegasys.teku.spec.datastructures.state;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ssz.backing.SszData;
import tech.pegasys.teku.ssz.backing.cache.IntCache;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.view.SszMutableContainerImpl;

class MutableBeaconStateImpl extends SszMutableContainerImpl
    implements MutableBeaconState, BeaconStateCache {

  static MutableBeaconStateImpl createBuilder() {
    return new MutableBeaconStateImpl(new BeaconStateImpl(), true);
  }

  private final TransitionCaches transitionCaches;
  private final boolean builder;

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
  protected BeaconStateImpl createImmutableSszComposite(
      TreeNode backingNode, IntCache<SszData> viewCache) {
    return new BeaconStateImpl(
        getSchema(),
        backingNode,
        viewCache,
        builder ? TransitionCaches.createNewEmpty() : transitionCaches);
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
  public Bytes32 hashTreeRoot() {
    return commitChanges().hashTreeRoot();
  }

  @Override
  public MutableBeaconState createWritableCopy() {
    return (MutableBeaconState) super.createWritableCopy();
  }

  @Override
  public String toString() {
    return BeaconStateImpl.toString(this);
  }

  @Override
  public boolean equals(Object obj) {
    return BeaconStateImpl.equals(this, obj);
  }

  @Override
  public int hashCode() {
    return BeaconStateImpl.hashCode(this);
  }

  @Override
  public <E1 extends Exception, E2 extends Exception, E3 extends Exception> BeaconState updated(
      Mutator<E1, E2, E3> mutator) {
    throw new UnsupportedOperationException();
  }
}
