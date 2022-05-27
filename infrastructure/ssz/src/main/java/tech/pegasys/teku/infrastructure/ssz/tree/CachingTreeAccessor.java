package tech.pegasys.teku.infrastructure.ssz.tree;

/**
 * Optimizes access to a tree node by its index. Instead of traversing the full path from the root
 * node on every access applies various optimizations like caching last accessed node
 * <p>
 * TODO: cache the full tree path for the last element and start traversing from the common ancestor
 * <p>
 * The class should be thread-safe
 */
public final class CachingTreeAccessor {

  private final static CachedTreeNode NA_CACHED_NODE = new CachedTreeNode(-1, null);

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

      TreeNode node = root.get(generalizedIndex);
      cachedTreeNode = new CachedTreeNode(index, node);
      return node;
    }
  }

  private static class CachedTreeNode {

    private final long nodeIndex;
    private final TreeNode node;

    public CachedTreeNode(long nodeIndex, TreeNode node) {
      this.nodeIndex = nodeIndex;
      this.node = node;
    }

    public long getNodeIndex() {
      return nodeIndex;
    }

    public TreeNode getNode() {
      return node;
    }
  }
}
