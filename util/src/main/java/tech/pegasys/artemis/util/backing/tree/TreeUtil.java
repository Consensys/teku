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

package tech.pegasys.artemis.util.backing.tree;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.List;
import java.util.function.Consumer;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.util.backing.Utils;
import tech.pegasys.artemis.util.backing.tree.TreeNode.Commit;
import tech.pegasys.artemis.util.backing.tree.TreeNode.Root;
import tech.pegasys.artemis.util.backing.tree.TreeNodeImpl.CommitImpl;
import tech.pegasys.artemis.util.backing.tree.TreeNodeImpl.RootImpl;

/** Misc Backing binary tree utils */
public class TreeUtil {

  public static final TreeNode ZERO_LEAF = new RootImpl(Bytes32.ZERO);
  private static final TreeNode[] ZERO_TREES;

  static {
    ZERO_TREES = new TreeNode[64];
    ZERO_TREES[0] = ZERO_LEAF;
    for (int i = 1; i < ZERO_TREES.length; i++) {
      ZERO_TREES[i] = new CommitImpl(ZERO_TREES[i - 1], ZERO_TREES[i - 1]);
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
        defaultNode, ZERO_LEAF.equals(defaultNode) ? 0 : maxLength, treeDepth(maxLength));
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
      return new CommitImpl(lTree, rTree);
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
      return new CommitImpl(
          createTree(leftSublist, depth - 1), createTree(rightSublist, depth - 1));
    }
  }

  public static int treeDepth(long maxChunks) {
    return Long.bitCount(Utils.nextPowerOf2(maxChunks) - 1);
  }

  /** Estimates the number of 'non-default' tree nodes */
  public static int estimateNonDefaultNodes(TreeNode node) {
    if (node instanceof Root) {
      return 1;
    } else {
      Commit commitNode = (Commit) node;
      if (commitNode.left() == commitNode.right()) {
        return 0;
      } else {
        return estimateNonDefaultNodes(commitNode.left())
            + estimateNonDefaultNodes(commitNode.right())
            + 1;
      }
    }
  }

  /** Dumps the tree to stdout */
  public static String dumpBinaryTree(TreeNode node) {
    StringBuilder ret = new StringBuilder();
    dumpBinaryTreeRec(node, "", false, s -> ret.append(s).append('\n'));
    return ret.toString();
  }

  private static void dumpBinaryTreeRec(
      TreeNode node, String prefix, boolean printCommit, Consumer<String> linesConsumer) {
    if (node instanceof Root) {
      Root rootNode = (Root) node;
      linesConsumer.accept(prefix + rootNode);
    } else {
      Commit commitNode = (Commit) node;
      String s = "├─┐";
      if (printCommit) {
        s += " " + commitNode;
      }
      if (commitNode.left() instanceof Root) {
        linesConsumer.accept(prefix + "├─" + commitNode.left());
      } else {
        linesConsumer.accept(prefix + s);
        dumpBinaryTreeRec(commitNode.left(), prefix + "│ ", printCommit, linesConsumer);
      }
      if (commitNode.right() instanceof Root) {
        linesConsumer.accept(prefix + "└─" + commitNode.right());
      } else {
        linesConsumer.accept(prefix + "└─┐");
        dumpBinaryTreeRec(commitNode.right(), prefix + "  ", printCommit, linesConsumer);
      }
    }
  }
}
