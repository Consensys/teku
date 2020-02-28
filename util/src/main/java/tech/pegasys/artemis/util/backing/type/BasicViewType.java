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

package tech.pegasys.artemis.util.backing.type;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.util.backing.ViewRead;
import tech.pegasys.artemis.util.backing.ViewType;
import tech.pegasys.artemis.util.backing.tree.TreeNode;

/**
 * Represents primitive view type
 *
 * @param <C> Class of the basic view of this type
 */
public abstract class BasicViewType<C extends ViewRead> implements ViewType {

  private final int bitsSize;

  BasicViewType(int bitsSize) {
    this.bitsSize = bitsSize;
  }

  @Override
  public int getBitsSize() {
    return bitsSize;
  }

  @Override
  public TreeNode getDefaultTree() {
    return TreeNode.createRoot(Bytes32.ZERO);
  }

  @Override
  public C createFromTreeNode(TreeNode node) {
    return createFromTreeNode(node, 0);
  }

  @Override
  public abstract C createFromTreeNode(TreeNode node, int internalIndex);

  public TreeNode createTreeNode(C newValue) {
    return updateTreeNode(TreeNode.createRoot(Bytes32.ZERO), 0, newValue);
  }

  @Override
  public abstract TreeNode updateTreeNode(TreeNode srcNode, int internalIndex, ViewRead newValue);
}
