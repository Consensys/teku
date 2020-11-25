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

package tech.pegasys.teku.ssz.backing.tree;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Collections.singletonList;
import static tech.pegasys.teku.ssz.backing.tree.GIndexUtil.LEFTMOST_G_INDEX;
import static tech.pegasys.teku.ssz.backing.tree.GIndexUtil.SELF_G_INDEX;
import static tech.pegasys.teku.ssz.backing.tree.GIndexUtil.gIdxCompare;
import static tech.pegasys.teku.ssz.backing.tree.GIndexUtil.gIdxGetChildIndex;
import static tech.pegasys.teku.ssz.backing.tree.GIndexUtil.gIdxGetRelativeGIndex;
import static tech.pegasys.teku.ssz.backing.tree.GIndexUtil.gIdxIsSelf;
import static tech.pegasys.teku.ssz.backing.tree.GIndexUtil.gIdxLeftGIndex;
import static tech.pegasys.teku.ssz.backing.tree.GIndexUtil.gIdxRightGIndex;
import static tech.pegasys.teku.ssz.backing.tree.TreeNodeImpl.LeafNodeImpl;

import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.Hash;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.ssz.backing.tree.GIndexUtil.NodeRelation;
import tech.pegasys.teku.ssz.backing.tree.TreeNodeImpl.BranchNodeImpl;

/**
 * Basic interface for Backing Tree node Backing Binary Tree concept for SSZ structures is described
 * here: https://github.com/protolambda/eth-merkle-trees/blob/master/typing_partials.md#tree
 *
 * <p>Tree node is immutable by design. Any update on a tree creates new nodes which refer both new
 * data nodes and old unmodified nodes
 */
public interface TreeNode {

  int NODE_BYTE_SIZE = 32;
  int NODE_BIT_SIZE = NODE_BYTE_SIZE * 8;

  static LeafNode createLeafNode(Bytes data) {
    return new LeafNodeImpl(data);
  }

  static BranchNode createBranchNode(TreeNode left, TreeNode right) {
    assert left != null && right != null;
    return new BranchNodeImpl(left, right);
  }

  /**
   * Leaf node of a tree which contains 'bytes32' value. This node type corresponds to the 'Root'
   * node in the spec:
   * https://github.com/protolambda/eth-merkle-trees/blob/master/typing_partials.md#structure
   */
  interface LeafNode extends TreeNode {

    /**
     * Returns only data bytes without zero right padding (unlike {@link #hashTreeRoot()}) E.g. if a
     * {@code LeafNode} corresponds to a contained UInt64 field, then {@code getData()} returns only
     * 8 bytes corresponding to the field value If a {@code Vector[Byte, 48]} is stored across two
     * {@code LeafNode}s then the second node {@code getData} would return just the last 16 bytes of
     * the vector (while {@link #hashTreeRoot()} would return zero padded 32 bytes)
     */
    Bytes getData();

    @Override
    default Bytes32 hashTreeRoot() {
      return Bytes32.rightPad(getData());
    }

    /**
     * @param target generalized index. Should be equal to 1
     * @return this node if 'target' == 1
     * @throws IllegalArgumentException if 'target' != 1
     */
    @NotNull
    @Override
    default TreeNode get(long target) {
      checkArgument(target == 1, "Invalid root index: %s", target);
      return this;
    }

    @Override
    default boolean iterate(
        TreeVisitor visitor, long thisGeneralizedIndex, long startGeneralizedIndex) {
      if (gIdxCompare(thisGeneralizedIndex, startGeneralizedIndex) == NodeRelation.Left) {
        return true;
      } else {
        return visitor.visit(this, thisGeneralizedIndex);
      }
    }

    @Override
    default TreeNode updated(long target, Function<TreeNode, TreeNode> nodeUpdater) {
      checkArgument(target == 1, "Invalid root index: %s", target);
      return nodeUpdater.apply(this);
    }
  }

  /**
   * Branch node of a tree. This node type corresponds to the 'Commit' node in the spec:
   * https://github.com/protolambda/eth-merkle-trees/blob/master/typing_partials.md#structure
   */
  interface BranchNode extends TreeNode {

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

  /**
   * Calculates (if necessary) and returns `hash_tree_root` of this tree node. Worth to mention that
   * `hash_tree_root` of a {@link LeafNode} is the node {@link Bytes32} content
   */
  Bytes32 hashTreeRoot();

  /**
   * Gets this node descendant by its 'generalized index'
   *
   * @param generalizedIndex generalized index of a tree is specified here:
   *     https://github.com/ethereum/eth2.0-specs/blob/2787fea5feb8d5977ebee7c578c5d835cff6dc21/specs/light_client/merkle_proofs.md#generalized-merkle-tree-index
   * @return node descendant
   * @throws IllegalArgumentException if no node exists for the passed generalized index
   */
  @NotNull
  TreeNode get(long generalizedIndex);

  boolean iterate(TreeVisitor visitor, long thisGeneralizedIndex, long startGeneralizedIndex);

  default void iterateRange(
      TreeVisitor visitor, long startGeneralizedIndex, long endGeneralizedIndexInclusive) {
    iterate(
        TreeVisitor.createTillIndexInclusive(visitor, endGeneralizedIndexInclusive),
        SELF_G_INDEX,
        startGeneralizedIndex);
  }

  default void iterateAll(TreeVisitor visitor) {
    iterate(visitor, SELF_G_INDEX, LEFTMOST_G_INDEX);
  }

  default void iterateAll(Consumer<TreeNode> simpleVisitor) {
    iterateAll((node, __) -> {
      simpleVisitor.accept(node);
      return true;
    });
  }

  /**
   * The same as {@link #updated(long, TreeNode)} except that existing node can be used to calculate
   * a new node
   */
  default TreeNode updated(long generalizedIndex, Function<TreeNode, TreeNode> nodeUpdater) {
    TreeNode newNode = nodeUpdater.apply(get(generalizedIndex));
    return updated(
        new TreeUpdates(singletonList(new TreeUpdates.Update(generalizedIndex, newNode))));
  }

  /** Updates the tree in a batch */
  default TreeNode updated(TreeUpdates newNodes) {
    TreeNode ret = this;
    for (int i = 0; i < newNodes.size(); i++) {
      ret = ret.updated(newNodes.getRelativeGIndex(i), newNodes.getNode(i));
    }
    return ret;
  }

  /**
   * 'Sets' a new node on place of the node at generalized index. This node and all its descendants
   * are left immutable. The updated subtree node is returned.
   *
   * @param generalizedIndex index of tree node to be replaced
   * @param node new node either leaf of subtree root node
   * @return the updated subtree root node
   */
  default TreeNode updated(long generalizedIndex, TreeNode node) {
    return updated(generalizedIndex, oldNode -> node);
  }
}
