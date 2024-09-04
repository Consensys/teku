/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.eip7732;

import com.google.common.base.MoreObjects;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.cache.IntCache;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.AbstractMutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.SlotCaches;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.TransitionCaches;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.ValidatorStatsAltair;

public class MutableBeaconStateEip7732Impl
    extends AbstractMutableBeaconState<BeaconStateEip7732Impl>
    implements MutableBeaconStateEip7732, BeaconStateCache, ValidatorStatsAltair {

  MutableBeaconStateEip7732Impl(final BeaconStateEip7732Impl backingImmutableView) {
    super(backingImmutableView);
  }

  MutableBeaconStateEip7732Impl(
      final BeaconStateEip7732Impl backingImmutableView, final boolean builder) {
    super(backingImmutableView, builder);
  }

  @Override
  protected BeaconStateEip7732Impl createImmutableBeaconState(
      final TreeNode backingNode,
      final IntCache<SszData> viewCache,
      final TransitionCaches transitionCaches,
      final SlotCaches slotCaches) {
    return new BeaconStateEip7732Impl(
        getSchema(), backingNode, viewCache, transitionCaches, slotCaches);
  }

  @Override
  protected void addCustomFields(final MoreObjects.ToStringHelper stringBuilder) {
    BeaconStateEip7732.describeCustomEip7732Fields(stringBuilder, this);
  }

  @Override
  public BeaconStateEip7732 commitChanges() {
    return (BeaconStateEip7732) super.commitChanges();
  }

  @Override
  public MutableBeaconStateEip7732 createWritableCopy() {
    return (MutableBeaconStateEip7732) super.createWritableCopy();
  }
}
