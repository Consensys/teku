/*
 * Copyright Consensys Software Inc., 2024
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
import static com.google.common.base.Preconditions.checkState;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PrimitiveIterator.OfInt;
import java.util.Queue;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszStableContainer;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszNone;
import tech.pegasys.teku.infrastructure.ssz.schema.SszFieldName;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszStableContainerSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszType;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.json.SszStableContainerTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.sos.SszDeserializeException;
import tech.pegasys.teku.infrastructure.ssz.sos.SszLengthBounds;
import tech.pegasys.teku.infrastructure.ssz.sos.SszReader;
import tech.pegasys.teku.infrastructure.ssz.sos.SszWriter;
import tech.pegasys.teku.infrastructure.ssz.tree.BranchNode;
import tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNodeSource;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNodeSource.CompressedBranchInfo;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNodeStore;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeUtil;

public abstract class AbstractSszStableContainerSchema<C extends SszStableContainer>
    extends AbstractSszContainerSchema<C> implements SszStableContainerSchema<C> {
  public static final long CONTAINER_G_INDEX = GIndexUtil.LEFT_CHILD_G_INDEX;
  public static final long BITVECTOR_G_INDEX = GIndexUtil.RIGHT_CHILD_G_INDEX;

  private final List<? extends NamedIndexedSchema<?>> definedChildrenSchemas;
  private final SszBitvectorSchema<SszBitvector> activeFieldsSchema;
  private final SszBitvector defaultActiveFields;
  private final TreeNode defaultTreeNode;
  private final DeserializableTypeDefinition<C> jsonTypeDefinition;

  private static long getContainerGIndex(final long rootGIndex) {
    return GIndexUtil.gIdxLeftGIndex(rootGIndex);
  }

  private static long getBitvectorGIndex(final long rootGIndex) {
    return GIndexUtil.gIdxRightGIndex(rootGIndex);
  }

  public static List<? extends NamedIndexedSchema<?>> continuousActiveNamedSchemas(
      final List<? extends NamedSchema<?>> namedSchemas) {
    return IntStream.range(0, namedSchemas.size())
        .mapToObj(index -> NamedIndexedSchema.of(index, namedSchemas.get(index)))
        .toList();
  }

  public static List<? extends NamedIndexedSchema<?>> continuousActiveSchemas(
      final List<SszSchema<?>> schemas) {
    return IntStream.range(0, schemas.size())
        .mapToObj(index -> new NamedIndexedSchema<>("field-" + index, index, schemas.get(index)))
        .toList();
  }

  public static class NamedIndexedSchema<T extends SszData> extends NamedSchema<T> {
    private final int index;

    protected NamedIndexedSchema(final String name, final int index, final SszSchema<T> schema) {
      super(name, schema);
      this.index = index;
    }

    protected static <T extends SszData> NamedIndexedSchema<T> of(
        final int index, final NamedSchema<T> schema) {
      return new NamedIndexedSchema<>(schema.getName(), index, schema.getSchema());
    }

    public int getIndex() {
      return index;
    }
  }

  public static <T extends SszData> NamedIndexedSchema<T> namedIndexedSchema(
      final SszFieldName fieldName, final int index, final SszSchema<T> schema) {
    return namedIndexedSchema(fieldName.getSszFieldName(), index, schema);
  }

  public static <T extends SszData> NamedIndexedSchema<T> namedIndexedSchema(
      final String fieldName, final int index, final SszSchema<T> schema) {
    return new NamedIndexedSchema<>(fieldName, index, schema);
  }

  public AbstractSszStableContainerSchema(
      final String name,
      final List<? extends NamedIndexedSchema<?>> definedChildrenSchemas,
      final int maxFieldCount) {
    super(name, createAllSchemas(definedChildrenSchemas, maxFieldCount));
    this.definedChildrenSchemas = definedChildrenSchemas;
    this.activeFieldsSchema = SszBitvectorSchema.create(maxFieldCount);
    this.defaultActiveFields = activeFieldsSchema.ofBits();
    this.defaultTreeNode =
        BranchNode.create(createDefaultContainerTreeNode(), defaultActiveFields.getBackingNode());
    this.jsonTypeDefinition = SszStableContainerTypeDefinition.createFor(this);
  }

  protected TreeNode createDefaultContainerTreeNode() {
    final List<TreeNode> defaultChildren = new ArrayList<>((int) getMaxLength());
    for (int i = 0; i < getFieldsCount(); i++) {
      defaultChildren.add(getChildSchema(i).getDefault().getBackingNode());
    }
    return TreeUtil.createTree(defaultChildren);
  }

  @Override
  public DeserializableTypeDefinition<C> getJsonTypeDefinition() {
    return jsonTypeDefinition;
  }

  @Override
  public List<? extends NamedIndexedSchema<?>> getDefinedChildrenSchemas() {
    return definedChildrenSchemas;
  }

  @Override
  public SszBitvector getActiveFieldsBitvectorFromBackingNode(final TreeNode node) {
    return activeFieldsSchema.createFromBackingNode(node.get(BITVECTOR_G_INDEX));
  }

  @Override
  public int getMaxFieldCount() {
    return activeFieldsSchema.getLength();
  }

  @Override
  public TreeNode getDefaultTree() {
    return defaultTreeNode;
  }

  @Override
  public TreeNode createTreeFromFieldValues(final List<? extends SszData> fieldValues) {
    throw new UnsupportedOperationException();
  }

  @Override
  public TreeNode createTreeFromOptionalFieldValues(
      final List<Optional<? extends SszData>> fieldValues) {
    final int definedFieldsCount = getFieldsCount();
    checkArgument(fieldValues.size() < definedFieldsCount, "Wrong number of filed values");

    final List<SszData> allFields = new ArrayList<>(definedFieldsCount);

    final IntList activeFieldIndices = new IntArrayList();

    for (int index = 0, fieldIndex = 0; index < definedFieldsCount; index++) {
      final Optional<? extends SszData> currentOptionalField;
      if (fieldIndex >= fieldValues.size()) {
        currentOptionalField = Optional.empty();
      } else {
        currentOptionalField = fieldValues.get(fieldIndex++);
      }

      if (currentOptionalField.isPresent()) {
        activeFieldIndices.add(index);
        allFields.add(currentOptionalField.get());
      } else {
        allFields.add(SszNone.INSTANCE);
      }
    }

    assert allFields.size() == definedFieldsCount;

    final TreeNode activeFieldsTree =
        activeFieldsSchema.ofBits(activeFieldIndices).getBackingNode();
    final TreeNode containerTree =
        TreeUtil.createTree(allFields.stream().map(SszData::getBackingNode).toList());

    return BranchNode.create(containerTree, activeFieldsTree);
  }

  @Override
  public SszBitvector getDefaultActiveFields() {
    return defaultActiveFields;
  }

  @Override
  public boolean isFixedSize() {
    return false;
  }

  @Override
  public int getSszFixedPartSize() {
    return 0;
  }

  @Override
  public int getSszVariablePartSize(final TreeNode node) {
    final SszBitvector activeFields = getActiveFieldsBitvectorFromBackingNode(node);
    return activeFields
            .streamAllSetBits()
            .map(
                activeFieldIndex ->
                    getChildSchema(activeFieldIndex)
                        .getSszSize(node.get(getChildGeneralizedIndex(activeFieldIndex))))
            .sum()
        + activeFieldsSchema.getSszSize(activeFields.getBackingNode());
  }

  @Override
  public int sszSerializeTree(final TreeNode node, final SszWriter writer) {
    final SszBitvector activeFieldsBitvector = getActiveFieldsBitvectorFromBackingNode(node);
    final int activeFieldsWroteBytes =
        activeFieldsSchema.sszSerializeTree(activeFieldsBitvector.getBackingNode(), writer);

    final TreeNode containerTree = node.get(CONTAINER_G_INDEX);

    int variableChildOffset = calcSszFixedPartSize(activeFieldsBitvector);

    final int[] variableSizes = new int[activeFieldsBitvector.size()];
    for (final OfInt activeIndicesIterator = activeFieldsBitvector.streamAllSetBits().iterator();
        activeIndicesIterator.hasNext(); ) {
      final int activeFieldIndex = activeIndicesIterator.next();

      final TreeNode childSubtree =
          containerTree.get(getContainerChildGeneralizedIndex(activeFieldIndex));
      final SszSchema<?> childType = getChildSchema(activeFieldIndex);
      if (childType.isFixedSize()) {
        int size = childType.sszSerializeTree(childSubtree, writer);
        assert size == childType.getSszFixedPartSize();
      } else {
        writer.write(SszType.sszLengthToBytes(variableChildOffset));
        int childSize = childType.getSszSize(childSubtree);
        variableSizes[activeFieldIndex] = childSize;
        variableChildOffset += childSize;
      }
    }

    activeFieldsBitvector
        .streamAllSetBits()
        .forEach(
            activeFieldIndex -> {
              SszSchema<?> childType = getChildSchema(activeFieldIndex);
              if (!childType.isFixedSize()) {
                final TreeNode childSubtree =
                    containerTree.get(getContainerChildGeneralizedIndex(activeFieldIndex));
                int size = childType.sszSerializeTree(childSubtree, writer);
                assert size == variableSizes[activeFieldIndex];
              }
            });

    return activeFieldsWroteBytes + variableChildOffset;
  }

  protected int calcSszFixedPartSize(final SszBitvector activeFieldBitvector) {
    return activeFieldBitvector
        .streamAllSetBits()
        .mapToObj(this::getChildSchema)
        .mapToInt(
            childType ->
                childType.isFixedSize() ? childType.getSszFixedPartSize() : SSZ_LENGTH_SIZE)
        .sum();
  }

  @Override
  public TreeNode sszDeserializeTree(final SszReader reader) {
    final SszReader activeFieldsReader = reader.slice(activeFieldsSchema.getSszFixedPartSize());
    final SszBitvector activeFields = activeFieldsSchema.sszDeserialize(activeFieldsReader);

    return BranchNode.create(
        deserializeContainer(reader, activeFields), activeFields.getBackingNode());
  }

  private TreeNode deserializeContainer(final SszReader reader, final SszBitvector activeFields) {
    int endOffset = reader.getAvailableBytes();
    int childCount = getFieldsCount();
    Queue<TreeNode> fixedChildrenSubtrees = new ArrayDeque<>(childCount);
    IntList variableChildrenOffsets = new IntArrayList(childCount);
    for (int i = 0; i < childCount; i++) {
      if (!activeFields.getBit(i)) {
        fixedChildrenSubtrees.add(SszNone.INSTANCE.getBackingNode());
        continue;
      }
      final SszSchema<?> childType = getChildSchema(i);
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
      final SszSchema<?> childType = getChildSchema(i);
      if (childType.isFixedSize() || !activeFields.getBit(i)) {
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
  public SszBitvectorSchema<SszBitvector> getActiveFieldsSchema() {
    return activeFieldsSchema;
  }

  @Override
  public long getChildGeneralizedIndex(final long elementIndex) {
    return GIndexUtil.gIdxCompose(
        CONTAINER_G_INDEX, getContainerChildGeneralizedIndex(elementIndex));
  }

  private long getContainerChildGeneralizedIndex(final long elementIndex) {
    return super.getChildGeneralizedIndex(elementIndex);
  }

  @Override
  public SszLengthBounds getSszLengthBounds() {
    return super.getSszLengthBounds().add(activeFieldsSchema.getSszLengthBounds());
  }

  public SszLengthBounds getSszLengthBoundsAsProfile() {
    return super.getSszLengthBounds();
  }

  public int getSszSizeAsProfile(final TreeNode node) {
    return super.getSszSize(node);
  }

  @Override
  public void storeBackingNodes(
      final TreeNodeStore nodeStore,
      final int maxBranchLevelsSkipped,
      final long rootGIndex,
      final TreeNode node) {
    final TreeNode containerSubtree = node.get(CONTAINER_G_INDEX);
    final TreeNode activeFieldsBitvectorSubtree = node.get(BITVECTOR_G_INDEX);

    super.storeBackingNodes(
        nodeStore, maxBranchLevelsSkipped, getContainerGIndex(rootGIndex), containerSubtree);

    activeFieldsSchema.storeBackingNodes(
        nodeStore,
        maxBranchLevelsSkipped,
        getBitvectorGIndex(rootGIndex),
        activeFieldsBitvectorSubtree);

    nodeStore.storeBranchNode(
        node.hashTreeRoot(),
        rootGIndex,
        2,
        new Bytes32[] {
          containerSubtree.hashTreeRoot(), activeFieldsBitvectorSubtree.hashTreeRoot()
        });
  }

  @Override
  public TreeNode loadBackingNodes(
      final TreeNodeSource nodeSource, final Bytes32 rootHash, final long rootGIndex) {
    if (TreeUtil.ZERO_TREES_BY_ROOT.containsKey(rootHash) || rootHash.equals(Bytes32.ZERO)) {
      return getDefaultTree();
    }
    final CompressedBranchInfo branchData = nodeSource.loadBranchNode(rootHash, rootGIndex);
    checkState(
        branchData.getChildren().length == 2,
        "Stable container root node must have exactly 2 children");
    checkState(branchData.getDepth() == 1, "Stable container root node must have depth of 1");
    final Bytes32 containerHash = branchData.getChildren()[0];
    final Bytes32 activeFieldsBitvectorHash = branchData.getChildren()[1];

    final long lastUsefulGIndex =
        GIndexUtil.gIdxChildGIndex(rootGIndex, maxChunks() - 1, treeDepth());
    final TreeNode containerTreeNode =
        LoadingUtil.loadNodesToDepth(
            nodeSource,
            containerHash,
            getContainerGIndex(rootGIndex),
            treeDepth(),
            defaultTreeNode.get(CONTAINER_G_INDEX),
            lastUsefulGIndex,
            this::loadChildNode);

    return BranchNode.create(
        containerTreeNode,
        activeFieldsSchema.loadBackingNodes(
            nodeSource, activeFieldsBitvectorHash, getBitvectorGIndex(rootGIndex)));
  }

  private TreeNode loadChildNode(
      final TreeNodeSource nodeSource, final Bytes32 childHash, final long childGIndex) {
    final int childIndex = GIndexUtil.gIdxChildIndexFromGIndex(childGIndex, treeDepth());
    return getChildSchema(childIndex).loadBackingNodes(nodeSource, childHash, childGIndex);
  }

  private static List<? extends NamedIndexedSchema<?>> createAllSchemas(
      final List<? extends NamedIndexedSchema<?>> childrenSchemas, final int maxFieldCount) {
    final Map<Integer, NamedIndexedSchema<?>> childrenSchemasByIndex =
        childrenSchemas.stream()
            .collect(
                Collectors.toUnmodifiableMap(NamedIndexedSchema::getIndex, Function.identity()));
    checkArgument(childrenSchemasByIndex.size() == childrenSchemas.size());
    checkArgument(childrenSchemasByIndex.keySet().stream().allMatch(i -> i < maxFieldCount));

    return IntStream.range(0, maxFieldCount)
        .mapToObj(
            idx ->
                childrenSchemasByIndex.getOrDefault(
                    idx,
                    new NamedIndexedSchema<>(
                        "__none_" + idx, idx, SszPrimitiveSchemas.NONE_SCHEMA)))
        .toList();
  }
}
