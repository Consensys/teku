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

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.ssz.SSZTypes.Bitlist;
import tech.pegasys.teku.ssz.backing.ListViewRead;
import tech.pegasys.teku.ssz.backing.ViewRead;
import tech.pegasys.teku.ssz.backing.tree.BranchNode;
import tech.pegasys.teku.ssz.backing.tree.LeafNode;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.view.ListViewReadImpl;
import tech.pegasys.teku.ssz.sos.SSZDeserializeException;
import tech.pegasys.teku.ssz.sos.SszReader;

public class ListViewType<C extends ViewRead> extends CollectionViewType<ListViewRead<C>> {

  public ListViewType(VectorViewType<C> vectorType) {
    this(vectorType.getElementType(), vectorType.getMaxLength());
  }

  public ListViewType(ViewType<?> elementType, long maxLength) {
    this(elementType, maxLength, TypeHints.none());
  }

  public ListViewType(ViewType<?> elementType, long maxLength, TypeHints hints) {
    super(maxLength, elementType, hints);
  }

  @Override
  protected TreeNode createDefaultTree() {
    return createTree(getCompatibleVectorType().createDefaultTree(), 0);
  }

  private TreeNode createTree(TreeNode dataNode, int length) {
    return BranchNode.create(dataNode, toLengthNode(length));
  }

  @Override
  public ListViewRead<C> getDefault() {
    return new ListViewReadImpl<C>(this, createDefaultTree());
  }

  @Override
  public ListViewRead<C> createFromBackingNode(TreeNode node) {
    return new ListViewReadImpl<>(this, node);
  }

  public VectorViewType<C> getCompatibleVectorType() {
    return new VectorViewType<>(getElementType(), getMaxLength(), true, getHints());
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
  public int sszSerialize(TreeNode node, Consumer<Bytes> writer) {
    int elementsCount = getLength(node);
    if (getElementType().getBitsSize() == 1) {
      // Bitlist is handled specially
      BytesCollector bytesCollector = new BytesCollector();
      sszSerializeVector(getVectorNode(node), bytesCollector, elementsCount);
      Bytes allBits = bytesCollector.getAll();
      Bytes allBitsWithBoundary = Bitlist.sszAppendLeadingBit(allBits, elementsCount);
      writer.accept(allBitsWithBoundary);
      return allBitsWithBoundary.size();
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

  private static class BytesCollector implements Consumer<Bytes> {
    private final List<Bytes> bytesList = new ArrayList<>();

    @Override
    public void accept(Bytes bytes) {
      bytesList.add(bytes);
    }

    public Bytes getAll() {
      return Bytes.wrap(bytesList.toArray(new Bytes[0]));
    }
  }
}
