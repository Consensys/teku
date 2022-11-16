/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.eip4844;

import com.google.common.base.MoreObjects;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.cache.IntCache;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.AbstractMutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.TransitionCaches;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.ValidatorStatsAltair;

public class MutableBeaconStateEip4844Impl
    extends AbstractMutableBeaconState<BeaconStateEip4844Impl>
    implements MutableBeaconStateEip4844, BeaconStateCache, ValidatorStatsAltair {

  MutableBeaconStateEip4844Impl(final BeaconStateEip4844Impl backingImmutableView) {
    super(backingImmutableView);
  }

  MutableBeaconStateEip4844Impl(
      final BeaconStateEip4844Impl backingImmutableView, final boolean builder) {
    super(backingImmutableView, builder);
  }

  @Override
  protected BeaconStateEip4844Impl createImmutableBeaconState(
      final TreeNode backingNode,
      final IntCache<SszData> viewCache,
      final TransitionCaches transitionCache) {
    return new BeaconStateEip4844Impl(getSchema(), backingNode, viewCache, transitionCache);
  }

  @Override
  protected void addCustomFields(final MoreObjects.ToStringHelper stringBuilder) {
    BeaconStateEip4844.describeCustomEip4844Fields(stringBuilder, this);
  }

  @Override
  public BeaconStateEip4844 commitChanges() {
    return (BeaconStateEip4844) super.commitChanges();
  }

  @Override
  public MutableBeaconStateEip4844 createWritableCopy() {
    return (MutableBeaconStateEip4844) super.createWritableCopy();
  }
}
