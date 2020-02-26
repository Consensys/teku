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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.util.backing.Utils;
import tech.pegasys.artemis.util.backing.tree.TreeNode.Commit;
import tech.pegasys.artemis.util.backing.tree.TreeNode.Root;
import tech.pegasys.artemis.util.backing.tree.TreeNodeImpl.CommitImpl;
import tech.pegasys.artemis.util.backing.tree.TreeNodeImpl.RootImpl;

/** Misc Backing binary tree utils */
public class TreeUtil {

  private static final TreeNode ZERO_LEAF = new RootImpl(Bytes32.ZERO);
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
   * @param zeroElement default leaf element. For complex vectors it could be default vector element
   *     struct subtree
   */
  public static TreeNode createDefaultTree(int maxLength, TreeNode zeroElement) {
    List<TreeNode> nodes =
        Stream.concat(
                IntStream.range(0, maxLength).mapToObj(i -> zeroElement),
                IntStream.range(maxLength, (int) Utils.nextPowerOf2(maxLength))
                    .mapToObj(i -> ZERO_LEAF))
            .collect(Collectors.toList());
    while (nodes.size() > 1) {
      List<TreeNode> parentNodes = new ArrayList<>(nodes.size() / 2);
      for (int i = 0; i < nodes.size(); i += 2) {
        parentNodes.add(new CommitImpl(nodes.get(i), nodes.get(i + 1)));
      }
      nodes = parentNodes;
    }
    return nodes.get(0);
  }

  /** Creates a binary tree with `nextPowerOf2(maxLength)` width and ZERO leaf nodes. */
  public static TreeNode createZeroTree(long maxLength) {
    return ZERO_TREES[treeDepth(maxLength)];
  }

  /** Creates a binary tree of width `nextPowerOf2(leafNodes.size())` with specific leaf nodes */
  public static TreeNode createTree(List<TreeNode> leafNodes) {
    int treeWidth = (int) Utils.nextPowerOf2(leafNodes.size());
    List<TreeNode> nodes = new ArrayList<>(leafNodes);
    nodes.addAll(Collections.nCopies(treeWidth - leafNodes.size(), ZERO_LEAF));
    while (nodes.size() > 1) {
      List<TreeNode> upperLevelNodes = new ArrayList<>(nodes.size() / 2);
      for (int i = 0; i < nodes.size() / 2; i++) {
        upperLevelNodes.add(new CommitImpl(nodes.get(i * 2), nodes.get(i * 2 + 1)));
      }
      nodes = upperLevelNodes;
    }
    return nodes.get(0);
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
  public static void dumpBinaryTree(TreeNode node) {
    dumpBinaryTreeRec(node, "", false);
  }

  private static void dumpBinaryTreeRec(TreeNode node, String prefix, boolean printCommit) {
    if (node instanceof Root) {
      Root rootNode = (Root) node;
      System.out.println(prefix + rootNode);
    } else {
      Commit commitNode = (Commit) node;
      String s = "├─┐";
      if (printCommit) {
        s += " " + commitNode;
      }
      if (commitNode.left() instanceof Root) {
        System.out.println(prefix + "├─" + commitNode.left());
      } else {
        System.out.println(prefix + s);
        dumpBinaryTreeRec(commitNode.left(), prefix + "│ ", printCommit);
      }
      if (commitNode.right() instanceof Root) {
        System.out.println(prefix + "└─" + commitNode.right());
      } else {
        System.out.println(prefix + "└─┐");
        dumpBinaryTreeRec(commitNode.right(), prefix + "  ", printCommit);
      }
    }
  }
}
