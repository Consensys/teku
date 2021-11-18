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
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.eventthread.EventThread;
import tech.pegasys.teku.protoarray.ForkChoiceStrategy;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.executionengine.ExecutePayloadResult;
import tech.pegasys.teku.spec.executionengine.ExecutionEngineChannel;
import tech.pegasys.teku.spec.executionengine.ExecutionPayloadStatus;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.logic.versions.merge.block.OptimisticExecutionPayloadExecutor;
import tech.pegasys.teku.spec.logic.versions.merge.helpers.MergeTransitionHelpers;
import tech.pegasys.teku.storage.client.RecentChainData;

class ForkChoicePayloadExecutor implements OptimisticExecutionPayloadExecutor {

  private final Spec spec;
  private final RecentChainData recentChainData;
  private final EventThread forkChoiceExecutor;
  private final SignedBeaconBlock block;
  private final ExecutionEngineChannel executionEngine;
  private Optional<SafeFuture<ExecutePayloadResult>> result = Optional.empty();

  ForkChoicePayloadExecutor(
      final Spec spec,
      final RecentChainData recentChainData,
      final EventThread forkChoiceExecutor,
      final SignedBeaconBlock block,
      final ExecutionEngineChannel executionEngine) {
    this.spec = spec;
    this.recentChainData = recentChainData;
    this.forkChoiceExecutor = forkChoiceExecutor;
    this.block = block;
    this.executionEngine = executionEngine;
  }

  public SafeFuture<BlockImportResult> combine(final BlockImportResult blockImportResult) {
    if (!blockImportResult.isSuccessful()) {
      // If the block import failed there's no point waiting for the payload result.
      return SafeFuture.completedFuture(blockImportResult);
    }
    if (result.isEmpty()) {
      // No execution was started so can return result unchanged
      updateForkChoiceForImportedBlock(block, blockImportResult, getForkChoiceStrategy());
      return SafeFuture.completedFuture(blockImportResult);
    }
    // Otherwise we'll have to wait for the payload result
    return result
        .get()
        .thenApplyAsync(
            payloadResult -> combineResults(blockImportResult, payloadResult), forkChoiceExecutor);
  }

  private BlockImportResult combineResults(
      final BlockImportResult blockImportResult, final ExecutePayloadResult payloadResult) {
    final ForkChoiceStrategy forkChoiceStrategy = getForkChoiceStrategy();
    forkChoiceStrategy.onExecutionPayloadResult(block.getRoot(), payloadResult.getStatus());
    if (payloadResult.getStatus() == ExecutionPayloadStatus.INVALID) {
      return BlockImportResult.failedStateTransition(
          new IllegalStateException(
              "Invalid ExecutionPayload: "
                  + payloadResult.getMessage().orElse("No reason provided")));
    }

    if (payloadResult.getStatus() == ExecutionPayloadStatus.VALID) {
      updateForkChoiceForImportedBlock(block, blockImportResult, forkChoiceStrategy);
    }
    return blockImportResult;
  }

  @Override
  public boolean optimisticallyExecute(
      final ExecutionPayloadHeader latestExecutionPayloadHeader,
      final ExecutionPayload executionPayload) {
    if (executionPayload.isDefault()) {
      // We're still pre-merge so no payload to execute
      // Note that the BlockProcessor will have already failed if this is default and shouldn't be
      // because it check the parentRoot matches
      return true;
    }
    final MergeTransitionHelpers mergeTransitionHelpers =
        spec.atSlot(block.getSlot())
            .getMergeTransitionHelpers()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Attempting to validate a merge block when spec does not have merge transition helpers"));

    if (latestExecutionPayloadHeader.isDefault()) {
      // This is the first filled payload, so need to check it's a valid merge block
      result =
          Optional.of(mergeTransitionHelpers.validateMergeBlock(executionEngine, executionPayload));
    } else {
      result = Optional.of(executionEngine.executePayload(executionPayload));
    }
    return true;
  }

  private void updateForkChoiceForImportedBlock(
      final SignedBeaconBlock block,
      final BlockImportResult result,
      final ForkChoiceStrategy forkChoiceStrategy) {

    final SlotAndBlockRoot bestHeadBlock = findNewChainHead(block, forkChoiceStrategy);
    if (!bestHeadBlock.getBlockRoot().equals(recentChainData.getBestBlockRoot().orElseThrow())) {
      recentChainData.updateHead(bestHeadBlock.getBlockRoot(), bestHeadBlock.getSlot());
      if (bestHeadBlock.getBlockRoot().equals(block.getRoot())) {
        result.markAsCanonical();
      }
    }
  }

  private SlotAndBlockRoot findNewChainHead(
      final SignedBeaconBlock block, final ForkChoiceStrategy forkChoiceStrategy) {
    // If the new block builds on our current chain head it must be the new chain head.
    // Since fork choice works by walking down the tree selecting the child block with
    // the greatest weight, when a block has only one child it will automatically become
    // a better choice than the block itself.  So the first block we receive that is a
    // child of our current chain head, must be the new chain head. If we'd had any other
    // child of the current chain head we'd have already selected it as head.
    if (recentChainData
        .getChainHead()
        .map(currentHead -> currentHead.getRoot().equals(block.getParentRoot()))
        .orElse(false)) {
      return new SlotAndBlockRoot(block.getSlot(), block.getRoot());
    }

    // Otherwise, use fork choice to find the new chain head as if this block is on time the
    // proposer weighting may cause us to reorg.
    // During sync, this may be noticeably slower than just comparing the chain head due to the way
    // ProtoArray skips updating all ancestors when adding a new block but it's cheap when in sync.
    final Checkpoint justifiedCheckpoint = recentChainData.getJustifiedCheckpoint().orElseThrow();
    final Checkpoint finalizedCheckpoint = recentChainData.getFinalizedCheckpoint().orElseThrow();
    return forkChoiceStrategy.findHead(justifiedCheckpoint, finalizedCheckpoint);
  }

  private ForkChoiceStrategy getForkChoiceStrategy() {
    forkChoiceExecutor.checkOnEventThread();
    return recentChainData
        .getForkChoiceStrategy()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Attempting to perform fork choice operations before store has been initialized"));
  }
}
