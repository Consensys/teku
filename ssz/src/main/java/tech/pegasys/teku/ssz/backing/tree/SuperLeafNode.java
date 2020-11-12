package tech.pegasys.teku.ssz.backing.tree;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.Math.max;

import com.google.common.base.Preconditions;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes;
import org.apache.tuweni.crypto.Hash;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.ssz.backing.tree.TreeNodeImpl.LeafNodeImpl;

/**
 * Works for:
 *   Vector[UInt64]
 *   Vector[byte]
 *   Vector[bytes32]
 *
 * Doesn't work for
 *   Container
 *   Vector[Container]
 */
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

  public Bytes getData() {
    return data;
  }

  @Override
  public Bytes32 hashTreeRoot() {
    return hashTreeRoot(0, 0, totalLeafCount);
  }

  private Bytes32 hashTreeRoot(int curDepth, long fromNodeIdx, long toNodeIdx) {
    if (fromNodeIdx >= dataLeafCount) {
      return TreeUtil.ZERO_TREES[depth - curDepth].hashTreeRoot();
    } else if (curDepth == depth) {
      assert toNodeIdx - fromNodeIdx == 1;
      if (fromNodeIdx + 1 < dataLeafCount) {
        return Bytes32.wrap(data, (int) fromNodeIdx * 32);
      } else {
        return Bytes32.rightPad(data.slice((int) (fromNodeIdx * 32)));
      }
    } else {
      long midNodeIdx = fromNodeIdx + (1 << (depth - curDepth - 1));
      assert midNodeIdx - fromNodeIdx == toNodeIdx - midNodeIdx;
      return Hash.sha2_256(Bytes.wrap(hashTreeRoot(curDepth + 1, fromNodeIdx, midNodeIdx),
          hashTreeRoot(curDepth + 1, midNodeIdx, toNodeIdx)));
    }
  }

  @NotNull
  @Override
  public TreeNode get(long generalizedIndex) {
    if (generalizedIndex == 1) {
      return this;
    }
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
    if (newNodes.isFinal()) {
      return newNodes.getNode(0);
    }
    long lastUpdatedGIndex = newNodes.getGIndex(newNodes.size() - 1);
    int lastUpdatedIndex = (int) (lastUpdatedGIndex - totalLeafCount);
    if (lastUpdatedIndex < 0) {
      throw new UnsupportedOperationException("Not yet supported updating branch nodes");
    }
    if (lastUpdatedIndex > totalLeafCount) {
      throw new IndexOutOfBoundsException("Invalid general index");
    }

    TreeNode lastNewNode = newNodes.getNode(newNodes.size() - 1);
    if (!(lastNewNode instanceof LeafNode)) {
      throw new IllegalArgumentException("Can't update leaf node with branch node");
    }
    LeafNode lastNewLeafNode = (LeafNode) lastNewNode;
    int newDataSize = max(data.size(), lastUpdatedIndex * 32 + lastNewLeafNode.getData().size());
    int newLeafCount = (newDataSize + 31) / 32;
    MutableBytes newData = MutableBytes.create(newDataSize);
    int updateIdx = 0;
    for (int leafIdx = 0; leafIdx < newLeafCount; leafIdx++) {
      if (updateIdx < newNodes.size() &&
          newNodes.getGIndex(updateIdx) - totalLeafCount == leafIdx) {

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
    return new SuperLeafNode(depth, newData);
  }
}
