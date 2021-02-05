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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import tech.pegasys.teku.ssz.backing.SszContainer;
import tech.pegasys.teku.ssz.backing.SszData;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.tree.TreeUtil;
import tech.pegasys.teku.ssz.sos.SSZDeserializeException;
import tech.pegasys.teku.ssz.sos.SszLengthBounds;
import tech.pegasys.teku.ssz.sos.SszReader;
import tech.pegasys.teku.ssz.sos.SszWriter;

public abstract class ContainerViewType<C extends SszContainer>
    implements CompositeViewType<C> {

  protected static class NamedType<T extends SszData> {
    private final String name;
    private final SszSchema<T> type;

    private NamedType(String name, SszSchema<T> type) {
      this.name = name;
      this.type = type;
    }

    public String getName() {
      return name;
    }

    public SszSchema<T> getType() {
      return type;
    }
  }

  protected static <T extends SszData> NamedType<T> namedType(String fieldName, SszSchema<T> type) {
    return new NamedType<>(fieldName, type);
  }

  private final String containerName;
  private final List<String> childrenNames;
  private final List<SszSchema<?>> childrenTypes;
  private final TreeNode defaultTree;
  private final long treeWidth;

  protected ContainerViewType(String name, List<NamedType<?>> childrenTypes) {
    this.containerName = name;
    this.childrenNames =
        childrenTypes.stream().map(NamedType::getName).collect(Collectors.toList());
    this.childrenTypes =
        childrenTypes.stream().map(NamedType::getType).collect(Collectors.toList());
    this.defaultTree = createDefaultTree();
    this.treeWidth = CompositeViewType.super.treeWidth();
  }

  protected ContainerViewType(List<SszSchema<?>> childrenTypes) {
    this.containerName = "";
    this.childrenNames =
        IntStream.range(0, childrenTypes.size())
            .mapToObj(i -> "field-" + i)
            .collect(Collectors.toList());
    this.childrenTypes = childrenTypes;
    this.defaultTree = createDefaultTree();
    this.treeWidth = CompositeViewType.super.treeWidth();
  }

  public static <C extends SszContainer> ContainerViewType<C> create(
      List<SszSchema<?>> childrenTypes, BiFunction<ContainerViewType<C>, TreeNode, C> instanceCtor) {
    return new ContainerViewType<C>(childrenTypes) {
      @Override
      public C createFromBackingNode(TreeNode node) {
        return instanceCtor.apply(this, node);
      }
    };
  }

  @Override
  public C getDefault() {
    return createFromBackingNode(getDefaultTree());
  }

  @Override
  public TreeNode getDefaultTree() {
    return defaultTree;
  }

  @Override
  public long treeWidth() {
    return treeWidth;
  }

  private TreeNode createDefaultTree() {
    List<TreeNode> defaultChildren = new ArrayList<>((int) getMaxLength());
    for (int i = 0; i < getChildCount(); i++) {
      defaultChildren.add(getChildType(i).getDefault().getBackingNode());
    }
    return TreeUtil.createTree(defaultChildren);
  }

  @Override
  public SszSchema<?> getChildType(int index) {
    return childrenTypes.get(index);
  }

  @Override
  public abstract C createFromBackingNode(TreeNode node);

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
    for (int i = 0; i < getChildCount(); i++) {
      if (!getChildType(i).isFixedSize()) {
        return false;
      }
    }
    return true;
  }

  @Override
  public int getFixedPartSize() {
    int size = 0;
    for (int i = 0; i < getChildCount(); i++) {
      SszSchema<?> childType = getChildType(i);
      size += childType.isFixedSize() ? childType.getFixedPartSize() : SSZ_LENGTH_SIZE;
    }
    return size;
  }

  @Override
  public int getVariablePartSize(TreeNode node) {
    int size = 0;
    for (int i = 0; i < getChildCount(); i++) {
      SszSchema<?> childType = getChildType(i);
      if (!childType.isFixedSize()) {
        size += childType.getSszSize(node.get(getGeneralizedIndex(i)));
      }
    }
    return size;
  }

  public int getChildCount() {
    return (int) getMaxLength();
  }

  public List<SszSchema<?>> getChildTypes() {
    return childrenTypes;
  }

  @Override
  public int sszSerializeTree(TreeNode node, SszWriter writer) {
    int variableChildOffset = getFixedPartSize();
    int[] variableSizes = new int[getChildCount()];
    for (int i = 0; i < getChildCount(); i++) {
      TreeNode childSubtree = node.get(getGeneralizedIndex(i));
      SszSchema<?> childType = getChildType(i);
      if (childType.isFixedSize()) {
        int size = childType.sszSerializeTree(childSubtree, writer);
        assert size == childType.getFixedPartSize();
      } else {
        writer.write(SszType.lengthToBytes(variableChildOffset));
        int childSize = childType.getSszSize(childSubtree);
        variableSizes[i] = childSize;
        variableChildOffset += childSize;
      }
    }
    for (int i = 0; i < getMaxLength(); i++) {
      SszSchema<?> childType = getChildType(i);
      if (!childType.isFixedSize()) {
        TreeNode childSubtree = node.get(getGeneralizedIndex(i));
        int size = childType.sszSerializeTree(childSubtree, writer);
        assert size == variableSizes[i];
      }
    }
    return variableChildOffset;
  }

  @Override
  public TreeNode sszDeserializeTree(SszReader reader) {
    int endOffset = reader.getAvailableBytes();
    int childCount = getChildCount();
    Queue<TreeNode> fixedChildrenSubtrees = new ArrayDeque<>(childCount);
    List<Integer> variableChildrenOffsets = new ArrayList<>(childCount);
    for (int i = 0; i < childCount; i++) {
      SszSchema<?> childType = getChildType(i);
      if (childType.isFixedSize()) {
        try (SszReader sszReader = reader.slice(childType.getFixedPartSize())) {
          TreeNode childNode = childType.sszDeserializeTree(sszReader);
          fixedChildrenSubtrees.add(childNode);
        }
      } else {
        int childOffset = SszType.bytesToLength(reader.read(SSZ_LENGTH_SIZE));
        variableChildrenOffsets.add(childOffset);
      }
    }

    if (variableChildrenOffsets.isEmpty()) {
      if (reader.getAvailableBytes() > 0) {
        throw new SSZDeserializeException("Invalid SSZ: unread bytes for fixed size container");
      }
    } else {
      if (variableChildrenOffsets.get(0) != endOffset - reader.getAvailableBytes()) {
        throw new SSZDeserializeException(
            "First variable element offset doesn't match the end of fixed part");
      }
    }

    variableChildrenOffsets.add(endOffset);

    ArrayDeque<Integer> variableChildrenSizes =
        new ArrayDeque<>(variableChildrenOffsets.size() - 1);
    for (int i = 0; i < variableChildrenOffsets.size() - 1; i++) {
      variableChildrenSizes.add(
          variableChildrenOffsets.get(i + 1) - variableChildrenOffsets.get(i));
    }

    if (variableChildrenSizes.stream().anyMatch(s -> s < 0)) {
      throw new SSZDeserializeException("Invalid SSZ: wrong child offsets");
    }

    List<TreeNode> childrenSubtrees = new ArrayList<>(childCount);
    for (int i = 0; i < childCount; i++) {
      SszSchema<?> childType = getChildType(i);
      if (childType.isFixedSize()) {
        childrenSubtrees.add(fixedChildrenSubtrees.remove());
      } else {
        try (SszReader sszReader = reader.slice(variableChildrenSizes.remove())) {
          TreeNode childNode = childType.sszDeserializeTree(sszReader);
          childrenSubtrees.add(childNode);
        }
      }
    }

    return TreeUtil.createTree(childrenSubtrees);
  }

  @Override
  public SszLengthBounds getSszLengthBounds() {
    return IntStream.range(0, getChildCount())
        .mapToObj(this::getChildType)
        // dynamic sized children need 4-byte offset
        .map(t -> t.getSszLengthBounds().addBytes((t.isFixedSize() ? 0 : SSZ_LENGTH_SIZE)))
        // elements are not packed in containers
        .map(SszLengthBounds::ceilToBytes)
        .reduce(SszLengthBounds.ZERO, SszLengthBounds::add);
  }

  public String getContainerName() {
    return containerName;
  }

  public List<String> getChildrenNames() {
    return childrenNames;
  }

  @Override
  public String toString() {
    return getContainerName().isEmpty() ? getClass().getName() : getContainerName();
  }
}
