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

package tech.pegasys.teku.spec.logic.versions.merge.statetransition;

import tech.pegasys.teku.spec.cache.IndexedAttestationCache;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.logic.common.statetransition.StateTransition;
import tech.pegasys.teku.spec.logic.common.statetransition.blockvalidator.BlockValidator;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.EpochProcessor;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.BlockProcessingException;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.logic.versions.merge.block.BlockProcessorMerge;
import tech.pegasys.teku.spec.logic.versions.merge.helpers.BeaconStateAccessorsMerge;

public class StateTransitionMerge extends StateTransition {

  private final BlockProcessorMerge blockProcessorMerge;

  protected StateTransitionMerge(
      final SpecConfig specConfig,
      final BlockProcessorMerge blockProcessorMerge,
      final EpochProcessor epochProcessor,
      final BlockValidator blockValidator) {
    super(specConfig, blockProcessorMerge, epochProcessor, blockValidator);
    this.blockProcessorMerge = blockProcessorMerge;
  }

  public static StateTransitionMerge create(
      final SpecConfig specConfig,
      final BlockProcessorMerge blockProcessorMerge,
      final EpochProcessor epochProcessor,
      final BeaconStateUtil beaconStateUtil,
      final BeaconStateAccessorsMerge beaconStateAccessors) {
    final BlockValidator blockValidator =
        BlockValidator.standard(
            specConfig, beaconStateUtil, blockProcessorMerge, beaconStateAccessors);
    return new StateTransitionMerge(
        specConfig, blockProcessorMerge, epochProcessor, blockValidator);
  }

  @Override
  protected void processBlock(
      final MutableBeaconState state,
      final BeaconBlock block,
      IndexedAttestationCache indexedAttestationCache)
      throws BlockProcessingException {
    super.processBlock(state, block, indexedAttestationCache);
    blockProcessorMerge.processExecutionPayload(
        state.toMutableVersionMerge().orElseThrow(), block.getBody());
  }
}
