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

import java.util.Arrays;
import tech.pegasys.artemis.util.backing.ListViewRead;
import tech.pegasys.artemis.util.backing.ListViewWrite;
import tech.pegasys.artemis.util.backing.VectorViewRead;
import tech.pegasys.artemis.util.backing.ViewRead;
import tech.pegasys.artemis.util.backing.cache.IntCache;
import tech.pegasys.artemis.util.backing.tree.TreeNode;
import tech.pegasys.artemis.util.backing.type.BasicViewTypes;
import tech.pegasys.artemis.util.backing.type.ContainerViewType;
import tech.pegasys.artemis.util.backing.type.ListViewType;
import tech.pegasys.artemis.util.backing.type.VectorViewType;
import tech.pegasys.artemis.util.backing.view.BasicViews.UInt64View;
import tech.pegasys.artemis.util.backing.view.ListViewWriteImpl.ListContainerWrite;

/**
 * View of SSZ List type. This view is compatible with and implemented as a <code>
 * Container[Vector(maxLength), size]</code> under the cover.
 */
public class ListViewReadImpl<ElementType extends ViewRead> implements ListViewRead<ElementType> {

  static class ListContainerRead<ElementType extends ViewRead> extends ContainerViewReadImpl {

    private static <C extends ViewRead>
        ContainerViewType<ListContainerRead<C>> vectorTypeToContainerType(
            VectorViewType<C> vectorType) {
      return new ContainerViewType<>(
          Arrays.asList(vectorType, BasicViewTypes.UINT64_TYPE), ListContainerRead::new);
    }

    public ListContainerRead(VectorViewType<ElementType> vectorType) {
      super(vectorTypeToContainerType(vectorType));
    }

    ListContainerRead(
        ContainerViewType<ListContainerRead<ElementType>> containerType, TreeNode backingNode) {
      super(containerType, backingNode);
    }

    public ListContainerRead(
        VectorViewType<ElementType> vectorType, TreeNode backingNode, IntCache<ViewRead> cache) {
      super(vectorTypeToContainerType(vectorType), backingNode, cache);
    }

    public int getSize() {
      return (int) ((UInt64View) get(1)).longValue();
    }

    public VectorViewRead<ElementType> getData() {
      return getAny(0);
    }

    @Override
    public ListContainerWrite<ElementType, ?> createWritableCopy() {
      return new ListContainerWrite<>(this);
    }

    @SuppressWarnings("unchecked")
    VectorViewType<ElementType> getVectorType() {
      return (VectorViewType<ElementType>) getType().getChildType(0);
    }
  }

  private final ListViewType<ElementType> type;
  private final ListContainerRead<ElementType> container;
  private final int cachedSize;
  //  private final int size;
  //  private final VectorViewRead<C> vector;

  public ListViewReadImpl(ListViewType<ElementType> type, TreeNode node) {
    this.type = type;
    this.container = new ListContainerRead<>(type.getCompatibleVectorType(), node, null);
    this.cachedSize = container.getSize();
  }

  public ListViewReadImpl(ListViewType<ElementType> type) {
    this.type = type;
    this.container = new ListContainerRead<>(type.getCompatibleVectorType());
    this.cachedSize = container.getSize();
  }

  public ListViewReadImpl(
      ListViewType<ElementType> type, ListContainerRead<ElementType> container) {
    this.type = type;
    this.container = container;
    this.cachedSize = container.getSize();
  }

  @Override
  public ElementType get(int index) {
    checkIndex(index);
    return container.getData().get(index);
  }

  @Override
  public ListViewWrite<ElementType> createWritableCopy() {
    return new ListViewWriteImpl<>(getType(), container.createWritableCopy());
  }

  @Override
  public ListViewType<ElementType> getType() {
    return type;
  }

  @Override
  public int size() {
    return cachedSize;
  }

  @Override
  public TreeNode getBackingNode() {
    return container.getBackingNode();
  }

  protected void checkIndex(int index) {
    if (index >= size()) {
      throw new IndexOutOfBoundsException(
          "Invalid index " + index + " for list with size " + size());
    }
  }
}
