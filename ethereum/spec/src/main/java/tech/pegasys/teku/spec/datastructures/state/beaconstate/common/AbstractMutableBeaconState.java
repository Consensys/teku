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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.common;

import com.google.common.base.MoreObjects.ToStringHelper;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.cache.IntCache;
import tech.pegasys.teku.infrastructure.ssz.impl.SszContainerImpl;
import tech.pegasys.teku.infrastructure.ssz.impl.SszMutableContainerImpl;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;

public abstract class AbstractMutableBeaconState<
        T extends SszContainerImpl & BeaconState & BeaconStateCache>
    extends SszMutableContainerImpl implements MutableBeaconState, BeaconStateCache {

  private final TransitionCaches transitionCaches;
  private final boolean builder;

  protected AbstractMutableBeaconState(T backingImmutableView) {
    this(backingImmutableView, false);
  }

  protected AbstractMutableBeaconState(T backingImmutableView, boolean builder) {
    super(backingImmutableView);
    this.transitionCaches =
        builder ? TransitionCaches.getNoOp() : backingImmutableView.getTransitionCaches().copy();
    this.builder = builder;
  }

  @Override
  public BeaconStateSchema<?, ?> getBeaconStateSchema() {
    return (BeaconStateSchema<?, ?>) getSchema();
  }

  @Override
  protected T createImmutableSszComposite(TreeNode backingNode, IntCache<SszData> viewCache) {
    return createImmutableBeaconState(
        backingNode, viewCache, builder ? TransitionCaches.createNewEmpty() : transitionCaches);
  }

  protected abstract T createImmutableBeaconState(
      TreeNode backingNode, IntCache<SszData> viewCache, TransitionCaches transitionCache);

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
  public <E1 extends Exception, E2 extends Exception, E3 extends Exception> BeaconState updated(
      Mutator<MutableBeaconState, E1, E2, E3> mutator) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int hashCode() {
    return BeaconStateInvariants.hashCode(this);
  }

  @Override
  public boolean equals(Object obj) {
    return BeaconStateInvariants.equals(this, obj);
  }

  @Override
  public String toString() {
    return BeaconStateInvariants.toString(this, this::addCustomFields);
  }

  protected abstract void addCustomFields(ToStringHelper stringBuilder);
}
