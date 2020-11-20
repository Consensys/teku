package tech.pegasys.teku.ssz.backing.tree;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.Math.max;
import static tech.pegasys.teku.ssz.backing.tree.GIndexUtil.gIdxGetChildIndex;
import static tech.pegasys.teku.ssz.backing.tree.GIndexUtil.gIdxGetRelativeGIndex;
import static tech.pegasys.teku.ssz.backing.tree.GIndexUtil.gIdxIsSelf;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.Hash;
import org.jetbrains.annotations.NotNull;

/**
 */
public class SuperBranchNode implements TreeNode {
  private final TreeNode[] children;
  private final Supplier<Bytes32> hashTreeRoot = Suppliers.memoize(this::calcHashTreeRoot);

  public static SuperBranchNode createDefault(TreeNode defaultChild, int childCount) {
    TreeNode[] children = new TreeNode[childCount];
    Arrays.fill(children, defaultChild);
    return new SuperBranchNode(children);
  }

  public SuperBranchNode(TreeNode[] children) {
    checkArgument(Long.bitCount(children.length) == 1, "Number of children should be power of 2");
    this.children = children;
  }

  private int getTotalChildCount() {
    return children.length;
  }

  private int getDepth() {
    return TreeUtil.treeDepth(children.length);
  }
  @Override
  public Bytes32 hashTreeRoot() {
    return hashTreeRoot.get();
  }

  private Bytes32 calcHashTreeRoot() {
    return hashTreeRoot(0, 0, getTotalChildCount());
  }

  private Bytes32 hashTreeRoot(int curDepth, long fromNodeIdx, long toNodeIdx) {
    if (curDepth == getDepth()) {
      assert toNodeIdx - fromNodeIdx == 1;
      return getChild((int) fromNodeIdx).hashTreeRoot();
    } else {
      long midNodeIdx = fromNodeIdx + (1L << (getDepth() - curDepth - 1));
      assert midNodeIdx - fromNodeIdx == toNodeIdx - midNodeIdx;
      return Hash.sha2_256(Bytes.wrap(hashTreeRoot(curDepth + 1, fromNodeIdx, midNodeIdx),
          hashTreeRoot(curDepth + 1, midNodeIdx, toNodeIdx)));
    }
  }

  private TreeNode getChild(int idx) {
    return children[idx];
  }

  @NotNull
  @Override
  public TreeNode get(long generalizedIndex) {
    if (gIdxIsSelf(generalizedIndex)) {
      return this;
    }
    checkArgument(generalizedIndex > 0, "Invalid zero index");
    int depth = getDepth();
    return getChild(gIdxGetChildIndex(generalizedIndex, depth))
        .get(gIdxGetRelativeGIndex(generalizedIndex, depth));
  }

  @Override
  public TreeNode updated(long generalizedIndex, Function<TreeNode, TreeNode> nodeUpdater) {
    throw new UnsupportedOperationException("TODO");
  }

  @Override
  public TreeNode updated(TreeUpdates newNodes) {
    if (newNodes.isEmpty()) {
      return this;
    }
    if (newNodes.isFinal()) {
      return newNodes.getNode(0);
    }
    List<TreeUpdates> childUpdates = newNodes.splitToDepth(getDepth());
    TreeNode[] newChildren = new TreeNode[getTotalChildCount()];
    for (int i = 0; i < getTotalChildCount(); i++) {
      newChildren[i] = getChild(i).updated(childUpdates.get(i));
    }

    return new SuperBranchNode(newChildren);
  }

  @Override
  public String toString() {
    StringBuilder ret = new StringBuilder("SBranch-" + getTotalChildCount() + "[");
    int dupCnt = 0;
    for (int i = 0; i < getTotalChildCount(); i++) {
      TreeNode cur = getChild(i);
      TreeNode next = i + 1 < getTotalChildCount() ? getChild(i + 1) : null;
      if (cur == next) {
        dupCnt++;
      } else {
        ret.append(dupCnt > 0 ? (dupCnt + 1) + "x " : "");
        ret.append(cur.toString() + ", ");
        dupCnt = 0;
      }
    }
    return ret.substring(0, ret.length() - 2) + "]";
  }
}
