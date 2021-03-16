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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair;

import com.google.common.base.MoreObjects.ToStringHelper;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.AbstractMutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.TransitionCaches;
import tech.pegasys.teku.ssz.SszData;
import tech.pegasys.teku.ssz.cache.IntCache;
import tech.pegasys.teku.ssz.tree.TreeNode;

class MutableBeaconStateAltairImpl extends AbstractMutableBeaconState<BeaconStateAltairImpl>
    implements MutableBeaconStateAltair, BeaconStateCache, ValidatorStatsAltair {

  MutableBeaconStateAltairImpl(BeaconStateAltairImpl backingImmutableView) {
    super(backingImmutableView);
  }

  MutableBeaconStateAltairImpl(BeaconStateAltairImpl backingImmutableView, boolean builder) {
    super(backingImmutableView, builder);
  }

  @Override
  protected BeaconStateAltairImpl createImmutableBeaconState(
      TreeNode backingNode, IntCache<SszData> viewCache, TransitionCaches transitionCache) {
    return new BeaconStateAltairImpl(getSchema(), backingNode, viewCache, transitionCache);
  }

  @Override
  public BeaconStateAltair commitChanges() {
    return (BeaconStateAltair) super.commitChanges();
  }

  @Override
  public <E1 extends Exception, E2 extends Exception, E3 extends Exception> BeaconState updated(
      Mutator<MutableBeaconState, E1, E2, E3> mutator) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <E1 extends Exception, E2 extends Exception, E3 extends Exception>
      BeaconStateAltair updatedAltair(Mutator<MutableBeaconStateAltair, E1, E2, E3> mutator)
          throws E1, E2, E3 {
    throw new UnsupportedOperationException();
  }

  @Override
  protected void addCustomFields(ToStringHelper stringBuilder) {
    BeaconStateAltairImpl.describeCustomFields(stringBuilder, this);
  }
}
