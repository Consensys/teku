/*
 * Copyright 2022 ConsenSys AG.
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

package tech.pegasys.teku.statetransition.forkchoice;

import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.bellatrix.BeaconBlockBodyBellatrix;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.executionengine.ExecutionEngineChannel;
import tech.pegasys.teku.spec.executionengine.PayloadStatus;
import tech.pegasys.teku.spec.logic.versions.bellatrix.helpers.BellatrixTransitionHelpers;
import tech.pegasys.teku.storage.client.RecentChainData;

public class MergeTransitionBlockValidator {

  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final RecentChainData recentChainData;
  private final ExecutionEngineChannel executionEngine;

  public MergeTransitionBlockValidator(
      final Spec spec,
      final RecentChainData recentChainData,
      final ExecutionEngineChannel executionEngine) {
    this.spec = spec;
    this.recentChainData = recentChainData;
    this.executionEngine = executionEngine;
  }

  public SafeFuture<PayloadStatus> verifyTransitionBlock(
      final ExecutionPayloadHeader latestExecutionPayloadHeader, final SignedBeaconBlock block) {
    if (latestExecutionPayloadHeader.isDefault()) {
      // This is the first filled payload, so verify it meets transition conditions
      return verifyTransitionPayload(
          block.getSlot(),
          BeaconBlockBodyBellatrix.required(block.getMessage().getBody()).getExecutionPayload());
    }

    if (transitionBlockNotFinalized()) {
      // The transition block hasn't been finalized yet
      final Optional<Bytes32> maybeTransitionBlockRoot =
          recentChainData
              .getForkChoiceStrategy()
              .orElseThrow()
              // Use the parent root because the block itself won't yet be imported
              .getOptimisticallySyncedTransitionBlockRoot(block.getParentRoot());
      if (maybeTransitionBlockRoot.isPresent()) {
        return retrieveAndVerifyNonFinalizedTransitionBlock(maybeTransitionBlockRoot.get());
      }
    }

    // Possibly while we were looking for a non-finalized transition, it got finalized
    // So verify if there's something not-yet verified
    return verifyFinalizedTransitionBlock();
  }

  private SafeFuture<PayloadStatus> verifyFinalizedTransitionBlock() {
    return recentChainData
        .getStore()
        .getFinalizedOptimisticTransitionPayload()
        .map(
            slotAndPayload ->
                verifyTransitionPayload(
                    slotAndPayload.getSlot(), slotAndPayload.getExecutionPayload()))
        .orElse(SafeFuture.completedFuture(PayloadStatus.VALID));
  }

  private boolean transitionBlockNotFinalized() {
    final BeaconState state = recentChainData.getStore().getLatestFinalized().getState();
    return !spec.atSlot(state.getSlot()).miscHelpers().isMergeTransitionComplete(state);
  }

  private SafeFuture<PayloadStatus> retrieveAndVerifyNonFinalizedTransitionBlock(
      final Bytes32 transitionBlockRoot) {
    return recentChainData
        .retrieveBlockByRoot(transitionBlockRoot)
        .thenCompose(
            block -> {
              if (block.isEmpty()) {
                // We might have finalized the transition block
                return verifyFinalizedTransitionBlock();
              }
              final BeaconBlock transitionBlock = block.get();
              final ExecutionPayload transitionExecutionPayload =
                  BeaconBlockBodyBellatrix.required(transitionBlock.getBody())
                      .getExecutionPayload();
              return verifyTransitionPayload(transitionBlock.getSlot(), transitionExecutionPayload);
            });
  }

  private SafeFuture<PayloadStatus> verifyTransitionPayload(
      final UInt64 slot, final ExecutionPayload executionPayload) {
    final BellatrixTransitionHelpers bellatrixTransitionHelpers =
        spec.atSlot(slot)
            .getBellatrixTransitionHelpers()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Attempting to validate a bellatrix block when spec does not have bellatrix transition helpers"));
    return bellatrixTransitionHelpers
        .validateMergeBlock(executionEngine, executionPayload)
        .exceptionally(
            error -> {
              LOG.error("Error while validating merge block", error);
              return PayloadStatus.failedExecution(error);
            });
  }
}
