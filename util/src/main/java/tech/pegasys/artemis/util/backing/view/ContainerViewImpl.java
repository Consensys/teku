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

import tech.pegasys.artemis.util.backing.CompositeViewWrite;
import tech.pegasys.artemis.util.backing.ContainerViewWrite;
import tech.pegasys.artemis.util.backing.ContainerViewWriteRef;
import tech.pegasys.artemis.util.backing.ViewRead;
import tech.pegasys.artemis.util.backing.ViewWrite;
import tech.pegasys.artemis.util.backing.tree.TreeNode;
import tech.pegasys.artemis.util.backing.type.ContainerViewType;

public class ContainerViewImpl<C extends ContainerViewImpl<C>>
    extends AbstractCompositeViewWrite<C, ViewRead>
    implements ContainerViewWriteRef<ViewRead, ViewWrite> {

  private final ContainerViewType<? extends ContainerViewWrite<ViewRead>> type;
  private TreeNode backingNode;

  public ContainerViewImpl(ContainerViewType<? extends ContainerViewWrite<ViewRead>> type) {
    this(type, type.createDefaultTree());
  }

  public ContainerViewImpl(
      ContainerViewType<? extends ContainerViewWrite<ViewRead>> type, TreeNode backingNode) {
    this.type = type;
    this.backingNode = backingNode;
  }

  public ContainerViewImpl(
      ContainerViewType<? extends ContainerViewWrite<ViewRead>> type, ViewRead... memberValues) {
    this(type, type.createDefaultTree());
    checkArgument(
        memberValues.length == getType().getMaxLength(),
        "Wrong number of member values: %s",
        memberValues.length);
    for (int i = 0; i < memberValues.length; i++) {
      set(i, memberValues[i]);
    }
  }

  @Override
  public ContainerViewType<? extends ContainerViewWrite<ViewRead>> getType() {
    return type;
  }

  @Override
  public TreeNode getBackingNode() {
    return backingNode;
  }

  @Override
  public ViewRead get(int index) {
    checkIndex(index);
    TreeNode node = backingNode.get(type.treeWidth() + index);
    return type.getChildType(index).createFromTreeNode(node);
  }

  @Override
  public ViewWrite getByRef(int index) {
    ViewWrite writableCopy = get(index).createWritableCopy();
    if (writableCopy instanceof CompositeViewWrite) {
      ((CompositeViewWrite<?>) writableCopy).setInvalidator(viewWrite -> set(index, viewWrite));
    }
    return writableCopy;
  }

  @Override
  public void set(int index, ViewRead child) {
    checkIndex(index);
    checkArgument(
        child.getType().equals(type.getChildType(index)),
        "Wrong child type at index %s. Expected: %s, was %s",
        index,
        type.getChildType(index),
        child.getType());
    backingNode = backingNode.set(type.treeWidth() + index, child.getBackingNode());
    invalidate();
  }

  @Override
  public void clear() {
    backingNode = getType().createDefaultTree();
    invalidate();
  }

  private void checkIndex(int index) {
    checkArgument(index >= 0 && index < type.getMaxLength(), "Index out of bounds: %s", index);
  }
}
