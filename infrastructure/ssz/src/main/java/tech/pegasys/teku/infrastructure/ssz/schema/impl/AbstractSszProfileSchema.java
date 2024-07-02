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

import static com.google.common.base.Preconditions.checkState;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.SszProfile;
import tech.pegasys.teku.infrastructure.ssz.SszStableContainer;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.schema.SszProfileSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszStableContainerSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.infrastructure.ssz.sos.SszReader;
import tech.pegasys.teku.infrastructure.ssz.sos.SszWriter;
import tech.pegasys.teku.infrastructure.ssz.tree.BranchNode;
import tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNodeSource;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNodeSource.CompressedBranchInfo;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNodeStore;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeUtil;

public abstract class AbstractSszProfileSchema<C extends SszProfile>
    extends AbstractSszStableContainerBaseSchema<C> implements SszProfileSchema<C> {

  private final IntList activeFieldIndicesCache;
  private final IntList optionalFieldIndicesCache;
  private final Integer[] optionalMapping;
  private final Set<Integer> optionalFieldIndices;
  private final Optional<SszBitvectorSchema<SszBitvector>> optionalFieldsSchema;
  private final SszStableContainerSchema<? extends SszStableContainer> stableContainer;
  private final DeserializableTypeDefinition<C> jsonTypeDefinition;

  public AbstractSszProfileSchema(
      final String name,
      final SszStableContainerSchema<? extends SszStableContainer> stableContainerSchema,
      final Set<Integer> requiredFieldIndices,
      final Set<Integer> optionalFieldIndices) {
    super(
        name,
        stableContainerSchema.getChildrenNamedSchemas(),
        requiredFieldIndices,
        optionalFieldIndices,
        stableContainerSchema.getMaxFieldCount());
    this.optionalFieldIndices = optionalFieldIndices;
    if (optionalFieldIndices.isEmpty()) {
      this.optionalFieldsSchema = Optional.empty();
      this.optionalMapping = new Integer[0];
      this.optionalFieldIndicesCache = IntList.of();

    } else {
      this.optionalFieldsSchema =
          Optional.of(SszBitvectorSchema.create(optionalFieldIndices.size()));
      this.optionalFieldIndicesCache =
          IntList.of(
              optionalFieldIndices.stream()
                  .sorted(Comparator.naturalOrder())
                  .mapToInt(i -> i)
                  .toArray());
      this.optionalMapping =
          new Integer[optionalFieldIndicesCache.getInt(optionalFieldIndices.size() - 1) + 1];
      optionalFieldIndices.stream()
          .sorted(Comparator.naturalOrder())
          .forEach(i -> optionalMapping[i] = optionalFieldIndicesCache.indexOf((int) i));
    }

    this.stableContainer = stableContainerSchema;
    this.activeFieldIndicesCache =
        IntList.of(
            requiredFieldIndices.stream()
                .sorted(Comparator.naturalOrder())
                .mapToInt(i -> i)
                .toArray());
    this.jsonTypeDefinition = null; // SszStableContainerTypeDefinition.createFor(this);
  }

  //  private static List<? extends NamedSchema<?>> prepareSchemas(
  //      final SszStableContainerSchema<? extends SszStableContainer> stableContainer,
  //      final Set<Integer> activeFieldIndices) {
  //    return stableContainer.getDefinedChildrenSchemas().stream()
  //        .map(
  //            namedIndexedSchema -> {
  //              final int index = namedIndexedSchema.getIndex();
  //              if (activeFieldIndices.contains(index)) {
  //                return namedIndexedSchema;
  //              }
  //              return new NamedIndexedSchema<>(
  //                  "__none_" + index, index, SszPrimitiveSchemas.NONE_SCHEMA);
  //            })
  //        .toList();
  //  }

  @Override
  public DeserializableTypeDefinition<C> getJsonTypeDefinition() {
    return jsonTypeDefinition;
  }

  @Override
  public SszStableContainerSchema<? extends SszStableContainer> getStableContainerSchema() {
    return stableContainer;
  }

  //  @Override
  //  public TreeNode createTreeFromFieldValues(final List<? extends SszData> fieldValues) {
  //    checkArgument(fieldValues.size() == requiredFields.getBitCount(), "Wrong number of filed
  // values");
  //    final int allFieldsSize = Math.toIntExact(getMaxLength());
  //    final List<SszData> allFields = new ArrayList<>(allFieldsSize);
  //
  //    for (int index = 0, fieldIndex = 0; index < allFieldsSize; index++) {
  //      allFields.add(requiredFields.getBit(index) ? fieldValues.get(fieldIndex++) :
  // SszNone.INSTANCE);
  //    }
  //
  //    return BranchNode.create(
  //        super.createTreeFromFieldValues(allFields), requiredFields.getBackingNode());
  //  }

  @Override
  int sszSerializeActiveFields(final SszBitvector activeFieldsBitvector, final SszWriter writer) {
    if (optionalFieldsSchema.isEmpty()) {
      // without optional fields, a profile won't serialize the bitvector
      return 0;
    }
    final IntList a = new IntArrayList(optionalFieldIndices.size());
    optionalFieldIndices.stream()
        .filter(activeFieldsBitvector::getBit)
        .map(i -> optionalMapping[i])
        .forEach(a::add);

    return optionalFieldsSchema
        .get()
        .sszSerializeTree(optionalFieldsSchema.get().ofBits(a).getBackingNode(), writer);
  }

  @Override
  SszBitvector sszDeserializeActiveFieldsTree(final SszReader reader) {
    if (optionalFieldsSchema.isEmpty()) {
      // without optional fields the active fields corresponds to the required fields of the schema
      return getRequiredFields();
    }
    final SszReader optionalFieldsReader =
        reader.slice(optionalFieldsSchema.get().getSszFixedPartSize());
    final SszBitvector optionalFields =
        optionalFieldsSchema.get().sszDeserialize(optionalFieldsReader);
    return getActiveFieldsSchema()
        .ofBits(
            IntStream.concat(
                    getRequiredFields().streamAllSetBits(),
                    optionalFields.streamAllSetBits().map(optionalFieldIndicesCache::getInt))
                .toArray());
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

    return BranchNode.create(containerTree, getRequiredFields().getBackingNode());
  }

  private TreeNode loadChildNode(
      final TreeNodeSource nodeSource, final Bytes32 childHash, final long childGIndex) {
    final int childIndex = GIndexUtil.gIdxChildIndexFromGIndex(childGIndex, treeDepth());
    return getChildSchema(childIndex).loadBackingNodes(nodeSource, childHash, childGIndex);
  }

  //  @Override
  //  public long getChildGeneralizedIndex(final long elementIndex) {
  //    return GIndexUtil.gIdxCompose(CONTAINER_G_INDEX,
  // super.getChildGeneralizedIndex(elementIndex));
  //  }
}
