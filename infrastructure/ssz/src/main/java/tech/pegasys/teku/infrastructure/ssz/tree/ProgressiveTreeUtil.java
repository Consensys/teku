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
    checkArgument(elementIndex >= 0, "Element index must be non-negative");
    // Level 0 holds indices [0, 0] (capacity 1, cumulative 1)
    // Level 1 holds indices [1, 4] (capacity 4, cumulative 5)
    // Level 2 holds indices [5, 20] (capacity 16, cumulative 21)
    // ...
    int level = 0;
    long cumulative = 0;
    while (true) {
      long cap = levelCapacity(level);
      if (elementIndex < cumulative + cap) {
        return level;
      }
      cumulative += cap;
      level++;
    }
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
