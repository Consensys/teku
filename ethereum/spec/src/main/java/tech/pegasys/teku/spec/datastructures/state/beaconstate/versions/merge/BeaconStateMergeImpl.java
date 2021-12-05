/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.merge;

import com.google.common.base.MoreObjects.ToStringHelper;
import tech.pegasys.teku.infrastructure.ssz.SszContainer;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.cache.IntCache;
import tech.pegasys.teku.infrastructure.ssz.schema.SszCompositeSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszContainerSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.AbstractBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.TransitionCaches;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.ValidatorStatsAltair;

class BeaconStateMergeImpl extends AbstractBeaconState<MutableBeaconStateMerge>
    implements BeaconStateMerge, BeaconStateCache, ValidatorStatsAltair {

  BeaconStateMergeImpl(final BeaconStateSchema<BeaconStateMerge, MutableBeaconStateMerge> schema) {
    super(schema);
  }

  BeaconStateMergeImpl(
      SszCompositeSchema<?> type,
      TreeNode backingNode,
      IntCache<SszData> cache,
      TransitionCaches transitionCaches) {
    super(type, backingNode, cache, transitionCaches);
  }

  BeaconStateMergeImpl(
      AbstractSszContainerSchema<? extends SszContainer> type, TreeNode backingNode) {
    super(type, backingNode);
  }

  @Override
  public BeaconStateSchemaMerge getBeaconStateSchema() {
    return (BeaconStateSchemaMerge) getSchema();
  }

  @Override
  public MutableBeaconStateMerge createWritableCopy() {
    return new MutableBeaconStateMergeImpl(this);
  }

  @Override
  protected void describeCustomFields(ToStringHelper stringBuilder) {
    describeCustomFields(stringBuilder, this);
  }

  static void describeCustomFields(ToStringHelper stringBuilder, final BeaconStateMerge state) {
    stringBuilder.add("execution_payload_header", state.getLatestExecutionPayloadHeader());
  }
}
