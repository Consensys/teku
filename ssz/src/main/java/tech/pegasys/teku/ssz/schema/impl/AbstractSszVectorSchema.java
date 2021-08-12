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

import static java.util.Collections.emptyList;
import static tech.pegasys.teku.ssz.tree.TreeUtil.bitsCeilToBytes;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ssz.SszData;
import tech.pegasys.teku.ssz.SszVector;
import tech.pegasys.teku.ssz.schema.SszCompositeSchema;
import tech.pegasys.teku.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.ssz.schema.SszSchema;
import tech.pegasys.teku.ssz.schema.SszSchemaHints;
import tech.pegasys.teku.ssz.schema.SszSchemaHints.SszSuperNodeHint;
import tech.pegasys.teku.ssz.schema.SszVectorSchema;
import tech.pegasys.teku.ssz.sos.SszDeserializeException;
import tech.pegasys.teku.ssz.sos.SszLengthBounds;
import tech.pegasys.teku.ssz.sos.SszReader;
import tech.pegasys.teku.ssz.sos.SszWriter;
import tech.pegasys.teku.ssz.tree.GIndexUtil;
import tech.pegasys.teku.ssz.tree.LazyBranchNode;
import tech.pegasys.teku.ssz.tree.LeafNode;
import tech.pegasys.teku.ssz.tree.SszSuperNode;
import tech.pegasys.teku.ssz.tree.TreeNode;
import tech.pegasys.teku.ssz.tree.TreeUtil;

public abstract class AbstractSszVectorSchema<
        SszElementT extends SszData, SszVectorT extends SszVector<SszElementT>>
    extends AbstractSszCollectionSchema<SszElementT, SszVectorT>
    implements SszVectorSchema<SszElementT, SszVectorT> {

  private final boolean isListBacking;
  private final int fixedPartSize;

  protected AbstractSszVectorSchema(SszSchema<SszElementT> elementType, long vectorLength) {
    this(elementType, vectorLength, false);
  }

  protected AbstractSszVectorSchema(
      SszSchema<SszElementT> elementType, long vectorLength, boolean isListBacking) {
    this(elementType, vectorLength, isListBacking, SszSchemaHints.none());
  }

  protected AbstractSszVectorSchema(
      SszSchema<SszElementT> elementSchema,
      long vectorLength,
      boolean isListBacking,
      SszSchemaHints hints) {
    super(vectorLength, elementSchema, hints);
    this.isListBacking = isListBacking;
    this.fixedPartSize = calcSszFixedPartSize();
  }

  @Override
  public SszVectorT getDefault() {
    return createFromBackingNode(getDefaultTree());
  }

  @Override
  protected TreeNode createDefaultTree() {
    if (isListBacking) {
      Optional<SszSuperNodeHint> sszSuperNodeHint = getHints().getHint(SszSuperNodeHint.class);
      if (sszSuperNodeHint.isPresent()) {
        int superNodeDepth = sszSuperNodeHint.get().getDepth();
        SszSuperNode defaultSuperSszNode =
            new SszSuperNode(superNodeDepth, elementSszSupernodeTemplate.get(), Bytes.EMPTY);
        int binaryDepth = treeDepth() - superNodeDepth;
        return TreeUtil.createTree(emptyList(), defaultSuperSszNode, binaryDepth);
      } else {
        return TreeUtil.createDefaultTree(maxChunks(), LeafNode.EMPTY_LEAF);
      }
    } else if (getElementsPerChunk() == 1) {
      return TreeUtil.createDefaultTree(maxChunks(), getElementSchema().getDefaultTree());
    } else {
      // packed vector
      int fullZeroNodesCount = getLength() / getElementsPerChunk();
      int lastNodeElementCount = getLength() % getElementsPerChunk();
      int lastNodeSizeBytes = bitsCeilToBytes(lastNodeElementCount * getSszElementBitSize());
      Stream<TreeNode> fullZeroNodes =
          Stream.<TreeNode>generate(() -> LeafNode.ZERO_LEAVES[32]).limit(fullZeroNodesCount);
      Stream<TreeNode> lastZeroNode =
          lastNodeSizeBytes > 0
              ? Stream.of(LeafNode.ZERO_LEAVES[lastNodeSizeBytes])
              : Stream.empty();
      return TreeUtil.createTree(
          Stream.concat(fullZeroNodes, lastZeroNode).collect(Collectors.toList()));
    }
  }

  @Override
  public abstract SszVectorT createFromBackingNode(TreeNode node);

  @Override
  public TreeNode loadBackingNodes(final BackingNodeSource source, final Bytes32 rootHash) {
    if (isListBacking) {
      final Optional<SszSuperNodeHint> sszSuperNodeHint =
          getHints().getHint(SszSuperNodeHint.class);
      if (sszSuperNodeHint.isPresent()) {
        int superNodeDepth = sszSuperNodeHint.get().getDepth();

        int binaryDepth = treeDepth() - superNodeDepth;
        return loadBackingNodes(
            source, rootHash, GIndexUtil.SELF_G_INDEX, binaryDepth, OptionalInt.of(superNodeDepth));
      }
    }
    return loadBackingNodes(
        source, rootHash, GIndexUtil.SELF_G_INDEX, treeDepth(), OptionalInt.empty());
  }

  private TreeNode loadBackingNodes(
      final BackingNodeSource source,
      final Bytes32 rootHash,
      final long generalizedIndex,
      final int depth,
      final OptionalInt superNodeDepth) {
    if (TreeUtil.ZERO_TREES_BY_ROOT.containsKey(rootHash)) {
      return getDefaultTree().get(generalizedIndex);
    }
    if (depth == 0) {
      // Load leaf data
      if (superNodeDepth.isPresent()) {
        return new SszSuperNode(
            superNodeDepth.getAsInt(),
            elementSszSupernodeTemplate.get(),
            source.getLeafData(rootHash));
      } else if (getElementSchema().isPrimitive()) {
        return LeafNode.create(source.getLeafData(rootHash));
      } else {
        return getElementSchema().loadBackingNodes(source, rootHash);
      }
    }
    final Pair<Bytes32, Bytes32> branch = source.getBranchData(rootHash);
    return new LazyBranchNode(
        rootHash,
        branch.getLeft(),
        branch.getRight(),
        () ->
            loadBackingNodes(
                source,
                branch.getLeft(),
                GIndexUtil.gIdxLeftGIndex(generalizedIndex),
                depth - 1,
                superNodeDepth),
        () ->
            loadBackingNodes(
                source,
                branch.getRight(),
                GIndexUtil.gIdxRightGIndex(generalizedIndex),
                depth - 1,
                superNodeDepth));
  }

  @Override
  public void storeBackingNodes(final TreeNode backingNode, final BackingNodeStore store) {
    final int depth = treeDepth();
    SszCompositeSchema.storeNonZeroBranchNodes(
        backingNode,
        store,
        Math.max(0, depth - MAX_DEPTH_COMPRESSION),
        nodeForCompression -> {
          final int childDepth = Math.min(depth, MAX_DEPTH_COMPRESSION);
          final int chunkCount = 1 << childDepth;
          final List<Bytes32> childHashes = new ArrayList<>();
          for (int childIndex = 0; childIndex < chunkCount; childIndex++) {
            final long childGIndex =
                GIndexUtil.gIdxChildGIndex(GIndexUtil.SELF_G_INDEX, childIndex, childDepth);
            final TreeNode childNode = nodeForCompression.get(childGIndex);
            if (!TreeUtil.ZERO_TREES_BY_ROOT.containsKey(childNode.hashTreeRoot())) {
              childHashes.add(childNode.hashTreeRoot());
              getElementSchema().storeBackingNodes(childNode, store);
            }
          }
          store.storeCompressedBranch(
              backingNode.hashTreeRoot(), depth, childHashes.toArray(new Bytes32[0]));
        });
  }

  @Override
  public int getLength() {
    long maxLength = getMaxLength();
    if (maxLength > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("Vector size too large: " + maxLength);
    }
    return (int) maxLength;
  }

  public int getChunksCount() {
    long maxChunks = maxChunks();
    if (maxChunks > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("Vector size too large: " + maxChunks);
    }
    return (int) maxChunks;
  }

  @Override
  public boolean isFixedSize() {
    return getElementSchema().isFixedSize();
  }

  @Override
  public int getSszVariablePartSize(TreeNode node) {
    return getVariablePartSize(node, getLength());
  }

  @Override
  public final int getSszFixedPartSize() {
    return fixedPartSize;
  }

  private int calcSszFixedPartSize() {
    if (isListBacking) {
      return 0;
    } else {
      int bitsPerChild = isFixedSize() ? getSszElementBitSize() : SSZ_LENGTH_SIZE * 8;
      return bitsCeilToBytes(getLength() * bitsPerChild);
    }
  }

  @Override
  public int sszSerializeTree(TreeNode node, SszWriter writer) {
    return sszSerializeVector(node, writer, getLength());
  }

  @Override
  public TreeNode sszDeserializeTree(SszReader reader) {
    if (getElementSchema() == SszPrimitiveSchemas.BIT_SCHEMA) {
      throw new UnsupportedOperationException(
          "Bitvector deserialization is only supported by SszBitvectorSchema");
    }

    DeserializedData data = sszDeserializeVector(reader);
    if (data.getChildrenCount() != getLength()) {
      throw new SszDeserializeException("Invalid Vector ssz");
    }
    return data.getDataTree();
  }

  @Override
  public SszLengthBounds getSszLengthBounds() {
    return getElementSchema()
        .getSszLengthBounds()
        // if elements are of dynamic size the offset size should be added for every element
        .addBytes(getElementSchema().isFixedSize() ? 0 : SSZ_LENGTH_SIZE)
        .mul(getLength())
        .ceilToBytes();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof AbstractSszVectorSchema)) {
      return false;
    }
    AbstractSszVectorSchema<?, ?> that = (AbstractSszVectorSchema<?, ?>) o;
    return getElementSchema().equals(that.getElementSchema())
        && getMaxLength() == that.getMaxLength();
  }

  @Override
  public int hashCode() {
    return super.hashCode();
  }

  @Override
  public String toString() {
    return "Vector[" + getElementSchema() + ", " + getLength() + "]";
  }
}
