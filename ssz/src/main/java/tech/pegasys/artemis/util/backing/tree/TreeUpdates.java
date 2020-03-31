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

import java.util.Collections;
import java.util.List;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;

/**
 * The collection of nodes and their target generalized indexes to be updated The class also
 * contains the target generalized index this set of changes is applicable to.
 *
 * @see TreeNode#updated(TreeUpdates)
 */
public class TreeUpdates {

  /** Convenient collector for the stream with <code>Pair<Long, TreeNode></code> elements */
  public static Collector<Pair<Long, TreeNode>, ?, TreeUpdates> collector() {
    return Collectors.collectingAndThen(Collectors.toList(), TreeUpdates::new);
  }

  private final List<Long> gIndexes;
  private final List<TreeNode> nodes;

  private final long prefix;
  private final int heightFromLeaf;

  /**
   * Creates a new instance of TreeNodes
   *
   * @param nodes the list of [[target generalized index], [new node value]] pairs
   *     <p><b>NOTE: the list should conform to the following prerequisites</b>:
   *     <ul>
   *       <li>the list should be sorted by target index
   *       <li>the generalized indexes should be on the same tree level. I.e. the highest order bit
   *           should be the same for all indexes
   *     </ul>
   *     Prerequisites are not checked for performance reasons. For an invalid list the behavior may
   *     be undefined but normally the {@link TreeNode#updated(TreeUpdates)} call would fail in this
   *     case
   */
  public TreeUpdates(List<Pair<Long, TreeNode>> nodes) {
    this(
        nodes.stream().map(Pair::getLeft).collect(Collectors.toList()),
        nodes.stream().map(Pair::getRight).collect(Collectors.toList()));
  }

  private TreeUpdates(List<Long> gIndexes, List<TreeNode> nodes) {
    this(gIndexes, nodes, 1, depth(gIndexes));
  }

  private TreeUpdates(List<Long> gIndexes, List<TreeNode> nodes, long prefix, int heightFromLeaf) {
    this.gIndexes = gIndexes;
    this.nodes = nodes;
    this.prefix = prefix;
    this.heightFromLeaf = heightFromLeaf;
  }

  /**
   * Split the nodes to left and right subtree subsets according the target generalized index
   *
   * @return the pair of node updates for left and right subtrees with accordingly adjusted target
   *     generalized indexes
   */
  public Pair<TreeUpdates, TreeUpdates> splitAtPivot() {
    long lPrefix = prefix << 1;
    long rPrefix = lPrefix | 1;
    long pivotGIndex = rPrefix << (heightFromLeaf - 1);

    int idx = Collections.binarySearch(gIndexes, pivotGIndex);
    int insIdx = idx < 0 ? -idx - 1 : idx;
    return Pair.of(
        new TreeUpdates(
            gIndexes.subList(0, insIdx), nodes.subList(0, insIdx), lPrefix, heightFromLeaf - 1),
        new TreeUpdates(
            gIndexes.subList(insIdx, gIndexes.size()),
            nodes.subList(insIdx, nodes.size()),
            rPrefix,
            heightFromLeaf - 1));
  }

  private static int depth(List<Long> gIndexes) {
    if (gIndexes.isEmpty()) return -1;
    return Long.bitCount(Long.highestOneBit(gIndexes.get(0)) - 1);
  }

  /** Number of updated nodes in this set */
  public int size() {
    return gIndexes.size();
  }

  /** Gets generalized index for update at position [index] */
  public long getGIndex(int index) {
    return gIndexes.get(index);
  }

  /** Gets new tree node for update at position [index] */
  public TreeNode getNode(int index) {
    return nodes.get(index);
  }

  /**
   * Checks if this instance is correct for the leaf node
   *
   * @throws IllegalArgumentException if not correct
   */
  public void checkLeaf() {
    if (heightFromLeaf != 0) {
      throw new IllegalArgumentException(
          "Non-zero heightFromLeaf for the leaf node: " + heightFromLeaf);
    }
    if (gIndexes.size() != 1) {
      throw new IllegalArgumentException(
          "Number of nodes should be 1 for a leaf node: " + gIndexes.size());
    }
    if (gIndexes.get(0) != prefix) {
      throw new IllegalArgumentException(
          "Leaf gIndex != prefix: " + gIndexes.get(0) + " != " + prefix);
    }
  }

  /** Indicates that this update should be applied to the node target generalized index */
  public boolean isFinal() {
    return (gIndexes.size() == 1 && gIndexes.get(0) == prefix);
  }
}
