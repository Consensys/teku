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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.ssz.tree.GIndexUtil.SELF_G_INDEX;

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.verification.VerificationMode;
import tech.pegasys.teku.ssz.schema.impl.IterationUtil.NodeVisitor;
import tech.pegasys.teku.ssz.tree.GIndexUtil;
import tech.pegasys.teku.ssz.tree.LeafNode;
import tech.pegasys.teku.ssz.tree.TreeNode;
import tech.pegasys.teku.ssz.tree.TreeUtil;

class IterationUtilTest {

  private static final int UNLIMITED_SKIP = Integer.MAX_VALUE;
  private final NodeVisitor visitor = mock(NodeVisitor.class);
  private final InOrder inOrder = inOrder(visitor);

  @BeforeEach
  void setUp() {
    when(visitor.canSkipBranch(any(), anyLong())).thenReturn(false);
  }

  @Test
  void shouldVisitBothChildrenOfBranchNode() {
    final LeafNode left = LeafNode.create(Bytes.of(1));
    final LeafNode right = LeafNode.create(Bytes.of(2));
    final TreeNode rootNode = TreeUtil.createTree(List.of(left, right));
    IterationUtil.visitNodesToDepth(visitor, UNLIMITED_SKIP, rootNode, SELF_G_INDEX, 1);
    inOrder.verify(visitor).onTargetDepthNode(left, GIndexUtil.LEFT_CHILD_G_INDEX);
    inOrder.verify(visitor).onTargetDepthNode(right, GIndexUtil.RIGHT_CHILD_G_INDEX);
    inOrder
        .verify(visitor)
        .onBranchNode(rootNode.hashTreeRoot(), SELF_G_INDEX, 1, roots(left, right));
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  void shouldVisitChildrenAtSpecifiedDepth() {
    final int depth = 5;
    final List<TreeNode> children = createChildrenForDepth(depth);
    final TreeNode rootNode = TreeUtil.createTree(children, depth);

    IterationUtil.visitNodesToDepth(visitor, UNLIMITED_SKIP, rootNode, SELF_G_INDEX, depth);

    verifyChildrenVisited(children, depth);
    inOrder
        .verify(visitor)
        .onBranchNode(rootNode.hashTreeRoot(), SELF_G_INDEX, depth, roots(children));
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  void shouldReportGeneralisedIndexCorrectlyWhenRootNodeIsNotSelfIndex() {
    final int rootGIndex = 12;
    final LeafNode left = LeafNode.create(Bytes.of(1));
    final LeafNode right = LeafNode.create(Bytes.of(2));
    final TreeNode rootNode = TreeUtil.createTree(List.of(left, right));
    IterationUtil.visitNodesToDepth(visitor, UNLIMITED_SKIP, rootNode, rootGIndex, 1);
    inOrder.verify(visitor).onTargetDepthNode(left, GIndexUtil.gIdxLeftGIndex(rootGIndex));
    inOrder.verify(visitor).onTargetDepthNode(right, GIndexUtil.gIdxRightGIndex(rootGIndex));
    inOrder
        .verify(visitor)
        .onBranchNode(rootNode.hashTreeRoot(), rootGIndex, 1, roots(left, right));
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  void shouldVisitIntermediateBranchesWhenDepthExceedsMaxSkipLevels_evenInterval() {
    final int depth = 4;
    final int maxBranchLevelsSkipped = 2;
    final List<TreeNode> children = createChildrenForDepth(depth);
    final TreeNode rootNode = TreeUtil.createTree(children, depth);

    IterationUtil.visitNodesToDepth(visitor, maxBranchLevelsSkipped, rootNode, SELF_G_INDEX, depth);

    // Visit branch nodes 5 levels deep
    final List<TreeNode> branchNodes = getNodesAtDepth(rootNode, maxBranchLevelsSkipped);
    // Children should be evenly split between intermediate branch nodes
    final int childrenPerBranchNode = children.size() / branchNodes.size();
    for (int i = 0; i < branchNodes.size(); i++) {
      final TreeNode branchNode = branchNodes.get(i);
      final long branchGIndex = GIndexUtil.gIdxChildGIndex(SELF_G_INDEX, i, maxBranchLevelsSkipped);
      final int firstChildIndex = i * childrenPerBranchNode;
      final List<TreeNode> branchChildren =
          children.subList(firstChildIndex, firstChildIndex + childrenPerBranchNode);
      // Visit children first
      verifyChildrenVisited(branchGIndex, branchChildren, maxBranchLevelsSkipped);
      // Then branch node itself
      inOrder
          .verify(visitor)
          .onBranchNode(
              branchNode.hashTreeRoot(),
              branchGIndex,
              maxBranchLevelsSkipped,
              roots(branchChildren));
    }
    // Then root node
    inOrder
        .verify(visitor)
        .onBranchNode(
            rootNode.hashTreeRoot(), SELF_G_INDEX, maxBranchLevelsSkipped, roots(branchNodes));
  }

  @Test
  void shouldVisitIntermediateBranchesWhenDepthExceedsMaxSkipLevels_unevenInterval() {
    final int depth = 4;
    final int maxBranchLevelsSkipped = 3;
    final List<TreeNode> children = createChildrenForDepth(depth);
    final TreeNode rootNode = TreeUtil.createTree(children, depth);

    IterationUtil.visitNodesToDepth(visitor, maxBranchLevelsSkipped, rootNode, SELF_G_INDEX, depth);

    // Visit branch nodes 5 levels deep
    final List<TreeNode> branchNodes = getNodesAtDepth(rootNode, maxBranchLevelsSkipped);
    // Children should be evenly split between intermediate branch nodes
    final int childrenPerBranchNode = children.size() / branchNodes.size();
    final int remainingDepth = depth - maxBranchLevelsSkipped;
    for (int i = 0; i < branchNodes.size(); i++) {
      final TreeNode branchNode = branchNodes.get(i);
      final long branchGIndex = GIndexUtil.gIdxChildGIndex(SELF_G_INDEX, i, maxBranchLevelsSkipped);
      final int firstChildIndex = i * childrenPerBranchNode;
      final List<TreeNode> branchChildren =
          children.subList(firstChildIndex, firstChildIndex + childrenPerBranchNode);
      // Visit children first
      verifyChildrenVisited(branchGIndex, branchChildren, remainingDepth);
      // Then branch node itself
      inOrder
          .verify(visitor)
          .onBranchNode(
              branchNode.hashTreeRoot(), branchGIndex, remainingDepth, roots(branchChildren));
    }
    // Then root node
    inOrder
        .verify(visitor)
        .onBranchNode(
            rootNode.hashTreeRoot(), SELF_G_INDEX, maxBranchLevelsSkipped, roots(branchNodes));
  }

  @Test
  void shouldNotVisitAnyNodesIfRootCanBeSkipped() {
    final int depth = 5;
    final List<TreeNode> children = createChildrenForDepth(depth);
    final TreeNode rootNode = TreeUtil.createTree(children, depth);

    when(visitor.canSkipBranch(rootNode.hashTreeRoot(), SELF_G_INDEX)).thenReturn(true);

    IterationUtil.visitNodesToDepth(visitor, UNLIMITED_SKIP, rootNode, SELF_G_INDEX, depth);

    inOrder.verify(visitor).canSkipBranch(rootNode.hashTreeRoot(), SELF_G_INDEX);
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  void shouldSkipIntermediateBranchesWhenPossible() {
    final int depth = 4;
    final int maxBranchLevelsSkipped = 2;
    final List<TreeNode> children = createChildrenForDepth(depth);
    final TreeNode rootNode = TreeUtil.createTree(children, depth);
    final List<TreeNode> branchNodes = getNodesAtDepth(rootNode, maxBranchLevelsSkipped);
    final Set<Integer> skippableBranchIndices = Set.of(0, 2);
    // Can skip intermediate branches 0 and 2, but should store the others
    skippableBranchIndices.forEach(
        index ->
            when(visitor.canSkipBranch(
                    branchNodes.get(index).hashTreeRoot(),
                    GIndexUtil.gIdxChildGIndex(SELF_G_INDEX, index, maxBranchLevelsSkipped)))
                .thenReturn(true));

    IterationUtil.visitNodesToDepth(visitor, maxBranchLevelsSkipped, rootNode, SELF_G_INDEX, depth);

    // Visit branch nodes 5 levels deep

    // Children should be evenly split between intermediate branch nodes
    final int childrenPerBranchNode = children.size() / branchNodes.size();
    for (int i = 0; i < branchNodes.size(); i++) {
      final TreeNode branchNode = branchNodes.get(i);
      final long branchGIndex = GIndexUtil.gIdxChildGIndex(SELF_G_INDEX, i, maxBranchLevelsSkipped);
      final int firstChildIndex = i * childrenPerBranchNode;
      final List<TreeNode> branchChildren =
          children.subList(firstChildIndex, firstChildIndex + childrenPerBranchNode);
      // Should skip all nodes under branches 0 and 2
      final VerificationMode verificationMode =
          skippableBranchIndices.contains(i) ? never() : times(1);
      // Visit children first
      verifyChildrenVisited(verificationMode, branchGIndex, branchChildren, maxBranchLevelsSkipped);
      // Then branch node itself
      inOrder
          .verify(visitor, verificationMode)
          .onBranchNode(
              branchNode.hashTreeRoot(),
              branchGIndex,
              maxBranchLevelsSkipped,
              roots(branchChildren));
    }
    // Then root node
    inOrder
        .verify(visitor)
        .onBranchNode(
            rootNode.hashTreeRoot(), SELF_G_INDEX, maxBranchLevelsSkipped, roots(branchNodes));
  }

  @Test
  void shouldNotStoreUnnecessaryChildNodes_singleBranchLevel() {
    final int depth = 4;
    final int maxBranchLevelsSkipped = Integer.MAX_VALUE;
    final List<TreeNode> children = createChildrenForDepth(depth);

    final TreeNode rootNode = TreeUtil.createTree(children, depth);
    final long rootNodeGIndex = 20;
    final int lastUsefulChildIndex = 5;
    final long lastUsefulGIndex =
        GIndexUtil.gIdxChildGIndex(rootNodeGIndex, lastUsefulChildIndex, depth);

    IterationUtil.visitNodesToDepth(
        visitor, maxBranchLevelsSkipped, rootNode, rootNodeGIndex, depth, lastUsefulGIndex);

    final List<TreeNode> visitedChildren = children.subList(0, lastUsefulChildIndex);
    verifyChildrenVisited(rootNodeGIndex, visitedChildren, depth);
    verify(visitor)
        .onBranchNode(rootNode.hashTreeRoot(), rootNodeGIndex, depth, roots(visitedChildren));
  }

  private List<TreeNode> getNodesAtDepth(final TreeNode rootNode, final int depth) {
    final List<TreeNode> nodes = new ArrayList<>();
    final long childCount = 1L << depth;
    for (long i = 0; i < childCount; i++) {
      final long childGIndex = GIndexUtil.gIdxChildGIndex(SELF_G_INDEX, i, depth);
      nodes.add(rootNode.get(childGIndex));
    }
    return nodes;
  }

  private void verifyChildrenVisited(final List<TreeNode> children, final int depth) {
    verifyChildrenVisited(SELF_G_INDEX, children, depth);
  }

  private void verifyChildrenVisited(
      final long rootGIndex, final List<TreeNode> children, final int depth) {
    verifyChildrenVisited(times(1), rootGIndex, children, depth);
  }

  private void verifyChildrenVisited(
      final VerificationMode verificationMode,
      final long rootGIndex,
      final List<TreeNode> children,
      final int depth) {
    for (int i = 0; i < children.size(); i++) {
      final TreeNode child = children.get(i);
      inOrder
          .verify(visitor, verificationMode)
          .onTargetDepthNode(child, GIndexUtil.gIdxChildGIndex(rootGIndex, i, depth));
    }
  }

  private List<TreeNode> createChildrenForDepth(final int depth) {
    final List<TreeNode> children = new ArrayList<>();
    for (int i = 0; i < (int) 1L << depth; i++) {
      children.add(LeafNode.create(Bytes.ofUnsignedInt(i, ByteOrder.BIG_ENDIAN)));
    }
    return children;
  }

  private Bytes32[] roots(final Collection<TreeNode> nodes) {
    return roots(nodes.stream());
  }

  private Bytes32[] roots(final TreeNode... nodes) {
    return roots(Stream.of(nodes));
  }

  private Bytes32[] roots(final Stream<TreeNode> nodes) {
    return nodes.map(TreeNode::hashTreeRoot).toArray(Bytes32[]::new);
  }
}
