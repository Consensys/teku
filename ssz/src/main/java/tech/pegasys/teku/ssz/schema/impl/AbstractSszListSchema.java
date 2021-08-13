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
import static tech.pegasys.teku.ssz.schema.SszCompositeSchema.divCeil;
import static tech.pegasys.teku.ssz.schema.SszCompositeSchema.storeNodesAtDepth;
import static tech.pegasys.teku.ssz.tree.TreeUtil.bitsCeilToBytes;

import java.nio.ByteOrder;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ssz.SszData;
import tech.pegasys.teku.ssz.SszList;
import tech.pegasys.teku.ssz.schema.SszListSchema;
import tech.pegasys.teku.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.ssz.schema.SszSchema;
import tech.pegasys.teku.ssz.schema.SszSchemaHints;
import tech.pegasys.teku.ssz.schema.SszSchemaHints.SszSuperNodeHint;
import tech.pegasys.teku.ssz.sos.SszLengthBounds;
import tech.pegasys.teku.ssz.sos.SszReader;
import tech.pegasys.teku.ssz.sos.SszWriter;
import tech.pegasys.teku.ssz.tree.BranchNode;
import tech.pegasys.teku.ssz.tree.GIndexUtil;
import tech.pegasys.teku.ssz.tree.LazyBranchNode;
import tech.pegasys.teku.ssz.tree.LeafDataNode;
import tech.pegasys.teku.ssz.tree.LeafNode;
import tech.pegasys.teku.ssz.tree.TreeNode;

public abstract class AbstractSszListSchema<
        ElementDataT extends SszData, SszListT extends SszList<ElementDataT>>
    extends AbstractSszCollectionSchema<ElementDataT, SszListT>
    implements SszListSchema<ElementDataT, SszListT> {
  private final SszVectorSchemaImpl<ElementDataT> compatibleVectorSchema;

  protected AbstractSszListSchema(SszSchema<ElementDataT> elementSchema, long maxLength) {
    this(elementSchema, maxLength, SszSchemaHints.none());
  }

  protected AbstractSszListSchema(
      SszSchema<ElementDataT> elementSchema, long maxLength, SszSchemaHints hints) {
    super(maxLength, elementSchema, hints);
    this.compatibleVectorSchema =
        new SszVectorSchemaImpl<>(getElementSchema(), getMaxLength(), true, getHints());
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

  @Override
  public TreeNode loadBackingNodes(final BackingNodeSource source, final Bytes32 rootHash) {
    // Lists start with a branch with the data on the left and length on the right
    final CompressedBranchInfo branchData = source.getBranchData(rootHash);
    checkState(
        branchData.getChildren().length == 2, "List root node must have exactly two children");
    checkState(branchData.getDepth() == 1, "List root node must have depth of 1");
    final Bytes32 vectorHash = branchData.getChildren()[0];
    final Bytes32 lengthHash = branchData.getChildren()[1];
    final int length = source.getLeafData(lengthHash).getInt(0, ByteOrder.LITTLE_ENDIAN);
    return new LazyBranchNode(
        rootHash,
        vectorHash,
        lengthHash,
        () -> getCompatibleVectorSchema().loadBackingNodes(source, vectorHash, length),
        () -> {
          return toLengthNode(length);
          //          if (lengthHash.isZero()) {
          //            return toLengthNode(0);
          //          } else {
          //            return LeafNode.create(source.getLeafData(lengthHash));
          //          }
        });
  }

  protected AbstractSszVectorSchema<ElementDataT, ?> getCompatibleVectorSchema() {
    return compatibleVectorSchema;
  }

  @Override
  public void storeBackingNodes(final TreeNode backingNode, final BackingNodeStore store) {
    // Lists start with a branch with the data on the left and length on the right
    final TreeNode vectorNode = getVectorNode(backingNode);
    final TreeNode lengthNode = backingNode.get(GIndexUtil.RIGHT_CHILD_G_INDEX);

    // Then store length node
    store.storeLeafNode((LeafDataNode) lengthNode);

    // And store vector data (omitting empty list items at the end...)
    storeVectorNodes(store, vectorNode, getLength(backingNode));

    // Store list root not
    store.storeCompressedBranch(
        backingNode.hashTreeRoot(),
        1,
        new Bytes32[] {vectorNode.hashTreeRoot(), lengthNode.hashTreeRoot()});
  }

  private void storeVectorNodes(
      final BackingNodeStore store, final TreeNode vectorNode, final int length) {
    final AbstractSszVectorSchema<ElementDataT, ?> vectorSchema = getCompatibleVectorSchema();
    final Optional<SszSuperNodeHint> sszSuperNodeHint =
        vectorSchema.getHints().getHint(SszSuperNodeHint.class);
    if (sszSuperNodeHint.isPresent()) {
      final SszSuperNodeHint hint = sszSuperNodeHint.get();
      final int depth = treeDepth() - hint.getDepth();
      final int elementsPerSuperNode = 1 << hint.getDepth();
      storeNodesAtDepth(
          vectorNode,
          store,
          depth,
          1L << depth,
          divCeil(length, elementsPerSuperNode),
          superNode -> store.storeLeafNode((LeafDataNode) superNode));
    } else {
      final int depth = vectorSchema.treeDepth();
      storeNodesAtDepth(
          vectorNode,
          store,
          depth,
          getElementsPerChunk() * (1L << depth),
          length,
          node -> vectorSchema.getElementSchema().storeBackingNodes(node, store));
    }
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
    SszLengthBounds elementLengthBounds = getElementSchema().getSszLengthBounds();
    // if elements are of dynamic size the offset size should be added for every element
    SszLengthBounds elementAndOffsetLengthBounds =
        elementLengthBounds.addBytes(getElementSchema().isFixedSize() ? 0 : SSZ_LENGTH_SIZE);
    SszLengthBounds maxLenBounds =
        SszLengthBounds.ofBits(0, elementAndOffsetLengthBounds.mul(getMaxLength()).getMaxBits());
    // adding 1 boundary bit for BitlistImpl
    return maxLenBounds
        .addBits(getElementSchema() == SszPrimitiveSchemas.BIT_SCHEMA ? 1 : 0)
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
