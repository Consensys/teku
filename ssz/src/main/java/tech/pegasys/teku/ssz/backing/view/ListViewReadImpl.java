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

package tech.pegasys.teku.ssz.backing.view;

import java.util.Arrays;
import java.util.Objects;
import tech.pegasys.teku.ssz.backing.ListViewRead;
import tech.pegasys.teku.ssz.backing.ListViewWrite;
import tech.pegasys.teku.ssz.backing.VectorViewRead;
import tech.pegasys.teku.ssz.backing.ViewRead;
import tech.pegasys.teku.ssz.backing.cache.IntCache;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.type.BasicViewTypes;
import tech.pegasys.teku.ssz.backing.type.ContainerViewType;
import tech.pegasys.teku.ssz.backing.type.ListViewType;
import tech.pegasys.teku.ssz.backing.type.VectorViewType;
import tech.pegasys.teku.ssz.backing.view.ListViewWriteImpl.ListContainerWrite;

/**
 * View of SSZ List type. This view is compatible with and implemented as a <code>
 * Container[Vector(maxLength), size]</code> under the cover.
 */
public class ListViewReadImpl<ElementType extends ViewRead> implements ListViewRead<ElementType> {

  static class ListContainerRead<ElementType extends ViewRead> extends ContainerViewReadImpl {

    private static <C extends ViewRead>
        ContainerViewType<ListContainerRead<C>> vectorTypeToContainerType(
            VectorViewType<C> vectorType) {
      return ContainerViewType.create(
          Arrays.asList(vectorType, BasicViewTypes.UINT64_TYPE), ListContainerRead::new);
    }

    public ListContainerRead(VectorViewType<ElementType> vectorType) {
      super(vectorTypeToContainerType(vectorType));
    }

    ListContainerRead(
        ContainerViewType<ListContainerRead<ElementType>> containerType, TreeNode backingNode) {
      super(containerType, backingNode);
    }

    public ListContainerRead(VectorViewType<ElementType> vectorType, TreeNode backingNode) {
      super(vectorTypeToContainerType(vectorType), backingNode);
    }

    public ListContainerRead(
        VectorViewType<ElementType> vectorType, TreeNode backingNode, IntCache<ViewRead> cache) {
      super(vectorTypeToContainerType(vectorType), backingNode, cache);
    }

    public int getSize() {
      return (int) ((BasicViews.UInt64View) get(1)).longValue();
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

  public ListViewReadImpl(ListViewType<ElementType> type, TreeNode node) {
    this.type = type;
    this.container = new ListContainerRead<>(type.getCompatibleVectorType(), node);
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

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ListViewRead)) {
      return false;
    }
    return hashTreeRoot().equals(((ViewRead) o).hashTreeRoot());
  }

  @Override
  public int hashCode() {
    return Objects.hash(hashTreeRoot());
  }
}
