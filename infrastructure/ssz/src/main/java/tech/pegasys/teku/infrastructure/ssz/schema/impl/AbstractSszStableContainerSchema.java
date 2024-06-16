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

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszStableContainer;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszNone;
import tech.pegasys.teku.infrastructure.ssz.schema.SszFieldName;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszStableContainerSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;
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
  private static final long CONTAINER_G_INDEX = GIndexUtil.LEFT_CHILD_G_INDEX;
  private static final long BITVECTOR_G_INDEX = GIndexUtil.RIGHT_CHILD_G_INDEX;

  private final IntList activeFieldIndicesCache;
  private final SszBitvectorSchema<SszBitvector> activeFieldsBitvectorSchema;
  private final SszBitvector activeFieldsBitvector;

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
      final List<? extends NamedIndexedSchema<?>> activeChildrenSchemas,
      final int maxFieldCount) {
    super(name, createAllSchemas(activeChildrenSchemas, maxFieldCount));

    this.activeFieldIndicesCache =
        IntList.of(activeChildrenSchemas.stream().mapToInt(NamedIndexedSchema::getIndex).toArray());
    this.activeFieldsBitvectorSchema = SszBitvectorSchema.create(maxFieldCount);
    this.activeFieldsBitvector = activeFieldsBitvectorSchema.ofBits(activeFieldIndicesCache);
  }

  @Override
  public TreeNode getDefaultTree() {
    return BranchNode.create(super.getDefaultTree(), activeFieldsBitvector.getBackingNode());
  }

  @Override
  public TreeNode createTreeFromFieldValues(final List<? extends SszData> fieldValues) {
    checkArgument(
        fieldValues.size() == activeFieldsBitvector.getBitCount(), "Wrong number of filed values");
    final int allFieldsSize = Math.toIntExact(getMaxLength());
    final List<SszData> allFields = new ArrayList<>(allFieldsSize);

    for (int index = 0, fieldIndex = 0; index < allFieldsSize; index++) {
      allFields.add(
          activeFieldsBitvector.getBit(index) ? fieldValues.get(fieldIndex++) : SszNone.INSTANCE);
    }

    return BranchNode.create(
        super.createTreeFromFieldValues(allFields), activeFieldsBitvector.getBackingNode());
  }

  @Override
  public SszBitvector getActiveFieldsBitvector() {
    return activeFieldsBitvector;
  }

  @Override
  public int getActiveFieldCount() {
    return activeFieldIndicesCache.size();
  }

  @Override
  public int getNthActiveFieldIndex(final int nthActiveField) {
    return activeFieldIndicesCache.getInt(nthActiveField);
  }

  @Override
  public boolean isActiveField(final int index) {
    checkArgument(
        index < activeFieldsBitvectorSchema.getMaxLength(), "Wrong number of filed values");
    return activeFieldsBitvector.getBit(index);
  }

  @Override
  public int sszSerializeTree(final TreeNode node, final SszWriter writer) {
    final TreeNode bitvectorSubtree = node.get(BITVECTOR_G_INDEX);

    int size1 = activeFieldsBitvectorSchema.sszSerializeTree(bitvectorSubtree, writer);
    int size2 = super.sszSerializeTree(node, writer);
    return size1 + size2;
  }

  public int sszSerializeTreeAsProfile(final TreeNode node, final SszWriter writer) {
    return super.sszSerializeTree(node, writer);
  }

  @Override
  public TreeNode sszDeserializeTree(final SszReader reader) {
    final SszReader activeFieldsReader =
        reader.slice(activeFieldsBitvectorSchema.getSszFixedPartSize());
    final SszBitvector bitvector = activeFieldsBitvectorSchema.sszDeserialize(activeFieldsReader);
    if (!bitvector.equals(activeFieldsBitvector)) {
      throw new SszDeserializeException(
          "Invalid StableContainer bitvector: "
              + bitvector
              + ", expected "
              + activeFieldsBitvector
              + " for the stable container of type "
              + this);
    }
    return BranchNode.create(super.sszDeserializeTree(reader), bitvector.getBackingNode());
  }

  public TreeNode sszDeserializeTreeAsProfile(final SszReader reader) {
    return BranchNode.create(
        super.sszDeserializeTree(reader), activeFieldsBitvector.getBackingNode());
  }

  @Override
  public long getChildGeneralizedIndex(final long elementIndex) {
    return GIndexUtil.gIdxCompose(CONTAINER_G_INDEX, super.getChildGeneralizedIndex(elementIndex));
  }

  @Override
  public SszLengthBounds getSszLengthBounds() {
    return super.getSszLengthBounds().add(activeFieldsBitvectorSchema.getSszLengthBounds());
  }

  public SszLengthBounds getSszLengthBoundsAsProfile() {
    return super.getSszLengthBounds();
  }

  @Override
  public int getSszSize(final TreeNode node) {
    return super.getSszSize(node) + activeFieldsBitvectorSchema.getSszFixedPartSize();
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
    super.storeBackingNodes(
        nodeStore, maxBranchLevelsSkipped, GIndexUtil.gIdxLeftGIndex(rootGIndex), containerSubtree);

    nodeStore.storeBranchNode(
        node.hashTreeRoot(), rootGIndex, 1, new Bytes32[] {containerSubtree.hashTreeRoot()});
  }

  @Override
  public TreeNode loadBackingNodes(
      final TreeNodeSource nodeSource, final Bytes32 rootHash, final long rootGIndex) {
    if (TreeUtil.ZERO_TREES_BY_ROOT.containsKey(rootHash) || rootHash.equals(Bytes32.ZERO)) {
      return getDefaultTree();
    }
    final CompressedBranchInfo branchData = nodeSource.loadBranchNode(rootHash, rootGIndex);
    checkState(
        branchData.getChildren().length == 1,
        "Stable container root node must have exactly 1 children");
    checkState(branchData.getDepth() == 1, "Stable container root node must have depth of 1");
    final Bytes32 containerHash = branchData.getChildren()[0];

    final long lastUsefulGIndex =
        GIndexUtil.gIdxChildGIndex(rootGIndex, maxChunks() - 1, treeDepth());
    final TreeNode containerTreeNode =
        LoadingUtil.loadNodesToDepth(
            nodeSource,
            containerHash,
            GIndexUtil.gIdxLeftGIndex(rootGIndex),
            treeDepth(),
            super.getDefaultTree(),
            lastUsefulGIndex,
            this::loadChildNode);

    return BranchNode.create(containerTreeNode, activeFieldsBitvector.getBackingNode());
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
