/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.protoarray;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class ProtoArray {

  private int pruneThreshold;

  private UInt64 justifiedEpoch;
  private UInt64 finalizedEpoch;
  // The epoch of our initial startup state
  // When starting from genesis, this value is zero (genesis epoch)
  private final UInt64 initialEpoch;

  private final List<ProtoNode> nodes;
  private final Map<Bytes32, Integer> indices;

  public ProtoArray(
      int pruneThreshold,
      UInt64 justifiedEpoch,
      UInt64 finalizedEpoch,
      UInt64 initialEpoch,
      List<ProtoNode> nodes,
      Map<Bytes32, Integer> indices) {
    this.pruneThreshold = pruneThreshold;
    this.justifiedEpoch = justifiedEpoch;
    this.finalizedEpoch = finalizedEpoch;
    this.initialEpoch = initialEpoch;
    this.nodes = nodes;
    this.indices = indices;
  }

  public Map<Bytes32, Integer> getIndices() {
    return indices;
  }

  public List<ProtoNode> getNodes() {
    return nodes;
  }

  public void setPruneThreshold(int pruneThreshold) {
    this.pruneThreshold = pruneThreshold;
  }

  /**
   * Register a block with the fork choice. It is only sane to supply a `None` parent for the
   * genesis block.
   *
   * @param blockSlot
   * @param blockRoot
   * @param stateRoot
   * @param justifiedEpoch
   * @param finalizedEpoch
   */
  public void onBlock(
      UInt64 blockSlot,
      Bytes32 blockRoot,
      Bytes32 parentRoot,
      Bytes32 stateRoot,
      UInt64 justifiedEpoch,
      UInt64 finalizedEpoch) {
    if (indices.containsKey(blockRoot)) {
      return;
    }

    int nodeIndex = nodes.size();

    ProtoNode node =
        new ProtoNode(
            blockSlot,
            stateRoot,
            blockRoot,
            parentRoot,
            Optional.ofNullable(indices.get(parentRoot)),
            justifiedEpoch,
            finalizedEpoch,
            UInt64.ZERO,
            Optional.empty(),
            Optional.empty());

    indices.put(node.getBlockRoot(), nodeIndex);
    nodes.add(node);

    node.getParentIndex()
        .ifPresent(parentIndex -> maybeUpdateBestChildAndDescendant(parentIndex, nodeIndex));
  }

  /**
   * Follows the best-descendant links to find the best-block (i.e., head-block).
   *
   * <p>The result of this function is not guaranteed to be accurate if `onBlock` has been called
   * without a subsequent `applyScoreChanges` call. This is because `onBlock` does not attempt to
   * walk backwards through the tree and update the best child / best descendant links.
   *
   * @param justifiedRoot
   * @return
   */
  public Bytes32 findHead(Bytes32 justifiedRoot) {
    int justifiedIndex =
        checkNotNull(indices.get(justifiedRoot), "ProtoArray: Unknown justified root");
    ProtoNode justifiedNode =
        checkNotNull(nodes.get(justifiedIndex), "ProtoArray: Unknown justified index");

    int bestDescendantIndex = justifiedNode.getBestDescendantIndex().orElse(justifiedIndex);
    ProtoNode bestNode =
        checkNotNull(nodes.get(bestDescendantIndex), "ProtoArray: Unknown best descendant index");

    // Perform a sanity check that the node is indeed valid to be the head.
    if (!nodeIsViableForHead(bestNode)) {
      throw new RuntimeException("ProtoArray: Best node is not viable for head");
    }

    return bestNode.getBlockRoot();
  }

  /**
   * Iterate backwards through the array, touching all nodes and their parents and potentially the
   * bestChildIndex of each parent.
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
   *
   * @param deltas
   * @param justifiedEpoch
   * @param finalizedEpoch
   */
  public void applyScoreChanges(List<Long> deltas, UInt64 justifiedEpoch, UInt64 finalizedEpoch) {
    checkArgument(deltas.size() == indices.size(), "ProtoArray: Invalid delta length");

    if (!justifiedEpoch.equals(this.justifiedEpoch)
        || !finalizedEpoch.equals(this.finalizedEpoch)) {
      this.justifiedEpoch = justifiedEpoch;
      this.finalizedEpoch = finalizedEpoch;
    }

    // Iterate backwards through all indices in `this.nodes`.
    for (int nodeIndex = nodes.size() - 1; nodeIndex >= 0; nodeIndex--) {
      ProtoNode node = nodes.get(nodeIndex);

      // There is no need to adjust the balances or manage parent of the zero hash since it
      // is an alias to the genesis block. The weight applied to the genesis block is
      // irrelevant as we _always_ choose it and it's impossible for it to have a parent.
      if (node.getBlockRoot().equals(Bytes32.ZERO)) {
        continue;
      }

      long nodeDelta = deltas.get(nodeIndex);
      node.adjustWeight(nodeDelta);

      if (node.getParentIndex().isPresent()) {
        int parentIndex = node.getParentIndex().get();
        deltas.set(parentIndex, deltas.get(parentIndex) + nodeDelta);
        maybeUpdateBestChildAndDescendant(parentIndex, nodeIndex);
      }
    }
  }

  /**
   * Update the tree with new finalization information. The tree is only actually pruned if both of
   * the two following criteria are met:
   *
   * <ul>
   *   <li>The supplied finalized epoch and root are different to the current values.
   *   <li>The number of nodes in `this` is at least `this.pruneThreshold`.
   * </ul>
   *
   * @param finalizedRoot
   */
  public void maybePrune(Bytes32 finalizedRoot) {
    int finalizedIndex =
        checkNotNull(indices.get(finalizedRoot), "ProtoArray: Finalized root is unknown");

    if (finalizedIndex < pruneThreshold) {
      // Pruning at small numbers incurs more cost than benefit.
      return;
    }

    // Remove the `indices` key/values for all the to-be-deleted nodes.
    for (int nodeIndex = 0; nodeIndex < finalizedIndex; nodeIndex++) {
      Bytes32 root =
          checkNotNull(nodes.get(nodeIndex), "ProtoArray: Invalid node index").getBlockRoot();
      indices.remove(root);
    }

    // Drop all the nodes prior to finalization.
    nodes.subList(0, finalizedIndex).clear();

    // Adjust the indices map.
    indices.replaceAll(
        (key, value) -> {
          int newIndex = value - finalizedIndex;
          checkState(newIndex >= 0, "ProtoArray: New array index less than 0.");
          return newIndex;
        });

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
   *   <li>The child is not the best child but becomes the best child. - The child is not the best
   *       child and does not become the best child.
   * </ul>
   *
   * @param parentIndex
   * @param childIndex
   */
  @SuppressWarnings("StatementWithEmptyBody")
  private void maybeUpdateBestChildAndDescendant(int parentIndex, int childIndex) {
    ProtoNode child = nodes.get(childIndex);
    ProtoNode parent = nodes.get(parentIndex);

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
                ProtoNode bestChild = nodes.get(bestChildIndex);

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

  /**
   * Helper for maybeUpdateBestChildAndDescendant
   *
   * @param parent
   * @param childIndex
   */
  private void changeToChild(ProtoNode parent, int childIndex) {
    ProtoNode child = nodes.get(childIndex);
    parent.setBestChildIndex(Optional.of(childIndex));
    parent.setBestDescendantIndex(Optional.of(child.getBestDescendantIndex().orElse(childIndex)));
  }

  /**
   * Helper for maybeUpdateBestChildAndDescendant
   *
   * @param parent
   */
  private void changeToNone(ProtoNode parent) {
    parent.setBestChildIndex(Optional.empty());
    parent.setBestDescendantIndex(Optional.empty());
  }

  /**
   * Indicates if the node itself is viable for the head, or if it's best descendant is viable for
   * the head.
   *
   * @param node
   * @return
   */
  private boolean nodeLeadsToViableHead(ProtoNode node) {
    boolean bestDescendantIsViableForHead =
        node.getBestDescendantIndex().map(nodes::get).map(this::nodeIsViableForHead).orElse(false);

    return bestDescendantIsViableForHead || nodeIsViableForHead(node);
  }

  /**
   * This is the equivalent to the <a
   * href="https://github.com/ethereum/eth2.0-specs/blob/v0.10.0/specs/phase0/fork-choice.md#filter_block_tree">filter_block_tree</a>
   * function in the eth2 spec:
   *
   * <p>Any node that has a different finalized or justified epoch should not be viable for the
   * head.
   *
   * @param node
   * @return
   */
  private boolean nodeIsViableForHead(ProtoNode node) {
    return (node.getJustifiedEpoch().equals(justifiedEpoch) || justifiedEpoch.equals(initialEpoch))
        && (node.getFinalizedEpoch().equals(finalizedEpoch) || finalizedEpoch.equals(initialEpoch));
  }

  public UInt64 getJustifiedEpoch() {
    return justifiedEpoch;
  }

  public UInt64 getFinalizedEpoch() {
    return finalizedEpoch;
  }

  public UInt64 getInitialEpoch() {
    return initialEpoch;
  }
}
