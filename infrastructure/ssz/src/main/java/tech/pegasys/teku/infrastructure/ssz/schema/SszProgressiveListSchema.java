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

import static tech.pegasys.teku.infrastructure.ssz.schema.ListSchemaUtil.getLength;
import static tech.pegasys.teku.infrastructure.ssz.schema.ListSchemaUtil.getVectorNode;
import static tech.pegasys.teku.infrastructure.ssz.schema.ListSchemaUtil.toLengthNode;
import static tech.pegasys.teku.infrastructure.ssz.tree.TreeUtil.bitsCeilToBytes;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.json.types.DeserializableArrayTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.impl.SszProgressiveListImpl;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszPrimitiveSchema;
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
 * Schema for ProgressiveList (EIP-7916) — a variable-length homogeneous collection with no max
 * capacity that uses a progressive merkle tree for stable generalized indices.
 *
 * <p>Tree structure: BranchNode(progressiveDataTree, lengthNode)
 *
 * <p>The progressive data tree is a right-leaning asymmetric tree where subtree capacities grow by
 * 4x per level.
 */
public class SszProgressiveListSchema<ElementDataT extends SszData>
    implements SszListSchema<ElementDataT, SszList<ElementDataT>> {

  private final SszSchema<ElementDataT> elementSchema;
  private final TreeNode defaultTree;
  private final int elementsPerChunk;
  private final DeserializableTypeDefinition<SszList<ElementDataT>> jsonTypeDefinition;

  public SszProgressiveListSchema(final SszSchema<ElementDataT> elementSchema) {
    this.elementSchema = elementSchema;
    this.elementsPerChunk = computeElementsPerChunk(elementSchema);
    this.defaultTree =
        BranchNode.create(ProgressiveTreeUtil.createProgressiveTree(List.of()), toLengthNode(0));
    this.jsonTypeDefinition =
        new DeserializableArrayTypeDefinition<>(
            elementSchema.getJsonTypeDefinition(), this::createFromElements);
  }

  public static <T extends SszData> SszProgressiveListSchema<T> create(
      final SszSchema<T> elementSchema) {
    return new SszProgressiveListSchema<>(elementSchema);
  }

  private static int computeElementsPerChunk(final SszSchema<?> schema) {
    if (schema.isPrimitive()) {
      return 256 / ((SszPrimitiveSchema<?, ?>) schema).getBitsSize();
    }
    return 1;
  }

  // ===== SszCollectionSchema =====

  @Override
  public SszSchema<ElementDataT> getElementSchema() {
    return elementSchema;
  }

  @Override
  public TreeNode createTreeFromElements(final List<? extends ElementDataT> elements) {
    final List<TreeNode> chunks = packElementsToChunks(elements);
    final TreeNode progressiveTree = ProgressiveTreeUtil.createProgressiveTree(chunks);
    return BranchNode.create(progressiveTree, toLengthNode(elements.size()));
  }

  // ===== SszCompositeSchema =====

  @Override
  public long getMaxLength() {
    return Long.MAX_VALUE;
  }

  @Override
  public SszSchema<?> getChildSchema(final int index) {
    return elementSchema;
  }

  @Override
  public int getElementsPerChunk() {
    return elementsPerChunk;
  }

  @Override
  public long maxChunks() {
    throw new UnsupportedOperationException("Progressive lists don't have a fixed maxChunks");
  }

  @Override
  public int treeDepth() {
    throw new UnsupportedOperationException("Progressive lists don't have a fixed tree depth");
  }

  @Override
  public long treeWidth() {
    throw new UnsupportedOperationException("Progressive lists don't have a fixed tree width");
  }

  @Override
  public long getChildGeneralizedIndex(final long elementIndex) {
    return GIndexUtil.gIdxCompose(
        GIndexUtil.LEFT_CHILD_G_INDEX,
        ProgressiveTreeUtil.getElementGeneralizedIndex(elementIndex));
  }

  @Override
  public void storeChildNode(
      final TreeNodeStore nodeStore,
      final int maxBranchLevelsSkipped,
      final long gIndex,
      final TreeNode node) {
    elementSchema.storeBackingNodes(nodeStore, maxBranchLevelsSkipped, gIndex, node);
  }

  // ===== SszSchema =====

  @Override
  public TreeNode getDefaultTree() {
    return defaultTree;
  }

  @Override
  public SszList<ElementDataT> createFromBackingNode(final TreeNode node) {
    return new SszProgressiveListImpl<>(this, node);
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
    final int length = getLength(node);
    if (elementSchema.isFixedSize()) {
      return (int) bitsCeilToBytes((long) length * getSszElementBitSize());
    } else {
      int size = 0;
      for (int i = 0; i < length; i++) {
        size += elementSchema.getSszSize(node.get(getChildGeneralizedIndex(i)));
        size += SszType.SSZ_LENGTH_SIZE;
      }
      return size;
    }
  }

  @Override
  public int sszSerializeTree(final TreeNode node, final SszWriter writer) {
    final int elementsCount = getLength(node);
    if (elementsCount == 0) {
      return 0;
    }
    final TreeNode dataNode = getVectorNode(node);
    if (elementSchema.isFixedSize()) {
      return sszSerializeFixed(dataNode, writer, elementsCount);
    } else {
      return sszSerializeVariable(dataNode, writer, elementsCount);
    }
  }

  private int sszSerializeFixed(
      final TreeNode dataNode, final SszWriter writer, final int elementsCount) {
    if (elementSchema instanceof AbstractSszPrimitiveSchema) {
      // Primitive packing: multiple values per 32-byte leaf chunk
      final int chunksCount = getChunks(elementsCount);
      int bytesCnt = 0;
      for (int c = 0; c < chunksCount; c++) {
        final long gIdx = ProgressiveTreeUtil.getElementGeneralizedIndex(c);
        final LeafNode leafNode = (LeafNode) dataNode.get(gIdx);
        final Bytes data = leafNode.getData();
        writer.write(data);
        bytesCnt += data.size();
      }
      return bytesCnt;
    } else {
      // Fixed-size composite elements: one per chunk
      int bytesCnt = 0;
      for (int i = 0; i < elementsCount; i++) {
        final long gIdx = ProgressiveTreeUtil.getElementGeneralizedIndex(i);
        final TreeNode childSubtree = dataNode.get(gIdx);
        bytesCnt += elementSchema.sszSerializeTree(childSubtree, writer);
      }
      return bytesCnt;
    }
  }

  private int sszSerializeVariable(
      final TreeNode dataNode, final SszWriter writer, final int elementsCount) {
    int variableOffset = SszType.SSZ_LENGTH_SIZE * elementsCount;
    for (int i = 0; i < elementsCount; i++) {
      final long gIdx = ProgressiveTreeUtil.getElementGeneralizedIndex(i);
      final TreeNode childSubtree = dataNode.get(gIdx);
      final int childSize = elementSchema.getSszSize(childSubtree);
      writer.write(SszType.sszLengthToBytes(variableOffset));
      variableOffset += childSize;
    }
    for (int i = 0; i < elementsCount; i++) {
      final long gIdx = ProgressiveTreeUtil.getElementGeneralizedIndex(i);
      final TreeNode childSubtree = dataNode.get(gIdx);
      elementSchema.sszSerializeTree(childSubtree, writer);
    }
    return variableOffset;
  }

  @Override
  public TreeNode sszDeserializeTree(final SszReader reader) {
    if (elementSchema.isFixedSize()) {
      return sszDeserializeFixed(reader);
    } else {
      return sszDeserializeVariable(reader);
    }
  }

  private TreeNode sszDeserializeFixed(final SszReader reader) {
    final int bytesSize = reader.getAvailableBytes();
    final int elementBitSize = getSszElementBitSize();
    if (elementBitSize >= 8) {
      if (bytesSize * 8L % elementBitSize != 0) {
        throw new SszDeserializeException(
            "SSZ sequence length is not multiple of fixed element size");
      }
    }

    if (elementSchema instanceof AbstractSszPrimitiveSchema) {
      // Primitive packing: multiple values per 32-byte leaf
      final int bytesPerElement = elementBitSize / 8;
      int bytesRemain = bytesSize;
      final List<LeafNode> childNodes = new ArrayList<>(bytesRemain / LeafNode.MAX_BYTE_SIZE + 1);
      while (bytesRemain > 0) {
        final int toRead = Math.min(bytesRemain, LeafNode.MAX_BYTE_SIZE);
        bytesRemain -= toRead;
        final Bytes bytes = reader.read(toRead);
        // Validate each element within the chunk (e.g., booleans must be 0 or 1)
        for (int offset = 0; offset < bytes.size(); offset += bytesPerElement) {
          elementSchema.sszDeserialize(bytes.slice(offset, bytesPerElement));
        }
        childNodes.add(LeafNode.create(bytes));
      }
      final int elementsCount = (int) (bytesSize * 8L / elementBitSize);
      final TreeNode progressiveTree = ProgressiveTreeUtil.createProgressiveTree(childNodes);
      return BranchNode.create(progressiveTree, toLengthNode(elementsCount));
    } else {
      // Fixed-size composite elements: one per chunk
      final int elementsCount = bytesSize / elementSchema.getSszFixedPartSize();
      final List<TreeNode> childNodes = new ArrayList<>();
      for (int i = 0; i < elementsCount; i++) {
        try (SszReader sszReader = reader.slice(elementSchema.getSszFixedPartSize())) {
          childNodes.add(elementSchema.sszDeserializeTree(sszReader));
        }
      }
      final TreeNode progressiveTree = ProgressiveTreeUtil.createProgressiveTree(childNodes);
      return BranchNode.create(progressiveTree, toLengthNode(elementsCount));
    }
  }

  private TreeNode sszDeserializeVariable(final SszReader reader) {
    final int endOffset = reader.getAvailableBytes();
    final List<TreeNode> childNodes = new ArrayList<>();
    if (endOffset > 0) {
      final int firstElementOffset = SszType.sszBytesToLength(reader.read(SszType.SSZ_LENGTH_SIZE));
      if (firstElementOffset % SszType.SSZ_LENGTH_SIZE != 0) {
        throw new SszDeserializeException("Invalid first element offset");
      }
      if (firstElementOffset > endOffset) {
        throw new SszDeserializeException(
            "First element offset exceeds available data: "
                + firstElementOffset
                + " > "
                + endOffset);
      }
      final int elementsCount = firstElementOffset / SszType.SSZ_LENGTH_SIZE;
      final List<Integer> elementOffsets = new ArrayList<>(elementsCount + 1);
      elementOffsets.add(firstElementOffset);
      for (int i = 1; i < elementsCount; i++) {
        elementOffsets.add(SszType.sszBytesToLength(reader.read(SszType.SSZ_LENGTH_SIZE)));
      }
      elementOffsets.add(endOffset);

      for (int i = 0; i < elementsCount; i++) {
        final int elementSize = elementOffsets.get(i + 1) - elementOffsets.get(i);
        if (elementSize < 0) {
          throw new SszDeserializeException("Invalid SSZ: wrong child offsets");
        }
        try (SszReader sszReader = reader.slice(elementSize)) {
          childNodes.add(elementSchema.sszDeserializeTree(sszReader));
        }
      }
    }
    final TreeNode progressiveTree = ProgressiveTreeUtil.createProgressiveTree(childNodes);
    return BranchNode.create(progressiveTree, toLengthNode(childNodes.size()));
  }

  @Override
  public SszLengthBounds getSszLengthBounds() {
    // Progressive lists have no max capacity — use Long.MAX_VALUE bits directly
    // to avoid overflow when converting from bytes to bits
    return SszLengthBounds.ofBits(0, Long.MAX_VALUE);
  }

  @Override
  public DeserializableTypeDefinition<SszList<ElementDataT>> getJsonTypeDefinition() {
    return jsonTypeDefinition;
  }

  @Override
  public void storeBackingNodes(
      final TreeNodeStore nodeStore,
      final int maxBranchLevelsSkipped,
      final long rootGIndex,
      final TreeNode node) {
    throw new UnsupportedOperationException(
        "Store/load backing nodes not yet supported for progressive lists");
  }

  @Override
  public TreeNode loadBackingNodes(
      final TreeNodeSource nodeSource, final Bytes32 rootHash, final long rootGIndex) {
    throw new UnsupportedOperationException(
        "Store/load backing nodes not yet supported for progressive lists");
  }

  // ===== Internal helpers =====

  private int getSszElementBitSize() {
    if (elementSchema.isPrimitive()) {
      return ((SszPrimitiveSchema<?, ?>) elementSchema).getBitsSize();
    }
    return elementSchema.getSszFixedPartSize() * 8;
  }

  private List<TreeNode> packElementsToChunks(final List<? extends ElementDataT> elements) {
    if (elementSchema.isPrimitive()) {
      // Pack primitive values into 32-byte chunks
      final int bitsPerElement = ((SszPrimitiveSchema<?, ?>) elementSchema).getBitsSize();
      final int elemPerChunk = 256 / bitsPerElement;
      final int bytesPerElement = bitsPerElement / 8;
      final List<TreeNode> chunks = new ArrayList<>();
      for (int i = 0; i < elements.size(); i += elemPerChunk) {
        final int count = Math.min(elemPerChunk, elements.size() - i);
        final byte[] chunkData = new byte[count * bytesPerElement];
        for (int j = 0; j < count; j++) {
          final Bytes elemBytes = elements.get(i + j).getBackingNode().hashTreeRoot();
          System.arraycopy(
              elemBytes.toArrayUnsafe(), 0, chunkData, j * bytesPerElement, bytesPerElement);
        }
        chunks.add(LeafNode.create(Bytes.wrap(chunkData)));
      }
      return chunks;
    } else {
      return elements.stream().map(SszData::getBackingNode).collect(Collectors.toList());
    }
  }

  @Override
  public Optional<String> getName() {
    return Optional.of("ProgressiveList[" + elementSchema + "]");
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof SszProgressiveListSchema<?> that)) {
      return false;
    }
    return elementSchema.equals(that.elementSchema);
  }

  @Override
  public int hashCode() {
    return Objects.hash(elementSchema);
  }

  @Override
  public String toString() {
    return "ProgressiveList[" + elementSchema + "]";
  }
}
