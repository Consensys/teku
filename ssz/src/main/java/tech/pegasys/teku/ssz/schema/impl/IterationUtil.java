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

package tech.pegasys.teku.ssz.schema.impl;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.ssz.tree.GIndexUtil.gIdxCompose;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ssz.tree.GIndexUtil;
import tech.pegasys.teku.ssz.tree.TreeNode;

public class IterationUtil {

  public static void visitNodesToDepth(
      final NodeVisitor nodeVisitor,
      final int maxBranchLevelsSkipped,
      final TreeNode rootNode,
      final long rootGIndex,
      final int depthToVisit) {
    checkArgument(depthToVisit > 0, "Depth must be positive");
    if (nodeVisitor.canSkipBranch(rootNode.hashTreeRoot(), rootGIndex)) {
      return;
    }
    if (depthToVisit <= maxBranchLevelsSkipped) {
      visitChildNodesAtDepth(nodeVisitor, rootNode, rootGIndex, depthToVisit);
    } else {
      visitIntermediateBranches(
          nodeVisitor, maxBranchLevelsSkipped, rootNode, rootGIndex, depthToVisit);
    }
  }

  private static void visitIntermediateBranches(
      final NodeVisitor nodeVisitor,
      final int maxBranchLevelsSkipped,
      final TreeNode rootNode,
      final long rootGIndex,
      final int depthToVisit) {
    // Max compression depth exceeded so will need to record some interim branch nodes
    final int childCount = Math.toIntExact(1L << maxBranchLevelsSkipped);
    final Bytes32[] childRoots = new Bytes32[childCount];
    final int remainingDepth = depthToVisit - maxBranchLevelsSkipped;
    for (int childIndex = 0; childIndex < childCount; childIndex++) {
      final long childRelativeGIndex =
          GIndexUtil.gIdxChildGIndex(GIndexUtil.SELF_G_INDEX, childIndex, maxBranchLevelsSkipped);
      final TreeNode childNode = rootNode.get(childRelativeGIndex);
      childRoots[childIndex] = childNode.hashTreeRoot();
      visitNodesToDepth(
          nodeVisitor,
          maxBranchLevelsSkipped,
          childNode,
          gIdxCompose(rootGIndex, childRelativeGIndex),
          remainingDepth);
    }
    nodeVisitor.onBranchNode(
        rootNode.hashTreeRoot(), rootGIndex, maxBranchLevelsSkipped, childRoots);
  }

  private static void visitChildNodesAtDepth(
      final NodeVisitor nodeVisitor,
      final TreeNode rootNode,
      final long rootGIndex,
      final int depthToVisit) {
    final int childCount = Math.toIntExact(1L << depthToVisit);
    final Bytes32[] childRoots = new Bytes32[childCount];
    for (int childIndex = 0; childIndex < childCount; childIndex++) {
      final long childRelativeGIndex =
          GIndexUtil.gIdxChildGIndex(GIndexUtil.SELF_G_INDEX, childIndex, depthToVisit);
      final TreeNode childNode = rootNode.get(childRelativeGIndex);
      childRoots[childIndex] = childNode.hashTreeRoot();
      nodeVisitor.onTargetDepthNode(childNode, gIdxCompose(rootGIndex, childRelativeGIndex));
    }
    nodeVisitor.onBranchNode(rootNode.hashTreeRoot(), rootGIndex, depthToVisit, childRoots);
  }

  public interface NodeVisitor {

    /**
     * Called prior to visiting a branch or its descendants to determine if the branch needs to be
     * visited.
     *
     * @param root the hash tree root of the branch node
     * @param gIndex the generalized index of the branch node
     * @return true if the branch node and all its descendants can be skipped, false to iterate into
     *     the descendants
     */
    boolean canSkipBranch(Bytes32 root, long gIndex);

    /**
     * Called when an intermediate branch node is visited. Multiple levels of branch nodes may be
     * skipped to optimise iteration and storage, in which case the children are {@code depth}
     * levels from the branch node.
     *
     * @param root the hash tree root of the branch node
     * @param gIndex the generalised index of the branch node
     * @param depth the number of tree levels being skipped. ie the depth of the tree from the
     *     branch node to the children
     * @param children the non-empty children at the specified depth from the branch node.
     */
    void onBranchNode(Bytes32 root, long gIndex, int depth, Bytes32[] children);

    /**
     * Called when a descendant node at the requested depth is reached.
     *
     * @param node the node at the target depth
     * @param gIndex the generalized index of the node
     */
    void onTargetDepthNode(TreeNode node, long gIndex);
  }
}
