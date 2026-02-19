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

package tech.pegasys.teku.infrastructure.ssz.tree;

import static com.google.common.base.Preconditions.checkArgument;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Utility for constructing and navigating progressive merkle trees as defined in EIP-7916.
 *
 * <p>A progressive merkle tree is a right-leaning asymmetric tree where subtree capacities grow by
 * 4x per level:
 *
 * <pre>
 *            root
 *             /\
 *            /  \
 *  Level 0 (1)  /\
 *              /  \
 *  Level 1 (4)   /\
 *               /  \
 *  Level 2 (16)  ...
 * </pre>
 *
 * Level L has capacity 4^L and depth 2*L in the balanced subtree.
 */
public class ProgressiveTreeUtil {

  public static final int MAX_LEVEL = 31;

  /**
   * Computes the generalized index of a level's balanced subtree root within the progressive tree.
   *
   * <p>This is the gIndex reached by taking {@code level} right turns down the right spine, then
   * one left turn into the balanced subtree. After {@code n} right turns from SELF(1) the gIndex is
   * {@code 2^(n+1) - 1}; one left turn doubles it, giving {@code (1 << (level + 2)) - 2}.
   */
  public static long spineGIndex(final int level) {
    checkArgument(level >= 0 && level <= MAX_LEVEL, "Invalid level value");
    return (1L << (level + 2)) - 2;
  }

  /** Returns the capacity of a single level: 4^level */
  public static long levelCapacity(final int level) {
    checkArgument(level >= 0 && level <= MAX_LEVEL, "Invalid level value");
    return 1L << (2 * level);
  }

  /**
   * Returns the cumulative capacity through level L (inclusive): (4^(L+1) - 1) / 3
   *
   * <p>Sequence: 1, 5, 21, 85, 341, 1365, ...
   */
  public static long cumulativeCapacity(final int level) {
    checkArgument(level >= 0 && level <= MAX_LEVEL, "Invalid level value");
    // (4^(level+1) - 1) / 3
    return ((1L << (2 * (level + 1))) - 1) / 3;
  }

  /** Returns the depth of the balanced subtree at a given level: 2 * level */
  public static int levelDepth(final int level) {
    return 2 * level;
  }

  /**
   * Determines which level contains the element at the given zero-based index.
   *
   * <p>formula: level = floor(log4(3 * elementIndex + 1))
   */
  public static int levelForIndex(final long elementIndex) {
    checkArgument(elementIndex >= 0, "Element index must be non-negative");
    final long val = (3L * elementIndex) + 1;
    final int log2 = 63 - Long.numberOfLeadingZeros(val);
    return log2 / 2; // floor(log2(val)) / 2 == floor(log4(val))
  }

  /**
   * Builds a progressive merkle tree from a list of chunk nodes.
   *
   * <p>Implements: merkleize_progressive(chunks):
   *
   * <pre>
   *   if chunks is empty: return Bytes32(0)
   *   a = merkleize(chunks[:cap], cap)
   *   b = merkleize_progressive(chunks[cap:], cap*4)
   *   return hash(a, b)
   * </pre>
   */
  public static TreeNode createProgressiveTree(final List<? extends TreeNode> chunks) {
    return createProgressiveTree(chunks, 0);
  }

  private static TreeNode createProgressiveTree(
      final List<? extends TreeNode> chunks, final int currentLevel) {
    if (chunks.isEmpty()) {
      return LeafNode.EMPTY_LEAF;
    }
    final long cap = levelCapacity(currentLevel);
    final int split = (int) Math.min(chunks.size(), cap);
    final int depth = levelDepth(currentLevel);
    final TreeNode left = TreeUtil.createTree(chunks.subList(0, split), depth);
    final TreeNode right =
        createProgressiveTree(chunks.subList(split, chunks.size()), currentLevel + 1);
    return BranchNode.create(left, right);
  }

  /**
   * Applies chunk-level updates to a progressive data tree, growing it if needed.
   *
   * <p>This method walks the progressive tree's right spine level-by-level. Changes are grouped by
   * level and applied as batched {@link TreeUpdates} to each level's balanced subtree (where all
   * changes share the same depth). New levels are created as needed for appends beyond existing
   * capacity. Unchanged subtrees are reused for structural sharing.
   *
   * @param dataTree existing progressive data tree (left child of the list/container root)
   * @param chunkUpdates map from chunk index to new TreeNode for that chunk
   * @param newTotalChunks total number of chunks after updates
   * @return updated progressive data tree with structural sharing
   */
  public static TreeNode updateProgressiveTree(
      final TreeNode dataTree,
      final Int2ObjectMap<TreeNode> chunkUpdates,
      final int newTotalChunks) {
    if (newTotalChunks == 0) {
      return LeafNode.EMPTY_LEAF;
    }
    if (chunkUpdates.isEmpty()) {
      return dataTree;
    }
    final int maxLevel = levelForIndex(newTotalChunks - 1);

    // Group by level and compute gIndices within each level's balanced subtree
    final Int2ObjectMap<List<TreeUpdates.Update>> updatesByLevel =
        new Int2ObjectOpenHashMap<>(maxLevel + 1);

    for (final Int2ObjectMap.Entry<TreeNode> entry : chunkUpdates.int2ObjectEntrySet()) {
      final int chunkIndex = entry.getIntKey();
      final int level = levelForIndex(chunkIndex);
      final long cumulativeBefore = level > 0 ? cumulativeCapacity(level - 1) : 0;
      final long posInLevel = chunkIndex - cumulativeBefore;
      final int depth = levelDepth(level);
      final long gIdx =
          depth > 0
              ? GIndexUtil.gIdxChildGIndex(GIndexUtil.SELF_G_INDEX, posInLevel, depth)
              : GIndexUtil.SELF_G_INDEX;

      updatesByLevel
          .computeIfAbsent(level, __ -> new ArrayList<>())
          .add(new TreeUpdates.Update(gIdx, entry.getValue()));
    }

    return updateLevel(dataTree, 0, maxLevel, updatesByLevel);
  }

  private static TreeNode updateLevel(
      final TreeNode node,
      final int level,
      final int maxLevel,
      final Int2ObjectMap<List<TreeUpdates.Update>> updatesByLevel) {
    if (level > maxLevel) {
      return LeafNode.EMPTY_LEAF;
    }

    TreeNode left;
    TreeNode right;
    if (node instanceof BranchNode branch) {
      left = branch.left();
      right = branch.right();
    } else {
      // Node doesn't exist yet (EMPTY_LEAF) - create zero-filled balanced subtree
      left = TreeUtil.ZERO_TREES[levelDepth(level)];
      right = LeafNode.EMPTY_LEAF;
    }

    final TreeNode originalLeft = left;

    // Apply changes for this level (sort by gIndex as required by TreeUpdates)
    final List<TreeUpdates.Update> levelUpdates = updatesByLevel.get(level);
    if (levelUpdates != null && !levelUpdates.isEmpty()) {
      levelUpdates.sort(Comparator.comparingLong(TreeUpdates.Update::getGeneralizedIndex));
      final TreeUpdates treeUpdates = new TreeUpdates(levelUpdates);
      left = left.updated(treeUpdates);
    }

    final TreeNode newRight = updateLevel(right, level + 1, maxLevel, updatesByLevel);

    @SuppressWarnings("ReferenceComparison")
    boolean unchanged = left == originalLeft && newRight == right;
    if (unchanged) {
      return node; // structural sharing
    }
    return BranchNode.create(left, newRight);
  }

  /**
   * Computes the generalized index for an element at the given logical index within a progressive
   * data tree.
   *
   * <p>For element i at level L, position p = i - cumulativeCapacity(L-1):
   *
   * <ol>
   *   <li>From root: take L right turns to reach level L's subtree parent, then 1 left turn
   *   <li>Within the balanced subtree: standard gIdxChildGIndex(SELF, p, levelDepth(L))
   *   <li>Compose all segments
   * </ol>
   */
  public static long getElementGeneralizedIndex(final long elementIndex) {
    checkArgument(elementIndex >= 0, "Element index must be non-negative");
    final int level = levelForIndex(elementIndex);

    final long gIdx = spineGIndex(level);

    // Within the balanced subtree at this level
    final int depth = levelDepth(level);
    if (depth == 0) {
      return gIdx;
    }

    final long posInLevel = elementIndex - (level > 0 ? cumulativeCapacity(level - 1) : 0);
    final long subtreeGIdx = GIndexUtil.gIdxChildGIndex(GIndexUtil.SELF_G_INDEX, posInLevel, depth);
    return GIndexUtil.gIdxCompose(gIdx, subtreeGIdx);
  }
}
