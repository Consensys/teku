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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.eip7594;

import com.google.common.base.MoreObjects;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.cache.IntCache;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.AbstractMutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.SlotCaches;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.TransitionCaches;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.ValidatorStatsAltair;

public class MutableBeaconStateEip7594Impl
    extends AbstractMutableBeaconState<BeaconStateEip7594Impl>
    implements MutableBeaconStateEip7594, BeaconStateCache, ValidatorStatsAltair {

  MutableBeaconStateEip7594Impl(final BeaconStateEip7594Impl backingImmutableView) {
    super(backingImmutableView);
  }

  MutableBeaconStateEip7594Impl(
      final BeaconStateEip7594Impl backingImmutableView, final boolean builder) {
    super(backingImmutableView, builder);
  }

  @Override
  protected BeaconStateEip7594Impl createImmutableBeaconState(
      final TreeNode backingNode,
      final IntCache<SszData> viewCache,
      final TransitionCaches transitionCaches,
      final SlotCaches slotCaches) {
    return new BeaconStateEip7594Impl(
        getSchema(), backingNode, viewCache, transitionCaches, slotCaches);
  }

  @Override
  protected void addCustomFields(final MoreObjects.ToStringHelper stringBuilder) {
    BeaconStateEip7594.describeCustomEip7594Fields(stringBuilder, this);
  }

  @Override
  public BeaconStateEip7594 commitChanges() {
    return (BeaconStateEip7594) super.commitChanges();
  }

  @Override
  public MutableBeaconStateEip7594 createWritableCopy() {
    return (MutableBeaconStateEip7594) super.createWritableCopy();
  }
}
