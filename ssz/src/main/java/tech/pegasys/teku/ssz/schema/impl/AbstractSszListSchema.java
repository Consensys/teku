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

package tech.pegasys.teku.ssz.schema.impl;

import static com.google.common.base.Preconditions.checkState;
import static tech.pegasys.teku.ssz.tree.TreeUtil.bitsCeilToBytes;

import java.nio.ByteOrder;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ssz.SszData;
import tech.pegasys.teku.ssz.SszList;
import tech.pegasys.teku.ssz.schema.SszListSchema;
import tech.pegasys.teku.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.ssz.schema.SszSchema;
import tech.pegasys.teku.ssz.schema.SszSchemaHints;
import tech.pegasys.teku.ssz.schema.impl.LoadingUtil.ChildLoader;
import tech.pegasys.teku.ssz.sos.SszLengthBounds;
import tech.pegasys.teku.ssz.sos.SszReader;
import tech.pegasys.teku.ssz.sos.SszWriter;
import tech.pegasys.teku.ssz.tree.BranchNode;
import tech.pegasys.teku.ssz.tree.GIndexUtil;
import tech.pegasys.teku.ssz.tree.LeafNode;
import tech.pegasys.teku.ssz.tree.TreeNode;
import tech.pegasys.teku.ssz.tree.TreeNodeSource;
import tech.pegasys.teku.ssz.tree.TreeNodeSource.CompressedBranchInfo;
import tech.pegasys.teku.ssz.tree.TreeNodeStore;
import tech.pegasys.teku.ssz.tree.TreeUtil;

public abstract class AbstractSszListSchema<
        ElementDataT extends SszData, SszListT extends SszList<ElementDataT>>
    extends AbstractSszCollectionSchema<ElementDataT, SszListT>
    implements SszListSchema<ElementDataT, SszListT> {
  private final SszVectorSchemaImpl<ElementDataT> compatibleVectorSchema;
  private final SszLengthBounds sszLengthBounds;

  protected AbstractSszListSchema(SszSchema<ElementDataT> elementSchema, long maxLength) {
    this(elementSchema, maxLength, SszSchemaHints.none());
  }

  protected AbstractSszListSchema(
      SszSchema<ElementDataT> elementSchema, long maxLength, SszSchemaHints hints) {
    super(maxLength, elementSchema, hints);
    this.compatibleVectorSchema =
        new SszVectorSchemaImpl<>(elementSchema, getMaxLength(), true, getHints());
    this.sszLengthBounds = computeSszLengthBounds(elementSchema, maxLength);
  }

  @Override
  protected TreeNode createDefaultTree() {
    return createTree(getCompatibleVectorSchema().getDefaultTree(), 0);
  }

  protected TreeNode createTree(TreeNode dataNode, int length) {
    return BranchNode.create(dataNode, toLengthNode(length));
  }

  @Override
  public SszListT getDefault() {
    return createFromBackingNode(createDefaultTree());
  }

  @Override
  public abstract SszListT createFromBackingNode(TreeNode node);

  protected AbstractSszVectorSchema<ElementDataT, ?> getCompatibleVectorSchema() {
    return compatibleVectorSchema;
  }

  @Override
  public long getChildGeneralizedIndex(long elementIndex) {
    return GIndexUtil.gIdxCompose(
        GIndexUtil.LEFT_CHILD_G_INDEX, super.getChildGeneralizedIndex(elementIndex));
  }

  @Override
  public int getSszFixedPartSize() {
    return 0;
  }

  @Override
  public int getSszVariablePartSize(TreeNode node) {
    int length = getLength(node);
    SszSchema<?> elementSchema = getElementSchema();
    if (elementSchema.isFixedSize()) {
      if (getSszElementBitSize() == 1) {
        // BitlistImpl is handled specially
        return length / 8 + 1;
      } else {
        return bitsCeilToBytes(length * getSszElementBitSize());
      }
    } else {
      return getCompatibleVectorSchema().getVariablePartSize(getVectorNode(node), length)
          + length * SSZ_LENGTH_SIZE;
    }
  }

  @Override
  public boolean isFixedSize() {
    return false;
  }

  @Override
  public int sszSerializeTree(TreeNode node, SszWriter writer) {
    int elementsCount = getLength(node);
    if (getElementSchema() == SszPrimitiveSchemas.BIT_SCHEMA) {
      throw new UnsupportedOperationException(
          "BitlistImpl serialization is only supported by SszBitlistSchema");
    } else {
      return getCompatibleVectorSchema()
          .sszSerializeVector(getVectorNode(node), writer, elementsCount);
    }
  }

  @Override
  public TreeNode sszDeserializeTree(SszReader reader) {
    if (getElementSchema() == SszPrimitiveSchemas.BIT_SCHEMA) {
      throw new UnsupportedOperationException(
          "BitlistImpl deserialization is only supported by SszBitlistSchema");
    } else {
      DeserializedData data = sszDeserializeVector(reader);
      return createTree(data.getDataTree(), data.getChildrenCount());
    }
  }

  @Override
  public void storeBackingNodes(
      final TreeNodeStore nodeStore,
      final int maxBranchLevelsSkipped,
      final long rootGIndex,
      final TreeNode node) {
    // Lists start with a branch with the data on the left and length on the right
    final TreeNode vectorNode = getVectorNode(node);
    final TreeNode lengthNode = node.get(GIndexUtil.RIGHT_CHILD_G_INDEX);

    // Stroe vector data (omitting empty list items at the end...)
    storeVectorNodes(
        nodeStore,
        maxBranchLevelsSkipped,
        GIndexUtil.gIdxLeftGIndex(rootGIndex),
        vectorNode,
        getLength(node));

    // Store leaf node
    nodeStore.storeLeafNode(lengthNode, GIndexUtil.gIdxRightGIndex(rootGIndex));

    // Store list root node
    nodeStore.storeBranchNode(
        node.hashTreeRoot(),
        GIndexUtil.gIdxRightGIndex(rootGIndex),
        1,
        new Bytes32[] {vectorNode.hashTreeRoot(), lengthNode.hashTreeRoot()});
  }

  private void storeVectorNodes(
      final TreeNodeStore nodeStore,
      final int maxBranchLevelsSkipped,
      final long rootGIndex,
      final TreeNode node,
      final int length) {
    if (length == 0) {
      // Nothing useful to store
      return;
    }
    final int childDepth = compatibleVectorSchema.treeDepth();
    if (childDepth == 0) {
      // Only one child so wrapper is omitted
      storeChildNode(nodeStore, maxBranchLevelsSkipped, rootGIndex, node);
      return;
    }
    final long lastUsefulGIndex = getVectorLastUsefulGIndex(rootGIndex, length);
    StoringUtil.storeNodesToDepth(
        nodeStore,
        maxBranchLevelsSkipped,
        node,
        rootGIndex,
        childDepth,
        lastUsefulGIndex,
        (targetDepthNode, targetDepthGIndex) ->
            compatibleVectorSchema.storeChildNode(
                nodeStore, maxBranchLevelsSkipped, targetDepthGIndex, targetDepthNode));
  }

  private long getVectorLastUsefulGIndex(final long rootGIndex, final int length) {
    if (length == 0) {
      return rootGIndex;
    }
    final int fullNodeCount = length / compatibleVectorSchema.getElementsPerChunk();
    int lastNodeElementCount = length % compatibleVectorSchema.getElementsPerChunk();
    final long lastUsefulChildIndex = lastNodeElementCount == 0 ? fullNodeCount - 1 : fullNodeCount;
    return GIndexUtil.gIdxChildGIndex(
        rootGIndex, lastUsefulChildIndex, compatibleVectorSchema.treeDepth());
  }

  @Override
  public TreeNode loadBackingNodes(
      final TreeNodeSource nodeSource, final Bytes32 rootHash, final long rootGIndex) {
    if (TreeUtil.ZERO_TREES_BY_ROOT.containsKey(rootHash) || rootHash.equals(Bytes32.ZERO)) {
      return getDefaultTree();
    }
    final CompressedBranchInfo branchData = nodeSource.loadBranchNode(rootHash, rootGIndex);
    checkState(
        branchData.getChildren().length == 2, "List root node must have exactly two children");
    checkState(branchData.getDepth() == 1, "List root node must have depth of 1");
    final Bytes32 vectorHash = branchData.getChildren()[0];
    final Bytes32 lengthHash = branchData.getChildren()[1];
    final int length =
        nodeSource
            .loadLeafNode(lengthHash, GIndexUtil.gIdxRightGIndex(rootGIndex))
            .getInt(0, ByteOrder.LITTLE_ENDIAN);

    final ChildLoader childLoader =
        (childNodeSource, childHash, childGIndex) ->
            LoadingUtil.loadCollectionChild(
                childNodeSource,
                childHash,
                childGIndex,
                length,
                compatibleVectorSchema.getElementsPerChunk(),
                compatibleVectorSchema.treeDepth(),
                compatibleVectorSchema.getElementSchema());
    final long vectorRootGIndex = GIndexUtil.gIdxLeftGIndex(rootGIndex);
    final long lastUsefulGIndex = getVectorLastUsefulGIndex(vectorRootGIndex, length);
    final TreeNode vectorNode =
        LoadingUtil.loadNodesToDepth(
            nodeSource,
            vectorHash,
            vectorRootGIndex,
            compatibleVectorSchema.treeDepth(),
            compatibleVectorSchema.getDefault().getBackingNode(),
            lastUsefulGIndex,
            childLoader);
    return BranchNode.create(vectorNode, toLengthNode(length));
  }

  private static TreeNode toLengthNode(int length) {
    return length == 0
        ? LeafNode.ZERO_LEAVES[8]
        : LeafNode.create(Bytes.ofUnsignedLong(length, ByteOrder.LITTLE_ENDIAN));
  }

  private static long fromLengthNode(TreeNode lengthNode) {
    assert lengthNode instanceof LeafNode;
    return ((LeafNode) lengthNode).getData().toLong(ByteOrder.LITTLE_ENDIAN);
  }

  protected static int getLength(TreeNode listNode) {
    long longLength = fromLengthNode(listNode.get(GIndexUtil.RIGHT_CHILD_G_INDEX));
    assert longLength < Integer.MAX_VALUE;
    return (int) longLength;
  }

  protected static TreeNode getVectorNode(TreeNode listNode) {
    return listNode.get(GIndexUtil.LEFT_CHILD_G_INDEX);
  }

  @Override
  public SszLengthBounds getSszLengthBounds() {
    return sszLengthBounds;
  }

  private static SszLengthBounds computeSszLengthBounds(
      final SszSchema<?> elementSchema, final long maxLength) {
    SszLengthBounds elementLengthBounds = elementSchema.getSszLengthBounds();
    // if elements are of dynamic size the offset size should be added for every element
    SszLengthBounds elementAndOffsetLengthBounds =
        elementLengthBounds.addBytes(elementSchema.isFixedSize() ? 0 : SSZ_LENGTH_SIZE);
    SszLengthBounds maxLenBounds =
        SszLengthBounds.ofBits(0, elementAndOffsetLengthBounds.mul(maxLength).getMaxBits());
    // adding 1 boundary bit for BitlistImpl
    return maxLenBounds
        .addBits(elementSchema == SszPrimitiveSchemas.BIT_SCHEMA ? 1 : 0)
        .ceilToBytes();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof AbstractSszListSchema)) {
      return false;
    }
    AbstractSszListSchema<?, ?> that = (AbstractSszListSchema<?, ?>) o;
    return getElementSchema().equals(that.getElementSchema())
        && getMaxLength() == that.getMaxLength();
  }

  @Override
  public int hashCode() {
    return super.hashCode();
  }

  @Override
  public String toString() {
    return "List[" + getElementSchema() + ", " + getMaxLength() + "]";
  }
}
