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

import static java.util.Collections.emptyList;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.ssz.backing.VectorViewRead;
import tech.pegasys.teku.ssz.backing.tree.LeafNode;
import tech.pegasys.teku.ssz.backing.tree.SszSuperNode;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.tree.TreeUtil;
import tech.pegasys.teku.ssz.backing.type.TypeHints.SszSuperNodeHint;
import tech.pegasys.teku.ssz.backing.view.VectorViewReadImpl;
import tech.pegasys.teku.ssz.sos.SSZDeserializeException;
import tech.pegasys.teku.ssz.sos.SszReader;

public class VectorViewType<C> extends CollectionViewType<VectorViewRead<C>> {

  private final boolean isListBacking;

  public VectorViewType(ViewType<?> elementType, long vectorLength) {
    this(elementType, vectorLength, false);
  }

  VectorViewType(ViewType<?> elementType, long vectorLength, boolean isListBacking) {
    this(elementType, vectorLength, isListBacking, TypeHints.none());
  }

  VectorViewType(
      ViewType<?> elementType, long vectorLength, boolean isListBacking, TypeHints hints) {
    super(vectorLength, elementType, hints);
    this.isListBacking = isListBacking;
  }

  @Override
  public VectorViewRead<C> getDefault() {
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
    } else if (getElementType().getBitsSize() == LeafNode.MAX_BIT_SIZE) {
      return TreeUtil.createDefaultTree(maxChunks(), getElementType().getDefaultTree());
    } else {
      // packed vector
      int totalBytes = (getLength() * getElementType().getBitsSize() + 7) / 8;
      int lastNodeSizeBytes = totalBytes % LeafNode.MAX_BYTE_SIZE;
      int fullZeroNodesCount = totalBytes / LeafNode.MAX_BYTE_SIZE;
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

  @SuppressWarnings({"rawtypes", "unchecked"})
  @Override
  public VectorViewRead<C> createFromBackingNode(TreeNode node) {
    return new VectorViewReadImpl(this, node);
  }

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
    return getElementType().isFixedSize();
  }

  @Override
  public int getVariablePartSize(TreeNode node) {
    return getVariablePartSize(node, getLength());
  }

  @Override
  public int getFixedPartSize() {
    int bitsPerChild = isFixedSize() ? getElementType().getBitsSize() : SSZ_LENGTH_SIZE * 8;
    return (getLength() * bitsPerChild + 7) / 8;
  }

  @Override
  public int sszSerialize(TreeNode node, Consumer<Bytes> writer) {
    return sszSerializeVector(node, writer, getLength());
  }

  @Override
  public TreeNode sszDeserializeTree(SszReader reader) {
    DeserializedData data = sszDeserializeVector(reader);
    if (getElementType() == BasicViewTypes.BIT_TYPE && getLength() % 8 > 0) {
      // for BitVector we need to check that all 'unused' bits in the last byte are 0
      int usedBitCount = getLength() % 8;
      if (data.getLastSszByte().orElseThrow() >>> usedBitCount != 0) {
        throw new SSZDeserializeException("Invalid Bitvector ssz: trailing bits are not 0");
      }
    } else {
      if (data.getChildrenCount() != getLength()) {
        throw new SSZDeserializeException("Invalid Vector ssz");
      }
    }
    return data.getDataTree();
  }
}
