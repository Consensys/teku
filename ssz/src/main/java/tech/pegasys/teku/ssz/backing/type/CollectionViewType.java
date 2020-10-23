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

import java.util.Objects;
import java.util.function.Consumer;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.tree.TreeUtil;

/** Type of homogeneous collections (like List and Vector) */
public abstract class CollectionViewType implements CompositeViewType {

  private final long maxLength;
  private final ViewType elementType;
  private volatile TreeNode defaultTree;

  CollectionViewType(long maxLength, ViewType elementType) {
    this.maxLength = maxLength;
    this.elementType = elementType;
  }

  protected abstract TreeNode createDefaultTree();

  @Override
  public TreeNode getDefaultTree() {
    if (defaultTree == null) {
      this.defaultTree = createDefaultTree();
    }
    return defaultTree;
  }

  @Override
  public long getMaxLength() {
    return maxLength;
  }

  public ViewType getElementType() {
    return elementType;
  }

  @Override
  public ViewType getChildType(int index) {
    return getElementType();
  }

  @Override
  public int getElementsPerChunk() {
    return 256 / getElementType().getBitsSize();
  }

  protected int getVariablePartSize(TreeNode vectorNode, int length) {
    if (isFixedSize()) {
      return 0;
    } else {
      int size = 0;
      for (int i = 0; i < length; i++) {
        size += getElementType().getSszSize(vectorNode.get(getGeneralizedIndex(i)));
      }
      return size;
    }
  }

  public int sszSerializeVector(TreeNode node, Consumer<Bytes> writer, int elementsCount) {
    if (getElementType().isFixedSize()) {
      if (getElementType().getBitsSize() == 1) {
        return sszSerializeFixedVectorRegular(node,writer,elementsCount);
      } else {
        return sszSerializeFixedVectorFast(node, writer, elementsCount);
      }
    } else {
      return sszSerializeVariableVector(node, writer, elementsCount);
    }
  }

  private int sszSerializeFixedVectorFast(TreeNode vectorNode, Consumer<Bytes> writer, int elementsCount) {
    int nodesCount = getChunks(elementsCount);
    int[] bytesCnt = new int[1];
    TreeUtil.iterateLeaves(
        vectorNode,
        getGeneralizedIndex(0),
        getGeneralizedIndex(nodesCount - 1),
        leaf -> {
          Bytes ssz = leaf.getSSZ();
          writer.accept(ssz);
          bytesCnt[0] += ssz.size();
        });
    return bytesCnt[0];
  }

  private int sszSerializeFixedVectorRegular(TreeNode vectorNode, Consumer<Bytes> writer, int elementsCount) {
    int nodesCount = getChunks(elementsCount);
    ViewType elementType = getElementType();
    int bytesCount = (elementsCount * elementType.getBitsSize() + 7) / 8;
    int size = 0;
    for (int i = 0; i < nodesCount; i++) {
      TreeNode childSubtree = vectorNode.get(getGeneralizedIndex(i));
      if (elementType instanceof BasicViewType) {
        Bytes ssz = childSubtree.hashTreeRoot();
        if (bytesCount < 32) {
          ssz = ssz.slice(0, bytesCount);
        }
        writer.accept(ssz);
        size += ssz.size();
      } else {
        size += elementType.sszSerialize(childSubtree, writer);
      }
      bytesCount -= 32;
    }
    return size;
  }

  private int sszSerializeVariableVector(
      TreeNode vectorNode, Consumer<Bytes> writer, int elementsCount) {
    ViewType elementType = getElementType();
    int variableOffset = SSZ_LENGTH_SIZE * elementsCount;
    for (int i = 0; i < elementsCount; i++) {
      TreeNode childSubtree = vectorNode.get(getGeneralizedIndex(i));
      int childSize = elementType.getSszSize(childSubtree);
      writer.accept(SSZType.lengthToBytes(variableOffset));
      variableOffset += childSize;
    }
    for (int i = 0; i < elementsCount; i++) {
      TreeNode childSubtree = vectorNode.get(getGeneralizedIndex(i));
      elementType.sszSerialize(childSubtree, writer);
    }
    return variableOffset;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    CollectionViewType that = (CollectionViewType) o;
    return maxLength == that.maxLength && elementType.equals(that.elementType);
  }

  @Override
  public int hashCode() {
    return Objects.hash(maxLength, elementType);
  }
}
