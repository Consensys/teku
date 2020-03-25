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

import java.util.function.Consumer;
import tech.pegasys.artemis.util.backing.ListViewRead;
import tech.pegasys.artemis.util.backing.ListViewWrite;
import tech.pegasys.artemis.util.backing.ListViewWriteRef;
import tech.pegasys.artemis.util.backing.VectorViewWriteRef;
import tech.pegasys.artemis.util.backing.ViewRead;
import tech.pegasys.artemis.util.backing.ViewWrite;
import tech.pegasys.artemis.util.backing.cache.IntCache;
import tech.pegasys.artemis.util.backing.tree.TreeNode;
import tech.pegasys.artemis.util.backing.type.ListViewType;
import tech.pegasys.artemis.util.backing.type.VectorViewType;
import tech.pegasys.artemis.util.backing.view.BasicViews.UInt64View;
import tech.pegasys.artemis.util.backing.view.ListViewReadImpl.ListContainerRead;

public class ListViewWriteImpl<R extends ViewRead, W extends R> implements ListViewWriteRef<R, W> {

  static class ListContainerWrite<R extends ViewRead, W extends R> extends ContainerViewWriteImpl {
    private final VectorViewType<R> vectorType;

    public ListContainerWrite(ListContainerRead<R> backingImmutableView) {
      super(backingImmutableView);
      vectorType = backingImmutableView.getVectorType();
    }

    public int getSize() {
      return (int) ((UInt64View) get(1)).longValue();
    }

    public void setSize(int size) {
      set(1, UInt64View.fromLong(size));
    }

    public VectorViewWriteRef<R, W> getData() {
      return getAnyByRef(0);
    }

    @Override
    protected AbstractCompositeViewRead<?, ViewRead> createViewRead(
        TreeNode backingNode, IntCache<ViewRead> viewCache) {
      return new ListContainerRead<R>(vectorType, backingNode, viewCache);
    }

    @Override
    @SuppressWarnings("unchecked")
    public ListContainerRead<R> commitChanges() {
      return (ListContainerRead<R>) super.commitChanges();
    }
  }

  private final ListViewType<R> type;
  private final ListContainerWrite<R, W> container;
  private int cachedSize;

  public ListViewWriteImpl(ListViewType<R> type, ListContainerWrite<R, W> container) {
    this.type = type;
    this.container = container;
    this.cachedSize = this.container.getSize();
  }

  @Override
  public ListViewType<R> getType() {
    return type;
  }

  @Override
  public int size() {
    return cachedSize;
  }

  @Override
  public R get(int index) {
    checkIndex(index, false);
    return container.getData().get(index);
  }

  @Override
  public W getByRef(int index) {
    checkIndex(index, false);
    return container.getData().getByRef(index);
  }

  @Override
  public ListViewRead<R> commitChanges() {
    return new ListViewReadImpl<R>(getType(), container.commitChanges());
  }

  @Override
  public void setInvalidator(Consumer<ViewWrite> listener) {
    container.setInvalidator(listener);
  }

  @Override
  public void set(int index, R value) {
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
  public ListViewWrite<R> createWritableCopy() {
    throw new UnsupportedOperationException("Creating a copy from writable list is not supported");
  }
}
