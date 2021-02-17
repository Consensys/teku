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

package tech.pegasys.teku.ssz.backing.schema;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import tech.pegasys.teku.ssz.backing.SszContainer;
import tech.pegasys.teku.ssz.backing.SszData;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.tree.TreeUtil;
import tech.pegasys.teku.ssz.sos.SszDeserializeException;
import tech.pegasys.teku.ssz.sos.SszLengthBounds;
import tech.pegasys.teku.ssz.sos.SszReader;
import tech.pegasys.teku.ssz.sos.SszWriter;

public abstract class AbstractSszContainerSchema<C extends SszContainer>
    implements SszContainerSchema<C> {

  protected static class NamedSchema<T extends SszData> {
    private final String name;
    private final SszSchema<T> schema;

    private NamedSchema(String name, SszSchema<T> schema) {
      this.name = name;
      this.schema = schema;
    }

    public String getName() {
      return name;
    }

    public SszSchema<T> getSchema() {
      return schema;
    }
  }

  protected static <T extends SszData> NamedSchema<T> namedSchema(
      String fieldName, SszSchema<T> schema) {
    return new NamedSchema<>(fieldName, schema);
  }

  private final String containerName;
  private final List<String> childrenNames;
  private final List<SszSchema<?>> childrenSchemas;
  private final TreeNode defaultTree;
  private final long treeWidth;

  protected AbstractSszContainerSchema(String name, List<NamedSchema<?>> childrenSchemas) {
    this.containerName = name;
    this.childrenNames =
        childrenSchemas.stream().map(NamedSchema::getName).collect(Collectors.toList());
    this.childrenSchemas =
        childrenSchemas.stream().map(NamedSchema::getSchema).collect(Collectors.toList());
    this.defaultTree = createDefaultTree();
    this.treeWidth = SszContainerSchema.super.treeWidth();
  }

  protected AbstractSszContainerSchema(List<SszSchema<?>> childrenSchemas) {
    this.containerName = "";
    this.childrenNames =
        IntStream.range(0, childrenSchemas.size())
            .mapToObj(i -> "field-" + i)
            .collect(Collectors.toList());
    this.childrenSchemas = childrenSchemas;
    this.defaultTree = createDefaultTree();
    this.treeWidth = SszContainerSchema.super.treeWidth();
  }

  @Override
  public TreeNode createTreeFromFieldValues(List<? extends SszData> fieldValues) {
    checkArgument(fieldValues.size() == getFieldsCount(), "Wrong number of filed values");
    return TreeUtil.createTree(
        fieldValues.stream().map(SszData::getBackingNode).collect(Collectors.toList()));
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
    for (int i = 0; i < getFieldsCount(); i++) {
      defaultChildren.add(getChildSchema(i).getDefault().getBackingNode());
    }
    return TreeUtil.createTree(defaultChildren);
  }

  @Override
  public SszSchema<?> getChildSchema(int index) {
    return childrenSchemas.get(index);
  }

  @Override
  public abstract C createFromBackingNode(TreeNode node);

  @Override
  public long getMaxLength() {
    return childrenSchemas.size();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    AbstractSszContainerSchema<?> that = (AbstractSszContainerSchema<?>) o;
    return childrenSchemas.equals(that.childrenSchemas);
  }

  @Override
  public int hashCode() {
    return Objects.hash(childrenSchemas);
  }

  @Override
  public boolean isFixedSize() {
    for (int i = 0; i < getFieldsCount(); i++) {
      if (!getChildSchema(i).isFixedSize()) {
        return false;
      }
    }
    return true;
  }

  @Override
  public int getFixedPartSize() {
    int size = 0;
    for (int i = 0; i < getFieldsCount(); i++) {
      SszSchema<?> childType = getChildSchema(i);
      size += childType.isFixedSize() ? childType.getFixedPartSize() : SSZ_LENGTH_SIZE;
    }
    return size;
  }

  @Override
  public int getVariablePartSize(TreeNode node) {
    int size = 0;
    for (int i = 0; i < getFieldsCount(); i++) {
      SszSchema<?> childType = getChildSchema(i);
      if (!childType.isFixedSize()) {
        size += childType.getSszSize(node.get(getChildGeneralizedIndex(i)));
      }
    }
    return size;
  }

  @Override
  public List<SszSchema<?>> getFieldSchemas() {
    return childrenSchemas;
  }

  @Override
  public int sszSerializeTree(TreeNode node, SszWriter writer) {
    int variableChildOffset = getFixedPartSize();
    int[] variableSizes = new int[getFieldsCount()];
    for (int i = 0; i < getFieldsCount(); i++) {
      TreeNode childSubtree = node.get(getChildGeneralizedIndex(i));
      SszSchema<?> childType = getChildSchema(i);
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
      SszSchema<?> childType = getChildSchema(i);
      if (!childType.isFixedSize()) {
        TreeNode childSubtree = node.get(getChildGeneralizedIndex(i));
        int size = childType.sszSerializeTree(childSubtree, writer);
        assert size == variableSizes[i];
      }
    }
    return variableChildOffset;
  }

  @Override
  public TreeNode sszDeserializeTree(SszReader reader) {
    int endOffset = reader.getAvailableBytes();
    int childCount = getFieldsCount();
    Queue<TreeNode> fixedChildrenSubtrees = new ArrayDeque<>(childCount);
    List<Integer> variableChildrenOffsets = new ArrayList<>(childCount);
    for (int i = 0; i < childCount; i++) {
      SszSchema<?> childType = getChildSchema(i);
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
        throw new SszDeserializeException("Invalid SSZ: unread bytes for fixed size container");
      }
    } else {
      if (variableChildrenOffsets.get(0) != endOffset - reader.getAvailableBytes()) {
        throw new SszDeserializeException(
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
      throw new SszDeserializeException("Invalid SSZ: wrong child offsets");
    }

    List<TreeNode> childrenSubtrees = new ArrayList<>(childCount);
    for (int i = 0; i < childCount; i++) {
      SszSchema<?> childType = getChildSchema(i);
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
    return IntStream.range(0, getFieldsCount())
        .mapToObj(this::getChildSchema)
        // dynamic sized children need 4-byte offset
        .map(t -> t.getSszLengthBounds().addBytes((t.isFixedSize() ? 0 : SSZ_LENGTH_SIZE)))
        // elements are not packed in containers
        .map(SszLengthBounds::ceilToBytes)
        .reduce(SszLengthBounds.ZERO, SszLengthBounds::add);
  }

  @Override
  public String getContainerName() {
    return !containerName.isEmpty() ? containerName : getClass().getName();
  }

  @Override
  public List<String> getFieldNames() {
    return childrenNames;
  }

  @Override
  public String toString() {
    return getContainerName();
  }
}
