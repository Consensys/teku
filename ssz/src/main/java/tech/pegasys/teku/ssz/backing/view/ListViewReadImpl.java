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

import java.util.Objects;
import java.util.stream.Collectors;
import tech.pegasys.teku.ssz.backing.ListViewRead;
import tech.pegasys.teku.ssz.backing.ListViewWrite;
import tech.pegasys.teku.ssz.backing.VectorViewRead;
import tech.pegasys.teku.ssz.backing.ViewRead;
import tech.pegasys.teku.ssz.backing.cache.IntCache;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.type.ContainerViewType;
import tech.pegasys.teku.ssz.backing.type.ListViewType;
import tech.pegasys.teku.ssz.backing.view.ListViewWriteImpl.ListContainerWrite;

/**
 * View of SSZ List type. This view is compatible with and implemented as a <code>
 * Container[Vector(maxLength), size]</code> under the cover.
 */
public class ListViewReadImpl<ElementType extends ViewRead> implements ListViewRead<ElementType> {

  public static class ListContainerRead<ElementType extends ViewRead>
      extends ContainerViewReadImpl {
    private final ListViewType<ElementType> type;

    private ListContainerRead(ListViewType<ElementType> type) {
      super(type.getCompatibleListContainerType());
      this.type = type;
    }

    public ListContainerRead(
        ListViewType<ElementType> type, ContainerViewType<?> containerType, TreeNode backingNode) {
      super(containerType, backingNode);
      this.type = type;
    }

    public ListContainerRead(ListViewType<ElementType> type, TreeNode backingNode) {
      super(type.getCompatibleListContainerType(), backingNode);
      this.type = type;
    }

    public ListContainerRead(
        ListViewType<ElementType> type, TreeNode backingNode, IntCache<ViewRead> cache) {
      super(type.getCompatibleListContainerType(), backingNode, cache);
      this.type = type;
    }

    public int getSize() {
      return (int) ((BasicViews.UInt64View) get(1)).longValue();
    }

    public VectorViewRead<ElementType> getData() {
      return getAny(0);
    }

    @Override
    public ListContainerWrite<ElementType, ?> createWritableCopy() {
      return new ListContainerWrite<>(this, type);
    }
  }

  private final ListViewType<ElementType> type;
  private final ListContainerRead<ElementType> container;
  private final int cachedSize;

  protected ListViewReadImpl(ListViewRead<ElementType> other) {
    if (other instanceof ListViewReadImpl) {
      // optimization to preserve child view caches
      ListViewReadImpl<ElementType> otherImpl = (ListViewReadImpl<ElementType>) other;
      this.type = otherImpl.type;
      this.container = otherImpl.container;
      this.cachedSize = otherImpl.cachedSize;
    } else {
      this.type = other.getType();
      this.container = new ListContainerRead<>(type, other.getBackingNode());
      this.cachedSize = container.getSize();
    }
  }

  public ListViewReadImpl(ListViewType<ElementType> type, TreeNode node) {
    this.type = type;
    this.container = new ListContainerRead<>(type, node);
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

  @Override
  public String toString() {
    int maxToDisplay = 1024;
    String elements =
        stream().limit(maxToDisplay).map(Object::toString).collect(Collectors.joining(", "));
    if (size() > maxToDisplay) {
      elements += " ... more " + (size() - maxToDisplay) + " elements";
    }
    return "ListViewRead{size=" + size() + ": " + elements + "}";
  }
}
