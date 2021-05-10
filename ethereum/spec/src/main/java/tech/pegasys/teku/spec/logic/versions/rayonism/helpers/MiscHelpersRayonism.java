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

package tech.pegasys.teku.spec.logic.versions.rayonism.helpers;

import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.rayonism.BeaconBlockBodyRayonism;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.rayonism.BeaconStateRayonism;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;

public class MiscHelpersRayonism extends MiscHelpers {

  public MiscHelpersRayonism(final SpecConfig specConfig) {
    super(specConfig);
  }

  public boolean isTransitionCompleted(final BeaconState genericState) {
    final BeaconStateRayonism state = BeaconStateRayonism.required(genericState);
    return !state.getLatest_execution_payload_header().equals(new ExecutionPayloadHeader());
  }

  public boolean isTransitionBlock(final BeaconState genericState, final BeaconBlock block) {
    final BeaconStateRayonism state = BeaconStateRayonism.required(genericState);
    final BeaconBlockBodyRayonism blockBody = BeaconBlockBodyRayonism.required(block.getBody());
    return !isTransitionCompleted(state)
        && !blockBody.getExecution_payload().equals(new ExecutionPayload());
  }

  public boolean isExecutionEnabled(final BeaconState genericState, final BeaconBlock block) {
    return isTransitionCompleted(genericState) || isTransitionBlock(genericState, block);
  }
}
