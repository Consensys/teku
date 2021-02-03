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

package tech.pegasys.teku.ssz.backing.type;

import static com.google.common.base.Preconditions.checkArgument;

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.ssz.SSZTypes.Bitlist;
import tech.pegasys.teku.ssz.backing.ListViewRead;
import tech.pegasys.teku.ssz.backing.ViewRead;
import tech.pegasys.teku.ssz.backing.tree.BranchNode;
import tech.pegasys.teku.ssz.backing.tree.LeafNode;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.view.ListViewReadImpl;
import tech.pegasys.teku.ssz.backing.view.ListViewReadImpl.ListContainerRead;
import tech.pegasys.teku.ssz.sos.SSZDeserializeException;
import tech.pegasys.teku.ssz.sos.SszLengthBounds;
import tech.pegasys.teku.ssz.sos.SszReader;
import tech.pegasys.teku.ssz.sos.SszWriter;

public class ListViewType<ElementViewT extends ViewRead>
    extends CollectionViewType<ElementViewT, ListViewRead<ElementViewT>> {
  private final VectorViewType<ElementViewT> compatibleVectorType;
  private final ContainerViewType<?> containerViewType;

  public ListViewType(ViewType<ElementViewT> elementType, long maxLength) {
    this(elementType, maxLength, TypeHints.none());
  }

  public ListViewType(ViewType<ElementViewT> elementType, long maxLength, TypeHints hints) {
    super(maxLength, elementType, hints);
    this.compatibleVectorType =
        new VectorViewType<>(getElementType(), getMaxLength(), true, getHints());
    this.containerViewType =
        ContainerViewType.create(
            Arrays.asList(getCompatibleVectorType(), BasicViewTypes.UINT64_TYPE),
            (type, node) -> new ListContainerRead<>(this, type, node));
  }

  @Override
  protected TreeNode createDefaultTree() {
    return createTree(getCompatibleVectorType().createDefaultTree(), 0);
  }

  private TreeNode createTree(TreeNode dataNode, int length) {
    return BranchNode.create(dataNode, toLengthNode(length));
  }

  @Override
  public ListViewRead<ElementViewT> getDefault() {
    return new ListViewReadImpl<>(this, createDefaultTree());
  }

  @Override
  public ListViewRead<ElementViewT> createFromBackingNode(TreeNode node) {
    return new ListViewReadImpl<>(this, node);
  }

  private VectorViewType<ElementViewT> getCompatibleVectorType() {
    return compatibleVectorType;
  }

  public ContainerViewType<?> getCompatibleListContainerType() {
    return containerViewType;
  }

  @Override
  public int getFixedPartSize() {
    return 0;
  }

  @Override
  public int getVariablePartSize(TreeNode node) {
    int length = getLength(node);
    ViewType<?> elementType = getElementType();
    if (elementType.isFixedSize()) {
      if (elementType.getBitsSize() == 1) {
        // Bitlist is handled specially
        return length / 8 + 1;
      } else {
        return length * elementType.getFixedPartSize();
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
    if (getElementType().getBitsSize() == 1) {
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
    if (getElementType().getBitsSize() == 1) {
      // Bitlist is handled specially
      Bytes bytes = reader.read(reader.getAvailableBytes());
      int length = Bitlist.sszGetLengthAndValidate(bytes);
      if (length > getMaxLength()) {
        throw new SSZDeserializeException("Too long bitlist");
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
    SszLengthBounds elementLengthBounds = getElementType().getSszLengthBounds();
    // if elements are of dynamic size the offset size should be added for every element
    SszLengthBounds elementAndOffsetLengthBounds =
        elementLengthBounds.addBytes(getElementType().isFixedSize() ? 0 : SSZ_LENGTH_SIZE);
    SszLengthBounds maxLenBounds =
        SszLengthBounds.ofBits(0, elementAndOffsetLengthBounds.mul(getMaxLength()).getMaxBits());
    // adding 1 boundary bit for Bitlist
    return maxLenBounds.addBits(getElementType().getBitsSize() == 1 ? 1 : 0).ceilToBytes();
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
    if (!(o instanceof ListViewType)) {
      return false;
    }
    ListViewType<?> that = (ListViewType<?>) o;
    return getElementType().equals(that.getElementType()) && getMaxLength() == that.getMaxLength();
  }

  @Override
  public int hashCode() {
    return super.hashCode();
  }

  @Override
  public String toString() {
    return "List[" + getElementType() + ", " + getMaxLength() + "]";
  }
}
