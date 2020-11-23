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
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.ssz.backing.VectorViewRead;
import tech.pegasys.teku.ssz.backing.tree.SuperLeafNode;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.tree.TreeUtil;
import tech.pegasys.teku.ssz.backing.view.VectorViewReadImpl;

public class VectorViewType<C> extends CollectionViewType {

  private final boolean isListBacking;
  private final TypeHints hints;

  public VectorViewType(ViewType elementType, long maxLength) {
    this(elementType, maxLength, false);
  }

  VectorViewType(ViewType elementType, long maxLength, boolean isListBacking) {
    this(elementType, maxLength, isListBacking, TypeHints.none());
  }

  VectorViewType(ViewType elementType, long maxLength, boolean isListBacking, TypeHints hints) {
    super(maxLength, elementType);
    this.isListBacking = isListBacking;
    this.hints = hints;
  }

  @Override
  public VectorViewRead<C> getDefault() {
    return createFromBackingNode(getDefaultTree());
  }

  @Override
  protected TreeNode createDefaultTree() {
    if (isListBacking) {
      if (hints.isSuperLeafNode()) {
        return new SuperLeafNode(treeDepth(), Bytes.EMPTY);
      } else if (hints.isSuperBranchNodes()) {
        return TreeUtil.createDefaultSuperTree(
            maxChunks(), TreeUtil.EMPTY_LEAF, hints.getSuperBranchDepths());
      } else {
        return TreeUtil.createDefaultTree(maxChunks(), TreeUtil.EMPTY_LEAF);
      }
    } else if (getElementType().getBitsSize() == TreeNode.NODE_BIT_SIZE) {
      return TreeUtil.createDefaultTree(maxChunks(), getElementType().getDefaultTree());
    } else {
      // packed vector
      int totalBytes = (getLength() * getElementType().getBitsSize() + 7) / 8;
      int lastNodeSizeBytes = totalBytes % TreeNode.NODE_BYTE_SIZE;
      int fullZeroNodesCount = totalBytes / TreeNode.NODE_BYTE_SIZE;
      Stream<TreeNode> fullZeroNodes =
          Stream.generate(() -> TreeUtil.ZERO_LEAVES[32]).limit(fullZeroNodesCount);
      Stream<TreeNode> lastZeroNode =
          lastNodeSizeBytes > 0
              ? Stream.of(TreeUtil.ZERO_LEAVES[lastNodeSizeBytes])
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
}
