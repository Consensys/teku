package tech.pegasys.teku.ssz.backing.tree;

import static java.lang.Math.max;

import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.ssz.backing.tree.TreeNodeImpl.LeafNodeImpl;

public class SuperLeafNode implements TreeNode {
  private final int depth;
  private final Bytes data;
  private final int dataLeafCount;
  private final long totalLeafCount;

  public SuperLeafNode(int depth, Bytes data) {
    this.depth = depth;
    this.data = data;
    this.dataLeafCount = (data.size() + 31) / 32;
    this.totalLeafCount = 1L << depth;
  }

  @Override
  public Bytes32 hashTreeRoot() {
    throw new UnsupportedOperationException("TODO");
  }

  @NotNull
  @Override
  public TreeNode get(long generalizedIndex) {
    if (generalizedIndex < totalLeafCount) {
      throw new UnsupportedOperationException("Getting branch node not supported yet");
    }
    long leafIndex = generalizedIndex - totalLeafCount;
    if (leafIndex < dataLeafCount - 1) {
      return new LeafNodeImpl(data.slice((int) (leafIndex * 32), 32));
    } else if (leafIndex == dataLeafCount - 1) {
      return new LeafNodeImpl(data.slice((int) (leafIndex * 32), data.size() % 32));
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
    int lastUpdatedIndex = (int) (newNodes.getGIndex(newNodes.size() - 1) - totalLeafCount);
    if (lastUpdatedIndex < 0) {
      throw new IllegalArgumentException("Invalid last generalized index");
    }
    int newLeafCount = max(lastUpdatedIndex + 1, dataLeafCount);
    MutableBytes newData = MutableBytes.create(32 * newLeafCount);
    int updateIdx = 0;
    for (int leafIdx = 0; leafIdx < newLeafCount; leafIdx++) {
      if (newNodes.getGIndex(updateIdx) - totalLeafCount == leafIdx) {
        LeafNode node = (LeafNode) newNodes.getNode(updateIdx);
        node.getData().copyTo(newData, leafIdx * 32);
        updateIdx++;
      }
    }
    int leafIdx = 0;
    return null;
  }
}
