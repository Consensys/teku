/*
 * Copyright Consensys Software Inc., 2025
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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeValidationStatus.INVALID;
import static tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeValidationStatus.OPTIMISTIC;
import static tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeValidationStatus.VALID;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.exceptions.FatalServiceFailureException;
import tech.pegasys.teku.infrastructure.logging.StatusLogger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;

public class ProtoArray {
  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private int pruneThreshold;

  private UInt64 currentEpoch;
  private Checkpoint justifiedCheckpoint;
  private Checkpoint finalizedCheckpoint;
  // The epoch of our initial startup state
  // When starting from genesis, this value is zero (genesis epoch)
  private final UInt64 initialEpoch;
  private final StatusLogger statusLog;

  /**
   * Lists all the known nodes. It is guaranteed that a node will be after its parent in the list.
   *
   * <p>The list may contain nodes which have been removed from the indices collection either
   * because they are now before the finalized checkpoint but pruning has not yet occurred or
   * because they extended from a now-invalid chain and were removed. This avoids having to update
   * the indices to entries in the list too often.
   */
  private final List<ProtoNode> nodes = new ArrayList<>();

  /**
   * protoArrayIndices allows root lookup to retrieve indices of protoNodes without looking through
   * the nodes list
   *
   * <p>Needs to be Maintained when nodes are added or removed from the nodes list.
   */
  private final ProtoArrayIndices indices = new ProtoArrayIndices();

  ProtoArray(
      final Spec spec,
      final int pruneThreshold,
      final UInt64 currentEpoch,
      final Checkpoint justifiedCheckpoint,
      final Checkpoint finalizedCheckpoint,
      final UInt64 initialEpoch,
      final StatusLogger statusLog) {
    this.spec = spec;
    this.pruneThreshold = pruneThreshold;
    this.currentEpoch = currentEpoch;
    this.justifiedCheckpoint = justifiedCheckpoint;
    this.finalizedCheckpoint = finalizedCheckpoint;
    this.initialEpoch = initialEpoch;
    this.statusLog = statusLog;
  }

  public static ProtoArrayBuilder builder() {
    return new ProtoArrayBuilder();
  }

  public boolean contains(final Bytes32 root) {
    return indices.contains(root);
  }

  public Optional<Integer> getIndexByRoot(final Bytes32 root) {
    return indices.get(root);
  }

  public Optional<ProtoNode> getProtoNode(final Bytes32 root) {
    return indices
        .get(root)
        .flatMap(
            blockIndex -> {
              if (blockIndex < getTotalTrackedNodeCount()) {
                return Optional.of(getNodeByIndex(blockIndex));
              }
              return Optional.empty();
            });
  }

  public List<ProtoNode> getNodes() {
    return nodes;
  }

  public void setPruneThreshold(final int pruneThreshold) {
    this.pruneThreshold = pruneThreshold;
  }

  /**
   * Register a block with the fork choice. It is only sane to supply a `None` parent for the
   * genesis block.
   */
  public void onBlock(
      final UInt64 blockSlot,
      final Bytes32 blockRoot,
      final Bytes32 parentRoot,
      final Bytes32 stateRoot,
      final BlockCheckpoints checkpoints,
      final UInt64 executionBlockNumber,
      final Bytes32 executionBlockHash,
      final boolean optimisticallyProcessed) {
    if (indices.contains(blockRoot)) {
      return;
    }

    int nodeIndex = getTotalTrackedNodeCount();

    ProtoNode node =
        new ProtoNode(
            blockSlot,
            stateRoot,
            blockRoot,
            parentRoot,
            indices.get(parentRoot),
            checkpoints,
            executionBlockNumber,
            executionBlockHash,
            UInt64.ZERO,
            Optional.empty(),
            Optional.empty(),
            optimisticallyProcessed && !executionBlockHash.isZero() ? OPTIMISTIC : VALID);

    indices.add(blockRoot, nodeIndex);
    nodes.add(node);

    updateBestDescendantOfParent(node, nodeIndex);
  }

  public void setInitialCanonicalBlockRoot(final Bytes32 initialCanonicalBlockRoot) {
    final Optional<ProtoNode> initialCanonicalProtoNode = getProtoNode(initialCanonicalBlockRoot);
    if (initialCanonicalProtoNode.isEmpty()) {
      LOG.warn("Initial canonical block root not found: {}", initialCanonicalBlockRoot);
      return;
    }

    applyToNodes(this::updateBestDescendantOfParent);

    // let's peak the best descendant of the initial canonical block root
    ProtoNode node =
        initialCanonicalProtoNode
            .get()
            .getBestDescendantIndex()
            .map(this::getNodeByIndex)
            .orElse(initialCanonicalProtoNode.get());

    // add a single weight to from the best descendant up to the root
    node.adjustWeight(1);
    while (node.getParentIndex().isPresent()) {
      final ProtoNode parent = getNodeByIndex(node.getParentIndex().get());
      parent.adjustWeight(1);
      node = parent;
    }

    applyToNodes(this::updateBestDescendantOfParent);
  }

  /**
   * Follows the best-descendant links to find the best-block (i.e. head-block), including any
   * optimistic nodes which have not yet been fully validated.
   *
   * @param currentEpoch the current epoch according to the Store current time
   * @param justifiedCheckpoint the justified checkpoint
   * @param finalizedCheckpoint the finalized checkpoint
   * @return the best node according to fork choice
   */
  public ProtoNode findOptimisticHead(
      final UInt64 currentEpoch,
      final Checkpoint justifiedCheckpoint,
      final Checkpoint finalizedCheckpoint) {
    return findHead(currentEpoch, justifiedCheckpoint, finalizedCheckpoint)
        .orElseThrow(fatalException("Finalized block was found to be invalid."));
  }

  public Optional<ProtoNode> findOptimisticallySyncedMergeTransitionBlock(final Bytes32 head) {
    final Optional<ProtoNode> maybeStartingNode = getProtoNode(head);
    if (maybeStartingNode.isEmpty()) {
      return Optional.empty();
    }
    ProtoNode currentNode = maybeStartingNode.get();
    if (currentNode.getExecutionBlockHash().isZero()) {
      // Transition not yet reached so no transition block
      return Optional.empty();
    }
    while (contains(currentNode.getBlockRoot())) {
      if (currentNode.getParentIndex().isEmpty() || currentNode.isFullyValidated()) {
        // Stop searching when we reach fully validated nodes or a node we don't have the parent for
        return Optional.empty();
      }
      final ProtoNode parentNode = getNodeByIndex(currentNode.getParentIndex().get());
      if (parentNode.getExecutionBlockHash().isZero()) {
        return Optional.of(currentNode);
      }
      currentNode = parentNode;
    }
    return Optional.empty();
  }

  private Supplier<FatalServiceFailureException> fatalException(final String message) {
    return () -> new FatalServiceFailureException("fork choice", message);
  }

  private Optional<ProtoNode> findHead(
      final UInt64 currentEpoch,
      final Checkpoint justifiedCheckpoint,
      final Checkpoint finalizedCheckpoint) {
    if (!this.currentEpoch.equals(currentEpoch)
        || !this.justifiedCheckpoint.equals(justifiedCheckpoint)
        || !this.finalizedCheckpoint.equals(finalizedCheckpoint)) {
      this.currentEpoch = currentEpoch;
      this.justifiedCheckpoint = justifiedCheckpoint;
      this.finalizedCheckpoint = finalizedCheckpoint;
      // Justified or finalized epoch changed so we have to re-evaluate all best descendants.
      applyToNodes(this::updateBestDescendantOfParent);
    }
    int justifiedIndex =
        indices
            .get(justifiedCheckpoint.getRoot())
            .orElseThrow(
                fatalException(
                    "Invalid or unknown justified root: " + justifiedCheckpoint.getRoot()));

    ProtoNode justifiedNode = getNodeByIndex(justifiedIndex);

    if (justifiedNode.isInvalid()) {
      return Optional.empty();
    }

    int bestDescendantIndex = justifiedNode.getBestDescendantIndex().orElse(justifiedIndex);
    ProtoNode bestNode = getNodeByIndex(bestDescendantIndex);

    // Normally the best descendant index would point straight to chain head, but onBlock only
    // updates the parent, not all the ancestors. When applyScoreChanges runs it propagates the
    // change back up and everything works, but we run findHead to determine if the new block should
    // become the best head so need to follow down the chain.
    while (bestNode.getBestDescendantIndex().isPresent() && !bestNode.isInvalid()) {
      bestDescendantIndex = bestNode.getBestDescendantIndex().get();
      bestNode = getNodeByIndex(bestDescendantIndex);
    }

    // Walk backwards to find the last valid node in the chain
    while (bestNode.isInvalid()) {
      final Optional<Integer> maybeParentIndex = bestNode.getParentIndex();
      if (maybeParentIndex.isEmpty()) {
        // No node on this chain with sufficient validity.
        return Optional.empty();
      }
      final int parentIndex = maybeParentIndex.get();
      bestNode = getNodeByIndex(parentIndex);
    }

    // Perform a sanity check that the node is indeed valid to be the head.
    if (!nodeIsViableForHead(bestNode) && !bestNode.equals(justifiedNode)) {
      throw new IllegalStateException(
          "ProtoArray: Best node " + bestNode.toLogString() + " is not viable for head");
    }
    return Optional.of(bestNode);
  }

  public void markNodeValid(final Bytes32 blockRoot) {
    final Optional<ProtoNode> maybeNode = getProtoNode(blockRoot);
    if (maybeNode.isEmpty()) {
      // Most likely just pruned prior to the validation result being received.
      LOG.debug("Couldn't mark block {} valid because it was unknown", blockRoot);
      return;
    }
    final ProtoNode node = maybeNode.get();
    node.setValidationStatus(VALID);
    Optional<Integer> parentIndex = node.getParentIndex();
    while (parentIndex.isPresent()) {
      final ProtoNode parentNode = getNodeByIndex(parentIndex.get());
      if (parentNode.isFullyValidated()) {
        break;
      }
      parentNode.setValidationStatus(VALID);
      parentIndex = parentNode.getParentIndex();
    }
  }

  /**
   * Marks blockRoot node and all found ancestors up to a node containing block with
   * `latestValidHash` (exclusive) execution block as INVALID. If node with `latestValidHash` is
   * found it's marked as VALID along with its ancestors
   *
   * @param blockRoot Transition block root
   * @param latestValidHash Latest valid hash of execution block
   */
  public void markNodeInvalid(final Bytes32 blockRoot, final Optional<Bytes32> latestValidHash) {
    markNodeInvalid(blockRoot, latestValidHash, true);
  }

  /**
   * Tries to find an INVALID chain starting from `blockRoot` up to a node containing block with
   * `latestValidHash` (exclusive), if such chain segment is found it's marked as INVALID, while
   * node containing `latestValidHash` and its ancestors are marked as VALID. If such chain segment
   * is not found, no changes are applied.
   *
   * @param blockRoot Parent of the node with INVALID execution block
   * @param latestValidHash Latest valid hash of execution block
   */
  public void markParentChainInvalid(
      final Bytes32 blockRoot, final Optional<Bytes32> latestValidHash) {
    markNodeInvalid(blockRoot, latestValidHash, false);
  }

  private void markNodeInvalid(
      final Bytes32 blockRoot,
      final Optional<Bytes32> latestValidHash,
      final boolean verifiedInvalidTransition) {
    if (!verifiedInvalidTransition && latestValidHash.isEmpty()) {
      // Couldn't find invalid chain segment with lack of data
      return;
    }
    final Optional<Integer> maybeIndex = indices.get(blockRoot);
    if (maybeIndex.isEmpty()) {
      LOG.debug("Couldn't update status for block {} because it was unknown", blockRoot);
      return;
    }
    final int index;
    final ProtoNode node;
    if (latestValidHash.isPresent()) {
      final Optional<Integer> maybeFirstInvalidNodeIndex =
          findFirstInvalidNodeIndex(maybeIndex.get(), latestValidHash.get());
      if (!verifiedInvalidTransition) {
        if (nodeHasExecutionHash(maybeIndex.get(), latestValidHash.get())) {
          // Nothing to do: head blockRoot contains payload with latestValidHash
          return;
        }
        if (maybeFirstInvalidNodeIndex.isEmpty()) {
          // Nothing to do: latestValidHash was not found, no proof of invalid transition
          return;
        }
      }

      index = maybeFirstInvalidNodeIndex.orElse(maybeIndex.get());
      node = getNodeByIndex(index);
    } else {
      index = maybeIndex.get();
      node = getNodeByIndex(index);
    }

    node.setValidationStatus(INVALID);
    removeBlockRoot(node.getBlockRoot());
    markDescendantsAsInvalid(index);
    // Applying zero deltas causes the newly marked INVALID nodes to have their weight set to 0
    applyDeltas(new LongArrayList(Collections.nCopies(getTotalTrackedNodeCount(), 0L)));
  }

  private boolean nodeHasExecutionHash(final int nodeIndex, final Bytes32 executionHash) {
    return getNodeByIndex(nodeIndex).getExecutionBlockHash().equals(executionHash);
  }

  private Optional<Integer> findFirstInvalidNodeIndex(
      final int invalidNodeIndex, final Bytes32 latestValidHash) {
    int firstInvalidNodeIndex = invalidNodeIndex;
    Optional<Integer> parentIndex = Optional.of(invalidNodeIndex);
    while (parentIndex.isPresent()) {
      final ProtoNode parentNode = getNodeByIndex(parentIndex.get());
      if (parentNode.getExecutionBlockHash().equals(latestValidHash)) {
        return Optional.of(firstInvalidNodeIndex);
      }
      firstInvalidNodeIndex = parentIndex.get();
      parentIndex = parentNode.getParentIndex();
    }
    // Couldn't find the last valid hash - so can't take advantage of it.
    // Alert this user as it may indicate that invalid payloads have been finalized
    // (or the EL client is malfunctioning somehow).
    statusLog.unknownLatestValidHash(latestValidHash);
    return Optional.empty();
  }

  private void markDescendantsAsInvalid(final int index) {
    final IntSet invalidParents = new IntOpenHashSet();
    invalidParents.add(index);
    // Need to mark all nodes extending from this one as invalid
    // Descendant nodes must be later in the array so can start from next index
    for (int i = index + 1; i < nodes.size(); i++) {
      final ProtoNode possibleDescendant = getNodeByIndex(i);
      if (possibleDescendant.getParentIndex().isEmpty()) {
        continue;
      }
      if (invalidParents.contains((int) possibleDescendant.getParentIndex().get())) {
        possibleDescendant.setValidationStatus(INVALID);
        removeBlockRoot(possibleDescendant.getBlockRoot());
        invalidParents.add(i);
      }
    }
  }

  /**
   * Iterate backwards through the array, touching all nodes and their parents and potentially the
   * bestChildIndex of each parent.
   *
   * <p>NOTE: this function should only throw exceptions when validating the parameters. Once we
   * start updating the protoarray we should not throw exceptions because we are currently not able
   * to rollback the changes. See {@link ForkChoiceStrategy#applyPendingVotes}.
   *
   * <p>The structure of the `nodes` array ensures that the child of each node is always touched
   * before its parent.
   *
   * <p>For each node, the following is done:
   *
   * <ul>
   *   <li>Update the node's weight with the corresponding delta.
   *   <li>Back-propagate each node's delta to its parents delta.
   *   <li>Compare the current node with the parents best child, updating it if the current node
   *       should become the best child.
   *   <li>If required, update the parents best descendant with the current node or its best
   *       descendant.
   * </ul>
   */
  public void applyScoreChanges(
      final LongList deltas,
      final UInt64 currentEpoch,
      final Checkpoint justifiedCheckpoint,
      final Checkpoint finalizedCheckpoint) {
    checkArgument(
        deltas.size() == getTotalTrackedNodeCount(),
        "ProtoArray: Invalid delta length expected %s but got %s",
        getTotalTrackedNodeCount(),
        deltas.size());

    this.currentEpoch = currentEpoch;
    this.justifiedCheckpoint = justifiedCheckpoint;
    this.finalizedCheckpoint = finalizedCheckpoint;

    applyDeltas(deltas);
  }

  public int getTotalTrackedNodeCount() {
    return nodes.size();
  }

  /**
   * Update the tree with new finalization information. The tree is only actually pruned if both of
   * the two following criteria are met:
   *
   * <ul>
   *   <li>The supplied finalized epoch and root are different to the current values.
   *   <li>The number of nodes in `this` is at least `this.pruneThreshold`.
   * </ul>
   */
  public void maybePrune(final Bytes32 finalizedRoot) {
    int finalizedIndex =
        indices
            .get(finalizedRoot)
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "ProtoArray: Finalized root is unknown " + finalizedRoot.toHexString()));

    if (finalizedIndex < pruneThreshold) {
      // Pruning at small numbers incurs more cost than benefit.
      return;
    }

    // Remove the `indices` key/values for all the to-be-deleted nodes.
    for (int nodeIndex = 0; nodeIndex < finalizedIndex; nodeIndex++) {
      Bytes32 root = getNodeByIndex(nodeIndex).getBlockRoot();
      indices.remove(root);
    }

    // Drop all the nodes prior to finalization.
    nodes.subList(0, finalizedIndex).clear();

    indices.offsetIndices(finalizedIndex);

    // Iterate through all the existing nodes and adjust their indices to match the
    // new layout of nodes.
    for (ProtoNode node : nodes) {
      node.getParentIndex()
          .ifPresent(
              parentIndex -> {
                // If node.parentIndex is less than finalizedIndex, set is to None.
                if (parentIndex < finalizedIndex) {
                  node.setParentIndex(Optional.empty());
                } else {
                  node.setParentIndex(Optional.of(parentIndex - finalizedIndex));
                }
              });

      node.getBestChildIndex()
          .ifPresent(
              bestChildIndex -> {
                int newBestChildIndex = bestChildIndex - finalizedIndex;
                checkState(
                    newBestChildIndex >= 0, "ProtoArray: New best child index is less than 0");
                node.setBestChildIndex(Optional.of(newBestChildIndex));
              });

      node.getBestDescendantIndex()
          .ifPresent(
              bestDescendantIndex -> {
                int newBestDescendantIndex = bestDescendantIndex - finalizedIndex;
                checkState(
                    newBestDescendantIndex >= 0,
                    "ProtoArray: New best descendant index is less than 0");
                node.setBestDescendantIndex(Optional.of(newBestDescendantIndex));
              });
    }
  }

  /**
   * Observe the parent at `parentIndex` with respect to the child at `childIndex` and potentially
   * modify the `parent.bestChild` and `parent.bestDescendant` values.
   *
   * <p>## Detail
   *
   * <p>There are four outcomes:
   *
   * <ul>
   *   <li>The child is already the best child but it's now invalid due to a FFG change and should
   *       be removed.
   *   <li>The child is already the best child and the parent is updated with the new best
   *       descendant.
   *   <li>The child is not the best child but becomes the best child.
   *   <li>The child is not the best child and does not become the best child.
   * </ul>
   */
  @SuppressWarnings("StatementWithEmptyBody")
  private void maybeUpdateBestChildAndDescendant(final int parentIndex, final int childIndex) {
    ProtoNode child = getNodeByIndex(childIndex);
    ProtoNode parent = getNodeByIndex(parentIndex);

    boolean childLeadsToViableHead = nodeLeadsToViableHead(child);

    parent
        .getBestChildIndex()
        .ifPresentOrElse(
            bestChildIndex -> {
              if (bestChildIndex.equals(childIndex) && !childLeadsToViableHead) {
                // If the child is already the best-child of the parent but it's not viable for
                // the head, remove it.
                changeToNone(parent);
              } else if (bestChildIndex.equals(childIndex)) {
                // If the child is the best-child already, set it again to ensure that the
                // best-descendant of the parent is updated.
                changeToChild(parent, childIndex);
              } else {
                ProtoNode bestChild = getNodeByIndex(bestChildIndex);

                boolean bestChildLeadsToViableHead = nodeLeadsToViableHead(bestChild);

                if (childLeadsToViableHead && !bestChildLeadsToViableHead) {
                  // The child leads to a viable head, but the current best-child doesn't.
                  changeToChild(parent, childIndex);
                } else if (!childLeadsToViableHead && bestChildLeadsToViableHead) {
                  // The best child leads to a viable head, but the child doesn't.
                  // No change.
                } else if (child.getWeight().equals(bestChild.getWeight())) {
                  // Tie-breaker of equal weights by root.
                  if (child
                          .getBlockRoot()
                          .toHexString()
                          .compareTo(bestChild.getBlockRoot().toHexString())
                      >= 0) {
                    changeToChild(parent, childIndex);
                  } else {
                    // No change.
                  }
                } else {
                  // Choose the winner by weight.
                  if (child.getWeight().compareTo(bestChild.getWeight()) >= 0) {
                    changeToChild(parent, childIndex);
                  } else {
                    // No change.
                  }
                }
              }
            },
            () -> {
              if (childLeadsToViableHead) {
                // There is no current best-child and the child is viable.
                changeToChild(parent, childIndex);
              } else {
                // There is no current best-child but the child is not not viable.
                // No change.
              }
            });
  }

  /** Helper for maybeUpdateBestChildAndDescendant */
  private void changeToChild(final ProtoNode parent, final int childIndex) {
    ProtoNode child = getNodeByIndex(childIndex);
    parent.setBestChildIndex(Optional.of(childIndex));
    parent.setBestDescendantIndex(Optional.of(child.getBestDescendantIndex().orElse(childIndex)));
  }

  /** Helper for maybeUpdateBestChildAndDescendant */
  private void changeToNone(final ProtoNode parent) {
    parent.setBestChildIndex(Optional.empty());
    parent.setBestDescendantIndex(Optional.empty());
  }

  /**
   * Indicates if the node itself is viable for the head, or if it's best descendant is viable for
   * the head.
   */
  private boolean nodeLeadsToViableHead(final ProtoNode node) {
    if (nodeIsViableForHead(node)) {
      return true;
    }

    return node.getBestDescendantIndex()
        .map(this::getNodeByIndex)
        .map(this::nodeIsViableForHead)
        .orElse(false);
  }

  /**
   * This is the equivalent to the <a
   * href="https://github.com/ethereum/consensus-specs/blob/v0.10.0/specs/phase0/fork-choice.md#filter_block_tree">filter_block_tree</a>
   * function in the eth2 spec:
   *
   * <p>Any node that has a different finalized or justified epoch should not be viable for the
   * head.
   */
  public boolean nodeIsViableForHead(final ProtoNode node) {
    if (node.isInvalid()) {
      return false;
    }

    // The voting source should be either at the same height as the store's justified checkpoint or
    // not more than two epochs ago
    if (!isVotingSourceWithinAcceptableRange(
        node.getJustifiedCheckpoint().getEpoch(), justifiedCheckpoint.getEpoch())) {
      return false;
    }

    return node.getFinalizedCheckpoint().getEpoch().equals(initialEpoch)
        || isFinalizedRootOrDescendant(node);
  }

  boolean isJustifiedRootOrDescendant(final ProtoNode node) {
    return getProtoNode(justifiedCheckpoint.getRoot())
        .map(
            justifiedNode ->
                hasAncestorAtSlot(node, justifiedNode.getBlockSlot(), justifiedNode.getBlockRoot()))
        .orElse(false);
  }

  private boolean isFinalizedRootOrDescendant(final ProtoNode node) {
    final UInt64 finalizedEpoch = finalizedCheckpoint.getEpoch();
    final Bytes32 finalizedRoot = finalizedCheckpoint.getRoot();

    final Checkpoint nodeFinalizedCheckpoint = node.getFinalizedCheckpoint();
    if (nodeFinalizedCheckpoint.getEpoch().equals(finalizedEpoch)
        && nodeFinalizedCheckpoint.getRoot().equals(finalizedRoot)) {
      return true;
    }

    final Checkpoint nodeJustifiedCheckpoint = node.getJustifiedCheckpoint();
    if (nodeJustifiedCheckpoint.getEpoch().equals(finalizedEpoch)
        && nodeJustifiedCheckpoint.getRoot().equals(finalizedRoot)) {
      return true;
    }

    final Checkpoint nodeUnrealizedFinalizedCheckpoint = node.getUnrealizedFinalizedCheckpoint();
    if (nodeUnrealizedFinalizedCheckpoint.getEpoch().equals(finalizedEpoch)
        && nodeUnrealizedFinalizedCheckpoint.getRoot().equals(finalizedRoot)) {
      return true;
    }

    final Checkpoint nodeUnrealizedJustifiedCheckpoint = node.getUnrealizedJustifiedCheckpoint();
    if (nodeUnrealizedJustifiedCheckpoint.getEpoch().equals(finalizedEpoch)
        && nodeUnrealizedJustifiedCheckpoint.getRoot().equals(finalizedRoot)) {
      return true;
    }

    final UInt64 finalizedSlot = spec.computeStartSlotAtEpoch(finalizedCheckpoint.getEpoch());
    return hasAncestorAtSlot(node, finalizedSlot, finalizedRoot);
  }

  /**
   * This is similar to the <a
   * href="https://github.com/ethereum/consensus-specs/blob/master/specs/phase0/fork-choice.md#get_ancestor">get_ancestor</a>
   * function in the eth2 spec.
   *
   * <p>The difference is that this is checking if the ancestor at slot is the required one.
   */
  private boolean hasAncestorAtSlot(
      final ProtoNode start, final UInt64 finalizedSlot, final Bytes32 requiredRoot) {
    ProtoNode node = start;
    while (node != null && node.getBlockSlot().isGreaterThan(finalizedSlot)) {
      node = node.getParentIndex().map(this::getNodeByIndex).orElse(null);
    }
    return node != null && requiredRoot.equals(node.getBlockRoot());
  }

  private boolean isVotingSourceWithinAcceptableRange(
      final UInt64 nodeJustifiedEpoch, final UInt64 currentJustifiedEpoch) {
    return currentJustifiedEpoch.equals(initialEpoch)
        || nodeJustifiedEpoch.equals(currentJustifiedEpoch)
        || nodeJustifiedEpoch.plus(2).isGreaterThanOrEqualTo(currentEpoch);
  }

  public Checkpoint getJustifiedCheckpoint() {
    return justifiedCheckpoint;
  }

  public Checkpoint getFinalizedCheckpoint() {
    return finalizedCheckpoint;
  }

  /**
   * Removes a block root from the lookup map. The actual node is not removed from the protoarray to
   * avoid recalculating indices. As a result, looking up the block by root will not find it but it
   * may still be "found" when iterating through all nodes or following links to parent or ancestor
   * nodes.
   *
   * @param blockRoot the block root to remove from the lookup map.
   */
  public void removeBlockRoot(final Bytes32 blockRoot) {
    indices.remove(blockRoot);
  }

  public void pullUpBlockCheckpoints(final Bytes32 blockRoot) {
    getProtoNode(blockRoot).ifPresent(ProtoNode::pullUpCheckpoints);
  }

  private void applyDeltas(final LongList deltas) {
    applyToNodes((node, nodeIndex) -> applyDelta(deltas, node, nodeIndex));
    applyToNodes(this::updateBestDescendantOfParent);
  }

  private void updateBestDescendantOfParent(final ProtoNode node, final int nodeIndex) {
    node.getParentIndex()
        .ifPresent(parentIndex -> maybeUpdateBestChildAndDescendant(parentIndex, nodeIndex));
  }

  private void applyDelta(final LongList deltas, final ProtoNode node, final int nodeIndex) {
    // If the node is invalid, remove any existing weight.
    long nodeDelta = node.isInvalid() ? -node.getWeight().longValue() : deltas.getLong(nodeIndex);
    node.adjustWeight(nodeDelta);

    if (node.getParentIndex().isPresent()) {
      int parentIndex = node.getParentIndex().get();
      deltas.set(parentIndex, deltas.getLong(parentIndex) + nodeDelta);
    }
  }

  private void applyToNodes(final NodeVisitor action) {
    for (int nodeIndex = getTotalTrackedNodeCount() - 1; nodeIndex >= 0; nodeIndex--) {
      final ProtoNode node = getNodeByIndex(nodeIndex);

      // No point processing the genesis block.
      if (node.getBlockRoot().equals(Bytes32.ZERO)) {
        continue;
      }
      action.onNode(node, nodeIndex);
    }
  }

  public Object2IntMap<Bytes32> getRootIndices() {
    return indices.getRootIndices();
  }

  ProtoNode getNodeByIndex(final int index) {
    return checkNotNull(nodes.get(index), "Missing node %s", index);
  }

  private interface NodeVisitor {
    void onNode(ProtoNode node, int nodeIndex);
  }
}
