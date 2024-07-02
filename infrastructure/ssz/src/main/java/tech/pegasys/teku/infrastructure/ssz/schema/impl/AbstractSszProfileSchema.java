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
import static tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszStableContainerSchema.CONTAINER_G_INDEX;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszProfile;
import tech.pegasys.teku.infrastructure.ssz.SszStableContainer;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszNone;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszProfileSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszStableContainerSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszStableContainerSchema.NamedIndexedSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.json.SszProfileTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.sos.SszReader;
import tech.pegasys.teku.infrastructure.ssz.tree.BranchNode;
import tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNodeSource;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNodeSource.CompressedBranchInfo;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNodeStore;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeUtil;

public abstract class AbstractSszProfileSchema<C extends SszProfile>
    extends AbstractSszContainerSchema<C> implements SszProfileSchema<C> {

  private final IntList activeFieldIndicesCache;
  private final SszBitvector activeFields;
  private final SszStableContainerSchema<? extends SszStableContainer> stableContainer;
  private final DeserializableTypeDefinition<C> jsonTypeDefinition;

  public AbstractSszProfileSchema(
      final String name,
      final SszStableContainerSchema<? extends SszStableContainer> stableContainerSchema,
      final Set<Integer> activeFieldIndices) {
    super(name, prepareSchemas(stableContainerSchema, activeFieldIndices));

    this.stableContainer = stableContainerSchema;
    this.activeFieldIndicesCache =
        IntList.of(
            activeFieldIndices.stream()
                .sorted(Comparator.naturalOrder())
                .mapToInt(index -> getEffectiveChildIndex(index, stableContainerSchema))
                .toArray());
    this.activeFields = getActiveFieldsSchema().ofBits(activeFieldIndices);
    this.jsonTypeDefinition = SszProfileTypeDefinition.createFor(this);
  }

  private static int getEffectiveChildIndex(
      final int index,
      final SszStableContainerSchema<? extends SszStableContainer> stableContainerSchema) {
    final List<? extends NamedIndexedSchema<?>> definedChildrenSchemas =
        stableContainerSchema.getDefinedChildrenSchemas();
    checkArgument(index >= 0 && index < definedChildrenSchemas.size(), "Index out of bounds");

    return definedChildrenSchemas.get(index).getIndex();
  }

  private static List<? extends NamedSchema<?>> prepareSchemas(
      final SszStableContainerSchema<? extends SszStableContainer> stableContainer,
      final Set<Integer> activeFieldIndices) {
    return stableContainer.getDefinedChildrenSchemas().stream()
        .map(
            namedIndexedSchema -> {
              final int index = namedIndexedSchema.getIndex();
              if (activeFieldIndices.contains(index)) {
                return namedIndexedSchema;
              }
              return new NamedIndexedSchema<>(
                  "__none_" + index, index, SszPrimitiveSchemas.NONE_SCHEMA);
            })
        .toList();
  }

  @Override
  public DeserializableTypeDefinition<C> getJsonTypeDefinition() {
    return jsonTypeDefinition;
  }

  @Override
  public List<? extends NamedIndexedSchema<?>> getDefinedChildrenSchemas() {
    return stableContainer.getDefinedChildrenSchemas();
  }

  @Override
  public SszStableContainerSchema<? extends SszStableContainer> getStableContainerSchema() {
    return stableContainer;
  }

  @Override
  public TreeNode createTreeFromFieldValues(final List<? extends SszData> fieldValues) {
    checkArgument(
        fieldValues.size() == getDefaultActiveFields().getBitCount(),
        "Wrong number of filed values");
    final int allFieldsSize = Math.toIntExact(getMaxLength());
    final List<SszData> allFields = new ArrayList<>(allFieldsSize);

    for (int index = 0, fieldIndex = 0; index < allFieldsSize; index++) {
      allFields.add(
          getDefaultActiveFields().getBit(index)
              ? fieldValues.get(fieldIndex++)
              : SszNone.INSTANCE);
    }

    return BranchNode.create(
        super.createTreeFromFieldValues(allFields), getDefaultActiveFields().getBackingNode());
  }

  @Override
  public TreeNode sszDeserializeTree(final SszReader reader) {
    return BranchNode.create(super.sszDeserializeTree(reader), activeFields.getBackingNode());
  }

  @Override
  public TreeNode getDefaultTree() {
    return BranchNode.create(super.getDefaultTree(), activeFields.getBackingNode());
  }

  @Override
  public int getMaxFieldCount() {
    return stableContainer.getMaxFieldCount();
  }

  @Override
  public SszBitvector getDefaultActiveFields() {
    return activeFields;
  }

  @Override
  public SszBitvector getActiveFieldsBitvectorFromBackingNode(final TreeNode node) {
    checkArgument(
        stableContainer.getActiveFieldsBitvectorFromBackingNode(node).equals(activeFields),
        "activeFields bitvector from backing node not matching the activeFields of the profile");
    return activeFields;
  }

  @Override
  public SszBitvectorSchema<SszBitvector> getActiveFieldsSchema() {
    return stableContainer.getActiveFieldsSchema();
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
    checkArgument(index < getActiveFieldsSchema().getMaxLength(), "Wrong number of filed values");
    return activeFields.getBit(index);
  }

  @Override
  public void storeBackingNodes(
      final TreeNodeStore nodeStore,
      final int maxBranchLevelsSkipped,
      final long rootGIndex,
      final TreeNode node) {
    final TreeNode containerSubtree = node.get(CONTAINER_G_INDEX);
    super.storeBackingNodes(
        nodeStore,
        maxBranchLevelsSkipped,
        GIndexUtil.gIdxLeftGIndex(rootGIndex),
        node.get(CONTAINER_G_INDEX));

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
        branchData.getChildren().length == 1, "Profile root node must have exactly 1 children");
    checkState(branchData.getDepth() == 1, "Profile root node must have depth of 1");
    final Bytes32 containerHash = branchData.getChildren()[0];

    long containerRootGIndex = GIndexUtil.gIdxLeftGIndex(rootGIndex);

    final long lastUsefulGIndex =
        GIndexUtil.gIdxChildGIndex(containerRootGIndex, maxChunks() - 1, treeDepth());
    TreeNode containerTree =
        LoadingUtil.loadNodesToDepth(
            nodeSource,
            containerHash,
            containerRootGIndex,
            treeDepth(),
            super.getDefaultTree(),
            lastUsefulGIndex,
            this::loadChildNode);

    return BranchNode.create(containerTree, activeFields.getBackingNode());
  }

  private TreeNode loadChildNode(
      final TreeNodeSource nodeSource, final Bytes32 childHash, final long childGIndex) {
    final int childIndex = GIndexUtil.gIdxChildIndexFromGIndex(childGIndex, treeDepth());
    return getChildSchema(childIndex).loadBackingNodes(nodeSource, childHash, childGIndex);
  }

  @Override
  public SszBitvector getActiveFields() {
    return activeFields;
  }

  @Override
  public long getChildGeneralizedIndex(final long elementIndex) {
    return GIndexUtil.gIdxCompose(CONTAINER_G_INDEX, super.getChildGeneralizedIndex(elementIndex));
  }
}
