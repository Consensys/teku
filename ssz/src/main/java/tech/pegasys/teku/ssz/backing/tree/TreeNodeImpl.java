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

import java.util.Objects;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes32;
import org.jetbrains.annotations.NotNull;

abstract class TreeNodeImpl implements TreeNode {

  static class LeafNodeImpl extends TreeNodeImpl implements LeafNode {
    private final Bytes32 root;

    public LeafNodeImpl(Bytes32 root) {
      this.root = root;
    }

    @Override
    public Bytes32 getRoot() {
      return root;
    }

    @Override
    public TreeNode updated(TreeUpdates newNodes) {
      if (newNodes.size() == 0) {
        return this;
      } else {
        newNodes.checkLeaf();
        return newNodes.getNode(0);
      }
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      LeafNodeImpl root1 = (LeafNodeImpl) o;
      return Objects.equals(root, root1.root);
    }

    @Override
    public int hashCode() {
      return Objects.hash(root);
    }

    @Override
    public String toString() {
      return "[" + (Bytes32.ZERO.equals(root) ? "0x0" : root) + "]";
    }
  }

  static class BranchNodeImpl extends TreeNodeImpl implements BranchNode {
    private final TreeNode left;
    private final TreeNode right;
    private volatile Bytes32 cachedHash = null;

    public BranchNodeImpl(TreeNode left, TreeNode right) {
      this.left = left;
      this.right = right;
    }

    @NotNull
    @Override
    public TreeNode left() {
      return left;
    }

    @NotNull
    @Override
    public TreeNode right() {
      return right;
    }

    @Override
    public BranchNode rebind(boolean left, TreeNode newNode) {
      return left ? new BranchNodeImpl(newNode, right()) : new BranchNodeImpl(left(), newNode);
    }

    @Override
    public TreeNode updated(TreeUpdates newNodes) {
      if (newNodes.size() == 0) {
        return this;
      } else if (newNodes.isFinal()) {
        return newNodes.getNode(0);
      } else {
        Pair<TreeUpdates, TreeUpdates> children = newNodes.splitAtPivot();
        return new BranchNodeImpl(
            left().updated(children.getLeft()), right().updated(children.getRight()));
      }
    }

    @Override
    public Bytes32 hashTreeRoot() {
      if (cachedHash == null) {
        cachedHash = BranchNode.super.hashTreeRoot();
      }
      return cachedHash;
    }

    @Override
    public String toString() {
      return "(" + (left == right ? "default" : left + ", " + right) + ')';
    }
  }
}
