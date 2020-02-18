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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.primitives.UnsignedLong;
import java.util.Arrays;
import java.util.List;
import tech.pegasys.artemis.util.backing.CompositeViewWrite;
import tech.pegasys.artemis.util.backing.ContainerViewWrite;
import tech.pegasys.artemis.util.backing.ListViewWriteRef;
import tech.pegasys.artemis.util.backing.VectorViewWrite;
import tech.pegasys.artemis.util.backing.ViewRead;
import tech.pegasys.artemis.util.backing.tree.TreeNode;
import tech.pegasys.artemis.util.backing.type.BasicViewTypes;
import tech.pegasys.artemis.util.backing.type.ContainerViewType;
import tech.pegasys.artemis.util.backing.type.ListViewType;
import tech.pegasys.artemis.util.backing.type.VectorViewType;
import tech.pegasys.artemis.util.backing.view.BasicViews.UInt64View;

public class ListViewImpl<R extends ViewRead, W extends R>
    extends AbstractCompositeViewWrite<ListViewImpl<R, W>, R> implements ListViewWriteRef<R, W> {

  private final ContainerViewWrite<ViewRead> container;

  public ListViewImpl(VectorViewType<R> vectorType) {
    ContainerViewType<ContainerViewWrite<ViewRead>> containerViewType =
        new ContainerViewType<>(
            List.of(vectorType, BasicViewTypes.UINT64_TYPE), ContainerViewImpl::new);
    container = containerViewType.createDefault();
  }

  public ListViewImpl(ListViewType<R> type, TreeNode node) {
    ContainerViewType<ContainerViewWrite<ViewRead>> containerViewType =
        new ContainerViewType<>(
            Arrays.asList(type.getCompatibleVectorType(), BasicViewTypes.UINT64_TYPE),
            ContainerViewImpl::new);
    container = containerViewType.createFromTreeNode(node);
  }

  @Override
  public int size() {
    UInt64View sizeView = (UInt64View) container.get(1);
    return sizeView.get().intValue();
  }

  @Override
  public R get(int index) {
    int size = size();
    checkArgument(index >= 0 && index < size, "Index out of bounds: %s, size=%s", index, size);
    return getVector().get(index);
  }

  @Override
  public W getByRef(int index) {
    @SuppressWarnings("unchecked")
    W writableCopy = (W) get(index).createWritableCopy();

    if (writableCopy instanceof CompositeViewWrite) {
      ((CompositeViewWrite<?>) writableCopy).setIvalidator(viewWrite -> set(index, writableCopy));
    }
    return writableCopy;
  }

  @Override
  public R set(int index, R value) {
    int size = size();
    checkArgument(
        (index >= 0 && index < size) || (index == size && index < getType().getMaxLength()),
        "Index out of bounds: %s, size=%s",
        index,
        size());

    if (index == size) {
      container.set(1, new UInt64View(UnsignedLong.valueOf(size + 1)));
    }

    container.update(
        0,
        view -> {
          @SuppressWarnings("unchecked")
          VectorViewWrite<R> vector = (VectorViewWrite<R>) view;
          vector.set(index, value);
          return vector;
        });

    invalidate();
    return null;
  }

  @Override
  public void clear() {
    container.clear();
  }

  @SuppressWarnings("unchecked")
  private VectorViewWrite<R> getVector() {
    return (VectorViewWrite<R>) container.get(0);
  }

  @Override
  public ListViewType<R> getType() {
    return new ListViewType<>(getVector().getType());
  }

  @Override
  public TreeNode getBackingNode() {
    return container.getBackingNode();
  }
}
