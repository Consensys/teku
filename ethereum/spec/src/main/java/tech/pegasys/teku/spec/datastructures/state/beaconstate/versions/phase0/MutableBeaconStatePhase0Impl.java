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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.phase0;

import com.google.common.base.MoreObjects.ToStringHelper;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.cache.IntCache;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.AbstractMutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.TransitionCaches;

class MutableBeaconStatePhase0Impl extends AbstractMutableBeaconState<BeaconStatePhase0Impl>
    implements MutableBeaconStatePhase0, BeaconStateCache, ValidatorStatsPhase0 {

  MutableBeaconStatePhase0Impl(BeaconStatePhase0Impl backingImmutableView) {
    super(backingImmutableView);
  }

  MutableBeaconStatePhase0Impl(BeaconStatePhase0Impl backingImmutableView, boolean builder) {
    super(backingImmutableView, builder);
  }

  @Override
  public BeaconStateSchemaPhase0 getBeaconStateSchema() {
    return (BeaconStateSchemaPhase0) getSchema();
  }

  @Override
  protected BeaconStatePhase0Impl createImmutableBeaconState(
      TreeNode backingNode, IntCache<SszData> viewCache, TransitionCaches transitionCache) {
    return new BeaconStatePhase0Impl(getSchema(), backingNode, viewCache, transitionCache);
  }

  @Override
  public BeaconStatePhase0 commitChanges() {
    return (BeaconStatePhase0) super.commitChanges();
  }

  @Override
  public <E1 extends Exception, E2 extends Exception, E3 extends Exception> BeaconState updated(
      Mutator<MutableBeaconState, E1, E2, E3> mutator) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <E1 extends Exception, E2 extends Exception, E3 extends Exception>
      BeaconStatePhase0 updatedPhase0(Mutator<MutableBeaconStatePhase0, E1, E2, E3> mutator)
          throws E1, E2, E3 {
    throw new UnsupportedOperationException();
  }

  @Override
  protected void addCustomFields(ToStringHelper stringBuilder) {
    BeaconStatePhase0Impl.describeCustomFields(stringBuilder, this);
  }
}
