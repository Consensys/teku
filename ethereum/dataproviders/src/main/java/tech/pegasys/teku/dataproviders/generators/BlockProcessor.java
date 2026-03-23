/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.dataproviders.generators;

import java.util.Optional;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.StateTransitionException;

class BlockProcessor {
  private final Spec spec;

  BlockProcessor(final Spec spec) {
    this.spec = spec;
  }

  public BeaconState process(
      final BeaconState preState,
      final SignedBeaconBlock block,
      final Optional<SignedExecutionPayloadEnvelope> executionPayload) {
    return executionPayload
        .map(envelope -> replayExecutionPayload(replayBlock(preState, block), envelope))
        .orElseGet(() -> replayBlock(preState, block));
  }

  private BeaconState replayBlock(final BeaconState preState, final SignedBeaconBlock block) {
    try {
      return spec.replayValidatedBlock(preState, block);
    } catch (StateTransitionException e) {
      throw new IllegalStateException(getFailedStateGenerationError(block), e);
    }
  }

  private BeaconState replayExecutionPayload(
      final BeaconState blockState, final SignedExecutionPayloadEnvelope signedEnvelope) {
    try {
      return spec.replayValidatedExecutionPayload(blockState, signedEnvelope);
    } catch (StateTransitionException e) {
      throw new IllegalStateException(
          String.format(
              "Unable to process execution payload at slot %s",
              signedEnvelope.getMessage().getSlot()),
          e);
    }
  }

  private String getFailedStateGenerationError(final SignedBeaconBlock block) {
    return String.format(
        "Unable to produce state for block at slot %s (%s)", block.getSlot(), block.getRoot());
  }
}
