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
import static tech.pegasys.teku.ssz.backing.tree.TreeNodeImpl.LeafNodeImpl;

import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.Hash;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.ssz.backing.tree.TreeNodeImpl.CompressedLeafNodeImpl;

/**
 * Basic interface for Backing Tree node Backing Binary Tree concept for SSZ structures is described
 * here: https://github.com/protolambda/eth-merkle-trees/blob/master/typing_partials.md#tree
 *
 * <p>Tree node is immutable by design. Any update on a tree creates new nodes which refer both new
 * data nodes and old unmodified nodes
 */
public interface TreeNode {

  static TreeNode createLeafNode(Bytes32 val) {
    return new LeafNodeImpl(val);
  }

  static TreeNode createCompressedLeafNode(Bytes val) {
    return new CompressedLeafNodeImpl(val);
  }

  /**
   * Leaf node of a tree which contains 'bytes32' value. This node type corresponds to the 'Root'
   * node in the spec:
   * https://github.com/protolambda/eth-merkle-trees/blob/master/typing_partials.md#structure
   */
  interface LeafNode extends TreeNode {

    /** Returns node value */
    Bytes32 getRoot();

    @Override
    default Bytes32 hashTreeRoot() {
      return getRoot();
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
      if (target == 1) {
        return this;
      } else {
        long anchor = Long.highestOneBit(target);
        long pivot = anchor >> 1;
        return target < (target | pivot)
            ? left().get((target ^ anchor) | pivot)
            : right().get((target ^ anchor) | pivot);
      }
    }

    @Override
    default TreeNode updated(long target, Function<TreeNode, TreeNode> nodeUpdater) {
      if (target == 1) {
        return nodeUpdater.apply(this);
      } else {
        long anchor = Long.highestOneBit(target);
        long pivot = anchor >> 1;
        if (target < (target | pivot)) {
          TreeNode newLeftChild = left().updated((target ^ anchor) | pivot, nodeUpdater);
          return rebind(true, newLeftChild);
        } else {
          TreeNode newRightChild = right().updated((target ^ anchor) | pivot, nodeUpdater);
          return rebind(false, newRightChild);
        }
      }
    }
  }

  /**
   * Calculates (if necessary) and returns `hash_tree_root` of this tree node.
   * Worth to mention that `hash_tree_root` of a {@link LeafNode} is the node {@link Bytes32} content
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

  /**
   * The same as {@link #updated(long, TreeNode)} except that existing node can be used to calculate
   * a new node
   */
  TreeNode updated(long generalizedIndex, Function<TreeNode, TreeNode> nodeUpdater);

  boolean isZero();

  /** Updates the tree in a batch */
  default TreeNode updated(TreeUpdates newNodes) {
    TreeNode ret = this;
    for (int i = 0; i < newNodes.size(); i++) {
      ret = ret.updated(newNodes.getGIndex(i), newNodes.getNode(i));
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
