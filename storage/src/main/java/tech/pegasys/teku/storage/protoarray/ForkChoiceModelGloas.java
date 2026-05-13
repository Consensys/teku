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

import com.google.common.annotations.VisibleForTesting;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoiceNode;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeData;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.teku.spec.executionlayer.ExecutionPayloadStatus;
import tech.pegasys.teku.storage.api.GloasForkChoiceRebuildData;
import tech.pegasys.teku.storage.api.StoredBlockMetadata;

/**
 * Storage-side implementation of the Gloas three-state fork-choice tree.
 *
 * <p>This class is the fork-aware model-side implementation of the Python helpers and handlers that
 * introduce the EMPTY/FULL child nodes:
 * https://github.com/ethereum/consensus-specs/blob/master/specs/gloas/fork-choice.md#modified-on_block
 * https://github.com/ethereum/consensus-specs/blob/master/specs/gloas/fork-choice.md#new-on_execution_payload
 * https://github.com/ethereum/consensus-specs/blob/master/specs/gloas/fork-choice.md#new-get_node_children
 * https://github.com/ethereum/consensus-specs/blob/master/specs/gloas/fork-choice.md#new-get_parent_payload_status
 * https://github.com/ethereum/consensus-specs/blob/master/specs/gloas/fork-choice.md#new-is_parent_node_full
 */
class ForkChoiceModelGloas implements ForkChoiceModel {

  private static final Logger LOG = LogManager.getLogger();
  private final UInt64 firstGloasSlot;
  private final int payloadTimelyThreshold;
  private final int dataAvailabilityTimelyThreshold;
  private final PtcVoteTracker ptcVoteTracker;

  ForkChoiceModelGloas(final SpecConfigGloas specConfig) {
    this(
        specConfig,
        specConfig.getPayloadTimelyThreshold(),
        specConfig.getDataAvailabilityTimelyThreshold(),
        new PtcVoteTracker());
  }

  ForkChoiceModelGloas(
      final SpecConfigGloas specConfig,
      final int payloadTimelyThreshold,
      final int dataAvailabilityTimelyThreshold,
      final PtcVoteTracker ptcVoteTracker) {
    this.firstGloasSlot = specConfig.getGloasForkEpoch().times(specConfig.getSlotsPerEpoch());
    this.payloadTimelyThreshold = payloadTimelyThreshold;
    this.dataAvailabilityTimelyThreshold = dataAvailabilityTimelyThreshold;
    this.ptcVoteTracker = ptcVoteTracker;
  }

  @Override
  public void processBlock(
      final ProtoArray protoArray,
      final BlockNodeVariantsIndex blockNodeIndex,
      final UInt64 blockSlot,
      final Bytes32 blockRoot,
      final Bytes32 parentRoot,
      final Bytes32 stateRoot,
      final BlockCheckpoints checkpoints,
      final Optional<UInt64> maybeExecutionBlockNumber,
      final Optional<Bytes32> maybeExecutionBlockHash,
      final boolean optimisticallyProcessed) {
    // Spec mapping: modified on_block(store, signed_block)
    // The parent choice follows get_parent_payload_status / is_parent_node_full and the node
    // layout follows get_node_children with a base PENDING node plus an immediate EMPTY child.
    // In Gloas, maybeExecutionBlockHash does not represent this block's eventual payload block
    // hash. During block import it carries the bid's parent_block_hash, which is used to resolve
    // whether the new node builds on the parent's FULL or EMPTY variant. The execution block
    // number/hash stored on the new base/EMPTY nodes are inherited from the resolved parent, while
    // this block's own payload block number/hash are only attached later by onExecutionPayload(...)
    // when the FULL node is created.
    final ForkChoiceNode baseNode = ForkChoiceNode.createBase(blockRoot);
    final Optional<ProtoNode> parentProtoNode =
        resolveParentNode(
            protoArray,
            blockNodeIndex,
            parentRoot,
            maybeExecutionBlockHash.orElse(ProtoNode.NO_EXECUTION_BLOCK_HASH));
    final UInt64 executionBlockNumber =
        parentProtoNode
            .map(ProtoNode::getExecutionBlockNumber)
            .orElse(ProtoNode.NO_EXECUTION_BLOCK_NUMBER);
    final Bytes32 executionBlockHash =
        parentProtoNode
            .map(ProtoNode::getExecutionBlockHash)
            .orElse(ProtoNode.NO_EXECUTION_BLOCK_HASH);

    // the node is optimistic only if it builds on top of optimistic node.
    // In gloas we start being optimistic from a FULL block
    final boolean isParentOptimistic = parentProtoNode.map(ProtoNode::isOptimistic).orElse(false);
    protoArray.addNode(
        baseNode,
        blockSlot,
        parentRoot,
        parentProtoNode.map(ProtoNode::getForkChoiceNode),
        stateRoot,
        checkpoints,
        executionBlockNumber,
        executionBlockHash,
        isParentOptimistic);
    blockNodeIndex.putBaseNode(blockRoot, blockSlot, baseNode);

    final ForkChoiceNode emptyNode = ForkChoiceNode.createEmpty(blockRoot);
    protoArray.addNode(
        emptyNode,
        blockSlot,
        parentRoot,
        Optional.of(baseNode),
        stateRoot,
        checkpoints,
        executionBlockNumber,
        executionBlockHash,
        isParentOptimistic);
    blockNodeIndex.attachEmptyNode(blockRoot, emptyNode);
  }

  @VisibleForTesting
  Optional<ProtoNode> resolveParentNode(
      final ProtoArray protoArray,
      final BlockNodeVariantsIndex blockNodeIndex,
      final Bytes32 parentRoot,
      final Bytes32 childParentBlockHash) {
    // Spec mapping: get_parent_payload_status(store, block)
    // FULL wins only when the child bid's parent_block_hash matches the parent's FULL execution
    // block hash. Otherwise we attach to EMPTY. The only cases where the structural base node is a
    // valid fallback are the fork boundary where the parent is pre-Gloas and the base-only anchor
    // node at the root of the protoarray. A completely missing parent is only valid while
    // rebuilding the very first anchor node into an empty protoarray.
    final Optional<ForkChoiceNode> fullNode = blockNodeIndex.getFullNode(parentRoot);
    if (fullNode.isPresent()) {
      final Optional<ProtoNode> maybeFullNode = protoArray.getNode(fullNode.get());
      if (maybeFullNode.isPresent()
          && maybeFullNode.get().getExecutionBlockHash().equals(childParentBlockHash)) {
        return maybeFullNode;
      }
    }

    final Optional<ProtoNode> emptyNode =
        blockNodeIndex.getEmptyNode(parentRoot).flatMap(protoArray::getNode);
    if (emptyNode.isPresent()) {
      return emptyNode;
    }

    final Optional<ProtoNode> baseNode =
        blockNodeIndex.getBaseNode(parentRoot).flatMap(protoArray::getNode);
    if (baseNode.isPresent()
        && (baseNode.get().getBlockSlot().isLessThan(firstGloasSlot)
            || baseNode.get().getParentIndex().isEmpty())) {
      return baseNode;
    }
    if (protoArray.getTotalTrackedNodeCount() == 0) {
      return Optional.empty();
    }

    if (parentRoot.equals(Bytes32.ZERO)) {
      return Optional.empty();
    }

    throw new IllegalStateException(
        String.format(
            "Missing GLOAS parent variants for parent root %s at or after slot %s",
            parentRoot, firstGloasSlot));
  }

  @Override
  public void onExecutionPayload(
      final ProtoArray protoArray,
      final BlockNodeVariantsIndex blockNodeIndex,
      final Bytes32 blockRoot,
      final UInt64 executionBlockNumber,
      final Bytes32 executionBlockHash,
      final boolean isOptimistic) {
    // Spec mapping: on_execution_payload(store, signed_execution_payload_envelope)
    if (blockNodeIndex.getFullNode(blockRoot).isPresent()) {
      return;
    }
    final Optional<ForkChoiceNode> maybeBaseNodeIdentity =
        resolveBaseNode(blockNodeIndex, blockRoot);
    final Optional<ProtoNodeData> maybeBaseNode =
        maybeBaseNodeIdentity.flatMap(protoArray::getNode).map(ProtoNode::getBlockData);
    if (maybeBaseNodeIdentity.isEmpty() || maybeBaseNode.isEmpty()) {
      return;
    }

    final ForkChoiceNode fullNode = ForkChoiceNode.createFull(blockRoot);
    final ProtoNodeData baseNode = maybeBaseNode.get();
    protoArray.addNode(
        fullNode,
        baseNode.getSlot(),
        baseNode.getParentRoot(),
        maybeBaseNodeIdentity,
        baseNode.getStateRoot(),
        baseNode.getCheckpoints(),
        executionBlockNumber,
        executionBlockHash,
        isOptimistic);
    blockNodeIndex.attachFullNode(blockRoot, fullNode);
  }

  @Override
  public void rebuildBlockNodesFromMetadata(
      final ProtoArray protoArray,
      final BlockNodeVariantsIndex blockNodeIndex,
      final StoredBlockMetadata block,
      final boolean optimisticallyProcessed) {
    // Recovery replays the same modified on_block logic as live import so the EMPTY/FULL layout
    // matches the Gloas store model after restart.
    final Optional<Bytes32> parentBlockHash =
        block
            .getGloasForkChoiceRebuildData()
            .map(GloasForkChoiceRebuildData::payloadParentBlockHash);
    final Optional<UInt64> executionBlockNumber =
        block
            .getExecutionBlockNumber()
            .or(
                () ->
                    blockNodeIndex
                        .getBaseNode(block.getParentRoot())
                        .flatMap(protoArray::getNode)
                        .map(ProtoNode::getExecutionBlockNumber));
    processBlock(
        protoArray,
        blockNodeIndex,
        block.getBlockSlot(),
        block.getBlockRoot(),
        block.getParentRoot(),
        block.getStateRoot(),
        block.getCheckpointEpochs().orElseThrow(),
        executionBlockNumber,
        parentBlockHash,
        optimisticallyProcessed);
    block
        .getGloasForkChoiceRebuildData()
        .flatMap(this::getFullNodeRebuildPayload)
        .ifPresent(
            rebuildPayload ->
                onExecutionPayload(
                    protoArray,
                    blockNodeIndex,
                    block.getBlockRoot(),
                    rebuildPayload.executionBlockNumber(),
                    rebuildPayload.executionBlockHash(),
                    optimisticallyProcessed));
  }

  @Override
  public Optional<ForkChoiceNode> resolveVoteNode(
      final Bytes32 voteRoot,
      final UInt64 voteSlot,
      final boolean payloadPresent,
      final ProtoArray protoArray,
      final BlockNodeVariantsIndex blockNodeIndex) {
    final Optional<ForkChoiceNode> maybeBaseNode = blockNodeIndex.getBaseNode(voteRoot);
    if (maybeBaseNode.isEmpty()) {
      return Optional.empty();
    }

    final Optional<UInt64> blockSlot =
        protoArray.getNode(maybeBaseNode.get()).map(ProtoNode::getBlockSlot);
    if (blockSlot.isPresent() && voteSlot.isLessThanOrEqualTo(blockSlot.get())) {
      return maybeBaseNode;
    }
    if (payloadPresent) {
      return blockNodeIndex.getFullNode(voteRoot).or(() -> maybeBaseNode);
    }
    return blockNodeIndex.getEmptyNode(voteRoot).or(() -> maybeBaseNode);
  }

  @Override
  public int compareViableChildren(
      final ProtoNode candidateChild,
      final ProtoNode currentBestChild,
      final ProtoNode parent,
      final ProtoArray protoArray,
      final BlockNodeVariantsIndex blockNodeIndex,
      final UInt64 currentSlot,
      final Optional<Bytes32> proposerBoostRoot) {
    // Spec mapping: the extra Gloas sort key in modified get_head only applies to the EMPTY/FULL
    // children returned by get_node_children(parent_root).
    if (!candidateChild.getBlockRoot().equals(parent.getBlockRoot())
        || !currentBestChild.getBlockRoot().equals(parent.getBlockRoot())) {
      return 0;
    }

    final UInt64 candidateEffectiveWeight = effectiveWeight(candidateChild, currentSlot);
    final UInt64 currentEffectiveWeight = effectiveWeight(currentBestChild, currentSlot);
    final int weightComparison = candidateEffectiveWeight.compareTo(currentEffectiveWeight);
    if (weightComparison != 0) {
      return weightComparison;
    }

    return Integer.compare(
        computePayloadStatusTiebreaker(
            candidateChild, protoArray, blockNodeIndex, currentSlot, proposerBoostRoot),
        computePayloadStatusTiebreaker(
            currentBestChild, protoArray, blockNodeIndex, currentSlot, proposerBoostRoot));
  }

  private UInt64 effectiveWeight(final ProtoNode node, final UInt64 currentSlot) {
    if (node.getPayloadStatus() == ForkChoicePayloadStatus.PAYLOAD_STATUS_PENDING
        || !node.getBlockSlot().plus(1).equals(currentSlot)) {
      return node.getWeight();
    }
    return UInt64.ZERO;
  }

  private int computePayloadStatusTiebreaker(
      final ProtoNode node,
      final ProtoArray protoArray,
      final BlockNodeVariantsIndex blockNodeIndex,
      final UInt64 currentSlot,
      final Optional<Bytes32> proposerBoostRoot) {
    if (node.getPayloadStatus() == ForkChoicePayloadStatus.PAYLOAD_STATUS_PENDING
        || !node.getBlockSlot().plus(1).equals(currentSlot)) {
      return node.getPayloadStatus().getValue();
    }
    if (node.getPayloadStatus() == ForkChoicePayloadStatus.PAYLOAD_STATUS_EMPTY) {
      return 1;
    }
    return shouldExtendPayload(blockNodeIndex, node.getBlockRoot(), protoArray, proposerBoostRoot)
        ? 2
        : 0;
  }

  private boolean shouldExtendPayload(
      final BlockNodeVariantsIndex blockNodeIndex,
      final Bytes32 blockRoot,
      final ProtoArray protoArray,
      final Optional<Bytes32> proposerBoostRoot) {

    // in spec, would call is_payload_verified
    //    if not is_payload_verified(store, root):
    //        return False
    final Optional<ProtoNode> node =
        blockNodeIndex.getFullNode(blockRoot).flatMap(protoArray::getNode);
    if (node.isEmpty() || !node.get().isFullyValidated()) {
      LOG.debug(
          "Node not found or the node is not fully validated, dont extend payload at blockroot {}",
          blockRoot);
      return false;
    }

    if (isPayloadTimely(blockNodeIndex, blockRoot)
        && isPayloadDataAvailable(blockNodeIndex, blockRoot)) {
      return true;
    }
    if (proposerBoostRoot.isEmpty()) {
      return true;
    }
    final Optional<ProtoNode> proposerNode =
        blockNodeIndex.getBaseNode(proposerBoostRoot.get()).flatMap(protoArray::getNode);
    if (proposerNode.isEmpty()) {
      return true;
    }
    if (!proposerNode.get().getParentRoot().equals(blockRoot)) {
      return true;
    }
    return blockNodeIndex
        .getFullNode(blockRoot)
        .flatMap(protoArray::getNode)
        .map(
            fullNode ->
                fullNode.getExecutionBlockHash().equals(proposerNode.get().getExecutionBlockHash()))
        .orElse(false);
  }

  private boolean isPayloadTimely(
      final BlockNodeVariantsIndex blockNodeIndex, final Bytes32 blockRoot) {
    if (blockNodeIndex.getFullNode(blockRoot).isEmpty()) {
      return false;
    }
    return ptcVoteTracker.getPayloadPresentVoteCount(blockRoot) > payloadTimelyThreshold;
  }

  private boolean isPayloadDataAvailable(
      final BlockNodeVariantsIndex blockNodeIndex, final Bytes32 blockRoot) {
    if (blockNodeIndex.getFullNode(blockRoot).isEmpty()) {
      return false;
    }
    return ptcVoteTracker.getDataAvailableVoteCount(blockRoot) > dataAvailabilityTimelyThreshold;
  }

  @Override
  public void onExecutionPayloadResult(
      final ProtoArray protoArray,
      final BlockNodeVariantsIndex blockNodeIndex,
      final Bytes32 blockRoot,
      final ExecutionPayloadStatus status,
      final Optional<Bytes32> latestValidHash,
      final boolean verifiedInvalidTransition,
      final HeadSelectionContext headSelectionContext) {
    if (status.isValid()) {
      // Only the FULL node needs validation marking — base/EMPTY are already VALID from block
      // import
      blockNodeIndex.getFullNode(blockRoot).ifPresent(protoArray::markNodeValid);
    } else if (status.isInvalid()) {
      if (verifiedInvalidTransition) {
        // In Gloas, an invalid execution payload only invalidates the FULL path.
        // The beacon block (base + EMPTY) remains valid — the builder, not the proposer, is at
        // fault.
        blockNodeIndex
            .getFullNode(blockRoot)
            .ifPresent(
                node -> protoArray.markNodeInvalid(node, latestValidHash, headSelectionContext));
      } else {
        // Unverified: a child's payload was invalid, pointing at this parent.
        // The base node is the correct anchor for parent-chain invalidation search.
        blockNodeIndex
            .getBaseNode(blockRoot)
            .ifPresent(
                node ->
                    protoArray.markParentChainInvalid(node, latestValidHash, headSelectionContext));
      }
    }
  }

  @Override
  public void onForkChoiceUpdatedResult(
      final ProtoArray protoArray,
      final BlockNodeVariantsIndex blockNodeIndex,
      final ForkChoiceNode node,
      final ExecutionPayloadStatus status,
      final Optional<Bytes32> latestValidHash,
      final boolean verifiedInvalidTransition,
      final HeadSelectionContext headSelectionContext) {
    if (status.isValid()) {
      protoArray.markNodeValid(node);
      return;
    }
    onExecutionPayloadResult(
        protoArray,
        blockNodeIndex,
        node.blockRoot(),
        status,
        latestValidHash,
        verifiedInvalidTransition,
        headSelectionContext);
  }

  @Override
  public Optional<ProtoNodeData> getBaseNodeData(
      final ProtoArray protoArray,
      final BlockNodeVariantsIndex blockNodeIndex,
      final Bytes32 blockRoot) {
    return blockNodeIndex
        .getBaseNode(blockRoot)
        .flatMap(protoArray::getNode)
        .map(ProtoNode::getBlockData);
  }

  @Override
  public boolean shouldExtendPayload(
      final ProtoArray protoArray,
      final BlockNodeVariantsIndex blockNodeIndex,
      final ReadOnlyStore store,
      final Bytes32 blockRoot) {
    return shouldExtendPayload(blockNodeIndex, blockRoot, protoArray, store.getProposerBoostRoot());
  }

  @Override
  public boolean isHeadCandidate(final ProtoNode node) {
    // Spec mapping: modified get_head(store)
    // In the Gloas three-state tree the base PENDING node is structural only. Candidate heads are
    // the EMPTY/FULL children selected from get_node_children(...).
    return node.getPayloadStatus() != ForkChoicePayloadStatus.PAYLOAD_STATUS_PENDING;
  }

  @Override
  public ProtoNode resolveBestDescendant(
      final ProtoNode candidate,
      final ProtoArray protoArray,
      final BlockNodeVariantsIndex blockNodeIndex,
      final UInt64 currentSlot,
      final Optional<Bytes32> proposerBoostRoot) {
    // The structural chain-walk in ProtoArray.findHead follows stale bestDescendantIndex pointers
    // that may have been set when only EMPTY existed under a base PENDING node. When a FULL
    // sibling is added later via onExecutionPayload, BASE.bestChild is correctly flipped to FULL
    // locally, but ancestor bestDescendantIndex pointers still point at EMPTY. Redirect the
    // landing point to BASE.bestChild — the model-preferred sibling — using the candidate's own
    // block root since PENDING/EMPTY/FULL all share it.
    final ProtoNode landingPoint =
        candidate
            .getBestDescendantIndex()
            .filter(__ -> !candidate.isInvalid())
            .map(protoArray::getNodeByIndex)
            .orElse(candidate);

    return resolveBaseNode(blockNodeIndex, landingPoint.getBlockRoot())
        .flatMap(protoArray::getNode)
        .flatMap(ProtoNode::getBestChildIndex)
        .map(protoArray::getNodeByIndex)
        .filter(sibling -> !sibling.isInvalid())
        .orElse(landingPoint);
  }

  @Override
  public Optional<ForkChoiceNode> resolveBaseNode(
      final BlockNodeVariantsIndex blockNodeIndex, final Bytes32 blockRoot) {
    return blockNodeIndex.getBaseNode(blockRoot);
  }

  @Override
  public Optional<ProtoNodeData> getExecutionNodeData(
      final ProtoArray protoArray,
      final BlockNodeVariantsIndex blockNodeIndex,
      final Bytes32 blockRoot) {
    // FULL is the execution-state node selected when the payload has been revealed.
    return blockNodeIndex
        .getFullNode(blockRoot)
        .or(() -> resolveBaseNode(blockNodeIndex, blockRoot))
        .flatMap(protoArray::getNode)
        .map(ProtoNode::getBlockData);
  }

  @Override
  public void pullUpBlockCheckpoints(
      final ProtoArray protoArray,
      final BlockNodeVariantsIndex blockNodeIndex,
      final Bytes32 blockRoot) {
    blockNodeIndex
        .getVariants(blockRoot)
        .ifPresent(variants -> variants.allNodes().forEach(protoArray::pullUpCheckpoints));
  }

  @Override
  public void onPtcVote(
      final Bytes32 blockRoot,
      final UInt64 validatorIndex,
      final boolean payloadPresent,
      final boolean blobDataAvailable) {
    // Spec mapping: on_payload_attestation_message / notify_ptc_messages
    ptcVoteTracker.recordVote(blockRoot, validatorIndex, payloadPresent, blobDataAvailable);
  }

  @Override
  public void onRemovedBlockRoot(
      final ProtoArray protoArray,
      final BlockNodeVariantsIndex blockNodeIndex,
      final Bytes32 blockRoot) {
    blockNodeIndex
        .getVariants(blockRoot)
        .ifPresent(variants -> variants.allNodes().forEach(protoArray::removeNode));
    blockNodeIndex.remove(blockRoot);
    ptcVoteTracker.remove(blockRoot);
  }

  @Override
  public void onPrunedBlocks(final BlockNodeVariantsIndex blockNodeIndex) {
    ptcVoteTracker.removeIf(root -> !blockNodeIndex.containsBlock(root));
  }

  private Optional<FullNodeRebuildPayload> getFullNodeRebuildPayload(
      final GloasForkChoiceRebuildData rebuildData) {
    return rebuildData
        .payloadBlockNumber()
        .map(
            executionBlockNumber ->
                new FullNodeRebuildPayload(executionBlockNumber, rebuildData.payloadBlockHash()));
  }

  private record FullNodeRebuildPayload(UInt64 executionBlockNumber, Bytes32 executionBlockHash) {}
}
