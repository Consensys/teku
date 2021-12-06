/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.infrastructure.ssz.tree;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes32;

class SimpleBranchNode implements BranchNode, TreeNode {

  private final TreeNode left;
  private final TreeNode right;
  private volatile Bytes32 cachedHash = null;

  public SimpleBranchNode(TreeNode left, TreeNode right) {
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
  public BranchNode rebind(boolean left, TreeNode newNode) {
    return left ? new SimpleBranchNode(newNode, right()) : new SimpleBranchNode(left(), newNode);
  }

  @Override
  public TreeNode updated(TreeUpdates newNodes) {
    if (newNodes.isEmpty()) {
      return this;
    } else if (newNodes.isFinal()) {
      return newNodes.getNode(0);
    } else {
      Pair<TreeUpdates, TreeUpdates> children = newNodes.splitAtPivot();
      return new SimpleBranchNode(
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
    return left == right ? ("(2x " + left + ")") : ("(" + left + ", " + right + ')');
  }
}
