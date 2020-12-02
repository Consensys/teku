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

import static tech.pegasys.teku.ssz.backing.view.BasicViews.UInt64View;
import static tech.pegasys.teku.ssz.backing.view.ListViewReadImpl.ListContainerRead;

import java.util.function.Consumer;
import tech.pegasys.teku.ssz.backing.ListViewRead;
import tech.pegasys.teku.ssz.backing.ListViewWrite;
import tech.pegasys.teku.ssz.backing.ListViewWriteRef;
import tech.pegasys.teku.ssz.backing.VectorViewWriteRef;
import tech.pegasys.teku.ssz.backing.ViewRead;
import tech.pegasys.teku.ssz.backing.ViewWrite;
import tech.pegasys.teku.ssz.backing.cache.IntCache;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.type.ListViewType;
import tech.pegasys.teku.ssz.backing.type.VectorViewType;

public class ListViewWriteImpl<
        ElementReadType extends ViewRead, ElementWriteType extends ElementReadType>
    implements ListViewWriteRef<ElementReadType, ElementWriteType> {

  static class ListContainerWrite<
          ElementReadType extends ViewRead, ElementWriteType extends ElementReadType>
      extends ContainerViewWriteImpl {
    private final VectorViewType<ElementReadType> vectorType;

    public ListContainerWrite(ListContainerRead<ElementReadType> backingImmutableView) {
      super(backingImmutableView);
      vectorType = backingImmutableView.getVectorType();
    }

    public int getSize() {
      return (int) ((UInt64View) get(1)).longValue();
    }

    public void setSize(int size) {
      set(1, UInt64View.fromLong(size));
    }

    public VectorViewWriteRef<ElementReadType, ElementWriteType> getData() {
      return getAnyByRef(0);
    }

    @Override
    protected AbstractCompositeViewRead<ViewRead> createViewRead(
        TreeNode backingNode, IntCache<ViewRead> viewCache) {
      return new ListContainerRead<ElementReadType>(vectorType, backingNode, viewCache);
    }

    @Override
    @SuppressWarnings("unchecked")
    public ListContainerRead<ElementReadType> commitChanges() {
      return (ListContainerRead<ElementReadType>) super.commitChanges();
    }
  }

  private final ListViewType<ElementReadType> type;
  private final ListContainerWrite<ElementReadType, ElementWriteType> container;
  private int cachedSize;

  public ListViewWriteImpl(
      ListViewType<ElementReadType> type,
      ListContainerWrite<ElementReadType, ElementWriteType> container) {
    this.type = type;
    this.container = container;
    this.cachedSize = this.container.getSize();
  }

  @Override
  public ListViewType<ElementReadType> getType() {
    return type;
  }

  @Override
  public int size() {
    return cachedSize;
  }

  @Override
  public ElementReadType get(int index) {
    checkIndex(index, false);
    return container.getData().get(index);
  }

  @Override
  public ElementWriteType getByRef(int index) {
    checkIndex(index, false);
    return container.getData().getByRef(index);
  }

  @Override
  public ListViewRead<ElementReadType> commitChanges() {
    return new ListViewReadImpl<>(getType(), container.commitChanges());
  }

  @Override
  public void setInvalidator(Consumer<ViewWrite> listener) {
    container.setInvalidator(listener);
  }

  @Override
  public void set(int index, ElementReadType value) {
    checkIndex(index, true);
    if (index == size()) {
      cachedSize++;
      container.setSize(cachedSize);
    }
    container.getData().set(index, value);
  }

  @Override
  public void clear() {
    container.clear();
    cachedSize = 0;
  }

  protected void checkIndex(int index, boolean set) {
    if ((!set && index >= size())
        || (set && (index > size() || index >= getType().getMaxLength()))) {
      throw new IndexOutOfBoundsException(
          "Invalid index " + index + " for list with size " + size());
    }
  }

  @Override
  public ListViewWrite<ElementReadType> createWritableCopy() {
    throw new UnsupportedOperationException("Creating a copy from writable list is not supported");
  }
}
