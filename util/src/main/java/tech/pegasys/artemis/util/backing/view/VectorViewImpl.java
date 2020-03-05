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

import java.util.function.Function;
import tech.pegasys.artemis.util.backing.CompositeViewWrite;
import tech.pegasys.artemis.util.backing.VectorViewWriteRef;
import tech.pegasys.artemis.util.backing.ViewRead;
import tech.pegasys.artemis.util.backing.tree.TreeNode;
import tech.pegasys.artemis.util.backing.type.VectorViewType;

public class VectorViewImpl<R extends ViewRead, W extends R>
    extends AbstractCompositeViewWrite<VectorViewImpl<R, W>, R>
    implements VectorViewWriteRef<R, W> {

  protected final VectorViewType<R> type;
  private TreeNode backingNode;
  private final long size;

  public VectorViewImpl(VectorViewType<R> type, TreeNode backingNode) {
    this.size = type.getMaxLength();
    this.type = type;
    this.backingNode = backingNode;
  }

  @Override
  public void set(int index, R value) {
    checkIndex(index);
    backingNode =
        updateNode(
            index / type.getElementsPerChunk(),
            oldBytes ->
                type.getElementType()
                    .updateBackingNode(oldBytes, index % type.getElementsPerChunk(), value));
    invalidate();
  }

  @Override
  public void clear() {
    backingNode = getType().getDefaultTree();
    invalidate();
  }

  @Override
  public R get(int index) {
    checkIndex(index);

    TreeNode node = getNode(index / type.getElementsPerChunk());
    @SuppressWarnings("unchecked")
    R ret =
        (R) type.getElementType().createFromBackingNode(node, index % type.getElementsPerChunk());
    return ret;
  }

  @Override
  public W getByRef(int index) {
    checkIndex(index);

    @SuppressWarnings("unchecked")
    W writableCopy = (W) get(index).createWritableCopy();

    if (writableCopy instanceof CompositeViewWrite) {
      ((CompositeViewWrite<?>) writableCopy).setInvalidator(viewWrite -> set(index, writableCopy));
    }
    return writableCopy;
  }

  private TreeNode updateNode(int listIndex, Function<TreeNode, TreeNode> nodeUpdater) {
    return backingNode.updated(type.getGeneralizedIndex(listIndex), nodeUpdater);
  }

  private TreeNode getNode(int listIndex) {
    return backingNode.get(type.getGeneralizedIndex(listIndex));
  }

  @Override
  public VectorViewType<R> getType() {
    return type;
  }

  @Override
  public TreeNode getBackingNode() {
    return backingNode;
  }

  private void checkIndex(int index) {
    if (index < 0 || index >= size) {
      throw new IndexOutOfBoundsException(
          "Index out of bounds: " + index + ", size=" + getType().getMaxLength());
    }
  }
}
