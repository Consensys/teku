package tech.pegasys.teku.ssz.backing.tree;

import static java.lang.Math.max;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes;
import org.apache.tuweni.crypto.Hash;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.ssz.backing.tree.TreeNodeImpl.LeafNodeImpl;

/**
 */
public class SuperBranchNode implements TreeNode {
  private final TreeNode[] children;
  private final Supplier<Bytes32> hashTreeRoot = Suppliers.memoize(this::calcHashTreeRoot);

  public SuperBranchNode(TreeNode[] children) {
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
    if (generalizedIndex == 1) {
      return this;
    }
    if (generalizedIndex < getTotalChildCount()) {
      throw new UnsupportedOperationException("Getting internal branch node not supported yet");
    }
    long childIndex = generalizedIndex - getTotalChildCount();
    if (childIndex < dataLeafCount - 1) {
      return new LeafNodeImpl(data.slice((int) (childIndex * 32), 32));
    } else if (childIndex == dataLeafCount - 1) {
      return new LeafNodeImpl(data.slice((int) (childIndex * 32),
          (int) (data.size() - childIndex * 32)));
    } else {
      return TreeUtil.EMPTY_LEAF;
    }
  }

  @Override
  public TreeNode updated(long generalizedIndex, Function<TreeNode, TreeNode> nodeUpdater) {
    throw new UnsupportedOperationException("TODO");
  }

  @Override
  public TreeNode updated(TreeUpdates newNodes) {
    if (newNodes.isFinal()) {
      return newNodes.getNode(0);
    }
    long lastUpdatedGIndex = newNodes.getGIndex(newNodes.size() - 1);
    int lastUpdatedIndex = (int) (lastUpdatedGIndex - getTotalChildCount());
    if (lastUpdatedIndex < 0) {
      throw new UnsupportedOperationException("Not yet supported updating branch nodes");
    }
    if (lastUpdatedIndex > getTotalChildCount()) {
      throw new IndexOutOfBoundsException("Invalid general index");
    }

    TreeNode lastNewNode = newNodes.getNode(newNodes.size() - 1);
    if (!(lastNewNode instanceof LeafNode)) {
      throw new IllegalArgumentException("Can't update leaf node with branch node");
    }
    LeafNode lastNewLeafNode = (LeafNode) lastNewNode;
    int newDataSize = max(data.size(), lastUpdatedIndex * 32 + lastNewLeafNode.getData().size());
    int newLeafCount = (newDataSize + 31) / 32;
    MutableBytes newData = MutableBytes.wrap(new byte[newDataSize]);
    int updateIdx = 0;
    for (int leafIdx = 0; leafIdx < newLeafCount; leafIdx++) {
      if (updateIdx < newNodes.size() &&
          newNodes.getGIndex(updateIdx) - getTotalChildCount() == leafIdx) {

        TreeNode node = newNodes.getNode(updateIdx);
        if (!(node instanceof LeafNode)) {
          throw new IllegalArgumentException("Can't update leaf node with branch node");
        }
        LeafNode leafNode = (LeafNode) node;
        if (leafIdx < lastUpdatedIndex && leafNode.getData().size() != 32) {
          throw new IllegalArgumentException("Update nodes should be packed");
        }
        leafNode.getData().copyTo(newData, leafIdx * 32);
        updateIdx++;
      } else {
        if (leafIdx + 1 < dataLeafCount) {
          data.slice(leafIdx * 32, 32).copyTo(newData, leafIdx * 32);
        }
        // else leaving zeroes
      }
    }
    return new SuperBranchNode(getDepth(), newData);
  }
}
