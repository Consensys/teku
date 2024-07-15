/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.infrastructure.ssz.schema.impl;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Suppliers;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.SszContainer;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.schema.SszContainerSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszFieldName;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszType;
import tech.pegasys.teku.infrastructure.ssz.schema.json.SszContainerTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.sos.SszDeserializeException;
import tech.pegasys.teku.infrastructure.ssz.sos.SszLengthBounds;
import tech.pegasys.teku.infrastructure.ssz.sos.SszReader;
import tech.pegasys.teku.infrastructure.ssz.sos.SszWriter;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeUtil;

public abstract class AbstractSszContainerSchema<C extends SszContainer>
    implements SszContainerSchema<C> {

  public static class NamedSchema<T extends SszData> {
    private final String name;
    private final SszSchema<T> schema;

    public static <T extends SszData> NamedSchema<T> of(
        final String name, final SszSchema<T> schema) {
      return new NamedSchema<>(name, schema);
    }

    protected NamedSchema(final String name, final SszSchema<T> schema) {
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

  public static <T extends SszData> NamedSchema<T> namedSchema(
      final SszFieldName fieldName, final SszSchema<T> schema) {
    return namedSchema(fieldName.getSszFieldName(), schema);
  }

  public static <T extends SszData> NamedSchema<T> namedSchema(
      final String fieldName, final SszSchema<T> schema) {
    return new NamedSchema<>(fieldName, schema);
  }

  private final Supplier<SszLengthBounds> sszLengthBounds =
      Suppliers.memoize(this::computeSszLengthBounds);
  private final String containerName;
  private final List<String> childrenNames = new ArrayList<>();
  private final Object2IntMap<String> childrenNamesToFieldIndex = new Object2IntOpenHashMap<>();
  private final List<? extends SszSchema<?>> childrenSchemas;
  private final TreeNode defaultTree;
  private final long treeWidth;
  private final int fixedPartSize;
  private final DeserializableTypeDefinition<C> jsonTypeDefinition;

  protected AbstractSszContainerSchema(
      final String name, final List<? extends NamedSchema<?>> childrenSchemas) {
    this.containerName = name;
    for (int i = 0; i < childrenSchemas.size(); i++) {
      final NamedSchema<?> childSchema = childrenSchemas.get(i);
      if (childrenNamesToFieldIndex.containsKey(childSchema.getName())) {
        throw new IllegalArgumentException(
            "Duplicate field name detected for field " + childSchema.getName() + " at index " + i);
      }
      childrenNamesToFieldIndex.put(childSchema.getName(), i);
      childrenNames.add(childSchema.getName());
    }
    this.childrenSchemas = childrenSchemas.stream().map(NamedSchema::getSchema).toList();
    this.defaultTree = createDefaultTree();
    this.treeWidth = SszContainerSchema.super.treeWidth();
    this.fixedPartSize = calcSszFixedPartSize();
    this.jsonTypeDefinition = SszContainerTypeDefinition.createFor(this);
  }

  protected AbstractSszContainerSchema(final List<SszSchema<?>> childrenSchemas) {
    this.containerName = "";
    for (int i = 0; i < childrenSchemas.size(); i++) {
      final String name = "field-" + i;
      childrenNamesToFieldIndex.put(name, i);
      childrenNames.add(name);
    }
    this.childrenSchemas = childrenSchemas;
    this.defaultTree = createDefaultTree();
    this.treeWidth = SszContainerSchema.super.treeWidth();
    this.fixedPartSize = calcSszFixedPartSize();
    this.jsonTypeDefinition = SszContainerTypeDefinition.createFor(this);
  }

  @Override
  public TreeNode createTreeFromFieldValues(final List<? extends SszData> fieldValues) {
    checkArgument(fieldValues.size() == getFieldsCount(), "Wrong number of filed values");
    return TreeUtil.createTree(fieldValues.stream().map(SszData::getBackingNode).toList());
  }

  @Override
  public C getDefault() {
    return createFromBackingNode(getDefaultTree());
  }

  @Override
  public DeserializableTypeDefinition<C> getJsonTypeDefinition() {
    return jsonTypeDefinition;
  }

  @Override
  public Optional<String> getName() {
    return Optional.of(containerName);
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
  public SszSchema<?> getChildSchema(final int index) {
    return childrenSchemas.get(index);
  }

  /**
   * Get the index of a field by name
   *
   * @param fieldName the name of the field
   * @return The index if it exists, otherwise -1
   */
  @Override
  public int getFieldIndex(final String fieldName) {
    return childrenNamesToFieldIndex.getOrDefault(fieldName, -1);
  }

  @Override
  public abstract C createFromBackingNode(TreeNode node);

  @Override
  public long getMaxLength() {
    return childrenSchemas.size();
  }

  @Override
  public boolean equals(final Object o) {
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
    // TODO, can we cache this?
    for (int i = 0; i < getFieldsCount(); i++) {
      if (!getChildSchema(i).isFixedSize()) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean hasExtraDataInBackingTree() {
    // TODO, can we cache this?
    for (int i = 0; i < getFieldsCount(); i++) {
      if (getChildSchema(i).hasExtraDataInBackingTree()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public int getSszFixedPartSize() {
    return fixedPartSize;
  }

  protected int calcSszFixedPartSize() {
    int size = 0;
    for (int i = 0; i < getFieldsCount(); i++) {
      SszSchema<?> childType = getChildSchema(i);
      size += childType.isFixedSize() ? childType.getSszFixedPartSize() : SSZ_LENGTH_SIZE;
    }
    return size;
  }

  @Override
  public int getSszVariablePartSize(final TreeNode node) {
    if (isFixedSize()) {
      return 0;
    } else {
      int size = 0;
      for (int i = 0; i < getFieldsCount(); i++) {
        SszSchema<?> childType = getChildSchema(i);
        if (!childType.isFixedSize()) {
          size += childType.getSszSize(node.get(getChildGeneralizedIndex(i)));
        }
      }
      return size;
    }
  }

  @Override
  public List<? extends SszSchema<?>> getFieldSchemas() {
    return childrenSchemas;
  }

  @Override
  public int sszSerializeTree(final TreeNode node, final SszWriter writer) {
    int variableChildOffset = getSszFixedPartSize();
    int[] variableSizes = new int[getFieldsCount()];
    for (int i = 0; i < getFieldsCount(); i++) {
      TreeNode childSubtree = node.get(getChildGeneralizedIndex(i));
      SszSchema<?> childType = getChildSchema(i);
      if (childType.isFixedSize()) {
        int size = childType.sszSerializeTree(childSubtree, writer);
        assert size == childType.getSszFixedPartSize();
      } else {
        writer.write(SszType.sszLengthToBytes(variableChildOffset));
        int childSize = childType.getSszSize(childSubtree);
        variableSizes[i] = childSize;
        variableChildOffset += childSize;
      }
    }
    for (int i = 0; i < childrenSchemas.size(); i++) {
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
  public TreeNode sszDeserializeTree(final SszReader reader) {
    int endOffset = reader.getAvailableBytes();
    int childCount = getFieldsCount();
    Queue<TreeNode> fixedChildrenSubtrees = new ArrayDeque<>(childCount);
    IntList variableChildrenOffsets = new IntArrayList(childCount);
    for (int i = 0; i < childCount; i++) {
      SszSchema<?> childType = getChildSchema(i);
      if (childType.isFixedSize()) {
        try (SszReader sszReader = reader.slice(childType.getSszFixedPartSize())) {
          TreeNode childNode = childType.sszDeserializeTree(sszReader);
          fixedChildrenSubtrees.add(childNode);
        }
      } else {
        int childOffset = SszType.sszBytesToLength(reader.read(SSZ_LENGTH_SIZE));
        variableChildrenOffsets.add(childOffset);
      }
    }

    if (variableChildrenOffsets.isEmpty()) {
      if (reader.getAvailableBytes() > 0) {
        throw new SszDeserializeException("Invalid SSZ: unread bytes for fixed size container");
      }
    } else {
      if (variableChildrenOffsets.getInt(0) != endOffset - reader.getAvailableBytes()) {
        throw new SszDeserializeException(
            "First variable element offset doesn't match the end of fixed part");
      }
    }

    variableChildrenOffsets.add(endOffset);

    ArrayDeque<Integer> variableChildrenSizes =
        new ArrayDeque<>(variableChildrenOffsets.size() - 1);
    for (int i = 0; i < variableChildrenOffsets.size() - 1; i++) {
      variableChildrenSizes.add(
          variableChildrenOffsets.getInt(i + 1) - variableChildrenOffsets.getInt(i));
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
    return sszLengthBounds.get();
  }

  private SszLengthBounds computeSszLengthBounds() {
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
