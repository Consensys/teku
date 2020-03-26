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

package tech.pegasys.artemis.util.backing.view;

import tech.pegasys.artemis.util.backing.VectorViewRead;
import tech.pegasys.artemis.util.backing.ViewRead;
import tech.pegasys.artemis.util.backing.cache.ArrayCache;
import tech.pegasys.artemis.util.backing.cache.IntCache;
import tech.pegasys.artemis.util.backing.tree.TreeNode;
import tech.pegasys.artemis.util.backing.type.CompositeViewType;
import tech.pegasys.artemis.util.backing.type.VectorViewType;
import tech.pegasys.artemis.util.backing.type.ViewType;

public class VectorViewReadImpl<ElementReadType extends ViewRead>
    extends AbstractCompositeViewRead<ElementReadType> implements VectorViewRead<ElementReadType> {

  public VectorViewReadImpl(CompositeViewType type, TreeNode backingNode) {
    super(type, backingNode);
  }

  public VectorViewReadImpl(
      CompositeViewType type, TreeNode backingNode, IntCache<ElementReadType> cache) {
    super(type, backingNode, cache);
  }

  @SuppressWarnings("unchecked")
  @Override
  protected ElementReadType getImpl(int index) {
    VectorViewType<ElementReadType> type = getType();
    ViewType elementType = type.getElementType();
    TreeNode node =
        getBackingNode().get(type.getGeneralizedIndex(index / type.getElementsPerChunk()));
    return (ElementReadType)
        elementType.createFromBackingNode(node, index % type.getElementsPerChunk());
  }

  @Override
  protected int sizeImpl() {
    return (int) Long.min(Integer.MAX_VALUE, getType().getMaxLength());
  }

  @Override
  public VectorViewWriteImpl<ElementReadType, ?> createWritableCopy() {
    return new VectorViewWriteImpl<>(this);
  }

  @SuppressWarnings("unchecked")
  @Override
  public VectorViewType<ElementReadType> getType() {
    return (VectorViewType<ElementReadType>) super.getType();
  }

  @Override
  protected IntCache<ElementReadType> createCache() {
    return size() > 16384 ? new ArrayCache<>() : new ArrayCache<>(size());
  }

  @Override
  protected void checkIndex(int index) {
    if (index >= size()) {
      throw new IndexOutOfBoundsException(
          "Invalid index " + index + " for vector with size " + size());
    }
  }
}
