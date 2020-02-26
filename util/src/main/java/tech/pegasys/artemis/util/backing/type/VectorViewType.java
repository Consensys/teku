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

import tech.pegasys.artemis.util.backing.VectorViewRead;
import tech.pegasys.artemis.util.backing.ViewType;
import tech.pegasys.artemis.util.backing.tree.TreeNode;
import tech.pegasys.artemis.util.backing.tree.TreeUtil;
import tech.pegasys.artemis.util.backing.view.VectorViewImpl;

public class VectorViewType<C> extends CollectionViewType {
  private final boolean isListBacking;

  public VectorViewType(ViewType elementType, long maxLength) {
    this(elementType, maxLength, false);
  }

  VectorViewType(ViewType elementType, long maxLength, boolean isListBacking) {
    super(maxLength, elementType);
    this.isListBacking = isListBacking;
  }

  @Override
  public VectorViewRead<C> createDefault() {
    return createFromTreeNode(createDefaultTree());
  }

  @Override
  public TreeNode createDefaultTree() {
    return isListBacking
        ? TreeUtil.createZeroTree(maxChunks())
        : TreeUtil.createDefaultTree(
            (int) maxChunks(), getElementType().createDefault().getBackingNode());
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  @Override
  public VectorViewRead<C> createFromTreeNode(TreeNode node) {
    return new VectorViewImpl(this, node);
  }
}
