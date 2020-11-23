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

package tech.pegasys.teku.ssz.backing.tree;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.ssz.backing.Utils;
import tech.pegasys.teku.ssz.backing.tree.TreeNode.BranchNode;
import tech.pegasys.teku.ssz.backing.tree.TreeNode.LeafNode;
import tech.pegasys.teku.ssz.backing.tree.TreeNodeImpl.BranchNodeImpl;
import tech.pegasys.teku.ssz.backing.tree.TreeNodeImpl.LeafNodeImpl;

/** Misc Backing binary tree utils */
public class TreeUtil {

  private static class ZeroLeafNode extends LeafNodeImpl {
    public ZeroLeafNode(int size) {
      super(Bytes.wrap(new byte[size]));
    }

    @Override
    public String toString() {
      return "(" + getData() + ")";
    }
  }

  private static class ZeroBranchNode extends BranchNodeImpl {
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

  /**
   * Pre-allocated leaf nodes with the data consisting of 0, 1, 2, ..., 32 zero bytes Worth to
   * mention that {@link TreeNode#hashTreeRoot()} for all these nodes return the same value {@link
   * org.apache.tuweni.bytes.Bytes32#ZERO}
   *
   * <p>Iterating leaves with this method is much faster that addressing each leaf by its general
   * index separately
   */
  public static final TreeNode[] ZERO_LEAVES =
      IntStream.range(0, TreeNode.NODE_BYTE_SIZE + 1)
          .mapToObj(ZeroLeafNode::new)
          .toArray(TreeNode[]::new);
  /** The {@link LeafNode} with empty data */
  public static final TreeNode EMPTY_LEAF = ZERO_LEAVES[0];

  static final TreeNode[] ZERO_TREES;

  static {
    ZERO_TREES = new TreeNode[64];
    ZERO_TREES[0] = EMPTY_LEAF;
    for (int i = 1; i < ZERO_TREES.length; i++) {
      ZERO_TREES[i] = new ZeroBranchNode(ZERO_TREES[i - 1], ZERO_TREES[i - 1], i);
      ZERO_TREES[i].hashTreeRoot(); // pre-cache
    }
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
        defaultNode, EMPTY_LEAF.equals(defaultNode) ? 0 : maxLength, treeDepth(maxLength));
  }

  public static TreeNode createDefaultSuperTree(
      long maxLength, TreeNode defaultNode, List<Integer> superBranchDepths) {

    int treeDepth = treeDepth(maxLength);
    checkArgument(superBranchDepths.stream().mapToInt(i -> i).sum() <= treeDepth);
    int superDepthIdx = 0;
    int curDepth = treeDepth;
    TreeNode curDepthDefaultNode = defaultNode;
    while (curDepth > 0) {
      int superDepth =
          superDepthIdx < superBranchDepths.size() ? superBranchDepths.get(superDepthIdx++) : 1;
      if (superDepth == 1) {
        curDepthDefaultNode = TreeNode.createBranchNode(curDepthDefaultNode, curDepthDefaultNode);
      } else {
        curDepthDefaultNode = SuperBranchNode.createDefault(curDepthDefaultNode, 1 << superDepth);
      }
      curDepth -= superDepth;
    }
    return curDepthDefaultNode;
  }

  /** Creates a binary tree of width `nextPowerOf2(leafNodes.size())` with specific leaf nodes */
  public static TreeNode createTree(List<TreeNode> leafNodes) {
    return createTree(leafNodes, treeDepth(leafNodes.size()));
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
      return new BranchNodeImpl(lTree, rTree);
    }
  }

  private static TreeNode createTree(List<TreeNode> leafNodes, int depth) {
    if (leafNodes.isEmpty()) {
      return ZERO_TREES[depth];
    } else if (depth == 0) {
      checkArgument(leafNodes.size() == 1);
      return leafNodes.get(0);
    } else {
      long index = 1L << (depth - 1);
      int iIndex = index > leafNodes.size() ? leafNodes.size() : (int) index;

      List<TreeNode> leftSublist = leafNodes.subList(0, iIndex);
      List<TreeNode> rightSublist = leafNodes.subList(iIndex, leafNodes.size());
      return new BranchNodeImpl(
          createTree(leftSublist, depth - 1), createTree(rightSublist, depth - 1));
    }
  }

  public static int treeDepth(long maxChunks) {
    return Long.bitCount(Utils.nextPowerOf2(maxChunks) - 1);
  }

  /** Estimates the number of 'non-default' tree nodes */
  public static int estimateNonDefaultNodes(TreeNode node) {
    if (node instanceof LeafNode) {
      return 1;
    } else {
      BranchNode branchNode = (BranchNode) node;
      if (branchNode.left() == branchNode.right()) {
        return 0;
      } else {
        return estimateNonDefaultNodes(branchNode.left())
            + estimateNonDefaultNodes(branchNode.right())
            + 1;
      }
    }
  }

  /**
   * Iterate all leaf tree nodes starting from the node with general index {@code fromGeneralIndex}
   * (including all node descendants if this is a branch node) and ending with the node with general
   * index {@code toGeneralIndex} inclusive (including all node descendants if this is a branch
   * node). On every {@link LeafNode} the supplied {@code visitor} is invoked.
   */
  public static void iterateLeaves(
      TreeNode node, long fromGeneralIndex, long toGeneralIndex, Consumer<LeafNode> visitor) {
    iterateNodes(
        node,
        fromGeneralIndex,
        toGeneralIndex,
        n -> {
          if (n instanceof LeafNode) {
            visitor.accept((LeafNode) n);
          }
        });
  }

  public static void iterateLeavesData(
      TreeNode node, long fromGeneralIndex, long toGeneralIndex, Consumer<Bytes> visitor) {
    iterateNodes(
        node,
        fromGeneralIndex,
        toGeneralIndex,
        n -> {
          if (n instanceof LeafNode) {
            visitor.accept(((LeafNode) n).getData());
          } else if (n instanceof SuperLeafNode) {
            visitor.accept(((SuperLeafNode) n).getData());
          }
        });
  }

  public static void iterateNodes(
      TreeNode node, long fromGeneralIndex, long toGeneralIndex, Consumer<TreeNode> visitor) {
    iterateNodesPriv(
        node,
        GIndexUtil.gIdxLeftmostFrom(fromGeneralIndex),
        GIndexUtil.gIdxRightmostFrom(toGeneralIndex),
        visitor);
  }

  private static void iterateNodesPriv(
      TreeNode node, long fromGeneralIndex, long toGeneralIndex, Consumer<TreeNode> visitor) {
    node.iterateRange(
        (itNode, __) -> {
          visitor.accept(itNode);
          return true;
        },
        fromGeneralIndex,
        toGeneralIndex);
  }

  /** Dumps the tree to stdout */
  public static String dumpBinaryTree(TreeNode node) {
    StringBuilder ret = new StringBuilder();
    dumpBinaryTreeRec(node, "", false, s -> ret.append(s).append('\n'));
    return ret.toString();
  }

  private static void dumpBinaryTreeRec(
      TreeNode node, String prefix, boolean printCommit, Consumer<String> linesConsumer) {
    if (node instanceof LeafNode) {
      LeafNode leafNode = (LeafNode) node;
      linesConsumer.accept(prefix + leafNode);
    } else {
      BranchNode branchNode = (BranchNode) node;
      String s = "├─┐";
      if (printCommit) {
        s += " " + branchNode;
      }
      if (branchNode.left() instanceof LeafNode) {
        linesConsumer.accept(prefix + "├─" + branchNode.left());
      } else {
        linesConsumer.accept(prefix + s);
        dumpBinaryTreeRec(branchNode.left(), prefix + "│ ", printCommit, linesConsumer);
      }
      if (branchNode.right() instanceof LeafNode) {
        linesConsumer.accept(prefix + "└─" + branchNode.right());
      } else {
        linesConsumer.accept(prefix + "└─┐");
        dumpBinaryTreeRec(branchNode.right(), prefix + "  ", printCommit, linesConsumer);
      }
    }
  }
}
