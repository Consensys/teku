/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.fulu;

import com.google.common.base.MoreObjects;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.cache.IntCache;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.AbstractMutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.SlotCaches;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.TransitionCaches;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.ValidatorStatsAltair;

public class MutableBeaconStateFuluImpl extends AbstractMutableBeaconState<BeaconStateFuluImpl>
    implements MutableBeaconStateFulu, BeaconStateCache, ValidatorStatsAltair {

  MutableBeaconStateFuluImpl(final BeaconStateFuluImpl backingImmutableView) {
    super(backingImmutableView);
  }

  MutableBeaconStateFuluImpl(
      final BeaconStateFuluImpl backingImmutableView, final boolean builder) {
    super(backingImmutableView, builder);
  }

  @Override
  protected BeaconStateFuluImpl createImmutableBeaconState(
      final TreeNode backingNode,
      final IntCache<SszData> viewCache,
      final TransitionCaches transitionCaches,
      final SlotCaches slotCaches) {
    return new BeaconStateFuluImpl(
        getSchema(), backingNode, viewCache, transitionCaches, slotCaches);
  }

  @Override
  protected void addCustomFields(final MoreObjects.ToStringHelper stringBuilder) {
    BeaconStateFulu.describeCustomFuluFields(stringBuilder, this);
  }

  @Override
  public BeaconStateFulu commitChanges() {
    return (BeaconStateFulu) super.commitChanges();
  }

  @Override
  public MutableBeaconStateFulu createWritableCopy() {
    return (MutableBeaconStateFulu) super.createWritableCopy();
  }
}
