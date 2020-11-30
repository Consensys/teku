package tech.pegasys.teku.ssz.backing.tree;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.ssz.backing.tree.GIndexUtil.gIdxCompare;
import static tech.pegasys.teku.ssz.backing.tree.GIndexUtil.gIdxGetChildIndex;
import static tech.pegasys.teku.ssz.backing.tree.GIndexUtil.gIdxGetRelativeGIndex;
import static tech.pegasys.teku.ssz.backing.tree.GIndexUtil.gIdxIsSelf;
import static tech.pegasys.teku.ssz.backing.tree.GIndexUtil.gIdxLeftGIndex;
import static tech.pegasys.teku.ssz.backing.tree.GIndexUtil.gIdxRightGIndex;

import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.Hash;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.ssz.backing.tree.GIndexUtil.NodeRelation;

/**
 * Branch node of a tree. This node type corresponds to the 'Commit' node in the spec:
 * https://github.com/protolambda/eth-merkle-trees/blob/master/typing_partials.md#structure
 */
public interface BranchNode extends TreeNode {

  /**
   * Returns left child node. It can be either a default or non-default node. Note that both left
   * and right child may be the same default instance
   */
  @NotNull
  TreeNode left();

  /**
   * Returns right child node. It can be either a default or non-default node. Note that both left
   * and right child may be the same default instance
   */
  @NotNull
  TreeNode right();

  /**
   * Rebind 'sets' a new left/right child of this node. Rebind doesn't modify this instance but
   * creates and returns a new one which contains a new assigned and old unmodified child
   */
  BranchNode rebind(boolean left, TreeNode newNode);

  @Override
  default Bytes32 hashTreeRoot() {
    return Hash.sha2_256(Bytes.concatenate(left().hashTreeRoot(), right().hashTreeRoot()));
  }

  @NotNull
  @Override
  default TreeNode get(long target) {
    checkArgument(target >= 1, "Invalid index: %s", target);
    if (gIdxIsSelf(target)) {
      return this;
    } else {
      long relativeGIndex = gIdxGetRelativeGIndex(target, 1);
      return gIdxGetChildIndex(target, 1) == 0
          ? left().get(relativeGIndex)
          : right().get(relativeGIndex);
    }
  }

  @Override
  default boolean iterate(
      TreeVisitor visitor, long thisGeneralizedIndex, long startGeneralizedIndex) {

    if (gIdxCompare(thisGeneralizedIndex, startGeneralizedIndex) == NodeRelation.Left) {
      return true;
    } else {
      return visitor.visit(this, thisGeneralizedIndex)
          && left().iterate(visitor, gIdxLeftGIndex(thisGeneralizedIndex), startGeneralizedIndex)
          && right()
          .iterate(visitor, gIdxRightGIndex(thisGeneralizedIndex), startGeneralizedIndex);
    }
  }

  @Override
  default TreeNode updated(long target, Function<TreeNode, TreeNode> nodeUpdater) {
    if (gIdxIsSelf(target)) {
      return nodeUpdater.apply(this);
    } else {
      long relativeGIndex = gIdxGetRelativeGIndex(target, 1);
      if (gIdxGetChildIndex(target, 1) == 0) {
        TreeNode newLeftChild = left().updated(relativeGIndex, nodeUpdater);
        return rebind(true, newLeftChild);
      } else {
        TreeNode newRightChild = right().updated(relativeGIndex, nodeUpdater);
        return rebind(false, newRightChild);
      }
    }
  }
}
