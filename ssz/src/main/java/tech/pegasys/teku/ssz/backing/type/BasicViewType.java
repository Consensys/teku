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

package tech.pegasys.teku.ssz.backing.type;

import static com.google.common.base.Preconditions.checkArgument;

import tech.pegasys.teku.ssz.backing.ViewRead;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.tree.TreeUtil;

/**
 * Represents primitive view type
 *
 * @param <C> Class of the basic view of this type
 */
public abstract class BasicViewType<C extends ViewRead> implements ViewType {

  private final int bitsSize;

  BasicViewType(int bitsSize) {
    checkArgument(
        bitsSize > 0 && bitsSize <= 256 && 256 % bitsSize == 0, "Invalid bitsize: %s", bitsSize);
    this.bitsSize = bitsSize;
  }

  @Override
  public int getBitsSize() {
    return bitsSize;
  }

  @Override
  public TreeNode getDefaultTree() {
    return TreeUtil.ZERO_LEAF;
  }

  @Override
  public C createFromBackingNode(TreeNode node) {
    return createFromBackingNode(node, 0);
  }

  @Override
  public abstract C createFromBackingNode(TreeNode node, int internalIndex);

  public TreeNode createBackingNode(C newValue) {
    return updateBackingNode(TreeUtil.ZERO_LEAF, 0, newValue);
  }

  @Override
  public abstract TreeNode updateBackingNode(
      TreeNode srcNode, int internalIndex, ViewRead newValue);
}
