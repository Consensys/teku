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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.genesis;

import tech.pegasys.teku.spec.datastructures.state.beaconstate.AbstractMutableBeaconStateImpl;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.TransitionCaches;
import tech.pegasys.teku.ssz.backing.SszData;
import tech.pegasys.teku.ssz.backing.cache.IntCache;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;

class MutableBeaconStateGenesisImpl extends AbstractMutableBeaconStateImpl<BeaconStateGenesisImpl>
    implements MutableBeaconState, BeaconStateCache {

  MutableBeaconStateGenesisImpl(BeaconStateGenesisImpl backingImmutableView) {
    super(backingImmutableView);
  }

  MutableBeaconStateGenesisImpl(BeaconStateGenesisImpl backingImmutableView, boolean builder) {
    super(backingImmutableView, builder);
  }

  @Override
  protected BeaconStateGenesisImpl createImmutableBeaconState(
      TreeNode backingNode, IntCache<SszData> viewCache, TransitionCaches transitionCache) {
    return new BeaconStateGenesisImpl(getSchema(), backingNode, viewCache, transitionCache);
  }

  @Override
  public String toString() {
    return BeaconStateGenesisImpl.toString(this);
  }

  @Override
  public boolean equals(Object obj) {
    return BeaconStateGenesisImpl.equals(this, obj);
  }

  @Override
  public int hashCode() {
    return BeaconStateGenesisImpl.hashCode(this);
  }

  @Override
  public <E1 extends Exception, E2 extends Exception, E3 extends Exception> BeaconState updated(
      Mutator<E1, E2, E3> mutator) {
    throw new UnsupportedOperationException();
  }
}
