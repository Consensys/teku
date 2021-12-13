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

package tech.pegasys.teku.spec.logic.versions.merge.helpers;

import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.merge.BeaconBlockBodyMerge;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.merge.BeaconStateMerge;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.MiscHelpersAltair;

public class MiscHelpersMerge extends MiscHelpersAltair {

  public MiscHelpersMerge(final SpecConfig specConfig) {
    super(specConfig);
  }

  @Override
  public boolean isMergeTransitionComplete(final BeaconState genericState) {
    final BeaconStateMerge state = BeaconStateMerge.required(genericState);
    return !state.getLatestExecutionPayloadHeader().isDefault();
  }

  public boolean isMergeTransitionBlock(final BeaconState genericState, final BeaconBlock block) {
    final BeaconStateMerge state = BeaconStateMerge.required(genericState);
    final BeaconBlockBodyMerge blockBody = BeaconBlockBodyMerge.required(block.getBody());
    return !isMergeTransitionComplete(state) && !blockBody.getExecutionPayload().isDefault();
  }

  public boolean isExecutionEnabled(final BeaconState genericState, final BeaconBlock block) {
    return isMergeTransitionBlock(genericState, block) || isMergeTransitionComplete(genericState);
  }
}
