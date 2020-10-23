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

package tech.pegasys.teku.ssz.backing.type;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.ssz.backing.ContainerViewRead;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.tree.TreeUtil;

public class ContainerViewType<C extends ContainerViewRead> implements CompositeViewType {

  private final List<ViewType> childrenTypes;
  private final BiFunction<ContainerViewType<C>, TreeNode, C> instanceCtor;
  private volatile TreeNode defaultTree;

  public ContainerViewType(
      List<ViewType> childrenTypes, BiFunction<ContainerViewType<C>, TreeNode, C> instanceCtor) {
    this.childrenTypes = childrenTypes;
    this.instanceCtor = instanceCtor;
  }

  @Override
  public C getDefault() {
    return createFromBackingNode(getDefaultTree());
  }

  @Override
  public TreeNode getDefaultTree() {
    if (defaultTree == null) {
      this.defaultTree = createDefaultTree();
    }
    return defaultTree;
  }

  private TreeNode createDefaultTree() {
    List<TreeNode> defaultChildren = new ArrayList<>((int) getMaxLength());
    for (int i = 0; i < getMaxLength(); i++) {
      defaultChildren.add(getChildType(i).getDefault().getBackingNode());
    }
    return TreeUtil.createTree(defaultChildren);
  }

  @Override
  public ViewType getChildType(int index) {
    return childrenTypes.get(index);
  }

  @Override
  public C createFromBackingNode(TreeNode node) {
    return instanceCtor.apply(this, node);
  }

  @Override
  public long getMaxLength() {
    return childrenTypes.size();
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

  @Override
  public boolean isFixedSize() {
    for (int i = 0; i < getMaxLength(); i++) {
      if (!getChildType(i).isFixedSize()) {
        return false;
      }
    }
    return true;
  }

  @Override
  public int getFixedPartSize() {
    int size = 0;
    for (int i = 0; i < getMaxLength(); i++) {
      ViewType childType = getChildType(i);
      size += childType.isFixedSize() ? childType.getFixedPartSize() : SSZ_LENGTH_SIZE;
    }
    return size;
  }

  @Override
  public int getVariablePartSize(TreeNode node) {
    int size = 0;
    for (int i = 0; i < getMaxLength(); i++) {
      ViewType childType = getChildType(i);
      if (!childType.isFixedSize()) {
        size += childType.getVariablePartSize(node.get(getGeneralizedIndex(i)));
      }
    }
    return size;
  }

  @Override
  public int sszSerialize(TreeNode node, Consumer<Bytes> writer) {
    int variableChildOffset = getFixedPartSize();
    for (int i = 0; i < getMaxLength(); i++) {
      TreeNode childSubtree = node.get(getGeneralizedIndex(i));
      ViewType childType = getChildType(i);
//      Bytes childSsz = childType.sszSerialize(childSubtree);
      if (childType.isFixedSize()) {
        childType.sszSerialize(childSubtree, writer);
      } else {
        writer.accept(SSZType.lengthToBytes(variableChildOffset));
        //        fixedParts.add(SSZType.lengthToBytes(variableChildOffset));
        //        variableParts.add(childSsz);
        variableChildOffset += childType.getSszSize(childSubtree);
      }
    }
    for (int i = 0; i < getMaxLength(); i++) {
      ViewType childType = getChildType(i);
      if (!childType.isFixedSize()) {
        TreeNode childSubtree = node.get(getGeneralizedIndex(i));
        childType.sszSerialize(childSubtree, writer);
      }
    }
    return variableChildOffset;
  }

//  @Override
//  public Bytes sszSerialize(TreeNode node) {
//    List<Bytes> fixedParts = new ArrayList<>();
//    List<Bytes> variableParts = new ArrayList<>();
//    int variableChildOffset = getFixedPartSize();
//    for (int i = 0; i < getMaxLength(); i++) {
//      TreeNode childSubtree = node.get(getGeneralizedIndex(i));
//      ViewType childType = getChildType(i);
//      Bytes childSsz = childType.sszSerialize(childSubtree);
//      if (childType.isFixedSize()) {
//        fixedParts.add(childSsz);
//      } else {
//        fixedParts.add(SSZType.lengthToBytes(variableChildOffset));
//        variableParts.add(childSsz);
//        variableChildOffset += childSsz.size();
//      }
//    }
//    return Bytes.wrap(
//        Bytes.wrap(fixedParts.toArray(new Bytes[0])),
//        Bytes.wrap(variableParts.toArray(new Bytes[0])));
//  }

  @Override
  public TreeNode sszDeserialize(Bytes ssz) {
    throw new UnsupportedOperationException("TODO");
  }
}
