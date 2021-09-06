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

package tech.pegasys.teku.ssz.tree;

import org.apache.tuweni.bytes.Bytes32;

public interface TreeNodeVisitor {

  /**
   * Called prior to visiting a branch or its descendants to determine if the branch needs to be
   * visited.
   *
   * @param root the hash tree root of the branch node
   * @param gIndex the generalized index of the branch node
   * @return true if the branch node and all its descendants can be skipped, false to iterate into
   *     the descendants
   */
  boolean canSkipBranch(Bytes32 root, long gIndex);

  /**
   * Called when an intermediate branch node is visited. Multiple levels of branch nodes may be
   * skipped to optimise iteration and storage, in which case the children are {@code depth} levels
   * from the branch node.
   *
   * @param root the hash tree root of the branch node
   * @param gIndex the generalised index of the branch node
   * @param depth the number of tree levels being skipped. ie the depth of the tree from the branch
   *     node to the children
   * @param children the non-empty children at the specified depth from the branch node.
   */
  void onBranchNode(Bytes32 root, long gIndex, int depth, Bytes32[] children);

  /**
   * Called when a leaf node is visited.
   *
   * @param node the visited leaf node
   * @param gIndex the generalized index of the node
   */
  void onLeafNode(LeafDataNode node, long gIndex);
}
