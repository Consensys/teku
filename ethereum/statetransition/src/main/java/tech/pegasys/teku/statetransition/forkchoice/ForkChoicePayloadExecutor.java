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

package tech.pegasys.teku.statetransition.forkchoice;

import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.bellatrix.BeaconBlockBodyBellatrix;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.executionengine.ExecutionEngineChannel;
import tech.pegasys.teku.spec.executionengine.PayloadStatus;
import tech.pegasys.teku.spec.logic.versions.bellatrix.block.OptimisticExecutionPayloadExecutor;
import tech.pegasys.teku.spec.logic.versions.bellatrix.helpers.BellatrixTransitionHelpers;
import tech.pegasys.teku.storage.client.RecentChainData;

class ForkChoicePayloadExecutor implements OptimisticExecutionPayloadExecutor {
  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final SignedBeaconBlock block;
  private final RecentChainData recentChainData;
  private final ExecutionEngineChannel executionEngine;
  private Optional<SafeFuture<PayloadStatus>> result = Optional.empty();

  ForkChoicePayloadExecutor(
      final Spec spec,
      final RecentChainData recentChainData,
      final SignedBeaconBlock block,
      final ExecutionEngineChannel executionEngine) {
    this.spec = spec;
    this.recentChainData = recentChainData;
    this.block = block;
    this.executionEngine = executionEngine;
  }

  public SafeFuture<PayloadStatus> getExecutionResult() {
    return result.orElse(SafeFuture.completedFuture(PayloadStatus.VALID));
  }

  @Override
  public boolean optimisticallyExecute(
      final ExecutionPayloadHeader latestExecutionPayloadHeader,
      final ExecutionPayload executionPayload) {
    if (executionPayload.isDefault()) {
      // We're still pre-merge so no payload to execute
      // Note that the BlockProcessor will have already failed if this is default and shouldn't be
      // because it checks the parentRoot matches
      return true;
    }

    result =
        Optional.of(
            executionEngine
                .newPayload(executionPayload)
                .thenCompose(
                    result -> {
                      if (result.hasValidStatus()) {
                        return verifyTransitionBlock(
                            latestExecutionPayloadHeader, executionPayload);
                      } else {
                        return SafeFuture.completedFuture(result);
                      }
                    })
                .exceptionally(
                    error -> {
                      LOG.error("Error while validating payload", error);
                      return PayloadStatus.failedExecution(error);
                    }));

    return true;
  }

  private SafeFuture<PayloadStatus> verifyTransitionBlock(
      final ExecutionPayloadHeader latestExecutionPayloadHeader,
      final ExecutionPayload executionPayload) {
    if (latestExecutionPayloadHeader.isDefault()) {
      // This is the first filled payload, so verify it meets transition conditions
      return verifyTransitionPayload(executionPayload);
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
        .map(this::verifyTransitionPayload)
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
              return verifyTransitionPayload(transitionExecutionPayload);
            });
  }

  private SafeFuture<PayloadStatus> verifyTransitionPayload(
      final ExecutionPayload executionPayload) {
    final BellatrixTransitionHelpers bellatrixTransitionHelpers =
        spec.atSlot(block.getSlot())
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
