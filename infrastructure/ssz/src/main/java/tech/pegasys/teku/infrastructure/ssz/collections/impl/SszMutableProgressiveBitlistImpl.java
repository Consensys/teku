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

package tech.pegasys.teku.infrastructure.ssz.collections.impl;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import tech.pegasys.teku.infrastructure.ssz.cache.IntCache;
import tech.pegasys.teku.infrastructure.ssz.collections.SszMutablePrimitiveList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszPrimitiveList;
import tech.pegasys.teku.infrastructure.ssz.impl.AbstractSszComposite;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBit;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszProgressiveBitlistSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.BranchNode;
import tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil;
import tech.pegasys.teku.infrastructure.ssz.tree.LeafNode;
import tech.pegasys.teku.infrastructure.ssz.tree.ProgressiveTreeUtil;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeUpdates;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

/**
 * Mutable implementation of SszBitlist backed by a progressive merkle tree.
 *
 * <p>Groups bit changes by 256-bit chunk, applies packed bit updates per chunk, and uses {@link
 * ProgressiveTreeUtil#updateProgressiveTree} to apply chunk-level changes to the progressive data
 * tree.
 */
public class SszMutableProgressiveBitlistImpl
    extends AbstractSszMutablePrimitiveCollection<Boolean, SszBit>
    implements SszMutablePrimitiveList<Boolean, SszBit> {

  private static final int BITS_PER_CHUNK = 256;

  private int cachedSize;
  private Int2ObjectMap<TreeNode> pendingChunkUpdates;

  public SszMutableProgressiveBitlistImpl(final SszProgressiveBitlistImpl backingImmutableBitlist) {
    super(backingImmutableBitlist);
    cachedSize = backingImmutableBitlist.size();
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @Override
  protected TreeUpdates changesToNewNodes(
      final Stream<Map.Entry<Integer, SszBit>> newChildValues, final TreeNode original) {
    SszPrimitiveSchema primitiveSchema = getPrimitiveElementSchema();
    int elementsPerChunk = BITS_PER_CHUNK;
    TreeNode dataTree = original.get(GIndexUtil.LEFT_CHILD_G_INDEX);
    int previousSize = backingImmutableData.size();
    int previousTotalChunks = (previousSize + BITS_PER_CHUNK - 1) / BITS_PER_CHUNK;
    int previousMaxLevel =
        previousTotalChunks > 0 ? ProgressiveTreeUtil.levelForIndex(previousTotalChunks - 1) : -1;

    // Group bit changes by chunk index
    Int2ObjectMap<List<SszPrimitiveSchema.PackedNodeUpdate>> grouped =
        new Int2ObjectOpenHashMap<>();
    newChildValues.forEach(
        entry -> {
          int chunkIndex = entry.getKey() / elementsPerChunk;
          int internalIndex = entry.getKey() % elementsPerChunk;
          grouped
              .computeIfAbsent(chunkIndex, k -> new ArrayList<>())
              .add(new SszPrimitiveSchema.PackedNodeUpdate(internalIndex, entry.getValue()));
        });

    // Apply packed updates per chunk
    pendingChunkUpdates = new Int2ObjectOpenHashMap<>();
    for (Int2ObjectMap.Entry<List<SszPrimitiveSchema.PackedNodeUpdate>> chunkEntry :
        grouped.int2ObjectEntrySet()) {
      int chunkIndex = chunkEntry.getIntKey();
      List<SszPrimitiveSchema.PackedNodeUpdate> packedUpdates = chunkEntry.getValue();

      TreeNode originalChunk;
      int chunkLevel = ProgressiveTreeUtil.levelForIndex(chunkIndex);
      if (chunkLevel <= previousMaxLevel && packedUpdates.size() < elementsPerChunk) {
        long chunkGIdx = ProgressiveTreeUtil.getElementGeneralizedIndex(chunkIndex);
        originalChunk = dataTree.get(chunkGIdx);
      } else {
        originalChunk = LeafNode.EMPTY_LEAF;
      }

      pendingChunkUpdates.put(
          chunkIndex, primitiveSchema.updatePackedNode(originalChunk, packedUpdates));
    }

    return new TreeUpdates(List.of(), List.of());
  }

  @Override
  protected TreeNode doFinalTreeUpdates(final TreeNode tree) {
    if (pendingChunkUpdates == null || pendingChunkUpdates.isEmpty()) {
      return updateSize(tree);
    }

    TreeNode dataTree = tree.get(GIndexUtil.LEFT_CHILD_G_INDEX);
    int totalChunks = (size() + BITS_PER_CHUNK - 1) / BITS_PER_CHUNK;

    TreeNode updatedDataTree =
        ProgressiveTreeUtil.updateProgressiveTree(dataTree, pendingChunkUpdates, totalChunks);

    pendingChunkUpdates = null;
    return BranchNode.create(updatedDataTree, createSizeNode());
  }

  private TreeNode updateSize(final TreeNode root) {
    return BranchNode.create(root.get(GIndexUtil.LEFT_CHILD_G_INDEX), createSizeNode());
  }

  private TreeNode createSizeNode() {
    return SszUInt64.of(UInt64.fromLongBits(size())).getBackingNode();
  }

  @Override
  public int size() {
    return cachedSize;
  }

  @Override
  public void set(final int index, final SszBit value) {
    super.set(index, value);
    if (index == cachedSize) {
      cachedSize++;
    }
  }

  @Override
  public void clear() {
    super.clear();
    cachedSize = 0;
  }

  @Override
  protected void checkIndex(final int index, final boolean set) {
    if (index < 0 || (!set && index >= size()) || (set && index > size())) {
      throw new IndexOutOfBoundsException(
          "Invalid index " + index + " for progressive bitlist with size " + size());
    }
  }

  @Override
  protected AbstractSszComposite<SszBit> createImmutableSszComposite(
      final TreeNode backingNode, final IntCache<SszBit> childrenCache) {
    return new SszProgressiveBitlistImpl(getSchema(), backingNode);
  }

  @SuppressWarnings("unchecked")
  @Override
  public SszProgressiveBitlistSchema getSchema() {
    return (SszProgressiveBitlistSchema) super.getSchema();
  }

  @Override
  @SuppressWarnings("unchecked")
  public SszPrimitiveList<Boolean, SszBit> commitChanges() {
    return (SszPrimitiveList<Boolean, SszBit>) super.commitChanges();
  }

  @Override
  public SszMutablePrimitiveList<Boolean, SszBit> createWritableCopy() {
    throw new UnsupportedOperationException(
        "Creating a writable copy from writable instance is not supported");
  }
}
