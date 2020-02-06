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
import tech.pegasys.artemis.util.backing.ContainerViewWrite;
import tech.pegasys.artemis.util.backing.ListViewRead;
import tech.pegasys.artemis.util.backing.ListViewWrite;
import tech.pegasys.artemis.util.backing.VectorViewRead;
import tech.pegasys.artemis.util.backing.VectorViewWrite;
import tech.pegasys.artemis.util.backing.ViewRead;
import tech.pegasys.artemis.util.backing.tree.TreeNode;
import tech.pegasys.artemis.util.backing.type.BasicViewTypes;
import tech.pegasys.artemis.util.backing.type.ContainerViewType;
import tech.pegasys.artemis.util.backing.type.ListViewType;
import tech.pegasys.artemis.util.backing.view.BasicViews.UnsignedLongView;

public class ListViewImpl<C extends ViewRead> implements ListViewWrite<C> {

  private final ContainerViewWrite<ViewRead> container;

  public ListViewImpl(VectorViewRead<C> data, int size) {
    ContainerViewType<ContainerViewWrite<ViewRead>> containerViewType =
        new ContainerViewType<>(
            Arrays.asList(data.getType(), BasicViewTypes.UNSIGNED_LONG_TYPE),
            ContainerViewImpl::new);
    container = containerViewType.createDefault();
    container.set(0, data);
    container.set(1, new UnsignedLongView(UnsignedLong.valueOf(size)));
  }

  public ListViewImpl(ListViewType<C> type, TreeNode node) {
    ContainerViewType<ContainerViewWrite<ViewRead>> containerViewType =
        new ContainerViewType<>(
            Arrays.asList(type.getCompatibleVectorType(), BasicViewTypes.UNSIGNED_LONG_TYPE),
            ContainerViewImpl::new);
    container = containerViewType.createFromTreeNode(node);
  }

  @Override
  public int size() {
    UnsignedLongView sizeView = (UnsignedLongView) container.get(1);
    return sizeView.get().intValue();
  }

  @Override
  public C get(int index) {
    int size = size();
    checkArgument(index >= 0 && index < size, "Index out of bounds: %s, size=%s", index, size);
    return getVector().get(index);
  }

  @Override
  public void set(int index, C value) {
    int size = size();
    checkArgument(
        (index >= 0 && index < size) || (index == size && index < getType().getMaxLength()),
        "Index out of bounds: %s, size=%s",
        index,
        size());

    if (index == size) {
      container.set(1, new UnsignedLongView(UnsignedLong.valueOf(size + 1)));
    }

    container.update(
        0,
        view -> {
          @SuppressWarnings("unchecked")
          VectorViewWrite<C> vector = (VectorViewWrite<C>) view;
          vector.set(index, value);
          return vector;
        });
  }

  @SuppressWarnings("unchecked")
  private VectorViewWrite<C> getVector() {
    return (VectorViewWrite<C>) container.get(0);
  }

  @Override
  public ListViewType<C> getType() {
    return new ListViewType<>(getVector().getType());
  }

  @Override
  public TreeNode getBackingNode() {
    return container.getBackingNode();
  }

  @Override
  public ListViewRead<C> commitChanges() {
    return this;
  }

  @Override
  public ListViewWrite<C> createWritableCopy() {
    return this;
  }
}
