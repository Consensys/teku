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

public class TreeNodes {

  public static Collector<Pair<Long, TreeNode>, ?, TreeNodes> collector() {
    return Collectors.collectingAndThen(Collectors.toList(), TreeNodes::new);
  }

  private final List<Long> gIndexes;
  private final List<TreeNode> nodes;

  private final long prefix;
  private final int heightFromLeaf;

  public TreeNodes(List<Pair<Long, TreeNode>> nodes) {
    this(
        nodes.stream().map(Pair::getLeft).collect(Collectors.toList()),
        nodes.stream().map(Pair::getRight).collect(Collectors.toList()));
  }

  public TreeNodes(List<Long> gIndexes, List<TreeNode> nodes) {
    this(gIndexes, nodes, 1, depth(gIndexes));
  }

  public TreeNodes(List<Long> gIndexes, List<TreeNode> nodes, long prefix, int heightFromLeaf) {
    this.gIndexes = gIndexes;
    this.nodes = nodes;
    this.prefix = prefix;
    this.heightFromLeaf = heightFromLeaf;
  }

  public Pair<TreeNodes, TreeNodes> splitAtPivot() {
    long lPrefix = prefix << 1;
    long rPrefix = lPrefix | 1;
    long pivotGIndex = rPrefix << (heightFromLeaf - 1);

    int idx = Collections.binarySearch(gIndexes, pivotGIndex);
    int insIdx = idx < 0 ? -idx - 1 : idx;
    return Pair.of(
        new TreeNodes(
            gIndexes.subList(0, insIdx), nodes.subList(0, insIdx), lPrefix, heightFromLeaf - 1),
        new TreeNodes(
            gIndexes.subList(insIdx, gIndexes.size()),
            nodes.subList(insIdx, nodes.size()),
            rPrefix,
            heightFromLeaf - 1));
  }

  private static int depth(List<Long> gIndexes) {
    if (gIndexes.isEmpty()) return -1;
    return Long.bitCount(Long.highestOneBit(gIndexes.get(0)) - 1);
  }

  public int size() {
    return gIndexes.size();
  }

  public long getGIndex(int index) {
    return gIndexes.get(index);
  }

  public TreeNode getNode(int index) {
    return nodes.get(index);
  }

  public void add(long gIndex, TreeNode node) {
    gIndexes.add(gIndex);
    nodes.add(node);
  }

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

  public boolean isFinal() {
    return (gIndexes.size() == 1 && gIndexes.get(0) == prefix);
  }
}
