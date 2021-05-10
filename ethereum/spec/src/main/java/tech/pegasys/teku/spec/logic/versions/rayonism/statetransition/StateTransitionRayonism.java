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

package tech.pegasys.teku.spec.logic.versions.rayonism.statetransition;

import tech.pegasys.teku.spec.cache.IndexedAttestationCache;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.rayonism.BeaconBlockBodyRayonism;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.logic.common.statetransition.StateTransition;
import tech.pegasys.teku.spec.logic.common.statetransition.blockvalidator.BlockValidator;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.EpochProcessor;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.BlockProcessingException;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.logic.versions.rayonism.block.BlockProcessorRayonism;
import tech.pegasys.teku.spec.logic.versions.rayonism.helpers.BeaconStateAccessorsRayonism;
import tech.pegasys.teku.spec.logic.versions.rayonism.helpers.MiscHelpersRayonism;

public class StateTransitionRayonism extends StateTransition {

  private final BlockProcessorRayonism blockProcessorRayonism;
  private final MiscHelpersRayonism miscHelpersRayonism;

  protected StateTransitionRayonism(
      final SpecConfig specConfig,
      final BlockProcessorRayonism blockProcessorRayonism,
      final MiscHelpersRayonism miscHelpersRayonism,
      final EpochProcessor epochProcessor,
      final BlockValidator blockValidator) {
    super(specConfig, blockProcessorRayonism, epochProcessor, blockValidator);
    this.blockProcessorRayonism = blockProcessorRayonism;
    this.miscHelpersRayonism = miscHelpersRayonism;
  }

  public static StateTransitionRayonism create(
      final SpecConfig specConfig,
      final BlockProcessorRayonism blockProcessorRayonism,
      final MiscHelpersRayonism miscHelpersRayonism,
      final EpochProcessor epochProcessor,
      final BeaconStateUtil beaconStateUtil,
      final BeaconStateAccessorsRayonism beaconStateAccessors) {
    final BlockValidator blockValidator =
        BlockValidator.standard(
            specConfig, beaconStateUtil, blockProcessorRayonism, beaconStateAccessors);
    return new StateTransitionRayonism(
        specConfig, blockProcessorRayonism, miscHelpersRayonism, epochProcessor, blockValidator);
  }

  @Override
  protected void processBlock(
      final MutableBeaconState state,
      final BeaconBlock block,
      IndexedAttestationCache indexedAttestationCache)
      throws BlockProcessingException {
    super.processBlock(state, block, indexedAttestationCache);

    final BeaconBlockBodyRayonism beaconBlockBodyRayonism =
        BeaconBlockBodyRayonism.required(block.getBody());

    // Pre-merge, skip processing
    if (miscHelpersRayonism.isExecutionEnabled(state, block)) {
      blockProcessorRayonism.processExecutionPayload(
          state.toMutableVersionRayonism().orElseThrow(),
          beaconBlockBodyRayonism.getExecution_payload());
    }
  }
}
