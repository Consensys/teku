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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra;

import com.google.common.base.MoreObjects;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.cache.IntCache;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszProfileSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateStableSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.AbstractMutableStableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.SlotCaches;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.TransitionCaches;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.ValidatorStatsAltair;

public class MutableBeaconStateElectraImpl
    extends AbstractMutableStableBeaconState<BeaconStateElectraImpl>
    implements MutableBeaconStateElectra, BeaconStateCache, ValidatorStatsAltair {

  MutableBeaconStateElectraImpl(final BeaconStateElectraImpl backingImmutableView) {
    super(backingImmutableView);
  }

  MutableBeaconStateElectraImpl(
      final BeaconStateElectraImpl backingImmutableView, final boolean builder) {
    super(backingImmutableView, builder);
  }

  @Override
  public BeaconStateStableSchema<? extends BeaconState, ? extends MutableBeaconState>
      getBeaconStateSchema() {
    return (BeaconStateStableSchema<? extends BeaconState, ? extends MutableBeaconState>)
        super.getBeaconStateSchema();
  }

  @Override
  protected BeaconStateElectraImpl createImmutableBeaconState(
      final TreeNode backingNode,
      final IntCache<SszData> viewCache,
      final TransitionCaches transitionCaches,
      final SlotCaches slotCaches) {
    return new BeaconStateElectraImpl(
        (AbstractSszProfileSchema<?>) getSchema(),
        backingNode,
        viewCache,
        transitionCaches,
        slotCaches);
  }

  @Override
  protected void addCustomFields(final MoreObjects.ToStringHelper stringBuilder) {
    BeaconStateElectra.describeCustomElectraFields(stringBuilder, this);
  }

  @Override
  public BeaconStateElectra commitChanges() {
    return (BeaconStateElectra) super.commitChanges();
  }

  @Override
  public MutableBeaconStateElectra createWritableCopy() {
    return (MutableBeaconStateElectra) super.createWritableCopy();
  }

  @Override
  public boolean isFieldActive(final int index) {
    return backingStableContainerBaseView.isFieldActive(index);
  }

  @Override
  public SszBitvector getActiveFields() {
    return backingStableContainerBaseView.getActiveFields();
  }
}
