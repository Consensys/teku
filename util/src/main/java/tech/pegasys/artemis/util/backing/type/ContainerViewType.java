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

package tech.pegasys.artemis.util.backing.type;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import tech.pegasys.artemis.util.backing.ContainerViewWrite;
import tech.pegasys.artemis.util.backing.ViewType;
import tech.pegasys.artemis.util.backing.tree.TreeNode;
import tech.pegasys.artemis.util.backing.tree.TreeUtil;

public class ContainerViewType<C extends ContainerViewWrite> implements CompositeViewType {

  private final List<ViewType> childrenTypes;
  private final BiFunction<ContainerViewType<C>, TreeNode, C> instanceCtor;

  public ContainerViewType(
      List<ViewType> childrenTypes, BiFunction<ContainerViewType<C>, TreeNode, C> instanceCtor) {
    this.childrenTypes = childrenTypes;
    this.instanceCtor = instanceCtor;
  }

  @Override
  public C createDefault() {
    return createFromTreeNode(createDefaultTree());
  }

  @Override
  public TreeNode createDefaultTree() {
    List<TreeNode> defaultChildren = new ArrayList<>((int) getMaxLength());
    for (int i = 0; i < getMaxLength(); i++) {
      defaultChildren.add(getChildType(i).createDefault().getBackingNode());
    }
    return TreeUtil.createTree(defaultChildren);
  }

  @Override
  public ViewType getChildType(int index) {
    return childrenTypes.get(index);
  }

  @Override
  public C createFromTreeNode(TreeNode node) {
    return instanceCtor.apply(this, node);
  }

  @Override
  public long getMaxLength() {
    return childrenTypes.size();
  }

  @Override
  public int getBitsPerElement() {
    return 256;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ContainerViewType<?> that = (ContainerViewType<?>) o;
    return childrenTypes.equals(that.childrenTypes);
  }

  @Override
  public int hashCode() {
    return Objects.hash(childrenTypes);
  }
}
