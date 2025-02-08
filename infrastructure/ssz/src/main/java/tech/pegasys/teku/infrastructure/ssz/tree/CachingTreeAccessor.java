/*
 * Copyright Consensys Software Inc., 2022
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

/**
 * Optimizes access to a tree node by its index. Instead of traversing the full path from the root
 * node on every access applies various optimizations like caching last accessed node
 *
 * <p>TODO: cache the full tree path for the last element and start traversing from the common
 * ancestor
 *
 * <p>The class should be thread-safe
 */
public final class CachingTreeAccessor {

  private static final CachedTreeNode NA_CACHED_NODE = new CachedTreeNode(-1, null, new TreeNode[0]);

  @FunctionalInterface
  public interface GeneralizedIndexCalculator {

    long toGeneralizedIndex(long vectorIndex);
  }

  private final TreeNode root;
  private final GeneralizedIndexCalculator generalizedIndexCalculator;

  private volatile CachedTreeNode cachedTreeNode = NA_CACHED_NODE;

  public CachingTreeAccessor(TreeNode root, GeneralizedIndexCalculator generalizedIndexCalculator) {
    this.root = root;
    this.generalizedIndexCalculator = generalizedIndexCalculator;
  }

  public TreeNode getNodeByVectorIndex(int index) {
    CachedTreeNode cached = cachedTreeNode;
    if (cached.getNodeIndex() == index) {
      return cached.getNode();
    } else {
      long generalizedIndex = generalizedIndexCalculator.toGeneralizedIndex(index);
      long cachedGeneralizedIndex = generalizedIndexCalculator.toGeneralizedIndex(cached.getNodeIndex());
      
      // Find the common ancestor level
      int commonLevel = findCommonAncestorLevel(generalizedIndex, cachedGeneralizedIndex);
      TreeNode startNode;
      if (commonLevel >= 0 && cached.getNodeIndex() >= 0) {
        // Start from the common ancestor in the cached path
        startNode = cached.getPath()[commonLevel];
      } else {
        startNode = root;
      }

      // Calculate path to the target node
      TreeNode[] path = calculatePath(startNode, generalizedIndex);
      TreeNode targetNode = path[path.length - 1];
      
      cachedTreeNode = new CachedTreeNode(index, targetNode, path);
      return targetNode;
    }
  }

  private int findCommonAncestorLevel(long index1, long index2) {
    if (index1 <= 0 || index2 <= 0) return -1;
    int level = 0;
    while (index1 != index2) {
      index1 = index1 >> 1;
      index2 = index2 >> 1;
      if (index1 == 0 || index2 == 0) return -1;
      level++;
    }
    return level;
  }

  private TreeNode[] calculatePath(TreeNode startNode, long generalizedIndex) {
    int depth = Long.SIZE - Long.numberOfLeadingZeros(generalizedIndex);
    TreeNode[] path = new TreeNode[depth];
    TreeNode currentNode = startNode;
    for (int i = 0; i < depth; i++) {
      path[i] = currentNode;
      boolean right = ((generalizedIndex >> (depth - i - 1)) & 1) == 1;
      currentNode = right ? currentNode.right() : currentNode.left();
    }
    return path;
  }

  private static class CachedTreeNode {

    private final long nodeIndex;
    private final TreeNode node;
    private final TreeNode[] path;

    public CachedTreeNode(final long nodeIndex, final TreeNode node, final TreeNode[] path) {
      this.nodeIndex = nodeIndex;
      this.node = node;
      this.path = path;
    }

    public long getNodeIndex() {
      return nodeIndex;
    }

    public TreeNode getNode() {
      return node;
    }

    public TreeNode[] getPath() {
      return path;
    }
  }
}
