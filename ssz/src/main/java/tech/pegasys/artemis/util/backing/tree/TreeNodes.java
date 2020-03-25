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
import org.apache.commons.lang3.tuple.Pair;

public class TreeNodes {
  private final List<Long> gIndexes;
  private final List<TreeNode> nodes;

  public TreeNodes() {
    this(new ArrayList<>(), new ArrayList<>());
  }

  public TreeNodes(List<Long> gIndexes, List<TreeNode> nodes) {
    this.gIndexes = gIndexes;
    this.nodes = nodes;
  }

  public Pair<TreeNodes, TreeNodes> split(long pivotGIndex) {
    int idx = Collections.binarySearch(gIndexes, pivotGIndex);
    int insIdx = idx < 0 ? -idx - 1 : idx;
    return Pair.of(
        new TreeNodes(gIndexes.subList(0, insIdx), nodes.subList(0, insIdx)),
        new TreeNodes(
            gIndexes.subList(insIdx, gIndexes.size()), nodes.subList(insIdx, nodes.size())));
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
}
