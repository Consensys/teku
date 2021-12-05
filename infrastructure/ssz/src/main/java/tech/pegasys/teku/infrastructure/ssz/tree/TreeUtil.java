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

package tech.pegasys.teku.infrastructure.ssz.tree;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

/** Misc Backing binary tree utils */
public class TreeUtil {

  public static class ZeroLeafNode extends SimpleLeafNode {
    public ZeroLeafNode(int size) {
      super(Bytes.wrap(new byte[size]));
    }

    @Override
    public String toString() {
      return "(" + getData() + ")";
    }
  }

  public static class ZeroBranchNode extends SimpleBranchNode {
    private final int height;

    public ZeroBranchNode(TreeNode left, TreeNode right, int height) {
      super(left, right);
      this.height = height;
    }

    @Override
    public String toString() {
      return "(ZeroBranch-" + height + ")";
    }
  }

  @VisibleForTesting public static final TreeNode[] ZERO_TREES;

  public static ImmutableMap<Bytes32, TreeNode> ZERO_TREES_BY_ROOT;

  static {
    ZERO_TREES = new TreeNode[64];
    ZERO_TREES[0] = LeafNode.EMPTY_LEAF;
    final ImmutableMap.Builder<Bytes32, TreeNode> mapBuilder = ImmutableMap.builder();
    for (int i = 1; i < ZERO_TREES.length; i++) {
      ZERO_TREES[i] = new ZeroBranchNode(ZERO_TREES[i - 1], ZERO_TREES[i - 1], i);
      mapBuilder.put(ZERO_TREES[i].hashTreeRoot(), ZERO_TREES[i]); // pre-cache
    }
    ZERO_TREES_BY_ROOT = mapBuilder.build();
  }

  public static int bitsCeilToBytes(int bits) {
    return (bits + 7) / 8;
  }

  public static long bitsCeilToBytes(long bits) {
    return (bits + 7) / 8;
  }

  /**
   * Creates a binary tree with `nextPowerOf2(maxLength)` width and following leaf nodes <code>
   * [zeroElement] * maxLength + [ZERO_LEAF] * (nextPowerOf2(maxLength) - maxLength)
   * </code>
   *
   * @param maxLength max number of leaf nodes
   * @param defaultNode default leaf element. For complex vectors it could be default vector element
   *     struct subtree
   */
  public static TreeNode createDefaultTree(long maxLength, TreeNode defaultNode) {
    return createTree(
        defaultNode, LeafNode.EMPTY_LEAF.equals(defaultNode) ? 0 : maxLength, treeDepth(maxLength));
  }

  /** Creates a binary tree of width `nextPowerOf2(leafNodes.size())` with specific leaf nodes */
  public static TreeNode createTree(List<? extends TreeNode> children) {
    return createTree(children, treeDepth(children.size()));
  }

  private static TreeNode createTree(TreeNode defaultNode, long defaultNodesCount, int depth) {
    if (defaultNodesCount == 0) {
      return ZERO_TREES[depth];
    } else if (depth == 0) {
      checkArgument(defaultNodesCount == 1);
      return defaultNode;
    } else {
      long leftNodesCount = Math.min(defaultNodesCount, 1 << (depth - 1));
      long rightNodesCount = defaultNodesCount - leftNodesCount;
      TreeNode lTree = createTree(defaultNode, leftNodesCount, depth - 1);
      TreeNode rTree =
          leftNodesCount == rightNodesCount
              ? lTree
              : createTree(defaultNode, rightNodesCount, depth - 1);
      return BranchNode.create(lTree, rTree);
    }
  }

  public static TreeNode createTree(List<? extends TreeNode> leafNodes, int depth) {
    if (leafNodes.isEmpty()) {
      return ZERO_TREES[depth];
    } else if (depth == 0) {
      checkArgument(leafNodes.size() == 1);
      return leafNodes.get(0);
    } else {
      long index = 1L << (depth - 1);
      int iIndex = index > leafNodes.size() ? leafNodes.size() : (int) index;

      List<? extends TreeNode> leftSublist = leafNodes.subList(0, iIndex);
      List<? extends TreeNode> rightSublist = leafNodes.subList(iIndex, leafNodes.size());
      return BranchNode.create(
          createTree(leftSublist, depth - 1), createTree(rightSublist, depth - 1));
    }
  }

  public static TreeNode createTree(
      List<? extends TreeNode> leafNodes, TreeNode defaultNode, int depth) {
    if (leafNodes.isEmpty()) {
      if (depth > 0) {
        TreeNode defaultChild = createTree(leafNodes, defaultNode, depth - 1);
        return BranchNode.create(defaultChild, defaultChild);
      } else {
        return defaultNode;
      }
    } else if (depth == 0) {
      checkArgument(leafNodes.size() == 1);
      return leafNodes.get(0);
    } else {
      long index = 1L << (depth - 1);
      int iIndex = index > leafNodes.size() ? leafNodes.size() : (int) index;

      List<? extends TreeNode> leftSublist = leafNodes.subList(0, iIndex);
      List<? extends TreeNode> rightSublist = leafNodes.subList(iIndex, leafNodes.size());
      return BranchNode.create(
          createTree(leftSublist, defaultNode, depth - 1),
          createTree(rightSublist, defaultNode, depth - 1));
    }
  }

  public static long nextPowerOf2(long x) {
    return x <= 1 ? 1 : Long.highestOneBit(x - 1) << 1;
  }

  public static int treeDepth(long maxChunks) {
    return Long.bitCount(nextPowerOf2(maxChunks) - 1);
  }

  /**
   * Iterate all leaf tree nodes starting from the node with general index {@code fromGeneralIndex}
   * (including all node descendants if this is a branch node) and ending with the node with general
   * index {@code toGeneralIndex} inclusive (including all node descendants if this is a branch
   * node). On every {@link LeafNode} the supplied {@code visitor} is invoked.
   */
  @VisibleForTesting
  static void iterateLeaves(
      TreeNode node, long fromGeneralIndex, long toGeneralIndex, Consumer<LeafNode> visitor) {
    node.iterateRange(
        fromGeneralIndex,
        toGeneralIndex,
        (n, idx) -> {
          if (n instanceof LeafNode) {
            visitor.accept((LeafNode) n);
          }
          return true;
        });
  }

  public static void iterateLeavesData(
      TreeNode node, long fromGeneralIndex, long toGeneralIndex, Consumer<Bytes> visitor) {
    node.iterateRange(
        fromGeneralIndex,
        toGeneralIndex,
        (n, idx) -> {
          if (n instanceof LeafDataNode) {
            visitor.accept(((LeafDataNode) n).getData());
          }
          return true;
        });
  }

  public static Bytes concatenateLeavesData(TreeNode tree) {
    List<Bytes> leavesData = new ArrayList<>();
    iterateLeavesData(
        tree, GIndexUtil.LEFTMOST_G_INDEX, GIndexUtil.RIGHTMOST_G_INDEX, leavesData::add);
    return Bytes.wrap(leavesData.toArray(new Bytes[0]));
  }
}
