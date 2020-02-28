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

package tech.pegasys.artemis.util.backing.tree;

import org.apache.tuweni.bytes.Bytes32;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.artemis.util.backing.tree.TreeNode.Commit;
import tech.pegasys.artemis.util.backing.tree.TreeNode.Root;

class TreeNodeImpl {

  static class RootImpl implements Root {
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
      return "[" + (Bytes32.ZERO.equals(root) ? "0x0" : root) + "]";
    }
  }

  static class CommitImpl implements Commit {
    private final TreeNode left;
    private final TreeNode right;
    private volatile Bytes32 cachedHash = null;

    public CommitImpl(TreeNode left, TreeNode right) {
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
    public Commit rebind(boolean left, TreeNode newNode) {
      return left ? new CommitImpl(newNode, right()) : new CommitImpl(left(), newNode);
    }

    @Override
    public Bytes32 hashTreeRoot() {
      if (cachedHash == null) {
        cachedHash = Commit.super.hashTreeRoot();
      }
      return cachedHash;
    }

    @Override
    public String toString() {
      return "(" + (left == right ? "default" : left + ", " + right) + ')';
    }
  }
}
