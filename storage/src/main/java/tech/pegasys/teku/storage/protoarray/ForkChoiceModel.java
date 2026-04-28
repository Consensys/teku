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

package tech.pegasys.teku.storage.protoarray;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoiceNode;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeData;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.teku.spec.executionlayer.ExecutionPayloadStatus;
import tech.pegasys.teku.storage.api.StoredBlockMetadata;

/**
 * Storage-side fork-aware model for fork choice.
 *
 * <p>This is the block/payload variants layer that maps beacon blocks onto node identities, while
 * {@link ProtoArray} remains a milestone-agnostic node engine.
 *
 * <p>The model also owns the fork-aware vote routing and child-comparison rules so the milestone
 * split stays concentrated in one place rather than being spread across extra resolver/policy
 * types.
 */
interface ForkChoiceModel {

  void processBlock(
      ProtoArray protoArray,
      BlockNodeVariantsIndex blockNodeIndex,
      UInt64 blockSlot,
      Bytes32 blockRoot,
      Bytes32 parentRoot,
      Bytes32 stateRoot,
      BlockCheckpoints checkpoints,
      Optional<UInt64> executionBlockNumber,
      Optional<Bytes32> executionBlockHash,
      boolean optimisticallyProcessed);

  void onExecutionPayload(
      ProtoArray protoArray,
      BlockNodeVariantsIndex blockNodeIndex,
      Bytes32 blockRoot,
      UInt64 executionBlockNumber,
      Bytes32 executionBlockHash,
      boolean isOptimistic);

  void rebuildBlockNodesFromMetadata(
      ProtoArray protoArray,
      BlockNodeVariantsIndex blockNodeIndex,
      StoredBlockMetadata block,
      boolean optimisticallyProcessed);

  /** Resolves a latest-message vote onto a concrete node identity. */
  Optional<ForkChoiceNode> resolveVoteNode(
      Bytes32 voteRoot,
      UInt64 voteSlot,
      boolean payloadPresent,
      ProtoArray protoArray,
      BlockNodeVariantsIndex blockNodeIndex);

  /**
   * Compares two viable siblings from the same parent.
   *
   * <p>A positive value means the candidate should replace the current best child. A negative value
   * means the current best child should remain. Zero falls back to ProtoArray's historic
   * weight/root ordering.
   */
  int compareViableChildren(
      ProtoNode candidateChild,
      ProtoNode currentBestChild,
      ProtoNode parent,
      ProtoArray protoArray,
      BlockNodeVariantsIndex blockNodeIndex,
      UInt64 currentSlot,
      Optional<Bytes32> proposerBoostRoot);

  Optional<ProtoNodeData> getBaseNodeData(
      ProtoArray protoArray, BlockNodeVariantsIndex blockNodeIndex, Bytes32 blockRoot);

  boolean shouldExtendPayload(
      ProtoArray protoArray,
      BlockNodeVariantsIndex blockNodeIndex,
      ReadOnlyStore store,
      Bytes32 blockRoot);

  /**
   * Returns whether the supplied node is a valid head candidate for this fork-aware model.
   *
   * <p>Pre-Gloas head candidates are the base nodes. In Gloas, the structural base node is never a
   * head and only the EMPTY/FULL children are valid terminal heads.
   */
  boolean isHeadCandidate(ProtoNode node);

  Optional<ForkChoiceNode> resolveBaseNode(
      BlockNodeVariantsIndex blockNodeIndex, Bytes32 blockRoot);

  Optional<ProtoNodeData> getExecutionNodeData(
      ProtoArray protoArray, BlockNodeVariantsIndex blockNodeIndex, Bytes32 blockRoot);

  void pullUpBlockCheckpoints(
      ProtoArray protoArray, BlockNodeVariantsIndex blockNodeIndex, Bytes32 blockRoot);

  void onPtcVote(
      Bytes32 blockRoot, UInt64 validatorIndex, boolean payloadPresent, boolean blobDataAvailable);

  void onRemovedBlockRoot(
      ProtoArray protoArray, BlockNodeVariantsIndex blockNodeIndex, Bytes32 blockRoot);

  void onExecutionPayloadResult(
      ProtoArray protoArray,
      BlockNodeVariantsIndex blockNodeIndex,
      Bytes32 blockRoot,
      ExecutionPayloadStatus status,
      Optional<Bytes32> latestValidHash,
      boolean verifiedInvalidTransition,
      HeadSelectionContext headSelectionContext);

  void onPrunedBlocks(BlockNodeVariantsIndex blockNodeIndex);
}
