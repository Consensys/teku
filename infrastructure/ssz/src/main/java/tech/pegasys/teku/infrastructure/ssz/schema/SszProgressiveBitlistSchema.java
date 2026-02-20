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
import static tech.pegasys.teku.infrastructure.ssz.schema.ListSchemaUtil.getLength;
import static tech.pegasys.teku.infrastructure.ssz.schema.ListSchemaUtil.getVectorNode;
import static tech.pegasys.teku.infrastructure.ssz.schema.ListSchemaUtil.toLengthNode;
import static tech.pegasys.teku.infrastructure.ssz.tree.TreeUtil.bitsCeilToBytes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.ssz.collections.impl.SszProgressiveBitlistImpl;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBit;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitlistSchema;
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
 * Schema for ProgressiveBitlist (EIP-7916) — a variable-length bit collection with no max capacity
 * that uses a progressive merkle tree for stable generalized indices.
 *
 * <p>Tree structure: BranchNode(progressiveDataTree, lengthNode)
 *
 * <p>Serialization is identical to regular Bitlist (bits packed + trailing boundary bit).
 * Merkleization uses the progressive tree structure with 256 bits per chunk.
 */
public class SszProgressiveBitlistSchema implements SszBitlistSchema<SszBitlist> {

  private static final int ELEMENTS_PER_CHUNK = 256;

  private final TreeNode defaultTree;
  private final DeserializableTypeDefinition<SszBitlist> jsonTypeDefinition;

  public SszProgressiveBitlistSchema() {
    this.defaultTree =
        BranchNode.create(ProgressiveTreeUtil.createProgressiveTree(List.of()), toLengthNode(0));
    this.jsonTypeDefinition =
        SszPrimitiveTypeDefinitions.sszSerializedType(this, "SSZ hexadecimal");
  }

  // ===== SszBitlistSchema =====

  @Override
  public SszBitlist ofBits(final int size, final int... setBitIndices) {
    return SszProgressiveBitlistImpl.ofBits(this, size, setBitIndices);
  }

  @Override
  public SszBitlist wrapBitSet(final int size, final BitSet bitSet) {
    return SszProgressiveBitlistImpl.wrapBitSet(this, size, bitSet);
  }

  @Override
  public SszBitlist fromBytes(final Bytes bytes) {
    checkArgument(bytes != null, "Input bytes cannot be null");
    try (final SszReader reader = SszReader.fromBytes(bytes)) {
      final TreeNode node = sszDeserializeTree(reader);
      return createFromBackingNode(node);
    }
  }

  @Override
  public SszBitlist createFromElements(final List<? extends SszBit> elements) {
    return ofBits(
        elements.size(),
        IntStream.range(0, elements.size()).filter(i -> elements.get(i).get()).toArray());
  }

  // ===== SszCollectionSchema =====

  @Override
  public SszSchema<SszBit> getElementSchema() {
    return SszPrimitiveSchemas.BIT_SCHEMA;
  }

  @Override
  public int getElementsPerChunk() {
    return ELEMENTS_PER_CHUNK;
  }

  // ===== SszCompositeSchema =====

  @Override
  public long getMaxLength() {
    return Long.MAX_VALUE;
  }

  @Override
  public SszSchema<?> getChildSchema(final int index) {
    return SszPrimitiveSchemas.BIT_SCHEMA;
  }

  @Override
  public int treeDepth() {
    throw new UnsupportedOperationException("Progressive bitlists don't have a fixed tree depth");
  }

  @Override
  public long treeWidth() {
    throw new UnsupportedOperationException("Progressive bitlists don't have a fixed tree width");
  }

  @Override
  public long maxChunks() {
    throw new UnsupportedOperationException("Progressive bitlists don't have a fixed maxChunks");
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
    getElementSchema().storeBackingNodes(nodeStore, maxBranchLevelsSkipped, gIndex, node);
  }

  // ===== SszSchema =====

  @Override
  public TreeNode getDefaultTree() {
    return defaultTree;
  }

  @Override
  public SszBitlist createFromBackingNode(final TreeNode node) {
    return new SszProgressiveBitlistImpl(this, node);
  }

  @Override
  public boolean isPrimitive() {
    return false;
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
    int bitCount = getLength(node);
    // bits packed + boundary bit byte
    return bitsCeilToBytes(bitCount) + (bitCount % 8 == 0 ? 1 : 0);
  }

  @Override
  public int sszSerializeTree(final TreeNode node, final SszWriter writer) {
    final int bitCount = getLength(node);
    if (bitCount == 0) {
      // Empty bitlist: just the boundary bit (0x01)
      writer.write(new byte[] {1});
      return 1;
    }

    final TreeNode dataNode = getVectorNode(node);
    final int chunksCount = chunksForBitCount(bitCount);
    final int totalDataBytes = bitsCeilToBytes(bitCount);

    // Collect all chunk data
    final byte[] allData = new byte[totalDataBytes];
    int offset = 0;
    for (int c = 0; c < chunksCount; c++) {
      final long gIdx = ProgressiveTreeUtil.getElementGeneralizedIndex(c);
      final TreeNode chunkNode = dataNode.get(gIdx);
      if (chunkNode instanceof LeafNode leafNode) {
        final Bytes data = leafNode.getData();
        final int toCopy = Math.min(data.size(), totalDataBytes - offset);
        System.arraycopy(data.toArrayUnsafe(), 0, allData, offset, toCopy);
        offset += LeafNode.MAX_BYTE_SIZE;
      }
    }

    // Add boundary bit
    final int bitIdx = bitCount % 8;
    if (bitIdx == 0) {
      writer.write(allData, 0, totalDataBytes);
      writer.write(new byte[] {1});
      return totalDataBytes + 1;
    } else {
      allData[totalDataBytes - 1] |= (byte) (1 << bitIdx);
      writer.write(allData, 0, totalDataBytes);
      return totalDataBytes;
    }
  }

  @Override
  public TreeNode sszDeserializeTree(final SszReader reader) {
    final int availableBytes = reader.getAvailableBytes();
    if (availableBytes == 0) {
      throw new SszDeserializeException("Empty progressive bitlist SSZ data");
    }
    final Bytes bytes = reader.read(availableBytes);

    final int length = sszGetLengthAndValidate(bytes);
    final Bytes treeBytes = sszTruncateLeadingBit(bytes, length);

    // Split into 32-byte leaf chunks
    final List<LeafNode> chunks = new ArrayList<>();
    int off = 0;
    final int size = treeBytes.size();
    while (off < size) {
      final int chunkSize = Math.min(LeafNode.MAX_BYTE_SIZE, size - off);
      chunks.add(LeafNode.create(treeBytes.slice(off, chunkSize)));
      off += LeafNode.MAX_BYTE_SIZE;
    }

    final TreeNode progressiveTree = ProgressiveTreeUtil.createProgressiveTree(chunks);
    return BranchNode.create(progressiveTree, toLengthNode(length));
  }

  @Override
  public SszLengthBounds getSszLengthBounds() {
    return SszLengthBounds.ofBytes(1, Long.MAX_VALUE / 2);
  }

  @Override
  public DeserializableTypeDefinition<SszBitlist> getJsonTypeDefinition() {
    return jsonTypeDefinition;
  }

  @Override
  public void storeBackingNodes(
      final TreeNodeStore nodeStore,
      final int maxBranchLevelsSkipped,
      final long rootGIndex,
      final TreeNode node) {
    throw new UnsupportedOperationException(
        "Store/load backing nodes not yet supported for progressive bitlists");
  }

  @Override
  public TreeNode loadBackingNodes(
      final TreeNodeSource nodeSource, final Bytes32 rootHash, final long rootGIndex) {
    throw new UnsupportedOperationException(
        "Store/load backing nodes not yet supported for progressive bitlists");
  }

  // ===== Internal helpers =====

  public TreeNode createTreeFromBitData(final int size, final byte[] bitSetBytes) {
    final int dataByteLen = bitsCeilToBytes(size);
    final byte[] paddedBytes =
        dataByteLen == 0 ? new byte[0] : Arrays.copyOf(bitSetBytes, dataByteLen);

    final List<LeafNode> chunks = new ArrayList<>();
    int off = 0;
    while (off < paddedBytes.length) {
      final int chunkSize = Math.min(LeafNode.MAX_BYTE_SIZE, paddedBytes.length - off);
      chunks.add(LeafNode.create(Bytes.wrap(paddedBytes, off, chunkSize)));
      off += LeafNode.MAX_BYTE_SIZE;
    }

    final TreeNode progressiveTree = ProgressiveTreeUtil.createProgressiveTree(chunks);
    return BranchNode.create(progressiveTree, toLengthNode(size));
  }

  private int chunksForBitCount(final int bitCount) {
    return (bitCount + ELEMENTS_PER_CHUNK - 1) / ELEMENTS_PER_CHUNK;
  }

  /**
   * Extracts the bitlist length from SSZ bytes by finding the boundary bit. Reuses the same logic
   * as SszBitlistImpl.sszGetLengthAndValidate().
   */
  private static int sszGetLengthAndValidate(final Bytes bytes) {
    final int numBytes = bytes.size();
    checkArgument(numBytes > 0, "BitlistImpl must contain at least one byte");
    checkArgument(bytes.get(numBytes - 1) != 0, "BitlistImpl data must contain end marker bit");
    final int lastByte = 0xFF & bytes.get(bytes.size() - 1);
    final int leadingBitIndex = Integer.bitCount(Integer.highestOneBit(lastByte) - 1);
    return leadingBitIndex + 8 * (numBytes - 1);
  }

  /** Removes the boundary bit from SSZ bytes. */
  private static Bytes sszTruncateLeadingBit(final Bytes bytes, final int length) {
    final Bytes bytesWithoutLast = bytes.slice(0, bytes.size() - 1);
    if (length % 8 == 0) {
      return bytesWithoutLast;
    } else {
      final int lastByte = 0xFF & bytes.get(bytes.size() - 1);
      final int leadingBit = 1 << (length % 8);
      final int lastByteWithoutLeadingBit = lastByte ^ leadingBit;
      return Bytes.concatenate(bytesWithoutLast, Bytes.of(lastByteWithoutLeadingBit));
    }
  }

  @Override
  public Optional<String> getName() {
    return Optional.of("ProgressiveBitlist");
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    return o instanceof SszProgressiveBitlistSchema;
  }

  @Override
  public int hashCode() {
    return SszProgressiveBitlistSchema.class.hashCode();
  }

  @Override
  public String toString() {
    return "ProgressiveBitlist";
  }
}
