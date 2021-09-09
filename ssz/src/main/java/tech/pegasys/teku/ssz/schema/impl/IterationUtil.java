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
import tech.pegasys.teku.ssz.tree.GIndexUtil.NodeRelation;
import tech.pegasys.teku.ssz.tree.TreeNode;
import tech.pegasys.teku.ssz.tree.TreeNodeStore;

public class IterationUtil {

  public static void visitNodesToDepth(
      final NodeVisitor nodeVisitor,
      final int maxBranchLevelsSkipped,
      final TreeNode rootNode,
      final long rootGIndex,
      final int depthToVisit) {
    visitNodesToDepth(
        nodeVisitor,
        maxBranchLevelsSkipped,
        rootNode,
        rootGIndex,
        depthToVisit,
        GIndexUtil.gIdxRightmostFrom(rootGIndex));
  }

  public static void visitNodesToDepth(
      final NodeVisitor nodeVisitor,
      final int maxBranchLevelsSkipped,
      final TreeNode rootNode,
      final long rootGIndex,
      final int depthToVisit,
      final long lastUsefulGIndex) {
    checkArgument(depthToVisit > 0, "Depth must be positive");
    if (nodeVisitor.canSkipBranch(rootNode.hashTreeRoot(), rootGIndex)) {
      return;
    }
    if (depthToVisit <= maxBranchLevelsSkipped) {
      visitChildNodesAtDepth(nodeVisitor, rootNode, rootGIndex, depthToVisit, lastUsefulGIndex);
    } else {
      visitIntermediateBranches(
          nodeVisitor,
          maxBranchLevelsSkipped,
          rootNode,
          rootGIndex,
          depthToVisit,
          lastUsefulGIndex);
    }
  }

  private static void visitIntermediateBranches(
      final NodeVisitor nodeVisitor,
      final int maxBranchLevelsSkipped,
      final TreeNode rootNode,
      final long rootGIndex,
      final int depthToVisit,
      final long lastUsefulGIndex) {
    // Max compression depth exceeded so will need to record some interim branch nodes

    final int remainingDepth = depthToVisit - maxBranchLevelsSkipped;
    final int childCount =
        getUsefulChildCount(rootGIndex, maxBranchLevelsSkipped, remainingDepth, lastUsefulGIndex);
    final Bytes32[] childRoots = new Bytes32[childCount];
    for (int childIndex = 0; childIndex < childCount; childIndex++) {
      final long childRelativeGIndex =
          GIndexUtil.gIdxChildGIndex(GIndexUtil.SELF_G_INDEX, childIndex, maxBranchLevelsSkipped);
      final long childGIndex = gIdxCompose(rootGIndex, childRelativeGIndex);

      final TreeNode childNode = rootNode.get(childRelativeGIndex);
      childRoots[childIndex] = childNode.hashTreeRoot();
      visitNodesToDepth(
          nodeVisitor,
          maxBranchLevelsSkipped,
          childNode,
          childGIndex,
          remainingDepth,
          lastUsefulGIndex);
    }
    nodeVisitor.onBranchNode(
        rootNode.hashTreeRoot(), rootGIndex, maxBranchLevelsSkipped, childRoots);
  }

  private static int getUsefulChildCount(
      final long rootGIndex,
      final int depthFromRoot,
      final int depthFromLastUsefulGIndex,
      final long lastUsefulGIndex) {
    // Find parent GIndex of lastUsefulGIndex at depth to be stored
    final long ancestorGIndexAtTargetDepth = lastUsefulGIndex >>> depthFromLastUsefulGIndex;

    final int childCount;
    if (GIndexUtil.gIdxCompare(rootGIndex, ancestorGIndexAtTargetDepth)
        == NodeRelation.Predecessor) {
      childCount =
          GIndexUtil.gIdxChildIndexFromGIndex(ancestorGIndexAtTargetDepth, depthFromRoot) + 1;
    } else {
      childCount = Math.toIntExact(1L << depthFromRoot);
    }
    return childCount;
  }

  private static void visitChildNodesAtDepth(
      final NodeVisitor nodeVisitor,
      final TreeNode rootNode,
      final long rootGIndex,
      final int depthToVisit,
      final long lastUsefulGIndex) {
    final int childCount;
    if (GIndexUtil.gIdxCompare(rootGIndex, lastUsefulGIndex) == NodeRelation.Predecessor) {
      childCount = GIndexUtil.gIdxChildIndexFromGIndex(lastUsefulGIndex, depthToVisit) + 1;
    } else {
      childCount = Math.toIntExact(1L << depthToVisit);
    }
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

  public interface NodeVisitor extends TargetDepthNodeVisitor {

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
  }

  public interface TargetDepthNodeVisitor {
    /**
     * Called when a descendant node at the requested depth is reached.
     *
     * @param node the node at the target depth
     * @param gIndex the generalized index of the node
     */
    void onTargetDepthNode(TreeNode node, long gIndex);
  }

  public static class TreNodeVisitorAdapter implements NodeVisitor {
    private final TreeNodeStore nodeVisitor;
    private final TargetDepthNodeVisitor targetDepthNodeVisitor;

    public TreNodeVisitorAdapter(
        final TreeNodeStore nodeVisitor, final TargetDepthNodeVisitor targetDepthNodeVisitor) {
      this.nodeVisitor = nodeVisitor;
      this.targetDepthNodeVisitor = targetDepthNodeVisitor;
    }

    @Override
    public boolean canSkipBranch(final Bytes32 root, final long gIndex) {
      return nodeVisitor.canSkipBranch(root, gIndex);
    }

    @Override
    public void onBranchNode(
        final Bytes32 root, final long gIndex, final int depth, final Bytes32[] children) {
      nodeVisitor.storeBranchNode(root, gIndex, depth, children);
    }

    @Override
    public void onTargetDepthNode(final TreeNode node, final long gIndex) {
      targetDepthNodeVisitor.onTargetDepthNode(node, gIndex);
    }
  }
}
