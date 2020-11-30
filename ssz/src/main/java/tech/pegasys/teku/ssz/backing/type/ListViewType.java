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

import java.util.function.Consumer;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.MutableBytes;
import tech.pegasys.teku.ssz.backing.ListViewRead;
import tech.pegasys.teku.ssz.backing.ViewRead;
import tech.pegasys.teku.ssz.backing.tree.LeafNode;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.tree.BranchNode;
import tech.pegasys.teku.ssz.backing.view.ListViewReadImpl;

public class ListViewType<C extends ViewRead> extends CollectionViewType {

  public ListViewType(VectorViewType<C> vectorType) {
    this(vectorType.getElementType(), vectorType.getMaxLength());
  }

  public ListViewType(ViewType elementType, long maxLength) {
    super(maxLength, elementType);
  }

  @Override
  protected TreeNode createDefaultTree() {
    return TreeNode.createBranchNode(
        getCompatibleVectorType().createDefaultTree(), LeafNode.ZERO_LEAVES[8]);
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
    return new VectorViewType<>(getElementType(), getMaxLength(), true);
  }

  @Override
  public int getFixedPartSize() {
    return 0;
  }

  @Override
  public int getVariablePartSize(TreeNode node) {
    int length = getLength(node);
    ViewType elementType = getElementType();
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

      LastBytesDelayer bytesDelayer = new LastBytesDelayer(writer);
      int sizeBytes =
          sszSerializeVector(getVectorNode(node), bytesDelayer, elementsCount)
              + ((elementsCount % 8 == 0) ? 1 : 0);
      Bytes lastBits = bytesDelayer.getLast();
      Bytes trailingByte = (elementsCount % 8 == 0) ? Bytes.of(0) : Bytes.EMPTY;
      MutableBytes mutableBytes = Bytes.wrap(lastBits, trailingByte).mutableCopy();
      byte lastByte = mutableBytes.get(mutableBytes.size() - 1);
      int boundaryBitOff = elementsCount % 8;
      mutableBytes.set(mutableBytes.size() - 1, (byte) (lastByte | 1 << boundaryBitOff));
      writer.accept(mutableBytes);
      return sizeBytes;
    } else {
      return sszSerializeVector(getVectorNode(node), writer, elementsCount);
    }
  }

  private static int getLength(TreeNode listNode) {
    if (!(listNode instanceof BranchNode)) {
      throw new IllegalArgumentException("Expected BranchNode for List, but got " + listNode);
    }
    return SSZType.bytesToLength(((BranchNode) listNode).right().hashTreeRoot());
  }

  private static TreeNode getVectorNode(TreeNode listNode) {
    if (!(listNode instanceof BranchNode)) {
      throw new IllegalArgumentException("Expected BranchNode for List, but got " + listNode);
    }
    return ((BranchNode) listNode).left();
  }

  private static class LastBytesDelayer implements Consumer<Bytes> {
    private final Consumer<Bytes> delegate;
    private Bytes last = Bytes.EMPTY;

    public LastBytesDelayer(Consumer<Bytes> delegate) {
      this.delegate = delegate;
    }

    @Override
    public void accept(Bytes bytes) {
      if (last != null) {
        delegate.accept(last);
      }
      last = bytes;
    }

    public Bytes getLast() {
      return last;
    }
  }
}
