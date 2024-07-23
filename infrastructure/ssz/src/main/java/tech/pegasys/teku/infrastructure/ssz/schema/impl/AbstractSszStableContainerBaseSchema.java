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
import static tech.pegasys.teku.infrastructure.ssz.schema.impl.ContainerSchemaUtil.deserializeFixedChild;
import static tech.pegasys.teku.infrastructure.ssz.schema.impl.ContainerSchemaUtil.deserializeVariableChild;
import static tech.pegasys.teku.infrastructure.ssz.schema.impl.ContainerSchemaUtil.serializeFixedChild;
import static tech.pegasys.teku.infrastructure.ssz.schema.impl.ContainerSchemaUtil.serializeVariableChild;
import static tech.pegasys.teku.infrastructure.ssz.schema.impl.ContainerSchemaUtil.validateAndPrepareForVariableChildrenDeserialization;

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
import java.util.Set;
import java.util.function.Supplier;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszStableContainerBase;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszNone;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszStableContainerBaseSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszType;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszContainerSchema.NamedSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.json.SszStableContainerBaseTypeDefinition;
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

/**
 * Implements the common container logic shared among Profile and StableContainer as per <a
 * href="https://eips.ethereum.org/EIPS/eip-7495">eip-7495</a> specifications.
 *
 * <p>With a combination of:
 *
 * <ol>
 *   <li>A NamedSchema list
 *   <li>A set of required field indices.
 *   <li>A set of optional field indices.
 *   <li>A theoretical future maximum field count.
 * </ol>
 *
 * this class can represent:
 *
 * <ol>
 *   <li>A StableContainer (empty required field indices, non-empty optional field indices)
 *   <li>A Profile (non-empty required field indices, optional field indices can be both empty and
 *       not-empty)
 * </ol>
 *
 * @param <C> the type of actual container class
 */
public abstract class AbstractSszStableContainerBaseSchema<C extends SszStableContainerBase>
    implements SszStableContainerBaseSchema<C> {
  public static final long CONTAINER_G_INDEX = GIndexUtil.LEFT_CHILD_G_INDEX;
  public static final long BITVECTOR_G_INDEX = GIndexUtil.RIGHT_CHILD_G_INDEX;

  private final Supplier<SszLengthBounds> sszLengthBounds =
      Suppliers.memoize(this::computeSszLengthBounds);
  private final int cachedMaxLength;
  private final String containerName;
  private final List<NamedSchema<?>> definedChildrenNamedSchemas;
  private final Object2IntMap<String> definedChildrenNamesToFieldIndex;
  private final List<String> definedChildrenNames;
  private final List<? extends SszSchema<?>> definedChildrenSchemas;
  private final int sszFixedPartSize;
  private final boolean isFixedSize;
  private final int maxFieldCount;
  private final long treeWidth;
  private final SszBitvectorSchema<SszBitvector> activeFieldsSchema;
  private final SszBitvector requiredFields;
  private final SszBitvector optionalFields;
  private final boolean hasOptionalFields;
  private final TreeNode defaultTreeNode;

  private final DeserializableTypeDefinition<C> jsonTypeDefinition;

  private static long getContainerGIndex(final long rootGIndex) {
    return GIndexUtil.gIdxLeftGIndex(rootGIndex);
  }

  private static long getBitvectorGIndex(final long rootGIndex) {
    return GIndexUtil.gIdxRightGIndex(rootGIndex);
  }

  public AbstractSszStableContainerBaseSchema(
      final String name,
      final List<NamedSchema<?>> definedChildrenNamedSchemas,
      final Set<Integer> requiredFieldIndices,
      final Set<Integer> optionalFieldIndices,
      final int maxFieldCount) {
    checkArgument(
        optionalFieldIndices.stream().noneMatch(requiredFieldIndices::contains),
        "optional and active fields must not overlap");

    this.containerName = name;
    this.maxFieldCount = maxFieldCount;
    this.definedChildrenNamedSchemas = definedChildrenNamedSchemas;
    this.definedChildrenSchemas =
        definedChildrenNamedSchemas.stream().map(NamedSchema::getSchema).toList();
    this.activeFieldsSchema = SszBitvectorSchema.create(maxFieldCount);
    this.requiredFields = activeFieldsSchema.ofBits(requiredFieldIndices);
    this.optionalFields = activeFieldsSchema.ofBits(optionalFieldIndices);
    this.cachedMaxLength = requiredFields.getBitCount() + optionalFields.getBitCount();
    this.treeWidth = SszStableContainerBaseSchema.super.treeWidth();
    this.definedChildrenNames =
        definedChildrenNamedSchemas.stream().map(NamedSchema::getName).toList();
    this.definedChildrenNamesToFieldIndex = new Object2IntOpenHashMap<>();
    for (int i = 0; i < definedChildrenNamedSchemas.size(); i++) {
      definedChildrenNamesToFieldIndex.put(definedChildrenNamedSchemas.get(i).getName(), i);
    }
    this.defaultTreeNode =
        BranchNode.create(
            createDefaultContainerTreeNode(requiredFieldIndices), requiredFields.getBackingNode());
    this.hasOptionalFields = optionalFields.getBitCount() > 0;
    this.jsonTypeDefinition = SszStableContainerBaseTypeDefinition.createFor(this);
    this.sszFixedPartSize = calcSszFixedPartSize();
    this.isFixedSize = calcIsFixedSize();
  }

  protected TreeNode createDefaultContainerTreeNode(final Set<Integer> activeFieldIndices) {
    final List<TreeNode> defaultChildren = new ArrayList<>(getMaxFieldCount());
    for (int i = 0; i < getMaxFieldCount(); i++) {
      if (activeFieldIndices.contains(i)) {
        defaultChildren.add(getChildSchema(i).getDefaultTree());
      } else {
        defaultChildren.add(SszNone.INSTANCE.getBackingNode());
      }
    }
    return TreeUtil.createTree(defaultChildren);
  }

  @Override
  public DeserializableTypeDefinition<C> getJsonTypeDefinition() {
    return jsonTypeDefinition;
  }

  /** MaxLength exposes the effective potential numbers of fields */
  @Override
  public long getMaxLength() {
    return cachedMaxLength;
  }

  /** The backing tree node is always filled up maxFieldCount, so maxChunks must reflect it */
  @Override
  public long maxChunks() {
    return ((long) getMaxFieldCount() - 1) / getElementsPerChunk() + 1;
  }

  @Override
  public long treeWidth() {
    return treeWidth;
  }

  @Override
  public List<NamedSchema<?>> getChildrenNamedSchemas() {
    return definedChildrenNamedSchemas;
  }

  @Override
  public SszBitvector getActiveFieldsBitvectorFromBackingNode(final TreeNode node) {
    if (hasOptionalFields) {
      return activeFieldsSchema.createFromBackingNode(node.get(BITVECTOR_G_INDEX));
    }
    return requiredFields;
  }

  @Override
  public SszBitvector getRequiredFields() {
    return requiredFields;
  }

  @Override
  public SszBitvector getOptionalFields() {
    return optionalFields;
  }

  @Override
  public int getMaxFieldCount() {
    return maxFieldCount;
  }

  @Override
  public TreeNode getDefaultTree() {
    return defaultTreeNode;
  }

  @Override
  public boolean hasOptionalFields() {
    return hasOptionalFields;
  }

  @Override
  public TreeNode createTreeFromFieldValues(final List<? extends SszData> fieldValues) {
    if (hasOptionalFields) {
      throw new UnsupportedOperationException(
          "createTreeFromFieldValues can be used only when the schema has no optional fields");
    }

    final int fieldsCount = getMaxFieldCount();
    checkArgument(fieldValues.size() <= fieldsCount, "Wrong number of filed values");

    final List<SszData> allFields = new ArrayList<>(fieldsCount);

    for (int index = 0, fieldIndex = 0; index < fieldsCount; index++) {
      if (fieldIndex >= fieldValues.size() || !requiredFields.getBit(index)) {
        allFields.add(SszNone.INSTANCE);
      } else {
        allFields.add(fieldValues.get(fieldIndex++));
      }
    }

    assert allFields.size() == fieldsCount;

    final TreeNode containerTree =
        TreeUtil.createTree(allFields.stream().map(SszData::getBackingNode).toList());

    return BranchNode.create(containerTree, requiredFields.getBackingNode());
  }

  @Override
  public TreeNode createTreeFromOptionalFieldValues(
      final List<Optional<? extends SszData>> fieldValues) {
    final int fieldsCount = getMaxFieldCount();
    checkArgument(fieldValues.size() < fieldsCount, "Wrong number of filed values");

    final List<SszData> allFields = new ArrayList<>(fieldsCount);

    final IntList activeFieldIndices = new IntArrayList();

    for (int index = 0, fieldIndex = 0; index < fieldsCount; index++) {
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
        if (requiredFields.getBit(index)) {
          throw new IllegalArgumentException("supposed to be active");
        }
        allFields.add(SszNone.INSTANCE);
      }
    }

    assert allFields.size() == fieldsCount;

    final TreeNode activeFieldsTree =
        activeFieldsSchema.ofBits(activeFieldIndices).getBackingNode();
    final TreeNode containerTree =
        TreeUtil.createTree(allFields.stream().map(SszData::getBackingNode).toList());

    return BranchNode.create(containerTree, activeFieldsTree);
  }

  @Override
  public int getFieldsCount() {
    return definedChildrenNamedSchemas.size();
  }

  @Override
  public boolean isFixedSize() {
    return isFixedSize;
  }

  private boolean calcIsFixedSize() {
    if (hasOptionalFields) {
      // for containers with optional fields we behave as variable ssz
      return false;
    }

    return requiredFields
        .streamAllSetBits()
        .mapToObj(this::getChildSchema)
        .allMatch(SszType::isFixedSize);
  }

  @Override
  public boolean hasExtraDataInBackingTree() {
    return true;
  }

  @Override
  public int getSszFixedPartSize() {
    return sszFixedPartSize;
  }

  int calcSszFixedPartSize() {
    if (hasOptionalFields) {
      // for containers with optional fields we behave as variable ssz
      return 0;
    }

    return calcSszFixedPartSize(requiredFields);
  }

  /**
   * Delegates active fields size: Profile and StableContainer have different
   * serialization\deserialization rules
   */
  abstract int getSszActiveFieldsSize(final TreeNode node);

  @Override
  public int getSszVariablePartSize(final TreeNode node) {
    if (hasOptionalFields) {
      // containers with optional fields always behaves as variable ssz
      final SszBitvector activeFields = getActiveFieldsBitvectorFromBackingNode(node);

      final int containerSize =
          activeFields
              .streamAllSetBits()
              .map(
                  activeFieldIndex -> {
                    final SszSchema<?> schema = getChildSchema(activeFieldIndex);
                    final int childSize =
                        schema.getSszSize(node.get(getChildGeneralizedIndex(activeFieldIndex)));
                    return schema.isFixedSize() ? childSize : childSize + SSZ_LENGTH_SIZE;
                  })
              .sum();

      final int activeFieldsSize = getSszActiveFieldsSize(activeFields.getBackingNode());
      return containerSize + activeFieldsSize;
    }

    if (isFixedSize()) {
      return 0;
    }

    final int[] size = {0};
    requiredFields
        .streamAllSetBits()
        .forEach(
            index -> {
              final SszSchema<?> childType = getChildSchema(index);
              if (!childType.isFixedSize()) {
                size[0] += childType.getSszSize(node.get(getChildGeneralizedIndex(index)));
              }
            });

    return size[0];
  }

  /**
   * Delegates serialization to deriving classes: Profile and StableContainer have different
   * serialization\deserialization rules
   */
  abstract int sszSerializeActiveFields(
      final SszBitvector activeFieldsBitvector, final SszWriter writer);

  @Override
  public int sszSerializeTree(final TreeNode node, final SszWriter writer) {
    final SszBitvector activeFieldsBitvector = getActiveFieldsBitvectorFromBackingNode(node);
    // we won't write active field when no optional fields are permitted
    final int activeFieldsWroteBytes = sszSerializeActiveFields(activeFieldsBitvector, writer);

    final int[] variableSizes = new int[activeFieldsBitvector.size()];

    final int variableChildOffset =
        activeFieldsBitvector
            .streamAllSetBits()
            .reduce(
                calcSszFixedPartSize(activeFieldsBitvector),
                (accumulatedOffset, activeFieldIndex) ->
                    accumulatedOffset
                        + serializeFixedChild(
                            writer,
                            this,
                            activeFieldIndex,
                            node,
                            variableSizes,
                            accumulatedOffset));

    activeFieldsBitvector
        .streamAllSetBits()
        .forEach(
            activeFieldIndex ->
                serializeVariableChild(writer, this, activeFieldIndex, variableSizes, node));

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

  /**
   * Delegates deserialization to deriving classes: Profile and StableContainer have different
   * serialization\deserialization rules
   */
  abstract SszBitvector sszDeserializeActiveFieldsTree(final SszReader reader);

  @Override
  public TreeNode sszDeserializeTree(final SszReader reader) {
    final SszBitvector activeFields = sszDeserializeActiveFieldsTree(reader);
    checkArgument(
        activeFields.getLastSetBitIndex() < definedChildrenSchemas.size(),
        "All extra bits in the Bitvector[N] that exceed the number of fields MUST be 0");
    return BranchNode.create(
        deserializeContainer(reader, activeFields), activeFields.getBackingNode());
  }

  private TreeNode deserializeContainer(final SszReader reader, final SszBitvector activeFields) {
    int endOffset = reader.getAvailableBytes();
    int childCount = getMaxFieldCount();
    final Queue<TreeNode> fixedChildrenSubtrees = new ArrayDeque<>(childCount);
    final IntList variableChildrenOffsets = new IntArrayList(childCount);
    activeFields
        .streamAllSetBits()
        .forEach(
            i ->
                deserializeFixedChild(
                    reader, fixedChildrenSubtrees, variableChildrenOffsets, this, i));

    final ArrayDeque<Integer> variableChildrenSizes =
        validateAndPrepareForVariableChildrenDeserialization(
            reader, variableChildrenOffsets, endOffset);

    final List<TreeNode> childrenSubtrees = new ArrayList<>(childCount);
    for (int i = 0; i < childCount; i++) {
      if (!activeFields.getBit(i)) {
        childrenSubtrees.add(SszPrimitiveSchemas.NONE_SCHEMA.getDefaultTree());
        continue;
      }
      deserializeVariableChild(
          reader, childrenSubtrees, fixedChildrenSubtrees, variableChildrenSizes, this, i);
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
        CONTAINER_G_INDEX,
        SszStableContainerBaseSchema.super.getChildGeneralizedIndex(elementIndex));
  }

  @Override
  public SszLengthBounds getSszLengthBounds() {
    return sszLengthBounds.get();
  }

  /**
   * Delegates activeFields bounds: Profile and StableContainer have different
   * serialization\deserialization rules
   */
  abstract SszLengthBounds computeActiveFieldsSszLengthBounds();

  /**
   * Bounds are calculate as follows:
   *
   * <p>ActiveFields bitvector bounds added to
   *
   * <ol>
   *   <li>Min bound is the min bound for the required only fields
   *   <li>Max bound is the max bound for the required fields and all optional fields
   * </ol>
   */
  private SszLengthBounds computeSszLengthBounds() {
    final SszLengthBounds requiredOnlyFieldsBounds =
        requiredFields
            .streamAllSetBits()
            .mapToObj(this::computeSingleSchemaLengthBounds)
            .reduce(SszLengthBounds.ZERO, SszLengthBounds::add);

    final SszLengthBounds includingOptionalFieldsBounds =
        optionalFields
            .streamAllSetBits()
            .mapToObj(this::computeSingleSchemaLengthBounds)
            .reduce(requiredOnlyFieldsBounds, SszLengthBounds::add);

    return requiredOnlyFieldsBounds
        .or(includingOptionalFieldsBounds)
        .add(computeActiveFieldsSszLengthBounds());
  }

  private SszLengthBounds computeSingleSchemaLengthBounds(final int fieldIndex) {
    final SszSchema<?> schema = getChildSchema(fieldIndex);
    return schema
        .getSszLengthBounds()
        // dynamic sized children need 4-byte offset
        .addBytes((schema.isFixedSize() ? 0 : SSZ_LENGTH_SIZE))
        // elements are not packed in containers
        .ceilToBytes();
  }

  @Override
  public void storeBackingNodes(
      final TreeNodeStore nodeStore,
      final int maxBranchLevelsSkipped,
      final long rootGIndex,
      final TreeNode node) {
    final TreeNode containerSubtree = node.get(CONTAINER_G_INDEX);
    final TreeNode activeFieldsBitvectorSubtree = node.get(BITVECTOR_G_INDEX);

    storeContainerBackingNodes(
        getActiveFieldsBitvectorFromBackingNode(node),
        nodeStore,
        maxBranchLevelsSkipped,
        getContainerGIndex(rootGIndex),
        containerSubtree);

    final Bytes32[] children;
    if (hasOptionalFields) {
      activeFieldsSchema.storeBackingNodes(
          nodeStore,
          maxBranchLevelsSkipped,
          getBitvectorGIndex(rootGIndex),
          activeFieldsBitvectorSubtree);

      children =
          new Bytes32[] {
            containerSubtree.hashTreeRoot(), activeFieldsBitvectorSubtree.hashTreeRoot()
          };
    } else {
      children = new Bytes32[] {containerSubtree.hashTreeRoot()};
    }

    nodeStore.storeBranchNode(node.hashTreeRoot(), rootGIndex, 1, children);
  }

  private void storeContainerBackingNodes(
      final SszBitvector activeFields,
      final TreeNodeStore nodeStore,
      final int maxBranchLevelsSkipped,
      final long rootGIndex,
      final TreeNode node) {

    final int childDepth = treeDepth();
    if (childDepth == 0) {
      // Only one child so wrapper is omitted
      storeChildNode(nodeStore, maxBranchLevelsSkipped, rootGIndex, node);
      return;
    }
    final long lastUsefulGIndex =
        GIndexUtil.gIdxChildGIndex(rootGIndex, maxChunks() - 1, childDepth);
    StoringUtil.storeNodesToDepth(
        nodeStore,
        maxBranchLevelsSkipped,
        node,
        rootGIndex,
        childDepth,
        lastUsefulGIndex,
        (targetDepthNode, targetDepthGIndex) ->
            storeChildNode(
                nodeStore,
                maxBranchLevelsSkipped,
                targetDepthGIndex,
                targetDepthNode,
                activeFields));
  }

  public void storeChildNode(
      final TreeNodeStore nodeStore,
      final int maxBranchLevelsSkipped,
      final long gIndex,
      final TreeNode node,
      final SszBitvector activeFields) {
    final int childIndex = GIndexUtil.gIdxChildIndexFromGIndex(gIndex, treeDepth());
    if (activeFields.getBit(childIndex)) {
      final SszSchema<?> childSchema = getChildSchema(childIndex);
      childSchema.storeBackingNodes(nodeStore, maxBranchLevelsSkipped, gIndex, node);
    } else {
      SszPrimitiveSchemas.NONE_SCHEMA.storeBackingNodes(
          nodeStore, maxBranchLevelsSkipped, gIndex, node);
    }
  }

  @Override
  public TreeNode loadBackingNodes(
      final TreeNodeSource nodeSource, final Bytes32 rootHash, final long rootGIndex) {
    if (TreeUtil.ZERO_TREES_BY_ROOT.containsKey(rootHash) || rootHash.equals(Bytes32.ZERO)) {
      return getDefaultTree();
    }
    final CompressedBranchInfo branchData = nodeSource.loadBranchNode(rootHash, rootGIndex);
    if (hasOptionalFields) {
      checkState(
          branchData.getChildren().length == 2,
          "Stable container root node must have exactly 2 children when allows optional fields");
    } else {
      checkState(
          branchData.getChildren().length == 1,
          "Stable container root node must have exactly 1 children when optional fields are not allowed");
    }

    checkState(branchData.getDepth() == 1, "Stable container root node must have depth of 1");
    final Bytes32 containerHash = branchData.getChildren()[0];

    final SszBitvector activeFields;
    if (hasOptionalFields) {
      final Bytes32 activeFieldsBitvectorHash = branchData.getChildren()[1];
      final TreeNode activeFieldsTreeNode =
          activeFieldsSchema.loadBackingNodes(
              nodeSource, activeFieldsBitvectorHash, getBitvectorGIndex(rootGIndex));
      activeFields = activeFieldsSchema.createFromBackingNode(activeFieldsTreeNode);
    } else {
      activeFields = requiredFields;
    }

    int lastActiveFieldOrFirst = Math.max(0, activeFields.getLastSetBitIndex());
    final long lastUsefulGIndex =
        GIndexUtil.gIdxChildGIndex(rootGIndex, lastActiveFieldOrFirst, treeDepth());
    final TreeNode containerTreeNode =
        LoadingUtil.loadNodesToDepth(
            nodeSource,
            containerHash,
            getContainerGIndex(rootGIndex),
            treeDepth(),
            defaultTreeNode.get(CONTAINER_G_INDEX),
            lastUsefulGIndex,
            (tns, childHash, childGIndex) ->
                loadChildNode(activeFields, tns, childHash, childGIndex));

    return BranchNode.create(containerTreeNode, activeFields.getBackingNode());
  }

  private TreeNode loadChildNode(
      final SszBitvector activeFields,
      final TreeNodeSource nodeSource,
      final Bytes32 childHash,
      final long childGIndex) {
    final int childIndex = GIndexUtil.gIdxChildIndexFromGIndex(childGIndex, treeDepth());
    if (activeFields.getLastSetBitIndex() >= childIndex && activeFields.getBit(childIndex)) {
      return getChildSchema(childIndex).loadBackingNodes(nodeSource, childHash, childGIndex);
    }
    return SszPrimitiveSchemas.NONE_SCHEMA.loadBackingNodes(nodeSource, childHash, childGIndex);
  }

  @Override
  public SszSchema<?> getChildSchema(final int index) {
    return definedChildrenSchemas.get(index);
  }

  @Override
  public List<? extends SszSchema<?>> getFieldSchemas() {
    return definedChildrenSchemas;
  }

  /**
   * Get the index of a field by name
   *
   * @param fieldName the name of the field
   * @return The index if it exists, otherwise -1
   */
  @Override
  public int getFieldIndex(final String fieldName) {
    return definedChildrenNamesToFieldIndex.getOrDefault(fieldName, -1);
  }

  @Override
  public String getContainerName() {
    return !containerName.isEmpty() ? containerName : getClass().getName();
  }

  @Override
  public List<String> getFieldNames() {
    return definedChildrenNames;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    AbstractSszStableContainerBaseSchema<?> that = (AbstractSszStableContainerBaseSchema<?>) o;
    return definedChildrenSchemas.equals(that.definedChildrenSchemas)
        && requiredFields.equals(that.requiredFields)
        && optionalFields.equals(that.optionalFields);
  }

  @Override
  public int hashCode() {
    return Objects.hash(definedChildrenSchemas, requiredFields, optionalFields);
  }

  @Override
  public String toString() {
    return getContainerName();
  }
}
