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
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.ssz.backing.ContainerViewRead;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.tree.TreeUtil;
import tech.pegasys.teku.ssz.sos.SSZDeserializeException;
import tech.pegasys.teku.ssz.sos.SszLengthBounds;
import tech.pegasys.teku.ssz.sos.SszReader;
import tech.pegasys.teku.ssz.sos.SszWriter;

public abstract class ContainerViewType<C extends ContainerViewRead>
    implements CompositeViewType<C> {

  private final List<ViewType<?>> childrenTypes;
  private final TreeNode defaultTree;
  private final long treeWidth;

  protected ContainerViewType(List<ViewType<?>> childrenTypes) {
    this.childrenTypes = childrenTypes;
    this.defaultTree = createDefaultTree();
    this.treeWidth = CompositeViewType.super.treeWidth();
  }

  public static <C extends ContainerViewRead> ContainerViewType<C> create(
      List<ViewType<?>> childrenTypes, BiFunction<ContainerViewType<C>, TreeNode, C> instanceCtor) {
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
  public ViewType<?> getChildType(int index) {
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
      ViewType<?> childType = getChildType(i);
      size += childType.isFixedSize() ? childType.getFixedPartSize() : SSZ_LENGTH_SIZE;
    }
    return size;
  }

  @Override
  public int getVariablePartSize(TreeNode node) {
    int size = 0;
    for (int i = 0; i < getChildCount(); i++) {
      ViewType<?> childType = getChildType(i);
      if (!childType.isFixedSize()) {
        size += childType.getSszSize(node.get(getGeneralizedIndex(i)));
      }
    }
    return size;
  }

  public int getChildCount() {
    return (int) getMaxLength();
  }

  public List<ViewType<?>> getChildTypes() {
    return childrenTypes;
  }

  @Override
  public int sszSerialize(TreeNode node, SszWriter writer) {
    int variableChildOffset = getFixedPartSize();
    int[] variableSizes = new int[getChildCount()];
    for (int i = 0; i < getChildCount(); i++) {
      TreeNode childSubtree = node.get(getGeneralizedIndex(i));
      ViewType<?> childType = getChildType(i);
      if (childType.isFixedSize()) {
        int size = childType.sszSerialize(childSubtree, writer);
        assert size == childType.getFixedPartSize();
      } else {
        writer.write(SSZType.lengthToBytes(variableChildOffset));
        int childSize = childType.getSszSize(childSubtree);
        variableSizes[i] = childSize;
        variableChildOffset += childSize;
      }
    }
    for (int i = 0; i < getMaxLength(); i++) {
      ViewType<?> childType = getChildType(i);
      if (!childType.isFixedSize()) {
        TreeNode childSubtree = node.get(getGeneralizedIndex(i));
        int size = childType.sszSerialize(childSubtree, writer);
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
      ViewType<?> childType = getChildType(i);
      if (childType.isFixedSize()) {
        try (SszReader sszReader = reader.slice(childType.getFixedPartSize())) {
          TreeNode childNode = childType.sszDeserializeTree(sszReader);
          fixedChildrenSubtrees.add(childNode);
        }
      } else {
        int childOffset = SSZType.bytesToLength(reader.read(SSZ_LENGTH_SIZE));
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

    ArrayDeque<Integer> variableChildrenSizes = new ArrayDeque<>(
        variableChildrenOffsets.size() - 1);
    for (int i = 0; i < variableChildrenOffsets.size() - 1; i++) {
      variableChildrenSizes
          .add(variableChildrenOffsets.get(i + 1) - variableChildrenOffsets.get(i));
    }

    if (variableChildrenSizes.stream().anyMatch(s -> s < 0)) {
      throw new SSZDeserializeException("Invalid SSZ: wrong child offsets");
    }

    List<TreeNode> childrenSubtrees = new ArrayList<>(childCount);
    for (int i = 0; i < childCount; i++) {
      ViewType<?> childType = getChildType(i);
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
}
