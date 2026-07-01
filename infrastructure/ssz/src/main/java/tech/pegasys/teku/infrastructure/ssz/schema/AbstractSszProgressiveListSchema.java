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

import static com.google.common.base.Preconditions.checkState;
import static tech.pegasys.teku.infrastructure.ssz.schema.ListSchemaUtil.getLength;
import static tech.pegasys.teku.infrastructure.ssz.schema.ListSchemaUtil.getVectorNode;
import static tech.pegasys.teku.infrastructure.ssz.schema.ListSchemaUtil.toLengthNode;
import static tech.pegasys.teku.infrastructure.ssz.tree.TreeUtil.bitsCeilToBytes;

import com.google.common.base.Suppliers;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.json.types.DeserializableArrayTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.impl.SszProgressiveListImpl;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchemaHints.SszSuperNodeHint;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszPrimitiveSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.LoadingUtil;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.StoringUtil;
import tech.pegasys.teku.infrastructure.ssz.sos.SszDeserializeException;
import tech.pegasys.teku.infrastructure.ssz.sos.SszLengthBounds;
import tech.pegasys.teku.infrastructure.ssz.sos.SszReader;
import tech.pegasys.teku.infrastructure.ssz.sos.SszWriter;
import tech.pegasys.teku.infrastructure.ssz.tree.BranchNode;
import tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil;
import tech.pegasys.teku.infrastructure.ssz.tree.LeafNode;
import tech.pegasys.teku.infrastructure.ssz.tree.ProgressiveTreeUtil;
import tech.pegasys.teku.infrastructure.ssz.tree.SszNodeTemplate;
import tech.pegasys.teku.infrastructure.ssz.tree.SszSuperNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNodeSource;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNodeSource.CompressedBranchInfo;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNodeStore;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeUtil;

/**
 * Base schema for ProgressiveList (EIP-7916) — a variable-length homogeneous collection with no max
 * capacity that uses a progressive merkle tree for stable generalized indices.
 *
 * <p>The {@code SszListT} type parameter is the concrete list type produced by the schema. Most
 * usages are {@link SszProgressiveListSchema} where {@code SszListT} is {@link SszList}, but
 * specialized subclasses (e.g. a progressive byte list) produce {@link SszList} subtypes so they
 * can preserve Teku's existing collection interfaces.
 *
 * <p>Tree structure: BranchNode(progressiveDataTree, lengthNode)
 *
 * <p>The progressive data tree is a right-leaning asymmetric tree where subtree capacities grow by
 * 4x per level.
 */
public abstract class AbstractSszProgressiveListSchema<
        ElementDataT extends SszData, SszListT extends SszList<ElementDataT>>
    implements SszListSchema<ElementDataT, SszListT> {

  private final SszSchema<ElementDataT> elementSchema;
  private final SszSchemaHints hints;
  private final TreeNode defaultTree;
  private final int elementsPerChunk;
  private final DeserializableTypeDefinition<SszListT> jsonTypeDefinition;
  private final Supplier<SszNodeTemplate> elementSszSupernodeTemplate =
      Suppliers.memoize(() -> SszNodeTemplate.createFromType(getElementSchema()));

  protected AbstractSszProgressiveListSchema(final SszSchema<ElementDataT> elementSchema) {
    this(elementSchema, SszSchemaHints.none());
  }

  protected AbstractSszProgressiveListSchema(
      final SszSchema<ElementDataT> elementSchema, final SszSchemaHints hints) {
    this.elementSchema = elementSchema;
    this.hints = hints;
    this.elementsPerChunk = computeElementsPerChunk(elementSchema);
    this.defaultTree =
        BranchNode.create(ProgressiveTreeUtil.createProgressiveTree(List.of()), toLengthNode(0));
    this.jsonTypeDefinition =
        new DeserializableArrayTypeDefinition<>(
            elementSchema.getJsonTypeDefinition(), this::createFromElements);
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
  @SuppressWarnings("unchecked")
  public SszListT createFromBackingNode(final TreeNode node) {
    return (SszListT) new SszProgressiveListImpl<>(this, node);
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
  public DeserializableTypeDefinition<SszListT> getJsonTypeDefinition() {
    return jsonTypeDefinition;
  }

  @Override
  public void storeBackingNodes(
      final TreeNodeStore nodeStore,
      final int maxBranchLevelsSkipped,
      final long rootGIndex,
      final TreeNode node) {
    final TreeNode dataTree = getVectorNode(node);
    final TreeNode lengthNode = node.get(GIndexUtil.RIGHT_CHILD_G_INDEX);
    final int length = getLength(node);
    final int totalChunks = getChunks(length);

    storeProgressiveDataTree(
        nodeStore,
        maxBranchLevelsSkipped,
        GIndexUtil.gIdxLeftGIndex(rootGIndex),
        dataTree,
        totalChunks);

    nodeStore.storeLeafNode(lengthNode, GIndexUtil.gIdxRightGIndex(rootGIndex));

    nodeStore.storeBranchNode(
        node.hashTreeRoot(),
        rootGIndex,
        1,
        new Bytes32[] {dataTree.hashTreeRoot(), lengthNode.hashTreeRoot()});
  }

  @Override
  public TreeNode loadBackingNodes(
      final TreeNodeSource nodeSource, final Bytes32 rootHash, final long rootGIndex) {
    if (TreeUtil.ZERO_TREES_BY_ROOT.containsKey(rootHash) || rootHash.equals(Bytes32.ZERO)) {
      return getDefaultTree();
    }

    final CompressedBranchInfo branchData = nodeSource.loadBranchNode(rootHash, rootGIndex);
    checkState(
        branchData.getChildren().length == 2, "List root node must have exactly two children");
    checkState(branchData.getDepth() == 1, "List root node must have depth of 1");

    final Bytes32 dataHash = branchData.getChildren()[0];
    final Bytes32 lengthHash = branchData.getChildren()[1];
    final int length =
        nodeSource
            .loadLeafNode(lengthHash, GIndexUtil.gIdxRightGIndex(rootGIndex))
            .getInt(0, ByteOrder.LITTLE_ENDIAN);

    final int totalChunks = getChunks(length);
    final long dataRootGIndex = GIndexUtil.gIdxLeftGIndex(rootGIndex);

    final TreeNode dataTree =
        loadProgressiveDataTree(nodeSource, dataHash, dataRootGIndex, totalChunks);

    return BranchNode.create(dataTree, toLengthNode(length));
  }

  // ===== Store/Load helpers =====

  private int getSuperNodeDepth() {
    return hints.getHint(SszSuperNodeHint.class).map(SszSuperNodeHint::getDepth).orElse(0);
  }

  private void storeProgressiveDataTree(
      final TreeNodeStore nodeStore,
      final int maxBranchLevelsSkipped,
      final long dataRootGIndex,
      final TreeNode dataTree,
      final int totalChunks) {
    final int superNodeDepth = getSuperNodeDepth();
    ProgressiveTreeUtil.storeProgressiveSpine(
        nodeStore,
        dataRootGIndex,
        dataTree,
        totalChunks,
        (levelGIndex, levelSubtree, chunksInLevel, depth) ->
            storeLevelSubtree(
                nodeStore,
                maxBranchLevelsSkipped,
                levelGIndex,
                levelSubtree,
                chunksInLevel,
                depth,
                superNodeDepth));
  }

  private void storeLevelSubtree(
      final TreeNodeStore nodeStore,
      final int maxBranchLevelsSkipped,
      final long levelGIndex,
      final TreeNode levelSubtree,
      final int chunksInLevel,
      final int depth,
      final int superNodeDepth) {
    if (chunksInLevel == 0) {
      return;
    }

    final int childDepth = Math.max(0, depth - superNodeDepth);

    if (depth == 0) {
      // Level 0: single chunk
      elementSchema.storeBackingNodes(nodeStore, maxBranchLevelsSkipped, levelGIndex, levelSubtree);
    } else if (childDepth == 0) {
      // SuperNode covers entire level subtree
      nodeStore.storeLeafNode(levelSubtree, levelGIndex);
    } else {
      final long lastUsefulGIndex =
          computeLevelLastUsefulGIndex(levelGIndex, chunksInLevel, childDepth, superNodeDepth);

      final StoringUtil.TargetDepthNodeHandler handler =
          superNodeDepth == 0
              ? (targetNode, targetGIndex) ->
                  elementSchema.storeBackingNodes(
                      nodeStore, maxBranchLevelsSkipped, targetGIndex, targetNode)
              : nodeStore::storeLeafNode;

      StoringUtil.storeNodesToDepth(
          nodeStore,
          maxBranchLevelsSkipped,
          levelSubtree,
          levelGIndex,
          childDepth,
          lastUsefulGIndex,
          handler);
    }
  }

  private static long computeLevelLastUsefulGIndex(
      final long levelGIndex,
      final int chunksInLevel,
      final int childDepth,
      final int superNodeDepth) {
    final int chunksPerTargetNode = Math.max(1, 1 << superNodeDepth);
    final int lastUsefulTargetIndex = (chunksInLevel - 1) / chunksPerTargetNode;
    return GIndexUtil.gIdxChildGIndex(levelGIndex, lastUsefulTargetIndex, childDepth);
  }

  private TreeNode loadProgressiveDataTree(
      final TreeNodeSource nodeSource,
      final Bytes32 dataHash,
      final long dataRootGIndex,
      final int totalChunks) {
    final int superNodeDepth = getSuperNodeDepth();
    return ProgressiveTreeUtil.loadProgressiveSpine(
        nodeSource,
        dataHash,
        dataRootGIndex,
        totalChunks,
        (levelHash, levelGIndex, chunksInLevel, depth) ->
            loadLevelSubtree(
                nodeSource, levelHash, levelGIndex, chunksInLevel, depth, superNodeDepth));
  }

  private TreeNode loadLevelSubtree(
      final TreeNodeSource nodeSource,
      final Bytes32 levelHash,
      final long levelGIndex,
      final int chunksInLevel,
      final int depth,
      final int superNodeDepth) {
    final int childDepth = Math.max(0, depth - superNodeDepth);

    if (depth == 0) {
      // Level 0: single chunk
      return loadChunkNode(nodeSource, levelHash, levelGIndex);
    } else if (childDepth == 0) {
      // SuperNode covers entire level subtree
      if (TreeUtil.ZERO_TREES_BY_ROOT.containsKey(levelHash)) {
        return new SszSuperNode(depth, elementSszSupernodeTemplate.get(), Bytes.EMPTY);
      }
      final Bytes data = nodeSource.loadLeafNode(levelHash, levelGIndex);
      return new SszSuperNode(depth, elementSszSupernodeTemplate.get(), data);
    } else {
      final long lastUsefulGIndex =
          computeLevelLastUsefulGIndex(levelGIndex, chunksInLevel, childDepth, superNodeDepth);

      final TreeNode defaultSubtree = TreeUtil.ZERO_TREES[depth];

      final LoadingUtil.ChildLoader childLoader =
          superNodeDepth == 0
              ? (childNodeSource, childHash, childGIndex) ->
                  loadChunkNode(childNodeSource, childHash, childGIndex)
              : (childNodeSource, childHash, childGIndex) -> {
                if (TreeUtil.ZERO_TREES_BY_ROOT.containsKey(childHash)) {
                  return new SszSuperNode(
                      superNodeDepth, elementSszSupernodeTemplate.get(), Bytes.EMPTY);
                }
                final Bytes data = childNodeSource.loadLeafNode(childHash, childGIndex);
                return new SszSuperNode(superNodeDepth, elementSszSupernodeTemplate.get(), data);
              };

      return LoadingUtil.loadNodesToDepth(
          nodeSource,
          levelHash,
          levelGIndex,
          childDepth,
          defaultSubtree,
          lastUsefulGIndex,
          childLoader);
    }
  }

  private TreeNode loadChunkNode(
      final TreeNodeSource nodeSource, final Bytes32 chunkHash, final long chunkGIndex) {
    if (TreeUtil.ZERO_TREES_BY_ROOT.containsKey(chunkHash)) {
      if (elementSchema.isPrimitive()) {
        return LeafNode.ZERO_LEAVES[LeafNode.MAX_BYTE_SIZE];
      } else {
        return elementSchema.getDefaultTree();
      }
    }
    if (elementSchema.isPrimitive()) {
      final Bytes data = nodeSource.loadLeafNode(chunkHash, chunkGIndex);
      return LeafNode.create(data);
    } else {
      return elementSchema.loadBackingNodes(nodeSource, chunkHash, chunkGIndex);
    }
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

  public SszSchemaHints getHints() {
    return hints;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final AbstractSszProgressiveListSchema<?, ?> that = (AbstractSszProgressiveListSchema<?, ?>) o;
    return Objects.equals(elementSchema, that.elementSchema) && Objects.equals(hints, that.hints);
  }

  @Override
  public int hashCode() {
    return Objects.hash(elementSchema, hints);
  }

  @Override
  public String toString() {
    return "ProgressiveList[" + elementSchema + "]" + getHints();
  }
}
