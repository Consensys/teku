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

import tech.pegasys.artemis.util.backing.ContainerView;
import tech.pegasys.artemis.util.backing.View;
import tech.pegasys.artemis.util.backing.tree.TreeNode;
import tech.pegasys.artemis.util.backing.type.ContainerViewType;

public class ContainerViewImpl implements ContainerView {
  private final ContainerViewType type;
  private TreeNode backingNode;

  public ContainerViewImpl(ContainerViewType type, TreeNode backingNode) {
    this.type = type;
    this.backingNode = backingNode;
  }

  @Override
  public ContainerViewType getType() {
    return type;
  }

  @Override
  public TreeNode getBackingNode() {
    return backingNode;
  }

  @Override
  public View get(int index) {
    checkIndex(index);
    TreeNode node = backingNode.get(type.treeWidth() + index);
    return type.getChildType(index).createFromTreeNode(node);
  }

  @Override
  public void set(int index, View child) {
    checkIndex(index);
    checkArgument(
        child.getType().equals(type.getChildType(index)),
        "Wrong child type at index %s. Expected: %s, was %s",
        index,
        type.getChildType(index),
        child.getType());
    backingNode = backingNode.set(type.treeWidth() + index, child.getBackingNode());
  }

  private void checkIndex(int index) {
    checkArgument(index >= 0 && index < type.getMaxLength(), "Index out of bounds: %s", index);
  }
}
