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

package tech.pegasys.teku.ssz.backing.schema;

import static com.google.common.base.Preconditions.checkArgument;

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.ssz.SSZTypes.Bitlist;
import tech.pegasys.teku.ssz.backing.SszData;
import tech.pegasys.teku.ssz.backing.SszList;
import tech.pegasys.teku.ssz.backing.tree.BranchNode;
import tech.pegasys.teku.ssz.backing.tree.LeafNode;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.view.SszListImpl;
import tech.pegasys.teku.ssz.backing.view.SszListImpl.ListContainerRead;
import tech.pegasys.teku.ssz.sos.SszDeserializeException;
import tech.pegasys.teku.ssz.sos.SszLengthBounds;
import tech.pegasys.teku.ssz.sos.SszReader;
import tech.pegasys.teku.ssz.sos.SszWriter;

public class SszListSchema<ElementDataT extends SszData>
    extends SszCollectionSchema<ElementDataT, SszList<ElementDataT>> {
  private final SszVectorSchema<ElementDataT> compatibleVectorSchema;
  private final SszContainerSchema<?> sszContainerSchema;

  public SszListSchema(SszSchema<ElementDataT> elementSchema, long maxLength) {
    this(elementSchema, maxLength, SszSchemaHints.none());
  }

  public SszListSchema(
      SszSchema<ElementDataT> elementSchema, long maxLength, SszSchemaHints hints) {
    super(maxLength, elementSchema, hints);
    this.compatibleVectorSchema =
        new SszVectorSchema<>(getElementSchema(), getMaxLength(), true, getHints());
    this.sszContainerSchema =
        SszContainerSchema.create(
            Arrays.asList(getCompatibleVectorSchema(), SszPrimitiveSchemas.UINT64_SCHEMA),
            (type, node) -> new ListContainerRead<>(this, type, node));
  }

  @Override
  protected TreeNode createDefaultTree() {
    return createTree(getCompatibleVectorSchema().createDefaultTree(), 0);
  }

  private TreeNode createTree(TreeNode dataNode, int length) {
    return BranchNode.create(dataNode, toLengthNode(length));
  }

  @Override
  public SszList<ElementDataT> getDefault() {
    return new SszListImpl<>(this, createDefaultTree());
  }

  @Override
  public SszList<ElementDataT> createFromBackingNode(TreeNode node) {
    return new SszListImpl<>(this, node);
  }

  private SszVectorSchema<ElementDataT> getCompatibleVectorSchema() {
    return compatibleVectorSchema;
  }

  public SszContainerSchema<?> getCompatibleListContainerType() {
    return sszContainerSchema;
  }

  @Override
  public int getFixedPartSize() {
    return 0;
  }

  @Override
  public int getVariablePartSize(TreeNode node) {
    int length = getLength(node);
    SszSchema<?> elementSchema = getElementSchema();
    if (elementSchema.isFixedSize()) {
      if (elementSchema.getBitsSize() == 1) {
        // Bitlist is handled specially
        return length / 8 + 1;
      } else {
        return length * elementSchema.getFixedPartSize();
      }
    } else {
      return getVariablePartSize(getVectorNode(node), length) + length * SSZ_LENGTH_SIZE;
    }
  }

  @Override
  public boolean isFixedSize() {
    return false;
  }

  @Override
  public int sszSerializeTree(TreeNode node, SszWriter writer) {
    int elementsCount = getLength(node);
    if (getElementSchema().getBitsSize() == 1) {
      // Bitlist is handled specially
      BytesCollector bytesCollector = new BytesCollector(/*elementsCount / 8 + 1*/ );
      sszSerializeVector(getVectorNode(node), bytesCollector, elementsCount);
      return bytesCollector.flushWithBoundaryBit(writer, elementsCount);
    } else {
      return sszSerializeVector(getVectorNode(node), writer, elementsCount);
    }
  }

  @Override
  public TreeNode sszDeserializeTree(SszReader reader) {
    if (getElementSchema().getBitsSize() == 1) {
      // Bitlist is handled specially
      int availableBytes = reader.getAvailableBytes();
      // preliminary rough check
      checkSsz(
          (availableBytes - 1) * 8 <= getMaxLength(),
          "SSZ sequence length exceeds max type length");
      Bytes bytes = reader.read(availableBytes);
      int length = Bitlist.sszGetLengthAndValidate(bytes);
      if (length > getMaxLength()) {
        throw new SszDeserializeException("Too long bitlist");
      }
      Bytes treeBytes = Bitlist.sszTruncateLeadingBit(bytes, length);
      try (SszReader sszReader = SszReader.fromBytes(treeBytes)) {
        DeserializedData data = sszDeserializeVector(sszReader);
        return createTree(data.getDataTree(), length);
      }
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

  private static int getLength(TreeNode listNode) {
    if (!(listNode instanceof BranchNode)) {
      throw new IllegalArgumentException("Expected BranchNode for List, but got " + listNode);
    }
    long longLength = fromLengthNode(((BranchNode) listNode).right());
    assert longLength < Integer.MAX_VALUE;
    return (int) longLength;
  }

  private static TreeNode getVectorNode(TreeNode listNode) {
    if (!(listNode instanceof BranchNode)) {
      throw new IllegalArgumentException("Expected BranchNode for List, but got " + listNode);
    }
    return ((BranchNode) listNode).left();
  }

  @Override
  public SszLengthBounds getSszLengthBounds() {
    SszLengthBounds elementLengthBounds = getElementSchema().getSszLengthBounds();
    // if elements are of dynamic size the offset size should be added for every element
    SszLengthBounds elementAndOffsetLengthBounds =
        elementLengthBounds.addBytes(getElementSchema().isFixedSize() ? 0 : SSZ_LENGTH_SIZE);
    SszLengthBounds maxLenBounds =
        SszLengthBounds.ofBits(0, elementAndOffsetLengthBounds.mul(getMaxLength()).getMaxBits());
    // adding 1 boundary bit for Bitlist
    return maxLenBounds.addBits(getElementSchema().getBitsSize() == 1 ? 1 : 0).ceilToBytes();
  }

  private static class BytesCollector implements SszWriter {

    private static class UnsafeBytes {
      private final byte[] bytes;
      private final int offset;
      private final int length;

      public UnsafeBytes(byte[] bytes, int offset, int length) {
        this.bytes = bytes;
        this.offset = offset;
        this.length = length;
      }
    }

    private final List<UnsafeBytes> bytesList = new ArrayList<>();
    private int size;

    @Override
    public void write(byte[] bytes, int offset, int length) {
      if (length == 0) {
        return;
      }
      bytesList.add(new UnsafeBytes(bytes, offset, length));
      size += length;
    }

    public int flushWithBoundaryBit(SszWriter writer, int boundaryBitOffset) {
      int bitIdx = boundaryBitOffset % 8;
      checkArgument((boundaryBitOffset + 7) / 8 == size, "Invalid boundary bit offset");
      if (bitIdx == 0) {
        bytesList.forEach(bb -> writer.write(bb.bytes, bb.offset, bb.length));
        writer.write(new byte[] {1});
        return size + 1;
      } else {
        UnsafeBytes lastBytes = bytesList.get(bytesList.size() - 1);
        byte lastByte = lastBytes.bytes[lastBytes.offset + lastBytes.length - 1];
        byte lastByteWithBoundaryBit = (byte) (lastByte ^ (1 << bitIdx));

        for (int i = 0; i < bytesList.size() - 1; i++) {
          UnsafeBytes bb = bytesList.get(i);
          writer.write(bb.bytes, bb.offset, bb.length);
        }
        if (lastBytes.length > 1) {
          writer.write(lastBytes.bytes, lastBytes.offset, lastBytes.length - 1);
        }
        writer.write(new byte[] {lastByteWithBoundaryBit});
        return size;
      }
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof SszListSchema)) {
      return false;
    }
    SszListSchema<?> that = (SszListSchema<?>) o;
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
