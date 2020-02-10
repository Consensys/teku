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

import java.util.function.Function;
import tech.pegasys.artemis.util.backing.CompositeViewWrite;
import tech.pegasys.artemis.util.backing.VectorViewWriteRef;
import tech.pegasys.artemis.util.backing.ViewRead;
import tech.pegasys.artemis.util.backing.tree.TreeNode;
import tech.pegasys.artemis.util.backing.tree.TreeNode.Commit;
import tech.pegasys.artemis.util.backing.type.VectorViewType;

public class VectorViewImpl<R extends ViewRead, W extends R>
    extends AbstractCompositeViewWrite<VectorViewImpl<R, W>, R>
    implements VectorViewWriteRef<R, W> {

  protected final VectorViewType<R> type;
  private TreeNode backingNode;

  public VectorViewImpl(VectorViewType<R> type, TreeNode backingNode) {
    this.type = type;
    this.backingNode = backingNode;
  }

  @Override
  public void set(int index, R value) {
    checkArgument(
        index >= 0 && index < type.getMaxLength(),
        "Index out of bounds: %s, size=%s",
        index,
        size());

    backingNode =
        updateNode(
            index / type.getElementsPerChunk(),
            oldBytes ->
                type.getElementType()
                    .updateTreeNode(oldBytes, index % type.getElementsPerChunk(), value));
    invalidate();
  }

  @Override
  public R get(int index) {
    checkArgument(
        index >= 0 && index < type.getMaxLength(),
        "Index out of bounds: %s, size=%s",
        index,
        size());
    TreeNode node = getNode(index / type.getElementsPerChunk());
    @SuppressWarnings("unchecked")
    R ret = (R) type.getElementType().createFromTreeNode(node, index % type.getElementsPerChunk());
    return ret;
  }

  @Override
  public W getByRef(int index) {
    @SuppressWarnings("unchecked")
    W writableCopy = (W) get(index).createWritableCopy();

    if (writableCopy instanceof CompositeViewWrite) {
      ((CompositeViewWrite<?>) writableCopy)
          .setIvalidator(viewWrite -> set(index, writableCopy));
    }
    return writableCopy;
  }

  private Commit updateNode(int listIndex, Function<TreeNode, TreeNode> nodeUpdater) {
    return (Commit) backingNode.update(type.treeWidth() + listIndex, nodeUpdater);
  }

  private TreeNode getNode(int listIndex) {
    return backingNode.get(type.treeWidth() + listIndex);
  }

  @Override
  public VectorViewType<R> getType() {
    return type;
  }

  @Override
  public TreeNode getBackingNode() {
    return backingNode;
  }
}
