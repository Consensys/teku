package tech.pegasys.artemis.util.backing.tree;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.util.backing.TreeNode;
import tech.pegasys.artemis.util.backing.TreeNode.Commit;
import tech.pegasys.artemis.util.backing.TreeNode.Root;

public class TreeNodeImpl {

  public static class RootImpl implements Root {
    private final Bytes32 root;

    public RootImpl(Bytes32 root) {
      this.root = root;
    }

    @Override
    public Bytes32 getRoot() {
      return root;
    }

    @Override
    public String toString() {
      Bytes trimmed = root.trimLeadingZeros();
      if (trimmed.size() > 4) {
        trimmed = root;
      }
      return "[" + trimmed + "]";
    }
  }

  public static class CommitImpl implements Commit {
    private final TreeNode left;
    private final TreeNode right;
    private Bytes32 cachedHash = null;

    public CommitImpl(TreeNode left, TreeNode right) {
      this.left = left;
      this.right = right;
    }

    @Override
    public TreeNode left() {
      return left;
    }

    @Override
    public TreeNode right() {
      return right;
    }

    @Override
    public Commit rebind(boolean left, TreeNode newNode) {
      return left ? new CommitImpl(newNode, right()) : new CommitImpl(left(), newNode);
    }

    @Override
    public Bytes32 hashTreeRoot() {
      if (cachedHash != null) {
        cachedHash = Commit.super.hashTreeRoot();
      }
      return cachedHash;
    }

    @Override
    public String toString() {
      return "(" + left + ", " + right + ')';
    }
  }

  public static TreeNode createZeroTree(int depth, TreeNode zeroElement) {
    TreeNode ret = zeroElement;
    for (int i = 0; i < depth; i++) {
      ret = new CommitImpl(ret, ret);
    }
    return ret;
  }
}
