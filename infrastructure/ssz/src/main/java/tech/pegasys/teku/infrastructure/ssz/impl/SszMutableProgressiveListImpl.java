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

package tech.pegasys.teku.infrastructure.ssz.impl;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.SszMutableList;
import tech.pegasys.teku.infrastructure.ssz.SszMutableRefList;
import tech.pegasys.teku.infrastructure.ssz.SszPrimitive;
import tech.pegasys.teku.infrastructure.ssz.cache.IntCache;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszProgressiveListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.BranchNode;
import tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil;
import tech.pegasys.teku.infrastructure.ssz.tree.LeafNode;
import tech.pegasys.teku.infrastructure.ssz.tree.ProgressiveTreeUtil;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeUpdates;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

/**
 * Mutable implementation of SszList backed by a progressive merkle tree. Handles both composite and
 * packed primitive element types.
 *
 * <p>Overrides the standard change-to-tree-update flow because progressive trees have mixed-depth
 * generalized indices. Changes are stored during {@link #changesToNewNodes} and applied level-by-
 * level in {@link #doFinalTreeUpdates} via {@link ProgressiveTreeUtil#updateProgressiveTree}.
 */
public class SszMutableProgressiveListImpl<
        SszElementT extends SszData, SszMutableElementT extends SszElementT>
    extends AbstractSszMutableCollection<SszElementT, SszMutableElementT>
    implements SszMutableRefList<SszElementT, SszMutableElementT> {

  private int cachedSize;
  private List<Map.Entry<Integer, SszElementT>> pendingChanges;

  public SszMutableProgressiveListImpl(
      final SszProgressiveListImpl<SszElementT> backingImmutableList) {
    super(backingImmutableList);
    cachedSize = backingImmutableList.size();
  }

  @Override
  protected TreeUpdates changesToNewNodes(
      final Stream<Map.Entry<Integer, SszElementT>> newChildValues, final TreeNode original) {
    // Consume and store changes; return empty TreeUpdates to bypass mixed-depth issue
    pendingChanges = newChildValues.toList();
    return new TreeUpdates(List.of(), List.of());
  }

  @Override
  protected TreeNode doFinalTreeUpdates(final TreeNode tree) {
    if (pendingChanges == null || pendingChanges.isEmpty()) {
      return updateSize(tree);
    }

    final TreeNode dataTree = tree.get(GIndexUtil.LEFT_CHILD_G_INDEX);
    final int elementsPerChunk = getSchema().getElementsPerChunk();
    final int previousSize = backingImmutableData.size();

    final Int2ObjectMap<TreeNode> chunkUpdates =
        buildChunkUpdates(dataTree, elementsPerChunk, previousSize);

    final int totalChunks =
        elementsPerChunk > 1 ? (size() + elementsPerChunk - 1) / elementsPerChunk : size();

    final TreeNode updatedDataTree =
        ProgressiveTreeUtil.updateProgressiveTree(dataTree, chunkUpdates, totalChunks);

    pendingChanges = null;
    return BranchNode.create(updatedDataTree, createSizeNode());
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private Int2ObjectMap<TreeNode> buildChunkUpdates(
      final TreeNode dataTree, final int elementsPerChunk, final int previousSize) {
    final Int2ObjectMap<TreeNode> chunkUpdates = new Int2ObjectOpenHashMap<>();

    if (elementsPerChunk > 1) {
      // Packed primitives: group element changes by chunk, apply updatePackedNode
      final SszPrimitiveSchema primitiveSchema =
          (SszPrimitiveSchema) getSchema().getElementSchema();
      final int previousTotalChunks = (previousSize + elementsPerChunk - 1) / elementsPerChunk;
      final int previousMaxLevel =
          previousTotalChunks > 0 ? ProgressiveTreeUtil.levelForIndex(previousTotalChunks - 1) : -1;

      // Group by chunk index
      final Int2ObjectMap<List<SszPrimitiveSchema.PackedNodeUpdate>> grouped =
          new Int2ObjectOpenHashMap<>();
      for (Map.Entry<Integer, SszElementT> entry : pendingChanges) {
        final int chunkIndex = entry.getKey() / elementsPerChunk;
        final int internalIndex = entry.getKey() % elementsPerChunk;
        grouped
            .computeIfAbsent(chunkIndex, k -> new ArrayList<>())
            .add(
                new SszPrimitiveSchema.PackedNodeUpdate(
                    internalIndex, (SszPrimitive) entry.getValue()));
      }

      for (Int2ObjectMap.Entry<List<SszPrimitiveSchema.PackedNodeUpdate>> chunkEntry :
          grouped.int2ObjectEntrySet()) {
        final int chunkIndex = chunkEntry.getIntKey();
        final List<SszPrimitiveSchema.PackedNodeUpdate> packedUpdates = chunkEntry.getValue();

        TreeNode originalChunk;
        final int chunkLevel = ProgressiveTreeUtil.levelForIndex(chunkIndex);
        if (chunkLevel <= previousMaxLevel && packedUpdates.size() < elementsPerChunk) {
          final long chunkGIdx = ProgressiveTreeUtil.getElementGeneralizedIndex(chunkIndex);
          originalChunk = dataTree.get(chunkGIdx);
        } else {
          originalChunk = LeafNode.EMPTY_LEAF;
        }

        chunkUpdates.put(
            chunkIndex, primitiveSchema.updatePackedNode(originalChunk, packedUpdates));
      }
    } else {
      // Composite elements: 1:1 chunk mapping
      for (Map.Entry<Integer, SszElementT> entry : pendingChanges) {
        chunkUpdates.put((int) entry.getKey(), entry.getValue().getBackingNode());
      }
    }

    return chunkUpdates;
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
  public void set(final int index, final SszElementT value) {
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
          "Invalid index " + index + " for progressive list with size " + size());
    }
  }

  @Override
  protected SszProgressiveListImpl<SszElementT> createImmutableSszComposite(
      final TreeNode backingNode, final IntCache<SszElementT> childrenCache) {
    return new SszProgressiveListImpl<>(getSchema(), backingNode, childrenCache);
  }

  @SuppressWarnings("unchecked")
  @Override
  public SszProgressiveListSchema<SszElementT> getSchema() {
    return (SszProgressiveListSchema<SszElementT>) super.getSchema();
  }

  @Override
  @SuppressWarnings("unchecked")
  public SszList<SszElementT> commitChanges() {
    return (SszList<SszElementT>) super.commitChanges();
  }

  @Override
  public SszMutableList<SszElementT> createWritableCopy() {
    throw new UnsupportedOperationException("Creating a copy from writable list is not supported");
  }
}
