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

  public TreeNodes(List<Long> gIndexes,
      List<TreeNode> nodes) {
    this.gIndexes = gIndexes;
    this.nodes = nodes;
  }

  public Pair<TreeNodes, TreeNodes> split(long pivotGIndex) {
    int idx = Collections.binarySearch(gIndexes, pivotGIndex);
    int insIdx = idx < 0 ? -idx - 1 : idx;
    return Pair.of(
        new TreeNodes(gIndexes.subList(0, insIdx), nodes.subList(0, insIdx)),
        new TreeNodes(
            gIndexes.subList(insIdx, gIndexes.size()),
            nodes.subList(insIdx, nodes.size())));
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
