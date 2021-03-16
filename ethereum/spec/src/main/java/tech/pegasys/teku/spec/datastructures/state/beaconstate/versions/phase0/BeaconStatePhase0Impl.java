/*
 * Copyright 2019 ConsenSys AG.
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
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.AbstractBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.TransitionCaches;
import tech.pegasys.teku.ssz.backing.SszContainer;
import tech.pegasys.teku.ssz.backing.SszData;
import tech.pegasys.teku.ssz.backing.cache.IntCache;
import tech.pegasys.teku.ssz.backing.schema.SszCompositeSchema;
import tech.pegasys.teku.ssz.backing.schema.impl.AbstractSszContainerSchema;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;

class BeaconStatePhase0Impl extends AbstractBeaconState<MutableBeaconStatePhase0>
    implements BeaconStatePhase0, BeaconStateCache, ValidatorStatsPhase0 {

  BeaconStatePhase0Impl(
      final BeaconStateSchema<BeaconStatePhase0, MutableBeaconStatePhase0> schema) {
    super(schema);
  }

  BeaconStatePhase0Impl(
      SszCompositeSchema<?> type,
      TreeNode backingNode,
      IntCache<SszData> cache,
      TransitionCaches transitionCaches) {
    super(type, backingNode, cache, transitionCaches);
  }

  BeaconStatePhase0Impl(
      AbstractSszContainerSchema<? extends SszContainer> type, TreeNode backingNode) {
    super(type, backingNode);
  }

  @Override
  public <E1 extends Exception, E2 extends Exception, E3 extends Exception>
      BeaconStatePhase0 updatedPhase0(Mutator<MutableBeaconStatePhase0, E1, E2, E3> mutator)
          throws E1, E2, E3 {
    MutableBeaconStatePhase0 writableCopy = createWritableCopyPriv();
    mutator.mutate(writableCopy);
    return writableCopy.commitChanges();
  }

  @Override
  protected MutableBeaconStatePhase0 createWritableCopyPriv() {
    return new MutableBeaconStatePhase0Impl(this);
  }

  @Override
  protected void describeCustomFields(ToStringHelper stringBuilder) {
    describeCustomFields(stringBuilder, this);
  }

  static void describeCustomFields(ToStringHelper stringBuilder, final BeaconStatePhase0 state) {
    stringBuilder
        .add("previous_epoch_attestations", state.getPrevious_epoch_attestations())
        .add("current_epoch_attestations", state.getCurrent_epoch_attestations());
  }
}
