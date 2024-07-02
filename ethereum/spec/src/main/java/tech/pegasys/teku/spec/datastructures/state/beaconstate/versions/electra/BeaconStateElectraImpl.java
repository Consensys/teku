/*
 * Copyright Consensys Software Inc., 2024
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
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszProfileSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateStableSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.AbstractBeaconStateProfile;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.SlotCaches;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.TransitionCaches;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.ValidatorStatsAltair;

public class BeaconStateElectraImpl extends AbstractBeaconStateProfile<MutableBeaconStateElectra>
    implements BeaconStateElectra, BeaconStateCache, ValidatorStatsAltair {

  BeaconStateElectraImpl(
      final BeaconStateStableSchema<BeaconStateElectra, MutableBeaconStateElectra> schema) {
    super(schema);
  }

  BeaconStateElectraImpl(
      final AbstractSszProfileSchema<?> type,
      final TreeNode backingNode,
      final IntCache<SszData> cache,
      final TransitionCaches transitionCaches,
      final SlotCaches slotCaches) {
    super(type, backingNode, cache, transitionCaches, slotCaches);
  }

  BeaconStateElectraImpl(final AbstractSszProfileSchema<?> type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  @Override
  public BeaconStateSchemaElectra getBeaconStateSchema() {
    return (BeaconStateSchemaElectra) getSchema();
  }

  @Override
  public MutableBeaconStateElectra createWritableCopy() {
    return new MutableBeaconStateElectraImpl(this);
  }

  @Override
  protected void describeCustomFields(final MoreObjects.ToStringHelper stringBuilder) {
    BeaconStateElectra.describeCustomElectraFields(stringBuilder, this);
  }
}
