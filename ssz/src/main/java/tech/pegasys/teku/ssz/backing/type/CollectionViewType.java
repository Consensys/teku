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

import static java.lang.Integer.min;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.ssz.backing.SszData;
import tech.pegasys.teku.ssz.backing.tree.LeafNode;
import tech.pegasys.teku.ssz.backing.tree.SszNodeTemplate;
import tech.pegasys.teku.ssz.backing.tree.SszSuperNode;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.tree.TreeUtil;
import tech.pegasys.teku.ssz.backing.type.TypeHints.SszSuperNodeHint;
import tech.pegasys.teku.ssz.sos.SSZDeserializeException;
import tech.pegasys.teku.ssz.sos.SszReader;
import tech.pegasys.teku.ssz.sos.SszWriter;

/** Type of homogeneous collections (like List and Vector) */
public abstract class CollectionViewType<ElementViewT extends SszData, ViewT extends SszData>
    implements SszCompositeSchema<ViewT> {

  private final long maxLength;
  private final SszSchema<ElementViewT> elementType;
  private final TypeHints hints;
  protected final Supplier<SszNodeTemplate> elementSszSupernodeTemplate =
      Suppliers.memoize(() -> SszNodeTemplate.createFromType(getElementType()));
  private volatile TreeNode defaultTree;

  protected CollectionViewType(
      long maxLength, SszSchema<ElementViewT> elementType, TypeHints hints) {
    this.maxLength = maxLength;
    this.elementType = elementType;
    this.hints = hints;
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

  public SszSchema<ElementViewT> getElementType() {
    return elementType;
  }

  @Override
  public SszSchema<?> getChildType(int index) {
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

  /**
   * Serializes {@code elementsCount} from the content of this collection
   *
   * @param vectorNode for a {@link VectorViewType} type - the node itself, for a {@link
   *     ListViewType} - the left sibling node of list size node
   */
  protected int sszSerializeVector(TreeNode vectorNode, SszWriter writer, int elementsCount) {
    if (getElementType().isFixedSize()) {
      return sszSerializeFixedVectorFast(vectorNode, writer, elementsCount);
    } else {
      return sszSerializeVariableVector(vectorNode, writer, elementsCount);
    }
  }

  private int sszSerializeFixedVectorFast(
      TreeNode vectorNode, SszWriter writer, int elementsCount) {
    if (elementsCount == 0) {
      return 0;
    }
    int nodesCount = getChunks(elementsCount);
    int[] bytesCnt = new int[1];
    TreeUtil.iterateLeavesData(
        vectorNode,
        getGeneralizedIndex(0),
        getGeneralizedIndex(nodesCount - 1),
        leafData -> {
          writer.write(leafData);
          bytesCnt[0] += leafData.size();
        });
    return bytesCnt[0];
  }

  private int sszSerializeVariableVector(TreeNode vectorNode, SszWriter writer, int elementsCount) {
    SszSchema<?> elementType = getElementType();
    int variableOffset = SSZ_LENGTH_SIZE * elementsCount;
    for (int i = 0; i < elementsCount; i++) {
      TreeNode childSubtree = vectorNode.get(getGeneralizedIndex(i));
      int childSize = elementType.getSszSize(childSubtree);
      writer.write(SszType.lengthToBytes(variableOffset));
      variableOffset += childSize;
    }
    for (int i = 0; i < elementsCount; i++) {
      TreeNode childSubtree = vectorNode.get(getGeneralizedIndex(i));
      elementType.sszSerializeTree(childSubtree, writer);
    }
    return variableOffset;
  }

  protected DeserializedData sszDeserializeVector(SszReader reader) {
    if (getElementType().isFixedSize()) {
      Optional<SszSuperNodeHint> sszSuperNodeHint = getHints().getHint(SszSuperNodeHint.class);
      if (sszSuperNodeHint.isPresent()) {
        return sszDeserializeSupernode(reader, sszSuperNodeHint.get().getDepth());
      } else {
        return sszDeserializeFixed(reader);
      }
    } else {
      return sszDeserializeVariable(reader);
    }
  }

  private DeserializedData sszDeserializeSupernode(SszReader reader, int supernodeDepth) {
    SszNodeTemplate template = elementSszSupernodeTemplate.get();
    int sszSize = reader.getAvailableBytes();
    if (sszSize % template.getSszLength() != 0) {
      throw new SSZDeserializeException("Ssz length is not multiple of element length");
    }
    int elementsCount = sszSize / template.getSszLength();
    int chunkSize = (1 << supernodeDepth) * template.getSszLength();
    int bytesRemain = sszSize;
    List<SszSuperNode> sszNodes = new ArrayList<>(bytesRemain / chunkSize + 1);
    while (bytesRemain > 0) {
      int toRead = min(bytesRemain, chunkSize);
      bytesRemain -= toRead;
      Bytes bytes = reader.read(toRead);
      SszSuperNode node = new SszSuperNode(supernodeDepth, template, bytes);
      sszNodes.add(node);
    }
    TreeNode tree =
        TreeUtil.createTree(
            sszNodes,
            new SszSuperNode(supernodeDepth, template, Bytes.EMPTY),
            treeDepth() - supernodeDepth);
    return new DeserializedData(tree, elementsCount);
  }

  private DeserializedData sszDeserializeFixed(SszReader reader) {
    int bytesSize = reader.getAvailableBytes();
    checkSsz(
        bytesSize % getElementType().getFixedPartSize() == 0,
        "SSZ sequence length is not multiple of fixed element size");
    int elementBitSize = getElementType().getBitsSize();
    if (elementBitSize >= 8) {
      checkSsz(
          bytesSize / getElementType().getFixedPartSize() <= getMaxLength(),
          "SSZ sequence length exceeds max type length");
    } else {
      // preliminary rough check
      checkSsz(
          (bytesSize - 1) * 8 / elementBitSize <= getMaxLength(),
          "SSZ sequence length exceeds max type length");
    }
    if (getElementType() instanceof SszPrimitiveSchema) {
      int bytesRemain = bytesSize;
      List<LeafNode> childNodes = new ArrayList<>(bytesRemain / LeafNode.MAX_BYTE_SIZE + 1);
      while (bytesRemain > 0) {
        int toRead = min(bytesRemain, LeafNode.MAX_BYTE_SIZE);
        bytesRemain -= toRead;
        Bytes bytes = reader.read(toRead);
        LeafNode node = LeafNode.create(bytes);
        childNodes.add(node);
      }

      Optional<Byte> lastByte;
      if (childNodes.isEmpty()) {
        lastByte = Optional.empty();
      } else {
        Bytes lastNodeData = childNodes.get(childNodes.size() - 1).getData();
        lastByte = Optional.of(lastNodeData.get(lastNodeData.size() - 1));
      }
      return new DeserializedData(
          TreeUtil.createTree(childNodes, treeDepth()), bytesSize * 8 / elementBitSize, lastByte);
    } else {
      int elementsCount = bytesSize / getElementType().getFixedPartSize();
      List<TreeNode> childNodes = new ArrayList<>();
      for (int i = 0; i < elementsCount; i++) {
        try (SszReader sszReader = reader.slice(getElementType().getFixedPartSize())) {
          TreeNode childNode = getElementType().sszDeserializeTree(sszReader);
          childNodes.add(childNode);
        }
      }
      return new DeserializedData(TreeUtil.createTree(childNodes, treeDepth()), elementsCount);
    }
  }

  private DeserializedData sszDeserializeVariable(SszReader reader) {
    final int endOffset = reader.getAvailableBytes();
    final List<TreeNode> childNodes = new ArrayList<>();
    if (endOffset > 0) {
      int firstElementOffset = SszType.bytesToLength(reader.read(SSZ_LENGTH_SIZE));
      checkSsz(firstElementOffset % SSZ_LENGTH_SIZE == 0, "Invalid first element offset");
      int elementsCount = firstElementOffset / SSZ_LENGTH_SIZE;
      checkSsz(elementsCount <= getMaxLength(), "SSZ sequence length exceeds max type length");
      List<Integer> elementOffsets = new ArrayList<>(elementsCount + 1);
      elementOffsets.add(firstElementOffset);
      for (int i = 1; i < elementsCount; i++) {
        int offset = SszType.bytesToLength(reader.read(SSZ_LENGTH_SIZE));
        elementOffsets.add(offset);
      }
      elementOffsets.add(endOffset);

      List<Integer> elementSizes =
          IntStream.range(0, elementOffsets.size() - 1)
              .map(i -> elementOffsets.get(i + 1) - elementOffsets.get(i))
              .boxed()
              .collect(Collectors.toList());

      if (elementSizes.stream().anyMatch(s -> s < 0)) {
        throw new SSZDeserializeException("Invalid SSZ: wrong child offsets");
      }

      for (int elementSize : elementSizes) {
        try (SszReader sszReader = reader.slice(elementSize)) {
          childNodes.add(getElementType().sszDeserializeTree(sszReader));
        }
      }
    }
    return new DeserializedData(TreeUtil.createTree(childNodes, treeDepth()), childNodes.size());
  }

  protected static void checkSsz(boolean condition, String error) {
    if (!condition) {
      throw new SSZDeserializeException(error);
    }
  }

  public TypeHints getHints() {
    return hints;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    CollectionViewType<?, ?> that = (CollectionViewType<?, ?>) o;
    return maxLength == that.maxLength && elementType.equals(that.elementType);
  }

  @Override
  public int hashCode() {
    return Objects.hash(maxLength, elementType);
  }

  static class DeserializedData {

    private final TreeNode dataTree;
    private final int childrenCount;
    private final Optional<Byte> lastSszByte;

    public DeserializedData(TreeNode dataTree, int childrenCount) {
      this(dataTree, childrenCount, Optional.empty());
    }

    public DeserializedData(TreeNode dataTree, int childrenCount, Optional<Byte> lastSszByte) {
      this.dataTree = dataTree;
      this.childrenCount = childrenCount;
      this.lastSszByte = lastSszByte;
    }

    public TreeNode getDataTree() {
      return dataTree;
    }

    public int getChildrenCount() {
      return childrenCount;
    }

    public Optional<Byte> getLastSszByte() {
      return lastSszByte;
    }
  }
}
