/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.infrastructure.ssz.tree;

import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes32;

public class MerkleUtil {

  /**
   * Returns the merkle inclusion proof for the field at the given leaf index in the given tree. See
   * https://github.com/ethereum/consensus-specs/blob/dev/ssz/merkle-proofs.md#merkle-multiproofs
   * for more info on merkle proofs.
   */
  public static List<Bytes32> constructMerkleProof(TreeNode root, long leafGeneralizedIndex) {
    if (leafGeneralizedIndex <= 1) {
      // Nothing to prove or invalid index.
      return Collections.emptyList();
    }
    // Path represented by generalized indices.
    List<Long> rootToLeafPath = getPathToNode(leafGeneralizedIndex);

    // The sibling of every node along the path is part of the proof.
    List<Bytes32> proof = new ArrayList<>();

    for (int i = 0; i < rootToLeafPath.size() - 1; i++) {
      long currentNodeIndex = rootToLeafPath.get(i);
      long nextNodeIndex = rootToLeafPath.get(i + 1);

      if (GIndexUtil.gIdxLeftGIndex(currentNodeIndex) == nextNodeIndex) {
        // Next node is left, so the right node is in the proof.
        proof.add(root.get(GIndexUtil.gIdxRightGIndex(currentNodeIndex)).hashTreeRoot());
      } else if (GIndexUtil.gIdxRightGIndex(currentNodeIndex) == nextNodeIndex) {
        // Next node is right, so the left node is in the proof.
        proof.add(root.get(GIndexUtil.gIdxLeftGIndex(currentNodeIndex)).hashTreeRoot());
      } else {
        throw new IllegalArgumentException("Invalid path from root to leaf: " + rootToLeafPath);
      }
    }

    Collections.reverse(proof);
    return Collections.unmodifiableList(proof);
  }

  /**
   * Returns the path from the root to the specified node as an array of generalized indices.
   * path[0] is the root itself (gIndex of 1) path[path.length - 1] is the node (gIndex of
   * `nodeIndex`)
   */
  @VisibleForTesting
  static List<Long> getPathToNode(long nodeIndex) {
    if (nodeIndex < 1) {
      throw new IllegalArgumentException("Invalid node index: " + nodeIndex);
    }

    // Breadth-first search maintaining the node index (relative to root) and path to the node.
    Queue<Pair<Long, List<Long>>> queue = new ArrayDeque<>();
    queue.add(
        Pair.of(
            GIndexUtil.SELF_G_INDEX,
            new ArrayList<>(Collections.singleton(GIndexUtil.SELF_G_INDEX))));
    int depth = 0;

    // Begin with root and terminate upon finding the specified node.
    while (!queue.isEmpty() && depth < GIndexUtil.MAX_DEPTH) {
      Pair<Long, List<Long>> indexAndPath = queue.remove();

      long currentIndex = indexAndPath.getLeft();
      List<Long> currentPath = indexAndPath.getRight();

      if (currentIndex == nodeIndex) {
        // Found the correct node.
        return currentPath;
      }

      // Traverse left and right children.
      long leftIndex = GIndexUtil.gIdxLeftGIndex(currentIndex);

      List<Long> leftPath = new ArrayList<>(currentPath);
      leftPath.add(leftIndex);

      queue.add(Pair.of(leftIndex, leftPath));

      long rightIndex = GIndexUtil.gIdxRightGIndex(currentIndex);

      List<Long> rightPath = new ArrayList<>(currentPath);
      rightPath.add(rightIndex);

      queue.add(Pair.of(rightIndex, rightPath));

      // Ensures termination for large values of `nodeIndex`.
      depth++;
    }

    throw new IllegalArgumentException("Path to node not found for node index: " + nodeIndex);
  }
}
