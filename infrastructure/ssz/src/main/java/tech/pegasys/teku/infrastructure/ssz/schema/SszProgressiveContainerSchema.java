/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.infrastructure.ssz.schema;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Suppliers;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.SszContainer;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.impl.SszProgressiveContainerImpl;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszContainerSchema.NamedSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.json.SszPrimitiveTypeDefinitions;
import tech.pegasys.teku.infrastructure.ssz.sos.SszDeserializeException;
import tech.pegasys.teku.infrastructure.ssz.sos.SszLengthBounds;
import tech.pegasys.teku.infrastructure.ssz.sos.SszReader;
import tech.pegasys.teku.infrastructure.ssz.sos.SszWriter;
import tech.pegasys.teku.infrastructure.ssz.tree.BranchNode;
import tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil;
import tech.pegasys.teku.infrastructure.ssz.tree.LeafNode;
import tech.pegasys.teku.infrastructure.ssz.tree.ProgressiveTreeUtil;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNodeSource;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNodeStore;

/**
 * Schema for ProgressiveContainer (EIP-7495) — a heterogeneous container with stable merkleization
 * via an active_fields bitvector and progressive merkle tree.
 *
 * <p>Tree structure: BranchNode(progressiveDataTree, activeFieldsLeafNode)
 *
 * <p>The progressive data tree has slots for all positions in active_fields. Active positions
 * contain field backing nodes; inactive positions contain zero hash.
 *
 * <p>hash_tree_root = hash(merkleize_progressive(slot_chunks), pack_bits(active_fields))
 */
public class SszProgressiveContainerSchema<C extends SszContainer>
    implements SszContainerSchema<C> {

  private final String containerName;
  private final boolean[] activeFields;
  private final List<String> fieldNames;
  private final Object2IntMap<String> fieldNamesToIndex;
  private final List<SszSchema<?>> fieldSchemas;
  private final int[] fieldToTreePosition;
  private final LeafNode activeFieldsLeafNode;
  private final TreeNode defaultTree;
  private final Supplier<SszLengthBounds> sszLengthBounds =
      Suppliers.memoize(this::computeSszLengthBounds);

  /**
   * Creates a ProgressiveContainerSchema.
   *
   * @param containerName name of the container
   * @param activeFields bitvector indicating which slots are active (must not be empty, last
   *     element must be true)
   * @param fieldSchemas named schemas for ONLY the active fields, in order of their active_fields
   *     positions
   */
  @SafeVarargs
  public SszProgressiveContainerSchema(
      final String containerName,
      final boolean[] activeFields,
      final NamedSchema<? extends SszData>... fieldSchemas) {
    this(containerName, activeFields, List.of(fieldSchemas));
  }

  public SszProgressiveContainerSchema(
      final String containerName,
      final boolean[] activeFields,
      final List<? extends NamedSchema<? extends SszData>> fieldSchemas) {
    checkArgument(activeFields.length > 0, "activeFields must not be empty");
    checkArgument(
        activeFields[activeFields.length - 1], "Last element of activeFields must be true");
    checkArgument(activeFields.length <= 256, "activeFields length must be <= 256");

    int activeCount = 0;
    for (boolean b : activeFields) {
      if (b) {
        activeCount++;
      }
    }
    checkArgument(
        activeCount == fieldSchemas.size(),
        "Number of active fields (%s) must match number of field schemas (%s)",
        activeCount,
        fieldSchemas.size());

    this.containerName = containerName;
    this.activeFields = activeFields.clone();
    this.fieldNames = new ArrayList<>();
    this.fieldNamesToIndex = new Object2IntOpenHashMap<>();
    this.fieldSchemas = new ArrayList<>();
    this.fieldToTreePosition = new int[fieldSchemas.size()];

    int fieldIdx = 0;
    for (int slot = 0; slot < activeFields.length; slot++) {
      if (activeFields[slot]) {
        NamedSchema<? extends SszData> ns = fieldSchemas.get(fieldIdx);
        fieldNames.add(ns.getName());
        fieldNamesToIndex.put(ns.getName(), fieldIdx);
        this.fieldSchemas.add(ns.getSchema());
        fieldToTreePosition[fieldIdx] = slot;
        fieldIdx++;
      }
    }

    this.activeFieldsLeafNode = createActiveFieldsLeafNode(activeFields);
    this.defaultTree = createDefaultTree();
  }

  private static LeafNode createActiveFieldsLeafNode(final boolean[] activeFields) {
    int byteLen = (activeFields.length + 7) / 8;
    byte[] bytes = new byte[byteLen];
    for (int i = 0; i < activeFields.length; i++) {
      if (activeFields[i]) {
        bytes[i / 8] |= (byte) (1 << (i % 8));
      }
    }
    return LeafNode.create(Bytes.wrap(bytes));
  }

  private TreeNode createDefaultTree() {
    List<TreeNode> slotChunks = createSlotChunks(getDefaultFieldNodes());
    TreeNode progressiveTree = ProgressiveTreeUtil.createProgressiveTree(slotChunks);
    return BranchNode.create(progressiveTree, activeFieldsLeafNode);
  }

  private List<TreeNode> getDefaultFieldNodes() {
    List<TreeNode> nodes = new ArrayList<>();
    for (int i = 0; i < fieldSchemas.size(); i++) {
      nodes.add(fieldSchemas.get(i).getDefault().getBackingNode());
    }
    return nodes;
  }

  private List<TreeNode> createSlotChunks(final List<TreeNode> activeFieldNodes) {
    List<TreeNode> slotChunks = new ArrayList<>(activeFields.length);
    int fieldIdx = 0;
    for (boolean activeField : activeFields) {
      if (activeField) {
        slotChunks.add(activeFieldNodes.get(fieldIdx));
        fieldIdx++;
      } else {
        slotChunks.add(LeafNode.EMPTY_LEAF);
      }
    }
    return slotChunks;
  }

  public boolean[] getActiveFields() {
    return activeFields.clone();
  }

  public int getTreePosition(final int fieldIndex) {
    return fieldToTreePosition[fieldIndex];
  }

  // ===== SszContainerSchema =====

  @Override
  public int getFieldIndex(final String fieldName) {
    return fieldNamesToIndex.getOrDefault(fieldName, -1);
  }

  @Override
  public TreeNode createTreeFromFieldValues(final List<? extends SszData> fieldValues) {
    checkArgument(
        fieldValues.size() == getFieldsCount(),
        "Wrong number of field values: expected %s, got %s",
        getFieldsCount(),
        fieldValues.size());
    List<TreeNode> fieldNodes =
        fieldValues.stream()
            .map(SszData::getBackingNode)
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    List<TreeNode> slotChunks = createSlotChunks(fieldNodes);
    TreeNode progressiveTree = ProgressiveTreeUtil.createProgressiveTree(slotChunks);
    return BranchNode.create(progressiveTree, activeFieldsLeafNode);
  }

  @Override
  public String getContainerName() {
    return !containerName.isEmpty() ? containerName : getClass().getName();
  }

  @Override
  public List<String> getFieldNames() {
    return fieldNames;
  }

  @Override
  public List<? extends SszSchema<?>> getFieldSchemas() {
    return fieldSchemas;
  }

  // ===== SszCompositeSchema =====

  @Override
  public long getMaxLength() {
    return fieldSchemas.size();
  }

  @Override
  public SszSchema<?> getChildSchema(final int index) {
    return fieldSchemas.get(index);
  }

  @Override
  public int treeDepth() {
    throw new UnsupportedOperationException("Progressive containers don't have a fixed tree depth");
  }

  @Override
  public long treeWidth() {
    throw new UnsupportedOperationException("Progressive containers don't have a fixed tree width");
  }

  @Override
  public long maxChunks() {
    throw new UnsupportedOperationException(
        "Progressive containers don't have a fixed maxChunks");
  }

  @Override
  public long getChildGeneralizedIndex(final long fieldIndex) {
    int treePosition = fieldToTreePosition[(int) fieldIndex];
    long progressiveGIdx = ProgressiveTreeUtil.getElementGeneralizedIndex(treePosition);
    return GIndexUtil.gIdxCompose(GIndexUtil.LEFT_CHILD_G_INDEX, progressiveGIdx);
  }

  @Override
  public void storeChildNode(
      final TreeNodeStore nodeStore,
      final int maxBranchLevelsSkipped,
      final long gIndex,
      final TreeNode node) {
    throw new UnsupportedOperationException(
        "Store/load backing nodes not yet supported for progressive containers");
  }

  // ===== SszSchema =====

  @Override
  public TreeNode getDefaultTree() {
    return defaultTree;
  }

  @Override
  public C createFromBackingNode(final TreeNode node) {
    return createContainerFromBackingNode(node);
  }

  @SuppressWarnings("unchecked")
  private C createContainerFromBackingNode(final TreeNode node) {
    return (C) new SszProgressiveContainerImpl(this, node);
  }

  @Override
  public boolean isPrimitive() {
    return false;
  }

  @Override
  public boolean isFixedSize() {
    for (SszSchema<?> childSchema : fieldSchemas) {
      if (!childSchema.isFixedSize()) {
        return false;
      }
    }
    return true;
  }

  @Override
  public int getSszFixedPartSize() {
    int size = 0;
    for (SszSchema<?> childType : fieldSchemas) {
      size += childType.isFixedSize() ? childType.getSszFixedPartSize() : SszType.SSZ_LENGTH_SIZE;
    }
    return size;
  }

  @Override
  public int getSszVariablePartSize(final TreeNode node) {
    if (isFixedSize()) {
      return 0;
    }
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
  public int sszSerializeTree(final TreeNode node, final SszWriter writer) {
    // Same serialization as a regular container — only active fields
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
    for (int i = 0; i < getFieldsCount(); i++) {
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
    // Same deserialization as regular container — read active fields
    int endOffset = reader.getAvailableBytes();
    int childCount = getFieldsCount();
    Queue<TreeNode> fixedChildrenSubtrees = new ArrayDeque<>(childCount);
    IntList variableChildrenOffsets = new IntArrayList(childCount);
    for (int i = 0; i < childCount; i++) {
      SszSchema<?> childType = getChildSchema(i);
      if (childType.isFixedSize()) {
        try (SszReader sszReader = reader.slice(childType.getSszFixedPartSize())) {
          fixedChildrenSubtrees.add(childType.sszDeserializeTree(sszReader));
        }
      } else {
        int childOffset = SszType.sszBytesToLength(reader.read(SszType.SSZ_LENGTH_SIZE));
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

    // Build field nodes from deserialized data
    List<TreeNode> fieldNodes = new ArrayList<>(childCount);
    for (int i = 0; i < childCount; i++) {
      SszSchema<?> childType = getChildSchema(i);
      if (childType.isFixedSize()) {
        fieldNodes.add(fixedChildrenSubtrees.remove());
      } else {
        try (SszReader sszReader = reader.slice(variableChildrenSizes.remove())) {
          fieldNodes.add(childType.sszDeserializeTree(sszReader));
        }
      }
    }

    // Build slot chunks: active positions get field nodes, inactive get zero
    List<TreeNode> slotChunks = createSlotChunks(fieldNodes);
    TreeNode progressiveTree = ProgressiveTreeUtil.createProgressiveTree(slotChunks);
    return BranchNode.create(progressiveTree, activeFieldsLeafNode);
  }

  @Override
  public SszLengthBounds getSszLengthBounds() {
    return sszLengthBounds.get();
  }

  private SszLengthBounds computeSszLengthBounds() {
    return IntStream.range(0, getFieldsCount())
        .mapToObj(this::getChildSchema)
        .map(t -> t.getSszLengthBounds().addBytes(t.isFixedSize() ? 0 : SszType.SSZ_LENGTH_SIZE))
        .map(SszLengthBounds::ceilToBytes)
        .reduce(SszLengthBounds.ZERO, SszLengthBounds::add);
  }

  @Override
  public DeserializableTypeDefinition<C> getJsonTypeDefinition() {
    return SszPrimitiveTypeDefinitions.sszSerializedType(this, "SSZ hexadecimal");
  }

  @Override
  public Optional<String> getName() {
    return Optional.of(containerName);
  }

  @Override
  public void storeBackingNodes(
      final TreeNodeStore nodeStore,
      final int maxBranchLevelsSkipped,
      final long rootGIndex,
      final TreeNode node) {
    throw new UnsupportedOperationException(
        "Store/load backing nodes not yet supported for progressive containers");
  }

  @Override
  public TreeNode loadBackingNodes(
      final TreeNodeSource nodeSource, final Bytes32 rootHash, final long rootGIndex) {
    throw new UnsupportedOperationException(
        "Store/load backing nodes not yet supported for progressive containers");
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof SszProgressiveContainerSchema<?> that)) {
      return false;
    }
    return Arrays.equals(activeFields, that.activeFields) && fieldSchemas.equals(that.fieldSchemas);
  }

  @Override
  public int hashCode() {
    return Objects.hash(Arrays.hashCode(activeFields), fieldSchemas);
  }

  @Override
  public String toString() {
    return getContainerName();
  }
}
