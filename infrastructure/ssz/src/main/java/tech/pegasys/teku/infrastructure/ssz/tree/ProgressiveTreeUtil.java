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

  /** Returns the capacity of a single level: 4^level */
  public static long levelCapacity(final int level) {
    checkArgument(level >= 0 && level <= 31, "Level must be in range [0, 31]");
    return 1L << (2 * level);
  }

  /**
   * Returns the cumulative capacity through level L (inclusive): (4^(L+1) - 1) / 3
   *
   * <p>Sequence: 1, 5, 21, 85, 341, 1365, ...
   */
  public static long cumulativeCapacity(final int level) {
    checkArgument(level >= 0 && level <= 31, "Level must be in range [0, 31]");
    // (4^(level+1) - 1) / 3
    return ((1L << (2 * (level + 1))) - 1) / 3;
  }

  /** Returns the depth of the balanced subtree at a given level: 2 * level */
  public static int levelDepth(final int level) {
    return 2 * level;
  }

  /** Determines which level contains the element at the given zero-based index. */
  public static int levelForIndex(final long elementIndex) {
    // formula: level = floor(log4(3 * elementIndex + 1))
    checkArgument(elementIndex >= 0, "Element index must be non-negative");
    final long val = 3L * elementIndex + 1;
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
    long cap = levelCapacity(currentLevel);
    int split = (int) Math.min(chunks.size(), cap);
    int depth = levelDepth(currentLevel);
    TreeNode left = TreeUtil.createTree(chunks.subList(0, split), depth);
    TreeNode right = createProgressiveTree(chunks.subList(split, chunks.size()), currentLevel + 1);
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
    int maxLevel = levelForIndex(newTotalChunks - 1);

    // Group by level and compute gIndices within each level's balanced subtree
    @SuppressWarnings({"unchecked", "rawtypes"})
    List<TreeUpdates.Update>[] updatesByLevel = new List[maxLevel + 1];

    for (Int2ObjectMap.Entry<TreeNode> entry : chunkUpdates.int2ObjectEntrySet()) {
      int chunkIndex = entry.getIntKey();
      int level = levelForIndex(chunkIndex);
      long cumulativeBefore = level > 0 ? cumulativeCapacity(level - 1) : 0;
      long posInLevel = chunkIndex - cumulativeBefore;
      int depth = levelDepth(level);
      long gIdx =
          depth > 0
              ? GIndexUtil.gIdxChildGIndex(GIndexUtil.SELF_G_INDEX, posInLevel, depth)
              : GIndexUtil.SELF_G_INDEX;

      if (updatesByLevel[level] == null) {
        updatesByLevel[level] = new ArrayList<>();
      }
      updatesByLevel[level].add(new TreeUpdates.Update(gIdx, entry.getValue()));
    }

    // Sort updates within each level by gIndex (required by TreeUpdates)
    for (List<TreeUpdates.Update> updates : updatesByLevel) {
      if (updates != null && updates.size() > 1) {
        updates.sort(Comparator.comparingLong(TreeUpdates.Update::getGeneralizedIndex));
      }
    }

    return updateLevel(dataTree, 0, maxLevel, updatesByLevel);
  }

  private static TreeNode updateLevel(
      final TreeNode node,
      final int level,
      final int maxLevel,
      final List<TreeUpdates.Update>[] updatesByLevel) {
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

    TreeNode originalLeft = left;

    // Apply changes for this level
    List<TreeUpdates.Update> levelUpdates = updatesByLevel[level];
    if (levelUpdates != null && !levelUpdates.isEmpty()) {
      TreeUpdates treeUpdates = new TreeUpdates(levelUpdates);
      left = left.updated(treeUpdates);
    }

    TreeNode newRight = updateLevel(right, level + 1, maxLevel, updatesByLevel);

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
    int level = levelForIndex(elementIndex);
    long posInLevel = elementIndex - (level > 0 ? cumulativeCapacity(level - 1) : 0);

    // Navigate from root: take 'level' right turns, then one left turn into the balanced subtree
    // Each right turn is gIdxRightGIndex, one left turn is gIdxLeftGIndex
    long gIdx = GIndexUtil.SELF_G_INDEX;
    for (int i = 0; i < level; i++) {
      gIdx = GIndexUtil.gIdxRightGIndex(gIdx);
    }
    gIdx = GIndexUtil.gIdxLeftGIndex(gIdx);

    // Within the balanced subtree at this level
    int depth = levelDepth(level);
    if (depth > 0) {
      long subtreeGIdx = GIndexUtil.gIdxChildGIndex(GIndexUtil.SELF_G_INDEX, posInLevel, depth);
      gIdx = GIndexUtil.gIdxCompose(gIdx, subtreeGIdx);
    }

    return gIdx;
  }
}
