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

import tech.pegasys.artemis.util.backing.ContainerViewRead;
import tech.pegasys.artemis.util.backing.ViewRead;
import tech.pegasys.artemis.util.backing.ViewWrite;
import tech.pegasys.artemis.util.backing.tree.TreeNode;
import tech.pegasys.artemis.util.backing.type.ContainerViewType;

public abstract class AbstractImmutableContainer<C extends AbstractImmutableContainer<C>>
    implements ContainerViewRead {

  private final ContainerViewType<? extends AbstractImmutableContainer<C>> type;
  private TreeNode backingNode;

  public AbstractImmutableContainer(
      ContainerViewType<? extends AbstractImmutableContainer<C>> type) {
    this(type, type.getDefaultTree());
  }

  public AbstractImmutableContainer(
      ContainerViewType<? extends AbstractImmutableContainer<C>> type, TreeNode backingNode) {
    this.type = type;
    this.backingNode = backingNode;
  }

  public AbstractImmutableContainer(
      ContainerViewType<? extends AbstractImmutableContainer<C>> type, ViewRead... memberValues) {
    this(type, type.getDefaultTree());
    checkArgument(
        memberValues.length == getType().getMaxLength(),
        "Wrong number of member values: %s",
        memberValues.length);
    for (int i = 0; i < memberValues.length; i++) {
      checkArgument(
          memberValues[i].getType().equals(type.getChildType(i)),
          "Wrong child type at index %s. Expected: %s, was %s",
          i,
          type.getChildType(i),
          memberValues[i].getType());
    }

    for (int i = 0; i < memberValues.length; i++) {
      backingNode = backingNode.set(type.treeWidth() + i, memberValues[i].getBackingNode());
    }
  }

  @Override
  public ContainerViewType<? extends AbstractImmutableContainer<C>> getType() {
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
    return type.getChildType(index).createFromBackingNode(node);
  }

  private void checkIndex(int index) {
    checkArgument(index >= 0 && index < type.getMaxLength(), "Index out of bounds: %s", index);
  }

  @Override
  public ViewWrite createWritableCopy() {
    throw new UnsupportedOperationException("This container doesn't support mutable View");
  }
}
